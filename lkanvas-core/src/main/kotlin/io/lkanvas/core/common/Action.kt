package io.lkanvas.core.common

/**
 * 上游 `common/Action.ts` 的 Kotlin 镜像：9 种 ActionType 与动作回调。
 */
enum class ActionType {
    ON_ZOOM,
    ON_SCROLL,
    ON_VISIBLE_RANGE_CHANGE,
    ON_CANDLE_TOOLTIP_FEATURE_CLICK,
    ON_INDICATOR_TOOLTIP_FEATURE_CLICK,
    ON_CROSSHAIR_FEATURE_CLICK,
    ON_CROSSHAIR_CHANGE,
    ON_CANDLE_BAR_CLICK,
    ON_PANE_DRAG,
}

/** 上游 `ActionCallback = (data?) => void`。 */
fun interface ActionCallback {
    fun onAction(data: Any?)
}
