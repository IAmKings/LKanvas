package io.lkanvas.core.archive

/**
 * 存档恢复门禁（PRD R-12/T-26，A-37/A-39）。
 *
 * 语义：
 * - 恢复进行中（[beginRestore] 之后、[endRestore] 之前）：数据/覆盖物的任何变化
 *   （包括自动创建的空列表 Reset）都**不得**作为「已保存状态」发出保存事件——
 *   否则短暂空状态会覆盖宿主存档（S0 级缺陷）。
 * - 恢复成功后进入正常态：用户编辑/数据变化发保存事件。
 * - 恢复失败（[endRestore](false)）：门禁回到「未恢复」态；**不得**因失败而
 *   发出写回事件（原存档由宿主持有，库不覆盖）。
 * - 空状态 + 未曾恢复过任何内容 + 无用户编辑：不发保存事件（首次打开的
 *   空图表没有可保存的状态）。
 *
 * 线程模型：由串行上下文持有（与 ChartState 快照回调同上下文）。
 */
class RestoreGate {

    /** 门禁状态。 */
    enum class Phase { IDLE, RESTORING, RESTORED }

    var phase: Phase = Phase.IDLE
        private set

    /** 恢复进行中。 */
    val restoring: Boolean get() = phase == Phase.RESTORING

    private var dirtySinceRestore = false

    /** 宿主发起恢复：进入恢复期，期间保存事件被抑制。 */
    fun beginRestore() {
        phase = Phase.RESTORING
        dirtySinceRestore = false
    }

    /**
     * 恢复结束。[success]=false 时不进入已恢复态（不产生保存事件）。
     */
    fun endRestore(success: Boolean) {
        if (phase != Phase.RESTORING) return
        phase = if (success) Phase.RESTORED else Phase.IDLE
        dirtySinceRestore = false
    }

    /**
     * 状态变化时询问是否应发出保存事件。
     * @param hasContent 图表当前是否有内容（数据非空或存在覆盖物）
     */
    fun shouldEmitSave(hasContent: Boolean): Boolean {
        if (restoring) return false
        if (phase == Phase.IDLE && !dirtySinceRestore && !hasContent) return false
        dirtySinceRestore = true
        return true
    }
}
