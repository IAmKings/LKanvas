package io.lkanvas.chart

/**
 * 数据加载契约——领域定义已镜像至 core/common（上游 `common/DataLoader.ts`
 * 同名文件）；本文件保留同包别名。
 *
 * 注：Kotlin typealias 不能访问嵌套分类器（`DataLoader.Nested` 非法），
 * 故嵌套类型以顶层别名逐一登记。
 */
typealias DataLoader = io.lkanvas.core.common.DataLoader

typealias DataLoadType = io.lkanvas.core.common.DataLoader.DataLoadType

typealias DataLoadMore = io.lkanvas.core.common.DataLoader.DataLoadMore

typealias GetBarsParams = io.lkanvas.core.common.DataLoader.GetBarsParams

typealias SubscribeBarParams = io.lkanvas.core.common.DataLoader.SubscribeBarParams

typealias UnsubscribeBarParams = io.lkanvas.core.common.DataLoader.UnsubscribeBarParams
