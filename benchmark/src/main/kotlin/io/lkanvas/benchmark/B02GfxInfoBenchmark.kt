package io.lkanvas.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B-02 扩展负载——等价计量口径（PRD 7.3「旧系统使用冻结的等价方法，不混算」）。
 *
 * 背景：FrameTimingMetric 需要 perfetto RenderThread doFrame 切片，在冻结设备
 * （MIUI 14）对非 debuggable 构建不可采集（minify 与否均复现，对照实验见
 * docs/performance-baseline.md §4）。本测试按等价口径采集：
 *
 * - 构建：`:app-sample` 的 benchmark buildType（**非压缩**，isMinifyEnabled=false，
 *   非 debuggable；R8 压缩口径的 gfxinfo 在该设备同样不可见——0 帧对照实验）。
 * - 计量：`dumpsys gfxinfo`（HWUI 帧统计）：Total frames / Janky / P50~P99。
 * - 负载：50k candle + 10 MA 实例 + 100 segment（intent extras，种子固定）。
 * - 交互：10 次向过去方向全宽滑动 + 10 次返回（B-02 持续交互）。
 *
 * **口径警告**：非压缩构建的帧耗时优于 R8 构建（启动/渲染均不含 R8 收益），
 * 该数据只用于「负载压力量级判定」，不得与 FrameTimingMetric 数据或压缩构建
 * 数据直接比较；R8 口径的帧数据待设备兼容后由 B02StressBenchmark 采集。
 *
 * 结果经 JUnit XML system-out 与 logcat（B02_GFXINFO 标签）双通道留档。
 */
@RunWith(AndroidJUnit4::class)
class B02GfxInfoBenchmark {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun stressScroll() {
        val pkg = "io.lkanvas.sample"
        // 冷启动 + stress 负载注入（注入耗时：50k reset + 10 指标全量 + 100 覆盖物）
        device.executeShellCommand("am force-stop $pkg")
        Thread.sleep(1_000)
        device.executeShellCommand(
            "am start -n $pkg/.MainActivity --ez stress_mode true " +
                "--ei stress_candles 50000 --ei stress_indicators 10 --ei stress_overlays 100",
        )
        Thread.sleep(22_000)
        device.executeShellCommand("dumpsys gfxinfo $pkg reset")

        // B-02 持续交互：先向过去方向（查看更早数据）滑 10 次，再拉回 10 次
        val w = device.displayWidth
        val h = device.displayHeight
        val midY = (h * 0.4).toInt()
        repeat(10) {
            device.swipe((w * 0.2).toInt(), midY, (w * 0.8).toInt(), midY, 25)
        }
        repeat(10) {
            device.swipe((w * 0.8).toInt(), midY, (w * 0.2).toInt(), midY, 25)
        }
        Thread.sleep(1_000)

        val raw = device.executeShellCommand("dumpsys gfxinfo $pkg")
        val metrics = parseGfxInfo(raw)

        // 计量可用性护栏（PRD：不能以缺计量掩盖；不可得即失败）
        check((metrics["totalFrames"]?.toIntOrNull() ?: 0) > 0) {
            "gfxinfo 未采集到帧（计量不可得）：\n" + raw.take(800)
        }

        val summary = metrics.entries.joinToString(", ") { "${it.key}=${it.value}" }
        val report = "B02_GFXINFO 50k/10ind/100ovl 20swipes -> $summary"
        println(report)
        android.util.Log.i("B02_GFXINFO", report)
        // JUnit XML system-out 留档（connected 测试报告自动收集）
        device.executeShellCommand(
            "log -t B02_GFXINFO -p i \"$summary\"",
        )
    }

    /** 解析 gfxinfo 关键行（Total frames / Janky / P50/P90/P95/P99）。 */
    internal fun parseGfxInfo(raw: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in raw.lineSequence()) {
            val t = line.trim()
            when {
                t.startsWith("Total frames rendered:") -> out["totalFrames"] = t.substringAfter(":").trim()
                t.startsWith("Janky frames:") -> out["janky"] = t.substringAfter(":").trim().substringBefore("(").trim()
                t.endsWith("th percentile:") -> {
                    val pct = t.substringBefore("th percentile").trim().removeSuffix("50").ifEmpty { "50" }
                    val key = when {
                        t.startsWith("50th") -> "p50"
                        t.startsWith("90th") -> "p90"
                        t.startsWith("95th") -> "p95"
                        t.startsWith("99th") -> "p99"
                        else -> t.substringBefore("th").trim() + "pct"
                    }
                    out[key] = t.substringAfter(":").trim().substringBefore("ms").trim()
                }
            }
        }
        return out
    }
}
