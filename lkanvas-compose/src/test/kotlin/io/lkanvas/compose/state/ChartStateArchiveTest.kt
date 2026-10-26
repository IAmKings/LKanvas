package io.lkanvas.compose.state

import io.lkanvas.core.archive.ChartArchive
import io.lkanvas.core.data.Candle
import io.lkanvas.core.overlay.OverlayPoint
import io.lkanvas.core.overlay.OverlayTemplates
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A-37 接线：恢复期间快照变化不触发保存回调；恢复后触发。 */
class ChartStateArchiveTest {

    private fun c(ts: Long, close: Double) = Candle(ts, close, close + 1, close - 1, close, 100.0)

    private fun newState(scope: CoroutineScope): ChartState =
        ChartState(externalScope = scope)

    @Test
    fun `恢复期间Reset空数据不触发保存回调`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val state = newState(CoroutineScope(dispatcher))
        val saves = mutableListOf<Int>()
        state.onArchiveDirty = { saves += 1 }

        // 宿主读档 → 发起恢复
        state.restoreGate.beginRestore()
        val n0 = saves.size
        // 引擎自动 Reset 空列表（模拟恢复前空状态）
        state.reset(MarketKey("T", "1m"), emptyList())
        assertEquals(n0, saves.size, "恢复期空状态不得发出保存事件")

        state.restoreGate.endRestore(true)
        // 恢复后数据变化
        state.reset(MarketKey("T", "1m"), listOf(c(1, 10.0)))
        assertTrue(saves.size > n0, "恢复后的数据变化触发保存")
    }

    @Test
    fun `applyRestoredArchive写回覆盖物与参数`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val state = newState(CoroutineScope(dispatcher))
        // 先造一份存档：segment 两点
        val id = state.overlays.beginCreate(OverlayTemplates["segment"]!!)
        state.overlays.addCreatePoint(id!!, OverlayPoint(1000, 10.0))
        state.overlays.addCreatePoint(id, OverlayPoint(1010, 12.0))
        val json = ChartArchive.export(
            marketKey = MarketKey("T", "1m"),
            overlays = state.overlays.exportCompleted(),
            indicatorParams = mapOf("MA" to listOf(3.0)),
            viewportAnchor = null,
        )
        // 清空后恢复
        val restored = ChartArchive.restore(json)
        assertTrue(restored is ChartArchive.RestoreResult.Ok)
        val state2 = newState(CoroutineScope(dispatcher))
        state2.applyRestoredArchive(restored.archive)
        assertEquals(1, state2.overlays.all().size, "覆盖物写回")
        // 指标参数写回经公开快照验证（MA 重算生效后图例可读，此处验证不抛错与门禁退出）
        assertFalse(state2.restoreGate.restoring)
    }
}
