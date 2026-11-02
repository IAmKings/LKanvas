package io.lkanvas.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class GfxInfoParserTest {
    @Test fun fullDumpKeepsFramePercentilesSeparateFromGpuAndLegacy() {
        val parsed = B02GfxInfoBenchmark().parseGfxInfo("""
            Total frames rendered: 559
            Janky frames: 447 (79.96%)
            Janky frames (legacy): 559 (100.00%)
            50th percentile: 19ms
            90th percentile: 23ms
            95th percentile: 25ms
            99th percentile: 30ms
            95th gpu percentile: 4ms
        """.trimIndent())
        assertEquals("559", parsed["totalFrames"])
        assertEquals("447", parsed["janky"])
        assertEquals("19", parsed["p50"])
        assertEquals("23", parsed["p90"])
        assertEquals("25", parsed["p95"])
        assertEquals("30", parsed["p99"])
        assertEquals("559", parsed["legacyJanky"])
    }
}
