package io.lkanvas.core.store

import io.lkanvas.core.data.Candle
import io.lkanvas.core.data.CandleValidationIssue
import io.lkanvas.core.data.CandleValidator

/**
 * 市场/品种/周期/复权口径标识（PRD 4.1 MarketKey）。
 * 用于隔离数据与存档；相同 MarketKey 重新加载也会产生新的 sessionId。
 */
data class MarketKey(
    val symbol: String,
    val period: String,
    val adjust: String = "none",
)

/** 数据操作类型（PRD R-02 基础操作）。 */
enum class DataOp { RESET, REPLACE_LATEST, UPSERT_LATEST, APPEND_BATCH, PREPEND_HISTORY, PATCH_HISTORY }

/** 拒绝原因（明确错误，不静默修正）。 */
enum class RejectReason {
    /** candle/批次校验失败，详见 issues */
    VALIDATION_FAILED,

    /** 空批次（Reset 允许空列表，其余操作不允许） */
    EMPTY_BATCH,

    /** 当前无数据，操作需要已有数据 */
    NO_DATA,

    /** ReplaceLatest 时间戳与末根不一致 */
    TIMESTAMP_MISMATCH,

    /** 时间戳小于末根（upsert 不允许隐式改历史） */
    TIMESTAMP_BEFORE_LATEST,

    /** Prepend 边界同时间戳但内容不同 */
    HISTORY_CONFLICT,

    /** 超出容量上限（不静默裁剪） */
    CAPACITY_EXCEEDED,
}

/** 变更回执：每次提交必得其一；被拒绝时数据与 revision 均不变（原子性）。 */
sealed interface ChangeResult {
    data class Accepted(
        val sessionId: Long,
        val revision: Long,
        val op: DataOp,
        /** Append/Upsert 追加的根数 */
        val appendedCount: Int = 0,
        /** Prepend 实际前插的根数（边界去重后） */
        val prependedCount: Int = 0,
        /** 是否替换了末根 */
        val replacedLatest: Boolean = false,
    ) : ChangeResult

    data class Rejected(
        val op: DataOp,
        val reason: RejectReason,
        val issues: List<CandleValidationIssue> = emptyList(),
        val detail: String = "",
    ) : ChangeResult
}

/** 版本一致的数据快照（RenderSnapshot 的数据部分）。 */
data class DataSnapshot(
    val sessionId: Long,
    val revision: Long,
    val marketKey: MarketKey?,
    val candles: List<Candle>,
)

/**
 * 串行数据变更存储（PRD R-02）。
 *
 * 线程模型：本类非线程安全，设计为在单一受控串行上下文中调用（直接同步调用或由
 * 上层单消费者协程驱动，见 ComputePipeline）；宿主并发提交必须经串行队列。
 *
 * 语义：
 * - Reset：整体替换并开启新会话；空列表合法（空数据状态）。
 * - ReplaceLatest：仅替换与末根时间戳相同的数据，否则明确拒绝。
 * - UpsertLatest（实时）：等于末根时间戳→替换末根；大于→追加一根；小于→明确拒绝。
 * - AppendBatch：全部大于末根时间戳的批量追加。
 * - PrependHistory：补充更早历史；边界重叠的完全相同 candle 幂等去重，
 *   同时间戳内容不同返回冲突；不改变已有数据的相对位置（补历史锚点保持）。
 * - 容量：默认 100,000 根，超限明确拒绝，禁止自动删除历史。
 * - 校验失败或拒绝时，数据与 revision 完全不变。
 */
