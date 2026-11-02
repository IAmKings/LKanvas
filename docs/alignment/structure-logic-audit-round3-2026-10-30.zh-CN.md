# Compose 工程与 KLineChart 结构、逻辑一致性第三轮审计

- 日期：2026-10-30（Asia/Shanghai）；与第二轮同日，单独保存本报告。
- 对象：`9ffd1281dd3feac1cc5a3be52379139ff6ef18fb`，core、门面与发布脚本版本均为 `0.7.0`。
- 比较点：第二轮对象 `cc9ad7e9a14db263316941c83281ef28fbcf0c96`。
- 上游固定基线：v10.1.0，`d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df`。
- 验收口径：沿用前两轮 S1/S2/S3/L1/L2/L3/V1/U1，以结构、控制流、数据契约和补丁局部可对应性为主；不以同名、拆文件、台账标绿或测试数量代替一致性。
- 范围与负载：单图表内正常的历史分页、实时推送、切品种、多个 pane/轴和覆盖物交互；含异步回调并发，这属于 DataLoader 已公开支持的使用方式。
- 改动边界：开始时工作区干净；本次只新增本报告。没有修改生产源码、仓库测试、构建配置、前两轮报告和上游快照。

## 1. 结论

**本轮继续取得实质进展，但仍不能按原标准宣布“结构与逻辑完整对齐”或“R01–R14 全项收口”。**

现在已有可保留的局部同步基础：27 个指标的规范 calc 路径仍在；16 个覆盖物不再仅有空定义，已经实际承担图形生成；StylesPatch 解决了“未提供字段”与“显式默认值”的区分；实时订阅不再被普通分页取消；初始数据不足时能自动续拉；动态 pane 的查询、设高和迁移已经贯通；真实 arc 补丁也有可独立重建的前后行为差异。

仍有关键阻断：**回调的代次检查没有延续到真正的数据写入，副 pane 的轴/值域仍与绘制分离，覆盖物编辑和十字线仍在部分路径使用主 pane 上下文。** 一些领域规则被复制到多个函数，导致局部修复后另一条路径继续保留旧逻辑。这正是原始验收希望避免的维护成本。

本报告列出 **11 项具体剩余发现**，每项有输入情形、源码证据及最小处理方向。没有因为原发现存在子项就一概否认已经完成的修复。

| 维度 | 第三轮判断 | 原因 |
|---|---|---|
| S1 来源与符号可追溯 | 部分通过 | 固定快照真实；映射补充了条目，但校验函数未执行、R 行未解析，仍有计划路径和缺项 |
| S2 职责结构 | 明显改善，未通过整体验收 | 指标/覆盖物有真实文件落点；Host 写入、轴、pane 转换和手势仍分散重复规则 |
| S3 算法与控制流形态 | 局部通过 | 指标 calc 和 arc 补丁可对应；部分覆盖物几何、attrs 及调用顺序仍不同 |
| L1 数据状态机 | 不通过 | 分页与自动续拉旧复现已修；旧回调跨切换写入仍可复现，见 T01 |
| L2 参数与公共契约 | 部分通过 | StylesPatch、absolute 原义、动态 pane 和动画基本场景已修；复杂映射及配置回读仍不完整 |
| L3 注册→执行→绘制/命中 | 部分通过 | 模板生成已进入执行链；副 pane 轴、编辑、命中和通用 figure 仍有旁路 |
| V1 验证证据 | 有进步，未形成完整门禁 | 原有 363 项通过，新增临时组合探针仍确认差异；R01–R14 未被脚本校验 |
| U1 真实源码补丁局部同步 | 本次 arc 演练通过（限定范围） | 有真实提交和有限 diff；独立重建修复前代码得到 false，当前得到 true；不能外推到所有领域 |

**后续不需要回退本轮，也没有依据要求再全库重写。** 应保留已正确的模板与补丁落点，将剩余调用链收敛到同一上下文和执行入口后再验收。

## 2. 实际核验结果

### 2.1 基线、样本与测试

