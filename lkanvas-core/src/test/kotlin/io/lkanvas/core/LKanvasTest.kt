package io.lkanvas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LKanvasTest {
    @Test
    fun `版本与上游基线已登记`() {
        assertTrue(LKanvas.VERSION.startsWith("0.13.0"))
        assertEquals("KLineChart v10.1.0", LKanvas.UPSTREAM_BASELINE)
    }
}
