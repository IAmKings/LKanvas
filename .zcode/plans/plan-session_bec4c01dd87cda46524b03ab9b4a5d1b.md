# 对齐排查与对齐实施计划（Kotlin ↔ KLineChart TS v10.1.0）

## 0. 背景与目标重述

第一轮按 PRD「领域等价、不逐字翻译」执行：27 指标数值已 oracle 逐位对齐，但**结构与接口**未对齐上游——内核是自研 `ChartDataStore/ChartEngine`，公开面是 `KLineChart/ChartState`。本轮把对齐升级为三层：

| 层 | 内容 | 现状 |
|---|---|---|
| L1 数值/公式语义 | 指标/几何计算结果 | ✅ 已达标（oracle 逐位对照），保持防回归 |
| L2 公开接口对齐 | WebView 用户按 KLineChart 文档 1:1 迁移（`init`/`Chart` 约 70 方法/`Options`/模板注册/事件/utils 22 项） | ❌ 主要缺口，当前 Kotlin 无同名对应 |
| L3 示例对齐 | app-sample 覆盖上游 demo 功能清单，支撑人工验收 | ⚠️ 功能覆盖但信息架构不同 |

**关键架构决策（本计划前提，请重点确认）**：采用**门面对齐**而非内核重构——保留现有内核（已通过 A-01~A-14 验收，上游版本漂移不集中于此），新增与 TS 同名同语义的门面 `LKanvasChart` + 模板/样式/事件层。理由：上游迭代集中在 Chart API/模板/样式树，门面镜像即可快速跟进；满足「内部实现逻辑不大改」；不推翻已验收行为。若期望内核也按 Store.ts/pane/view 重排到类文件级，请拒绝并说明——那会扩大约 3 倍量级且与第一轮冲突。

## 阶段 A：对齐基线（纯分析，产出 3 文档 + 1 矩阵）

- **A1 TS 清单** `docs/alignment/ts-inventory.md`：逐符号登记（签名/语义/默认值/上游文件:行号）：
  - `index.ts` 33 个导出：`init/dispose/version/utils(22 项)/register{Indicator,Overlay,Figure,Styles,Locale,XAxis,YAxis,Hotkey}/getSupported*/getFigureClass/getOverlayClass/getHotkey` + 类型别名（CandleType/LineType/PolygonType/TooltipShowRule/TooltipShowType/ActionType/IndicatorSeries/OverlayMode/OverlayDrawingMode/YAxisOverride/ZoomAnchor/FormatDateType…）
  - `Chart.ts` 约 70 方法分 6 组：数据（setData/applyNewData/applyMoreData/updateData/getDataList/resetData/setDataLoader）、指标（createIndicator/overrideIndicator/getIndicators/removeIndicator/getIndicatorByInstanceId）、覆盖物（createOverlay 支持数组/getOverlays/overrideOverlay/removeOverlay）、视口（setBarSpace/getBarSpace/getVisibleRange/scrollTo*4/scrollByDistance/zoomAt*3/offsetRight/maxOffsetLeftRight/leftMinVisibleBarCount/rightMin…/setZoomEnabled/setScrollEnabled/setZoomAnchor）、配置（setStyles/getStyles/setLocale/setTimezone/setThousandsSeparator/setDecimalFold/setFormatter/setHotkey/setLayoutOptions/setPaneOptions/createYAxis/removeYAxis/getYAxes/overrideYAxis/overrideXAxis）、生命周期（resize/destroy/convertToPictureUrl/executeAction/subscribeAction/unsubscribeAction）、DOM 组（getDom/getSize/getContainer/getDrawPaneById/getDrawPanes/layout/updatePane）
  - `Options.ts`：locale/timezone/styles/formatter(formatDate/formatBigNumber/formatExtendText)/thousandsSeparator/decimalFold/zoomAnchor(cursor|last_bar)/hotkey/layout(barSpaceLimit/pane/yAxis)
  - `Styles.ts` 全样式树：Grid/Candle(type,bar 回调,area,priceMark,tooltip)/Indicator(ohlc,bars[],lines[],circles[],texts[],lastValueMark,tooltip)/Tooltip(showRule=always|follow_cross|none,showType=standard|rect,legend,title,features)/Crosshair/Overlay(point 六态,line,rect,polygon,circle,arc,text)/Separator/XAxis/YAxis
  - `component/Indicator.ts`：Indicator 接口 22 字段（id/paneId/yAxisId/name/shortName/precision/calcParams/shouldOhlc/shouldFormatBigNumber/visible/zLevel/extendData/series/figures/minValue/maxValue/styles/shouldUpdate/calc/regenerateFigures/createTooltipDataSource/draw）
  - `component/Overlay.ts`：Overlay 接口 + OverlayTemplate（totalStep/needDefaultPointFigure/needDefaultXAxisFigure/needDefaultYAxisFigure/createPointFigures/createXAxisFigures/createYAxisFigures/checkEventOn/drawingMode=step|continuous/performEvent*）+ OverlayMode(normal|weak_magnet|strong_magnet)
  - `common/Action.ts`：9 种 ActionType
