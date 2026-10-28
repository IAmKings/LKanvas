package io.lkanvas.core.overlay

/**
 * 覆盖物锚点：时间戳 + 数值（PRD R-09：绘图点用数据/时间锚点保存，
 * 不持久化裸数组 index；补历史/缩放/改尺寸后仍锚定原数据）。
 */
data class OverlayPoint(
    val timestamp: Long,
    val value: Double,
) {
    operator fun plus(delta: OverlayPoint) = OverlayPoint(timestamp + delta.timestamp, value + delta.value)
    operator fun minus(other: OverlayPoint) = OverlayPoint(timestamp - other.timestamp, value - other.value)
}

/** 覆盖物生命周期阶段（PRD R-09）。 */
enum class OverlayPhase { IDLE, CREATING, SELECTED, DRAGGING, COMPLETED, REMOVED }

/** 磁吸模式（PRD R-09）。 */
enum class MagnetMode { NORMAL, WEAK, STRONG }

/** 覆盖物实例：稳定 ID、控制点、样式、锁定/可见、层级（PRD R-09）。 */
class OverlayInstance(
    val id: String,
    val templateName: String,
    val points: MutableList<OverlayPoint>,
    var locked: Boolean = false,
    var visible: Boolean = true,
    var zIndex: Int = 0,
    /** 扩展数据（宿主业务标签等）。 */
    val extensionData: MutableMap<String, String> = mutableMapOf(),
    /** 所属 pane（Alpha: main）。 */
    val paneId: String = "main",
) {
    /** 门面 paneId 覆写（createOverlay(paneId) 设置；读取优先于 [paneId]）。 */
    var paneIdOverride: String? = null

    /** 上游 groupId：分组 id（批量操作/filter 用）。 */
    var groupId: String? = null

    /** 上游 currentStep：当前交互步（创建中 = points.size）。 */
    val currentStep: Int get() = points.size

    /** 上游 fixedZLevel：层级是否固定（true=不随选中浮起）。 */
    var fixedZLevel: Boolean = false

    /** 上游 modeSensitivity：磁吸灵敏度像素（默认 8）。 */
    var modeSensitivity: Double = 8.0

    /** 上游 styles：实例级样式覆盖。 */
    var styles: Any? = null

    /** 生效 paneId（override 优先）。 */
    val effectivePaneId: String get() = paneIdOverride ?: paneId

    /** 生命周期阶段（公开只读；变更经 [OverlayManager]）。 */
    var phase: OverlayPhase = OverlayPhase.IDLE
        internal set
}

/** 覆盖物事件（PRD R-09/A-27：开始/完成/取消事件恰好一次）。 */
sealed interface OverlayEvent {
    data class CreateStarted(val id: String, val template: String) : OverlayEvent
    data class CreatePointAdded(val id: String, val pointIndex: Int) : OverlayEvent
    data class Created(val id: String, val template: String) : OverlayEvent
    data class CreateCancelled(val id: String) : OverlayEvent

    /** 连续采样（brush）达到点数上限：终止采样并报告（PRD R-09）。 */
    data class SampleLimitReached(val id: String, val maxPoints: Int) : OverlayEvent
    data class Selected(val id: String) : OverlayEvent
    data object Deselected : OverlayEvent
    data class DragStarted(val id: String, val pointIndex: Int?) : OverlayEvent
    data class DragFinished(val id: String, val pointIndex: Int?) : OverlayEvent
    data class Removed(val id: String) : OverlayEvent
    data class LockedChanged(val id: String, val locked: Boolean) : OverlayEvent
}

/**
 * 覆盖物管理器（每图表一个实例；由串行上下文持有与调用）。
 *
 * 状态机：IDLE →（beginCreate）CREATING →（补齐点数）COMPLETED；
 * 选中 SELECTED → 拖动 DRAGGING → SELECTED；CREATING 可 cancel（恰好一次取消事件）。
 * 锁定实例禁止编辑（加点/拖动），但允许选择与检查。
 */
