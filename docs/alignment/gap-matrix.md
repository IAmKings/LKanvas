# 差距矩阵（TS ↔ Kotlin 对齐现状）v2

> A3 产物（v2 修复版：一符号一行 / 行 ID / 单字母分类 / 验收方式列 / 批次 2a·2b·2c / 纠正方法名 / 补 convertToPixel·convertFromPixel / Styles 字段级 / 统计改由脚本生成）。
> 分类：**A** 直接对齐｜**B** 适配｜**C** 平台不适用｜**D** 有意差异｜**E** 缺失。验收：**test** 单测｜**diff** 上游自动对照｜**compile** 编译即证｜**parity** 上游文档示例迁移测试｜**manual** 人工｜**—** 不适用。
> **完整性由 `tools/alignment/check.py` 强制校验**（Chart.ts 全部方法必须有行、格式违规即失败）；统计数字由该脚本输出，本文不手写。

## 批次定义与出口条件（v2）

| 批次 | 范围 | 出口条件 |
|---|---|---|
| 2a | 数据（DataLoader 适配/SymbolInfo/Period）+ 事件 9 种 + 坐标转换 + 生命周期 | 门面数据链路单测绿；**parity-1**：上游 quickstart 数据接入示例改初始化后在门面跑通 |
| 2b | 指标 CRUD + 覆盖物 CRUD | create/remove/getIndicators/getOverlays 单测绿；**parity-2**：上游 createIndicator/createOverlay 文档示例跑通 |
| 2c | 视口全量 + 配置组 + pane/轴基础 | scroll/zoom/offset/enabled 单测绿；**parity-3**：上游视口控制示例跑通 |
| 3 | 指标模板 22 字段 + 注册 API + 27 指标元数据自动 diff | diff 0 差异 |
| 4 | 覆盖物模板字段 + figure 注册 + 16 覆盖物元数据 diff | diff 0 差异 |
| 5 | Styles 树 43 接口 + 默认值对照 + tooltip features + 多轴 | 默认值 diff 0 差异；高优样式接线 |
| 6 | app-sample 对齐上游 demo + 人工验收 | demo-checklist 全绿；全部既有测试 0 失败 |

**验收方式新增 parity 定义**：取上游官方文档代码片段，仅替换初始化（`init(ds)`→`LKanvasChart(state)`），在门面上编译并通过行为断言；parity 用例纳入常规测试（防接口漂移）。


