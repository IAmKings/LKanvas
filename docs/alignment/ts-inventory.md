# TS API 清单（上游 KLineChart v10.1.0）

> 对齐排查 A1 产物（v2 修复版：补行号/事件 payload/默认值/Styles 字段级；纠正 `getConvertPictureUrl` 方法名；补 `convertToPixel`/`convertFromPixel`）。
> 提取方式可复现：方法与行号来自 `grep -nE "^  [a-zA-Z]+\(" Chart.ts`（79 个，不含 constructor）；Styles 接口字段来自 `common/Styles.ts` 正则解析（43 个接口）。完整性由 `tools/alignment/check.py` 校验。
> 每个符号在 gap-matrix.md 中有对应行，状态 ∈ {A 直接对齐, B 适配, C 平台不适用, D 有意差异, E 缺失}。

## 1. index.ts 顶层导出

| 符号 | 说明 |
|---|---|
| `version()` | 构建期替换 `__VERSION__` |
| `init(ds, options?)` | `(HTMLElement\|string, Options?) => Nullable<Chart>`；同一 DOM 重复 init 返回既有实例 |
| `dispose(dcs)` | `(HTMLElement\|Chart\|string) => void` |
| `utils` 25 项 | `clone, merge, isString, isNumber, isValid, isObject, isArray, isFunction, isBoolean, formatValue, formatPrecision, formatBigNumber, formatDate(=formatTimestampByTemplate), formatThousands, formatFoldDecimal, calcTextWidth, getLinearSlopeIntercept, getLinearYFromSlopeIntercept, getLinearYFromCoordinates, checkCoordinateOnArc/Circle/Line/Polygon/Rect/Text` |
| `registerIndicator` / `getSupportedIndicators()` | 指标模板注册（全局） |
| `registerOverlay` / `getSupportedOverlays()` / `getOverlayClass(name)` | 覆盖物模板注册 |
| `registerFigure` / `getFigureClass` / `getSupportedFigures()` | 图形注册 |
| `registerStyles(name, styles)` / 内置 `light`/`dark` | 样式注册（extension/styles/index.ts） |
| `registerLocale(locale)` / `getSupportedLocales()` | i18n；内置 `'zh-CN'`、`'en-US'` |
| `registerXAxis(override)` / `registerYAxis(override)` | 轴注册 |
| `registerHotkey(hotkey)` / `getHotkey()` / `getSupportedHotkeys()` | 浏览器快捷键 |

类型别名导出：`ActionType, CandleTooltipRectPosition, CandleType, Chart, DomPosition, FeatureType, FormatDateType, IndicatorSeries, Layout, LineType, OverlayDrawingMode, OverlayMode, PolygonType, TooltipFeaturePosition, TooltipShowRule, TooltipShowType, YAxisFilter, YAxisOverride, ZoomAnchor`。

## 2. Chart 实例方法（79 个，含行号）

#### 2.1 DOM 与内部布局

| 方法 | 行号 | 签名要点 |
|---|---|---|

#### 2.2 数据与品种周期

| 方法 | 行号 | 签名要点 |
|---|---|---|

#### 2.3 配置

| 方法 | 行号 | 签名要点 |
|---|---|---|

#### 2.4 视口与交互开关

| 方法 | 行号 | 签名要点 |
|---|---|---|

#### 2.5 指标

| 方法 | 行号 | 签名要点 |
|---|---|---|

#### 2.6 覆盖物

| 方法 | 行号 | 签名要点 |
|---|---|---|

#### 2.7 Pane 与轴

| 方法 | 行号 | 签名要点 |
|---|---|---|

#### 2.8 滚动/缩放/坐标转换

| 方法 | 行号 | 签名要点 |
|---|---|---|

#### 2.9 事件与生命周期

| 方法 | 行号 | 签名要点 |
|---|---|---|

#### 2.10 其他

