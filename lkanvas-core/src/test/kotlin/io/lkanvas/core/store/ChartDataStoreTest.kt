package io.lkanvas.core.store

import io.lkanvas.core.data.Candle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartDataStoreTest {

    private fun c(ts: Long, close: Double = 10.0, volume: Double? = 100.0) =
        Candle(ts, close, close + 1.0, close - 1.0, close, volume)

    private val key = MarketKey("BTCUSDT", "1m")

    private fun storeWithData(n: Int = 10): ChartDataStore {
        val s = ChartDataStore()
        s.reset(key, (0 until n).map { c(1000L + it, 10.0 + it) })
        return s
    }

    // ---------- A-03：同 timestamp 连续替换 ----------

    @Test
    fun `同时间戳连续替换一千次长度不变且终值与一次替换一致`() {
        val store = storeWithData()
        repeat(1000) { i ->
            val r = store.replaceLatest(c(1009L, close = 10.0 + i * 0.01))
            assertTrue(r is ChangeResult.Accepted, "第 $i 次替换应成功")
        }
        assertEquals(10, store.candleCount, "替换不改变长度")
        // 1000 次的最后一次 close = 10 + 999*0.01（浮点表达式原样比较）
        assertEquals(10.0 + 999 * 0.01, store.candlesView()[9].close)

        // 与「一次性替换到同值」的终态一致（A-03）
        val once = storeWithData()
        once.replaceLatest(c(1009L, close = 10.0 + 999 * 0.01))
        assertEquals(once.lastCandle, store.lastCandle)
    }

    @Test
    fun `ReplaceLatest 时间戳不一致被拒绝且状态不变`() {
        val store = storeWithData()
        val rev = store.revision
        val r = store.replaceLatest(c(1005L)) // 非末根
        assertTrue(r is ChangeResult.Rejected && r.reason == RejectReason.TIMESTAMP_MISMATCH)
        assertEquals(rev, store.revision, "拒绝不推进 revision")
        assertEquals(10.0, store.candlesView()[0].close, "数据不变")
    }

    // ---------- UpsertLatest 三分支 ----------

    @Test
    fun `upsert 等于末根替换大于追加小于拒绝`() {
        val store = storeWithData()

        // 等于末根：替换
        assertTrue(store.upsertLatest(c(1009L, 20.0)) is ChangeResult.Accepted)
        assertEquals(10, store.candleCount)
        assertEquals(20.0, store.lastCandle!!.close)

        // 大于末根：追加
        val append = store.upsertLatest(c(1010L, 21.0))
        assertTrue(append is ChangeResult.Accepted && append.appendedCount == 1)
        assertEquals(11, store.candleCount)

        // 小于末根：明确拒绝，不能隐式改历史
        val before = store.snapshot()
        val reject = store.upsertLatest(c(1005L, 999.0))
        assertTrue(reject is ChangeResult.Rejected && reject.reason == RejectReason.TIMESTAMP_BEFORE_LATEST)
        assertEquals(before, store.snapshot(), "拒绝后快照完全一致")
    }

    // ---------- A-04：混合操作顺序与回执一致 ----------

    @Test
    fun `替换追加Reset混合顺序回执一致且无跨Reset污染`() {
        val store = ChartDataStore()
        assertTrue(store.revision == 0L && store.isEmpty)

        val r1 = store.reset(key, listOf(c(100), c(101), c(102)))
        assertTrue(r1 is ChangeResult.Accepted && r1.revision == 1L)
        val session1 = store.sessionId

        val r2 = store.replaceLatest(c(102, 11.0))
        assertTrue(r2 is ChangeResult.Accepted && r2.revision == 2L && r2.replacedLatest)

        val r3 = store.appendBatch(listOf(c(103), c(104)))
        assertTrue(r3 is ChangeResult.Accepted && r3.revision == 3L && r3.appendedCount == 2)
        assertEquals(listOf(100L, 101L, 102L, 103L, 104L), store.candlesView().map { it.timestamp })

        // 失败插入不应推进 revision
        val bad = store.appendBatch(listOf(c(104)))
        assertTrue(bad is ChangeResult.Rejected)
        assertEquals(3L, store.revision)

        // Reset 开启新会话
        val r4 = store.reset(key, listOf(c(200), c(201)))
        assertTrue(r4 is ChangeResult.Accepted && r4.revision == 4L)
        assertEquals(session1 + 1, store.sessionId, "Reset 必须产生新 sessionId")
        assertEquals(2, store.candleCount, "Reset 替换全部数据，旧数据不残留")
    }

    // ---------- A-05：分页边界与容量 ----------

    @Test
    fun `前插边界完全重复幂等去重`() {
        val store = storeWithData() // 1000..1009，close = 10.0..19.0
        val revBefore = store.revision
        // 新数据 [998..999] + 完全重复 [1000..1009]
        val batch = (998..1009).map { c(it.toLong(), 10.0 + (it - 1000)) }
        val r = store.prependHistory(batch)
        assertTrue(r is ChangeResult.Accepted)
        assertEquals(2, r.prependedCount, "只前插 998、999 两根")
        assertEquals(12, store.candleCount)
        assertEquals(revBefore + 1, store.revision)

        // 再次提交完全相同批次 → 幂等，无 revision 变化
        val again = store.prependHistory(batch)
        assertTrue(again is ChangeResult.Accepted && again.prependedCount == 0)
        assertEquals(revBefore + 1, store.revision, "幂等去重不推进 revision")
    }

    @Test
    fun `前插边界同时间戳内容不同返回冲突`() {
        val store = storeWithData() // 1000..1009, close=10..19
        val conflict = (998..1001).map { c(it.toLong(), 55.0) } // 1000、1001 内容不同
        val r = store.prependHistory(conflict)
        assertTrue(r is ChangeResult.Rejected && r.reason == RejectReason.HISTORY_CONFLICT)
        assertEquals(10, store.candleCount, "冲突被拒绝，数据不变")
    }

    @Test
    fun `容量超限明确拒绝且不静默裁剪`() {
        val store = ChartDataStore(maxCandleCount = 5)
        store.reset(key, (0 until 5).map { c(100L + it) })
        val r = store.appendBatch(listOf(c(105), c(106)))
        assertTrue(r is ChangeResult.Rejected && r.reason == RejectReason.CAPACITY_EXCEEDED)
        assertEquals(5, store.candleCount, "超限不追加不裁剪")

        val up = store.upsertLatest(c(106))
        assertTrue(up is ChangeResult.Rejected && up.reason == RejectReason.CAPACITY_EXCEEDED)

        val pre = store.prependHistory((95 until 100).map { c(it.toLong()) })
        assertTrue(pre is ChangeResult.Rejected && pre.reason == RejectReason.CAPACITY_EXCEEDED)
        assertEquals(5, store.candleCount)
    }

    // ---------- 前插保持既有数据位置（补历史锚点前提） ----------

    @Test
    fun `前插后既有根的时间戳序列与索引平移可预期`() {
        val store = storeWithData() // 1000..1009
        val oldTs = store.candlesView().map { it.timestamp }
        store.prependHistory((990 until 1000).map { c(it.toLong()) })
        val after = store.candlesView()
        assertEquals(oldTs, after.subList(10, 20).map { it.timestamp }, "既有数据相对次序不变，仅整体平移 10")
        assertEquals((990 until 1010).map { it.toLong() }, after.map { it.timestamp })
    }

    @Test
    fun `校验失败的批次被拒绝且整次提交不改变原状态`() {
        val store = storeWithData()
        val before = store.snapshot()
        val bad = listOf(c(1005), c(1004)) // 逆序
        val r = store.appendBatch(bad)
        assertTrue(r is ChangeResult.Rejected && r.reason == RejectReason.VALIDATION_FAILED)
        assertEquals(before, store.snapshot())
    }

    @Test
    fun `空数据状态合法且快照一致`() {
        val store = ChartDataStore()
        val r = store.reset(key, emptyList())
        assertTrue(r is ChangeResult.Accepted)
        assertEquals(0, store.candleCount)
        assertEquals(null, store.lastTimestamp)
        val snap = store.snapshot()
        assertEquals(0, snap.candles.size)
        assertEquals(key, snap.marketKey)
    }
}
