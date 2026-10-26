# Kotlin 公开面清单（LKanvas 当前）

> 对齐排查 A2 产物。统计自 `lkanvas-core/src/main/kotlin`（105 个顶层类型）与 `lkanvas-compose/src/main/kotlin`（13 类型 + 4 顶层函数）。
> 矩阵（gap-matrix.md）以本清单为"Kotlin 现状"列依据。

## 1. lkanvas-core（105 顶层类型，按包）

### data（数据模型，R-01/T-03）
`Candle`（timestamp/open/high/low/close/volume?/turnover?）、`CandleInvalidReason`、`CandleValidationIssue`、`CandleValidator`

### store（串行变更，R-02/T-04/T-15）
`ChartDataStore`（reset/replaceLatest/upsertLatest/appendBatch/prependHistory/patchHistory/snapshot/candlesView + sessionId/revision/marketKey/first|lastTimestamp/candleCount/isEmpty）、`ChangeResult`（Accepted(op,appended,prepended,replacedLatest)/Rejected(op,reason,issues,detail)）、`DataOp`（RESET/REPLACE_LATEST/UPSERT_LATEST/APPEND_BATCH/PREPEND_HISTORY/PATCH_HISTORY）、`RejectReason`（7 种）、`MarketKey`（symbol/period/adjust）

### compute（引擎与门禁，R-03/T-05）
`ChartEngine`（apply/tryApply/launchCompute/bumpConfig/run/close + snapshot/onSnapshot/onResultApplied/onResultDropped/droppedResults/lastDropReason）、`ChangeRequest`（Reset/ReplaceLatest/UpsertLatest/AppendBatch/PrependHistory/PatchHistory）、`AppliedChange`、`ComputeTicket`、`DropReason`、`ComputeMode`

### datasource（加载，R-04/T-10）
`HistorySource`（loadInitial/loadEarlier + InitialPage/Page）、`RealtimeSource`、`Subscription`、`LoadOutcome`、`LoadState`（Idle/InitialLoading/Ready/Empty/InitialFailed/PagingEarlier/PagingFailed/RealtimeDisconnected）、`ChartLoader`（start/loadMoreEarlier，同方向单游标）、`DeterministicDataSource`

### indicator（引擎与 27 模板，R-08/T-09~T-14）
`IndicatorDefinition`（name/shortName/calcParams/precision/series/plots/plotsFor/incremental + initScratch/stepScratch/rowFromScratch）、`IndicatorInstance`、`IndicatorRegistry`（register/get/unregister/all）、`IndicatorEngine`（recompute/advance/computeInto）、`IndicatorResult`（列存 DoubleArray，NaN=Missing）、`IndicatorResultState`、`PlotDef`（key/title/type/baseValue）、`PlotType`（LINE/BAR）、`IndicatorSeries`（PRICE/VOLUME/NORMAL）、`ChangeKind`、`IncrementalCapability`（FULL_ONLY/REPLACE_APPEND）、`InstanceStatus`（PENDING/READY/ERROR）
内置模板类：MA、VOL、MACD、EMA、SMA、BOLL、BBI、BIAS、AO、MTM、ROC、PSY、KDJ(Stoch)、RSI、WR、CCI、DMI、BRAR、CR、VR、EMV、SAR、TRIX、OBV、PVT、AVP、DMA（27 个类）

### overlay（覆盖物，R-09/T-19~T-22）
`OverlayManager`（beginCreate/addCreatePoint/finishCreate/cancelCreate/select/beginDrag/drag/endDrag/setLocked/setVisible/remove/removeSelected/restoreFrom/exportCompleted/all/get + creatingId/selectedId/onEvent/magnetMode）、`OverlayInstance`、`OverlayPoint`（timestamp+value 锚点）、`OverlayDefinition`（name/pointCount/constraint/continuous/maxPoints/supportWholeDrag/supportMagnet）、`OverlayPhase`、`OverlayEvent`（含 SampleLimitReached）、`OverlayConstraint`、`OverlayTemplates`（16 种注册表）、`OverlayGeometry`（裁剪/射线/平行/通道/斐波那契）、`MagnetMode`
16 定义：Segment、StraightLine、RayLine、HorizontalRayLine、HorizontalSegment、HorizontalStraightLine、VerticalRayLine、VerticalSegment、VerticalStraightLine、ParallelStraightLine、PriceChannelLine、FibonacciLine、PriceLine、SimpleAnnotation、SimpleTag、Brush

### axis（纵轴，R-06/T-16/T-17）
`LinearYAxis`（valueToY/yToValue/ticks/sanitizeRange/rangeOfCandles）、`YAxisSpec`（Normal/Logarithm/Percentage(base)/Custom）、`YAxisMath`（valueToReal/realToValue/realRange/valueToY/yToValue/ticks）、`AxisTick`、`AxisTicks`、`CustomYAxis`

