package io.lkanvas.core.archive

import io.lkanvas.core.overlay.OverlayInstance
import io.lkanvas.core.overlay.OverlayManager
import io.lkanvas.core.overlay.OverlayPhase
import io.lkanvas.core.overlay.OverlayPoint
import io.lkanvas.core.overlay.OverlayTemplates
import io.lkanvas.core.store.MarketKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A-36/A-38 基础：导出→重建→恢复语义等价；未知模板/损坏存档/超限按策略处理。 */
class ChartArchiveTest {

    private val key = MarketKey("BTCUSDT", "1m", adjust = "fwd")

    private fun segment(manager: OverlayManager, id: String?): OverlayInstance {
        manager.beginCreate(OverlayTemplates["segment"]!!)
        manager.addCreatePoint(id ?: "x", OverlayPoint(1000, 10.0))
        manager.addCreatePoint(id ?: "x", OverlayPoint(1010, 12.0))
        return manager.all().last()
    }

    // ---------- A-36：导出→重建→恢复语义等价 ----------

    @Test
    fun `导出后重建恢复覆盖物语义等价`() {
        val src = OverlayManager()
        val createId = src.beginCreate(OverlayTemplates["segment"]!!)!!
        src.addCreatePoint(createId, OverlayPoint(1000, 10.0))
        src.addCreatePoint(createId, OverlayPoint(1010, 12.0))
        val seg = src.all().last()
        seg.locked = true
        seg.zIndex = 3
        seg.extensionData["note"] = "测试标注"

        val json = ChartArchive.export(
            marketKey = key,
            overlays = src.exportCompleted(),
            indicatorParams = mapOf("MA" to listOf(5.0, 10.0)),
            viewportAnchor = ChartArchive.ViewportAnchor(scrollIndex = 42.5, barSpacing = 8.0),
        )

        // 重建：空 manager 恢复
        val dst = OverlayManager()
        val result = ChartArchive.restore(json)
        assertIs<ChartArchive.RestoreResult.Ok>(result)
        assertEquals(1, result.archive.overlays.size)
        dst.restoreFrom(result.archive.overlays)

        val restored = dst.all().single()
        assertEquals("segment", restored.templateName)
        assertEquals(seg.points, restored.points, "锚点逐点一致")
        assertEquals(true, restored.locked)
        assertEquals(3, restored.zIndex)
        assertEquals("测试标注", restored.extensionData["note"])
        assertEquals(OverlayPhase.COMPLETED, restored.phase)
        assertEquals(key, result.archive.marketKey)
        assertEquals(listOf(5.0, 10.0), result.archive.indicatorParams["MA"])
        assertEquals(42.5, result.archive.viewportAnchor!!.scrollIndex, 1e-9)
    }

    @Test
    fun `恢复锚点写回视口且被钳制`() {
        val vp = io.lkanvas.core.viewport.Viewport(minBarSpacing = 1.0, maxBarSpacing = 50.0)
        vp.layout(360.0, 1000)
        vp.restoreAnchor(scrollIndex = 500.0, barSpacing = 999.0, dataCount = 1000)
        assertEquals(50.0, vp.barSpacing, 1e-9, "超出 maxBarSpacing 被钳制")
        assertTrue(vp.scrollIndex.isFinite())
    }

    // ---------- A-38：未知模板/损坏/旧版本/超限 ----------

    @Test
    fun `未知模板条目保留供再次恢复而不丢弃`() {
        val raw = """
            {"schemaVersion":1,"marketKey":null,"overlays":[
              {"id":"u1","template":"mysteryTool","points":[{"ts":1,"value":2.0}],"locked":false,"visible":true,"zIndex":0,"paneId":"main","extensionData":{}}
            ]}
        """.trimIndent()
        val result = ChartArchive.restore(raw)
        assertIs<ChartArchive.RestoreResult.Ok>(result)
        assertEquals(0, result.archive.overlays.size, "已知列表为空")
        assertEquals(1, result.archive.unknownOverlays.size, "未知条目原样保留")
        assertEquals("mysteryTool", result.archive.unknownOverlays.single().templateName)
    }

    @Test
    fun `损坏JSON整次失败且原文保留`() {
        val raw = """{"schemaVersion":1,"overlays":[{"id": BAD}"""
        val result = ChartArchive.restore(raw)
        assertIs<ChartArchive.RestoreResult.Failed>(result)
        assertTrue(result.error.message != null || result.error.cause != null)
        assertEquals(raw, result.rawJson, "原文保留，宿主决定处置")
    }

    @Test
    fun `缺失schemaVersion或旧版本整次失败`() {
        val missing = ChartArchive.restore("""{"overlays":[]}""")
        assertIs<ChartArchive.RestoreResult.Failed>(missing)
        val old = ChartArchive.restore("""{"schemaVersion":99,"overlays":[]}""")
        assertIs<ChartArchive.RestoreResult.Failed>(old, "旧 schema 不自动迁移")
    }

    @Test
    fun `覆盖物超限显式拒绝不静默裁剪`() {
        val many = (0 until ChartArchive.MAX_OVERLAYS + 1).map { i ->
            OverlayInstance("ov-$i", "segment", mutableListOf(OverlayPoint(i.toLong(), 1.0)))
        }
        val error = kotlin.runCatching {
            ChartArchive.export(key, many, emptyMap(), null)
        }.exceptionOrNull()
        assertTrue(error is ChartArchive.ArchiveTooLargeException, "500+1 超限显式错误")
    }

    // ---------- 创建中实例不导出 ----------

    @Test
    fun `CREATING与DRAGGING实例不进入导出`() {
        val manager = OverlayManager()
        val id = manager.beginCreate(OverlayTemplates["segment"]!!)
        manager.addCreatePoint(id!!, OverlayPoint(1L, 1.0)) // 仍在创建中
        val completed = manager.exportCompleted()
        assertTrue(completed.isEmpty(), "创建中不导出")
        manager.cancelCreate()
    }

    // ---------- 空存档往返 ----------

    @Test
    fun `extraPaneIds往返与旧版存档缺省`() {
        val anchor = ChartArchive.ViewportAnchor(3.0, 8.0)
        val json = ChartArchive.export(null, emptyList(), emptyMap(), anchor, extraPaneIds = listOf("pane_b", "pane_a"))
        val restored = ChartArchive.restore(json)
        assertTrue(restored is ChartArchive.RestoreResult.Ok)
        assertEquals(listOf("pane_b", "pane_a"), (restored as ChartArchive.RestoreResult.Ok).archive.extraPaneIds)
        // 旧版存档（无 extraPaneIds 字段）：缺省空，不失败
        val legacy = " " + JSON_LEGACY + " "
        val r2 = ChartArchive.restore(legacy.trimIndent())
        assertTrue(r2 is ChartArchive.RestoreResult.Ok)
        assertEquals(emptyList(), (r2 as ChartArchive.RestoreResult.Ok).archive.extraPaneIds)
    }

    @Test
    fun `空内容导出恢复不崩溃`() {
        val json = ChartArchive.export(null, emptyList(), emptyMap(), null)
        val result = ChartArchive.restore(json)
        assertIs<ChartArchive.RestoreResult.Ok>(result)
        assertEquals(0, result.archive.overlays.size)
        assertEquals(null, result.archive.marketKey)
        assertEquals(null, result.archive.viewportAnchor)
    }
}


private const val JSON_LEGACY = """{"schemaVersion":1,"overlays":[],"viewport":{"scrollIndex":0.0,"barSpacing":8.0,"present":false}}"""
