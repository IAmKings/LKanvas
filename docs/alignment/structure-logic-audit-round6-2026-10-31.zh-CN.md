# Compose 工程与 KLineChart 结构、逻辑一致性第六轮审计及目标收敛情况

- 日期：2026-10-31（Asia/Shanghai）。
- 审计对象：`f0b8dc09ede3b30867974797321ec6dab82bfbbe`，版本`0.10.0`；包含本轮末尾的文档纠偏提交。
- 对比起点：第五轮对象`f11f2e23a246374602a5423aa623d6a28d1a02c3`（0.9.0）。
- 固定上游：KLineChart v10.1.0，`d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df`。
- 标准不变：以领域职责、数据/参数契约、条件与调用顺序、实际消费者、共同输入行为和有限源码补丁的可对应性验收。不要求跨语言标点相同，不按文本相似率或测试数量评分。
- 范围：复核N01–N07，追踪相关加载/会话、pane/轴/值域、figure、覆盖物和格式链；扩展既有契约下的先后会话失败、多轴、多层级及坐标组合。正常负载为单图表、异步数据、多个指标/轴/覆盖物。
- 修改边界：开始时工作区干净；本次只新增本报告，未修改实现、仓库测试、构建配置、上游快照和前五轮报告。

## 1. 结论

**整体仍在向原目标收敛，但尚未通过完整结构与逻辑一致性验收，也尚未进入只剩最终验收的阶段。**

本轮有实质进展：内置pane的转换与绘制共用值域、叠加实例进入范围；figure回调收到真实value/candles，局部top/bottom正确平移；priceLine小数折叠与实例精度覆盖生效；固定层级对象恢复单点编辑；单个非法INIT后的推送已被阻断。这些修复应保留。

剩余问题仍有共同根因：**失败结果未绑定到发起会话，轴选择仍位于实例循环之外，部分图形字段继续被赋予不同含义，控制点的绘制与命中未共享同一层级/坐标上下文。** 某些注释声称已分组或已移入代码块，实际控制流尚未如此。

本轮将8个运行差异归为 **6项发现：4项P1、2项P2**。第五轮为7项、4项P1；条目范围有变化，不能按7→6换算完成率。可以确认功能和部分职责结构继续改善，但关键阻断数量尚未明显下降。

| 维度 | 本轮判断 | 主要依据 |
|---|---|---|
| S1 来源与符号可追溯 | 部分通过 | 155文件官方快照一致；27指标映射齐全；LINE attrs差异有登记，但登记不等于已满足原标准 |
| S2 职责/依赖结构 | 继续收敛，未整体通过 | 范围与上下文的实际消费者统一；轴仍取第一项，控点仍在另一绘制上下文 |
| S3 算法/控制流形态 | 局部通过 | calc与arc局部同步基础保持；BAR字段含义、LINE attrs及范围约束仍有差异 |
| L1 数据状态机 | 不通过整体验收 | 单次失败原复现已修；较早失败会清理较新有效会话，M01 |
| L2 参数与公共契约 | 部分通过 | 格式、Layout、相对坐标修复保留；矩形attrs、min/max、层级契约未闭合 |
| L3 注册→计算→绘制/命中/编辑 | 部分通过 | 真正多轴仍共用第一条投影，控点绘制与命中分离，M02/M04/M06 |
| V1 差分与门禁 | 部分通过 | 371项既有测试通过，外部组合验证仍复现差异；N06台账缺行 |
| U1 局部源码补丁同步 | 原arc演练限定通过 | 历史before/after、实际命中及负差角度绘制保持正确；没有外推全部领域 |

## 2. 实际核验结果

