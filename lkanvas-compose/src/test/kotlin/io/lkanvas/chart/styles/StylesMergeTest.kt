package io.lkanvas.chart.styles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 批次 5-3：setStyles 语义（字符串主题 + DeepPartial 深合并）与主题注册表。 */
class StylesMergeTest {

    @Test
    fun `深合并_嵌套对象递归_未提供字段保留`() {
        val base = GridStyle(horizontal = LineStyle(color = "#AAA", size = 2.0), vertical = LineStyle(color = "#BBB"))
        // patch 只改 horizontal.color：size=2.0 与 vertical 整体应保留
        val patch = GridStyle(horizontal = LineStyle(color = "#CCC"))
        val merged = StylesMerge.merge(base, patch) as GridStyle
        assertEquals("#CCC", merged.horizontal.color, "patch 值生效")
        assertEquals(2.0, merged.horizontal.size, "base 未提供字段保留")
        assertEquals("#BBB", merged.vertical.color, "未触及子对象保留")
    }

    @Test
    fun `深合并_null字段不覆盖`() {
        val base = StateLineStyle(color = "#123456")
        val patch = StateLineStyle(color = null)
        val merged = StylesMerge.merge(base, patch) as StateLineStyle
        assertEquals("#123456", merged.color, "patch null 不覆盖 base")
    }

    @Test
    fun `主题解析与注册表`() {
        assertEquals(StylesThemes.light, StylesMerge.resolveTheme("light"))
        assertEquals(StylesThemes.dark, StylesMerge.resolveTheme("dark"))
        assertEquals(StylesThemes.light, StylesMerge.resolveTheme("unknown"), "未知名回退 light")
        // 注册表
        assertEquals(listOf("light", "dark"), StylesThemes.registeredNames())
        val custom = Styles(separator = SeparatorStyle(color = "#123123"))
        StylesThemes.registerStyles("custom", custom)
        assertTrue(StylesThemes.registeredNames().contains("custom"))
    }

    @Test
    fun `dark主题与light主题色不同`() {
        assertNotEquals(StylesThemes.light.grid.horizontal.color, StylesThemes.dark.grid.horizontal.color)
        assertNotEquals(StylesThemes.light.separator.color, StylesThemes.dark.separator.color)
        assertFalse(StylesThemes.dark.separator.color.isEmpty())
    }
}