| 项目 | 本次结果 | 证明范围 |
|---|---|---|
| 官方固定提交归档 vs 本地 `third_party/.../src` | 155/155 文件逐字节一致；修改、缺失、额外文件均为 0 | 固定上游对照没有被修改 |
| TS 指标样本重新生成到临时目录 | 27/27 JSON 与仓库内容一致 | 默认 oracle 样本可再生 |
| core 测试 | 222 项，失败/错误/跳过均为 0 | 当前 core 断言 |
| Compose debug 单元测试 | 141 项，失败/错误/跳过均为 0 | 当前门面、状态与 JVM 可测试分支 |
| 临时第三轮审计探针 | 21 项均通过：10 项确认修复、11 项确认差异 | 清单见 §6；差异探针通过表示成功复现问题 |
| check.py | 命令退出成功，解析 273 行 | 文档覆盖和被解析条目的格式/引用检查，不等于执行行为验收 |
| 官方 main compare | 仍为 3 个提交，涉及 2 个 README 与 arc.ts | 本次查询未发现第二轮已知 arc 修复之后的其他源码差异 |

原有测试禁用构建缓存并实际执行：

```sh
./gradlew :lkanvas-core:cleanTest :lkanvas-compose:cleanTestDebugUnitTest \
  :lkanvas-core:test :lkanvas-compose:testDebugUnitTest \
  --no-build-cache --console=plain
```

生产 Kotlin 源码规模为 core 81 文件/7,119 行、Compose 26 文件/7,667 行。规模只界定审计对象，不作为质量评分。

