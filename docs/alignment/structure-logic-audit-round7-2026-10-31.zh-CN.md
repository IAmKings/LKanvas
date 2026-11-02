# Compose 工程与 KLineChart 结构、逻辑一致性第七轮审计及目标收敛情况

- 日期：2026-10-31（Asia/Shanghai）。
- 审计对象：`be48e829290b5a06af8da4a1e9f147d12e721029`，版本 `0.11.0`。
- 对比起点：第六轮对象 `f0b8dc09ede3b30867974797321ec6dab82bfbbe`（`0.10.0`）。
- 固定上游：KLineChart v10.1.0，`d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df`。
- 标准沿用第一轮的 S1/S2/S3/L1/L2/L3/V1/U1：以职责、数据契约、主循环、条件和调用顺序、实际消费者及局部同步能力验收；不按文本相似率、测试数量或版本号评分。
- 范围：复核 M01–M06，检查对应会话、轴、范围、figure、覆盖物链路，并验证既有契约中的组合输入。未追加功能要求。
- 修改边界：开始时工作区干净；本次只新增本报告。实现、仓库测试、构建配置、上游快照、既有文档均未改动；验证材料位于仓库外的临时目录。

## 1. 审计结论

**本轮有明确的结构性收敛，比第六轮更接近原目标；仍未通过完整结构与逻辑一致性验收。**

第六轮 6 项发现对应的 8 个原始差异探针，本轮均已改为正确行为并通过。实际代码也发生了所需的结构变化：轴解析进入实例循环，固定和动态 pane 共用投影入口；控点确实位于裁剪块内；命中改为单一层级遍历；BAR 四字段和 LINE coordinates 已进入绘制消费者。这些不是仅改注释或补台账，应保留。

在此基础上，本轮复现 **4 项剩余发现：1 项 P1、3 项 P2**。P1 是失败 INIT 的已入队推送仍会写入旧会话；P2 集中在绑定轴的范围约束、部分 BAR attrs 和混合 LINE attrs。M04/M06 在本次覆盖范围内关闭；M01/M03/M05 的原样例修复成立，但相关领域尚未完整关闭。M02 的每实例轴选择已关闭，其下游范围问题单独记录。

| 验收维度 | 本轮判断 | 依据 |
|---|---|---|
| S1 来源、符号与差异可追溯 | 部分通过 | 155 个官方快照源码一致；既有指标映射保留；台账“行为通过”的范围仍宽于当前证据 |
| S2 领域职责与依赖结构 | 明显改善，未整体通过 | 实例轴选择、控点上下文和层级遍历统一；失败会话的最终提交归属仍缺一环 |
| S3 算法与控制流形态 | 部分通过 | 指标公式基准保持；LINE 仍把逐行独立几何覆盖解释为连续路径状态 |
| L1 数据与状态机 | 不通过整体验收 | 失败 Reset 与随后入队的 Update 没有共同的提交身份门禁，见 R7-01 |
| L2 参数和公共契约 | 部分通过 | 完整矩形字段已消费；部分覆盖和绑定轴 min/max 仍有差异 |
| L3 注册→计算→绘制/命中/编辑 | 明显改善，部分通过 | 多实例轴与控点链路修复有效；部分几何组合仍错误 |
| V1 差分和门禁 | 部分通过 | 388 项既有测试通过；独立探针的 5 个差异用例归为 4 项发现；台账校验不验证运行语义 |
| U1 有限上游补丁同步 | 既有 arc 演练限定通过 | 历史 before/after、实际消费者和角度探针保持通过；未外推为全领域升级保证 |

## 2. 基线和验证结果