class OverlayManager {

    private val instances = LinkedHashMap<String, OverlayInstance>()
    private var idSerial = 0L

    /** 创建中实例的目标点数（beginCreate 时记录；continuous 模板为 0）。 */
    private var creatingPointCount: Int = 0

    /** 创建中实例是否为连续采样模板。 */
    private var creatingContinuous = false

    /** 创建中实例的采样上限。 */
    private var creatingMaxPoints = Int.MAX_VALUE

    /** 采样上限事件是否已发（恰好一次）。 */
    private var sampleLimitEmitted = false

    /** 当前创建中的实例 ID。 */
    val creatingId: String? get() = instances.values.firstOrNull { it.phase == OverlayPhase.CREATING }?.id

    /** 当前选中实例 ID。 */
    var selectedId: String? = null
        private set

    /** 事件回调（串行上下文）。 */
    var onEvent: ((OverlayEvent) -> Unit)? = null

    /** 磁吸模式（手势层读取并应用）。 */
    var magnetMode: MagnetMode = MagnetMode.WEAK

    fun all(): List<OverlayInstance> = instances.values.toList()

    fun get(id: String): OverlayInstance? = instances[id]

    // ---------- 创建 ----------

    /** 进入创建模式；已有创建中实例或未知模板返回 null。 */
    fun beginCreate(template: OverlayDefinition): String? {
        if (creatingId != null) return null
        val id = "ov-${++idSerial}"
        val inst = OverlayInstance(id, template.name, mutableListOf())
        inst.phase = OverlayPhase.CREATING
        creatingPointCount = template.pointCount
        creatingContinuous = template.continuous
        creatingMaxPoints = template.maxPoints
        sampleLimitEmitted = false
        instances[id] = inst
        emit(OverlayEvent.CreateStarted(id, template.name))
        return id
    }

    /**
     * 创建中加控制点；补齐模板点数后自动完成（Created 恰好一次）。
     * 连续模板（brush）：达到 [OverlayDefinition.maxPoints] 后拒绝采样并
     * 发出恰好一次 [OverlayEvent.SampleLimitReached]（PRD R-09）。
     * @return 完成返回 true
     */
    fun addCreatePoint(id: String, point: OverlayPoint): Boolean {
        val inst = creating(id) ?: return false
        if (creatingContinuous) {
            if (inst.points.size >= creatingMaxPoints) {
                if (!sampleLimitEmitted) {
                    sampleLimitEmitted = true
                    emit(OverlayEvent.SampleLimitReached(id, creatingMaxPoints))
                }
                return false
            }
            inst.points += point
            emit(OverlayEvent.CreatePointAdded(id, inst.points.size - 1))
            return false
        }
        inst.points += point
        emit(OverlayEvent.CreatePointAdded(id, inst.points.size - 1))
        if (inst.points.size >= creatingPointCount) {
            inst.phase = OverlayPhase.COMPLETED
            creatingPointCount = 0
            emit(OverlayEvent.Created(id, inst.templateName))
            return true
        }
        return false
    }

    /** 连续模板显式完成创建（Created 恰好一次）；至少需要 2 个采样点。 */
    fun finishCreate(id: String): Boolean {
        val inst = creating(id) ?: return false
        if (!creatingContinuous) return false
        if (inst.points.size < 2) return false
        inst.phase = OverlayPhase.COMPLETED
        creatingPointCount = 0
        creatingContinuous = false
        emit(OverlayEvent.Created(id, inst.templateName))
        return true
    }

    /** 取消创建（Cancelled 恰好一次并移除实例）。 */
    fun cancelCreate() {
        val id = creatingId ?: return
        instances.remove(id)
        if (selectedId == id) selectedId = null
        creatingPointCount = 0
        creatingContinuous = false
        emit(OverlayEvent.CreateCancelled(id))
    }

    // ---------- 选择与编辑 ----------

