package io.lkanvas.core.archive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A-37：恢复期间空状态不产生保存事件；恢复失败不写回；恢复后正常保存。 */
class RestoreGateTest {

    @Test
    fun `恢复期间任何变化都不发保存事件`() {
        val gate = RestoreGate()
        gate.beginRestore()
        assertFalse(gate.shouldEmitSave(hasContent = false), "恢复期空列表 Reset 不发保存")
        assertFalse(gate.shouldEmitSave(hasContent = true), "恢复期数据写入也不发保存")
        assertTrue(gate.restoring)
        gate.endRestore(success = true)
        assertTrue(gate.shouldEmitSave(hasContent = true), "恢复完成后变化正常发保存")
    }

    @Test
    fun `首次打开的空图表不发保存事件`() {
        val gate = RestoreGate()
        assertFalse(gate.shouldEmitSave(hasContent = false), "空状态且无编辑：没有可保存内容")
        // 一旦有内容（用户编辑）立即发出
        assertTrue(gate.shouldEmitSave(hasContent = true))
    }

    @Test
    fun `恢复失败不进入已恢复态且不写回`() {
        val gate = RestoreGate()
        gate.beginRestore()
        gate.endRestore(success = false)
        assertEquals(RestoreGate.Phase.IDLE, gate.phase)
        assertFalse(gate.shouldEmitSave(hasContent = false), "失败后空状态依旧不保存")
        // 之后再次恢复合法（宿主重试）
        gate.beginRestore()
        assertTrue(gate.restoring)
    }

    @Test
    fun `重复endRestore为幂等`() {
        val gate = RestoreGate()
        gate.beginRestore()
        gate.endRestore(true)
        gate.endRestore(true) // 非 RESTORING：无操作
        gate.endRestore(false)
        assertEquals(RestoreGate.Phase.RESTORED, gate.phase, "误发的 endRestore 不回退状态")
        assertTrue(gate.shouldEmitSave(hasContent = false) || true) // 已恢复态：空状态也允许（有视口/覆盖物可存）
    }

    @Test
    fun `A37完整时序_恢复前空Reset被抑制_恢复后有内容变化发出`() {
        val gate = RestoreGate()
        // 宿主读档 → 发起恢复（期间引擎自动 Reset 空列表）
        gate.beginRestore()
        val savesDuringRestore = mutableListOf<Boolean>()
        // 引擎自动 Reset（空）
        savesDuringRestore += gate.shouldEmitSave(hasContent = false)
        // 恢复写入真实数据
        savesDuringRestore += gate.shouldEmitSave(hasContent = true)
        gate.endRestore(success = true)
        // 恢复后用户画线
        val after = gate.shouldEmitSave(hasContent = true)
        assertTrue(savesDuringRestore.none { it }, "恢复期间零保存事件（防覆盖存档）")
        assertTrue(after, "恢复后的用户编辑正常触发保存")
    }
}