| 方法 | 行号 | 签名要点 |
|---|---|---|
| `constructor` | Chart.ts:148 | () |
| `getDrawPaneById` | Chart.ts:208 | () |
| `getContainer` | Chart.ts:219 | () |
| `getChartStore` | Chart.ts:223 | () |
| `getXAxisPane` | Chart.ts:227 | () |
| `getDrawPanes` | Chart.ts:231 | () |
| `getSeparatorPanes` | Chart.ts:235 | () |
| `layout` | Chart.ts:239 | () |
| `updatePane` | Chart.ts:518 | () |
| `getDom` | Chart.ts:530 | () |
| `getSize` | Chart.ts:553 | () |
| `setSymbol` | Chart.ts:584 | () |
| `getSymbol` | Chart.ts:591 | () |
| `setPeriod` | Chart.ts:595 | () |
| `getPeriod` | Chart.ts:602 | () |
| `setStyles` | Chart.ts:606 | () |
| `getStyles` | Chart.ts:612 | () |
| `setLayoutOptions` | Chart.ts:616 | () |
| `getLayoutOptions` | Chart.ts:620 | () |
| `setFormatter` | Chart.ts:624 | () |
| `getFormatter` | Chart.ts:630 | () |
| `setLocale` | Chart.ts:634 | () |
| `getLocale` | Chart.ts:640 | () |
| `setTimezone` | Chart.ts:644 | () |
| `getTimezone` | Chart.ts:650 | () |
| `setThousandsSeparator` | Chart.ts:654 | () |
| `getThousandsSeparator` | Chart.ts:660 | () |
| `setDecimalFold` | Chart.ts:664 | () |
| `getDecimalFold` | Chart.ts:670 | () |
| `setHotkey` | Chart.ts:674 | () |
| `getHotkey` | Chart.ts:678 | () |
| `getHotKey` | Chart.ts:682 | () |
| `setOffsetRightDistance` | Chart.ts:697 | () |
| `getOffsetRightDistance` | Chart.ts:701 | () |
| `setMaxOffsetLeftDistance` | Chart.ts:705 | () |
| `setMaxOffsetRightDistance` | Chart.ts:713 | () |
| `setLeftMinVisibleBarCount` | Chart.ts:721 | () |
| `setRightMinVisibleBarCount` | Chart.ts:729 | () |
| `setBarSpace` | Chart.ts:737 | () |
| `getBarSpace` | Chart.ts:741 | () |
| `getVisibleRange` | Chart.ts:745 | () |
| `resetData` | Chart.ts:789 | () |
| `getDataList` | Chart.ts:793 | () |
| `setDataLoader` | Chart.ts:797 | () |
| `createIndicator` | Chart.ts:802 | () |
| `overrideIndicator` | Chart.ts:837 | () |
| `getIndicators` | Chart.ts:863 | () |
| `removeIndicator` | Chart.ts:867 | () |
| `createOverlay` | Chart.ts:903 | () |
| `getOverlays` | Chart.ts:939 | () |
| `overrideOverlay` | Chart.ts:943 | () |
| `removeOverlay` | Chart.ts:947 | () |
| `setPaneOptions` | Chart.ts:951 | () |
| `createYAxis` | Chart.ts:1003 | () |
| `removeYAxis` | Chart.ts:1025 | () |
| `getYAxes` | Chart.ts:1057 | () |
| `overrideYAxis` | Chart.ts:1079 | () |
| `overrideXAxis` | Chart.ts:1096 | () |
| `getPaneOptions` | Chart.ts:1106 | () |
| `setZoomEnabled` | Chart.ts:1114 | () |
| `isZoomEnabled` | Chart.ts:1118 | () |
| `setZoomAnchor` | Chart.ts:1122 | () |
| `getZoomAnchor` | Chart.ts:1126 | () |
| `setScrollEnabled` | Chart.ts:1130 | () |
| `isScrollEnabled` | Chart.ts:1134 | () |
| `scrollByDistance` | Chart.ts:1138 | () |
| `scrollToRealTime` | Chart.ts:1154 | () |
| `scrollToDataIndex` | Chart.ts:1161 | () |
| `scrollToTimestamp` | Chart.ts:1166 | () |
| `zoomAtCoordinate` | Chart.ts:1171 | () |
| `zoomAtDataIndex` | Chart.ts:1191 | () |
| `zoomAtTimestamp` | Chart.ts:1196 | () |
| `convertToPixel` | Chart.ts:1201 | () |
| `convertFromPixel` | Chart.ts:1232 | () |
| `executeAction` | Chart.ts:1261 | () |
| `subscribeAction` | Chart.ts:1278 | () |
| `unsubscribeAction` | Chart.ts:1282 | () |
| `getConvertPictureUrl` | Chart.ts:1286 | () |
| `resize` | Chart.ts:1315 | () |
| `destroy` | Chart.ts:1327 | () |
## 3. 事件与 payload（common/Action.ts + 分发点源码）