上游 main 固定到 `66dd45b31e93b57a4c75a3e0b98bc2a8393df36e`，比较入口为 [官方固定 compare](https://github.com/klinecharts/KLineChart/compare/d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df...66dd45b31e93b57a4c75a3e0b98bc2a8393df36e)。v10.1.0 快照保持不动，本地另行采纳 arc 补丁，是正确的基线管理方式。

### 2.2 相比第二轮已确认的改善

1. **分页与实时订阅使用不同代次。** 临时用例确认分页在途时当前实时推送仍可更新；第二轮 R01 的直接复现已消失。
2. **数据提交后发出范围变化。** INIT 只有一根且 more=true，无需手势即出现后续请求；第二轮 R03 的直接复现已消失。
3. **StylesPatch 的字段存在语义成立。** dark 下只隐藏 grid 不再重置 separator，随后显式设回 show=true 也生效。完整 Styles 重载现在被明确作为全量替换，不能再将它当局部补丁使用。
4. **简单 absolute/pane 转换修正。** 同时间戳切 absolute 不改变 x；副 pane 的 absolute y 与 relative y 相差该 pane 的 top。
5. **动态 pane 查询、设高、迁移可用。** 新 RSI pane 可查、可设为 123 px，迁移后旧空 pane 被移除；内置 VOL 在布局完成后创建也会重新布局，不再是零高度。
6. **公开 Y 轴的 spec 注册已统一。** 注册 log spec 后创建能得到该 spec，创建额外轴也不再修改主轴规格；模板的其他默认字段仍有遗漏，见 T02。
7. **十字线 null 清除及按 x 推导索引已修。** 第二轮这两个直接复现通过；动态 pane 的 y 限制仍未修，见 T05。
8. **缩放动画已经有中间状态。** 128 ms 动画会从初值经过中间间距到达目标，不能再表述为“duration 完全未执行”。
9. **覆盖物生成规则迁回模板。** renderer 已去掉内置模板名称分发，改为统一调用 createPointFigures；这是实际职责改善。
10. **真实 arc 补丁可局部验证。** 本次从 `b63d173^` 重建旧 FigureHit，在同一 -0.5° 样例上旧实现 false、当前 true；当前 ArcEndpointDrillTest 的两项用例也通过。见 [Figures.kt:60](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/figure/Figures.kt:60)、[ArcEndpointDrillTest.kt:19](../../lkanvas-core/src/test/kotlin/io/lkanvas/core/figure/ArcEndpointDrillTest.kt:19)。

## 3. 第二轮 R01–R14 逐项结论

| 原编号 | 第三轮状态 | 复核结论 |
|---|---|---|
| R01 分页导致实时失效 | 已修复原复现 | subscriptionGeneration 不再随普通分页增加，分页后推送正常 |
| R02 旧数据写新品种 | 仍未关闭 | INIT 绑定 key 有进步；写入前没有复核会话，非 INIT 接收 key 却不使用；T01 |
| R03 提交后无自动分页链 | 已修复原复现 | onSnapshot→emitRangeChange→边界检查已贯通，INIT 不足时能续拉 |
| R04 局部 Styles 重置 | 已修复，按新 patch 契约 | StylesPatch 局部应用保留其他字段，恢复默认值也正确 |
| R05 absolute/pane 转换 | 部分修复 | absolute 原义已修；副轴高度、叠加指标值域仍与渲染不同；T02/T03 |
| R06 轴实例不统一 | 部分修复 | spec 注册与同步有进步；模板默认字段丢失、副 pane 轴仍用主图映射；T02 |
| R07 pane 查询/迁移 | 已修复所检复现 | 创建、查询、设高、迁移及空 pane 清理通过；不等于所有 Layout 配置均已对齐 |
| R08 figure/visible | 部分修复 | 动态 pane 可见性与 BAR 分发已增加；主图/额外轴/固定副图仍绕开通用入口，attrs 未保留原几何语义；T06 |
| R09 覆盖物名称分支/pane | 部分修复 | 名称分支已移除、模板已执行；编辑输入、边界坐标、几何、层级及命中仍不一致；T04/T07/T08/T09 |
| R10 Crosshair | 部分修复 | null/x 已修，动态 pane 的 y 仍被截回主图；T05 |
| R11 动画/布局接口 | 部分修复 | 已有真正动画；Layout 字段、配置回读、完整图片格式契约仍不完整；T10 |
| R12 验收工具/映射 | 部分修复 | SLA 行不再豁免；新结构校验函数未调用，R 行完全不解析，映射仍缺 2 个指标；T11 |
| R13 无 before/after 演练 | 本次限定通过 | 有真实源码提交、有限本地 diff，可独立重建 before/after |
| R14 arc 端点容差 | FigureHit 修复通过 | 原端点样例修复；覆盖物 UI 仍走另一套未修的 arc 命中，是另一个执行链问题，见 T08 |

上述判定只关闭已核验的原问题，不将所有同领域子项自动判为通过，也不把已经修复的问题原样重复登记。

## 4. 必须修复的剩余阻断

### T01 · P1：会话检查仍止于回调入口，旧数据可以在切换完成后写入

**职责：** Store 负责判定回调归属；Host/ChartEngine 负责实际应用数据。

**问题：** generation/subscriptionGeneration 比较在获取 Store 锁之前，真正应用时不再复核。检查通过后若遇到切换，旧动作仍可排队执行。INIT 会按旧 key 重置整个状态；Backward/Forward/Update 虽接收 key，却完全不使用，直接向当前数据提交。

**可复现结果：** 让旧回调通过代次检查后等待 Store 锁，在锁内完成 B 的切换与 INIT，再释放旧回调：

- A 的旧实时 close=999 进入已就绪的 B 数据，B 的 candle 列表成为 `[200,999]`，marketKey 仍为 B。
- A 的旧 INIT 随后可把状态 reset 为 A、close=99，而门面 getSymbol 仍为 B。

临时探针只控制调度和锁竞争，数据经公开 DataLoader 回调提交；没有更改生产会话字段。这是公开支持“任意线程回调”的真实并发交错，不是要求宿主重复调用回调。

**证据：** [Store.kt:169](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/store/Store.kt:169)、[Store.kt:195](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/store/Store.kt:195)、[Store.kt:240](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/store/Store.kt:240)、[LKanvasChart.kt:458](../../lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:458)。现有 Parity9 先返回 A、再切 B，并等 B 数据覆盖后才断言，未覆盖该交错：[Parity9DataPipelineTest.kt:136](../../lkanvas-compose/src/test/kotlin/io/lkanvas/chart/parity/Parity9DataPipelineTest.kt:136)。

**最小处理方向：** 将不可变会话标识/代次带到唯一串行写入入口，在真正执行变更前复核。所有操作，包括 INIT 和实时更新，都必须使用该门禁；仅绑定 key 不能区分 A→B→A 的不同会话。

**不修复：** 仍有跨品种数据污染及旧会话回退风险；“检查点 key 绑定”不能作为端到端关闭 R02 的依据。

### T02 · P1：副 pane 的额外轴仍按主 pane 高度投影，轴实例也丢失所属上下文

**职责：** 每个轴应属于确定 pane，持有该 pane 的值域、变换、几何和默认配置。

**问题：** syncExtraAxes 把全部 pane 的轴放入同一组 map，ExtraAxisMeta 没有 paneId；axisValueToY/axisValueAt 对这些轴一律使用 `paneHeightsPx.main`。drawExtraPane 则按自身高度和普通公共值域画图，不消费绑定轴 spec/reverse。主图额外轴循环还会遍历副 pane 的轴记录，可能在主图区出现无关轴标签。

公开轴注册也只桥接 name/createSpec，模板 reverse/inside/position/scrollZoomEnabled/gap 不进入构造。临时注册 reverse=true 的模板后，查回模板和创建轴均为 false。

**复现：** RSI pane 高 80 px，主 pane 高 480 px，绑定 log 额外轴后，同一点预期 pane 相对 y≈21.144，convert 实际 y≈126.861，相差约 105.718 px。误差不是字体或 Float 精度造成的。

**证据：** [LKanvasChart.kt:1069](../../lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:1069)、[ChartState.kt:382](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:382)、[PaneRenderers.kt:134](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:134)、[PaneRenderers.kt:316](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:316)、[LkanvasRegistry.kt:125](../../lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LkanvasRegistry.kt:125)。

**最小处理方向：** 在实际轴上下文保留所属 pane、默认配置、spec 与 bounding；按 pane 选择轴，绘制、转换和标签复用该上下文。不能仅把“同步范围”从 main 扩为 all，而让下游继续按 main 处理。

**不修复：** 单主图多轴测试可通过，副图绑定轴仍显示错误。上游轴补丁仍需人工维护多个分离规则。

### T03 · P1：叠加指标的转换值域与实际渲染值域不同

**职责：** 同一 pane 的全部可见指标应共同决定值域，转换与绘制使用同一结果。

**问题：** paneValueToY/paneValueAt 对动态 pane 只取第一个可见实例；drawExtraPane 聚合全部可见实例所有列。MACD pane 的转换也未完整复用 renderer 对 0 基线的规则。

**复现：** 同一个 pane 叠加 MA(5) 与 BOLL(20,10)，按实际渲染公共值域计算的相对 y≈45.918，门面转换得到 y≈105.932。只做 convertToPixel/convertFromPixel 往返仍可能通过，因为两端共用了同一套错误的第一实例值域。

**证据：** [ChartState.kt:450](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:450)、[ChartState.kt:495](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:495)、[PaneRenderers.kt:325](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:325)。

**最小处理方向：** 以可见实例、figure/baseValue 和轴规则生成一份 pane 值域，供计算坐标、绘制、覆盖物和标签共同使用，删除第一实例的近似分支。

**不修复：** 叠加后画线/宿主坐标与图形位置不符，后续值域补丁必须在两份算法中同步修正。

### T04 · P1：覆盖物绘制开始感知 pane，但编辑输入仍用主轴，部分模板边界也混用坐标系

**职责：** 同一覆盖物的点选、拖动、绘制和命中应使用实例所属 pane 的同一坐标系统。

**问题：** 新 overlayPointToPx(p,paneId) 已进入绘制/命中；手势创建和拖动仍调用无 pane 参数的 overlayPointFromPx，其内部只执行 mainPaneValueAt。renderer 给模板的边界又是 `(0,0,width,paneBottomAbsolute)`，而不是该 pane 的局部 `(0,0,width,height)`。

**复现：** 在 RSI pane 中把 value=50 转为屏幕触点后，经实际手势所用输入函数得到 value≈113.141，而不是 50。对于副 pane 的 verticalStraightLine，模板从 y=0 画到绝对 bottom，会跨越主图；FibLevel 则使用 pane 相对 y 后直接绘制，缺少 pane top 平移。

**证据：** [ChartState.kt:549](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:549)、[ChartState.kt:563](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:563)、[ChartGestures.kt:243](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/gesture/ChartGestures.kt:243)、[OverlayRenderer.kt:75](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:75)、[OverlayRenderer.kt:93](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:93)、[VerticalStraightLine.kt:21](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/extension/overlay/VerticalStraightLine.kt:21)。

**最小处理方向：** 创建/拖动从实例取得 pane/轴上下文，模板统一使用 pane 局部坐标，在绘制/命中边界只做一次平移并裁剪；磁吸也使用该上下文。

**不修复：** 已能在副图显示的覆盖物，一经编辑仍会改变数值或跳位。仅把 paneId 存入实例不构成生命周期一致。

### T05 · P1：动态 pane 十字线仍被截回主图，值标签也始终读主轴

**职责：** 十字线应保留触点所在 pane 的纵向坐标，并由该 pane 的轴显示数值。

**问题与复现：** showCrosshairAt 和 applyCrosshairAction 的 y 上限仍是 main+vol+macd，没有动态 pane；输入 RSI pane 中点，crosshairPosition.y 被钳制到该 pane 上方。门面传入的 Crosshair.paneId 也未进入 applyCrosshairAction。标签函数已修 reverse 主轴问题，但仍始终调用 mainPaneValueAt。

**证据：** [ChartState.kt:622](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:622)、[ChartState.kt:894](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:894)、[LKanvasChart.kt:418](../../lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:418)、[ChartRenderer.kt:330](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/ChartRenderer.kt:330)。

**最小处理方向：** 以实际 pane 集合和 bounding 处理 y，保留 pane 归属，标签按同一轴反算。null 和 x 的已修逻辑应保留。

**不修复：** 主图联动用例已正确，动态副图的长按/联动仍显示错误位置或价格。

### T06 · P1：通用 figure 管线只覆盖动态 pane，其他渲染路径仍保留旧逻辑

**职责：** 任意 pane 的指标都应按相同的 figure.type、attrs、styles 和实例契约生成图形。

**问题与情形：** drawInstanceFigures 只从 drawExtraPane 调用；主图及主图额外轴仍直接 drawLinePlots，固定 VOL/MACD 继续有专用路径。因此在 main 或额外轴上创建 MACD，BAR 列仍可走折线。attrs 现在虽然被调用，却被当成样式 map，只取 color/lineWidth，未消费上游 attrs 的坐标、尺寸等几何属性；实例级 styles 同样没有形成完整合并规则。

**证据：** [PaneRenderers.kt:118](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:118)、[PaneRenderers.kt:178](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:178)、[PaneRenderers.kt:358](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:358)、[PaneRenderers.kt:692](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:692)、[PaneRenderers.kt:767](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:767)；对照 [上游 IndicatorView.ts:134](../../third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:134)。

**最小处理方向：** 将所有 pane/轴入口接到同一 figure 执行流程，分别处理几何 attrs 与绘制 styles，保留 figure 顺序和基线规则；不要继续为每个 pane 修一份分支。

**不修复：** 新动态副图的 BAR 修复不能推广为全部显示模板一致。模板补丁仍会遗漏某些 renderer。

## 5. 应修复的契约与维护问题

### T07 · P2：priceLine 的几何仍与上游不同，模板上下文也不足以保留格式规则

**问题与复现：** 本地 PriceLineDefinition 生成全宽线，给定锚点 x=200、width=600，线段从 0→600；上游从锚点 200→600。价格文本固定两位小数，上游按品种或指标精度，再经过 thousandsSeparator/decimalFold。

**证据：** [PriceLine.kt:20](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/extension/overlay/PriceLine.kt:20)、[上游 priceLine.ts:24](../../third_party/klinechart-v10.1.0/src/extension/overlay/priceLine.ts:24)。模板只接收 points、宽高、像素点及字符串扩展数据，无法获得上游同等的精度/格式化上下文。

**最小处理方向：** 恢复锚点到右边界的线段，给模板提供需要的轴/精度/格式函数；继续复用公共格式实现。

**不修复：** 文件和函数已经能定位补丁，但原有几何与格式行为还不能视为镜像。相同例子会画出不同图形。

### T08 · P2：arc 补丁修在 FigureHit，覆盖物真实命中却绕过它

**问题与复现：** core FigureHit 已正确判断角度、半径带和端点；OverlayRenderer.hitFigureShape 的 Arc 分支只判断到圆心距离小于 r+HIT_RADIUS，连起止角都不读。自定义覆盖物返回中心(100,100)、r=50、0°→90°的 Arc，点击圆心：core 命中 false，实际 OverlayRenderer.hitTest 返回覆盖物 ID。

**证据：** [Figures.kt:60](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/figure/Figures.kt:60)、[OverlayRenderer.kt:301](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:301)。临时探针直接调用当前覆盖物命中入口，没有依赖截图判断。

**最小处理方向：** 将图形描述转换为同一个 core Figure，并复用 FigureHit；必要的触摸容差通过参数传入。不要维护第二套简化 arc 算法。

**不修复：** 补丁演练在数学原语范围内成立，但真实覆盖物仍保留错误行为。这说明同一规则的执行入口尚未收敛。

### T09 · P2：覆盖物绘制排序方向相反，命中会选到被遮住的对象

**问题与复现：** drawOverlays 和 hitTest 都按 zIndex 降序遍历。画布是后画覆盖先画，因此高 z 先画后会被低 z 覆盖；命中却优先返回高 z。临时记录模板调用顺序，z=10 与 z=0 的绘制实际为 `[high,low]`。

上游先升序排序，再顺序绘制，高层最后画。绘制与命中不能机械使用同一遍历方向。

**证据：** [OverlayRenderer.kt:49](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:49)、[OverlayRenderer.kt:194](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:194)、[上游 Store.ts:1570](../../third_party/klinechart-v10.1.0/src/Store.ts:1570)、[上游 OverlayView.ts:483](../../third_party/klinechart-v10.1.0/src/view/OverlayView.ts:483)。

**最小处理方向：** 保留一份层级顺序，绘制从低到高、命中从高到低；选中浮起和 fixedZLevel 按原契约处理。

**不修复：** 所见和所选不同。两端使用同一个排序表达式并不等于逻辑一致。

### T10 · P2：Layout 配置补齐了部分入口，但未保持存取及完整配置语义

**问题与复现：** 设置 paneDragEnabled 后 getLayoutOptions 不返回它；动态 pane 的 heights 也不在 getter 返回 map 中。上游 Layout 包含 barSpaceLimit/pane/yAxis 默认规则，本地仍以 paneHeights 与内置 vol/macd 的开关 map 代替，无法表达完整默认轴/窗格配置。临时用例设置 vol=false 后回读得到 null。

图片导出开始消费非白背景，但默认 WHITE 分支直接返回源图，不执行默认背景合成；bmp 也仍转为 png。这些平台差异应准确登记，而非因参数存在判为全部同义。

**证据：** [LKanvasChart.kt:961](../../lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:961)、[LKanvasChart.kt:970](../../lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:970)、[LKanvasChart.kt:989](../../lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:989)、[LKanvasChart.kt:1015](../../lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:1015)、[上游 Options.ts:88](../../third_party/klinechart-v10.1.0/src/Options.ts:88)。导出分支仅做静态检查，本次未执行 Android bitmap 集成测试。

**最小处理方向：** 将配置状态保存为同一个可查询对象，再由实际 pane/轴消费；明确并测试平台替代限制。图片背景合成不要把“等于默认值”误当成未提供。

**不修复：** 宿主保存/恢复配置和迁移上游示例仍需特殊处理，R11 不能完整关闭。

### T11 · P2：R01–R14 台账被解析器全部跳过，新增结构校验也未接入 main

**问题与实测：**

- parse_matrix 只接受 2–3 个大写字母加三位数字；新条目是 `R01`…`R14`。14 行虽然都标“行为通过”，解析结果中一行都没有，未接受状态或引用检查。
- validate_structure_map 已实现，但没有调用点。check.py 运行成功；单独调用该函数得到 15 条路径错误，包含缩略的 `...` 目标路径。应区分计划路径、缩略说明和真实实现证据，不能直接把这 15 条当作 15 个实现缺失。
- 指标映射表实为 25 行，仍缺 averagePrice.ts、bollingerBands.ts；去掉“待登记”字样不能证明 27 项已补全。
- 被解析的 273 行仍为：已声明237、行为通过19、不适用15、已接线2；没有“结构可对应”。其中19不包含新 R 行，不能用它证明本轮14项已通过。

**证据：** [check.py:104](../../tools/alignment/check.py:104)、[check.py:129](../../tools/alignment/check.py:129)、[check.py:312](../../tools/alignment/check.py:312)、[gap-matrix.md:420](gap-matrix.md:420)、[structure-map.md:51](structure-map.md:51)。

**最小处理方向：** 正式支持 R 编号格式，接入结构检查并明确可执行证据格式，按上游集合校验映射缺项；再把具体测试结果与符号映射作为验收材料。文件存在检查仍不能替代行为测试。

**不修复：** 本轮新验收项仍绕过工具。台账通过既不能证明结构完整，也不能反向证明237个已声明项均未实现，只说明记录未完成验收。

## 6. 临时探针与证据边界

临时测试类为 `audit.ThirdAuditTest`，通过 `/tmp/lkanvas-audit-round3-20261030/audit.init.gradle` 注入外部测试源。最终执行21项、失败/错误/跳过均为0：10项确认修复，11项确认差异。未将这些探针写入仓库或变成保护当前错误行为的正式测试。

| 方法 | 类型 | 本次确认 |
|---|---|---|
| fixedPaginationKeepsRealtime | 修复确认 | 分页不再使实时订阅失效 |
| fixedInitialBoundaryLoadsWithoutGesture | 修复确认 | 初始边界无需手势即续拉 |
| fixedPartialStylePreservesDarkAndRestoresDefault | 修复确认 | StylesPatch 保留其他字段并可恢复默认 |
| fixedSimpleAbsoluteMeaning | 修复确认 | absolute 不改 x，y 相差 pane top |
| fixedPaneQuerySizeAndMove | 修复确认 | 动态 pane 查询、设高和迁移 |
| fixedBuiltinPaneRelayoutAfterCreation | 修复确认 | 布局后新增 VOL 可获得非零高度 |
| fixedRegisteredSpecDoesNotChangeMainAxis | 修复确认 | 公开轴 spec 生效且不改主轴 |
| fixedBasicCrosshairAndClear | 修复确认 | x 推导索引和 null 清除 |
| fixedAnimationHasIntermediateState | 修复确认 | 动画有真实中间间距并到达目标 |
| realPatchBeforeAndAfter | 修复确认 | 真实历史代码 false，当前 arc 端点 true |
| oldRealtimeAcceptedBeforeSwitchCanWriteIntoB | 差异确认 | B 数据被追加旧 A 行情，T01 |
| oldInitAcceptedBeforeSwitchCanRevertSession | 差异确认 | 门面 B、数据状态回退 A，T01 |
| secondaryAxisUsesMainPaneHeight | 差异确认 | 副轴转换误用主 pane 高度，T02 |
| registeredAxisDefaultFlagsAreDropped | 差异确认 | 轴模板 reverse 默认字段丢失，T02 |
| stackedPaneConversionUsesOnlyFirstIndicator | 差异确认 | 叠加后转换/渲染值域不同，T03 |
| overlayGestureInputStillUsesMainPaneAxis | 差异确认 | 副图触点按主轴反算，T04 |
| dynamicCrosshairIsClampedBackIntoMain | 差异确认 | 触摸和 executeAction 均截断副图 y，T05 |
| priceLineStartsAtLeftInsteadOfAnchor | 差异确认 | 线段0→600，上游应200→600，T07 |
| actualOverlayArcHitBypassesPatchedCore | 差异确认 | arc 圆心在实际入口被误命中，T08 |
| painterVisitsHigherZBeforeLowerZ | 差异确认 | 模板实际绘制遍历先high后low，T09 |
| layoutGetterDropsConfiguredDragFlags | 差异确认 | 配置回读缺少已设置开关，T10 |

T01 使用受控锁竞争把正常并发交错固定下来，便于重复；没有修改生产字段。T09 记录真实 drawOverlays 的模板执行顺序，使用空图形产物和无操作绘制接收器，不代表本次生成了真机像素截图。T02/T03 将转换结果与生产绘制代码所用公式及范围比较，不将“同一函数的往返”误称为屏幕差分。

## 7. 后续同步能力与重新验收条件

| 领域 | 本轮结构改善 | 剩余同步成本 |
|---|---|---|
| 27 指标计算 | 规范 calc/同文件 Scratch 与 oracle 保持 | 每笔公式补丁仍需检查两路径；本轮没有破坏原有数值基线 |
| 16 覆盖物 | 生成逻辑已真正进入模板，删除名称分发 | pane 坐标、手势逆映射、格式上下文和命中仍需跨文件修复 |
| Store 数据 | 分页/订阅代次分离，提交后自动续拉 | 实际写入会话门禁仍不在唯一消费者入口 |
| pane/轴 | 动态 pane 管理贯通，轴 spec 可注册 | 多张派生表缺少完整 pane 上下文，转换和绘制仍各算值域 |
| figure 渲染 | 动态副图已有按类型分发入口 | 其他 pane 仍存在旁路，attrs 被改成样式而非几何契约 |
| 真实补丁 | arc 演练有有限源码变更和可重建前后结果 | 必须再确认实际用户入口是否使用被修复的公共实现 |

保持原验收标准，下一轮至少需要：

1. **在真正写入时验证会话。** 同时覆盖分页、INIT、实时、A→B→A，并验证每个可观察状态不会被旧任务污染。
2. **每个 pane/轴只有一套值域和几何上下文。** 覆盖多实例叠加、副图额外轴、log/reverse、触摸与 absolute 转换。
3. **模板规则在所有执行入口一致。** 几何、样式、命中、编辑和层级顺序使用相同描述及公共数学实现。
4. **所有 pane 都走完整 figure 契约。** BAR/LINE、attrs、styles、可见性和基线不是分别修补。
5. **工具确实校验本轮编号和真实映射。** R表、SLA表、结构映射和测试运行证据相互对应；计划路径不能冒充已实现路径。

验收应继续按“一个上游变更块能定位有限本地改动、共同输入产生对应结果”评判，不采用源码文本相似率或修复数量百分比。

## 8. 审计边界

本次深入复核了第二轮修复涉及的 Store/Host/ChartEngine 数据入口、样式补丁、pane/轴/转换、十字线、覆盖物模板/绘制/命中/手势、figure 渲染入口、动画、配置和验收工具。27个指标的数值依据现有oracle和双路径测试，未重新逐行证明所有公式和所有参数边界。

未重做真机截图、完整触控端到端、TalkBack、Macrobenchmark、R8及Maven consumer；未重新认证现有性能报告。未穷举全部样式字段、图片编码、几何和异常输入。静态确认的分支缺口与运行探针结果已分别说明。

本报告没有实施修复。当前优先风险是T01的跨会话数据写入，其次是副 pane 的坐标、轴和编辑一致性。结构已经更接近可局部维护的移植，但仍不应把363项测试通过或台账全绿表述为完整结构与逻辑对齐。