| 检查 | 本轮结果 | 证明范围 |
|---|---|---|
| core 既有 JVM 测试 | 222 项，失败/错误/跳过均 0 | 当前正式测试覆盖的行为 |
| Compose 既有 JVM 测试 | 166 项，失败/错误/跳过均 0 | 当前正式测试覆盖的行为；包含本轮新增的录制绘制测试 |
| 外部 `audit.SeventhAuditTest` | 60 项，失败/错误/跳过均 0 | 55 项确认基准/修复，5 项差异探针归为 4 项发现 |
| TS oracle 重新生成 | 27 份 JSON，与仓库 fixture 解析后全部一致 | 使用固定上游真实 calc；结合 core 测试确认这些共同输入结果 |
| 官方快照比对 | 官方 155、本地 155；修改/缺失/新增均 0 | `third_party/klinechart-v10.1.0/src` 的来源完整性 |
| `tools/alignment/check.py` | 通过，解析 309 行 | 七面覆盖、表格格式、签名和引用存在性；不证明结构或行为正确 |
| 真实 arc 补丁复验 | 通过 | 原历史源码端点命中 false、当前 true；UI 消费与负角跨度基准保持 |

既有测试先以 `cleanTest` / `cleanTestDebugUnitTest` 清理测试结果后执行，结果另行保存，随后才加载外部探针。没有用外部测试的 XML 代替正式测试计数，也没有把“成功复现错误”的 5 项探针计作健康行为。

```sh
./gradlew :lkanvas-core:cleanTest :lkanvas-compose:cleanTestDebugUnitTest \
  :lkanvas-core:test :lkanvas-compose:testDebugUnitTest \
  --no-build-cache --console=plain

./gradlew -I /tmp/lkanvas-audit-round7-20261031/audit.init.gradle \
  :lkanvas-compose:testDebugUnitTest --tests 'audit.SeventhAuditTest' \
  --no-build-cache --console=plain

python3 -B tools/alignment/check.py
```

临时材料：[基线计数](/tmp/lkanvas-audit-round7-20261031/baseline-results.json)、[探针源码](/tmp/lkanvas-audit-round7-20261031/src/audit/SeventhAuditTest.kt)、[探针结果与完整方法清单](/tmp/lkanvas-audit-round7-20261031/probe-results.json)、[上游快照比对](/tmp/lkanvas-audit-round7-20261031/snapshot.json)、[oracle 生成器](/tmp/lkanvas-audit-round7-20261031/gen-fixtures.mts)、[oracle 比对](/tmp/lkanvas-audit-round7-20261031/oracle-results.json)、[台账校验输出](/tmp/lkanvas-audit-round7-20261031/alignment.log)。临时目录可能被系统清理，关键输入、预期和实测值已写入下文；这些材料未进入交付制品。

### 当前上游与固定版本