`ActionType` 9 种；分发点与 payload 形状（自源码提取）：

| ActionType | 分发点 | payload |
|---|---|---|
| `onZoom` | Store.ts:1247 | `{ scale: number }`（前后 barSpace 比） |
| `onScroll` | Store.ts:946 | `{ distance: number }`（px） |
| `onVisibleRangeChange` | Store.ts:742 | `VisibleRange{from,to,realFrom,realTo}` |
| `onCrosshairChange` | Store.ts:1309 | `Crosshair`（见 §5） |
| `onCandleBarClick` | CandleBarView.ts:35 | `VisibleRangeData{dataIndex,x,data:{prev,data,next}}` |
| `onPaneDrag` | SeparatorWidget.ts:149 | `{ paneId: string }` |
| `onCandleTooltipFeatureClick` | tooltip feature 点击 | `{paneId?, featureId?}`（feature 交互） |
| `onIndicatorTooltipFeatureClick` | 同上 | 同上 |
| `onCrosshairFeatureClick` | 同上 | 同上 |

`subscribeAction(type, callback)` / `unsubscribeAction(type, callback?)` / `executeAction(type, data)`（Chart.ts:1278/1282/1261）。

## 4. Options 字段与默认值（Store.ts 构造 + 404~453）

| 字段 | 默认值 | 说明 |
|---|---|---|
| `locale` | `'en-US'`（未注册名回退 en-US 并 logWarn；内置 zh-CN/en-US） | Store.ts:160 |
| `timezone` | `''`（空=系统时区，Intl 默认） | Store.ts:417 |
| `styles` | `light` 主题 merge 默认样式 | `setStyles` 支持字符串\|DeepPartial，`merge()` 深合并（Store.ts:452） |
| `formatter` | 内置 formatDate/formatBigNumber/formatExtendText | |
| `thousandsSeparator` | `{ sign: ',' }` | Store.ts:165 |
| `decimalFold` | `{ threshold: 3 }` | Store.ts:173 |
| `zoomAnchor` | `{ main: 'cursor', xAxis: 'cursor' }`（`'cursor'\|'last_bar'`） | Store.ts:229 |
| `hotkey` | `{ enabled: true, exclude: [] }` | Store.ts:181 |
| `layout` | `barSpaceLimit{min,max}` / `pane` / `yAxis` | 见 Options.ts:88 |

## 5. 公共数据类型（字段级）

