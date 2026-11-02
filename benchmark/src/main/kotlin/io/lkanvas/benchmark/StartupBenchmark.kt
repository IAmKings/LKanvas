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
    fun coldStartup() = inPortrait { benchmarkRule.measureRepeated(
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
    } }

    @org.junit.Ignore("K60U MIUI14 warm StartupTimingMetric has missing metrics; explicit equivalent probe: WarmReadinessTest")
    @Test
    fun warmStartup() = inPortrait { benchmarkRule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = { pressHome() },
    ) {
        // Recreate the Activity in the warm process; a launcher resume only measures the first iteration.
        startActivityAndWait(android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            setClassName(PACKAGE, "io.lkanvas.sample.MainActivity")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        device.waitForIdle()
    } }

    companion object {
        const val PACKAGE = "io.lkanvas.sample"
    }
}
