package io.lkanvas.core.overlay

import io.lkanvas.core.extension.overlay.SegmentDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OverlayManagerTest {

    private val events = mutableListOf<OverlayEvent>()
    private val manager = OverlayManager().apply {
        onEvent = { events += it }
    }

    private fun p(ts: Long, v: Double) = OverlayPoint(ts, v)

    // ---------- O-01 segment 创建流程 ----------

    @Test
    fun `segment两点创建完成且事件恰好一次`() {
        val id = manager.beginCreate(SegmentDefinition)!!
        assertEquals(1, events.count { it is OverlayEvent.CreateStarted })
        assertTrue(!manager.addCreatePoint(id, p(100, 10.0)), "第一点未完成")
        assertEquals(1, events.count { it is OverlayEvent.CreatePointAdded })
        assertTrue(manager.addCreatePoint(id, p(200, 12.0)), "第二点完成")
        assertEquals(1, events.count { it is OverlayEvent.Created })
        assertEquals(OverlayPhase.COMPLETED, manager.get(id)!!.phase)
        assertEquals(2, manager.get(id)!!.points.size)
    }

    @Test
    fun `取消创建事件恰好一次且实例移除`() {
        val id = manager.beginCreate(SegmentDefinition)!!
        manager.addCreatePoint(id, p(100, 10.0))
        manager.cancelCreate()
        manager.cancelCreate() // 二次取消无事件
        assertEquals(1, events.count { it is OverlayEvent.CreateCancelled })
        assertEquals(null, manager.get(id))
        assertEquals(0, manager.all().size)
    }

    @Test
    fun `已有创建中实例时不能开始新创建`() {
        manager.beginCreate(SegmentDefinition)
        assertEquals(null, manager.beginCreate(SegmentDefinition))
    }

    // ---------- 选择/拖动/锁定（A-27 基础） ----------

    private fun createdSegment(): String {
        val id = manager.beginCreate(SegmentDefinition)!!
        manager.addCreatePoint(id, p(100, 10.0))
        manager.addCreatePoint(id, p(200, 12.0))
        return id
    }

    @Test
    fun `选中拖动单点与整体并结束`() {
        val id = createdSegment()
        manager.select(id)
        assertEquals(1, events.count { it is OverlayEvent.Selected })

        assertTrue(manager.beginDrag(id, pointIndex = 1))
        manager.drag(id, pointIndex = 1, to = p(300, 15.0))
        assertEquals(p(300, 15.0), manager.get(id)!!.points[1])
        assertEquals(p(100, 10.0), manager.get(id)!!.points[0], "其他点不动")
        manager.endDrag(id, pointIndex = 1)
        assertEquals(1, events.count { it is OverlayEvent.DragFinished })

        // 整体拖动：两点同步平移
        assertTrue(manager.beginDrag(id, pointIndex = null))
        manager.drag(id, pointIndex = null, deltaTimestamp = 50, deltaValue = 1.0)
        assertEquals(p(150, 11.0), manager.get(id)!!.points[0])
        assertEquals(p(350, 16.0), manager.get(id)!!.points[1])
        manager.endDrag(id, pointIndex = null)
    }

    @Test
    fun `锁定禁止编辑但允许选择检查`() {
        val id = createdSegment()
        manager.setLocked(id, true)
        assertEquals(1, events.count { it is OverlayEvent.LockedChanged })
        manager.select(id)
        assertEquals(OverlayPhase.SELECTED, manager.get(id)!!.phase)
        assertTrue(!manager.beginDrag(id, pointIndex = 0), "锁定拒绝拖动")
        assertEquals(0, events.count { it is OverlayEvent.DragStarted })
    }

    @Test
    fun `删除由明确操作触发且事件一次`() {
        val id = createdSegment()
        manager.select(id)
        manager.removeSelected()
        assertEquals(1, events.count { it is OverlayEvent.Removed })
        assertEquals(null, manager.get(id))
        assertEquals(null, manager.selectedId, "选中随删除清除")
        manager.remove(id) // 再删无事件
        assertEquals(1, events.count { it is OverlayEvent.Removed })
    }

    @Test
    fun `多实例稳定ID且互不干扰`() {
        val a = createdSegment()
        val b = createdSegment()
        assertTrue(a != b)
        manager.select(a)
        manager.beginDrag(a, 0)
        assertEquals(OverlayPhase.DRAGGING, manager.get(a)!!.phase)
        assertEquals(OverlayPhase.COMPLETED, manager.get(b)!!.phase, "b 不受 a 的编辑影响")
        manager.endDrag(a, 0)
    }
    @kotlin.test.Test
    fun drawingPreviewDoesNotCommitAPointAndTheHookSeesTheUpdatedGeometry() {
        val manager = OverlayManager()
        var calls = 0
        val template = object : OverlayDefinition {
            override val name = "preview_core_test"
            override val pointCount = 2
            override val performEventMoveForDrawing: ((String, OverlayPoint) -> Unit) = { id, point ->
                calls++
                val inst = manager.get(id)!!
                kotlin.test.assertEquals(point, inst.points.last())
                inst.points[inst.points.lastIndex] = point.copy(value = 42.0)
            }
        }
        val id = manager.beginCreate(template)!!
        manager.moveCreatePoint(id, OverlayPoint(0, 1.0))
        kotlin.test.assertEquals(0, manager.get(id)!!.currentStep)
        manager.addCreatePoint(id, manager.get(id)!!.points.last())
        kotlin.test.assertEquals(1, manager.get(id)!!.currentStep)
        manager.moveCreatePoint(id, OverlayPoint(60000, 2.0))
        manager.moveCreatePoint(id, OverlayPoint(61000, 3.0))
        kotlin.test.assertEquals(1, manager.get(id)!!.currentStep)
        kotlin.test.assertEquals(2, manager.get(id)!!.points.size)
        kotlin.test.assertTrue(manager.addCreatePoint(id, manager.get(id)!!.points.last()))
        kotlin.test.assertEquals(42.0, manager.get(id)!!.points.last().value)
        kotlin.test.assertEquals(3, calls)
        kotlin.test.assertEquals(OverlayPhase.COMPLETED, manager.get(id)!!.phase)
    }

}