| 类型 | 字段 | 源 |
|---|---|---|
| `KLineData` | `timestamp, open, high, low, close, volume?, turnover?` | common/Data.ts |
| `Crosshair` | `x?, y?`（Partial<Coordinate>）+ `paneId?, realX?, timestamp?, kLineData?, dataIndex?, realDataIndex?` | common/Crosshair.ts |
| `BarSpace` | `bar, halfBar, gapBar, halfGapBar` | common/BarSpace.ts |
| `BarSpaceLimit` | `min, max` | 同上 |
| `VisibleRange` | `readonly from, to, realFrom, realTo` | common/VisibleRange.ts |
| `VisibleRangeData` | `dataIndex, x, data: NeighborData<Nullable<KLineData>>`（prev/data/next） | common/Data.ts:36 |
| `SymbolInfo` | `ticker, pricePrecision, volumePrecision`（默认 PRICE=2/VOLUME=2） | common/SymbolInfo.ts |
| `Period` | `type: 'second'\|'minute'\|'hour'\|'day'\|'week'\|'month'\|'year'` + `milliseconds?` | common/Period.ts |
| `DataLoader` | `getBars({type:'init'\|'forward'\|'backward'\|'update', timestamp, symbol, period, callback(data, more?: boolean\|{backward,forward})})`；`subscribeBar?({symbol,period,callback})`；`unsubscribeBar?({symbol,period})` | common/DataLoader.ts |

**v10 关键事实**：`setData/applyNewData/applyMoreData/updateData`（v9 API）已删除，数据接入仅 DataLoader。

## 6. Styles 接口与字段（43 个，字段级）

默认样式：`common/Styles.ts` 的 `getDefault*Style()`（candle.type='candle_solid'，bar.compareRule='current_open'，area.value='close' 等）+ `extension/styles/{light,dark}.ts` 覆盖色板；注册表 `extension/styles/index.ts`。


### 接口字段表

| 接口 | 字段 |
|---|---|
| `Margin` | `marginLeft`, `marginTop`, `marginRight`, `marginBottom` |
| `Padding` | `paddingLeft`, `paddingTop`, `paddingRight`, `paddingBottom` |
| `Offset` | `offsetLeft`, `offsetTop`, `offsetRight`, `offsetBottom` |
| `LineStyle` | `style`, `size`, `color`, `dashedValue` |
| `SmoothLineStyle` | `smooth` |
| `StateLineStyle` | `show` |
| `PathStyle` | `style`, `color`, `lineWidth` |
| `PolygonStyle` | `style`, `color`, `borderColor`, `borderSize`, `borderStyle`, `borderDashedValue` |
| `RectStyle` | `borderRadius` |
| `TextStyle` | `style`, `color`, `size`, `family`, `weight`, `borderStyle`, `borderDashedValue`, `borderSize`, `borderColor`, `borderRadius`, `backgroundColor` |
| `StateTextStyle` | `show` |
| `ChangeColor` | `upColor`, `downColor`, `noChangeColor` |
| `GradientColor` | `offset`, `color` |
| `FeaturePathStyle` | `path` |
| `FeatureIconFontStyle` | `family`, `code` |
| `FeatureStyle` | `id`, `backgroundColor`, `activeBackgroundColor`, `size`, `color`, `activeColor`, `borderRadius`, `type`, `content` |
| `GridStyle` | `show`, `horizontal`, `vertical` |
| `TooltipLegendChild` | `text`, `color` |
| `TooltipLegend` | `title`, `value` |
| `TooltipFeatureStyle` | `position` |
| `TooltipStyle` | `showRule`, `showType`, `features` |
| `CandleAreaPointStyle` | `show`, `color`, `radius`, `rippleColor`, `rippleRadius`, `animation`, `animationDuration` |
| `CandleAreaStyle` | `lineSize`, `lineColor`, `value`, `smooth`, `backgroundColor`, `point` |
| `CandleHighLowPriceMarkStyle` | `show`, `color`, `textOffset`, `textSize`, `textFamily`, `textWeight` |
| `CandleLastPriceMarkStyle` | `show`, `compareRule`, `line`, `text`, `extendTexts` |
| `CandlePriceMarkStyle` | `show`, `high`, `low`, `last` |
| `CandleTooltipRectStyle` | `position` |
| `CandleTooltipStyle` | `title`, `legend`, `rect` |
| `CandleBarColor` | `compareRule`, `upBorderColor`, `downBorderColor`, `noChangeBorderColor`, `upWickColor`, `downWickColor`, `noChangeWickColor` |
| `CandleBarColorCallbackReturnStyle` | `color`, `borderColor`, `wickColor` |
| `CandleStyle` | `type`, `bar`, `area`, `priceMark`, `tooltip` |
| `IndicatorLastValueMarkStyle` | `show`, `text` |
| `IndicatorTooltipStyle` | `title`, `legend` |
| `IndicatorStyle` | `ohlc`, `bars`, `lines`, `circles`, `texts`, `lastValueMark`, `tooltip` |
| `AxisTickLineStyle` | `length` |
| `AxisTickTextStyle` | `marginStart`, `marginEnd` |
| `AxisStyle` | `show`, `size`, `axisLine`, `tickLine`, `tickText` |
| `CrosshairDirectionStyle` | `show`, `line`, `text` |
| `CrosshairStyle` | `show`, `horizontal`, `vertical` |
| `OverlayPointStyle` | `color`, `borderColor`, `borderSize`, `radius`, `activeColor`, `activeBorderColor`, `activeBorderSize`, `activeRadius` |
| `OverlayStyle` | `point`, `line`, `rect`, `polygon`, `circle`, `arc`, `text` |
| `SeparatorStyle` | `size`, `color`, `fill`, `activeBackgroundColor` |
| `Styles` | `grid`, `candle`, `indicator`, `xAxis`, `yAxis`, `separator`, `crosshair`, `overlay` |

