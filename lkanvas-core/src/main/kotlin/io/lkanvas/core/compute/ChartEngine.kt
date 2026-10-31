package io.lkanvas.core.compute

import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.ChangeResult
import io.lkanvas.core.store.DataOp
import io.lkanvas.core.store.RejectReason
import io.lkanvas.core.store.ChartDataStore
import io.lkanvas.core.store.DataSnapshot
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** 数据变更请求（经引擎串行队列提交到 [ChartDataStore]）。 */
/**
 * 数据变更请求。会话归属门禁在门面 dataEpoch（Q01，第四轮复审 2026-10-30）：
 * 接受时点身份捕获/复核，不再经由引擎字段（旧机制读协程运行时状态，ABA 与
 * 首推场景均不成立）。
 */
sealed interface ChangeRequest {
    /**
     * R7-01（第七轮复审 2026-10-31）：**提交归属**——请求所属的 INIT 尝试代次
     * （[Long.MIN_VALUE] = 不受归属门禁约束的内部/无主请求）。INIT 最终建会话
     * 失败（Reset 被拒）时，同代次已入队的后续动作在引擎处理时（FIFO 于该
     * Reset 之后）被拒——失败会话的已入队推送不再串写旧会话快照。
     */
    data class Reset(val key: MarketKey?, val candles: List<Candle>, val initEpoch: Long = Long.MIN_VALUE) : ChangeRequest
    data class ReplaceLatest(val candle: Candle) : ChangeRequest
    data class UpsertLatest(val candle: Candle, val initEpoch: Long = Long.MIN_VALUE) : ChangeRequest
    data class AppendBatch(val candles: List<Candle>, val initEpoch: Long = Long.MIN_VALUE) : ChangeRequest
    data class PrependHistory(val candles: List<Candle>, val initEpoch: Long = Long.MIN_VALUE) : ChangeRequest

    /** 显式纠正已存在历史值（R-02 PatchHistory）。 */
    data class PatchHistory(val patches: List<Candle>) : ChangeRequest
}

/** 已接受变更摘要（快照事件附带，视口锚点维护用）。 */
data class AppliedChange(
    val op: DataOp,
    val appendedCount: Int,
    val prependedCount: Int,
    val replacedLatest: Boolean,
)

/**
 * 计算任务票据（PRD R-03）：任务发出时在串行上下文捕获，提交结果时校验。
 * 任意字段过期（会话切换、数据版本回退、参数变化）结果即被丢弃。
 */
data class ComputeTicket(
    val instanceId: String,
    val sessionId: Long,
    val dataRevision: Long,
    val configVersion: Long,
)

/** 结果被门禁丢弃的原因（诊断用，PRD R-15/A-44）。 */
enum class DropReason { SESSION_STALE, CONFIG_STALE, DATA_STALE }

/** 引擎内部事件（全部在单消费者串行上下文处理）。 */
private sealed interface EngineEvent {
    data class Change(val request: ChangeRequest, val reply: CompletableDeferred<ChangeResult>) : EngineEvent
    data class ConfigChanged(val instanceId: String, val reply: CompletableDeferred<ComputeTicket>) : EngineEvent
    data class ComputeStart(
        val instanceId: String,
        val onStart: (ticket: ComputeTicket, candles: List<Candle>) -> Unit,
    ) : EngineEvent

    data class ComputeDone(val ticket: ComputeTicket, val payload: Any?) : EngineEvent
}

/**
 * 图表引擎：数据变更串行化 + 异步计算结果门禁（PRD R-02/R-03）。
 *
 * 线程模型：
 * - [run] 是唯一消费者，在调用方给定的协程中串行处理全部事件；
 * - 数据写入、票据生成、结果提交都发生在该串行上下文（计算线程不直接改可观察状态）；
 * - 计算本体通过 [launchCompute] 在 [computeDispatcher] 上运行，完成后把结果投回事件队列。
 *
 * 背压：事件队列容量 [queueCapacity]；[apply] 满载时挂起（背压），
 * [tryApply] 满载时返回 null（明确拒绝，不静默丢失）。
 *
 * 门禁（[isCurrent]）：sessionId 必须等于当前会话；configVersion 必须等于该实例当前
 * 配置版本；dataRevision 不得早于该实例已应用的版本（乱序旧结果丢弃）。
 */