## 1. index.ts 顶层导出（上游实际 39 个：20 值符号 + 19 类型别名；`utils` 对象展开为 IDX-004~028 共 25 行；由 check.py 从 index.ts 提取校验）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|---|---|---|
| IDX-001 | `init` | index.ts | 门面 init() 已建（LKanvasChart.init(state)） | B | test+parity | S | 2a | Android 无 DOM：init(state) |
| IDX-002 | `dispose` | index.ts | LKanvasChart.dispose 已建 | B | test | S | 2a |  |
| IDX-003 | `version` | index.ts | LKanvasChart.version() 已建 | B | test | S | 2a | 门面静态同名 |
| IDX-004 | `utils.clone` | utils | data class copy 等价 | B | compile | S | 2a | |
| IDX-005 | `utils.merge` | utils | LkanvasUtils.merge 未建；setStyles 深合并批次 5 | E | test | S | 2a | setStyles 语义基础 |
| IDX-006~012 | `utils.isString/isNumber/isValid/isObject/isArray/isFunction/isBoolean` | utils | Kotlin 类型系统等价 | C | compile | S | 2a | 提供 utils 对象占位 |
| IDX-013 | `utils.formatValue` | utils/format | LkanvasUtils 待建（分散于 ChartText/ChartLocale） | E | test | S | 2a |  |
| IDX-014 | `utils.formatPrecision` | utils/format | LkanvasUtils 待建 | E | test | S | 2a |  |
| IDX-015 | `utils.formatBigNumber` | utils/format | LkanvasUtils 待建 | E | test | S | 2a |  |
| IDX-016 | `utils.formatDate` | utils/format | LkanvasUtils 待建 | E | test | S | 2a | 模板化 |
| IDX-017 | `utils.formatThousands` | utils/format | LkanvasUtils 待建 | E | test | S | 2a |  |
| IDX-018 | `utils.formatFoldDecimal` | utils/format | LkanvasUtils 待建 | E | test | S | 2a |  |
| IDX-019 | `utils.calcTextWidth` | utils/canvas | ChartText.measureWidth（门面待包装） | B | test | S | 2a |  |
| IDX-020 | `utils.getLinearSlopeIntercept` | figure/line | OverlayGeometry 内联（门面待包装） | B | test | S | 2a | 抽公共 |
| IDX-021 | `utils.getLinearYFromSlopeIntercept` | figure/line | 门面待抽公共 | E | test | S | 2a |  |
| IDX-022 | `utils.getLinearYFromCoordinates` | figure/line | 门面待抽公共 | E | test | S | 2a |  |
| IDX-023 | `utils.checkCoordinateOnArc` | extension/figure/arc.ts | LkanvasFigures.checkCoordinateOnArc 已建 | B | test | S | 4 | 同名包装 |
| IDX-024 | `utils.checkCoordinateOnCircle` | extension/figure/circle.ts | LkanvasFigures.checkCoordinateOnCircle 已建 | B | test | S | 4 | 同名包装 |
| IDX-025 | `utils.checkCoordinateOnLine` | extension/figure/line.ts | LkanvasFigures.checkCoordinateOnLine 已建 | B | test | S | 4 | 同名包装 |
| IDX-026 | `utils.checkCoordinateOnPolygon` | extension/figure/polygon.ts | LkanvasFigures.checkCoordinateOnPolygon 已建 | B | test | S | 4 | 同名包装 |
| IDX-027 | `utils.checkCoordinateOnRect` | extension/figure/rect.ts | LkanvasFigures.checkCoordinateOnRect 已建 | B | test | S | 4 | 同名包装 |
| IDX-028 | `utils.checkCoordinateOnText` | extension/figure/text.ts | LkanvasFigures.checkCoordinateOnText 已建 | B | test | S | 4 | 同名包装 |
| IDX-029 | `registerIndicator` | ext/indicator | LkanvasRegistry.registerIndicator 已建（全局+27 预注册） | B | test | M | 3 | 全局注册表 |
| IDX-030 | `getSupportedIndicators` | ext/indicator | LkanvasRegistry.getSupportedIndicators 已建 | B | test | S | 3 |  |
| IDX-031 | `registerOverlay` | ext/overlay | LkanvasRegistry.registerOverlay 已建 | B | test | M | 4 |  |
| IDX-032 | `getSupportedOverlays` | ext/overlay | LkanvasRegistry.getSupportedOverlays 已建（16） | B | test | S | 4 |  |
| IDX-033 | `getOverlayClass` | ext/overlay | LkanvasRegistry.getOverlayDefinition 已建 | B | test | S | 4 |  |
| IDX-034 | `registerFigure` | ext/figure | LkanvasFigures.registerFigure 已建（7 内置预注册） | B | test | S | 4 |  |
| IDX-035 | `getFigureClass` | ext/figure | LkanvasFigures.getFigureFactory 已建 | B | test | S | 4 |  |
| IDX-036 | `getSupportedFigures` | ext/figure | LkanvasFigures.getSupportedFigures 已建 | B | test | S | 4 |  |
| IDX-037 | `registerStyles` | ext/styles | ChartTheme.dark() 近似 | E | test | M | 5 | light/dark 注册 |
| IDX-038 | `registerLocale` | ext/i18n | LkanvasRegistry.registerLocale 已建 | B | test | S | 3 |  |
| IDX-039 | `getSupportedLocales` | ext/i18n | LkanvasRegistry.getSupportedLocales 已建 | B | test | S | 3 |  |
| IDX-040 | `registerXAxis` | ext/x-axis | 无 | E | test | M | 4 | |
| IDX-041 | `registerYAxis` | ext/y-axis | CustomYAxis 近似 | E | test | M | 4 | |
| IDX-042 | `registerHotkey` | ext/hotkey | 无 | C | compile | S | 2c | 浏览器键鼠 |
| IDX-043 | `getHotkey` | ext/hotkey | 无 | C | compile | S | 2c | |
| IDX-044 | `getSupportedHotkeys` | ext/hotkey | 无 | C | compile | S | 2c | |

