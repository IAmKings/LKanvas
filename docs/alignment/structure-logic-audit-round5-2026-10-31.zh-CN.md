# Compose 工程与 KLineChart 结构、逻辑一致性第五轮审计

- 日期：2026-10-31（Asia/Shanghai）。
- 审计对象：`f11f2e23a246374602a5423aa623d6a28d1a02c3`，版本 `0.9.0`。
- 比较起点：第四轮对象 `1f1c3476159686e9dfd87f927413d25039b7ae32`（0.8.0）。
- 固定上游：KLineChart v10.1.0，`d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df`。
- 验收标准不变：职责结构、领域数据和参数契约、分支/调用顺序、实际消费者、共同输入行为及有限源码补丁的可对应性。跨 TS/Kotlin 不要求标点或语言关键字相同，不按文本相似率验收。
- 范围：单图表异步加载、分页/实时、切会话、多 pane/轴/指标叠加、figure 与覆盖物绘制/命中/编辑、格式与 Layout、对齐工具。扩展验证包括外部行情校验失败与已公开的 externalScope。
- 修改边界：开始时工作区干净；本次只新增本报告，没有修改实现、仓库测试、配置、上游快照和前四轮报告。

## 1. 验收结论与收敛判断

**本轮继续收敛，而且有实际的结构收敛；仍未达到完整结构与逻辑对齐验收，不能确认 Q01–Q09 全项关闭。**

与第四轮相比，新增 helper 没有消费者的情况已明显改善：drawExtraPane 确实调用 paneVisibleRange，BAR 确实执行 attrs，固定 VOL/MACD 的专用绘制函数被删除，改为遍历全部可见实例。PaneRenderers 本轮 diff 为新增108行、删除145行，净减少37行；这只用于佐证分支被合并，不作为质量评分。

第四轮的原复现已经通过本轮重新断言：旧 ABA 推送不再污染第二次 A；INIT 后立即首推正常保留；锁内拒绝的旧 INIT 不再订阅；单实例副轴坐标和 log/reverse 投影一致；动态 BAR 值域、覆盖物输入反算、Layout 消费、arc 方向和 fixedZLevel 线身命中也通过。

剩余发现更集中，但还不是纯粹的零散边角：**实际提交失败没有反馈到加载/订阅状态，多轴仍按 pane 选第一条轴，内置 pane 的共享值域仍保留首实例分支，figure 的上下文与几何契约也只接通了一部分。** 这些仍会影响后续上游补丁能否自然覆盖所有执行入口。

本报告将12个差异复现归为 **7项发现：4项P1、3项P2**。不把第四轮已通过的原样例重新登记为未修复，也不把新发现一概解释成本轮引入的回归。

| 维度 | 第五轮判断 | 主要证据 |
|---|---|---|
| S1 来源与符号可追溯 | 部分通过 | 官方快照155/155一致，27指标映射集合完整；映射仍不能证明全部调用链 |
| S2 职责结构 | 继续改善，未整体通过 | 删除固定副图专用绘制、共享函数进入实际消费者；轴/值域仍有第一项近似规则 |
| S3 算法与控制流 | 局部通过 | calc/双路径与arc演练保持；figure attrs及上下文仍未保留上游同等语义 |
| L1 数据状态机 | 不通过整体验收 | 原ABA/首推/过期订阅用例已修；实际INIT拒绝后仍发生会话混写，N01 |
| L2 参数与公共契约 | 部分通过 | 副轴相对坐标、Layout、品种精度与千分位改善；格式与控点编辑仍有余项 |
| L3 注册→执行→绘制/命中/编辑 | 部分通过 | BAR attrs和全实例绘制真实接通；绑定轴选择、图形上下文及控点分支仍不一致 |
| V1 验证与门禁 | 部分通过 | 370项既有测试通过；48项外部探针仍确认差异；台账校验不等于行为验收 |
| U1 真实源码补丁局部同步 | 原arc演练限定通过，范围扩大 | 历史before/after、实际命中入口和负差角度绘制均通过；未外推所有领域 |

## 2. 核验基线与执行结果