class ChartEngine(
    private val scope: CoroutineScope,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    queueCapacity: Int = 128,
    private val store: ChartDataStore = ChartDataStore(),
) {

    private val events = Channel<EngineEvent>(queueCapacity)

    /** 实例配置版本（参数/样式定义变化时 bump）。只在串行上下文读写。 */
    private val configVersions = HashMap<String, Long>()

    /** 每个实例已应用结果的数据版本（用于丢弃乱序旧结果）。 */
    private val appliedDataRevision = HashMap<String, Long>()

    @Volatile
    private var currentSnapshot: DataSnapshot = DataSnapshot(0, 0, null, emptyList())

    /** 最新快照（不可变引用，任意线程可读）。 */
    val snapshot: DataSnapshot get() = currentSnapshot

    /** 门禁丢弃计数（诊断）。 */
    val droppedResults = AtomicInteger(0)

    /** 最近一次丢弃原因（诊断/测试）。 */
    @Volatile
    var lastDropReason: DropReason? = null
        private set

    /** 快照监听（在串行上下文回调；不得阻塞）；附带本次接受的变更摘要。 */
    var onSnapshot: ((snapshot: DataSnapshot, change: AppliedChange?) -> Unit)? = null

    /** 结果被应用时回调（串行上下文；payload 已通过门禁）。 */
    var onResultApplied: ((ticket: ComputeTicket, payload: Any?) -> Unit)? = null

    /** 结果被丢弃时回调（串行上下文；诊断用）。 */
    var onResultDropped: ((ticket: ComputeTicket, reason: DropReason) -> Unit)? = null

    /** 最近一次接受的变更（诊断）。 */
    var lastApplied: AppliedChange? = null
        private set

    /** 挂起式提交数据变更：队列满载时背压挂起。 */
    suspend fun apply(request: ChangeRequest): ChangeResult {
        val reply = CompletableDeferred<ChangeResult>()
        events.send(EngineEvent.Change(request, reply))
        return reply.await()
    }

    /** 非挂起提交：队列满载返回 null（明确拒绝），由宿主决定重试策略。 */
    fun tryApply(request: ChangeRequest): CompletableDeferred<ChangeResult>? {
        val reply = CompletableDeferred<ChangeResult>()
        return if (events.trySend(EngineEvent.Change(request, reply)).isSuccess) reply else null
    }

    /**
     * bump 实例配置版本（参数变化等）：必须在串行上下文生效并返回新票据，
     * 保证旧配置的在途结果在提交时被 [DropReason.CONFIG_STALE] 丢弃。
     */
    suspend fun bumpConfig(instanceId: String): ComputeTicket {
        val reply = CompletableDeferred<ComputeTicket>()
        events.send(EngineEvent.ConfigChanged(instanceId, reply))
        return reply.await()
    }

    /**
     * 发起一次全量计算：票据与数据在串行上下文捕获后交给 [computeDispatcher]，
     * 计算完成投回事件队列，由串行消费者按门禁决定应用或丢弃。
     *
     * 计算协程运行在引擎 [scope]（调用方取消 scope 即取消全部在途计算；
     * 被取消的结果不会到达队列）。
     *
     * @return 是否成功入队（false = 队列满载明确拒绝）
     */
    fun launchCompute(
        instanceId: String,
        block: suspend (ticket: ComputeTicket, candles: List<Candle>) -> Any?,
    ): Boolean {
        val sent = events.trySend(
            EngineEvent.ComputeStart(instanceId) { ticket, candles ->
                scope.launch(computeDispatcher) {
                    val payload = block(ticket, candles)
                    events.trySend(EngineEvent.ComputeDone(ticket, payload))
                }
            },
        )
        return sent.isSuccess
    }

    /** 引擎主循环：串行消费全部事件直到 [close]。 */
    suspend fun run() {
        for (event in events) {
            when (event) {
                is EngineEvent.Change -> {
                    val result = applyToStore(event.request)
                    event.reply.complete(result)
                    if (result is ChangeResult.Accepted) {
                        currentSnapshot = store.snapshot()
                        lastApplied = AppliedChange(
                            op = result.op,
                            appendedCount = result.appendedCount,
                            prependedCount = result.prependedCount,
                            replacedLatest = result.replacedLatest,
                        )
                        onSnapshot?.invoke(currentSnapshot, lastApplied)
                    }
                }
                is EngineEvent.ConfigChanged -> {
                    val next = (configVersions[event.instanceId] ?: 0L) + 1
                    configVersions[event.instanceId] = next
                    event.reply.complete(currentTicket(event.instanceId))
                }
                is EngineEvent.ComputeStart -> {
                    event.onStart(currentTicket(event.instanceId), store.candlesView().toList())
                }
                is EngineEvent.ComputeDone -> {
                    val t = event.ticket
                    when (val reason = staleReason(t)) {
                        null -> {
                            appliedDataRevision[t.instanceId] = t.dataRevision
                            onResultApplied?.invoke(t, event.payload)
                        }
                        else -> {
                            droppedResults.incrementAndGet()
                            lastDropReason = reason
                            onResultDropped?.invoke(t, reason)
                        }
                    }
                }
            }
        }
    }

    fun close() {
        events.close()
    }

    private fun staleReason(ticket: ComputeTicket): DropReason? = when {
        ticket.sessionId != store.sessionId -> DropReason.SESSION_STALE
        ticket.configVersion != (configVersions[ticket.instanceId] ?: 0L) -> DropReason.CONFIG_STALE
        ticket.dataRevision < (appliedDataRevision[ticket.instanceId] ?: Long.MIN_VALUE) -> DropReason.DATA_STALE
        else -> null
    }

    private fun currentTicket(instanceId: String): ComputeTicket = ComputeTicket(
        instanceId = instanceId,
        sessionId = store.sessionId,
        dataRevision = store.revision,
        configVersion = configVersions[instanceId] ?: 0L,
    )

    /** R7-01：失败 INIT 尝试的代次水位（Reset 拒绝时抬升；FIFO 保证其后同代次动作在此之后处理）。 */
    private var rejectedInitWatermark = Long.MIN_VALUE

    private fun sessionRejected(op: DataOp): ChangeResult.Rejected =
        ChangeResult.Rejected(op, RejectReason.SESSION_MISMATCH, detail = "R7-01：所属 INIT 尝试建会话失败（提交归属门禁）")

    private fun applyToStore(request: ChangeRequest): ChangeResult = when (request) {
            is ChangeRequest.Reset -> {
                val r = store.reset(request.key, request.candles)
                if (r is ChangeResult.Accepted) {
                    // 新会话：全部旧结果作废
                    appliedDataRevision.clear()
                } else {
                    // R7-01：Reset 最终拒绝 = 该 INIT 尝试建会话失败——抬升拒绝
                    // 水位，同代次已入队后续动作在引擎处理时被拒（宿主协程入口
                    // 的水位门禁不够：Reset 经引擎 channel 挂起等待时，后续
                    // Update 已先行入队并被处理）
                    rejectedInitWatermark = maxOf(rejectedInitWatermark, request.initEpoch)
                }
                r
            }
            is ChangeRequest.ReplaceLatest -> store.replaceLatest(request.candle)
            is ChangeRequest.UpsertLatest ->
                if (request.initEpoch > Long.MIN_VALUE && request.initEpoch <= rejectedInitWatermark) sessionRejected(DataOp.UPSERT_LATEST)
                else store.upsertLatest(request.candle)
            is ChangeRequest.AppendBatch ->
                if (request.initEpoch > Long.MIN_VALUE && request.initEpoch <= rejectedInitWatermark) sessionRejected(DataOp.APPEND_BATCH)
                else store.appendBatch(request.candles)
            is ChangeRequest.PrependHistory ->
                if (request.initEpoch > Long.MIN_VALUE && request.initEpoch <= rejectedInitWatermark) sessionRejected(DataOp.PREPEND_HISTORY)
                else store.prependHistory(request.candles)
            is ChangeRequest.PatchHistory -> store.patchHistory(request.patches)
    }
}