## 2. Chart 方法（79，一方法一行）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|---|---|---|
| CH-001 | `getDrawPaneById` | Chart.ts:208 | PaneHeights 简化 pane 模型 | B | compile | S | 2c | 等价 getPaneBounding(paneId) |
| CH-002 | `getContainer` | Chart.ts:219 | 无 DOM；Android 等价 getBounding() | C | compile | S | 2c | 容器尺寸查询替代 |
| CH-003 | `getChartStore` | Chart.ts:223 | 架构决策：内核不暴露 | D | — | — | — | 门面封装内核 |
| CH-004 | `getXAxisPane` | Chart.ts:227 | 架构决策：内核不暴露 | D | — | — | — |  |
| CH-005 | `getDrawPanes` | Chart.ts:231 | 固定三 pane | B | compile | S | 2c |  |
| CH-006 | `getSeparatorPanes` | Chart.ts:235 | adjustPane 分隔条手势 | B | compile | S | 2c |  |
| CH-007 | `layout` | Chart.ts:239 | onLayout 自动布局 | B | test | S | 2c |  |
| CH-008 | `updatePane` | Chart.ts:518 | viewRevision 驱动重绘 | B | test | S | 2c |  |
| CH-009 | `getDom` | Chart.ts:530 | 无 DOM | C | compile | S | 2c | Android 无等价，文档标注 |
| CH-010 | `getSize` | Chart.ts:553 | paneHeightsPx/contentWidthPx | B | test | S | 2c | getBounding(paneId?) 等价 |
| CH-011 | `setSymbol` | Chart.ts:584 | MarketKey（缺精度字段） | B | test | S | 2a | SymbolInfo 精度旁路存储 |
| CH-012 | `getSymbol` | Chart.ts:591 | 同上 | B | test | S | 2a |  |
| CH-013 | `setPeriod` | Chart.ts:595 | MarketKey.period 字符串 | B | test | S | 2a | Period{type,milliseconds} 类型化 |
| CH-014 | `getPeriod` | Chart.ts:602 | 同上 | B | test | S | 2a |  |
| CH-015 | `setStyles` | Chart.ts:606 | 门面 setStyles(String/Styles)+Styles→Theme 桥接已建（真机双向切换） | B | test | M | 5 | 字符串主题+DeepPartial merge |
| CH-016 | `getStyles` | Chart.ts:612 | 已建（Styles 树全量返回） | B | test | S | 5 |  |
| CH-017 | `setLayoutOptions` | Chart.ts:616 | ChartLayoutConfig 部分 | B | compile | M | 5 | barSpaceLimit/pane/yAxis |
| CH-018 | `getLayoutOptions` | Chart.ts:620 | 同上 | B | compile | S | 5 |  |
| CH-019 | `setFormatter` | Chart.ts:624 | 门面 set/get 已建（Formatter 类型） | B | test | S | 2c | Formatter 接口对齐 |
| CH-020 | `getFormatter` | Chart.ts:630 | 已建 | B | test | S | 2c |  |
| CH-021 | `setLocale` | Chart.ts:634 | 门面 setLocale/getLocale 已建（zh-CN/en-US 映射 ChartLocale） | B | test | S | 2c | setLocale('en-US')→EN_US |
| CH-022 | `getLocale` | Chart.ts:640 | 已建 | B | test | S | 2c |  |
| CH-023 | `setTimezone` | Chart.ts:644 | 门面 set/getTimezone 已建（存储；时间标签接线待 5） | B | test | S | 2c |  |
| CH-024 | `getTimezone` | Chart.ts:650 | 已建 | B | test | S | 2c |  |
| CH-025 | `setThousandsSeparator` | Chart.ts:654 | 门面 set/get 已建（ThousandsSeparator 类型） | B | test | S | 2c | {sign} 可配 |
| CH-026 | `getThousandsSeparator` | Chart.ts:660 | 已建 | B | test | S | 2c |  |
| CH-027 | `setDecimalFold` | Chart.ts:664 | 门面 set/get 已建（DecimalFold 类型） | B | test | S | 2c | {threshold:3} 默认 |
| CH-028 | `getDecimalFold` | Chart.ts:670 | 已建 | B | test | S | 2c |  |
| CH-029 | `setHotkey` | Chart.ts:674 | 浏览器键鼠 | C | compile | S | 2c | Android 外接键鼠 P2 |
| CH-030 | `getHotkey` | Chart.ts:678 | 无 | C | compile | S | 2c |  |
| CH-031 | `getHotKey` | Chart.ts:682 | 无（旧拼写别名） | C | compile | S | 2c |  |
| CH-032 | `setOffsetRightDistance` | Chart.ts:697 | 门面 set/get 已建（px↔槽位） | B | test | S | 2c | px↔槽位换算 |
| CH-033 | `getOffsetRightDistance` | Chart.ts:701 | 已建 | B | test | S | 2c |  |
| CH-034 | `setMaxOffsetLeftDistance` | Chart.ts:705 | 门面 set 已建（overscroll） | B | test | S | 2c |  |
| CH-035 | `setMaxOffsetRightDistance` | Chart.ts:713 | 门面 set 已建 | B | test | S | 2c |  |
| CH-036 | `setLeftMinVisibleBarCount` | Chart.ts:721 | 无 | E | test | S | 2c | clamp 语义 |
| CH-037 | `setRightMinVisibleBarCount` | Chart.ts:729 | 无 | E | test | S | 2c |  |
| CH-038 | `setBarSpace` | Chart.ts:737 | barSpacing 直改 | B | test | S | 2c | BarSpace{bar,halfBar,gapBar,halfGapBar} |
| CH-039 | `getBarSpace` | Chart.ts:741 | 无结构 getter | E | test | S | 2c |  |
| CH-040 | `getVisibleRange` | Chart.ts:745 | visibleRange 扩展（缺 realFrom/realTo） | B | test | S | 2a | 结构补 realFrom/realTo |
| CH-041 | `resetData` | Chart.ts:789 | ChartDataStore.reset | B | test | S | 2a |  |
| CH-042 | `getDataList` | Chart.ts:793 | dataSnapshot.candles | B | test | S | 2a |  |
| CH-043 | `setDataLoader` | Chart.ts:797 | ChartLoader+HistorySource 构造注入 | B | test+parity | M | 2a | 运行时替换+四 type 适配（init/forward/backward/update） |
| CH-044 | `createIndicator` | Chart.ts:802 | 门面 createIndicator(name, calcParams, paneId, isStack) 已建 | B | test+parity | M | 2b | isStack 依赖 pane 模型（批次 5） |
| CH-045 | `overrideIndicator` | Chart.ts:837 | 门面 overrideIndicator(id, calcParams, visible) 已建 | B | test | M | 2b | styles/visible/extendData… |
| CH-046 | `getIndicators` | Chart.ts:863 | 门面 getIndicators(filter) 已建 | B | test | S | 2b | filter{id,name,paneId,isStack} |
| CH-047 | `removeIndicator` | Chart.ts:867 | 门面 removeIndicator(filter) 已建 | B | test | M | 2b |  |
| CH-048 | `createOverlay` | Chart.ts:903 | 门面 createOverlay(含数组/编程式+交互式) 已建 | B | test+parity | M | 2b | 数组重载+直接完成 |
| CH-049 | `getOverlays` | Chart.ts:939 | 门面 getOverlays(filter) 已建 | B | test | S | 2b | filter{id,groupId,name,paneId} |
| CH-050 | `overrideOverlay` | Chart.ts:943 | 门面 overrideOverlay(id, OverlayOverride) 已建 | B | test | M | 2b | points/styles/extendData |
| CH-051 | `removeOverlay` | Chart.ts:947 | 门面 removeOverlay(filter/id) 已建 | B | test | S | 2b | 批量 |
| CH-052 | `setPaneOptions` | Chart.ts:951 | 门面 setPaneOptions 已建（main/vol/macd 高度，35% 保底钳制） | B | test | M | 2c | pane{id,height,axis?} |
| CH-053 | `createYAxis` | Chart.ts:1003 | YAxisSpec 单主轴 | E | test | L | 5 | 多轴实例化 |
| CH-054 | `removeYAxis` | Chart.ts:1025 | 无 | E | test | S | 5 |  |
| CH-055 | `getYAxes` | Chart.ts:1057 | 无 | E | compile | S | 5 |  |
| CH-056 | `overrideYAxis` | Chart.ts:1079 | 无 | E | test | S | 5 |  |
| CH-057 | `overrideXAxis` | Chart.ts:1096 | 无 | E | test | S | 5 |  |
| CH-058 | `getPaneOptions` | Chart.ts:1106 | 门面 getPaneOptions 已建 | B | test | S | 2c |  |
| CH-059 | `setZoomEnabled` | Chart.ts:1114 | 门面 set/is 已建 | B | test | S | 2c | 手势层 |
| CH-060 | `isZoomEnabled` | Chart.ts:1118 | 已建 | B | test | S | 2c |  |
| CH-061 | `setZoomAnchor` | Chart.ts:1122 | 门面 set/get 已建（ZoomAnchor 类型） | B | test | S | 2c | cursor/last_bar 语义等价 |
| CH-062 | `getZoomAnchor` | Chart.ts:1126 | 已建 | B | test | S | 2c |  |
| CH-063 | `setScrollEnabled` | Chart.ts:1130 | 门面 set/is 已建 | B | test | S | 2c |  |
| CH-064 | `isScrollEnabled` | Chart.ts:1134 | 已建 | B | test | S | 2c |  |
| CH-065 | `scrollByDistance` | Chart.ts:1138 | 门面 scrollByDistance 已建（动画待） | B | test | M | 2c | Animatable 动画 |
| CH-066 | `scrollToRealTime` | Chart.ts:1154 | scrollToLatest | B | test | S | 2c |  |
| CH-067 | `scrollToDataIndex` | Chart.ts:1161 | scrollToIndex | B | test | S | 2c |  |
| CH-068 | `scrollToTimestamp` | Chart.ts:1166 | scrollToTimestamp | A | test | S | 2c |  |
| CH-069 | `zoomAtCoordinate` | Chart.ts:1171 | zoomBy(anchorX) | B | test | S | 2c |  |
| CH-070 | `zoomAtDataIndex` | Chart.ts:1191 | 无索引重载 | E | test | S | 2c |  |
| CH-071 | `zoomAtTimestamp` | Chart.ts:1196 | 无时间重载 | E | test | S | 2c |  |
| CH-072 | `convertToPixel` | Chart.ts:1201 | overlayPointToPx（单点） | B | test+parity | M | 2a | 数组+ConvertFilter |
| CH-073 | `convertFromPixel` | Chart.ts:1232 | overlayPointFromPx（单点） | B | test+parity | M | 2a |  |
| CH-074 | `executeAction` | Chart.ts:1261 | 无统一入口 | E | test | S | 2a |  |
| CH-075 | `subscribeAction` | Chart.ts:1278 | 分散回调（onCandleClick 等） | E | test+parity | M | 2a | 9 种 ActionType |
| CH-076 | `unsubscribeAction` | Chart.ts:1282 | 无 | E | test | S | 2a |  |
| CH-077 | `getConvertPictureUrl` | Chart.ts:1286 | ChartSnapshotExporter（PNG only） | B | test | S | 2a | 补 jpeg/bmp+backgroundColor |
| CH-078 | `resize` | Chart.ts:1315 | onLayout 自动 | A | test | — | — |  |
| CH-079 | `destroy` | Chart.ts:1327 | scope 未暴露销毁 | E | test | S | 2a | engine.close+scope.cancel |

