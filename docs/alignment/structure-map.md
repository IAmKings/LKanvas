# 上游 ↔ 本地结构映射台账（structure-map）

> 目的（审计 §8 第 6 行）：对每个可迁移符号登记「上游路径/符号 → 本地路径/符号 → 必要差异 → 验证用例」，使上游一个变更块能明确定位有限本地改动；不再以「引擎等价」一笔带过。
>
> - 上游基线：KLineChart v10.1.0（`d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df`），快照 `third_party/klinechart-v10.1.0/src/`（155 文件，逐字节核验一致）。
> - 状态沿用 gap-matrix 四态：已声明 / 已接线 / 行为通过 / 结构可对应。
> - 映射规则：领域代码（Store 数据路径、Chart 门面、component 契约、extension 模板、common 类型与工具）镜像上游文件与符号命名（camelCase → PascalCase.kt）；平台层（pane/view/widget 的 DOM-Canvas 结构）不镜像，由 Compose 渲染层承担并在本台账登记平台替代映射。

## 1. 文件级映射总表

| 上游文件 | 本地文件（0.6.0 目标） | 现本地文件（迁移前） | 必要差异 | 状态 |
|---|---|---|---|---|
| `Store.ts`（数据路径段） | `lkanvas-core/.../store/Store.kt`（0.6.0 已落地：load/addData/resetData/边界触发/代次门禁） | `ChartDataStore.kt`（会话门禁）与 `ChartEngine.kt`（计算编排）保留为内核 | 数据路径补丁→Store.kt 局部移植（Parity4） | 行为通过 |
| `Chart.ts` | `lkanvas-compose/.../chart/LKanvasChart.kt`（门面方法集逐一对齐） | 同名（方法语义待对齐） | Android 无 DOM：init(state)、Compose 组合 | 已声明 |
| `Event.ts`+`Eventful.ts` | `lkanvas-core/.../store/ChartEvents.kt` | 门面 actionCallbacks | notExecuteAction 抑制语义（SLA-015） | 已声明 |
| `Options.ts` | `lkanvas-compose/.../chart/ChartOptions.kt`（对齐字段集） | 同名 | 平台适配 | 已声明 |
| `common/Data.ts` | `lkanvas-core/.../common/Data.kt` | `ChartTypes.kt`（扁平 payload） | 无（嵌套 payload 属上游原貌，SLA-015） | 已声明 |
| `common/{Crosshair,Point,BarSpace,VisibleRange,Period,SymbolInfo,Action,DataLoader,Nullable,DeepPartial}.ts` | `lkanvas-core/.../common/` 同名.kt | 分散在 ChartTypes/SymbolInfo/DataLoader | 类型等价 | 已声明 |
| `common/Styles.ts` | `lkanvas-core/.../common/Styles.kt`（compose 侧 typealias 保路径） | `lkanvas-compose/.../styles/Styles.kt` | 主题桥接留 compose | 已声明 |
| `common/utils/typeChecks.ts`（merge） | `lkanvas-core/.../common/utils/Merge.kt` | `StylesMerge.kt`（skip-defaults 规则） | 移除 skip-defaults，提供即合并（SLA-009） | 已声明 |
| `common/utils/format.ts` | `lkanvas-core/.../common/utils/Format.kt` | `LkanvasUtils.kt` + `ChartText.kt` | timezone/千分位/decimalFold/formatter 注入（SLA-011） | 已声明 |
| `common/utils/number.ts` | `lkanvas-core/.../common/utils/NumberUtil.kt` | LkanvasUtils 内联 | — | 已声明 |
| `component/Indicator.ts` | `lkanvas-core/.../component/Indicator.kt` | `IndicatorEngine.kt` 多类型 | calc 规范入口 + Scratch 优化双路径（SLA-002） | 已声明 |
| `component/Overlay.ts` | `lkanvas-core/.../component/Overlay.kt` | `OverlayManager.kt`+`OverlayApi.kt` | 生命周期/类型/模板合一 | 已声明 |
| `component/Axis.ts`+`XAxis.ts`+`YAxis.ts` | `lkanvas-core/.../component/{Axis,XAxis,YAxis}.kt` | `YAxisSpec.kt`+`LinearYAxis.kt`+`AxisApi.kt` | 轴实例持有映射并被绘制/转换共消费（SLA-010/012） | 已声明 |
| `component/Figure.ts` | `lkanvas-core/.../component/Figure.kt` | `Figures.kt`（core）+`FigureRenderer.kt`（compose） | Canvas→Compose 绘制 | 已声明 |
| `extension/indicator/*.ts`（27） | `lkanvas-core/.../extension/indicator/` 上游同名 27 文件 + `Index.kt`（0.6.0 已落地） | 4 个合并文件已删除 | calc 逐行对照；oracle 27/27 + DualPathParityTest 双路径 | 行为通过 |
| `extension/overlay/*.ts`（16） | `lkanvas-core/.../extension/overlay/` 上游同名 16 文件 + `Index.kt`（0.6.0 已落地） | `OverlayTemplates.kt` 已删除（契约移 OverlayDefinition.kt） | 渲染/命中/手势共表（Parity5）；`OverlayGeometry.kt` 保留为共享几何助手 | 行为通过 |
| `extension/figure/index.ts`（注册表） | `lkanvas-core/.../extension/figure/Index.kt`（0.6.0：类型+命中开关注册表） | 原语在 `core/figure/Figures.kt`、绘制在平台层 `FigureRenderer.kt`（单一 when，稳定） | 图形绘制为平台关注点，注册/命中经共表；逐文件拆分不带来补丁映射收益（登记决策） | 已接线 |
| `view/IndicatorView.ts:142` figure.attrs 几何协议 | 本地 `PlotDef.attrs` 消费于 `PaneRenderers.kt drawBarPlots`（BAR top/bottom/x/width，pane 相对坐标 + paneTop 平移）；**LINE 型 attrs 几何消费为平台差异**——本地 LINE 由列+视口生成（drawLinePlots native drawLines 批量），非上游 coordinates 点列，attrs 几何不适用 | `FigureRenderer.kt` / `drawLinePlots` | 上游 attrs 为每行几何覆盖（坐标/尺寸）；本地 LINE 走数据驱动批量路径。差异登记 gap-matrix §16 N04 | 已接线 |
| `extension/y-axis/{normal,logarithm,percentage}.ts` | `lkanvas-core/.../extension/yaxis/` 3 模板 + `Index.kt`（0.6.0 已落地，createYAxis 消费 template） | `YAxisSpec.kt` 保留为规格/数学 | 模板按名构造轴实例并进主轴规格（Parity6） | 行为通过 |
| `extension/x-axis/normal.ts` | `lkanvas-core/.../extension/xaxis/Normal.kt` + `Index.kt` | `ChartState.xAxisState` | — | 已声明 |
| `extension/i18n/{en-US,zh-CN}.ts` | `lkanvas-core/.../extension/i18n/` 同名 + `Index.kt` | `ChartLocale.kt` | registerLocale 消费（SLA-004） | 已声明 |
| `extension/styles/{light,dark}.ts` | `lkanvas-core/.../extension/styles/` 同名 + `Index.kt` | `StylesDefaults.kt`/`ChartTheme.kt` 默认值 | 默认值 fixture 与上游 diff（SLA-016） | 已声明 |
| `extension/hotkey/*.ts`（5） | 不镜像（键盘 DOM 事件） | — | 平台不适用（C）；桌面等效物如需另立 | — |
| `pane/*.ts`、`view/*.ts`、`widget/*.ts`（32） | 不镜像文件（DOM/Canvas 结构）；职责映射见 §4 | `ChartRenderer.kt`+`PaneRenderers.kt`+`OverlayRenderer.kt`+`TooltipRenderer.kt` | 平台替代：Compose 单画布替代分窗格 canvas | 已声明 |