## 7. 指标模板字段（component/Indicator.ts:99~）

`Indicator` 接口 22 字段：`id, paneId, yAxisId, name, shortName, precision, calcParams[], shouldOhlc, shouldFormatBigNumber, visible, zLevel, extendData, series('normal'|'price'|'volume'), figures[], minValue?, maxValue?, styles?, shouldUpdate?, calc, regenerateFigures?, createTooltipDataSource?, draw?`。
`IndicatorFigure{key, title, type('line'|'bar'|'circle'), baseValue?, styles?(回调), attrs?(回调)}`；`IndicatorCreate = Partial<Indicator> 去掉 result + 必填 name`；`IndicatorFilter{id?, name?, paneId?, isStack?}`。

## 8. 覆盖物模板字段（component/Overlay.ts:99~226）

`OverlayTemplate`：`name, totalStep, needDefaultPointFigure, needDefaultXAxisFigure, needDefaultYAxisFigure, createPointFigures, createXAxisFigures, createYAxisFigures, checkEventOn, drawingMode('step'|'continuous'), performEventPressedMove, performEventMoveForDrawing, onRemoved, onClick, onDoubleClick, onRightClick, onMouseDown/Enter/Leave/Move/Up, onTouchStart/Move/End, onPressedMove, extendData`。
`OverlayFilter{id?, groupId?, name?, paneId?}`；`OverlayMode = 'normal'|'weak_magnet'|'strong_magnet'`；`OverlayFigure{type, attrs[], styles?, checkEvent?}`。

## 9. 枚举值

`CandleType('candle_solid'|'candle_stroke'|'candle_up_stroke'|'candle_down_stroke'|'ohlc'|'area')`、`LineType('dashed'|'solid')`、`PolygonType('stroke'|'fill'|'stroke_fill')`、`PathType('stroke'|'fill')`、`TooltipShowRule('always'|'follow_cross'|'none')`、`TooltipShowType('standard'|'rect')`、`TooltipFeaturePosition('left'|'middle'|'right')`、`FeatureType('path'|'icon_font')`、`IndicatorSeries('normal'|'price'|'volume')`、`OverlayMode` 三值、`OverlayDrawingMode('step'|'continuous')`、`ZoomAnchorType('cursor'|'last_bar')`、`FormatDateType('tooltip'|'crosshair'|'xAxis')`、`ActionType` 9 值。