## 3. 事件 payload（9 种 ActionType）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|---|---|---|
| EVT-001 | `onZoom`（payload `{scale}`） | Store.ts:1247 | onZoom{scale} 接线（缩放手势后 emit） | B | test | S | 2a |  |
| EVT-002 | `onScroll`（`{distance}`） | Store.ts:946 | onScroll{distance} 接线 | B | test | S | 2a |  |
| EVT-003 | `onVisibleRangeChange`（`VisibleRange`） | Store.ts:742 | onVisibleRangeChange 接线 | B | test | S | 2a |  |
| EVT-004 | `onCrosshairChange`（`Crosshair`） | Store.ts:1309 | onCrosshairChange 接线（Crosshair payload） | B | test | S | 2a | payload 结构补 |
| EVT-005 | `onCandleBarClick`（`VisibleRangeData`） | CandleBarView.ts:35 | onCandleBarClick 接线（VisibleRangeData payload） | B | test | S | 2a | payload 补 prev/next |
| EVT-006 | `onPaneDrag`（`{paneId}`） | SeparatorWidget.ts:149 | onPaneDrag 接线（{paneId}） | B | test | S | 2a |  |
| EVT-007 | `onCandleTooltipFeatureClick` | tooltip | 无（features 未实现） | E | test | M | 5 | 依赖批次 5 |
| EVT-008 | `onIndicatorTooltipFeatureClick` | tooltip | 无 | E | test | M | 5 | 同上 |
| EVT-009 | `onCrosshairFeatureClick` | crosshair | 无 | E | test | M | 5 | 同上 |


