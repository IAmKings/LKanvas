package io.lkanvas.core.archive

import io.lkanvas.core.overlay.OverlayInstance
import io.lkanvas.core.overlay.OverlayPhase
import io.lkanvas.core.overlay.OverlayPoint
import io.lkanvas.core.extension.overlay.OverlayTemplates
import io.lkanvas.core.store.MarketKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * 图表存档（PRD R-12/T-25）：版本化 DTO + JSON 序列化。
 *
 * 包含：MarketKey、schemaVersion、已完成绘图（CREATING/DRAGGING 不导出）、
 * 指标实例参数、视口锚点（时间索引+间距；不持久化裸 index 语义由宿主
 * 在恢复时以数据重定位）。不序列化协程/回调/Context/运行对象。
 *
 * 上限（PRD R-12）：快照 1 MiB、覆盖物 500 个，超限显式错误，不静默裁剪。
 * 恢复：已知字段损坏默认整次失败并保留原文（A-38）；未知模板条目
 * 保留在 [RestoreResult.Ok.unknownOverlays] 供再次恢复，不静默丢弃；
 * 恢复时机由宿主控制（本类不做任何 IO 与自动写回）。
 */
object ChartArchive {

    const val SCHEMA_VERSION: Int = 1
    const val MAX_ARCHIVE_BYTES: Int = 1 shl 20
    const val MAX_OVERLAYS: Int = 500

    private val json = Json { ignoreUnknownKeys = false }

    /** 超限错误（显式拒绝，不静默裁剪）。 */
    class ArchiveTooLargeException(message: String) : IllegalStateException(message)

    /** 视口锚点（滚动索引 + barSpacing）。 */
    data class ViewportAnchor(val scrollIndex: Double, val barSpacing: Double)

    /** 导出内容（宿主可修改后再次导出或写回）。 */
    data class Archive(
        val marketKey: MarketKey?,
        val overlays: List<OverlayInstance>,
        /** 未知模板条目（原样保留，供再次导出/后续版本恢复）。 */
        val unknownOverlays: List<OverlayInstance>,
        val indicatorParams: Map<String, List<Double>>,
        val viewportAnchor: ViewportAnchor?,
        /** 0.5.x：动态副图 paneId 渲染序（旧版存档缺省空；宿主据此重建 pane）。 */
        val extraPaneIds: List<String> = emptyList(),
    )

    /** 恢复结果：Ok（含未知条目）或 Failed（原文保留）。 */
    sealed interface RestoreResult {
        data class Ok(val archive: Archive) : RestoreResult
        data class Failed(val error: Throwable, val rawJson: String) : RestoreResult
    }

    // ---------- 导出 ----------