class ChartDataStore(
    private val maxCandleCount: Int = DEFAULT_MAX_CANDLE_COUNT,
) {

    private val candles = ArrayList<Candle>()
    private var sessionSerial = 0L
    private var revisionSerial = 0L

    var marketKey: MarketKey? = null
        private set

    /** 当前会话 ID：每次 Reset 产生新值（PRD R-03）。 */
    var sessionId: Long = 0L
        private set

    /** 已接受的数据变更版本（PRD 4.1 Revision）。 */
    val revision: Long get() = revisionSerial

    val candleCount: Int get() = candles.size
    val isEmpty: Boolean get() = candles.isEmpty()
    val firstTimestamp: Long? get() = candles.firstOrNull()?.timestamp
    val lastTimestamp: Long? get() = candles.lastOrNull()?.timestamp
    val lastCandle: Candle? get() = candles.lastOrNull()

    /** 不可变快照（拷贝；调用方安全持有）。 */
    fun snapshot(): DataSnapshot = DataSnapshot(
        sessionId = sessionId,
        revision = revision,
        marketKey = marketKey,
        candles = candles.toList(),
    )

    /** 只读视图（不拷贝；仅保证在串行上下文内消费）。 */
    fun candlesView(): List<Candle> = candles

    /**
     * 显式纠正已存在历史值（PRD R-02 PatchHistory）：
     * - 每个 patch 的时间戳必须已存在（二分定位），否则明确拒绝；
     * - patch 自身须通过 candle 校验；整批原子（任一失败数据不变）；
     * - 不改变数量与时间戳序列，仅替换内容；revision 递增。
     */
    fun patchHistory(patches: List<Candle>): ChangeResult {
        if (patches.isEmpty()) {
            return ChangeResult.Rejected(DataOp.PATCH_HISTORY, RejectReason.EMPTY_BATCH)
        }
        if (candles.isEmpty()) {
            return ChangeResult.Rejected(DataOp.PATCH_HISTORY, RejectReason.NO_DATA)
        }
        // 先全部定位与校验，再统一替换（原子性）
        data class Located(val index: Int, val patch: Candle)
        val located = ArrayList<Located>(patches.size)
        for (p in patches) {
            val issue = CandleValidator.validateBatch(listOf(p))
            if (issue.isNotEmpty()) {
                return ChangeResult.Rejected(DataOp.PATCH_HISTORY, RejectReason.VALIDATION_FAILED, issue)
            }
            val index = indexOfTimestampExact(p.timestamp)
                ?: return ChangeResult.Rejected(
                    DataOp.PATCH_HISTORY,
                    RejectReason.TIMESTAMP_MISMATCH,
                    detail = "时间戳 ${p.timestamp} 不存在于当前数据",
                )
            located += Located(index, p)
        }
        for (l in located) candles[l.index] = l.patch
        return ChangeResult.Accepted(sessionId, ++revisionSerial, DataOp.PATCH_HISTORY)
    }

    private fun indexOfTimestampExact(timestamp: Long): Int? {
        var lo = 0
        var hi = candles.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val ts = candles[mid].timestamp
            when {
                ts == timestamp -> return mid
                ts < timestamp -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        return null
    }

    fun reset(key: MarketKey? = marketKey, newCandles: List<Candle>): ChangeResult {
        val issues = CandleValidator.validateBatch(newCandles)
        if (issues.isNotEmpty()) {
            return ChangeResult.Rejected(DataOp.RESET, RejectReason.VALIDATION_FAILED, issues)
        }
        if (newCandles.size > maxCandleCount) {
            return ChangeResult.Rejected(DataOp.RESET, RejectReason.CAPACITY_EXCEEDED, detail = "数量 ${newCandles.size} 超上限 $maxCandleCount")
        }
        candles.clear()
        candles.addAll(newCandles)
        marketKey = key
        sessionId = ++sessionSerial
        val rev = ++revisionSerial
        return ChangeResult.Accepted(sessionId, rev, DataOp.RESET, appendedCount = newCandles.size)
    }

    fun replaceLatest(candle: Candle): ChangeResult {
        val last = candles.lastOrNull()
            ?: return ChangeResult.Rejected(DataOp.REPLACE_LATEST, RejectReason.NO_DATA)
        if (candle.timestamp != last.timestamp) {
            return ChangeResult.Rejected(
                DataOp.REPLACE_LATEST,
                RejectReason.TIMESTAMP_MISMATCH,
                detail = "新时间戳 ${candle.timestamp} != 末根 ${last.timestamp}",
            )
        }
        val single = CandleValidator.validateBatch(listOf(candle))
        if (single.isNotEmpty()) {
            return ChangeResult.Rejected(DataOp.REPLACE_LATEST, RejectReason.VALIDATION_FAILED, single)
        }
        candles[candles.size - 1] = candle
        val rev = ++revisionSerial
        return ChangeResult.Accepted(sessionId, rev, DataOp.REPLACE_LATEST, replacedLatest = true)
    }

    fun upsertLatest(candle: Candle): ChangeResult {
        val last = candles.lastOrNull()
            ?: return ChangeResult.Rejected(DataOp.UPSERT_LATEST, RejectReason.NO_DATA)
        val single = CandleValidator.validateBatch(listOf(candle))
        if (single.isNotEmpty()) {
            return ChangeResult.Rejected(DataOp.UPSERT_LATEST, RejectReason.VALIDATION_FAILED, single)
        }
        return when {
            candle.timestamp == last.timestamp -> {
                candles[candles.size - 1] = candle
                ChangeResult.Accepted(sessionId, ++revisionSerial, DataOp.UPSERT_LATEST, replacedLatest = true)
            }
            candle.timestamp > last.timestamp -> {
                if (candles.size + 1 > maxCandleCount) {
                    return ChangeResult.Rejected(
                        DataOp.UPSERT_LATEST,
                        RejectReason.CAPACITY_EXCEEDED,
                        detail = "现有 ${candles.size} + 追加 1 超上限 $maxCandleCount",
                    )
                }
                candles.add(candle)
                ChangeResult.Accepted(sessionId, ++revisionSerial, DataOp.UPSERT_LATEST, appendedCount = 1)
            }
            else -> ChangeResult.Rejected(
                DataOp.UPSERT_LATEST,
                RejectReason.TIMESTAMP_BEFORE_LATEST,
                detail = "时间戳 ${candle.timestamp} 小于末根 ${last.timestamp}，不能隐式改历史",
            )
        }
    }

    fun appendBatch(newCandles: List<Candle>): ChangeResult {
        if (newCandles.isEmpty()) {
            return ChangeResult.Rejected(DataOp.APPEND_BATCH, RejectReason.EMPTY_BATCH)
        }
        val lastTs = lastTimestamp
        val issues = CandleValidator.validateBatch(newCandles, lastTimestamp = lastTs)
        if (issues.isNotEmpty()) {
            return ChangeResult.Rejected(DataOp.APPEND_BATCH, RejectReason.VALIDATION_FAILED, issues)
        }
        if (candles.size + newCandles.size > maxCandleCount) {
            return ChangeResult.Rejected(
                DataOp.APPEND_BATCH,
                RejectReason.CAPACITY_EXCEEDED,
                detail = "现有 ${candles.size} + 追加 ${newCandles.size} 超上限 $maxCandleCount",
            )
        }
        candles.addAll(newCandles)
        return ChangeResult.Accepted(sessionId, ++revisionSerial, DataOp.APPEND_BATCH, appendedCount = newCandles.size)
    }

    fun prependHistory(newCandles: List<Candle>): ChangeResult {
        if (newCandles.isEmpty()) {
            return ChangeResult.Rejected(DataOp.PREPEND_HISTORY, RejectReason.EMPTY_BATCH)
        }
        if (candles.isEmpty()) {
            // 无数据时前插等价于填充初始数据（不新开会话）
            val issues = CandleValidator.validateBatch(newCandles)
            if (issues.isNotEmpty()) {
                return ChangeResult.Rejected(DataOp.PREPEND_HISTORY, RejectReason.VALIDATION_FAILED, issues)
            }
            if (newCandles.size > maxCandleCount) {
                return ChangeResult.Rejected(DataOp.PREPEND_HISTORY, RejectReason.CAPACITY_EXCEEDED, detail = "数量 ${newCandles.size} 超上限 $maxCandleCount")
            }
            candles.addAll(newCandles)
            return ChangeResult.Accepted(sessionId, ++revisionSerial, DataOp.PREPEND_HISTORY, prependedCount = newCandles.size)
        }
        // 批次内部合法性
        val issues = CandleValidator.validateBatch(newCandles)
        if (issues.isNotEmpty()) {
            return ChangeResult.Rejected(DataOp.PREPEND_HISTORY, RejectReason.VALIDATION_FAILED, issues)
        }
        // 从批次尾部向前剥离与既有头部重叠的时间戳：完全相同→去重；不同→冲突
        var cut = newCandles.size
        val existingByIdx = HashMap<Long, Candle>(minOf(newCandles.size, 64))
        // 只需对既有头部一段建立索引（重叠区必然在头部）
        var overlapHint = 0
        val firstExistingTs = candles.first().timestamp
        while (overlapHint < candles.size && candles[overlapHint].timestamp <= newCandles.last().timestamp) {
            existingByIdx[candles[overlapHint].timestamp] = candles[overlapHint]
            overlapHint++
        }
        while (cut > 0) {
            val c = newCandles[cut - 1]
            val existing = existingByIdx[c.timestamp] ?: break
            if (existing != c) {
                return ChangeResult.Rejected(
                    DataOp.PREPEND_HISTORY,
                    RejectReason.HISTORY_CONFLICT,
                    detail = "时间戳 ${c.timestamp} 与既有数据重叠但内容不同（批次位置 ${cut - 1}）",
                )
            }
            cut--
        }
        val toInsert = newCandles.subList(0, cut)
        if (toInsert.isEmpty()) {
            // 边界完全重复：幂等成功，不改变数据也不推进 revision（避免无谓重算）
            return ChangeResult.Accepted(sessionId, revision, DataOp.PREPEND_HISTORY, prependedCount = 0)
        }
        // 前插部分必须整体早于既有首根
        if (toInsert.last().timestamp >= firstExistingTs) {
            return ChangeResult.Rejected(
                DataOp.PREPEND_HISTORY,
                RejectReason.VALIDATION_FAILED,
                detail = "前插区间与既有数据时间戳交叉且存在缺口外的时间戳 ${toInsert.last().timestamp}",
            )
        }
        if (candles.size + toInsert.size > maxCandleCount) {
            return ChangeResult.Rejected(
                DataOp.PREPEND_HISTORY,
                RejectReason.CAPACITY_EXCEEDED,
                detail = "现有 ${candles.size} + 前插 ${toInsert.size} 超上限 $maxCandleCount",
            )
        }
        candles.addAll(0, toInsert)
        return ChangeResult.Accepted(sessionId, ++revisionSerial, DataOp.PREPEND_HISTORY, prependedCount = toInsert.size)
    }

    companion object {
        const val DEFAULT_MAX_CANDLE_COUNT: Int = 100_000
    }
}
