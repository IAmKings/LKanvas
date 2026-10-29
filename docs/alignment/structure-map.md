# 上游 ↔ 本地结构映射台账（structure-map）

> 目的（审计 §8 第 6 行）：对每个可迁移符号登记「上游路径/符号 → 本地路径/符号 → 必要差异 → 验证用例」，使上游一个变更块能明确定位有限本地改动；不再以「引擎等价」一笔带过。
>
> - 上游基线：KLineChart v10.1.0（`d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df`），快照 `third_party/klinechart-v10.1.0/src/`（155 文件，逐字节核验一致）。
> - 状态沿用 gap-matrix 四态：已声明 / 已接线 / 行为通过 / 结构可对应。
> - 映射规则：领域代码（Store 数据路径、Chart 门面、component 契约、extension 模板、common 类型与工具）镜像上游文件与符号命名（camelCase → PascalCase.kt）；平台层（pane/view/widget 的 DOM-Canvas 结构）不镜像，由 Compose 渲染层承担并在本台账登记平台替代映射。

## 1. 文件级映射总表

| 上游文件 | 本地文件（0.6.0 目标） | 现本地文件（迁移前） | 必要差异 | 状态 |
|---|---|---|---|---|
| `Store.ts` | `lkanvas-core/.../store/Store.kt` | `ChartDataStore.kt`+`ChartEngine.kt`+`ChartLoader.kt`+`Viewport.kt` 分散 | 会话代次门禁为本地增强（D，SLA-006）；Viewport 数学保留为内部助手 | 已声明 |
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
| `extension/indicator/*.ts`（27） | `lkanvas-core/.../extension/indicator/` 上游同名 27 文件 + `Index.kt` | `BuiltInIndicators.kt`+`WindowIndicators.kt`+`OscillatorIndicators.kt`+`RecursiveIndicators.kt` | calc 逐行对照；oracle 27/27 | 已声明 |
| `extension/overlay/*.ts`（16） | `lkanvas-core/.../extension/overlay/` 上游同名 16 文件 + `Index.kt` | `OverlayTemplates.kt` | 模板钩子唯一执行来源（SLA-013）；`OverlayGeometry.kt` 保留为共享几何助手 | 已声明 |
| `extension/figure/*.ts`（7+index） | `lkanvas-core/.../extension/figure/` 同名 7 文件 + `Index.kt` | `Figures.kt` 合并 | Canvas→Compose | 已声明 |
| `extension/y-axis/{normal,logarithm,percentage}.ts` | `lkanvas-core/.../extension/yaxis/` 同名 3 文件 + `Index.kt` | `YAxisSpec.kt` 内置枚举 | 模板按名构造轴实例（SLA-010） | 已声明 |
| `extension/x-axis/normal.ts` | `lkanvas-core/.../extension/xaxis/Normal.kt` + `Index.kt` | `ChartState.xAxisState` | — | 已声明 |
| `extension/i18n/{en-US,zh-CN}.ts` | `lkanvas-core/.../extension/i18n/` 同名 + `Index.kt` | `ChartLocale.kt` | registerLocale 消费（SLA-004） | 已声明 |
| `extension/styles/{light,dark}.ts` | `lkanvas-core/.../extension/styles/` 同名 + `Index.kt` | `StylesDefaults.kt`/`ChartTheme.kt` 默认值 | 默认值 fixture 与上游 diff（SLA-016） | 已声明 |
| `extension/hotkey/*.ts`（5） | 不镜像（键盘 DOM 事件） | — | 平台不适用（C）；桌面等效物如需另立 | — |
| `pane/*.ts`、`view/*.ts`、`widget/*.ts`（32） | 不镜像文件（DOM/Canvas 结构）；职责映射见 §4 | `ChartRenderer.kt`+`PaneRenderers.kt`+`OverlayRenderer.kt`+`TooltipRenderer.kt` | 平台替代：Compose 单画布替代分窗格 canvas | 已声明 |

## 2. 符号级映射 — Store 数据路径（SLA-005/006，Phase 2 填充至结构可对应）

| 上游符号（Store.ts） | 本地符号 | 差异 | 验证用例 | 状态 |
|---|---|---|---|---|
| `_processDataLoad(type)` | Store.processDataLoad | loading 门禁一致；init/forward/backward 三分支 | Parity4PaginationTest | 已声明 |
| `_addData backward/forward`（同名方向 more 更新） | Store.addData | 消除交叉赋值 | Parity4PaginationTest.moreDirection | 已声明 |
| `_adjustVisibleRange` 末尾边界触发 | Store 内视口调整钩子 | from==0&&more.forward→forward；to==total&&more.backward→backward | Parity4PaginationTest.boundaryTrigger | 已声明 |
| `resetData`（退订→改配置→清 loading→init） | Store.resetData | + 本地增强：请求代次门禁（D 类登记） | Parity4SymbolSwitchTest | 已声明 |
| `_processDataUnsubscribe` | Store.unsubscribeBar | 切换与 destroy 两路径生效 | Parity4SymbolSwitchTest.unsubscribeOrder | 已声明 |

## 3. 符号级映射 — 指标模板（Phase 3 逐个回填；27 文件）

> 每行：上游文件/`calc` → 本地文件/`calc`（规范路径），Scratch 增量为同文件优化路径（`@OptIn` 内部）；验证=oracle 数值 diff + calc/Scratch 双路径互验。

| 上游文件 | 本地文件 | 状态 |
|---|---|---|
| `movingAverage.ts` | `extension/indicator/MovingAverage.kt` | 已声明 |
| `exponentialMovingAverage.ts` | `extension/indicator/ExponentialMovingAverage.kt` | 已声明 |
| `simpleMovingAverage.ts` | `extension/indicator/SimpleMovingAverage.kt` | 已声明 |
| `movingAverageConvergenceDivergence.ts` | `extension/indicator/MovingAverageConvergenceDivergence.kt` | 已声明 |
| `volume.ts` | `extension/indicator/Volume.kt` | 已声明 |
| （其余 22 个模板 Phase 3 拆分时逐行登记） | | 已声明 |

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