## 2. 符号级映射 — Store 数据路径（SLA-005/006，Phase 2 填充至结构可对应）

| 上游符号（Store.ts） | 本地符号 | 差异 | 验证用例 | 状态 |
|---|---|---|---|---|
| `_processDataLoad(type)` | Store.processDataLoad | loading 门禁一致；init/forward/backward 三分支 | Parity4DataPathTest | 已声明 |
| `_addData backward/forward`（同名方向 more 更新） | Store.addData | 消除交叉赋值 | Parity4DataPathTest | 已声明 |
| `_adjustVisibleRange` 末尾边界触发 | Store 内视口调整钩子 | from==0&&more.forward→forward；to==total&&more.backward→backward | Parity4DataPathTest | 已声明 |
| `resetData`（退订→改配置→清 loading→init） | Store.resetData | + 本地增强：请求代次门禁（D 类登记） | Parity4DataPathTest | 已声明 |
| `_processDataUnsubscribe` | Store.unsubscribeBar | 切换与 destroy 两路径生效 | Parity4DataPathTest | 已声明 |

## 3. 符号级映射 — 指标模板（Phase 3 逐个回填；27 文件）

> 每行：上游文件/`calc` → 本地文件/`calc`（规范路径），Scratch 增量为同文件优化路径（`@OptIn` 内部）；验证=oracle 数值 diff + calc/Scratch 双路径互验。

