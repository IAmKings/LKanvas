# 差距矩阵（TS ↔ Kotlin 对齐现状）

> A3 产物。分类：**A** 直接对齐（已有同名等价物）｜**B** 适配（语义同需映射/重命名）｜**C** 平台不适用（附 Android 等价说明）｜**D** 有意差异（D-01/D-02/D-03 或 PRD 登记）｜**E** 缺失（需实现）。
> 工作量：S（小时级）/ M（天级）/ L（多天）。批次 = 执行批次 1~6。
> 状态统计见文末；**unknown 必须为 0**。

## 1. index.ts 顶层导出

| TS 符号 | Kotlin 现状 | 分类 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|
| `init(ds, options?)` | E：`LKanvasChart` 门面未建（现 KLineChart composable + ChartState） | B | M | 2 | Android 无 DOM：ds → `init(state, options)` 或工厂 |
| `dispose(dcs)` | E：scope 未暴露销毁 | B | S | 2 | 映射 scope.cancel + engine.close |
| `version()` | A：`LKanvas.VERSION` | B | S | 2 | 门面补同名静态 |
| `utils.clone/merge` | B：Kotlin data class copy / 自研 merge 未抽公共 | E | S | 2 | `LkanvasUtils.merge(DeepPartial)` |
| `utils.isString/…` 7 项 | E（Kotlin 类型系统等价不需要） | C | S | 2 | 提供空实现或不移植（矩阵登记） |
| `utils.formatValue/formatPrecision/formatBigNumber/formatThousands/formatFoldDecimal/formatDate` | B：分散在 ChartText.price/volume/ChartLocale | E | M | 2 | 抽 `LkanvasUtils` 单例对齐命名 |
| `utils.calcTextWidth` | B：ChartText.measureWidth | E | S | 2 | 同上 |
| `utils.getLinearSlopeIntercept/getLinearYFrom*` | B：OverlayGeometry 内联（parallelLine 斜率计算） | E | S | 2 | 抽公共 |
| `utils.checkCoordinateOn*` 6 种 | A：FigureHit（7 种含 path） | B | S | 4 | 同名包装 |
| `registerIndicator/getSupportedIndicators` | B：IndicatorRegistry（实例级，无全局名注册表） | E | M | 3 | 门面静态全局注册表 |
| `registerOverlay/getSupportedOverlays/getOverlayClass` | B：OverlayTemplates（16 内置 + 手动单例） | E | M | 4 | 统一注册表 |
| `registerFigure/getFigureClass/getSupportedFigures` | B：Figure 原语无注册表 | E | S | 4 | |
| `registerStyles`（dark/light） | B：ChartTheme.dark()（字段树不同，见 §5） | E | M | 5 | |
| `registerLocale/getSupportedLocales` | B：ChartLocale.ZH_CN/EN_US 常量（无注册表） | E | S | 3 | |
| `registerXAxis/registerYAxis` | E：YAxisSpec 存在但无注册体系 | E | M | 4 | |
| `registerHotkey/getHotkey/getSupportedHotkeys` | E：无（浏览器键鼠） | C | S | 2 | Android 键鼠外设 P2（PRD 3.3） |

## 2. Chart 数据组（v10 DataLoader 模式）

| TS 符号 | Kotlin 现状 | 分类 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|
| `setDataLoader(loader)` | B：ChartLoader + HistorySource（构造注入，非运行时替换） | E | M | 2 | **v10 无 setData/applyNewData/updateData（v9 遗留已删）**；门面提供 loader + 直接提交双通道 |
| `DataLoader.getBars{type: init/forward/backward/update}` | A：HistorySource.loadInitial/loadEarlier + DeterministicDataSource | B | M | 2 | type 四值语义一一对应；callback 风格 vs suspend 适配 |
| `DataLoader.subscribeBar/unsubscribeBar` | B：RealtimeSource/Subscription | B | S | 2 | |
| `DataLoadMore = boolean\|{backward,forward}` | B：HistorySource.Page.hasMore（单方向） | E | S | 2 | 补双向对象形式 |
| `getDataList()` | A：ChartState.dataSnapshot.candles / store.candlesView | B | S | 2 | |
| `resetData()` | A：ChartDataStore.reset | B | S | 2 | |
| `setSymbol/getSymbol` | B：MarketKey（symbol/period/adjust） | E | S | 2 | SymbolInfo{ticker,pricePrecision,volumePrecision} → 补精度字段到 MarketKey 或旁路 |
| `setPeriod/getPeriod` | A：MarketKey.period（字符串） | B | S | 2 | Period{type,milliseconds} 类型化 |
| `setLoadMoreDataCount` | E | E | S | 2 | ChartLoader 触发阈值 |