- **A2 Kotlin 清单** `docs/alignment/kt-inventory.md`：core/compose 当前 public 符号
- **A3 差距矩阵** `docs/alignment/gap-matrix.md`：每行 = TS 符号 | Kotlin 现状 | 分类（**A 直接对齐 / B 适配 / C 平台不适用 / D 有意差异 D-01~03 / E 缺失**）| 工作量 S/M/L | 批次
- **A4 出口**：矩阵覆盖 100% 符号、0 unknown（延续 PRD 3.2 口径）

## 阶段 B：Chart 门面对齐（P0，WebView 用户最小迁移面）

新建 `io.lkanvas.chart.LKanvasChart` + `init()/dispose()` 工厂，方法映射现有 `ChartState/ChartEngine`：

- **B1 数据组**：`setData/applyNewData/applyMoreData/updateData/getDataList/resetData/setDataLoader`——先读上游 Store 源码确认 `updateData` 的 upsert 语义与 `applyMoreData` 分页契约，再映射 `ChangeRequest`（与 R-02 语义 diff 记入矩阵）
- **B2 指标组**：`createIndicator(String|IndicatorCreate, isStack)/overrideIndicator/getIndicators(filter)/removeIndicator(filter)/getIndicatorByInstanceId`（filter= id/name/paneId 组合）
- **B3 覆盖物组**：`createOverlay(String|OverlayCreate|数组, paneId?)/getOverlays/overrideOverlay/removeOverlay`
- **B4 视口组**：`setBarSpace/getBarSpace/getVisibleRange/scrollToDataIndex/scrollToTimestamp/scrollToRealTime/scrollByDistance(animation)/zoomAtCoordinate/zoomAtDataIndex/zoomAtTimestamp/setOffsetRightDistance/setMaxOffsetLeft/RightDistance/setLeftMinVisibleBarCount/setRightMinVisibleBarCount/setZoomEnabled/isZoomEnabled/setScrollEnabled/isScrollEnabled/setZoomAnchor`——Viewport 缺的语义补齐（动画滚动、双向最小可见数、zoomAnchor）
- **B5 配置组**：`setStyles/getStyles/setLocale/getLocale/setTimezone/getTimezone/setThousandsSeparator/setDecimalFold/setFormatter/setHotkey/setLayoutOptions/setPaneOptions/getPaneOptions/createYAxis/removeYAxis/getYAxes/overrideYAxis/overrideXAxis`（多轴已有 YAxisSpec 基础，补 pane 级 API）
- **B6 事件组**：`subscribeAction/unsubscribeAction/executeAction` + 9 种 ActionType 枚举（onZoom/onScroll/onVisibleRangeChange/onCrosshairChange/onCandleBarClick/onPaneDrag/三个 FeatureClick），从现有手势/引擎事件接线
- **B7 生命周期**：`resize/destroy/convertToPictureUrl(includeOverlay,type,backgroundColor→PNG 导出映射)/executeAction`；DOM 方法 → C 类：提供 `getBounding(paneId?)` 等价
- **B8 验收**：每方法编译/单测；sample 冒烟改走门面

## 阶段 C：模板结构对齐（P0，跟进上游版本的核心）

