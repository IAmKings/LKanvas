package io.lkanvas.core.i18n

/**
 * 图表文案（PRD R-11/T-24）：库内全部面向用户文本集中于此。
 * 纯 Kotlin 数据类（不依赖 Android 资源），默认中文，宿主可整体替换
 * （内置 `ZH_CN` / `EN_US`）实现中英文切换。
 */
data class ChartLocale(
    // 方向词（涨跌不只依赖颜色，R-13）
    val up: String = "上涨",
    val down: String = "下跌",
    val flat: String = "持平",
    val versusPrevTemplate: String = "，较前一根%s",
    // 摘要
    val volume: String = "成交量",
    val noData: String = "K线图，暂无数据",
    val chartSummaryTemplate: String = "K线图，共 %d 根数据。",
    val latestPrefix: String = "最新：",
    val selectedPrefix: String = "当前选中：",
    val hintActions: String = "可使用无障碍动作切换上一根/下一根、放大缩小。",
    // 指标 Missing 显示
    val missing: String = "—",
    // 无障碍动作
    val actionPrevCandle: String = "上一根",
    val actionNextCandle: String = "下一根",
    val actionZoomIn: String = "放大",
    val actionZoomOut: String = "缩小",
    val actionDeleteOverlay: String = "删除选中画线",
    val actionNextOverlay: String = "选中下一画线",
    val overlayCountTemplate: String = "画线共 %d 条",
    val overlaySelectedTemplate: String = "选中画线",
    val overlayPoints: String = "控制点",
) {
    fun overlaySummary(count: Int): String = overlayCountTemplate.format(count)
    fun versusPrev(direction: Direction): String = versusPrevTemplate.format(
        when (direction) {
            Direction.UP -> up
            Direction.DOWN -> down
            Direction.FLAT -> flat
        },
    )

    enum class Direction { UP, DOWN, FLAT }

    companion object {
        /** 中文（默认）。 */
        val ZH_CN: ChartLocale = ChartLocale()

        /** 英文。 */
        val EN_US: ChartLocale = ChartLocale(
            up = "up",
            down = "down",
            flat = "flat",
            versusPrevTemplate = ", %s vs previous candle",
            volume = "Volume",
            noData = "Candlestick chart, no data",
            chartSummaryTemplate = "Candlestick chart, %d candles.",
            latestPrefix = "Latest: ",
            selectedPrefix = "Selected: ",
            hintActions = "Accessibility actions available: Previous candle / Next candle / Zoom in / Zoom out.",
            missing = "—",
            actionPrevCandle = "Previous candle",
            actionNextCandle = "Next candle",
            actionZoomIn = "Zoom in",
            actionZoomOut = "Zoom out",
            actionDeleteOverlay = "Delete selected drawing",
            actionNextOverlay = "Select next drawing",
            overlayCountTemplate = "%d drawings",
            overlaySelectedTemplate = "Selected drawing",
            overlayPoints = "points",
        )
    }
}
