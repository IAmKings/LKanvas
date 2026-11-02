package io.lkanvas.benchmark

import androidx.test.uiautomator.UiDevice
import java.io.File

/** UiAutomation executes argv, so use a script when the trace config must be piped over stdin. */
internal fun UiDevice.startFrameTrace(config: File, destination: String): Int {
    val script = File(config.parentFile, "start-trace.sh")
    script.writeText("cat ${config.absolutePath} | perfetto -D --txt -c - -o $destination 2>&1\n")
    val output = executeShellCommand("sh ${script.absolutePath}")
    File(config.parentFile, "trace-start-output.txt").writeText(output)
    return output.lineSequence().mapNotNull { it.trim().toIntOrNull() }.firstOrNull()
        ?: error("trace did not start: $output")
}