### viewport（视口，R-05/T-06）
`Viewport`（layout/candleCenterX/logicalIndexAt/dataIndexCenterX/scrollBy/zoomBy/zoomAtLatest/scrollToIndex/scrollToTimestamp/scrollToLatest/isAtLatest/onPrepended/onAppended/onReset/restoreAnchor/currentAnchor + barSpacing/min/max/rightOffsetSlots/scrollIndex/contentWidth/visibleCount + indexOfTimestamp）、`VisibleRange`(viewport 包内)

### figure（基础图形 7 种）
`Figure`（Circle/Line/Polygon/Rect/Text/Arc/Path）、`P`、`FigureHit`（hit/distanceToSegment/pointNearEdges/pointInPolygon/arcSample）、`FigureValidate`

### archive（存档，R-12/T-25/T-26）
`ChartArchive`（export/exportFrom/restore + SCHEMA_VERSION/MAX_*、Archive、RestoreResult、ViewportAnchor、ArchiveTooLargeException）、`RestoreGate`（beginRestore/endRestore/shouldEmitSave/phase）

### diagnostics（诊断，R-15/T-30）
`ChartDiagnostics`（setEnabled/isEnabled/record/entries/summary/timeCompute + lastComputeNanos/lastRenderNanos/computeCount/renderCount）

### accessibility（无障碍，R-13/T-27）
`AccessibilitySummary`（candleSummary/indicatorSummary/chartSummary/indicatorValues）

### i18n（文案，R-11/T-24）
`ChartLocale`（ZH_CN/EN_US）

### 其他
`LKanvas`（VERSION/UPSTREAM_BASELINE）

## 2. 核心类成员签名（v2 补充：可复现提取）

> 由脚本从源码正则提取（`fun name(` 与 `var/val name`），保证与代码同步；矩阵"Kotlin 现状"列以此为准。完整参数签名见源文件。

- **ChartDataStore**（store）：fun snapshot, candlesView, patchHistory, reset, replaceLatest, upsertLatest, appendBatch, prependHistory；var/val marketKey, sessionId, revision, candleCount, isEmpty, firstTimestamp, lastTimestamp, lastCandle
- **ChartEngine**（compute）：fun apply, tryApply, bumpConfig, launchCompute, run, close；var/val snapshot, droppedResults, lastDropReason, onSnapshot, onResultApplied, onResultDropped, lastApplied
- **OverlayManager**（overlay）：fun all, get, beginCreate, addCreatePoint, finishCreate, cancelCreate, select, beginDrag, drag, endDrag, setLocked, setVisible, remove, removeSelected, restoreFrom, exportCompleted；var/val creatingId, selectedId, onEvent, magnetMode
- **Viewport**（viewport）：fun layout, candleCenterX, logicalIndexAt, dataIndexCenterX, scrollBy, zoomBy, zoomAtLatest, scrollToIndex, scrollToTimestamp, scrollToLatest, isAtLatest, onPrepended, onAppended, onReset, restoreAnchor, restoreAnchor, currentAnchor；var/val scrollIndex, contentWidth, visibleCount
- **IndicatorRegistry**（indicator-registry）：fun register, get, unregister, all；var/val 
- **ChartLoader**（loader）：fun start, loadMoreEarlier；var/val state, onStateChange

## 3. lkanvas-compose（13 类型 + 4 顶层函数）

### 组件与状态
`KLineChart(state, modifier, theme, config, legendSlot?)`、`ChartState`（约 45 公开成员：数据操作 reset/upsertLatest/appendBatch/prependHistory/patchHistory、视口 panBy/zoomAt/fling、十字线 show/move/hide、覆盖物 beginCreateOverlay/finishCreateOverlay/cancelCreateOverlay/currentTool、布局 onLayout/adjustPane/paneSeparatorAt/axisZoomBy/isInAxisGutter、无障碍 select{Previous,Next}Candle/zoom{In,Out}/removeSelectedOverlay/accessibilityText、存档 applyRestoredArchive/restoreGate/onArchiveDirty、诊断 diagnostics、快照 dataSnapshot/maResult/volResult/macdResult/viewport/locale/legendMode/mainAxisSpec/mainChartType/gapRatio/onCandleClick/onSnapshot 等）、`LegendData`、`GestureOwner`

### 绘制
`ChartRenderer`（render + Params）、`PaneRenderers`（draw{Main,Volume,Macd}Pane）、`OverlayRenderer`（drawOverlays/hitTest）、`FigureRenderer`、`ChartText`（price/volume/measureWidth/timeLabel/timeAxisLabel）、`VisibleRange`、`MainChartType`（6 种）、`ChartTheme`（含 dark()）、`ChartLayoutConfig`

### 导出
`ChartSnapshotExporter`（export/exportPng + Result/TooLarge/MAX_DIMENSION）
