package io.lkanvas.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B-01 启动骨架（P1 首轮）：冷/热进程到完整图表的 StartupTimingMetric。
 * 门槛冻结值见 docs/performance-baseline.md（PRD 7.3）。
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        // 图表首帧后稍等稳定（完整图表 = 主图 + 三内置指标首帧）
        device.waitForIdle()
    }

    companion object {
        const val PACKAGE = "io.lkanvas.sample"
    }
}