    fun select(id: String?) {
        if (id == null) {
            if (selectedId != null) {
                selectedId = null
                emit(OverlayEvent.Deselected)
            }
            return
        }
        val inst = instances[id] ?: return
        if (inst.phase == OverlayPhase.CREATING) return
        if (selectedId != id) {
            selectedId = id
            inst.phase = OverlayPhase.SELECTED
            emit(OverlayEvent.Selected(id))
        }
    }

    /**
     * 开始拖动（锁定实例拒绝）；[pointIndex] 为 null 表示整体拖动。
     */
    fun beginDrag(id: String, pointIndex: Int?): Boolean {
        val inst = instances[id] ?: return false
        if (inst.locked) return false
        if (inst.phase != OverlayPhase.SELECTED && inst.phase != OverlayPhase.COMPLETED) return false
        inst.phase = OverlayPhase.DRAGGING
        emit(OverlayEvent.DragStarted(id, pointIndex))
        return true
    }

    /**
     * 拖动推进：单点更新为 [to]；整体拖动按增量 [deltaTimestamp]/[deltaValue] 平移全部点。
     */
    fun drag(id: String?, pointIndex: Int?, to: OverlayPoint? = null, deltaTimestamp: Long = 0, deltaValue: Double = 0.0) {
        val inst = id?.let { instances[it] } ?: return
        if (inst.phase != OverlayPhase.DRAGGING) return
        when {
            pointIndex != null && to != null && pointIndex < inst.points.size ->
                inst.points[pointIndex] = to
            pointIndex == null -> {
                for (i in inst.points.indices) {
                    val p = inst.points[i]
                    inst.points[i] = OverlayPoint(p.timestamp + deltaTimestamp, p.value + deltaValue)
                }
            }
        }
    }

    /** 结束拖动（DragFinished 恰好一次）。 */
    fun endDrag(id: String?, pointIndex: Int?) {
        val inst = id?.let { instances[it] } ?: return
        if (inst.phase != OverlayPhase.DRAGGING) return
        inst.phase = OverlayPhase.SELECTED
        emit(OverlayEvent.DragFinished(inst.id, pointIndex))
    }

    fun setLocked(id: String, locked: Boolean) {
        val inst = instances[id] ?: return
        inst.locked = locked
        emit(OverlayEvent.LockedChanged(id, locked))
    }

    fun setVisible(id: String, visible: Boolean) {
        get(id)?.visible = visible
    }

    /** 删除必须由明确操作触发（PRD R-09）。 */
    fun remove(id: String) {
        if (instances.remove(id) != null) {
            if (selectedId == id) selectedId = null
            emit(OverlayEvent.Removed(id))
        }
    }

    /** 删除当前选中（宿主菜单动作）。 */
    fun removeSelected() {
        selectedId?.let { remove(it) }
    }

    /**
     * 从存档恢复已完成绘图（PRD R-12：恢复时机由宿主控制，本方法同步写回；
     * 全部置为 COMPLETED，宿主自行决定未知模板条目的处置）。
     */
    fun restoreFrom(restored: List<OverlayInstance>) {
        restored.forEach { inst ->
            inst.phase = OverlayPhase.COMPLETED
            instances[inst.id] = inst
            // 保持 idSerial 前进避免后续创建冲突
            inst.id.removePrefix("ov-").toLongOrNull()?.let { if (it > idSerial) idSerial = it }
        }
    }

    /** 导出已完成绘图（CREATING/DRAGGING 不导出；不改变任何状态）。 */
    fun exportCompleted(): List<OverlayInstance> =
        instances.values.filter { it.phase == OverlayPhase.COMPLETED || it.phase == OverlayPhase.IDLE || it.phase == OverlayPhase.SELECTED }

    private fun creating(id: String): OverlayInstance? =
        instances[id]?.takeIf { it.phase == OverlayPhase.CREATING }

    private fun emit(event: OverlayEvent) {
        onEvent?.invoke(event)
    }
}