## 3. Chart 指标组

| TS 符号 | Kotlin 现状 | 分类 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|
| `createIndicator(value, isStack?)` | B：IndicatorRegistry/ChartState 内置 3 实例；无运行时按名创建公开 API | E | M | 2/3 | isStack（同 pane 叠加）依赖 pane 模型（阶段 D/pane 项） |
| `overrideIndicator` | B：IndicatorInstance.updateParams（部分） | E | M | 2 | override 面宽（styles/visible/calcParams/extendData…） |
| `getIndicators(filter)` | B：ChartState 私有 computeInstances | E | S | 2 | filter{id,name,paneId,isStack} |
| `removeIndicator(filter)` | E（内置实例不可移除） | E | M | 2 | |
| `getIndicatorByInstanceId(id)` | E | E | S | 2 | |
| Indicator 22 字段 | B：Definition 仅 name/shortName/calcParams/precision/series/plots/incremental | E | L | 3 | minValue/maxValue/shouldOhlc/shouldFormatBigNumber/visible/zLevel/extendData/styles/shouldUpdate/createTooltipDataSource/draw/figures 回调（批次 3 C1） |
| `series` 三值 | A：IndicatorSeries（PRICE/VOLUME/NORMAL） | A | — | — | |
| calc 逐位语义 | A：oracle fixtures 逐位对照（27/27） | A | — | — | L1 已达标 |

## 4. Chart 覆盖物组

| TS 符号 | Kotlin 现状 | 分类 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|
| `createOverlay(value, paneId?)` | B：OverlayManager.beginCreate（交互创建）/ restoreFrom（编程创建不完整） | E | M | 2 | 编程式两点直接完成需补 |
| `getOverlays(filter)` | B：all()（无 filter） | E | S | 2 | filter{id,groupId,name,paneId}；groupId 缺 |
| `overrideOverlay` | E | E | M | 2 | points/styles/extendData/locked/visible |
| `removeOverlay(filter)` | B：remove/removeSelected | E | S | 2 | 支持数组/批量 |
| OverlayTemplate 字段 | B：Definition 仅 name/pointCount/constraint/continuous/maxPoints | E | L | 4 | totalStep/needDefault*Figure/create*Figures/checkEventOn/drawingMode/performEvent*/on* 回调（批次 4 C3） |
| `OverlayMode` 三档 | A：MagnetMode（NORMAL/WEAK/STRONG） | B | S | 2 | 门面同名枚举 |
| `drawingMode(step\|continuous)` | A：continuous 布尔 | B | S | 4 | |
| 16 内置覆盖物 totalStep 等元数据 | B：pointCount（totalStep-1） | B | S | 4 | C4 自动 diff |
| 斐波那契价格空间投影 | A：实现 | D-02 | — | — | 有意差异登记 |

## 5. Chart 视口组

| TS 符号 | Kotlin 现状 | 分类 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|
| `setBarSpace(space)` | B：Viewport.barSpacing + zoomBy | E | S | 2 | BarSpace{bar,halfBar} 结构 |
| `getBarSpace()` | E | E | S | 2 |
| `getVisibleRange()` | B：Viewport.visibleRange（compose 扩展） | E | S | 2 | VisibleRange{from,to,realFrom,realTo} 结构对齐 |
| `scrollByDistance(d, animation?)` | B：Viewport.scrollBy | E | M | 2 | 动画补（ValueAnimator/Animatable） |
| `scrollToRealTime(animation?)` | B：scrollToLatest | E | S | 2 | |
| `scrollToDataIndex/scrollToTimestamp` | A：Viewport.scrollToIndex/scrollToTimestamp | B | S | 2 |
| `scrollToTimestamp(timestamp, animation?)` | A：同上（时间重载） | B | S | 2 |
| `zoomAtDataIndex(scale, dataIndex, animation?)` | E：仅按 px 锚点，缺索引重载 | E | S | 2 |
| `zoomAtTimestamp(scale, timestamp, animation?)` | E：缺时间重载 | E | S | 2 | |
| `zoomAtCoordinate/zoomAtDataIndex/zoomAtTimestamp` | B：zoomBy(anchorX) | E | S | 2 | 坐标系换算 |
| `setOffsetRightDistance(distance)` | E：rightOffsetSlots（槽位非 px） | E | S | 2 | px→槽位换算 |
| `getOffsetRightDistance()` | E | E | S | 2 |
| `setMaxOffsetLeft/RightDistance` | E | E | S | 2 | overscroll 语义 |
| `setMaxOffsetLeftDistance(d)` | E | E | S | 2 | 同上（单侧） |
| `setMaxOffsetRightDistance(d)` | E | E | S | 2 | 同上（单侧） |
| `setLeftMinVisibleBarCount(count)` | E | E | S | 2 | 左侧最小可见数 clamp |
| `setRightMinVisibleBarCount(count)` | E | E | S | 2 | 右侧最小可见数 clamp |
| `setZoomEnabled(enabled)` | E | E | S | 2 | 手势层开关 |
| `isZoomEnabled()` | E | E | S | 2 |
| `setScrollEnabled(enabled)` | E | E | S | 2 | |
| `isScrollEnabled()` | E | E | S | 2 |
| `setZoomAnchor(anchor)` | D：缩放锚点按手势（触点）实现 | E | S | 2 | cursor/last_bar 语义等价登记 |
| `getZoomAnchor()` | E | E | S | 2 |

