package io.lkanvas.benchmark

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

/** Freeze measurement geometry independently of the phone's physical orientation. */
internal fun inPortrait(block: () -> Unit) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val original = listOf("font_scale", "user_rotation", "accelerometer_rotation").associateWith {
        device.executeShellCommand("settings get system $it").trim()
    }
    try {
        device.executeShellCommand("settings put system font_scale 1.0")
        device.executeShellCommand("wm user-rotation lock 0")
        val capture = File(context.getExternalFilesDir("validation"), "measurement-geometry.png")
        var stable = 0
        var width = 0
        var height = 0
        for (attempt in 0 until 40) {
            Thread.sleep(250)
            device.executeShellCommand("wm user-rotation lock 0")
            val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            width = image?.width ?: 0; height = image?.height ?: 0
            stable = if (width > 0 && height > width) stable + 1 else 0
            if (stable >= 2 && image != null) java.io.FileOutputStream(capture).use {
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            image?.recycle()
            if (stable >= 2) break
        }
        check(stable >= 2) { "measurement requires stable actual portrait output; last=${width}x$height" }
        println("MEASUREMENT_GEOMETRY ${width}x$height, font_scale=1.0")
        block()
    } finally {
        device.executeShellCommand("wm user-rotation ${if (original["accelerometer_rotation"] == "1") "free" else "lock ${original["user_rotation"]}"}")
        original.forEach { (key, value) -> device.executeShellCommand("settings put system $key $value") }
    }
}