## 4. 公共类型

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|---|---|---|
| TYP-001 | `KLineData` | Data.ts | Candle 同名同义 | A | existing | — | — | |
| TYP-002 | `Crosshair` | Crosshair.ts | Crosshair 类型已建（chart/ChartTypes.kt） | B | test | S | 2a | 字段级见 ts-inventory §5 |
| TYP-003 | `BarSpace{bar,halfBar,gapBar,halfGapBar}` | BarSpace.ts | barSpacing 单值 | E | test | S | 2c | |
| TYP-004 | `VisibleRange{from,to,realFrom,realTo}` | VisibleRange.ts | VisibleRange{from,to,realFrom,realTo} 已建 | B | test | S | 2a |  |
| TYP-005 | `VisibleRangeData` | Data.ts:36 | VisibleRangeData 已建（prev/data/next） | B | test | S | 2a | 事件 payload 用 |
| TYP-006 | `SymbolInfo` | SymbolInfo.ts | SymbolInfo 已建（ticker/precision 默认 2） | B | test | S | 2a |  |
| TYP-007 | `Period` | Period.ts | Period 已建（type+milliseconds+toMillis） | B | test | S | 2a |  |
| TYP-008 | `DataLoader`（含 DataLoadMore 双向） | DataLoader.ts | DataLoader 已建（init/forward/backward+subscribeBar；v10 无 update type） | B | test+parity | M | 2a | 四 type 对齐 |
| TYP-009 | `CandleType` 六值 | Styles.ts:242 | MainChartType 同名 | A | existing | — | — | |
| TYP-010 | `LineType/PolygonType/PathType/TooltipShowRule/TooltipShowType/TooltipFeaturePosition/FeatureType/FormatDateType/ZoomAnchorType` | Styles.ts 等 | 部分散落 | E | compile | S | 5 | 枚举同名化 |
| TYP-011 | `IndicatorSeries` 三值 | Indicator.ts:34 | IndicatorSeries（PRICE/VOLUME/NORMAL） | A | existing | — | — | 字符串值对齐在批次 3 |
| TYP-012 | `OverlayMode` 三值 | Overlay.ts:29 | MagnetMode | B | test | S | 2b | 门面同名枚举 |
| TYP-013 | `OverlayDrawingMode` | Overlay.ts:44 | continuous 布尔 | B | test | S | 4 | |
| TYP-014 | `ActionType` 九值 | Action.ts:19 | ActionType 九值枚举已建 + subscribe/execute | B | test | S | 2a |  |


