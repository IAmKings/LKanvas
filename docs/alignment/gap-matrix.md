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


## 1. index.ts 顶层导出

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|---|---|---|
| IDX-001 | `init` | index.ts | 门面工厂未建 | E | test+parity | S | 2a | Android 无 DOM：init(state) |
| IDX-002 | `dispose` | index.ts | 未暴露销毁 | E | test | S | 2a | |
| IDX-003 | `version` | index.ts | LKanvas.VERSION | B | compile | S | 2a | 门面静态同名 |
| IDX-004 | `utils.clone` | utils | data class copy 等价 | B | compile | S | 2a | |
| IDX-005 | `utils.merge` | utils | 未抽公共 DeepPartial merge | E | test | S | 2a | setStyles 语义基础 |
| IDX-006~012 | `utils.isString/isNumber/isValid/isObject/isArray/isFunction/isBoolean` | utils | Kotlin 类型系统等价 | C | compile | S | 2a | 提供 utils 对象占位 |
| IDX-013 | `utils.formatValue` | utils/format | 分散 | E | test | S | 2a | |
| IDX-014 | `utils.formatPrecision` | utils/format | ChartText.price 近似 | E | test | S | 2a | |
| IDX-015 | `utils.formatBigNumber` | utils/format | ChartText.volume 近似 | E | test | S | 2a | |
| IDX-016 | `utils.formatDate` | utils/format | ChartText.timeLabel 近似 | E | test | S | 2a | 模板化 |
| IDX-017 | `utils.formatThousands` | utils/format | DecimalFormat 近似 | E | test | S | 2a | |
| IDX-018 | `utils.formatFoldDecimal` | utils/format | 无 | E | test | S | 2a | |
| IDX-019 | `utils.calcTextWidth` | utils/canvas | ChartText.measureWidth | B | test | S | 2a | |
| IDX-020 | `utils.getLinearSlopeIntercept` | figure/line | OverlayGeometry 内联 | E | test | S | 2a | 抽公共 |
| IDX-021 | `utils.getLinearYFromSlopeIntercept` | figure/line | 无独立函数 | E | test | S | 2a | |
| IDX-022 | `utils.getLinearYFromCoordinates` | figure/line | 无独立函数 | E | test | S | 2a | |
| IDX-023~028 | `utils.checkCoordinateOn{Arc,Circle,Line,Polygon,Rect,Text}` | figure/* | FigureHit 命中（7 种含 path） | B | test | S | 4 | 同名包装 |
| IDX-029 | `registerIndicator` | ext/indicator | IndicatorRegistry（实例级） | E | test | M | 3 | 全局注册表 |
| IDX-030 | `getSupportedIndicators` | ext/indicator | 无 | E | test | S | 3 | |
| IDX-031 | `registerOverlay` | ext/overlay | OverlayTemplates（内置） | E | test | M | 4 | |
| IDX-032 | `getSupportedOverlays` | ext/overlay | 无 | E | test | S | 4 | |
| IDX-033 | `getOverlayClass` | ext/overlay | 无（模板即对象） | B | compile | S | 4 | |
| IDX-034 | `registerFigure` | ext/figure | 无注册表 | E | test | S | 4 | |
| IDX-035 | `getFigureClass` | ext/figure | 无 | B | compile | S | 4 | |
| IDX-036 | `getSupportedFigures` | ext/figure | 无 | E | test | S | 4 | |
| IDX-037 | `registerStyles` | ext/styles | ChartTheme.dark() 近似 | E | test | M | 5 | light/dark 注册 |
| IDX-038 | `registerLocale` | ext/i18n | ChartLocale 常量 | E | test | S | 3 | |
| IDX-039 | `getSupportedLocales` | ext/i18n | 无 | E | test | S | 3 | |
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
| CH-015 | `setStyles` | Chart.ts:606 | ChartTheme（字段树不同） | B | test | M | 5 | 字符串主题+DeepPartial merge |
| CH-016 | `getStyles` | Chart.ts:612 | 同上 | B | test | S | 5 |  |
| CH-017 | `setLayoutOptions` | Chart.ts:616 | ChartLayoutConfig 部分 | B | compile | M | 5 | barSpaceLimit/pane/yAxis |
| CH-018 | `getLayoutOptions` | Chart.ts:620 | 同上 | B | compile | S | 5 |  |
| CH-019 | `setFormatter` | Chart.ts:624 | lambda 分散（ChartText/locale） | B | test | S | 2c | Formatter 接口对齐 |
| CH-020 | `getFormatter` | Chart.ts:630 | 同上 | B | compile | S | 2c |  |
| CH-021 | `setLocale` | Chart.ts:634 | ChartLocale（T-24 已有） | B | test | S | 2c | setLocale('en-US')→EN_US |
| CH-022 | `getLocale` | Chart.ts:640 | state.locale | B | test | S | 2c |  |
| CH-023 | `setTimezone` | Chart.ts:644 | 默认系统时区，无注入 | E | test | S | 2c |  |
| CH-024 | `getTimezone` | Chart.ts:650 | 无 | E | test | S | 2c |  |
| CH-025 | `setThousandsSeparator` | Chart.ts:654 | DecimalFormat 内建千分位 | E | test | S | 2c | {sign} 可配 |
| CH-026 | `getThousandsSeparator` | Chart.ts:660 | 无 | E | compile | S | 2c |  |
| CH-027 | `setDecimalFold` | Chart.ts:664 | 无小数折叠 | E | test | S | 2c | {threshold:3} 默认 |
| CH-028 | `getDecimalFold` | Chart.ts:670 | 无 | E | compile | S | 2c |  |
| CH-029 | `setHotkey` | Chart.ts:674 | 浏览器键鼠 | C | compile | S | 2c | Android 外接键鼠 P2 |
| CH-030 | `getHotkey` | Chart.ts:678 | 无 | C | compile | S | 2c |  |
| CH-031 | `getHotKey` | Chart.ts:682 | 无（旧拼写别名） | C | compile | S | 2c |  |
| CH-032 | `setOffsetRightDistance` | Chart.ts:697 | rightOffsetSlots（槽位非 px） | B | test | S | 2c | px↔槽位换算 |
| CH-033 | `getOffsetRightDistance` | Chart.ts:701 | 无 getter | E | test | S | 2c |  |
| CH-034 | `setMaxOffsetLeftDistance` | Chart.ts:705 | overscrollSlots=0 | E | test | S | 2c |  |
| CH-035 | `setMaxOffsetRightDistance` | Chart.ts:713 | 无 | E | test | S | 2c |  |
| CH-036 | `setLeftMinVisibleBarCount` | Chart.ts:721 | 无 | E | test | S | 2c | clamp 语义 |
| CH-037 | `setRightMinVisibleBarCount` | Chart.ts:729 | 无 | E | test | S | 2c |  |
| CH-038 | `setBarSpace` | Chart.ts:737 | barSpacing 直改 | B | test | S | 2c | BarSpace{bar,halfBar,gapBar,halfGapBar} |
| CH-039 | `getBarSpace` | Chart.ts:741 | 无结构 getter | E | test | S | 2c |  |
| CH-040 | `getVisibleRange` | Chart.ts:745 | visibleRange 扩展（缺 realFrom/realTo） | B | test | S | 2a | 结构补 realFrom/realTo |
| CH-041 | `resetData` | Chart.ts:789 | ChartDataStore.reset | B | test | S | 2a |  |
| CH-042 | `getDataList` | Chart.ts:793 | dataSnapshot.candles | B | test | S | 2a |  |
| CH-043 | `setDataLoader` | Chart.ts:797 | ChartLoader+HistorySource 构造注入 | B | test+parity | M | 2a | 运行时替换+四 type 适配（init/forward/backward/update） |
| CH-044 | `createIndicator` | Chart.ts:802 | 内置 3 实例，无按名运行时创建 | E | test+parity | M | 2b | isStack 依赖 pane 模型（批次 5） |
| CH-045 | `overrideIndicator` | Chart.ts:837 | updateParams（部分） | E | test | M | 2b | styles/visible/extendData… |
| CH-046 | `getIndicators` | Chart.ts:863 | computeInstances 私有 | E | test | S | 2b | filter{id,name,paneId,isStack} |
| CH-047 | `removeIndicator` | Chart.ts:867 | 内置实例不可移除 | E | test | M | 2b |  |
| CH-048 | `createOverlay` | Chart.ts:903 | beginCreate 交互式；编程式不完整 | E | test+parity | M | 2b | 数组重载+直接完成 |
| CH-049 | `getOverlays` | Chart.ts:939 | all() 无 filter | E | test | S | 2b | filter{id,groupId,name,paneId} |
| CH-050 | `overrideOverlay` | Chart.ts:943 | 无 | E | test | M | 2b | points/styles/extendData |
| CH-051 | `removeOverlay` | Chart.ts:947 | remove/removeSelected | E | test | S | 2b | 批量 |
| CH-052 | `setPaneOptions` | Chart.ts:951 | adjustPane（拖动） | B | test | M | 2c | pane{id,height,axis?} |
| CH-053 | `createYAxis` | Chart.ts:1003 | YAxisSpec 单主轴 | E | test | L | 5 | 多轴实例化 |
| CH-054 | `removeYAxis` | Chart.ts:1025 | 无 | E | test | S | 5 |  |
| CH-055 | `getYAxes` | Chart.ts:1057 | 无 | E | compile | S | 5 |  |
| CH-056 | `overrideYAxis` | Chart.ts:1079 | 无 | E | test | S | 5 |  |
| CH-057 | `overrideXAxis` | Chart.ts:1096 | 无 | E | test | S | 5 |  |
| CH-058 | `getPaneOptions` | Chart.ts:1106 | PaneHeights 只读 | B | test | S | 2c |  |
| CH-059 | `setZoomEnabled` | Chart.ts:1114 | 无开关 | E | test | S | 2c | 手势层 |
| CH-060 | `isZoomEnabled` | Chart.ts:1118 | 无 | E | test | S | 2c |  |
| CH-061 | `setZoomAnchor` | Chart.ts:1122 | 手势固定触点锚 | D | test | S | 2c | cursor/last_bar 语义等价 |
| CH-062 | `getZoomAnchor` | Chart.ts:1126 | 无 | E | compile | S | 2c |  |
| CH-063 | `setScrollEnabled` | Chart.ts:1130 | 无开关 | E | test | S | 2c |  |
| CH-064 | `isScrollEnabled` | Chart.ts:1134 | 无 | E | test | S | 2c |  |
| CH-065 | `scrollByDistance` | Chart.ts:1138 | scrollBy（无动画） | B | test | M | 2c | Animatable 动画 |
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
| EVT-001 | `onZoom`（payload `{scale}`） | Store.ts:1247 | 无 | E | test | S | 2a | |
| EVT-002 | `onScroll`（`{distance}`） | Store.ts:946 | 无 | E | test | S | 2a | |
| EVT-003 | `onVisibleRangeChange`（`VisibleRange`） | Store.ts:742 | viewRevision 近似 | E | test | S | 2a | |
| EVT-004 | `onCrosshairChange`（`Crosshair`） | Store.ts:1309 | crosshairPosition（Offset） | E | test | S | 2a | payload 结构补 |
| EVT-005 | `onCandleBarClick`（`VisibleRangeData`） | CandleBarView.ts:35 | onCandleClick（仅 Candle） | E | test | S | 2a | payload 补 prev/next |
| EVT-006 | `onPaneDrag`（`{paneId}`） | SeparatorWidget.ts:149 | adjustPane 无回调 | E | test | S | 2a | |
| EVT-007 | `onCandleTooltipFeatureClick` | tooltip | 无（features 未实现） | E | test | M | 5 | 依赖批次 5 |
| EVT-008 | `onIndicatorTooltipFeatureClick` | tooltip | 无 | E | test | M | 5 | 同上 |
| EVT-009 | `onCrosshairFeatureClick` | crosshair | 无 | E | test | M | 5 | 同上 |


## 4. 公共类型

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|---|---|---|
| TYP-001 | `KLineData` | Data.ts | Candle 同名同义 | A | existing | — | — | |
| TYP-002 | `Crosshair` | Crosshair.ts | Offset 十字线 | E | test | S | 2a | 字段级见 ts-inventory §5 |
| TYP-003 | `BarSpace{bar,halfBar,gapBar,halfGapBar}` | BarSpace.ts | barSpacing 单值 | E | test | S | 2c | |
| TYP-004 | `VisibleRange{from,to,realFrom,realTo}` | VisibleRange.ts | from/to | E | test | S | 2a | |
| TYP-005 | `VisibleRangeData` | Data.ts:36 | 无 | E | test | S | 2a | 事件 payload 用 |
| TYP-006 | `SymbolInfo` | SymbolInfo.ts | MarketKey 无精度 | E | test | S | 2a | |
| TYP-007 | `Period` | Period.ts | 字符串 | E | test | S | 2a | |
| TYP-008 | `DataLoader`（含 DataLoadMore 双向） | DataLoader.ts | HistorySource 单向 more | E | test+parity | M | 2a | 四 type 对齐 |
| TYP-009 | `CandleType` 六值 | Styles.ts:242 | MainChartType 同名 | A | existing | — | — | |
| TYP-010 | `LineType/PolygonType/PathType/TooltipShowRule/TooltipShowType/TooltipFeaturePosition/FeatureType/FormatDateType/ZoomAnchorType` | Styles.ts 等 | 部分散落 | E | compile | S | 5 | 枚举同名化 |
| TYP-011 | `IndicatorSeries` 三值 | Indicator.ts:34 | IndicatorSeries（PRICE/VOLUME/NORMAL） | A | existing | — | — | 字符串值对齐在批次 3 |
| TYP-012 | `OverlayMode` 三值 | Overlay.ts:29 | MagnetMode | B | test | S | 2b | 门面同名枚举 |
| TYP-013 | `OverlayDrawingMode` | Overlay.ts:44 | continuous 布尔 | B | test | S | 4 | |
| TYP-014 | `ActionType` 九值 | Action.ts:19 | 分散回调 | E | test | S | 2a | |


## 5. 指标模板字段（22，逐字段）

| ID | TS 字段 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|---|---|
| IND-01 | `id` | IndicatorInstance.id | A | existing | — | — | |
| IND-02 | `paneId` | 无 | E | test | S | 3 | |
| IND-03 | `yAxisId` | 无 | E | test | S | 5 | 依赖多轴 |
| IND-04 | `name` | 有 | A | existing | — | — | |
| IND-05 | `shortName` | 有 | A | existing | — | — | |
| IND-06 | `precision` | 有 | A | existing | — | — | |
| IND-07 | `calcParams` | 有 | A | existing | — | — | |
| IND-08 | `shouldOhlc` | 无 | E | diff | S | 3 | MA 等 5 个模板为 true |
| IND-09 | `shouldFormatBigNumber` | 无 | E | diff | S | 3 | VOL 为 true |
| IND-10 | `visible` | Instance.visible 近似 | B | test | S | 2b | |
| IND-11 | `zLevel` | 无 | E | test | S | 3 | |
| IND-12 | `extendData` | 无 | E | test | M | 3 | |
| IND-13 | `series` | IndicatorSeries | A | diff | — | 3 | 值字符串化（price） |
| IND-14 | `figures`（含 styles/attrs 回调） | PlotDef（无回调） | E | diff | M | 3 | |
| IND-15 | `minValue` | 无 | E | diff | S | 3 | VOL=0 |
| IND-16 | `maxValue` | 无 | E | diff | S | 3 | |
| IND-17 | `styles` | 无 | E | test | M | 5 | |
| IND-18 | `shouldUpdate` | 无 | E | test | S | 3 | |
| IND-19 | `calc` | 引擎等价（oracle 逐位） | A | diff | — | — | L1 已达标 |
| IND-20 | `regenerateFigures` | plotsFor | B | diff | S | 3 | 改名兼容 |
| IND-21 | `createTooltipDataSource` | 无 | E | test | M | 5 | |
| IND-22 | `draw` | 无 | E | test | M | 5 | 自定义绘制 |
| IND-23 | 27 指标元数据（name/params/figures 等） | oracle 数值已对齐；元数据未 diff | E | diff | M | 3 | F1 自动 diff |


## 6. 覆盖物模板字段（逐字段）

| ID | TS 字段 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|---|---|
| OVL-01 | `name` | 有 | A | existing | — | — | |
| OVL-02 | `totalStep` | pointCount+1 | B | diff | S | 4 | 兼容读取 |
| OVL-03 | `needDefaultPointFigure` | 无 | E | diff | S | 4 | |
| OVL-04 | `needDefaultXAxisFigure` | 无 | E | diff | S | 4 | |
| OVL-05 | `needDefaultYAxisFigure` | 无 | E | diff | S | 4 | |
| OVL-06 | `createPointFigures` | 内置渲染器分支 | E | test | L | 4 | 返回 Figure 原语 |
| OVL-07 | `createXAxisFigures` | 无 | E | test | M | 4 | |
| OVL-08 | `createYAxisFigures` | 无 | E | test | M | 4 | |
| OVL-09 | `checkEventOn` | hitTest | B | test | S | 4 | |
| OVL-10 | `drawingMode(step/continuous)` | continuous 布尔 | B | diff | S | 4 | |
| OVL-11 | `performEventPressedMove` | 手势内置 | E | test | M | 4 | |
| OVL-12 | `performEventMoveForDrawing` | 手势内置 | E | test | M | 4 | |
| OVL-13 | `onRemoved` | OverlayEvent.Removed | B | test | S | 4 | |
| OVL-14 | `onClick/onDoubleClick/onRightClick` | 无（右键平台不适用） | E | test | M | 4 | 右键→长按菜单等价 |
| OVL-15 | `onMouseDown/Enter/Leave/Move/Up` | 无 | E | test | M | 4 | 指针语义映射 |
| OVL-16 | `onTouchStart/Move/End` | 手势内置 | E | test | M | 4 | |
| OVL-17 | `onPressedMove` | DRAGGING 状态 | B | test | S | 4 | |
| OVL-18 | `extendData` | extensionData | B | test | S | 4 | |
| OVL-19 | 16 覆盖物元数据 diff | pointCount 已核 | E | diff | S | 4 | F1 自动 diff |
| OVL-20 | `groupId`（filter 用） | 无 | E | test | S | 2b | |

## 7. Styles 接口（43，一接口一行；字段级见 ts-inventory §6）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
|---|---|---|---|---|---|---|---|---|
| STY-001 | `Margin` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-002 | `Padding` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-003 | `Offset` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-004 | `LineStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-005 | `SmoothLineStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-006 | `StateLineStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-007 | `PathStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-008 | `PolygonStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-009 | `RectStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-010 | `TextStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-011 | `StateTextStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-012 | `ChangeColor` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-013 | `GradientColor` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-014 | `FeaturePathStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-015 | `FeatureIconFontStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-016 | `FeatureStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-017 | `GridStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-018 | `TooltipLegendChild` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-019 | `TooltipLegend` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-020 | `TooltipFeatureStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-021 | `TooltipStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-022 | `CandleAreaPointStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-023 | `CandleAreaStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-024 | `CandleHighLowPriceMarkStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-025 | `CandleLastPriceMarkStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-026 | `CandlePriceMarkStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-027 | `CandleTooltipRectStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-028 | `CandleTooltipStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-029 | `CandleBarColor` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-030 | `CandleBarColorCallbackReturnStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-031 | `CandleStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-032 | `IndicatorLastValueMarkStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-033 | `IndicatorTooltipStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-034 | `IndicatorStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-035 | `AxisTickLineStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-036 | `AxisTickTextStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-037 | `AxisStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-038 | `CrosshairDirectionStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-039 | `CrosshairStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-040 | `OverlayPointStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-041 | `OverlayStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |
| STY-042 | `SeparatorStyle` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | S | 5 | 批次 5 字段级展开+默认值对照 |
| STY-043 | `Styles` | Styles.ts | ChartTheme 部分覆盖（字段级见 ts-inventory §6） | E | diff | M | 5 | 批次 5 字段级展开+默认值对照 |

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

## 统计

**本文不手写统计**——数字由 `tools/alignment/check.py` 输出（分类/批次计数 + 完整性校验），避免 v1 手写失真。校验失败（格式违规/方法缺行/枚举非法）即退出非 0。
