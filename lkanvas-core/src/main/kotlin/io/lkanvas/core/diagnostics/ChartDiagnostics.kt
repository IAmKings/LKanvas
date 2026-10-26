package io.lkanvas.core.diagnostics

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 运行诊断（PRD R-15/T-30，A-44 基础）：默认关闭零开销；开启后收集
 * session/revision、可见范围、计算耗时、绘制耗时与门禁丢弃计数。
 *
 * - 不记录认证信息，不上传任何数据（仅进程内环形缓冲）。
 * - 关闭时 [record] 直接返回（一次布尔读），不产生分配。
 * - 供宿主诊断入口读取；导出/展示由宿主决定。
 */
class ChartDiagnostics(
    /** 环形缓冲容量（条）。 */
    private val capacity: Int = 256,
) {

    private val enabled = AtomicBoolean(false)

    @PublishedApi
    internal fun isRunning(): Boolean = enabled.get()

    /** 最近一次计算耗时（纳秒；关闭时不更新）。 */
    val lastComputeNanos = AtomicLong(0)

    /** 计算次数计数。 */
    val computeCount = AtomicLong(0)

    /** 最近一次绘制耗时（纳秒）。 */
    val lastRenderNanos = AtomicLong(0)

    /** 绘制帧计数。 */
    val renderCount = AtomicLong(0)

    private val ring = ArrayDeque<Entry>(capacity)

    /** 单条诊断记录。 */
    data class Entry(
        val timestampNanos: Long,
        val sessionId: Long,
        val revision: Long,
        val kind: String,
        val detail: String,
    )

    fun setEnabled(value: Boolean) {
        enabled.set(value)
    }

    fun isEnabled(): Boolean = enabled.get()

    /**
     * 记录一条诊断（关闭时零开销返回）。
     * @param sessionId/revision 当前会话与数据版本
     * @param kind 类别（compute/render/drop/apply…）
     */
    fun record(sessionId: Long, revision: Long, kind: String, detail: String) {
        if (!enabled.get()) return
        synchronized(ring) {
            if (ring.size >= capacity) ring.removeFirst()
            ring.addLast(Entry(System.nanoTime(), sessionId, revision, kind, detail))
        }
    }

    /** 快照（最多最近 capacity 条；宿主诊断入口用）。 */
    fun entries(): List<Entry> = synchronized(ring) { ring.toList() }

    /** 汇总一行（慢计算定位用；PRD A-44「开启可定位 session、revision 和慢插件」）。 */
    fun summary(): String {
        if (!isEnabled()) return "诊断未开启"
        val compute = lastComputeNanos.get() / 1e6
        val render = lastRenderNanos.get() / 1e6
        return "计算 %.2f ms × %d 次；绘制 %.2f ms × %d 帧；缓冲 %d 条".format(
            compute, computeCount.get(), render, renderCount.get(), entries().size,
        )
    }

    /** 计时包装（关闭时直接执行不计时）。 */
    inline fun <T> timeCompute(sessionId: Long, revision: Long, detail: () -> String, block: () -> T): T {
        if (!isRunning()) return block()
        val start = System.nanoTime()
        val result = block()
        val took = System.nanoTime() - start
        lastComputeNanos.set(took)
        computeCount.incrementAndGet()
        record(sessionId, revision, "compute", "${detail()} took=%.2fms".format(took / 1e6))
        return result
    }
}