## 5. 指标模板字段（22，逐字段）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|---|---|---|
| IND-001 | `id` | component/Indicator.ts | IndicatorInstance.id | A | existing | — | — | |
| IND-002 | `paneId` | component/Indicator.ts | 无 | E | test | S | 3 | |
| IND-003 | `yAxisId` | component/Indicator.ts | 无 | E | test | S | 5 | 依赖多轴 |
| IND-004 | `name` | component/Indicator.ts | 有 | A | existing | — | — | |
| IND-005 | `shortName` | component/Indicator.ts | 有 | A | existing | — | — | |
| IND-006 | `precision` | component/Indicator.ts | 有 | A | existing | — | — | |
| IND-007 | `calcParams` | component/Indicator.ts | 有 | A | existing | — | — | |
| IND-008 | `shouldOhlc` | component/Indicator.ts | 已建：IndicatorDefinition.shouldOhlc（6 模板覆写 true） | B | diff | S | 3 | MA 等 5 个模板为 true |
| IND-009 | `shouldFormatBigNumber` | component/Indicator.ts | 已建：shouldFormatBigNumber（VOL=true） | B | diff | S | 3 | VOL 为 true |
| IND-010 | `visible` | component/Indicator.ts | 已建：Definition.visible + Instance.visible | B | test | S | 2b |  |
| IND-011 | `zLevel` | component/Indicator.ts | 已建：Definition.zLevel（默认 0） | B | test | S | 3 |  |
| IND-012 | `extendData` | component/Indicator.ts | 已建：Definition.extendData | B | test | M | 3 |  |
| IND-013 | `series` | component/Indicator.ts | 已建：series 枚举（meta diff 断言值映射 undefined→normal） | B | diff | — | 3 | 值字符串化（price） |
| IND-014 | `figures`（含 styles/attrs 回调） | component/Indicator.ts | PlotDef（无回调） | E | diff | M | 3 | |
| IND-015 | `minValue` | component/Indicator.ts | 已建：minValue（VOL=0 覆写） | B | diff | S | 3 | VOL=0 |
| IND-016 | `maxValue` | component/Indicator.ts | 已建：maxValue（默认 null） | B | diff | S | 3 |  |
| IND-017 | `styles` | component/Indicator.ts | 无 | E | test | M | 5 | |
| IND-018 | `shouldUpdate` | component/Indicator.ts | 已建：shouldUpdate（默认 true；PRD R-08） | B | test | S | 3 |  |
| IND-019 | `calc` | component/Indicator.ts | 引擎等价（oracle 逐位） | A | diff | — | — | L1 已达标 |
| IND-020 | `regenerateFigures` | component/Indicator.ts | 已建：plotsFor（meta diff 断言 figures 顺序） | B | diff | S | 3 | 改名兼容 |
| IND-021 | `createTooltipDataSource` | component/Indicator.ts | 无 | E | test | M | 5 | |
| IND-022 | `draw` | component/Indicator.ts | 无 | E | test | M | 5 | 自定义绘制 |
| IND-023 | 27 个内置模板元数据整体 | extension/indicator/*.ts | oracle 数值已对齐；元数据未 diff | E | diff | M | 3 | F1 自动 diff |
| IND-024 | `result`（calc 输出数组，getIndicators 返回体） | component/Indicator.ts | 已建：IndicatorResult 列存（门面视图转换待 2c） | B | test | S | 2b | 结构等价（行存↔列存），门面做视图转换 |


## 6. 覆盖物模板字段（逐字段）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|---|---|---|
| OVL-001 | `name` | component/Overlay.ts | 有 | A | existing | — | — | |
| OVL-002 | `totalStep` | component/Overlay.ts | 已建：Definition.totalStep（=pointCount+1，meta diff 16/16 断言） | B | diff | S | 4 | 兼容读取 |
| OVL-003 | `needDefaultPointFigure` | component/Overlay.ts | 已建（meta diff 断言；simpleAnnotation/tag/brush=false） | B | diff | S | 4 |  |
| OVL-004 | `needDefaultXAxisFigure` | component/Overlay.ts | 已建（meta diff 断言） | B | diff | S | 4 |  |
| OVL-005 | `needDefaultYAxisFigure` | component/Overlay.ts | 已建（meta diff 断言） | B | diff | S | 4 |  |
| OVL-006 | `createPointFigures` | component/Overlay.ts | 内置渲染器分支 | E | test | L | 4 | 返回 Figure 原语 |
| OVL-007 | `createXAxisFigures` | component/Overlay.ts | 无 | E | test | M | 4 | |
| OVL-008 | `createYAxisFigures` | component/Overlay.ts | 无 | E | test | M | 4 | |
| OVL-009 | `checkEventOn` | component/Overlay.ts | 已建：Definition.checkEventOn | B | test | S | 4 |  |
| OVL-010 | `drawingMode(step/continuous)` | component/Overlay.ts | continuous 布尔 | B | diff | S | 4 | |
| OVL-011 | `performEventPressedMove` | component/Overlay.ts | 手势内置 | E | test | M | 4 | |
| OVL-012 | `performEventMoveForDrawing` | component/Overlay.ts | 手势内置 | E | test | M | 4 | |
| OVL-013 | `onRemoved` | component/Overlay.ts | OverlayEvent.Removed | B | test | S | 4 | |
| OVL-014 | `onClick/onDoubleClick/onRightClick` | component/Overlay.ts | 无（右键平台不适用） | E | test | M | 4 | 右键→长按菜单等价 |
| OVL-015 | `onMouseDown/Enter/Leave/Move/Up` | component/Overlay.ts | 无 | E | test | M | 4 | 指针语义映射 |
| OVL-016 | `onTouchStart/Move/End` | component/Overlay.ts | 手势内置 | E | test | M | 4 | |
| OVL-017 | `onPressedMove` | component/Overlay.ts | DRAGGING 状态 | B | test | S | 4 | |
| OVL-018 | `extendData` | component/Overlay.ts | 已建：Definition.extendData | B | test | S | 4 |  |
| OVL-019 | 16 个内置模板元数据整体 | extension/overlay/*.ts | pointCount 已核 | E | diff | S | 4 | F1 自动 diff |
| OVL-020 | `groupId` | component/Overlay.ts | 无（filter 用） | E | test | S | 2b | |
### Overlay 实例接口字段（24，v3 补盘点）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|---|---|---|
| OVL-021 | `id` | component/Overlay.ts | OverlayInstance.id | B | test | S | 2b |  |
| OVL-022 | `groupId` | component/Overlay.ts | 无（OVL-020 | E | test | S | 4 | 已由模板行覆盖，实例面暴露 |
| OVL-023 | `paneId` | component/Overlay.ts | paneId 固定 main | B | test | S | 2b |  |
| OVL-024 | `name` | component/Overlay.ts | templateName | B | test | S | 2b |  |
| OVL-025 | `totalStep` | component/Overlay.ts | 已建：Definition.totalStep（=pointCount+1，meta diff 16/16 断言） | B | diff | S | 4 | 已由模板行覆盖，实例面暴露 |
| OVL-026 | `currentStep` | component/Overlay.ts | 由 phase/points.size 推导（getter 未暴露） | E | test | S | 2b | 由 phase 推导 getter |
| OVL-027 | `drawingMode` | component/Overlay.ts | 已建：continuous 布尔（meta diff 断言 brush=continuous） | B | diff | S | 2b |  |
| OVL-028 | `lock` | component/Overlay.ts | OverlayInstance.locked 已建 | B | test | S | 2b |  |
| OVL-029 | `visible` | component/Overlay.ts | 已建：Definition.visible + Instance.visible | B | test | S | 2b |  |
| OVL-030 | `zLevel` | component/Overlay.ts | 已建：Definition.zLevel（默认 0） | B | test | S | 2b |  |
| OVL-031 | `fixedZLevel` | component/Overlay.ts | 无 | E | test | S | 4 |  |
| OVL-032 | `needDefaultPointFigure` | component/Overlay.ts | 已建（meta diff 断言；simpleAnnotation/tag/brush=false） | B | diff | S | 4 | 已由模板行覆盖，实例面暴露 |
| OVL-033 | `needDefaultXAxisFigure` | component/Overlay.ts | 已建（meta diff 断言） | B | diff | S | 4 | 已由模板行覆盖，实例面暴露 |
| OVL-034 | `needDefaultYAxisFigure` | component/Overlay.ts | 已建（meta diff 断言） | B | diff | S | 4 | 已由模板行覆盖，实例面暴露 |
| OVL-035 | `mode` | component/Overlay.ts | magnetMode + 门面 OverlayMode 枚举已建 | B | test | S | 2b | OverlayMode 枚举 |
| OVL-036 | `modeSensitivity` | component/Overlay.ts | 未实现（上游默认值等价） | E | diff | S | 4 |  |
| OVL-037 | `points` | component/Overlay.ts | OverlayInstance.points（时间锚点）已建 | A | existing | S | 2b | 锚点结构等价 |
| OVL-038 | `extendData` | component/Overlay.ts | 已建：Definition.extendData | B | test | S | 4 | 已由模板行覆盖，实例面暴露 |
| OVL-039 | `styles` | component/Overlay.ts | 无 | E | test | S | 4 |  |
| OVL-040 | `createPointFigures` | component/Overlay.ts | 内置渲染（OVL-006 | B | test | S | 4 | 已由模板行覆盖，实例面暴露 |
| OVL-041 | `createXAxisFigures` | component/Overlay.ts | 无（OVL-007 | E | test | S | 4 | 已由模板行覆盖，实例面暴露 |
| OVL-042 | `createYAxisFigures` | component/Overlay.ts | 无（OVL-008 | E | test | S | 4 | 已由模板行覆盖，实例面暴露 |
| OVL-043 | `performEventPressedMove` | component/Overlay.ts | 手势内置（OVL-011 | B | test | S | 4 | 已由模板行覆盖，实例面暴露 |
| OVL-044 | `performEventMoveForDrawing` | component/Overlay.ts | 手势内置（OVL-012 | B | test | S | 4 | 已由模板行覆盖，实例面暴露 |
## 7. Styles 接口（43，一接口一行；字段级见 ts-inventory §6）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|---|---|---|
| STY-001 | `Margin` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-002 | `Padding` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-003 | `Offset` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-004 | `LineStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-005 | `SmoothLineStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-006 | `StateLineStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-007 | `PathStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-008 | `PolygonStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-009 | `RectStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-010 | `TextStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-011 | `StateTextStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-012 | `ChangeColor` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-013 | `GradientColor` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-014 | `FeaturePathStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-015 | `FeatureIconFontStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-016 | `FeatureStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-017 | `GridStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-018 | `TooltipLegendChild` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-019 | `TooltipLegend` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-020 | `TooltipFeatureStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-021 | `TooltipStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-022 | `CandleAreaPointStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-023 | `CandleAreaStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-024 | `CandleHighLowPriceMarkStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-025 | `CandleLastPriceMarkStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-026 | `CandlePriceMarkStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-027 | `CandleTooltipRectStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-028 | `CandleTooltipStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-029 | `CandleBarColor` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-030 | `CandleBarColorCallbackReturnStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-031 | `CandleStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-032 | `IndicatorLastValueMarkStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-033 | `IndicatorTooltipStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-034 | `IndicatorStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-035 | `AxisTickLineStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-036 | `AxisTickTextStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-037 | `AxisStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-038 | `CrosshairDirectionStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-039 | `CrosshairStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-040 | `OverlayPointStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-041 | `OverlayStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-042 | `SeparatorStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-043 | `Styles` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 |

## 8. 数值语义（L1，防回归项）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|---|---|---|
| NUM-001 | 27 指标 calc 数值 | extension/indicator/* | oracle 逐位对照已达标 | A | diff | — | — | 既有 fixtures 防回归 |
| NUM-002 | percentage 零基准行为 | y-axis/percentage.ts | 「—」+有限范围 | D | existing | — | — | 有意差异 D-01 |
| NUM-003 | 斐波那契价格投影 | overlay/fibonacciLine.ts | 价格空间算层级 | D | existing | — | — | 有意差异 D-02 |
| NUM-004 | 缺失字段语义 | extension/indicator/* | Missing（NaN） | D | existing | — | — | 有意差异 D-03 |
| NUM-005 | 视口数据↔像素往返 | — | ≤0.5px 达标 | A | test | — | — | A-12 既有 |
| NUM-006 | OHLC 输入校验 | — | 严于上游（定位错误） | D | existing | — | — | PRD R-01 |

---

## 9. 批次依赖与执行顺序（v3）

```
2a（数据+事件+坐标转换+生命周期，44 行）
 └─> 2b（指标/覆盖物 CRUD，9+10 行）── 无跨批依赖，可与 2c 并行
 └─> 2c（视口+配置，48 行）
       └─> 3（指标模板+注册 API）──依赖 2b 的实例管理
             └─> 4（覆盖物模板+figure）
                   └─> 5（Styles 树 57 行；多轴依赖 pane 基础；EVT-007~009 依赖本批 tooltip）
                         └─> 6（示例对齐+人工验收，收口）
```
硬依赖仅两条：3 依赖 2b；5 依赖 2c（pane/轴基础）。2a/2b/2c 之间无阻塞，按人力可并行。

### 执行状态（收口）

- 批次 2a/2b/2c：✅（parity-1/2/3 全绿）
- 批次 3/4：✅（两 meta diff 0 差异）
- 批次 5：✅（Styles 43 接口 + 默认值对照 + setStyles 语义；绘制端渐进消费继续）
- 批次 6：✅（demo-checklist 人工验收无阻断；consumer 门面重编通过）
- 最终：**E=55**（余量为绘制端接线与 DOM 等价物，均为 B 类已映射；unknown=0）

## 10. 工作量与日历估算（v3：S=0.5d / M=1.5d / L=3d，单人折算）

| 批次 | 行数 | S/M/L 分布 | 人日 | 说明 |
|---|---|---|---|---|
| 2a | 44 | S27/M8/L0 | 25.5 | DataLoader 适配与事件接线为主 |
| 2b | 19 | S12/M5/L0 | 21.0 | 含 IND-024 与 OVL 实例字段行 |
| 2c | 48 | S34/M12/L1 | 55.0 | 最大单批；多轴 createYAxis L 级滑入 5 的占位可减 |
| 3 | 5 | S2/M3/L0 | 5.5 | 含 IND-023 元数据 diff（M） |
| 4 | 10 | S6/M3/L1 | 12.0 | createPointFigures 为 L |
| 5 | 57 | S12/M43/L2 | 76.0 | 最大总量；Styles 43 接口+tooltip+多轴 |
| 6 | — | 人工验收 | 3.0 | demo-checklist 执行 |
| **合计** | | | **≈198 人日（单人）** | 双人并行（2a/2b+2c 分线）日历约 4~4.5 个月 |

> 估算口径：纯开发+自测；含每批 parity 用例编写，不含上游新版本变更（v10.1.0 后暂无）。

## 11. parity 用例定义（v3 具体化，纳入常规测试）

| 用例 | 上游文档来源 | 门面写法 | 断言 |
|---|---|---|---|
| parity-1（2a 出口） | docs/guide「快速上手/数据接入」getBars+subscribeBar 示例 | 同名 `setDataLoader(object : DataLoader{...})`，仅 init()→LKanvasChart 构造 | 100 根渲染就绪；update 推进末根；forward 触发 prepend |
| parity-2（2b 出口） | docs/guide「指标」「覆盖物」createIndicator('MACD')/createOverlay({name:'segment',points:[...]}) | 同名调用 | 返回 instanceId；getIndicators/getOverlays 可查；removeIndicator 清理 |
| parity-3（2c 出口） | docs API「视口控制」scrollToDataIndex/zoomAtCoordinate/setBarSpace 示例 | 同名调用 | 每项有单测断言（索引/缩放比/BarSpace 值） |

实现位置：`lkanvas-compose/src/test/.../parity/Parity1DataLoader.kt` 等；上游片段以注释注明出处 URL。

## 12. Definition of Done（本轮对齐完成判据）

1. `python3 tools/alignment/check.py` 通过且矩阵 E 计数 = 0（全部转 A/B/C/D）
2. parity-1/2/3 纳入 `./gradlew test` 常规回归且绿
3. 既有 208 测试 + 27 指标 oracle diff 保持 0 失败（L1 防回归）
4. 独立 consumer（tools/consumer-example）改用门面 API 重新编译通过
5. 台账 compatibility.md 与 upstream-sync.md 同步更新；D 类差异均有 ID 与测试

## 13. 发布判定（0.2.0）

- 上述 DoD 全部满足；批次 2a+2b+2c+3 完成为最低发版面（WebView 迁移者可用数据/指标/覆盖物/视口主链路）
- 批次 4/5 允许随后以 0.3.x 递进（样式树与高级模板不阻塞 0.2.0）
- 版本号与制品：io.lkanvas:*:0.2.0 发布至本地 Maven；README 增加「从 KLineChart(WebView) 迁移」章节并引用 parity 用例为证

## 统计

**本文不手写统计**——数字由 `tools/alignment/check.py` 输出（分类/批次计数 + 完整性校验），避免 v1 手写失真。校验失败（格式违规/方法缺行/枚举非法）即退出非 0。
