package io.lkanvas.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 批次 3-4：注册 API 同名（对齐 index.ts register 系 / getSupported 系）。 */
class LkanvasRegistryTest {

    @Test
    fun `27内置指标预注册且getSupportedIndicators完整`() {
        assertEquals(27, LkanvasRegistry.getSupportedIndicators().size)
        val names = LkanvasRegistry.getSupportedIndicators()
        listOf("MA", "VOL", "MACD", "BOLL", "KDJ", "SAR", "DMA").forEach {
            assertTrue(names.contains(it), "内置 $it")
        }
    }

    @Test
    fun `registerIndicator同名冲突与显式替换`() {
        val custom = io.lkanvas.core.indicator.builtin.MovingAverageIndicator()
        assertFalse(LkanvasRegistry.registerIndicator(custom), "同名首次注册冲突")
        assertTrue(LkanvasRegistry.registerIndicator(custom, replace = true), "显式替换成功")
    }

    @Test
    fun `16覆盖物预注册且registerOverlay语义一致`() {
        assertEquals(16, LkanvasRegistry.getSupportedOverlays().size)
        val custom = object : io.lkanvas.core.overlay.OverlayDefinition {
            override val name = "customTool"
            override val pointCount = 2
        }
        assertTrue(LkanvasRegistry.registerOverlay(custom))
        assertFalse(LkanvasRegistry.registerOverlay(custom), "同名冲突")
        assertTrue(LkanvasRegistry.registerOverlay(custom, replace = true))
        assertNotNull(LkanvasRegistry.getOverlayDefinition("customTool"))
    }

    @Test
    fun `locale注册表含zh_CN与en_US`() {
        assertEquals(listOf("zh-CN", "en-US"), LkanvasRegistry.getSupportedLocales())
        assertNotNull(LkanvasRegistry.getLocale("en-US"))
    }
}