- **C1 IndicatorTemplate 补齐**（对齐 22 字段）：缺 `minValue/maxValue/shouldOhlc/shouldFormatBigNumber/zLevel(现无)/visible/extendData/styles/shouldUpdate/createTooltipDataSource/draw`；`figures` 支持 `styles/attrs` 回调；`calc(dataList, indicatorContext)` 语义对齐；`regenerateFigures`（现 plotsFor 改名兼容）
- **C2 内置 27 指标元数据 diff 自动化**：oracle 脚本导出上游模板元数据 JSON（name/shortName/series/calcParams/precision/figures key+title/min/max）→ Kotlin 测试逐字段比对注册表，0 差异为过
- **C3 OverlayTemplate 补齐**：`totalStep`（兼容读取 = pointCount+1）、`needDefaultPointFigure/needDefaultXAxisFigure/needDefaultYAxisFigure`、`createPointFigures/createXAxisFigures/createYAxisFigures`（返回 Figure 原语描述）、`checkEventOn`、`drawingMode(step|continuous→现 continuous)`、`performEventPressedMove/performEventMoveForDrawing`、`on*` 事件回调族、`extendData`
- **C4 内置 16 覆盖物元数据 diff 自动化**（同 C2）
- **C5 figure 体系**：`registerFigure/getSupportedFigures/getFigureClass` + `checkCoordinateOn{Arc,Circle,Line,Polygon,Rect,Text}`（映射 FigureHit）
- **C6 注册 API 同名**：`registerIndicator/registerOverlay/registerLocale/registerStyles/registerXAxis/registerYAxis/registerHotkey/getSupportedIndicators/getSupportedOverlays/getSupportedLocales/getSupportedHotkeys/getSupportedFigures`
- **C7 验收**：C2/C4 自动 diff 全绿；矩阵模板字段全部非 E

## 阶段 D：样式树与 Options 对齐（P1）

- **D1 Styles Kotlin 化**：按 Styles.ts 逐接口 data class（字段名/类型/默认值对照）：Margin/Padding/LineStyle/SmoothLineStyle/PathStyle/PolygonStyle/RectStyle/TextStyle/StateLineStyle/StateTextStyle/ChangeColor/GradientColor/FeaturePathStyle/FeatureIconFontStyle/FeatureStyle/GridStyle/Tooltip*(ShowRule/ShowType/legend/title/features)/CandleStyle(type,bar,area,priceMark,tooltip)/IndicatorStyle/CrosshairStyle/OverlayStyle/SeparatorStyle/XAxisStyle/YAxisStyle
- **D2 默认值对照**：上游 defaultStyles vs 现有 ChartTheme diff 表，不一致处以上游为准修正（现自定义主题登记迁移说明）
- **D3 渐进接线**：样式树先全量定义；绘制消费按优先级（candle.bar 回调 → indicator lines/bars/circles → tooltip 文本/legend → crosshair/overlay 点 → grid/separator），未接线字段矩阵标 `wired=false`
- **D4 setStyles 语义**：字符串内置主题（registerStyles dark/light）+ DeepPartial merge（对齐 utils.merge）

## 阶段 E：示例对齐（人工验收基准，P1）

- **E1**：以上游官方 demo 提取功能清单 → `docs/alignment/demo-checklist.md`（周期/指标选择、主图类型、画线工具、样式设置面板、loadMore 分页、实时更新、扩展指标线、精度/时区切换），每项含上游操作路径与预期
- **E2**：app-sample 重排 UI 信息架构对齐 demo（顶部周期+指标+主图选择行、样式设置面板、16 种画线工具栏、分页/实时开关）——复用现有功能不重写内核
- **E3**：checklist 逐项人工验收回填（含 TalkBack/字体 2.0 回归），全绿为过

## 阶段 F：防漂移工具链（持续）

- **F1** 模板元数据自动 diff 进常规测试（C2/C4）
- **F2** 上游检查清单衔接 `docs/upstream-sync.md`：compare 时同时 diff Chart.ts/Options.ts/Styles.ts/模板字段 → 更新矩阵
- **F3** 矩阵统计脚本：aligned/adapted/intentional/missing 计数，unknown>0 即失败

## 执行批次与出口

| 批次 | 内容 | 出口条件 |
|---|---|---|
| 1 | 阶段 A | 3 文档 + 矩阵 100% 覆盖 0 unknown |
| 2 | 阶段 B | 门面方法全映射、事件 9 种接线、sample 冒烟走门面 |
| 3 | C1/C2/C6+F1 | 27 指标元数据自动 diff 0 差异 |
| 4 | C3~C5 | 16 覆盖物 diff 0 差异、figure 注册对齐 |
| 5 | 阶段 D | Styles 树全量定义+默认值对齐+高优接线 |
| 6 | 阶段 E | demo-checklist 人工验收全绿；oracle 与 208 测试保持 0 失败 |

## 明确不做（边界）

- 内核不重写为上游 Store/View/Widget 的 DOM 结构映射（保留现引擎，门面提供 TS 语义）
- DOM/CSS/浏览器事件不对齐（矩阵标 C，提供 Android 等价说明）
- 有意差异 D-01/D-02/D-03 不回退（矩阵标 D）
- 内置模板绘制不改为「上游 createPointFigures 逐字翻译」，模板层提供对齐接口（内置仍走现渲染器；自定义模板走新 Figure 描述接口）