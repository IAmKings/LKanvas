# TS API 清单（上游 KLineChart v10.1.0）

> 对齐排查 A1 产物。来源：`third_party/klinechart-v10.1.0/src/`（index.ts / Chart.ts / Options.ts / Styles.ts / component/Indicator.ts / component/Overlay.ts / common/Action.ts / common/DataLoader.ts / extension/*）。
> 每个符号在 gap-matrix.md 中必须有对应行，状态 ∈ {A 直接对齐, B 适配, C 平台不适用, D 有意差异, E 缺失}。

## 1. index.ts 顶层导出（33）

| 符号 | 签名/说明 | 源位置 |
|---|---|---|
| `version()` | `(): string`（构建期替换 `__VERSION__`） | index.ts |
| `init(ds, options?)` | `(HTMLElement\|string, Options?) => Nullable<Chart>`；同一 DOM 重复 init 返回既有实例 | index.ts |
| `dispose(dcs)` | `(HTMLElement\|Chart\|string) => void` | index.ts |
| `utils.clone` | 深拷贝 | common/utils/typeChecks |
| `utils.merge` | DeepPartial 合并（setStyles 语义基础） | 同上 |
| `utils.isString/isNumber/isValid/isObject/isArray/isFunction/isBoolean` | 类型判断 7 项 | 同上 |
| `utils.formatValue` | 空值兜底取值 | common/utils/format |
| `utils.formatPrecision` | `(value, precision)` 精度格式 | 同上 |
| `utils.formatBigNumber` | 万/亿缩写 | 同上 |
| `utils.formatDate` (=formatTimestampByTemplate) | `(timestamp, format, type)` | 同上 |
| `utils.formatThousands` | 千分位 | 同上 |
| `utils.formatFoldDecimal` | 小数折叠 | 同上 |
| `utils.calcTextWidth` | 文本测宽（canvas） | common/utils/canvas |
| `utils.getLinearSlopeIntercept` | 两点斜率截距 | extension/figure/line |
| `utils.getLinearYFromSlopeIntercept` | 截距→y | 同上 |
| `utils.getLinearYFromCoordinates` | 两点→y | 同上 |
| `utils.checkCoordinateOnArc/Circle/Line/Polygon/Rect/Text` | 6 种图形命中 | extension/figure/* |
| `registerIndicator(template)` | 指标模板注册 | extension/indicator/index |
| `getSupportedIndicators()` | `(): string[]` | 同上 |
| `registerOverlay(template)` | 覆盖物模板注册 | extension/overlay/index |
| `getSupportedOverlays()` | `(): string[]` | 同上 |
| `getOverlayClass(name)` | 取覆盖物类 | 同上 |
| `registerFigure(figure)` / `getFigureClass` / `getSupportedFigures()` | 图形注册体系 | extension/figure/index |
| `registerStyles(name, styles)` / 内置 dark/light | 样式注册 | extension/styles/index |
| `registerLocale(locale)` / `getSupportedLocales()` | i18n 注册 | extension/i18n/index |
| `registerXAxis(override)` / `registerYAxis(override)` | 轴注册 | extension/x-axis, y-axis |
| `registerHotkey(hotkey)` / `getHotkey()` / `getSupportedHotkeys()` | 快捷键（浏览器键鼠） | extension/hotkey/index |

类型别名导出：`ActionType, CandleTooltipRectPosition, CandleType, Chart, DomPosition, FeatureType, FormatDateType, IndicatorSeries, Layout, LineType, OverlayDrawingMode, OverlayMode, PolygonType, TooltipFeaturePosition, TooltipShowRule, TooltipShowType, YAxisFilter, YAxisOverride, ZoomAnchor`。

## 2. Chart 实例方法（Chart.ts，约 70 个公开方法）

### 2.1 数据（v10 模式：DataLoader，**无** setData/applyNewData/updateData）

| 方法 | 签名 | 说明 |
|---|---|---|
| `setDataLoader(loader)` | `(DataLoader) => void`（内部 resetData） | 数据源；见 §7 |
| `getDataList()` | `(): KLineData[]` | 全量数据 |
| `resetData()` | `(): void` | 清空并触发 loader init |
| `setSymbol(symbol)` / `getSymbol()` | `SymbolInfo{ticker,pricePrecision,volumePrecision}` | 切品种（reset+重载） |
| `setPeriod(period)` / `getPeriod()` | `Period{type,milliseconds?}` | 切周期 |

DataLoader 契约（common/DataLoader.ts）：
- `getBars(params: {type: 'init'|'forward'|'backward'|'update', timestamp, symbol, period, callback(data, more?: boolean|{backward,forward})})`
- `subscribeBar?(params: {symbol, period, callback(data)})` / `unsubscribeBar?(params)`

### 2.2 指标

| 方法 | 签名 |
|---|---|
| `createIndicator(value, isStack?)` | `(string\|IndicatorCreate, boolean?) => Nullable<string>`（返回 instanceId） |
| `overrideIndicator(override)` | `(IndicatorOverride) => boolean` |
| `getIndicators(filter?)` | `(IndicatorFilter?) => Indicator[]` |
| `removeIndicator(filter?)` | `(IndicatorFilter?) => boolean` |
| `getIndicatorByInstanceId(id)` | Store 提供 |

`IndicatorCreate = Omit<Partial<Indicator>,'result'|'name'> & {name}`；`IndicatorFilter = {id?,name?,paneId?,isStack?}`。
`Indicator` 接口字段（22）：`id, paneId, yAxisId, name, shortName, precision, calcParams[], shouldOhlc, shouldFormatBigNumber, visible, zLevel, extendData, series('normal'|'price'|'volume'), figures[], minValue?, maxValue?, styles?, shouldUpdate?, calc, regenerateFigures?, createTooltipDataSource?, draw?`。

### 2.3 覆盖物

| 方法 | 签名 |
|---|---|
| `createOverlay(value, paneId?)` | `(string\|OverlayCreate\|数组, string?) => Nullable<string>\|Array`（返回 id/数组） |
| `getOverlays(filter?)` | `(OverlayFilter?) => Overlay[]` |
| `overrideOverlay(override)` | `(OverlayOverride) => boolean` |
| `removeOverlay(filter?)` | `(OverlayFilter?) => boolean` |

`OverlayFilter = {id?, groupId?, name?, paneId?}`。
`OverlayTemplate` 字段：`name, totalStep, needDefaultPointFigure, needDefaultXAxisFigure, needDefaultYAxisFigure, createPointFigures, createXAxisFigures, createYAxisFigures, checkEventOn, drawingMode('step'|'continuous'), performEventPressedMove, performEventMoveForDrawing, onRemoved, onClick, onDoubleClick, onRightClick, onMouseDown/Enter/Leave/Move/Up, onTouchStart/Move/End, onPressedMove, extendData`。`OverlayMode = 'normal'|'weak_magnet'|'strong_magnet'`。

### 2.4 视口/交互

| 方法 | 签名 |
|---|---|
| `setBarSpace(space)` / `getBarSpace()` | `BarSpace{bar, halfBar}` |
| `getVisibleRange()` | `VisibleRange{from, to, realFrom, realTo}` |
| `scrollByDistance(distance, animationDuration?)` | px 距离滚动 |
| `scrollToRealTime(animationDuration?)` | 回到最新 |
| `scrollToDataIndex(index, animationDuration?)` | 定位索引 |
| `scrollToTimestamp(timestamp, animationDuration?)` | 定位时间戳 |
| `zoomAtCoordinate(scale, coordinate?, animationDuration?)` | 触点缩放 |
| `zoomAtDataIndex(scale, dataIndex, animationDuration?)` | 索引缩放 |
| `zoomAtTimestamp(scale, timestamp, animationDuration?)` | 时间缩放 |
| `setOffsetRightDistance(distance)` / `getOffsetRightDistance()` | 右留白 px |
| `setMaxOffsetLeftDistance(d)` / `setMaxOffsetRightDistance(d)` | 最大越滚 |
| `setLeftMinVisibleBarCount(count)` / `setRightMinVisibleBarCount(count)` | 最小可见数 |
| `setZoomEnabled(enabled)` / `isZoomEnabled()` | 缩放开关 |
| `setScrollEnabled(enabled)` / `isScrollEnabled()` | 滚动开关 |
| `setZoomAnchor(anchor)` / `getZoomAnchor()` | `'cursor'|'last_bar'` |
| `setLoadMoreDataCount(count)` | 分页触发条数（Store） |

### 2.5 配置

| 方法 | 说明 |
|---|---|
| `setStyles(value: string\|DeepPartial<Styles>)` / `getStyles()` | 字符串内置主题或深度合并 |
| `setLayoutOptions(layout)` / `getLayoutOptions()` | barSpaceLimit/pane/yAxis |
| `setFormatter(f)` / `getFormatter()` | formatDate/formatBigNumber/formatExtendText |
| `setLocale(locale)` / `getLocale()` | i18n |
| `setTimezone(tz)` / `getTimezone()` | 时区 |
| `setThousandsSeparator(opts)` / `getThousandsSeparator()` | `{sign, symbol}` |
| `setDecimalFold(opts)` / `getDecimalFold()` | `{threshold, precision}` |
| `setHotkey(hotkey)` / `getHotkey()`/`getHotKey()` | 浏览器快捷键 |
| `setPaneOptions(options: Partial<PaneOptions>)` / `getPaneOptions(id?)` | pane 高度/轴状态 |
| `createYAxis(yAxis)` / `removeYAxis(filter)` / `getYAxes(filter)` / `overrideYAxis(yAxis)` / `overrideXAxis(xAxis)` | 多轴 |

### 2.6 事件与生命周期

| 方法 | 说明 |
|---|---|
| `subscribeAction(type, callback)` / `unsubscribeAction(type, callback?)` | 9 种 ActionType：`onZoom, onScroll, onVisibleRangeChange, onCandleTooltipFeatureClick, onIndicatorTooltipFeatureClick, onCrosshairFeatureClick, onCrosshairChange, onCandleBarClick, onPaneDrag` |
| `executeAction(type, data)` | 手动触发事件 |
| `resize()` | 容器尺寸变化重排 |
| `destroy()` | 销毁 |
| `convertToPictureUrl(includeOverlay?, type?, backgroundColor?)` | 截图 URL |
| `getDom(paneId?, position?)` / `getSize(paneId?, position?)` / `getContainer()` | DOM（平台不适用候选） |
| `getDrawPaneById(id)` / `getDrawPanes()` / `getSeparatorPanes()` / `layout(options)` / `updatePane(level, paneId?)` | 内部布局操作 |
| `getChartStore()` / `getXAxisPane()` | 内部对象 |

## 3. Options（init 第二参）

```ts
{ locale?, timezone?, styles?: string|DeepPartial<Styles>, formatter?: Partial<Formatter>,
  thousandsSeparator?, decimalFold?, zoomAnchor?, hotkey?, layout? }
```

## 4. Styles 树（common/Styles.ts，节级；字段级在阶段 D 细化）

`Styles = { grid, candles, indicator, tooltip, crosshair, overlay, separator, xAxis, yAxis }`
- `CandleStyle{type: CandleType('candle_solid'|'candle_stroke'|'candle_up_stroke'|'candle_down_stroke'|'ohlc'|'area'), bar: CandleBarColor|回调, area, priceMark(high/low/last), tooltip(legacy)}`
- `IndicatorStyle{ohlc(bars compareRule), bars[], lines[], circles[], texts[], lastValueMark{show,text}, tooltip{title{showName,showParams},legend{defaultValue}}}`
- `TooltipStyle{offset, showRule, showType, title, legend{color,size,family,weight,margin,defaultValue}, features{path|icon_font, position}}`
- `CrosshairStyle{show, horizontal{text,features}, vertical{text}}`
- `OverlayStyle{point{color,borderColor,borderSize,radius,activeColor,activeBorderColor,activeBorderSize,activeRadius}, line, rect, polygon, circle, arc, text}`
- `SeparatorStyle{size,color,fill}`、`XAxisStyle/YAxisStyle{show,name,minimum/maximum,scrollZoomPosition,tickText,tickLine,axisLine,custom}`

## 5. KLineData

`{timestamp, open, high, low, close, volume?, turnover?}`（common/Data.ts）。

## 6. SymbolInfo / Period

`SymbolInfo{ticker, pricePrecision, volumePrecision}`；`Period{type: 'second'|'minute'|'hour'|'day'|'week'|'month'|'year', milliseconds?}`。
