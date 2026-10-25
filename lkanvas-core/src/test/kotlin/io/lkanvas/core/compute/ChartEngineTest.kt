package io.lkanvas.core.compute

import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.DataSnapshot
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartEngineTest {

    private fun c(ts: Long, close: Double = 10.0) =
        Candle(ts, close, close + 1.0, close - 1.0, close, 100.0)

    private val keyA = MarketKey("AAA", "1m")
    private val keyB = MarketKey("BBB", "1m")

    /** 统一虚拟时间运行引擎主循环直至 [body] 返回。 */
    private fun runEngine(body: suspend (ChartEngine) -> Unit) = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val engine = ChartEngine(
            scope = CoroutineScope(dispatcher),
            computeDispatcher = dispatcher,
        )
        val loop = launch(dispatcher) { engine.run() }
        try {
            withTimeout(5_000) { body(engine) }
        } finally {
            engine.close()
            loop.cancel()
        }
    }

    // ---------- A-07：旧会话结果即使晚到也被丢弃 ----------

    @Test
    fun `A品种请求晚于B返回时最终始终为B`() = runEngine { engine ->
        // 切到 A 并发起计算，但计算被人为延迟
        engine.apply(ChangeRequest.Reset(keyA, listOf(c(100), c(101))))
        val dropped = CompletableDeferred<ComputeTicket>()
        engine.onResultDropped = { t, _ -> if (!dropped.isCompleted) dropped.complete(t) }
        val applied = mutableListOf<ComputeTicket>()
        engine.onResultApplied = { t, _ -> applied += t }

        engine.launchCompute("MA") { ticket, _ ->
            kotlinx.coroutines.delay(100) // A 的计算很慢（虚拟时间）
            ticket
        }
        // 计算未完成时切到 B
        engine.apply(ChangeRequest.Reset(keyB, listOf(c(200), c(201))))
        kotlinx.coroutines.delay(300)

        assertTrue(dropped.isCompleted, "旧会话结果必须被丢弃")
        assertEquals(DropReason.SESSION_STALE, engine.lastDropReason)
        assertTrue(applied.isEmpty(), "旧会话结果不得被应用")
    }

    @Test
    fun `切回A也不接纳旧session的结果`() = runEngine { engine ->
        engine.apply(ChangeRequest.Reset(keyA, listOf(c(100))))
        val sessionBefore = engine.snapshot.sessionId

        engine.launchCompute("MA") { ticket, _ ->
            kotlinx.coroutines.delay(100)
            ticket
        }
        engine.apply(ChangeRequest.Reset(keyB, listOf(c(200))))
        kotlinx.coroutines.delay(30)
        // 切回 A（产生更新的 sessionId）
        engine.apply(ChangeRequest.Reset(keyA, listOf(c(300))))
        assertTrue(engine.snapshot.sessionId > sessionBefore, "切回 A 是新会话，不复用旧 session")

        kotlinx.coroutines.delay(300)
        assertEquals(1, engine.droppedResults.get(), "旧 session 结果仍被丢弃")
    }

    // ---------- A-08：参数变化与计算延迟交错 ----------

    @Test
    fun `参数变化后旧配置结果被拒绝且不冒充当前值`() = runEngine { engine ->
        engine.apply(ChangeRequest.Reset(keyA, listOf(c(100), c(101), c(102))))
        engine.bumpConfig("MA") // v1
        val applied = mutableListOf<String>()
        engine.onResultApplied = { _, payload -> applied += payload as String }

        // v1 配置下的慢计算
        engine.launchCompute("MA") { _, _ ->
            kotlinx.coroutines.delay(120)
            "v1-result"
        }
        kotlinx.coroutines.delay(30) // 计算进行中
        val newTicket = engine.bumpConfig("MA") // v2：参数变化
        assertEquals(2L, newTicket.configVersion)

        kotlinx.coroutines.delay(300)
        assertTrue(applied.isEmpty(), "旧配置结果被 CONFIG_STALE 丢弃，不存在旧数值冒充当前值")
        assertEquals(1, engine.droppedResults.get())
    }

    @Test
    fun `新配置结果正常应用`() = runEngine { engine ->
        engine.apply(ChangeRequest.Reset(keyA, listOf(c(100))))
        engine.bumpConfig("MA")
        val applied = mutableListOf<String>()
        engine.onResultApplied = { _, payload -> applied += payload as String }

        engine.launchCompute("MA") { _, _ -> "v2-result" }
        kotlinx.coroutines.delay(100)
        assertEquals(listOf("v2-result"), applied)
        assertEquals(0, engine.droppedResults.get())
    }

    // ---------- A-09：计算中 Reset 为空，旧结果不得复用 ----------

    @Test
    fun `计算中Reset为空则旧结果被丢弃`() = runEngine { engine ->
        engine.apply(ChangeRequest.Reset(keyA, listOf(c(100), c(101))))
        engine.launchCompute("MA") { _, _ ->
            kotlinx.coroutines.delay(120)
            "old-data-result"
        }
        kotlinx.coroutines.delay(30)
        engine.apply(ChangeRequest.Reset(keyA, emptyList())) // 新会话、空数据
        val applied = mutableListOf<Any?>()
        engine.onResultApplied = { _, p -> applied += p }

        kotlinx.coroutines.delay(300)
        assertTrue(applied.isEmpty(), "空会话后旧指标结果不得复用")
        assertEquals(1, engine.droppedResults.get())
    }

    @Test
    fun `乱序旧版本数据结果被丢弃而较新结果可应用`() = runEngine { engine ->
        engine.apply(ChangeRequest.Reset(keyA, listOf(c(100))))
        val applied = mutableListOf<Long>()
        engine.onResultApplied = { t, _ -> applied += t.dataRevision }

        // 第一次计算（rev1）慢
        engine.launchCompute("MA") { ticket, _ ->
            kotlinx.coroutines.delay(200)
            "rev-${ticket.dataRevision}"
        }
        // 数据推进到 rev2 后再发起快计算
        engine.apply(ChangeRequest.UpsertLatest(c(101)))
        engine.launchCompute("MA") { ticket, _ -> "rev-${ticket.dataRevision}" }

        kotlinx.coroutines.delay(400)
        // rev2 已应用；rev1 晚到被 DATA_STALE 丢弃
        assertEquals(listOf(2L), applied)
        assertEquals(1, engine.droppedResults.get())
    }

    // ---------- 快照一致性与背压 ----------

    @Test
    fun `快照始终来自单一串行写入无跨会话混合`() = runEngine { engine ->
        val snapshots = mutableListOf<DataSnapshot>()
        engine.onSnapshot = { snap, _ -> snapshots += snap }

        engine.apply(ChangeRequest.Reset(keyA, listOf(c(100), c(101))))
        engine.apply(ChangeRequest.Reset(keyB, listOf(c(200))))

        assertEquals(2, snapshots.size)
        assertEquals(2, snapshots[0].candles.size)
        assertEquals(1, snapshots[1].candles.size)
        assertEquals(keyB, engine.snapshot.marketKey)
        assertTrue(snapshots[0].sessionId != snapshots[1].sessionId)
        // 每个快照自洽：sessionId/revision 与数据来自同一次串行提交
        snapshots.forEach { s ->
            assertEquals(s.sessionId == snapshots[0].sessionId, s.candles.size == 2)
        }
    }

    @Test
    fun `有界队列满载时tryApply明确拒绝不静默丢失`() = runTest {
        val engine = ChartEngine(CoroutineScope(UnconfinedTestDispatcher()), queueCapacity = 2)
        // 不启动 run()：队列无人消费
        val d1 = engine.tryApply(ChangeRequest.Reset(keyA, listOf(c(100))))
        val d2 = engine.tryApply(ChangeRequest.UpsertLatest(c(101)))
        val d3 = engine.tryApply(ChangeRequest.UpsertLatest(c(102)))
        assertTrue(d1 != null && d2 != null, "未满载应入队")
        assertTrue(d3 == null, "满载必须明确拒绝（返回 null）")
        engine.close()
    }
}