## 6. Chart 配置组

| TS 符号 | Kotlin 现状 | 分类 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|
| `setStyles(string\|DeepPartial)` / `getStyles` | B：ChartTheme（字段树不同）+ registerStyles 未建 | E | L | 5 | 阶段 D：Styles 树全量定义 |
| `setLayoutOptions/getLayoutOptions` | E（barSpaceLimit/pane/yAxis） | E | M | 5 | |
| `setFormatter`（formatDate/formatBigNumber/formatExtendText） | B：ChartText/ChartLocale/ChartState timeText lambda | E | S | 2 | Formatter 接口对齐 |
| `setLocale(locale)` | B：ChartLocale + state.locale（T-24） | E | S | 2 | setLocale('en-US') 映射 EN_US |
| `getLocale()` | E | E | S | 2 |
| `setTimezone(tz)` | B：ChartText 用 TimeZone.getDefault | E | S | 2 | 显式时区注入补齐 |
| `getTimezone()` | E | E | S | 2 |
| `setThousandsSeparator/setDecimalFold` | B：DecimalFormat 千分位有；小数折叠无 | E | S | 2 | |
| `setDecimalFold(opts)/getDecimalFold()` | E：小数折叠未实现 | E | S | 2 | `{threshold, precision}` |
| `setHotkey` | C | C | — | — | 浏览器键鼠；Android 外接键鼠 P2 |
| `setPaneOptions/getPaneOptions` | B：adjustPane（拖动）+ PaneHeights | E | M | 2 | pane{id,height,axis?} |
| `createYAxis/removeYAxis/getYAxes/overrideYAxis/overrideXAxis` | B：YAxisSpec/CustomYAxis（单主轴规格） | E | L | 5 | 多轴实例化在固定三 pane 内渐进 |
| `removeYAxis(filter)` | E | E | S | 5 | |
| `getYAxes(filter)` | E | E | S | 5 | |
| `overrideYAxis(yAxis)` | E | E | S | 5 | |
| `overrideXAxis(xAxis)` | E | E | S | 5 | |
| `unsubscribeAction(type, callback?)` | E（随 subscribeAction 一起实现） | E | S | 2 | |

## 7. 事件与生命周期

| TS 符号 | Kotlin 现状 | 分类 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|
| `subscribeAction/unsubscribeAction` 9 种 | B：分散回调（onCandleClick/onSnapshot/onArchiveDirty/onEvent） | E | M | 2 | ActionType 枚举 + 统一注册表 |
| `executeAction(type, data)` | E | E | S | 2 | |
| `onCrosshairChange` | B：crosshairPosition 可观察 | E | S | 2 | |
| `onVisibleRangeChange` | B：viewRevision + viewport（无回调） | E | S | 2 | |
| `onZoom/onScroll` | B：手势内无区分回调 | E | S | 2 | |
| `onCandleBarClick` | A：onCandleClick | B | S | 2 | |
| `onPaneDrag` | B：PANE_ADJUST 手势（无回调） | E | S | 2 | |
| 三个 FeatureClick（tooltip 图标点击） | E（tooltip features 未实现） | E | M | 5 | 依赖阶段 D tooltip |
| `resize()` | A：onLayout 自动 | A | — | — | |
| `destroy()` | E：scope 未暴露销毁 | E | S | 2 | |
| `convertToPictureUrl(includeOverlay,type,bg)` | B：ChartSnapshotExporter（PNG only） | E | S | 2 | 补 jpeg/bmp 编码与背景色参 |
| `getDom/getSize/getContainer` | C（Android 无 DOM）→ 提供尺寸查询等价 | C | S | 2 | getBounding(paneId?) |
| `getDrawPaneById/getDrawPanes/getSeparatorPanes/layout/updatePane` | B：PaneHeights/adjustPane（简化 pane 模型） | E | M | 5 | 固定三 pane 的渐进开放 |
| `getChartStore/getXAxisPane` | C（内核不暴露） | D | — | — | 架构决策：内核封装 |

