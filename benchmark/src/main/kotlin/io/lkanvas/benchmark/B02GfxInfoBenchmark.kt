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
 * 背景：历史默认 FrameTiming 捕获配置缺 RenderThread 匹配；本轮显式配置已可采
 *（见 FrameTimingBenchmark）。本测试独立保留 HWUI 口径，不与 CPU/overrun 混算：
 *
 * - 构建：`:app-sample` 的 benchmark buildType（**非压缩**，isMinifyEnabled=false，
 *   非 debuggable；R8 release 另行安装后可复采，不与本变体混算）。
 * - 计量：`dumpsys gfxinfo`（HWUI 帧统计）：Total frames / Janky / P50~P99。
 * - 负载：50k candle + 10 MA 实例 + 100 segment（intent extras，种子固定）。
 * - 交互：10 次向过去方向全宽滑动 + 10 次返回（B-02 持续交互）。
 *
 * **口径警告**：非压缩与 R8 构建应分别采样，不预设哪种构建更快，
 * 该数据只用于「负载压力量级判定」，不得与 FrameTimingMetric 数据或压缩构建
 * 数据直接比较；R8 release 的 HWUI 已可复采，FrameTiming 显式捕获配置已独立实测。
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
    fun stressScroll() = inPortrait {
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

        val captureTrace = InstrumentationRegistry.getArguments().getString("frameTrace") == "true"
        val traceStart = android.os.SystemClock.elapsedRealtime()
        if (captureTrace) {
            val config = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("validation"), "frames.pbtxt")
            config.writeText("""
                buffers { size_kb: 65536 fill_policy: RING_BUFFER }
                duration_ms: 17000
                data_sources { config { name: "linux.ftrace" ftrace_config {
                    ftrace_events: "sched/sched_switch" ftrace_events: "sched/sched_waking"
                    atrace_categories: "gfx" atrace_categories: "view" atrace_categories: "input"
                    atrace_apps: "io.lkanvas.sample"
                } } }
                data_sources { config { name: "linux.process_stats" process_stats_config {
                    scan_all_processes_on_start: true proc_stats_poll_ms: 500
                } } }
                data_sources { config { name: "android.surfaceflinger.frametimeline" } }
            """.trimIndent())
            device.startFrameTrace(config, "/data/misc/perfetto-traces/lkanvas-b02.perfetto-trace")
        }

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
        if (captureTrace) Thread.sleep((traceStart + 18_000 - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0))

        val raw = device.executeShellCommand("dumpsys gfxinfo $pkg framestats")
        val output = java.io.File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("validation"),
            "b02-${System.currentTimeMillis()}.txt",
        )
        output.writeText(raw)
        val metrics = parseGfxInfo(raw)
        check(listOf("totalFrames", "janky", "p50", "p90", "p95", "p99").all { metrics[it]?.toDoubleOrNull() != null }) {
            "gfxinfo 计量不完整；原始输出：${output.absolutePath}"
        }
        android.util.Log.i("B02_GFXINFO", "MEASUREMENT_ONLY; deadline/input gates unverified; raw=${output.absolutePath}")

        // 计量可用性护栏（PRD：不能以缺计量掩盖；不可得即失败）
        check((metrics["totalFrames"]?.toIntOrNull() ?: 0) > 0) {
            "gfxinfo 未采集到帧（计量不可得）：\n" + raw.take(800)
        }

        // 验收补强：分位数随摘要落盘（T-34 参照 P50 22/P95 30ms 的对照口径）
        val summary = metrics.entries.joinToString(", ") { "${it.key}=${it.value}" }
        val report = "B02_GFXINFO 50k/10ind/100ovl 20swipes -> $summary"
        println(report)
        android.util.Log.i("B02_GFXINFO", report)
        // 分位数单独逐行落 log（UiDevice shell 输出可能截断长 dump）
        metrics.forEach { (k, v) ->
            if (k != "totalFrames" && k != "janky") {
                android.util.Log.i("B02_GFXINFO", "PCTL $k=$v")
            }
        }
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
                t.startsWith("Janky frames (legacy):") -> out["legacyJanky"] = t.substringAfter(":").trim().substringBefore("(").trim()
                else -> Regex("^(50|90|95|99)th percentile:\\s*(\\d+(?:\\.\\d+)?)ms$").matchEntire(t)?.let { match ->
                    out["p${match.groupValues[1]}"] = match.groupValues[2]
                }
            }
        }
        return out
    }
}