| 检查 | 结果 | 边界 |
|---|---|---|
| 官方固定提交归档 vs 本地 third_party/src | 155/155逐字节一致，修改/缺失/额外均为0 | 固定上游对照可靠 |
| 上游TS oracle重生成 | 27/27 JSON与仓库fixture一致 | 默认样本可再生，非全部参数证明 |
| core既有测试 | 222，失败/错误/跳过均0 | 当前core断言 |
| Compose debug既有单元测试 | 148，失败/错误/跳过均0 | 与core合计370 |
| 第五轮临时测试 | 48，失败/错误/跳过均0 | 36项基准/修复确认；12项成功复现剩余差异 |
| check.py | 通过，解析296行，Q01–Q09全部纳入 | 文件存在、枚举和覆盖检查 |
| 结构映射 | 指标集合27项，缺失/额外均0；结构引用检查无错误 | 本轮单独人工集合核对 |

既有测试在临时测试注入之前实际执行并保存计数，禁用构建缓存：

```sh
./gradlew :lkanvas-core:cleanTest :lkanvas-compose:cleanTestDebugUnitTest \
  :lkanvas-core:test :lkanvas-compose:testDebugUnitTest \
  --no-build-cache --console=plain
python3 -B tools/alignment/check.py
```

本次在线核对的 main 仍为 `66dd45b31e93b57a4c75a3e0b98bc2a8393df36e`，相对固定基线ahead 3，差异为2个README与arc.ts；没有发现第四轮之后的新源码差异。参见[本次固定上游比较](https://github.com/klinecharts/KLineChart/compare/d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df...66dd45b31e93b57a4c75a3e0b98bc2a8393df36e)。版本来源对齐与移植实现的结构/逻辑对齐分别判断。

## 3. 第四轮 Q01–Q09 逐项复核

| 原编号 | 本轮已确认的修复 | 仍需处理的范围 |
|---|---|---|
| Q01 会话身份 | 真正锁等待的ABA、普通及externalScope下首推、锁内拒绝后的订阅副作用均通过 | INIT实际被ChartDataStore拒绝后，Host仍宣告受理并建立新订阅，N01 |
| Q02 副轴坐标/绘制 | 相对坐标正确；absolute只加一次top；单实例log+reverse绘制与转换一致；轴模板默认字段可回读 | 全pane取第一条额外轴，不按实例绑定；轴范围与pane范围仍不同，N02 |
| Q03 共享值域 | 动态pane的恒值VOL样例从转换/绘制差40px变为一致，共享函数已被renderer调用 | 内置vol/macd分支仍只取第一实例，vol转换另留0..max分支，N03 |
| Q04 覆盖物pane | value=50反算正确；CREATE初始化实例pane；图形主体已裁剪，主体pane外命中被拒 | 控制点绘制与命中仍绕过pane边界，N06；未完成真机触摸E2E |
| Q05 figure管线 | BAR attrs确实执行；VOL/MACD确实遍历全部可见实例；专用drawVolumeBars/drawMacdBars删除 | attrs字段/坐标/上下文不完整，部分调用未传candles，N04 |
| Q06 priceLine格式 | 品种精度5得到123.45678；千分位可按配置生成12_345.67890 | 小数折叠未执行，指标实例精度覆盖未消费，N05 |
| Q07 动态Layout | 禁用拖动后高度保持80；动态高度123设置及回读正确，实际共用ChartState状态 | 原复现已修；旧私有空列表声明仍在但已无消费者，不再按行为故障登记 |
| Q08 arc跨度 | 实际drawFigure对180°→90°传入270°，与命中一致；原端点容差与圆心不命中保持 | 原绘制/命中复现已修；不据此声称所有辅助几何与角度边界均已穷举 |
| Q09 fixedZLevel | 选中后的线身可命中并整体拖动 | 控制点仍被固定层级标志排除，单点编辑变为整体拖动，N07 |

早期轮次已修的StylesPatch、分页/订阅代次分离、自动续拉、pane查询/迁移、Crosshair基本语义与动画中间状态仍通过本轮基准测试。

本轮不会仅因expectedSessionId从引擎请求中移除而判定失败。新的dataEpoch方案已修复所检原交错；N01依据的是实际校验拒绝后的错误结果，不是要求必须保留某一种字段或实现形式。

## 4. 必须修复的剩余问题

### N01 · P1：INIT实际被拒绝，加载层仍建立新订阅，后续数据写进旧会话

**职责与问题：** ChartDataStore负责数据合法性与真正的会话切换；Store/Host负责加载和订阅生命周期。Host.applyInit发起异步reset后立即返回true，且不读取reset的ChangeResult；Store据此订阅。校验失败时数据内核保留旧会话，门面的dataEpoch却已进入新一轮，后续applyUpdate不使用传入key，直接更新当前内核。

**可复现输入与结果：**

1. A正常INIT，ts=1000、close=100。
2. 切B，B的INIT返回非法OHLC：open=200、high=190、low=210、close=200。ChartDataStore拒绝该批次，数据marketKey仍为A。
3. B订阅已经建立，随后提交合法ts=3000、close=999。
4. 门面getSymbol=B，数据marketKey=A，close列表成为`[100,999]`。

非法INIT本身被校验器正确拦截；错误在于拒绝结果没有约束后续生命周期，不能以“输入非法”解释为允许新订阅污染旧会话。此用例走公开DataLoader回调，没有修改生产字段或注入会话状态。

**证据：** [LKanvasChart.kt:475](../../lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:475)、[LKanvasChart.kt:504](../../lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:504)、[Store.kt:173](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/store/Store.kt:173)、[Store.kt:223](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/store/Store.kt:223)、[ChartDataStore.kt:177](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/store/ChartDataStore.kt:177)。

**最小处理方向：** 把真实提交结果反馈到INIT/订阅及会话状态；失败时不能对旧内核接纳新会话推送。复用现有ChangeResult与串行写入入口，明确失败、重试及缓冲首推的顺序，保留本轮已经正确的ABA与首推行为。

**不修复：** 数据源一次非法初始批次就能造成品种与行情不一致。成功路径的代次门禁仍不足以形成完整会话契约。

### N02 · P1：副图按pane选第一条额外轴，未按指标实际绑定选择投影和值域

**职责与问题：** 每个实例应按自己的yAxisId选择轴与范围。drawExtraPane使用extraYAxisMeta中第一个paneId匹配项，再把同一个y传给全部实例；范围来自整个pane，而convert仍用extraAxisRange中的该轴绑定子集。内置vol/macd的通用入口则仍只用普通线性y，不读取绑定轴。

**运行复现：**

- RSI实例仍绑定默认main，在其pane注册一条未绑定任何实例的reverse额外轴。绘制y由应有541变为501；仅增加未使用的轴就翻转了默认指标。
- 同pane中RSI绑定log+reverse轴，MA绑定默认轴。RSI同一点的轴转换绝对y≈539.856431，renderer实际提供≈534.334726；只有一个实例时两者已经相等，叠加后重新不同。

**证据：** [PaneRenderers.kt:341](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:341)、[PaneRenderers.kt:355](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:355)、[PaneRenderers.kt:287](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:287)、[ChartState.kt:382](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:382)、[ChartState.kt:878](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:878)。

**最小处理方向：** 按实例绑定的轴分组，绘制、转换和标签复用该轴上下文及可见绑定实例范围；未使用的轴不改变其他实例投影。不要用pane内第一条轴代替每实例的轴选择。

**不修复：** 多轴、多实例组合仍不能对应上游的轴归属规则，新增或排序轴会改变无关图形。

### N03 · P1：renderer已共用值域入口，但内置pane入口仍漏掉叠加实例并与转换分叉

**职责与问题：** paneVisibleRange的动态分支聚合全部可见实例，但vol/macd分支仍调用volumePaneResult/macdPaneResult取首个结果。vol没有保证0基线，paneValueToY/paneValueAt又保留单独的0..max规则。绘制循环已经遍历全实例，范围却没有一起变成全实例。

**运行复现：**

- 内置vol各可见列及成交量恒为100：renderer通过paneVisibleRange得到`(99,101)`，yOf(100)=520；转换仍按`(0,100)`得到绝对y=480，差40px。用相同top/bottom隔离了范围公式，未把差异归于分隔线像素。
- macd pane首个实例为MA，叠加一个结果为1000的合法注册指标。后者确实被绘制，但范围仍为`(0,122.557...)`，其yOf(1000)≈-92.757，pane top=480，落在裁剪区外。

**证据：** [ChartState.kt:463](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:463)、[ChartState.kt:492](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:492)、[PaneRenderers.kt:284](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:284)。

**最小处理方向：** 全部pane基于实际可见实例与其figure基线/范围约束计算，转换读取同一个结果。删除内置pane的首实例近似与单独转换公式，保留本轮已接通的全实例绘制。

**不修复：** 指标已进入绘制循环仍可能被错误范围裁掉；“调用同一个函数”尚不能证明范围规则已经统一。

### N04 · P1：figure回调已执行，但几何与上下文仍不等价，跨pane会产生错误绘制

**职责与问题：** 上游IndicatorView按图形合并attrs几何，并提供真实数据、坐标、轴和bounding上下文。本地BAR新增消费只处理top/bottom；上游bar的x/y/width/height不进入最终几何，LINE也未消费attrs。attrs的四参回调被传入NaN和空candles；main、主图额外轴、动态pane调用drawInstanceFigures时又未传candles，使styles回调也收到空列表。

**运行复现：**

- 动态pane top=481，attrs按本地已声明的pane相对协议返回top=5/bottom=15。实际矩形绘制top仍为5，没有平移到486，完整落在该pane裁剪区外。
- 输入上游矩形attrs `{x:20,y:30,width:7,height:9}`，实际绘制参数为`[-3,0,6,100]`，没有覆盖默认矩形。
- 图形列实际值为100、行情200根，真实动态pane入口调用attrs时却收到value=NaN、candles.size=0；styles的candles.size也为0。内置VOL的新增配色回调对缺失candle返回中性色，因此该上下文缺失会影响正常默认新建pane路径。

**证据：** [PaneRenderers.kt:120](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:120)、[PaneRenderers.kt:369](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:369)、[PaneRenderers.kt:656](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:656)、[PaneRenderers.kt:707](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:707)、[PaneRenderers.kt:776](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:776)、[上游IndicatorView.ts:142](../../third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:142)、[Volume.kt:39](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/extension/indicator/Volume.kt:39)。

**最小处理方向：** 统一传入实际行值、行情及pane/轴/几何上下文；按图形类型把attrs合并为最终几何，在确定边界只平移一次。补齐所有入口的实际参数，保留已经统一的实例循环，不能用空参数适配已声明的回调。

**不修复：** 上游figure补丁虽能找到本地函数，直接移植后仍可能不生效、用错数据或完全被裁掉，维护成本尚未满足原目标。

## 5. 应修复的契约余项

### N05 · P2：priceLine有品种精度和千分位，但小数折叠及实例精度覆盖仍未消费

**问题与复现：** formatPriceText末尾直接return，没有使用formatConfig.decimalFoldThreshold。品种精度8、threshold=3、value=0.00001234，当前输出`0.00001234`，现有公共格式函数和上游契约对应`0.0{4}1234`。另将指标实例公开的precisionOverride设为6后，12.345678仍输出12.35，因为renderer只读definition.precision。

**证据：** [OverlayRenderer.kt:154](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:154)、[Format.kt:67](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/common/utils/Format.kt:67)、[IndicatorEngine.kt:217](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/indicator/IndicatorEngine.kt:217)、[上游priceLine.ts:24](../../third_party/klinechart-v10.1.0/src/extension/overlay/priceLine.ts:24)。

**最小处理方向：** 以实例有效精度执行precision→thousands→decimalFold，复用已有格式函数，补上最后一步消费。

**不修复：** 同一组配置在不同文本入口仍得到不同结果。本轮品种精度与千分位的修复成立，不能继续表述为全部格式都未执行。

### N06 · P2：覆盖物主体有pane边界，选中控制点仍跨pane绘制和命中

**问题与复现：** 控制点绘制位于新clipRect块之外，命中第一段也在hitBody的pane边界检查之前。RSI覆盖物控制点位于pane相对y=-20，选中后在主图区仍命中pointIndex=0；真实绘制调用记录两个控制圆心y=461，所属pane top=481。

**证据：** [OverlayRenderer.kt:99](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:99)、[OverlayRenderer.kt:142](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:142)、[OverlayRenderer.kt:241](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:241)。上游[OverlayView.ts:494](../../third_party/klinechart-v10.1.0/src/view/OverlayView.ts:494)与[默认控制点:577](../../third_party/klinechart-v10.1.0/src/view/OverlayView.ts:577)在所属pane/widget绘制上下文中执行，Compose单画布需显式保留这个边界。

**最小处理方向：** 控制点与主体使用相同pane裁剪和输入归属，边缘可触达容差在该边界内明确处理。

**不修复：** 未选中时越界主体已被阻断，选中后仍会在其他pane显示和抢占编辑，生命周期的边界语义不完整。

### N07 · P2：fixedZLevel线身恢复命中，控制点仍被禁用，单点拖动变成整体拖动

**问题与复现：** hitTest控制点分支仍把inst.fixedZLevel作为continue条件。选中未锁定segment，普通状态点第一个端点得到pointIndex=0；只将fixedZLevel改为true后，同一点变为pointIndex=null、wholeDrag=true。实际手势因此进入WHOLE_DRAG而非POINT_DRAG。

**证据：** [OverlayRenderer.kt:243](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:243)、[OverlayRenderer.kt:265](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:265)、[ChartGestures.kt:128](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/gesture/ChartGestures.kt:128)、[上游Overlay.ts:153](../../third_party/klinechart-v10.1.0/src/component/Overlay.ts:153)。上游该字段只约束层级浮起，不禁用控制点编辑。

**最小处理方向：** 固定层级对象在声明的z顺序中保留控制点候选，把是否浮起与是否可以编辑分开判断。

**不修复：** 对象不再完全不可命中，但端点编辑仍改变成另一种操作，Q09只能关闭原线身样例。

## 6. 临时测试与证据边界

测试类为`audit.FifthAuditTest`，外部源通过临时init script注入；没有写入仓库测试，也没有把错误行为变为正式回归预期。

```sh
./gradlew -I /tmp/lkanvas-audit-round5-20261031/audit.init.gradle \
  :lkanvas-compose:testDebugUnitTest --tests 'audit.FifthAuditTest' \
  --no-build-cache --console=plain
```

按主断言分为36项基准/修复确认、12项差异复现。后者通过表示成功确认当前差异：

| 方法 | 主类型 | 确认结果 |
|---|---|---|
| `fixedAnimationHasIntermediateState` | 基准/修复 | 动画有中间状态 |
| `fixedAxisConstructionAndTemplateReadback` | 基准/修复 | 轴构造和模板回读reverse一致 |
| `fixedBasicCrosshairAndClear` | 基准/修复 | 十字线x推导与null清除 |
| `fixedBuiltinDragFlagReadback` | 基准/修复 | 内置拖动禁用可回读 |
| `fixedBuiltinPaneRelayoutAfterCreation` | 基准/修复 | 新增内置pane获得非零高度 |
| `fixedBuiltinPanesVisitEveryVisibleInstance` | 基准/修复 | VOL/MACD真实调用两实例draw钩子 |
| `fixedDynamicBarRangeReachesRenderer` | 基准/修复 | 动态VOL恒值范围实际进入绘制 |
| `fixedDynamicCrosshairYAndPane` | 基准/修复 | 动态pane十字线位置与归属保持 |
| `fixedDynamicDragFlagConsumption` | 基准/修复 | 动态pane禁拖后高度保持80 |
| `fixedDynamicHeightSetter` | 基准/修复 | 动态高度123设置与回读生效 |
| `fixedImmediateRealtimeAfterInit` | 基准/修复 | INIT立即首推保留 |
| `fixedImmediateRealtimeWithExternalScope` | 基准/修复 | 公开externalScope下合法首推保留 |
| `fixedInitialBoundaryLoadsWithoutGesture` | 基准/修复 | 初始不足时自动续拉 |
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
| `fixedPriceLinePrecisionAndThousandsFormat` | 基准/修复 | 精度5和自定义千分位共同生效 |
| `fixedPriceLineSymbolPrecision` | 基准/修复 | 品种精度5实际消费 |
| `fixedRealtimeAcceptedBeforeABA` | 基准/修复 | 真正锁等待经过ABA后旧推送被拒 |
| `fixedRegisteredSpecDoesNotChangeMainAxis` | 基准/修复 | 轴spec注册生效且不改主轴 |
| `fixedSecondaryAxisRelativeConvention` | 基准/修复 | 副轴相对坐标与逆变换正确 |
| `fixedSelectedFixedZOverlayBodyHit` | 基准/修复 | 固定层级对象选中后线身仍命中 |
| `fixedSimpleAbsoluteMeaning` | 基准/修复 | 简单absolute只改变y参考系 |
| `fixedSimpleBarAttrsReachDrawPipeline` | 基准/修复 | BAR真实矩形绘制调用attrs |
| `fixedSingletonBoundLogReversePane` | 基准/修复 | 单实例log/reverse绘制与转换一致 |
| `fixedStackedLinesConversion` | 基准/修复 | 纯线叠加范围与转换一致 |
| `fixedSubPaneOverlayBodyBoundary` | 基准/修复 | 副图越界主体不命中主图区 |
| `localPaneOverlayTemplateBoundsAreNowRelative` | 基准/修复 | 模板收到局部高度与像素点 |
| `realPatchBeforeAndAfter` | 基准/修复 | 历史arc端点false、当前true |
| `barAttrsLocalGeometryIsDrawnOutsideDynamicPane` | 差异复现 | 局部几何被当绝对坐标绘制，N04 |
| `boundAxisProjectionUsesOtherAxisInstancesRange` | 差异复现 | 同pane另一轴实例影响本轴范围，N02 |
| `builtinMacdRangeStillIgnoresStackedInstances` | 差异复现 | 内置MACD范围漏掉已进入绘制的叠加实例，N03 |
| `builtinVolumeRangeStillDisagreesWithConversion` | 差异复现 | 内置VOL转换/绘制差40px，N03 |
| `figureCallbacksLoseValueAndCandleContext` | 差异复现 | attrs收到NaN/空行情，styles收到空行情，N04 |
| `fixedZLevelStillDisablesControlPointEditing` | 差异复现 | 固定层级端点命中变为整体拖动，N07 |
| `priceLineDecimalFoldIsNotApplied` | 差异复现 | 已配置的小数折叠未执行，N05 |
| `priceLineIgnoresInstancePrecisionOverride` | 差异复现 | 指标实例精度覆盖未消费，N05 |
| `rejectedInitStillSubscribesAndNewFeedWritesOldSession` | 差异复现 | 实际INIT拒绝后新推送写进旧会话，N01 |
| `selectedControlPointStillDrawsAndHitsOutsidePane` | 差异复现 | 选中控点跨pane绘制并命中，N06 |
| `unusedSubAxisChangesDefaultIndicatorProjection` | 差异复现 | 未绑定额外轴改变默认实例投影，N02 |
| `upstreamBarRectangleAttrsAreIgnored` | 差异复现 | 上游矩形几何字段未覆盖默认值，N04 |

证据材料位于`/tmp/lkanvas-audit-round5-20261031/`：外部测试源、最终JUnit XML、基线测试计数、oracle生成结果、快照核验及执行日志。临时目录不是长期交付制品；本报告保留了关键输入、条件和结果，修复后应将相应期望行为保存到正式测试。

- N01完全通过公开DataLoader回调复现；本轮不依靠反射写入会话字段。原ABA基准仅用现有Store锁固定线程交错，结果已从错误接纳变为正确拒绝。
- N02/N03从真实renderer的draw钩子读取其yOf，在读取后中止本次绘制；这是生产投影的运行证据，不是手写往返公式或真机像素截图。
- N04从真实figure/动态pane入口记录回调参数和drawRect参数；pane裁剪导致图形不可见的判断基于矩形与裁剪范围，未声称本次生成了截图。
- N05直接调用正式文本生成函数，验证字符串；N06记录drawCircle和实际hitTest；N07运行实际命中并静态追踪手势分支。完整触控E2E未执行。
- 真实arc演练的before源已再次与`git show b63d173^:.../Figures.kt`核对，仅替换测试包名；端点样例旧false、当前true。实际覆盖物命中与负差绘制也保持正确。

## 7. 台账、结构保持与后续验收

check.py本次解析296行：已声明237、行为通过42、已接线2、不适用15，结构可对应仍为0。Q01–Q09均被解析；结构映射检查确实执行，27指标映射集合本次人工核对完整。不能继续重复第四轮之前的“新编号被跳过”或“helper从未调用”结论。

与此同时，全表42项“行为通过”的计数只证明被引用文件/测试存在并满足脚本检查；它不执行本轮多轴、校验失败或图形上下文用例。本轮README的“Q01–Q09全项收口”仍超出实际证据。

| 领域 | 本轮实际收敛 | 尚未完成的结构/逻辑保持 |
|---|---|---|
| 数据状态机 | 以接受时点epoch及捕获的订阅代次修复原交错 | Store的受理成功与ChartDataStore的实际成功仍分离，N01 |
| pane/轴 | 相对坐标与单轴投影正确，共享范围成为真实消费者入口 | 第一条轴、首实例范围仍代替完整绑定集合，N02/N03 |
| figure | 删除固定副图专用画柱，所有可见实例进入通用流程，BAR attrs执行 | 数据/几何/样式上下文没有等价贯穿所有入口，N04 |
| 覆盖物 | 主体局部坐标、输入反算、主体裁剪、arc方向已改善 | 控制点边界及层级编辑分支独立遗漏，N06/N07 |
| 格式与Layout | 品种精度/千分位、动态高度和禁用状态实际生效 | 格式链最后一步及实例精度仍未消费，N05 |
| 指标计算与同步演练 | 27 oracle与calc/Scratch基线保持，arc有限补丁可重复验证 | 这些证据不能代替整个图形/状态模型的一致性证明 |

与第四轮相比，下一步可以更集中地修改已有入口，继续删除首项近似和参数旁路；不需要撤销本轮或重新实现整库。验收仍应要求：

1. INIT成功/失败、订阅与后续写入在同一会话身份下保持一致，失败结果不得遗留可混写的状态。
2. 每个实例的绑定轴及有效范围在渲染、转换、标签中一致；覆盖未绑定轴、两轴叠加、内置pane多实例和BAR基线。
3. figure拿到真实数据和几何上下文，attrs按上游语义进入最终产物，全部pane一致消费。
4. 覆盖物主体、控制点、命中和手势共同遵守pane及层级契约；格式链与配置覆盖完整执行。
5. 正式测试从真实消费者进入，并结合实际符号映射及局部源码补丁验证。测试数量和发版声明不替代结构验收。

## 8. 未检查范围与风险

本轮深入检查第四轮修复涉及的Store/Host/ChartEngine、pane/轴/值域、figure及覆盖物调用链、格式与Layout和验收脚本；扩展了拒绝INIT、多实例、多轴、attrs上下文及控制点组合。

未重做真机截图、完整触摸E2E、TalkBack、Macrobenchmark、R8、Maven发布/consumer及图片编码集成；未重新认证已有性能报告。未逐行证明27指标的全部参数边界，未穷举所有样式、图形和异常输入。辅助几何函数也未全面重新认证。

**本报告未实施修复。最高优先级是N01的INIT失败后会话混写，其次是多轴/值域与figure上下文。总体继续接近目标，但仍不能将当前版本作为完整结构与逻辑对齐验收通过。**
