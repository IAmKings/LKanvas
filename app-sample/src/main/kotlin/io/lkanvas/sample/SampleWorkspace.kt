package io.lkanvas.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.OverlayCreate
import io.lkanvas.chart.YAxisFilter
import io.lkanvas.chart.YAxisOverride
import io.lkanvas.compose.KLineChart
import io.lkanvas.compose.export.ChartSnapshotExporter
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartLayoutConfig
import io.lkanvas.compose.theme.MainChartType
import io.lkanvas.core.axis.YAxisSpec
import io.lkanvas.core.extension.overlay.OverlayTemplates
import io.lkanvas.core.overlay.OverlayPoint
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private enum class SampleSheet(val title: String, val icon: Int) {
    INDICATORS("指标", R.drawable.ic_indicators), DRAWING("画线", R.drawable.ic_draw),
    CHART("图表", R.drawable.ic_chart), MORE("更多", R.drawable.ic_more),
    STYLES("样式设置", R.drawable.ic_chart), DIAGNOSTICS("诊断", R.drawable.ic_diagnostics),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleWorkspace(
    facade: LKanvasChart,
    feed: LocalFeed,
    dark: Boolean,
    periodIndex: Int,
    stress: StressConfig?,
    onThemeChange: () -> Unit,
    onPeriodChange: (Int) -> Unit,
    onSymbolChange: () -> Unit,
) {
    val state = facade.state
    var sheet by remember { mutableStateOf<SampleSheet?>(null) }
    var customLegend by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val messages = remember { SnackbarHostState() }
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val styles = facade.getStyles()
    val theme = remember(styles) { facade.resolveTheme() }
    val report: (String) -> Unit = { message -> scope.launch { messages.showSnackbar(message) } }
    // 父容器只依赖数量/品种；逐笔价格由行情摘要与图表各自观察。
    val candleCount by remember(state) { derivedStateOf { state.dataSnapshot.candles.size } }
    val marketKey by remember(state) { derivedStateOf { state.dataSnapshot.marketKey } }
    // 覆盖物由可观察 revision 驱动；不在普通报价更新时复制完整列表。
    val overlays = remember(state.overlayRevision) { state.overlays.all() }
    val creating = overlays.firstOrNull { it.id == state.overlays.creatingId }
    val selected = overlays.firstOrNull { it.id == state.overlays.selectedId }

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val landscape = maxWidth > maxHeight
            val compact = landscape || LocalDensity.current.fontScale > 1.5f
            val sheetHeight = maxHeight * 0.92f
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_lkanvas), null, Modifier.size(32.dp), tint = colors.primary)
                    Text("LKanvas", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                    if (stress == null) TextButton(onClick = onSymbolChange, modifier = Modifier.semantics { contentDescription = "切换品种" }) {
                        Text(marketKey?.symbol ?: "DEMO", fontSize = 12.sp)
                    }
                    if (landscape) LandscapeMarketSummary(state)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onThemeChange) {
                        Icon(painterResource(R.drawable.ic_theme), if (dark) "切换浅色主题" else "切换深色主题")
                    }
                    IconButton(onClick = { sheet = SampleSheet.DIAGNOSTICS }) {
                        Icon(painterResource(R.drawable.ic_diagnostics), "打开诊断")
                    }
                }
                if (!landscape) MarketSummary(state, theme.upColor, theme.downColor, compact, when {
                    stress != null -> "压力模式 · 静态负载"
                    feed.paused -> "模拟已暂停"
                    else -> "模拟推送 · 5 次/秒"
                })
                HorizontalDivider(color = colors.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                        samplePeriods.forEachIndexed { index, (label, _) ->
                            TextButton(
                                onClick = { if (index != periodIndex) onPeriodChange(index) },
                                enabled = stress == null,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (periodIndex == index) colors.primary else colors.onSurfaceVariant,
                                    containerColor = if (periodIndex == index) colors.primaryContainer else Color.Transparent,
                                ),
                                modifier = Modifier.heightIn(min = 48.dp).semantics {
                                    this.selected = index == periodIndex
                                    role = Role.Tab
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text(label, fontSize = 13.sp, fontWeight = if (periodIndex == index) FontWeight.SemiBold else FontWeight.Normal) }
                        }
                    }
                    IconButton(onClick = { facade.scrollToRealTime() }, enabled = candleCount > 0) {
                        Icon(painterResource(R.drawable.ic_latest), "回到最新")
                    }
                }
                HorizontalDivider(color = colors.outlineVariant)
                if (candleCount == 0) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text("正在准备模拟行情…", modifier = Modifier.padding(top = 12.dp), color = colors.onSurfaceVariant)
                        }
                    }
                } else {
                    KLineChart(
                        state, Modifier.weight(1f).fillMaxWidth(), theme = theme,
                        config = ChartLayoutConfig(subPaneHeight = (76 * LocalDensity.current.fontScale).dp, legendTextSize = 11.sp, axisTextSize = 11.sp),
                        styles = styles,
                        legendSlot = if (customLegend) { { data ->
                            Text(
                                "${data.timeText}  收 ${priceText(data.candle.close)}  MA5 ${priceText(data.maValues["ma1"])}",
                                color = colors.onSurface, fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().background(colors.surface).padding(8.dp),
                            )
                        } } else null,
                    )
                }
                HorizontalDivider(color = colors.outlineVariant)
                if (creating != null || selected != null) {
                    Row(
                        Modifier.fillMaxWidth().background(colors.surfaceVariant).padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (creating != null) "${overlayLabel(creating.templateName)} · 已取 ${creating.points.size} 点" else "已选中 · ${overlayLabel(selected!!.templateName)}",
                            fontSize = 12.sp, modifier = Modifier.weight(1f),
                        )
                        if (creating != null) {
                            TextButton(onClick = { if (!state.finishCreateOverlay()) report("请继续取点，或取消当前画线") }) { Text("完成") }
                            TextButton(onClick = { state.cancelCreateOverlay() }) { Text("取消") }
                        } else {
                            TextButton(onClick = { state.removeSelectedOverlay() }) { Text("删除", color = colors.error) }
                            IconButton(onClick = { state.overlays.select(null); state.notifyOverlayChanged() }) {
                                Icon(painterResource(R.drawable.ic_close), "取消选择画线")
                            }
                        }
                    }
                } else if (!compact) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$candleCount 根 · ${chartTypeLabel(state.mainChartType)}", fontSize = 11.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("点按查看 · 拖动浏览", fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(SampleSheet.INDICATORS, SampleSheet.DRAWING, SampleSheet.CHART, SampleSheet.MORE).forEach { item ->
                        Surface(onClick = { sheet = item }, modifier = Modifier.weight(1f), color = colors.surface, shape = RoundedCornerShape(8.dp)) {
                            if (compact) {
                                Row(Modifier.heightIn(min = 48.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painterResource(item.icon), null, Modifier.size(20.dp), tint = colors.primary)
                                    Text(item.title, fontSize = 12.sp, color = colors.onSurface, modifier = Modifier.padding(start = 8.dp))
                                }
                            } else {
                                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(painterResource(item.icon), null, Modifier.size(22.dp), tint = colors.primary)
                                    Text(item.title, fontSize = 12.sp, color = colors.onSurface, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(messages, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp, start = 12.dp, end = 12.dp))
            val currentSheet = sheet
            if (currentSheet != null) {
                val panelScroll = key(currentSheet) { rememberScrollState() }
                ModalBottomSheet(
                    onDismissRequest = { sheet = null },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = colors.surface,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                ) {
                    Column(Modifier.fillMaxWidth().heightIn(max = sheetHeight)) {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(currentSheet.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { sheet = null }) { Icon(painterResource(R.drawable.ic_close), "关闭面板") }
                        }
                        Column(Modifier.weight(1f, fill = false).verticalScroll(panelScroll).padding(bottom = 24.dp)) {
                            when (currentSheet) {
                                SampleSheet.INDICATORS -> IndicatorPanel(facade)
                                SampleSheet.STYLES -> StylesPanel(facade)
                                SampleSheet.DIAGNOSTICS -> DiagnosticsPanel(facade)
                                SampleSheet.DRAWING -> {
                                    PanelCaption("选择工具后在图表取点；双指缩放不受影响。")
                                    for (tool in OverlayTemplates.supported()) {
                                        ChoiceRow(overlayLabel(tool), tool, state.currentTool == tool) {
                                            state.cancelCreateOverlay()
                                            state.currentTool = tool
                                            if (state.beginCreateOverlay()) {
                                                state.notifyOverlayChanged()
                                                sheet = null
                                            } else report("当前工具无法开始，请取消未完成的画线后重试")
                                        }
                                    }
                                }
                                SampleSheet.CHART -> {
                                    val axis = remember(state.viewRevision) { state.mainAxisSpec }
                                    PanelHeading("主图类型")
                                    for (type in MainChartType.entries) {
                                        ChoiceRow(chartTypeLabel(type), selected = state.mainChartType == type) {
                                            val current = facade.getStyles()
                                            facade.setStyles(current.copy(candle = current.candle.copy(type = type.name.lowercase(Locale.ROOT))))
                                        }
                                    }
                                    PanelHeading("价格坐标")
                                    listOf("线性" to YAxisSpec.Normal, "对数" to YAxisSpec.Logarithm, "百分比" to YAxisSpec.Percentage(null)).forEach { (title, spec) ->
                                        ChoiceRow(title, selected = axis::class == spec::class) {
                                            state.mainAxisSpec = spec
                                            state.notifyViewportChanged()
                                        }
                                    }
                                    TextButton(onClick = { sheet = SampleSheet.STYLES }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("网格、十字线与 Tooltip 设置 →") }
                                }
                                SampleSheet.MORE -> {
                                    val indicators = remember(state.viewRevision) { facade.getIndicators() }
                                    PanelCaption("真实图表能力与可复现的本地演示。数据不来自交易所。")
                                    SettingSwitch("深色主题", "页面、面板、图表与导出保持一致", dark, onChange = { onThemeChange() })
                                    SettingSwitch("模拟行情推送", "加速演示：每秒推进一分钟行情", !feed.paused, enabled = stress == null, onChange = { feed.paused = !it })
                                    SettingSwitch("自定义图例", "演示宿主 legendSlot", customLegend, onChange = { customLegend = it })
                                    val bollOn = indicators.any { it.id == "sample_pane_boll" }
                                    SettingSwitch("独立 BOLL 副图", "演示动态 pane 的创建与移除", bollOn, onChange = {
                                        if (it) facade.createIndicator("BOLL", paneId = "pane_boll", id = "sample_pane_boll")
                                        else facade.removeIndicator(ChartState.IndicatorFilter(id = "sample_pane_boll"))
                                    })
                                    val axisOn = indicators.any { it.id == "sample_multi_ma" }
                                    SettingSwitch("独立 MA10 坐标轴", "保留主图原有 MA，单独管理演示实例", axisOn, onChange = {
                                        if (it) {
                                            facade.createYAxis(YAxisOverride(id = "ax_ma10"))
                                            facade.createIndicator("MA", calcParams = listOf(10.0), paneId = "main", isStack = true, yAxisId = "ax_ma10", id = "sample_multi_ma")
                                        } else {
                                            facade.removeIndicator(ChartState.IndicatorFilter(id = "sample_multi_ma"))
                                            facade.removeYAxis(YAxisFilter(id = "ax_ma10"))
                                        }
                                    })
                                    TextButton(onClick = {
                                        val candles = state.dataSnapshot.candles
                                        val range = state.mainPaneRange()
                                        val visible = state.visibleIndexRange()
                                        if (range != null && visible != null && state.overlays.creatingId == null) {
                                            val a = candles[visible.first.coerceIn(candles.indices)].timestamp
                                            val b = candles[visible.second.coerceIn(candles.indices)].timestamp
                                            val mid = a + (b - a) / 2
                                            val (lo, hi) = range
                                            val span = hi - lo
                                            listOf("sample_circle", "sample_rect", "sample_polygon", "sample_arc").forEach { name ->
                                                val points = if (name == "sample_polygon") listOf(OverlayPoint(a, lo + span * .2), OverlayPoint(mid, lo + span * .1), OverlayPoint(mid, lo + span * .4), OverlayPoint(a, lo + span * .5))
                                                    else listOf(OverlayPoint(a, lo + span * .7), OverlayPoint(mid, lo + span * .3))
                                                facade.createOverlay(OverlayCreate(name, points = points))
                                            }
                                            sheet = null
                                            report("已添加圆、矩形、多边形与圆弧示例")
                                        } else report("请先加载行情并完成当前画线")
                                    }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("添加四种宿主图形示例") }
                                    FilledTonalButton(onClick = {
                                        val out = File(context.cacheDir, "lkanvas-export.png")
                                        val result = runCatching {
                                            out.outputStream().use { ChartSnapshotExporter.exportPng(state, measurer, it, theme = facade.resolveTheme(), widthPx = 1080, heightPx = 1600) }
                                        }
                                        sheet = null
                                        result.onSuccess { report("PNG 已导出到应用缓存：${out.absolutePath}") }
                                            .onFailure { report("PNG 导出失败：${it.message ?: "无法写入文件"}") }
                                    }, enabled = candleCount > 0, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { Text("导出图表 PNG · 1080 × 1600") }
                                    TextButton(onClick = { sheet = SampleSheet.DIAGNOSTICS }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("打开诊断与运行日志 →") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeMarketSummary(state: ChartState) {
    val snapshot = state.dataSnapshot
    Text(
        "${snapshot.marketKey?.symbol ?: "STRESS"}  ${priceText(snapshot.candles.lastOrNull()?.close)} · ${snapshot.candles.size} 根",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        modifier = Modifier.padding(start = 20.dp),
    )
}

@Composable
private fun MarketSummary(state: ChartState, up: Color, down: Color, compact: Boolean, status: String) {
    val candle = state.dataSnapshot.candles.lastOrNull()
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (compact) 4.dp else 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.dataSnapshot.marketKey?.symbol ?: "STRESS", color = colors.primary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.background(colors.primaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
            Text("模拟指数", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (!compact) Text(status, color = colors.onSurfaceVariant, fontSize = 10.sp)
        }
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(priceText(candle?.close), fontSize = if (compact) 24.sp else 34.sp, fontWeight = FontWeight.SemiBold, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
            if (candle != null) {
                val change = candle.close - candle.open
                val percent = if (candle.open != 0.0) change / candle.open * 100 else 0.0
                Text(String.format(Locale.US, "%+.2f (%+.2f%%)", change, percent) + " · 本根", color = if (change >= 0) up else down, fontSize = 12.sp)
            }
        }
        if (!compact) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("开盘" to candle?.open, "最高" to candle?.high, "最低" to candle?.low, "成交量" to candle?.volume).forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, color = colors.onSurfaceVariant, fontSize = 11.sp)
                        Text(priceText(value), fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                Text("当前 K 线", color = colors.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.align(Alignment.Bottom))
            }
        }
    }
}

fun priceText(value: Double?): String = value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%,.2f", it) } ?: "—"

fun chartTypeLabel(type: MainChartType): String = when (type) {
    MainChartType.CANDLE_SOLID -> "实心蜡烛"
    MainChartType.CANDLE_STROKE -> "空心蜡烛"
    MainChartType.CANDLE_UP_STROKE -> "上涨空心"
    MainChartType.CANDLE_DOWN_STROKE -> "下跌空心"
    MainChartType.OHLC -> "美国线"
    MainChartType.AREA -> "面积图"
}

fun overlayLabel(name: String): String = when (name) {
    "segment" -> "线段"; "straightLine" -> "直线"; "rayLine" -> "射线"
    "horizontalRayLine" -> "水平射线"; "horizontalSegment" -> "水平线段"; "horizontalStraightLine" -> "水平直线"
    "verticalRayLine" -> "垂直射线"; "verticalSegment" -> "垂直线段"; "verticalStraightLine" -> "垂直直线"
    "parallelStraightLine" -> "平行线"; "priceChannelLine" -> "价格通道"; "fibonacciLine" -> "斐波那契"
    "priceLine" -> "价格线"; "simpleAnnotation" -> "文字注释"; "simpleTag" -> "价格标签"; "brush" -> "自由画笔"
    else -> name
}

@Composable
fun PanelCaption(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
}

@Composable
fun PanelHeading(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
fun ChoiceRow(title: String, subtitle: String? = null, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontSize = 14.sp) },
        supportingContent = subtitle?.let { { Text(it, fontSize = 12.sp) } },
        trailingContent = { RadioButton(selected, onClick = null) },
        modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
    )
}

@Composable
fun SettingSwitch(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontSize = 14.sp) },
        supportingContent = { Text(subtitle, fontSize = 12.sp) },
        trailingContent = { Switch(checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}