## 8. Options（init 第二参）

| TS 字段 | Kotlin 现状 | 分类 | 工作量 | 批次 |
|---|---|---|---|---|
| `locale` | B：ChartLocale + state.locale | E | S | 2 |
| `timezone` | B：默认时区（无注入） | E | S | 2 |
| `styles: string\|DeepPartial` | B：ChartTheme 实例 | E | L | 5 |
| `formatter` | B：lambda 分散 | E | S | 2 |
| `thousandsSeparator/decimalFold` | B：部分内建 | E | S | 2 |
| `zoomAnchor/hotkey` | D/C | C | — | 2 |
| `layout` | E | E | M | 5 |

## 9. Styles 树（接口级；字段级明细在批次 5 时细化追加）

| TS 接口 | Kotlin 现状 | 分类 | 工作量 | 批次 |
|---|---|---|---|---|
| `Styles` 9 节 | B：ChartTheme 平面色板（字段树不同） | E | L | 5 |
| `CandleStyle.type` 六值 | A：MainChartType（6 种同名） | A | — | — |
| `CandleBarColor`（含回调） | B：up/down/noChange 常量色 | E | M | 5 |
| `IndicatorStyle{ohlc,bars,lines,circles,texts,lastValueMark,tooltip}` | B：linePalette + 固定绘制 | E | L | 5 |
| `TooltipStyle`（showRule/showType/legend/features） | B：图例（slot 定制已支持） | E | L | 5 |
| `CrosshairStyle` | B：crosshairLine 单色 | E | M | 5 |
| `OverlayStyle`（point 六态等） | B：OverlayRenderer 常量 | E | M | 5 |
| `XAxisStyle/YAxisStyle` | B：axisText/axisLine | E | M | 5 |
| `SeparatorStyle` | B：axisLine | E | S | 5 |
| `GridStyle` | B：gridLine | E | S | 5 |

## 10. 数据与模型

| TS 类型 | Kotlin 现状 | 分类 | 备注 |
|---|---|---|---|
| `KLineData{timestamp,open,high,low,close,volume?,turnover?}` | A：Candle 字段同名同义 | A | — |
| OHLC 校验（递增/有限） | D：Kotlin 严格校验 + 定位错误（比上游严格，PRD R-01） | D | 登记 |
| `SymbolInfo{ticker,pricePrecision,volumePrecision}` | B：MarketKey 无精度字段 | E | S | 2 |
| `Period{type,milliseconds}` | B：MarketKey.period 字符串 | E | S | 2 |
| `Crosshair{...}` 事件数据 | B：Offset十字线 | E | S | 2 |
| `VisibleRange{from,to,realFrom,realTo}` | B：仅 from/to | E | S | 2 |

## 11. 数值语义（L1）

| 项 | 状态 |
|---|---|
| 27 指标 oracle 逐位对照 | A（fixtures + 手算 + 增量一致性） |
| D-01 percentage 零基准 | D（有意差异） |
| D-02 斐波那契价格投影 | D（有意差异） |
| D-03 缺失字段 Missing | D（有意差异） |
| 视口往返 ≤0.5px | A（A-12 测试） |

## 统计（批次 1 出口）

| 分类 | 数量 |
|---|---|
| A 直接对齐 | 4 |
| B 适配 | 13 |
| C 平台不适用 | 5 |
| D 有意差异 | 1 |
| E 缺失（待实现） | 85 |
| unknown | **0** |
| 合计 | 108 |


E 项全部指派批次（2：26 项 / 3：8 项 / 4：9 项 / 5：19 项），其余 E 在阶段 B~E 进行时并入。注：Styles 字段级（§9 之下）按接口级登记，批次 5 展开字段级后追加矩阵行。
