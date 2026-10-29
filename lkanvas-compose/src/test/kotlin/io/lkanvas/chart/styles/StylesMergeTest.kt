package io.lkanvas.chart.styles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 批次 5-3 / 批次 06：setStyles 语义（字符串主题 + DeepPartial 深合并）与主题注册表。 */
class StylesMergeTest {

    @Test
    fun `深合并_嵌套对象递归_roundTrip携带值生效`() {
        val base = GridStyle(horizontal = LineStyle(color = "#AAA", size = 2.0), vertical = LineStyle(color = "#BBB"))
        // 上游 merge 按提供的属性合并：round-trip copy（全量字段携带）等价上游全量字面量
        val patch = base.copy(horizontal = base.horizontal.copy(color = "#CCC"))
        val merged = StylesMerge.merge(base, patch) as GridStyle
        assertEquals("#CCC", merged.horizontal.color, "patch 值生效")
        assertEquals(2.0, merged.horizontal.size, "round-trip 携带值保留（上游全量字面量同语义）")
        assertEquals("#BBB", merged.vertical.color)
    }

    @Test
    fun `显式设回默认值生效_SLA009复现`() {
        // 审计复现：show=false 后显式设回 true 必须生效（旧 skip-defaults 规则把等于默认值的
        // patch 视为未提供而被吞；上游按提供属性合并无该规则）
        val off = StylesMerge.merge(Styles(), Styles(grid = GridStyle(show = false))) as Styles
        assertEquals(false, off.grid.show)
        val on = StylesMerge.merge(off, Styles(grid = GridStyle(show = true))) as Styles
        assertEquals(true, on.grid.show, "显式设回默认值生效（提供即合并）")
        // 线宽恢复默认同样生效（审计指出的同规则影响）
        val wide = StylesMerge.merge(Styles(), Styles(grid = Styles().grid.copy(horizontal = LineStyle(size = 3.0)))) as Styles
        assertEquals(3.0, wide.grid.horizontal.size)
        val restored = StylesMerge.merge(wide, Styles(grid = Styles().grid.copy(horizontal = LineStyle(size = 1.0)))) as Styles
        assertEquals(1.0, restored.grid.horizontal.size, "线宽恢复默认")
    }

    @Test
    fun `深合并_null字段不覆盖`() {
        val base = StateLineStyle(color = "#123456")
        val patch = StateLineStyle(color = null)
        val merged = StylesMerge.merge(base, patch) as StateLineStyle
        assertEquals("#123456", merged.color, "patch null=未提供，不覆盖 base（上游属性缺失语义）")
    }

    @Test
    fun `主题解析与注册表`() {
        assertEquals(StylesThemes.light, StylesMerge.resolveTheme("light"))
        assertEquals(StylesThemes.dark, StylesMerge.resolveTheme("dark"))
        assertEquals(StylesThemes.light, StylesMerge.resolveTheme("unknown"), "未知名回退 light")
        // 注册表
        assertTrue(StylesThemes.registeredNames().containsAll(listOf("light", "dark")))
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