    fun export(
        marketKey: MarketKey?,
        overlays: List<OverlayInstance>,
        indicatorParams: Map<String, List<Double>>,
        viewportAnchor: ViewportAnchor?,
        extraPaneIds: List<String> = emptyList(),
    ): String {
        if (overlays.size > MAX_OVERLAYS) {
            throw ArchiveTooLargeException("覆盖物 ${overlays.size} 超上限 $MAX_OVERLAYS")
        }
        val root = buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            if (marketKey != null) put("marketKey", marketKey.toJson()) else put("marketKey", kotlinx.serialization.json.JsonNull)
            put("overlays", overlays.map { it.toJson() }.let { buildJsonArray { it.forEach { add(it) } } })
            put("indicatorParams", buildJsonObject {
                indicatorParams.forEach { (name, params) ->
                    put(name, buildJsonArray { params.forEach { add(JsonPrimitive(it)) } })
                }
            })
            put("viewport", buildJsonObject {
                put("scrollIndex", viewportAnchor?.scrollIndex ?: 0.0)
                put("barSpacing", viewportAnchor?.barSpacing ?: 0.0)
                put("present", viewportAnchor != null)
            })
            if (extraPaneIds.isNotEmpty()) {
                put("extraPaneIds", buildJsonArray { extraPaneIds.forEach { add(JsonPrimitive(it)) } })
            }
        }
        val text = root.toString()
        if (text.encodeToByteArray().size > MAX_ARCHIVE_BYTES) {
            throw ArchiveTooLargeException("存档 ${text.length} 字符超上限 $MAX_ARCHIVE_BYTES 字节")
        }
        return text
    }

    /** 便捷：从 OverlayManager 导出已完成绘图（CREATING/DRAGGING 不导出）。 */
    fun exportFrom(
        marketKey: MarketKey?,
        overlays: List<OverlayInstance>,
        indicatorParams: Map<String, List<Double>>,
        viewportAnchor: ViewportAnchor?,
        extraPaneIds: List<String> = emptyList(),
    ): String = export(marketKey, overlays.filter { it.phase == OverlayPhase.COMPLETED || it.phase == OverlayPhase.IDLE || it.phase == OverlayPhase.SELECTED }, indicatorParams, viewportAnchor, extraPaneIds)

    // ---------- 恢复 ----------

    fun restore(rawJson: String): RestoreResult = try {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val version = root["schemaVersion"]?.jsonPrimitive?.int
            ?: throw IllegalStateException("缺少 schemaVersion（旧版或损坏存档）")
        if (version != SCHEMA_VERSION) {
            throw IllegalStateException("schema 版本 $version 与当前 $SCHEMA_VERSION 不兼容，需宿主显式迁移")
        }
        val marketKeyEl = root["marketKey"]
        val marketKey = if (marketKeyEl is JsonObject) marketKeyEl.toMarketKey() else null
        val known = mutableListOf<OverlayInstance>()
        val unknown = mutableListOf<OverlayInstance>()
        root["overlays"]?.jsonArray?.forEach { el ->
            val inst = el.jsonObject.toOverlayInstance()
            if (OverlayTemplates[inst.templateName] != null) known += inst else unknown += inst
        }
        if (known.size + unknown.size > MAX_OVERLAYS) {
            throw ArchiveTooLargeException("覆盖物 ${known.size + unknown.size} 超上限 $MAX_OVERLAYS")
        }
        val params = HashMap<String, List<Double>>()
        root["indicatorParams"]?.jsonObject?.forEach { (name, arr) ->
            params[name] = arr.jsonArray.map { it.jsonPrimitive.double }
        }
        val viewport = root["viewport"]?.jsonObject?.let { vp ->
            if (vp["present"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true) {
                ViewportAnchor(vp["scrollIndex"]!!.jsonPrimitive.double, vp["barSpacing"]!!.jsonPrimitive.double)
            } else null
        }
        // 0.5.x：extraPaneIds（旧版存档无此字段，缺省空——向后兼容不升版本号）
        val extraPaneIds = root["extraPaneIds"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        RestoreResult.Ok(Archive(marketKey, known, unknown, params, viewport, extraPaneIds))
    } catch (t: Throwable) {
        // 已知字段损坏：整次失败，原文保留（不自动覆盖，A-38）
        RestoreResult.Failed(t, rawJson)
    }

    // ---------- DTO 映射 ----------

    private fun MarketKey.toJson() = buildJsonObject {
        put("symbol", symbol)
        put("period", period)
        put("adjust", adjust)
    }

    private fun JsonObject.toMarketKey(): MarketKey = MarketKey(
        symbol = getValue("symbol").jsonPrimitive.content,
        period = getValue("period").jsonPrimitive.content,
        adjust = get("adjust")?.jsonPrimitive?.content ?: "none",
    )

    private fun OverlayInstance.toJson() = buildJsonObject {
        put("id", id)
        put("template", templateName)
        put("points", buildJsonArray {
            points.forEach { p ->
                add(buildJsonObject {
                    put("ts", p.timestamp)
                    put("value", p.value)
                })
            }
        })
        put("locked", locked)
        put("visible", visible)
        put("zIndex", zIndex)
        put("paneId", effectivePaneId)
        mode?.let { put("mode", it.name) }
        put("modeSensitivity", modeSensitivity)
        put("extensionData", buildJsonObject { extensionData.forEach { (k, v) -> put(k, v) } })
    }

    private fun JsonObject.toOverlayInstance(): OverlayInstance {
        val id = getValue("id").jsonPrimitive.content
        val template = getValue("template").jsonPrimitive.content
        val points = getValue("points").jsonArray.map { p ->
            val o = p.jsonObject
            OverlayPoint(o["ts"]!!.jsonPrimitive.long, o["value"]!!.jsonPrimitive.double)
        }
        val inst = OverlayInstance(
            id = id,
            templateName = template,
            points = points.toMutableList(),
            locked = get("locked")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
            visible = get("visible")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
            zIndex = get("zIndex")?.jsonPrimitive?.int ?: 0,
            paneId = get("paneId")?.jsonPrimitive?.content ?: "main",
        )
        get("extensionData")?.jsonObject?.forEach { (k, v) ->
            inst.extensionData[k] = v.jsonPrimitive.content
        }
        get("mode")?.jsonPrimitive?.content?.let { inst.mode = io.lkanvas.core.overlay.MagnetMode.valueOf(it) }
        get("modeSensitivity")?.jsonPrimitive?.double?.let {
            require(it.isFinite() && it >= 0) { "modeSensitivity 必须是有限非负数" }
            inst.modeSensitivity = it
        }
        inst.phase = OverlayPhase.COMPLETED
        return inst
    }
}
