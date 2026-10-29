package io.lkanvas.core.common.utils

import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

/**
 * 上游 `common/utils/typeChecks.ts` merge/clone 的 Kotlin 镜像（SLA-009 收口）。
 *
 * 上游语义：**按提供的属性合并**——`for key in source` + hasOwnProperty，
 * 提供的值即覆盖（等于默认值的 patch 值照常写入），嵌套对象递归合并。
 * Kotlin data class 无 undefined：约定 **字段值非 null 即视为已提供**，
 * null 视为未提供（保留 base）。
 */
object Merge {

    /**
     * 上游 `merge(target, source)`：DeepPartial 深合并。
     * - patch 整体为 null → 返回 base（未提供）。
     * - base 为 null → 返回 patch。
     * - 类型不同 → patch 整体替换。
     * - 同类型 data class → 逐字段：patch 字段 null 保留 base，非 null 覆盖；
     *   两边均非 null 且同类 data class 时递归合并。
     * - 非 data class（含集合/标量）→ patch 整体替换。
     */
    fun merge(base: Any?, patch: Any?): Any? {
        if (patch == null) return base
        if (base == null) return patch
        if (base::class != patch::class) return patch
        val k = base::class
        if (!k.isData) return patch
        val primary = k.primaryConstructor ?: return patch
        val membersByName = k.members.associateBy { it.name }

        val args = HashMap<KParameter, Any?>()
        for (param in primary.parameters) {
            val name = param.name ?: continue
            val getter = membersByName[name] ?: continue
            val baseVal = getter.call(base)
            val patchVal = getter.call(patch)
            args[param] = when {
                patchVal == null -> baseVal
                baseVal != null && baseVal::class.isData && baseVal::class == patchVal::class ->
                    merge(baseVal, patchVal)
                else -> patchVal
            }
        }
        return primary.callBy(args)
    }

    /**
     * 上游 `clone`：本域 data class 均为不可变值对象——标量与 data class 直接返回
     * 原实例（无共享可变状态）；集合做防御性拷贝。
     */
    fun clone(target: Any?): Any? = when (target) {
        is List<*> -> target.toList()
        is Map<*, *> -> target.toMap()
        else -> target
    }
}
