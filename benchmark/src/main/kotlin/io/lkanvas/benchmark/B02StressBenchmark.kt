package io.lkanvas.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B-02 扩展负载（PRD 7.2 固定负载纪律）：50k candle + 10 个 MA 实例 +
 * 100 条 segment 覆盖物（intent extras 注入），持续横向滚动交互 +
 * [FrameTimingMetric]（frameDurationCpuMs 分位数与 frameOverrunMs 超帧比例）。
 *
 * 历史默认捕获配置曾缺少 RenderThread 匹配，保留旧入口及失败记录。
 * 当前设备已通过 [FrameTimingBenchmark] 的显式捕获配置取得 CPU/overrun 指标；
 * 不再将旧失败解释为该设备对所有非 debuggable 构建均不可采。

 */
@Ignore("历史默认捕获入口；当前显式配置及真实指标见 FrameTimingBenchmark")
@RunWith(AndroidJUnit4::class)
class B02StressBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun stressScroll() = benchmarkRule.measureRepeated(
        packageName = StartupBenchmark.PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait(
                Intent().apply {
                    setClassName(StartupBenchmark.PACKAGE, "io.lkanvas.sample.MainActivity")
                    putExtra("stress_mode", true)
                    putExtra("stress_candles", 50_000)
                    putExtra("stress_indicators", 10)
                    putExtra("stress_overlays", 100)
                },
            )
            // 等负载注入完成（50k reset + 10 指标全量计算 + 100 覆盖物）
            device.waitForIdle()
        },
    ) {
        // 持续横向滚动（B-02 持续交互）：先向过去方向滑 10 次，再拉回 10 次
        val startX = (device.displayWidth * 0.8).toInt()
        val endX = (device.displayWidth * 0.2).toInt()
        val midY = (device.displayHeight * 0.4).toInt()
        repeat(10) {
            device.swipe(startX, midY, endX, midY, 30)
        }
        repeat(10) {
            device.swipe(endX, midY, startX, midY, 30)
        }
    }
}