| 检查 | 本次结果 | 证明边界 |
|---|---|---|
| 官方固定提交归档 vs third_party/src | 155/155逐字节一致；修改/缺失/额外均0 | 固定来源可靠 |
| 上游TS oracle重生成到临时目录 | 27/27 JSON与仓库fixture一致 | 默认样本可再生，非全部参数证明 |
| core既有测试 | 222，失败/错误/跳过均0 | 当前core断言 |
| Compose debug既有单元测试 | 149，失败/错误/跳过均0 | 合计371 |
| 第六轮外部临时验证 | 55，失败/错误/跳过均0 | 47项基准/修复确认，8项成功复现剩余差异 |
| check.py | 通过；302行，状态合法及引用存在 | 不是运行行为或完整控制流证明 |
| 结构映射 | 27指标集合完整，结构引用检查无错误 | 本次人工核对集合 |

既有测试在外部测试注入前实际执行，并单独保存计数：

```sh
./gradlew :lkanvas-core:cleanTest :lkanvas-compose:cleanTestDebugUnitTest \
  :lkanvas-core:test :lkanvas-compose:testDebugUnitTest \
  --no-build-cache --console=plain
python3 -B tools/alignment/check.py
```

本次在线复核main仍为`66dd45b31e93b57a4c75a3e0b98bc2a8393df36e`，相对固定基线ahead 3，差异为两个README和arc.ts；没有发现第五轮之后的新源码差异。参见[固定到本次上游HEAD的官方compare](https://github.com/klinecharts/KLineChart/compare/d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df...66dd45b31e93b57a4c75a3e0b98bc2a8393df36e)。来源版本与移植实现的结构/逻辑一致性分别判断。

## 3. 第五轮N01–N07逐项结论

| 原编号 | 本轮确认修复的部分 | 当前结论及余项 |
|---|---|---|
| N01 INIT真实拒绝结果 | 非法B INIT后，其订阅推送不再写进A；正式Parity12也验证合法重试恢复 | 部分修复；旧B失败的异步清理会退订已就绪C，M01 |
| N02 每实例绑定轴 | 未绑定轴不再影响默认实例；第一个被选中轴使用自身范围，原RSI+MA中RSI点对齐 | 部分修复；两个真正绑定的轴仍共用第一条投影，内置pane仍忽略绑定轴，M02 |
| N03 全实例范围/转换 | 内置pane范围纳入叠加1000；VOL转换与绘制范围一致，原差40px消失 | 聚合与同源已修；VOL零下界及模板minValue/maxValue仍未执行，M05 |
| N04 figure上下文/几何 | 实际value=100、candles=200；各调用点已补参数；相对top/bottom绘制到paneTop+5 | 部分修复；BAR的x按中心解释、y/height未消费，LINE attrs未实现，M03 |
| N05 格式链 | 小数折叠0.0{4}1234、实例精度6输出12.345678均通过 | 所检原复现已修复 |
| N06 控点pane边界 | pane外触点不再命中控点 | 绘制未进入clip/translate，改成局部坐标后反而偏移到主图，M04 |
| N07 fixedZLevel编辑 | 单对象端点命中pointIndex=0，未再变成整体拖动 | 原单对象编辑已修；跨层级组合仍违背声明z顺序，M06 |

早期已修的分页/实时共存、自动续拉、StylesPatch、pane管理、Crosshair、动画、主体覆盖物裁剪、arc命中及绘制方向仍通过本轮基准。不会将已通过的原样例重复登记为未修。

## 4. 必须修复的剩余阻断

### M01 · P1：旧INIT的失败清理没有会话归属，会退订较新的有效会话

**职责与问题：** Host收到reset的Rejected后，对全局dataEpoch执行decrementAndGet，并调用无归属参数的store.unsubscribeData。异步结果没有携带并复核它所属的epoch/key；此时Store可能已经切到另一个品种。减1也不是恢复该失败操作自己的身份，而是在改写当前全局代次。

**公开回调复现：** A已就绪；B的非法INIT已提交但尚未处理结果，立即切C，C同步返回合法INIT。调度完成后getSymbol和marketKey均为C，初始close为300，但B的失败延续调用退订当前C。退订记录为`[A,B,C]`，随后C的合法ts=6000、close=301推送被丢弃，列表仍为`[300]`。

该用例未修改生产字段或锁，仅固定“较早异步失败晚于新会话受理”的正常交错。单个失败B的原复现已修，本项是该反馈逻辑的跨会话组合问题。

**证据：** [LKanvasChart.kt:476](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:476)、[失败分支:486](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:486)、[数据操作门禁:497](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:497)。

**最小处理方向：** INIT捕获自身身份，处理失败及其他后续副作用前确认仍属于当前会话；只清理其所属订阅。代次保持单调，用失效机制代替减1回滚，保留首推与合法重试行为。

**不修复：** 前一个品种的一次失败会停止当前正常品种实时更新。会话错误路径仍未形成完整生命周期门禁。

### M02 · P1：所谓轴分组仍在实例循环外，多个有效绑定继续共用第一条轴

**职责与问题：** drawExtraPane现在筛掉未使用轴，并改用第一条绑定轴自己的范围；但firstOrNull仍在for(inst in insts)之前，y也只创建一次。所有实例继续使用同一轴。固定VOL/MACD入口仍给全部实例提供普通线性y，没有进入相同的绑定轴选择。

**运行复现：** 同pane创建两个RSI实例，分别绑定reverse轴和normal轴。第一条轴为reverse，第二个normal实例某值的正确绝对y=541，实际renderer提供y=501。另在内置vol绑定reverse RSI，正确轴转换为500，renderer实际约540.665691。

**证据：** [PaneRenderers.kt:339](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:339)、[实例循环:358](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:358)、[固定pane:287](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:287)。

**最小处理方向：** 在每个实例或实际轴分组内解析yAxisId及其范围、spec、reverse，再传入绘制；固定和动态pane使用同一选择入口。过滤第一项并不等于按实例分组。

**不修复：** 第一个轴的测试可以通过，第二个轴仍画错；上游轴补丁还要在多条近似路径中维护。

### M03 · P1：BAR矩形字段仍不具备上游含义，LINE attrs被登记差异后仍未实现

**职责与问题：** 新增x/width读取后，x仍被当成中心再减半宽；上游attrs.x是矩形左边界。y/height仍未进入最终矩形。零pane偏移下输入`{x:20,y:30,width:7,height:9}`，当前真实绘制参数为`[16.5,0,7,100]`，预期为`[20,30,7,9]`。width和本地top/bottom平移已生效，不再称为attrs完全不执行。

LINE attrs仍没有绘制消费者。本轮structure-map将其登记为平台差异，但批量drawLines是本地优化选择，不能据此把上游几何覆盖契约改成不适用。登记满足了追溯需要，没有自动满足本次结构/逻辑验收，也没有用户变更原标准的依据。

**证据：** [PaneRenderers.kt:716](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:716)、[矩形输出:733](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:733)、[上游IndicatorView.ts:142](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:142)、[差异登记:30](/Users/pauldeman/Documents/ai_workspace/KKLineChart/docs/alignment/structure-map.md:30)。

**最小处理方向：** 按类型合并上游完整attrs；只给未提供的字段生成默认几何。保留无attrs的批量快路径，提供attrs时执行几何覆盖，再送入现有绘制入口，不需要重写全部renderer。

**不修复：** 上游模板补丁仍需要重新解释坐标字段或舍弃行为，不能称为可直接局部对应的移植。

### M04 · P1：控点实际仍在clip/translate之外，减去paneTop后产生绘制回归

**职责与问题：** OverlayRenderer的clipRect和translate在第140、141行已经闭合；控制点在第144行之后执行，未移动到块内。代码只把绝对坐标改为`it.y-paneTop`，因此局部坐标被直接画到整图。

**运行复现：** RSI pane top=481，合法value=50的屏幕y约523.9328。追踪真实DrawContext的translate和Canvas保存/恢复调用，两个控制圆心的有效屏幕y均约42.9328，裁剪栈深度均0；命中则仍在正确的绝对y约523.9328返回pointIndex=0。

这是相较上一版可定位的坐标回归。控点pane外命中已被修复，但注释“移入pane裁剪块内”与大括号控制范围不符。

**证据：** [OverlayRenderer.kt:99](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:99)、[闭合与控点:140](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:140)、[命中映射:259](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:259)。

**最小处理方向：** 真正将控点放入相应裁剪/平移块，或统一在绝对坐标下绘制并使用相同裁剪；只在一个明确边界变换一次，并同时验证可见位置和命中位置。

**不修复：** 正常副图控点画到主图，用户看到的位置与可拖动位置分离；这不是字体或Float精度误差。

## 5. 应修复的领域契约余项

### M05 · P2：范围入口已统一，但模板minValue/maxValue仍没有被执行

**问题与复现：** paneVisibleRange聚合全部实例后仍只扫描数据及部分0基线，不消费模板minValue/maxValue。内置VOL明确声明minValue=0，恒定成交量100时范围仍为`(99,101)`，不是包含0的范围。注册minValue=-50、maxValue=500的合法BAR指标，数据为100，实际范围却为`(0,100)`。

转换与绘制现在共享这一结果，原来的40px差异已消失；但共享一个漏掉约束的结果仍不符合上游。上游YAxis在计算默认范围时明确合并指标声明的上下界。

**证据：** [ChartState.kt:442](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:442)、[VOL分支:467](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:467)、[Volume.kt](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/extension/indicator/Volume.kt)、[上游YAxis.ts:163](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/component/YAxis.ts:163)、[范围合并:224](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/component/YAxis.ts:224)。

**最小处理方向：** 在现有范围入口中合并实际绑定、可见指标的上下界约束，供绘制和转换共同使用；无需重新拆出独立VOL转换公式。

**不修复：** 自定义轴范围声明无效，VOL的0基线落在显示范围之外；参数契约仍被简化。

### M06 · P2：fixedZLevel控点恢复编辑后，仍全局优先于遮住它的高层对象

**问题与复现：** 控点命中是单独的全局第一遍，按selectedId筛选后直接返回；高层未选中对象的主体要到后续遍历才检查。创建z=0的固定层级segment并选中，再叠加z=10、线宽60的水平线覆盖其端点。真实绘制顺序为控点先画、高层宽线后覆盖；同位置命中仍返回低层segment的pointIndex=0。

上游在绘制顺序中注册figure，Eventful按子对象逆序分发事件；fixedZLevel保留声明层级。恢复控点可编辑不能顺便给固定层级对象全局最高命中优先级。

**证据：** [OverlayRenderer.kt:248](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:248)、[后续主体遍历:271](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:271)、[上游Eventful.ts:42](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/common/Eventful.ts:42)、[OverlayView.ts:481](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/view/OverlayView.ts:481)、[fixedZLevel契约:153](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/component/Overlay.ts:153)。

**最小处理方向：** 以一致的实际层级顺序处理主体和控点；选中浮起与固定层级分别按原契约处理，保留单对象端点编辑的修复。

**不修复：** 被高层遮住的低层控点仍能抢占交互，声明的固定z顺序只在绘制侧成立。

## 6. 临时验证与证据边界

外部类`audit.SixthAuditTest`通过临时init script加载，最终55项均执行通过。47项主断言确认基准或修复；8项主断言确认剩余差异。没有把这些断言写成仓库中保护错误行为的正式测试。

```sh
./gradlew -I /tmp/lkanvas-audit-round6-20261031/audit.init.gradle \
  :lkanvas-compose:testDebugUnitTest --tests 'audit.SixthAuditTest' \
  --no-build-cache --console=plain
```

| 方法 | 主类型 | 结果 |
|---|---|---|
| `fixedAnimationHasIntermediateState` | 基准/修复 | 动画有中间状态 |
| `fixedAxisConstructionAndTemplateReadback` | 基准/修复 | 轴构造和模板回读reverse一致 |
| `fixedBasicCrosshairAndClear` | 基准/修复 | 十字线x推导与null清除 |
| `fixedBoundAxisOwnRangeForFirstInstance` | 基准/修复 | 首个绑定轴使用自身范围，原RSI样例对齐 |
| `fixedBuiltinDragFlagReadback` | 基准/修复 | 内置拖动禁用可回读 |
| `fixedBuiltinMacdRangeIncludesStackedInstances` | 基准/修复 | 叠加1000进入内置pane范围 |
| `fixedBuiltinPaneRelayoutAfterCreation` | 基准/修复 | 新增内置pane获得非零高度 |
| `fixedBuiltinPanesVisitEveryVisibleInstance` | 基准/修复 | VOL/MACD真实调用两实例draw钩子 |
| `fixedBuiltinVolumeConversionUsesDrawRange` | 基准/修复 | 内置VOL转换/绘制同源；不证明0下界已消费 |
| `fixedControlPointHitBoundary` | 基准/修复 | pane外控点触点被拒；绘制另见M04 |
| `fixedDynamicBarRangeReachesRenderer` | 基准/修复 | 动态VOL恒值范围实际进入绘制 |
| `fixedDynamicCrosshairYAndPane` | 基准/修复 | 动态pane十字线位置与归属保持 |
| `fixedDynamicDragFlagConsumption` | 基准/修复 | 动态pane禁拖后高度保持80 |
| `fixedDynamicHeightSetter` | 基准/修复 | 动态高度123设置与回读生效 |
| `fixedFigureCallbackValueAndCandleContext` | 基准/修复 | 真实value=100、行情200根进入回调 |
| `fixedFixedZLevelControlPointEditing` | 基准/修复 | 固定层级单对象端点可编辑 |
| `fixedImmediateRealtimeAfterInit` | 基准/修复 | INIT立即首推保留 |
| `fixedImmediateRealtimeWithExternalScope` | 基准/修复 | 公开externalScope下合法首推保留 |
| `fixedInitialBoundaryLoadsWithoutGesture` | 基准/修复 | 初始不足时自动续拉 |
| `fixedLocalBarAttrsAreTranslated` | 基准/修复 | 本地top/bottom在绘制时加paneTop |
| `fixedNegativeArcSweepPaintAndHit` | 基准/修复 | 180→90传入270°绘制跨度 |
| `fixedOldInitBlockedAcrossAToB` | 基准/修复 | 原锁等待旧INIT被拒且只订阅B |
| `fixedOldRealtimeBlockedAcrossAToB` | 基准/修复 | 原A→B锁等待旧推送被拒 |
| `fixedOverlayArcUsesPatchedCore` | 基准/修复 | 覆盖物实际命中使用修复后的core |
| `fixedOverlayPaneInput` | 基准/修复 | 副pane value=50触点反算正确 |
| `fixedPaginationKeepsRealtime` | 基准/修复 | 分页期间实时订阅仍生效 |
| `fixedPainterVisitsLowBeforeHigh` | 基准/修复 | 实际绘制低z→高z |
| `fixedPaneQuerySizeAndMove` | 基准/修复 | 动态pane查询、设高、迁移 |
| `fixedPartialStylePreservesDarkAndRestoresDefault` | 基准/修复 | StylesPatch保留其他字段并可恢复默认 |
| `fixedPriceLineAnchorGeometry` | 基准/修复 | priceLine从锚点到右界 |
| `fixedPriceLineDecimalFold` | 基准/修复 | 格式链执行小数折叠 |
| `fixedPriceLineInstancePrecisionOverride` | 基准/修复 | 实例精度覆盖生效 |
| `fixedPriceLinePrecisionAndThousandsFormat` | 基准/修复 | 精度5和自定义千分位共同生效 |
| `fixedPriceLineSymbolPrecision` | 基准/修复 | 品种精度5实际消费 |
| `fixedRealtimeAcceptedBeforeABA` | 基准/修复 | 真正锁等待经过ABA后旧推送被拒 |
| `fixedRegisteredSpecDoesNotChangeMainAxis` | 基准/修复 | 轴spec注册生效且不改主轴 |
| `fixedRejectedInitFeedCannotWriteOldSession` | 基准/修复 | 单个非法INIT后其推送不再写旧会话 |
| `fixedSecondaryAxisRelativeConvention` | 基准/修复 | 副轴相对坐标与逆变换正确 |
| `fixedSelectedFixedZOverlayBodyHit` | 基准/修复 | 固定层级对象选中后线身仍命中 |
| `fixedSimpleAbsoluteMeaning` | 基准/修复 | 简单absolute只改变y参考系 |
| `fixedSimpleBarAttrsReachDrawPipeline` | 基准/修复 | BAR真实矩形绘制调用attrs |
| `fixedSingletonBoundLogReversePane` | 基准/修复 | 单实例log/reverse绘制与转换一致 |
| `fixedStackedLinesConversion` | 基准/修复 | 纯线叠加范围与转换一致 |
| `fixedSubPaneOverlayBodyBoundary` | 基准/修复 | 副图越界主体不命中主图区 |
| `fixedUnusedSubAxisDoesNotAffectDefault` | 基准/修复 | 未绑定轴不再改变默认指标投影 |
| `localPaneOverlayTemplateBoundsAreNowRelative` | 基准/修复 | 模板收到局部高度与像素点 |
| `realPatchBeforeAndAfter` | 基准/修复 | 历史arc端点false、当前true |
| `builtinPaneStillIgnoresBoundReverseAxis` | 差异复现 | 内置pane仍忽略绑定reverse轴，M02 |
| `customIndicatorMinAndMaxDoNotConstrainRange` | 差异复现 | 声明[-50,500]但得到[0,100]，M05 |
| `fixedLayerControlPointTakesHitAboveCoveringHigherLayer` | 差异复现 | 低层固定控点被覆盖后仍抢先命中，M06 |
| `lateRejectedInitUnsubscribesNewerValidSession` | 差异复现 | 旧B失败清理退订较新C，C推送被丢，M01 |
| `subPaneControlPointsAreDrawnAtUntranslatedLocalY` | 差异复现 | 控点画在局部y，裁剪深度0，M04 |
| `twoBoundAxesStillUseFirstProjectionForBothInstances` | 差异复现 | 两个有效绑定共用第一条轴，M02 |
| `upstreamBarRectangleAttrsAreIgnored` | 差异复现 | 矩形x/y/height仍非上游含义，M03 |
| `volumeRangeStillOmitsDeclaredZeroMinimum` | 差异复现 | VOL声明0下界但得到99..101，M05 |

外部源码、最终JUnit XML、基线计数、oracle、快照核验和执行日志保存在`/tmp/lkanvas-audit-round6-20261031/`。临时目录不是长期制品；本报告保留关键输入、调度条件和结果，修复后应将对应期望行为保存为正式回归测试。

- M01使用公开配置与DataLoader回调，在StandardTestDispatcher下固定异步结果交错，未反射写入生产字段。早期ABA基准仅以现有Store锁固定竞争，修复仍保持。
- M02读取真实renderer给draw钩子的yOf，读取后中止绘制；不是手写转换往返，也不是截图。
- M03记录真实figure入口的drawRect参数；LINE无消费者由全仓调用点及上游路径静态核对，未声称已做LINE像素差分。
- M04记录真实绘制调用经过的DrawContext变换及保存/恢复栈，得到有效坐标和裁剪深度；没有生成真机像素截图。
- M06同时记录控点与高层60px宽线的绘制先后，以及实际hitTest结果。未执行完整触摸E2E。
- 历史arc before文件再次与`git show b63d173^:.../Figures.kt`核对，仅替换测试包名；同样端点输入旧false、当前true。

## 7. 当前目标收敛情况

### 7.1 已经稳定积累的成果

| 领域 | 从第一轮到本轮的变化 | 当前判断 |
|---|---|---|
| 指标公式与文件落点 | 四个合并文件/Scratch为主，恢复为27模板的规范calc及同文件增量路径 | 已形成可保留的局部同步基础；默认样本与双路径基准保持 |
| 注册与公共行为 | 全局表断链、默认实例和CRUD差异，逐步接通注册、创建、默认与批量契约 | 已修基准在后续轮次持续通过 |
| 覆盖物模板 | 按名称集中分支，转为模板生成并进入实际绘制/公共命中 | 主体职责更接近原结构；控点和层级仍未完成 |
| pane/figure | 固定三pane及专用绘制，转为动态pane、全实例通用figure入口 | 结构改善真实；每实例轴与完整几何契约仍阻断 |
| 数据/样式/视口 | 分页缺口、样式存在语义和动画空实现等逐步修复 | 正常基准稳定；失败结果的会话归属仍未闭合 |
| 升级证据 | 文档演练为主，增加真实arc源码补丁及可重建before/after | 局部证明成立，尚不能外推所有领域 |

**收敛判断：净进展成立，当前进入组合契约和错误路径收口阶段；还不能称为只剩边角或单纯补验收证据。** 第五轮的4项P1到本轮仍为4项P1，只是具体复现范围变化。新问题中包括修复引入的回归，故不是严格单调改善。

六轮的16→14→11→9→7→6只是各次报告的发现条目数。检查粒度和组合深度不同，不能据此声称某个完成百分比。本轮真正的收敛证据是已有基准持续通过、专用分支被删除、范围与回调成为实际共享消费者；真正未收敛的证据是控制流仍在实例循环外选轴、旧异步结果仍能影响新会话。

### 7.2 剩余工作的合理规划

在冻结现有目标、不追加功能的前提下，**建议保留2–3轮余量：1轮集中根因修复，1轮独立验收，再留1轮必要返修。** 这是工程估算，不是通过承诺。

此前“再3–4轮”的估计有一个前提：消除共同根因。这个前提在会话失败归属和每实例轴选择上仍未完全满足，不能按已经过了两轮机械扣减。下一轮是否满足以下条件，比剩余条目数量更有预测价值：

1. 异步失败只能清理发起它的会话，代次不回退，成功/失败/首推/重试在同一规则下成立。
2. 轴选择与投影位于实际实例或轴分组内，固定与动态pane都复用它。
3. 默认几何与attrs覆盖保持上游含义，控点的裁剪、平移和层级顺序与命中一致。
4. 范围入口真正执行模板约束，并由真实绘制消费者和多实例组合验证。
5. 根因修复后做独立复审和必要真机触摸/绘制验证，复验已有源码补丁演练；条件允许时再抽样相关领域，保留之前已通过的基准。

这仍属于有限领域入口的修正，没有证据要求全库重写。若继续只修单个样例，而让“第一条轴”“全局清理”“注释已完成”保留在实际代码中，轮次就无法可靠预计。

## 8. 台账与未检查范围

check.py解析302行：已声明237、行为通过48、已接线2、不适用15，结构可对应仍为0。解析器已支持N编号；但台账本身只有N01/N02/N03/N04/N05/N07，**缺N06行**，不是解析器跳过了该行。结构引用检查通过与27指标映射齐全均已核对。

历史0.9.0“全项收口”表述已纠偏，这属于真实改进；当前关闭判断仍应以运行证据为准。LINE attrs的登记明确了差异，但不能替代原标准下的行为保持。

本轮未重做真机截图、完整触摸E2E、TalkBack、Macrobenchmark、R8、Maven发布/consumer和图片编码集成，未重新认证既有性能报告。未逐行证明27指标的全部参数边界，也未穷举全部样式、图形和异常输入。

**本报告未实施修复。当前最高风险是旧失败动作停止较新会话实时更新，其次是多轴和控点绘制/命中不一致。总体继续接近目标，完整验收仍未通过。**