本次在线查询的上游 main 为 `66dd45b31e93b57a4c75a3e0b98bc2a8393df36e`，比固定 v10.1.0 ahead 3；差异仍为 `README.md`、`README.zh-CN.md` 和 `src/extension/figure/arc.ts`。没有出现相对第六轮的新源码增量。参见[官方固定 SHA 比较](https://github.com/klinecharts/KLineChart/compare/d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df...66dd45b31e93b57a4c75a3e0b98bc2a8393df36e)。

因此，来源基线可靠，已知 arc 增量的既有局部同步保持有效；这与“Compose 的全部结构和行为已与上游对齐”是两个不同的判断，后者仍未成立。

## 3. 第六轮 M01–M06 逐项复核

| 原发现 | 本轮实测与结构变化 | 结论 |
|---|---|---|
| M01：旧失败清理较新会话 | `myEpoch + myGen` 归属复核、代次不回退；B 失败后 C 保持订阅，C 数据 `[300,301]`，退订记录仅 A/B | 原复现已修；失败会话已入队动作仍有 R7-01，领域未完整关闭 |
| M02：多轴共用首条投影、内置 pane 忽略绑定轴 | `instanceBoundYOf` 在两个实际实例循环内调用；双轴第二实例绘制/转换均为 y=541，内置 reverse 轴均为 y=500；未绑定回退基准保持 | 每实例选择问题在本轮覆盖范围内关闭；下游范围另见 R7-02 |
| M03：BAR 字段误解、LINE coordinates 零消费 | `{x:20,y:30,width:7,height:9}` 实画为 `[20,30,7,9]`；LINE coordinates 进入真实 `drawPath` 分支 | 原契约入口已接通；部分覆盖、混合行仍有 R7-03/04，未完整关闭 |
| M04：控点在裁剪/平移块外 | 控点位于 clipRect 内、局部主体 translate 外；有效屏幕 y 两次均为 523.9328，clipDepth 均为 1，与转换结果一致 | 在本轮覆盖范围内关闭；pane 外命中拒绝、主体边界基准也保持 |
| M05：模板 min/max 不执行 | `paneVisibleRange` 合并约束；VOL 得到 `[0,100]`，自定义约束得到 `[-50,500]` | 默认 pane 原复现已修；绑定轴范围仍遗漏相同约束，R7-02 |
| M06：低层固定控点全局优先 | 单一 z 降序遍历，同实例先控点后主体；被 z=10 宽线覆盖时命中高层，单对象固定控点仍可编辑 | 在本轮覆盖范围内关闭 |

结构证据：[双路径实例轴入口](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:412)、[内置 pane 实际调用](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:296)、[动态 pane 实际调用](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:351)、[控点裁剪上下文](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:102)、[单一命中遍历](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:253)。

上述“关闭”仅指明确复核的发现与覆盖范围，不构成该领域全部配置、全部异常路径的穷举证明。

## 4. 剩余发现（按优先级）

### R7-01 · P1：失败 INIT 前已经入队的实时推送仍能串写旧会话

**输入和实测：** A 成功载入一根 close=100；切换 B，B 的 INIT 回调返回非法 OHLC（high=190、low=210）。在调度器继续执行前，立即调用刚建立的 B 订阅回调，提交 close=999。随后排空队列，门面品种为 B，数据快照 key 仍为 A，但 A 的行情已变为 `[100,999]`。正确结果应保留 A 的 `[100]`，失败 B 的推送不得进入 A。

**控制流根因：** `applyInit` 受理即增加 epoch 并返回 true，Store 因而建立 B 订阅。B Update 捕获相同 epoch，通过宿主门禁，随后和 Reset 一起进入引擎队列。数据存储拒绝非法 Reset 后保留 A；引擎接着处理不带目标 key/提交会话身份的 UpsertLatest。较晚执行的 rejected continuation 退订 B，可以阻断后续回调，却无法撤销已入队、甚至已经提交的 B Update。

这与第六轮“旧失败退订新 C”是不同的先后顺序。双身份清理修复有效，但没有给失败 INIT 的排队动作提供提交归属。原来“失败后排空，再推送”的用例没有覆盖本顺序。

**证据：** [INIT 受理与异步结果](../../lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:476)、[Update 仅检查 epoch](../../lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:521)、[Store 依受理结果订阅](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/store/Store.kt:173)、[引擎队列提交](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/compute/ChartEngine.kt:127)、[Reset 拒绝后保留旧数据](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/store/ChartDataStore.kt:177)、[UpsertLatest 提交未带身份](../../lkanvas-core/src/main/kotlin/io/lkanvas/core/compute/ChartEngine.kt:245)。

**最小处理方向：** 在既有串行链路中，让 INIT 的后续动作受其最终建会话结果约束，提交前拒绝失败会话动作。仅在 rejected continuation 再改一次全局 epoch 不能保证拦住已进入引擎队列的请求。保留旧失败不干扰新 C、ABA 旧回调门禁，以及合法 INIT 同步首推能够保留的基准。

**不处理的影响：** 行情可能被另一品种的报价污染，旧快照与当前品种不一致；这是当前主要验收阻断。这里不要求 TS 接受非法输入或复制 Kotlin 的校验器，而要求平台异步适配保留已确定的会话隔离契约。

### R7-02 · P2：绑定轴范围没有执行模板 minValue/maxValue

**输入和实测：** 使用继承 VOL（`minValue=0`）的恒值 100 指标，绑定同 pane 的普通额外轴。默认 `paneVisibleRange` 为 `(0,100)`，但 `extraAxisRange` 为 `(100,100)`。实际 `instanceBoundYOf` 对基线 0 的输出约为 y=4521，而所属 pane 仅 y=481…561，基线落在 pane 外。

**结构根因：** 本轮把实例选轴统一到了 `instanceBoundYOf`，这一步正确；但它进入 `extraAxisRange`。M05 的约束合并只添加到 `paneVisibleRange`，额外轴仍只扫描结果列，没有合并绑定实例的 `definition.minValue/maxValue`。这条范围入口同时用于额外轴转换和投影，单纯证明“绘制与转换相同”会让两者共享错误范围。

上游 YAxis 在本轴对应的指标集合中先聚合 specifyMin/specifyMax，再与观测范围合并，范围约束并不限于未绑定的默认 pane。

**证据：** [pane 约束合并](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:526)、[额外轴范围扫描](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:913)、[绑定投影消费者](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:422)、[上游按轴选指标](../../third_party/klinechart-v10.1.0/src/component/YAxis.ts:142)、[上游模板约束聚合](../../third_party/klinechart-v10.1.0/src/component/YAxis.ts:163)、[上游范围合并](../../third_party/klinechart-v10.1.0/src/component/YAxis.ts:222)。

**最小处理方向：** 将已有 min/max 聚合规则应用于实际绑定的轴集合，使额外轴的绘制、转换和标签消费相同的正确范围。不能直接拿整个 pane 的范围替代独立轴范围，否则会撤销本轮多轴修复。

**不处理的影响：** 同一指标只因绑定轴就丢失模板范围声明，柱基线/刻度随之失真；后续范围补丁仍需分别寻找入口。

### R7-03 · P2：BAR 部分覆盖的默认几何仍与上游不一致

**输入和实测：** 两个独立输入均通过实际 BAR 消费者复现：

- 动态 pane 顶部为 481，恒值 100、范围 `[0,100]`。attrs 仅返回 `{height:9}`，保持默认 x/y/width；实际矩形 top=962，预期仍为默认 top=481，只改变高度。
- 默认槽宽 8、柱中心 x=4，attrs 仅返回 `{width:10}`。上游先采用覆盖宽度，再计算缺省左边界，应为 -1；本地仍按旧 bodyWidth=6 计算左边界，得到 1，中心偏移 2 像素。

完整 `{x,y,width,height}` 的原复现已通过，不能据此推断部分覆盖也已对齐。

**逻辑根因：** `y(v)` 已经是绝对画布坐标。进入“有 y 或 height”的分支后，缺省 y 取 `min(vy,baseY)`，随后无条件再加 `paneTop`。只有自定义 attrs.y 是 pane 局部坐标，需要这次平移；默认 y 已经完成平移。该分支把两种坐标来源合在了同一次加法里。另一个分支中，默认 ax 在读取有效 width 前已经按 bodyWidth 决定；上游则先取 `attrs.width ?? 默认宽度`，再生成缺省 x。因此，几何字段需要按依赖顺序合成，并非四个字段各自独立覆盖就已等价。

**证据：** [默认 x 与覆盖宽度的计算顺序](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:744)、[动态 pane 的绝对 defaultY](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:344)、[BAR 部分字段分支](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:750)、[上游默认矩形后展开 attrs](../../third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:180)。

**最小处理方向：** 保持默认几何先计算、attrs 按键覆盖；明确默认 y 与 attrs.y 的坐标系，只对后者平移一次；缺省 x 按有效宽度居中，同时保留显式 attrs.x 的左边界含义。保留完整四字段、无 attrs、局部 top/bottom 兼容输入。

**不处理的影响：** 合法的部分几何覆盖把副图柱体移出裁剪范围或使柱中心偏移；后续同步仍需逐键排查本地特殊分支。

### R7-04 · P2：LINE 混合自定义与默认 coordinates 时多出连接线

**输入和实测：** 三行数据的默认坐标分别为 `(4,100)`、`(12,110)`、`(20,120)`。仅第 0 行返回自定义点列 `[(300,400),(500,600)]`，其余行不覆盖 coordinates。上游应画该自定义点列，以及第 1 行自己的默认段 `(12,110)→(20,120)`。

实际记录到 1 次自定义 `drawPath`，另有 native `drawLines` 数组：

```text
[500,600,12,110, 12,110,20,120]
```

其中 `(500,600)→(12,110)` 为多出的线段。探针使用实际 `drawInstanceFigures`，录制 native Canvas 的 drawLines 参数；未根据静态源码推测结果。

**结构根因：** 自定义行绘制后，把点列末尾写入全局 `lastX/lastY`，并置 `prevWasFinite=true`。下一默认行于是从这个自定义终点接续。上游每行把 `attrs.coordinates ?? [currentCoordinate,nextCoordinate]` 作为独立 figure 段登记，不将自定义终点改写为下一行的默认起点。这里仍把逐行几何覆盖改成了连续路径状态。

**证据：** [自定义行写回连续状态](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:625)、[下一行生成批量段](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:632)、[上游独立段语义](../../third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:194)。

**最小处理方向：** 按上游行段定义决定默认段与覆盖段，再按样式批量提交；批量优化仍可保留，但不能用上一自定义终点决定下一默认段。验证默认→覆盖→默认、NaN 间隔和行级样式，避免只靠清空 prev 状态而漏画另一侧合法默认段。

**不处理的影响：** 局部覆盖几何时出现长斜线/伪连接；上游逐行 figure 补丁不能直接对应本地连续状态分支。

## 5. 验证边界与台账判断

本轮新增外部差异探针分别为：

| 方法 | 主断言用途 | 实测 |
|---|---|---|
| `remainingRejectedInitAllowsAlreadyQueuedPushIntoOldSnapshot` | 复现 R7-01 | symbol=B、key=A、closes=`[100,999]` |
| `remainingBoundAxisIgnoresDeclaredMinimum` | 复现 R7-02 | 默认范围 `(0,100)`，绑定范围 `(100,100)`，基线 y≈4521 |
| `remainingHeightOnlyBarAttrsDoublePaneOffset` | 复现 R7-03 | top=962，预期 481 |
| `remainingWidthOnlyBarAttrsUseOldDefaultLeft` | 复现 R7-03 | width=10 时 left=1，预期 -1 |
| `remainingMixedLineAttrsCreateBridgeFromCustomEndpoint` | 复现 R7-04 | 额外提交自定义终点到下一默认点的段 |

其余 55 项覆盖原 arc 补丁、分页与实时订阅、INIT 首推/重试、旧会话/ABA 门禁、样式 patch、动画、pane 查询迁移与高度、相对/绝对转换、多轴与混合绑定、范围叠加、figure 上下文、覆盖物格式、裁剪/平移和层级命中。完整方法与输出见探针结果 JSON。第六轮 8 项差异已全部转为正确行为断言；本轮 5 项差异断言没有写进仓库去固化错误行为。

当前 Compose 构建配置启用了 `unitTests.isReturnDefaultValues=true`。正式 `TestDrawScope` 和本次探针录制真实绘制调用、参数及 clip/translate 深度；LINE 探针另用 Android Canvas 子类记录批量坐标。**这些是消费者和几何参数证据，不是真机像素栅格、截图或手势 E2E 证据。** 没有把 mocked Android Path 的默认返回值当成图形已经正确显示。

台账校验结果为 309 行：已声明 237、行为通过 55、已接线 2、不适用 15。M 编号和此前遗漏的 N06 已进入解析/台账，文档完整性改善。[M01–M06 台账](gap-matrix.md:462)与[figure 结构映射](structure-map.md:30)均已更新，但 M01/M03/M05 的“行为通过”不能再解释为覆盖全部组合。当前工具检查引用存在，不执行上述输入，更不能因本轮 check.py 通过就判定完整验收通过。

上述范围说明是对现有状态的审计意见，本次没有修改台账状态，也没有撤销已经成立的修复证据。

## 6. 当前目标收敛情况

**从第一轮到第七轮，净收敛成立；第七轮的结构性进展比第六轮更明确。**

| 领域 | 已取得的收敛证据 | 当前距目标的差距 |
|---|---|---|
| 来源与指标算法 | 固定 SHA、官方快照一致、符号映射和 27 组共同输入结果保持 | 全参数/异常边界未穷举；不能据 fixture 声称全部算法逐行等价 |
| 加载和会话 | 分页不误杀实时、旧回调/ABA 门禁、旧失败不清理新 C、合法同步首推保持 | 失败 INIT 的已入队动作仍无最终提交归属，唯一剩余 P1 |
| pane/轴/范围 | 默认范围供绘制和转换共享；固定/动态 pane 真正逐实例选轴；默认模板 min/max 生效 | 绑定轴范围仍未消费同一约束规则 |
| figure 数据与几何 | 真实 value/candles 上下文、BAR 四字段含义、LINE 点列入口接通 | 部分覆盖的坐标系/默认字段依赖与逐行段定义仍有局部差异 |
| 覆盖物与交互 | 主体/控点裁剪、绝对坐标、固定层级编辑和单一 z 命中在本轮探针中统一 | 本轮原发现关闭；仍需最终设备验证 |
| 后续升级成本 | 已有 arc 真实补丁仍可局部对应；修复主要集中在有限领域入口 | 尚无其他领域的真实补丁演练，不能保证以后任何更新都免于较大适配 |

历轮发现数量为 **16→14→11→9→7→6→4**；第六轮为 4 项 P1，本轮为 1 项 P1。不同轮的检查粒度和问题归类存在变化，因此这组数字仅作为历史索引，不换算完成百分比，也不用线性外推预测“再两轮必然归零”。

比条目下降更有价值的证据，是这次实际消除了“循环外选首轴”“控点在裁剪外”“全局控点第一遍”等结构分叉。剩余问题也更集中：已有入口覆盖不完整、平台队列缺少最终身份约束。没有证据要求全库重写；仍应沿现有职责做局部修正。

但尚未进入纯文档或最终验收阶段：R7-01 直接影响数据归属；R7-03/04 也说明新分支会带来组合回归。项目在总体方向上越来越接近目标，并非每个修改都严格单调改善。

### 预计还需几轮

冻结原有范围、不追加功能，**仍建议预留 2–3 轮：1 轮集中修复这 4 项，1 轮独立验收，必要时再留 1 轮返修。** 这是工作安排估算，置信度中等，不是最终通过承诺。相比第六轮，同样的余量现在有更明确的结构依据，不能因为版本已经递增就机械扣减。

下一轮达到以下条件，才适合把剩余工作收缩为独立验收：

1. 合法 INIT 首推、非法 INIT 已入队首推、旧失败后新 C、ABA 和重试使用一致的会话提交规则，不只在回调入口拦截。
2. 每轴范围包含本轴模板约束，保持本轮逐实例投影及独立范围；固定/动态路径均由实际消费者确认。
3. BAR 完整/部分/默认 attrs 在非零 paneTop 下只平移一次、宽度改变时仍按有效宽度居中；LINE 默认与覆盖行组合遵循独立段语义，保持 NaN/样式基准。
4. 本轮已通过的控点、层级、范围、格式和历史补丁探针保持；再做独立复审及必要的设备绘制/触摸验证。

若以上能一次修到共同入口，已有结果支持近期完成验收；若继续只补单个输入而留下不同提交身份、范围入口或路径状态规则，剩余轮数就无法可靠估计。

## 7. 未检查范围和最终判断

本轮未执行真机/模拟器截图、完整触摸 E2E、TalkBack、Macrobenchmark、R8、Maven 发布/consumer 和图片编码集成，未重新认证既有性能数据；没有穷举所有图形、样式、参数和异常输入。新增录制测试不替代这些检查。

**本次仅新增审计报告，未实施修复。第七轮继续且更明确地向目标收敛；仍有 1 项 P1 与 3 项 P2，完整验收未通过。当前最高风险是失败新会话的已入队推送污染旧会话行情。**
