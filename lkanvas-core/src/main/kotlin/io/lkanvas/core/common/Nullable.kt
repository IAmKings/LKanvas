package io.lkanvas.core.common

/**
 * 上游 `common/Nullable.ts` / `common/DeepPartial.ts` 的 Kotlin 镜像说明。
 *
 * - TS `Nullable<T>` → Kotlin `T?`（语言原生；不设别名以免递归别名限制）。
 * - TS `DeepPartial<T>` → data class 全字段默认值 + null=未提供 约定
 *   （见 common/utils/Merge.kt 语义）。
 */