| 上游文件 | 本地文件 | 状态 |
|---|---|---|
| `movingAverage.ts` | `extension/indicator/MovingAverage.kt` | 已声明 |
| `exponentialMovingAverage.ts` | `extension/indicator/ExponentialMovingAverage.kt` | 已声明 |
| `simpleMovingAverage.ts` | `extension/indicator/SimpleMovingAverage.kt` | 已声明 |
| `movingAverageConvergenceDivergence.ts` | `extension/indicator/MovingAverageConvergenceDivergence.kt` | 已声明 |
| `volume.ts` | `extension/indicator/Volume.kt` | 已声明 |
| `averagePrice.ts` | `extension/indicator/AveragePrice.kt` | 已声明 |
| `awesomeOscillator.ts` | `extension/indicator/AwesomeOscillator.kt` | 已声明 |
| `bollingerBands.ts` | `extension/indicator/BollingerBands.kt` | 已声明 |
| `bias.ts` | `extension/indicator/Bias.kt` | 已声明 |
| `brar.ts` | `extension/indicator/Brar.kt` | 已声明 |
| `bullAndBearIndex.ts` | `extension/indicator/BullAndBearIndex.kt` | 已声明 |
| `commodityChannelIndex.ts` | `extension/indicator/CommodityChannelIndex.kt` | 已声明 |
| `currentRatio.ts` | `extension/indicator/CurrentRatio.kt` | 已声明 |
| `differentOfMovingAverage.ts` | `extension/indicator/DifferentOfMovingAverage.kt` | 已声明 |
| `directionalMovementIndex.ts` | `extension/indicator/DirectionalMovementIndex.kt` | 已声明 |
| `easeOfMovementValue.ts` | `extension/indicator/EaseOfMovementValue.kt` | 已声明 |
| `momentum.ts` | `extension/indicator/Momentum.kt` | 已声明 |
| `onBalanceVolume.ts` | `extension/indicator/OnBalanceVolume.kt` | 已声明 |
| `priceAndVolumeTrend.ts` | `extension/indicator/PriceAndVolumeTrend.kt` | 已声明 |
| `psychologicalLine.ts` | `extension/indicator/PsychologicalLine.kt` | 已声明 |
| `rateOfChange.ts` | `extension/indicator/RateOfChange.kt` | 已声明 |
| `relativeStrengthIndex.ts` | `extension/indicator/RelativeStrengthIndex.kt` | 已声明 |
| `stoch.ts` | `extension/indicator/Stoch.kt` | 已声明 |
| `stopAndReverse.ts` | `extension/indicator/StopAndReverse.kt` | 已声明 |
| `tripleExponentiallySmoothedAverage.ts` | `extension/indicator/TripleExponentiallySmoothedAverage.kt` | 已声明 |
| `volumeRatio.ts` | `extension/indicator/VolumeRatio.kt` | 已声明 |
| `williamsR.ts` | `extension/indicator/WilliamsR.kt` | 已声明 |

## 4. 平台替代映射（不镜像文件的职责对照，Phase 6 回填）

| 上游职责 | 本地承担 | 差异说明 | 状态 |
|---|---|---|---|
| `Pane/Widget/View` 分窗格 canvas | `ChartRenderer.kt` 单画布按 pane 布局绘制 | pane 集合以 Store 为统一来源（SLA-003） | 已声明 |
| `CandleBarView` 等逐 view | `PaneRenderers.drawCandles` 等函数 | 逐根回调消费（SLA-011） | 已声明 |
| `OverlayView` | `OverlayRenderer.kt` | 模板钩子驱动（SLA-013） | 已声明 |
| `Event/DOM 触摸` | `ChartGestures.kt` | 开关检查入 Store（SLA-011） | 已声明 |
| `Animation.ts` requestAnimationFrame | Compose `withFrameNanos`（Phase 7） | animationDuration 执行 | 已声明 |

## 5. 审计发现 → 映射条目索引

见 gap-matrix §14 SLA-001~016；本台账 Phase 逐批回填「状态 → 结构可对应」，Phase 8 终检时逐条核对。
