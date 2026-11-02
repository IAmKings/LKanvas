package io.lkanvas.benchmark

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Each independent warm sample must yield a metric; incomplete multi-iteration output cannot pass. */
@RunWith(Parameterized::class)
class WarmStartupBenchmark(private val sample: Int) {
    @get:Rule val rule = MacrobenchmarkRule()

    @OptIn(androidx.benchmark.ExperimentalBenchmarkConfigApi::class, androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi::class)
    @Test(timeout = 180000) fun warmStartup() = inPortrait {
        var preparedPid = ""
        rule.measureRepeated(
            packageName = StartupBenchmark.PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.WARM,
            iterations = 1,
            experimentalConfig = androidx.benchmark.ExperimentalConfig(perfettoConfig = CONFIG),
            setupBlock = {
                startActivityAndWait()
                Thread.sleep(300)
                preparedPid = device.executeShellCommand("pidof ${StartupBenchmark.PACKAGE}").trim()
                check(preparedPid.isNotEmpty())
                pressHome(500)
            },
        ) {
            startActivityAndWait(android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                setClassName(StartupBenchmark.PACKAGE, "io.lkanvas.sample.MainActivity")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            check(preparedPid == device.executeShellCommand("pidof ${StartupBenchmark.PACKAGE}").trim()) { "sample $sample lost its warm process" }
            Thread.sleep(600)
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "sample={0}")
        fun samples(): List<Array<Int>> = (0 until 5).map { arrayOf(it) }
        // 预热线程在 trace 前已创建，显式记录名称供内置解析器匹配 RenderThread。
        @OptIn(androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi::class)
        internal val CONFIG = androidx.benchmark.perfetto.PerfettoConfig.Text("""
                    buffers { size_kb: 65536 fill_policy: RING_BUFFER }
                    data_sources { config { name: "linux.ftrace" ftrace_config {
                        ftrace_events: "sched/sched_switch" ftrace_events: "sched/sched_waking"
                        ftrace_events: "task/task_newtask" ftrace_events: "task/task_rename"
                        atrace_categories: "am" atrace_categories: "wm" atrace_categories: "view"
                        atrace_categories: "gfx" atrace_categories: "dalvik" atrace_apps: "*"
                    } } }
                    data_sources { config { name: "linux.process_stats" process_stats_config {
                        scan_all_processes_on_start: true record_thread_names: true proc_stats_poll_ms: 100
                    } } }
                    data_sources { config { name: "android.packages_list" } }
                    data_sources { config { name: "android.surfaceflinger.frametimeline" } }
                """.trimIndent())
    }
}
