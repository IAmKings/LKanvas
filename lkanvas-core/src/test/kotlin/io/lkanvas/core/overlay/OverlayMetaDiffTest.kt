package io.lkanvas.core.overlay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import io.lkanvas.core.extension.overlay.OverlayTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F1 自动 diff（对齐批次 4 出口）：Kotlin 16 覆盖物模板元数据 ↔ 上游 TS
 * 逐字段比对（totalStep/needDefaultPointFigure/needDefaultXAxisFigure/
 * needDefaultYAxisFigure/drawingMode↔continuous）。fixtures 由
 * tools/alignment/extract_ts_indicator_meta.py 的 extract_overlays 生成。
 */
class OverlayMetaDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    private data class TsMeta(
        val totalStep: Int?,
        val needDefaultPointFigure: Boolean,
        val needDefaultXAxisFigure: Boolean,
        val needDefaultYAxisFigure: Boolean,
        val drawingMode: String?,
    )

    private fun loadTsMeta(): Map<String, TsMeta> {
        val text = javaClass.getResourceAsStream("/fixtures/ts-overlay-meta.json")!!
            .bufferedReader().readText()
        val root = json.parseToJsonElement(text).jsonObject
        val out = mutableMapOf<String, TsMeta>()
        for ((name, el) in root) {
            val o = el.jsonObject
            out[name] = TsMeta(
                totalStep = o["totalStep"]?.jsonPrimitive?.intOrNull,
                needDefaultPointFigure = o["needDefaultPointFigure"]?.jsonPrimitive?.booleanOrNull ?: false,
                needDefaultXAxisFigure = o["needDefaultXAxisFigure"]?.jsonPrimitive?.booleanOrNull ?: false,
                needDefaultYAxisFigure = o["needDefaultYAxisFigure"]?.jsonPrimitive?.booleanOrNull ?: false,
                drawingMode = o["drawingMode"]?.jsonPrimitive?.contentOrNull,
            )
        }
        return out
    }

    private val defs: Map<String, OverlayDefinition> = io.lkanvas.core.extension.overlay.OverlayTemplates.let { table -> table.supported().mapNotNull { table[it] }.associateBy { it.name } }

    @Test
    fun `16模板全部覆盖且name集合一致`() {
        val ts = loadTsMeta()
        assertEquals(16, ts.size)
        assertEquals(16, defs.size)
        assertEquals(ts.keys, defs.keys, "name 集合一致")
    }

    @Test
    fun `totalStep与上游一致`() {
        val ts = loadTsMeta()
        for ((name, def) in defs) {
            assertEquals(ts[name]!!.totalStep, def.totalStep, "$name totalStep")
        }
    }

    @Test
    fun `needDefault三字段与上游一致`() {
        val ts = loadTsMeta()
        for ((name, def) in defs) {
            assertEquals(ts[name]!!.needDefaultPointFigure, def.needDefaultPointFigure, "$name needDefaultPointFigure")
            assertEquals(ts[name]!!.needDefaultXAxisFigure, def.needDefaultXAxisFigure, "$name needDefaultXAxisFigure")
            assertEquals(ts[name]!!.needDefaultYAxisFigure, def.needDefaultYAxisFigure, "$name needDefaultYAxisFigure")
        }
    }

    @Test
    fun `drawingMode与continuous一致`() {
        val ts = loadTsMeta()
        for ((name, def) in defs) {
            val tsMode = ts[name]!!.drawingMode
            val expectContinuous = tsMode == "continuous"
            assertEquals(expectContinuous, def.continuous, "$name drawingMode=$tsMode")
        }
    }

    @Test
    fun `pointCount与totalStep换算一致`() {
        // 换算约定：pointCount = totalStep - 1（continuous 模板除外）
        val ts = loadTsMeta()
        for ((name, def) in defs) {
            if (!def.continuous) {
                assertEquals(ts[name]!!.totalStep!! - 1, def.pointCount, "$name pointCount = totalStep-1")
            }
        }
    }
}
