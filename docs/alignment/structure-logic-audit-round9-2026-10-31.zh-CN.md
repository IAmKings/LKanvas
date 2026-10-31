# Compose 工程与 KLineChart 结构、逻辑一致性第九轮审计及目标收敛情况

- 日期：2026-10-31（Asia/Shanghai）。
- 审计对象：`7357d0cb2bffcdc10f79ef9147afc0e3874703e9`，版本 `0.13.0`。
- 对比起点：第八轮对象 `f0e68f9c540aea57c599aea9ae9a438dc8afa8da`（`0.12.0`）。
- 固定上游：KLineChart v10.1.0，`d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df`。
- 标准不变：沿用 S1/S2/S3/L1/L2/L3/V1/U1，按领域职责、数据及参数契约、主循环、条件与调用顺序、实际消费者、共同输入行为和有限补丁同步能力验收；不按文本相似率、标点或测试数量评分。
- 工程用途与负载：纯 Kotlin 核心加 Compose 原生图表；按单图表、异步行情、多指标/轴/覆盖物、公开 state 复用和历史滚动场景审计。
- 范围：复核 R8-01…R8-03，追踪提交身份生命周期和 LINE 行段消费者，补验数据边界与可见范围边界的组合；不追加新功能。
- 修改边界：开始时工作区干净；本次只新增本报告，未修改工程代码、正式测试、构建配置、快照或既有文档。验证材料均位于仓库外临时目录。

## 1. 审计结论

**第九轮有明确收敛：第八轮 3 项原复现均已修复，64 项历史探针全部按正确预期通过；本轮覆盖中未确认 P1。完整结构与逻辑一致性验收仍未通过，剩余 2 项 P2。**

本次修复进入了共同规则：会话身份计数与宿主拒绝水位移入可复用的 ChartState，与引擎生命周期一致；默认 LINE 段的几何和 styles 均归所属前行；下一点缺失时不再登记自定义段。连续三次在失败后复用同一 state，最终 epoch=7，新会话仍能更新，旧订阅也未污染数据。之前的失败已入队门禁、多轴、范围、BAR、控点和层级基准保持。

剩余问题均集中在 LINE：**R9-01 把数据有效性误限为可见范围，R9-02 将自定义段立即绘制、默认段延迟提交，反转了两者的覆盖顺序。** 本轮三个本地运行用例复现这两项差异，并直接执行上游 `IndicatorView.drawImp` 确认对照结果。

因此，现在比第七、八轮更接近最后验收；但不能将原 NaN 用例修复扩展成全部行段边界都已一致，也不能据此承诺任意后续上游更新都无需较大适配。

| 验收维度 | 本轮判断 | 主要依据 |
|---|---|---|
| S1 来源与符号可追溯 | 部分通过 | 155 个官方快照源码一致、既有映射保留；R8-03 台账把额外可见区限制误归为上游条件，需同步纠偏 |
| S2 职责与依赖结构 | 身份链路通过，绘制仍部分通过 | 身份与水位生命周期统一；LINE 仍有立即/延后两个提交出口，顺序未统一 |
| S3 循环、分支与算法形态 | 部分通过 | 行段样式归属修复；仍多了上游没有的可见区条件，混合段的实际提交顺序也不同 |
| L1 数据与状态机 | 本轮覆盖通过 | 失败/重试/同步首推、旧回调/ABA、跨门面及连续 state 复用基准通过；未穷举全部调度 |
| L2 参数与公共契约 | 部分通过 | 原范围、BAR 与样式契约保持；部分可见范围仍影响合法 coordinates 覆盖 |
| L3 注册→计算→绘制/命中/编辑 | 部分通过 | 原多轴、控点和层级链通过；实际动态 pane 仍复现 R9-01，覆盖顺序仍有 R9-02 |
| V1 差分与门禁证据 | 明显改善，尚未全通过 | 401 项正式测试通过；上游真实 View 与本地消费者对照仍有一项差异 |
| U1 局部源码补丁同步 | 原 arc 演练限定通过 | 历史 before/after 和消费者复验保持；未增加其他真实源码补丁演练 |

## 2. 基线与验证结果

| 检查 | 本轮结果 | 证据边界 |
|---|---|---|
| core 正式 JVM 测试 | 222 项，失败/错误/跳过均 0 | 当前正式覆盖 |
| Compose 正式 JVM 测试 | 179 项，失败/错误/跳过均 0 | 共计 401 项正式测试 |
| 外部 `audit.NinthAuditTest` | 68 项，失败/错误/跳过均 0 | 65 项确认基准/修复；3 项差异探针归为 2 项 P2，不计为健康行为 |
| 第八轮 3 项原差异 | 全部修复 | state 复用、行样式归属、下一点 NaN |
| 连续 state 复用补验 | 三次成功/失败/复用后 FINAL 为 `[500,501]`，epoch=7 | 原订阅回调再推 999 也未串写新快照 |
| 上游实际 View 执行 | 五组输入，输出符合第八轮修复及本轮预期 | 执行真实 `IndicatorView.drawImp`、`ChildrenView.eachChildren`、figure 准备/样式/合并路径；宿主与绘图出口录制 |
| TS 指标 oracle 重生成 | 27 份 JSON，与仓库 fixture 解析后全部一致 | 固定上游真实 calc、共同输入结果 |
| 官方快照比对 | 官方 155、本地 155；修改/缺失/新增均 0 | `third_party/klinechart-v10.1.0/src` 来源完整性 |
| `tools/alignment/check.py` | 通过，316 行 | 七面覆盖、格式、签名与引用存在；不执行绘制语义 |
| arc 真实补丁复验 | 通过 | 历史原端点 false、当前 true；实际命中消费者和负角跨度基准保持 |

正式测试先清理结果后执行并保存计数，随后才加载外部探针；没有混用两类 XML。外部差异断言没有写入正式测试去固化错误行为。

```sh
./gradlew :lkanvas-core:cleanTest :lkanvas-compose:cleanTestDebugUnitTest \
  :lkanvas-core:test :lkanvas-compose:testDebugUnitTest \
  --no-build-cache --console=plain

./gradlew -I /tmp/lkanvas-audit-round9-20261031/audit.init.gradle \
  :lkanvas-compose:testDebugUnitTest --tests 'audit.NinthAuditTest' \
  --no-build-cache --console=plain

tools/oracle/node_modules/.bin/tsx /tmp/lkanvas-audit-round9-20261031/view-oracle.mts
python3 -B tools/alignment/check.py
```

材料：[正式测试计数](/tmp/lkanvas-audit-round9-20261031/baseline-results.json)、[外部探针源码](/tmp/lkanvas-audit-round9-20261031/src/audit/NinthAuditTest.kt)、[探针结果与完整方法清单](/tmp/lkanvas-audit-round9-20261031/probe-results.json)、[上游 View 录制脚本](/tmp/lkanvas-audit-round9-20261031/view-oracle.mts)、[上游 View 输出](/tmp/lkanvas-audit-round9-20261031/view-oracle.json)、[快照比对](/tmp/lkanvas-audit-round9-20261031/snapshot.json)、[指标生成器](/tmp/lkanvas-audit-round9-20261031/gen-fixtures.mts)、[指标比对](/tmp/lkanvas-audit-round9-20261031/oracle-results.json)、[台账输出](/tmp/lkanvas-audit-round9-20261031/alignment.log)。临时目录可能被清理，关键输入与输出另在下文保存。

### 当前上游

本次在线查询 main 仍为 `66dd45b31e93b57a4c75a3e0b98bc2a8393df36e`，相对固定基线 ahead 3，差异仍为两个 README 和 `src/extension/figure/arc.ts`。相对第八轮没有新源码增量。参见[官方固定 SHA 比较](https://github.com/klinecharts/KLineChart/compare/d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df...66dd45b31e93b57a4c75a3e0b98bc2a8393df36e)。

来源完整和 arc 增量适配有效，与整个移植实现已完全对齐分别判断。

## 3. 第八轮 R8-01…R8-03 逐项复核

| 原发现 | 本轮实测与结构变化 | 结论 |
|---|---|---|
| R8-01：旧引擎水位误拒重建门面 | 身份计数与宿主拒绝水位由 ChartState 持有；原复现 C 更新为 `[300,301]`；连续三轮复用最终 epoch=7、FINAL 为 `[500,501]`；旧订阅、失败已入队动作、合法同步首推等基准保持 | 本轮覆盖范围内关闭，未复现新的 P1 |
| R8-02：默认段取后一行样式 | 回调行号为 `[0,1]`，两段分别进入宽度 2/4 的两个批次；上游实际 View 输出也分别使用 2/4 | 原发现关闭 |
| R8-03：下一点缺失仍画自定义段 | `[100,110,NaN,130,140]` 的自定义路径为 0，仅默认行 0、3 的段保留；正式 NaN 测试也已纠偏 | 缺失下一点原复现关闭；新添的可见范围限制导致 R9-01，行段边界尚未整体关闭 |

证据：[state 级身份与水位](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:56)、[门面从 state 分配身份](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:486)、[默认段样式取所属前行](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:609)、[下一点检查与额外可见区限制](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:625)。

## 4. 剩余发现

### R9-01 · P2：用可见范围代替数据范围判断下一点，跳过末个可见行的合法自定义 LINE 段

**用途与契约：** 行段属于当前行，其下一点从指标完整 result 的 `dataIndex+1` 取得。当前行进入可见行遍历后，只要完整数据中当前/下一点有效，该行的 coordinates 覆盖仍应生效。自定义点列可以位于 pane 内，不能仅按下一行是否参加本次遍历决定是否丢弃。

**共同输入：** 完整指标行值 `[100,110,120]`，本次只遍历行 `[0,1]`；仅行 1 返回 `coordinates=[(4,20),(12,30)]`，下一行 2 的值 120 有效。保持数据和回调不变，再将遍历范围扩大到 `[0,1,2]` 作对照。

| 消费者 | 遍历行 `[0,1]` | 扩大到 `[0,1,2]` |
|---|---|---|
| 固定上游实际 IndicatorView | 生成默认段 P0→P1 和行 1 覆盖段，共两项 line figure | 仍生成相同两项 figure |
| 本地实际 `drawInstanceFigures` | attrs 只求值行 0，自定义 drawPath=0，行 1 覆盖段丢失 | attrs 求值行 0/1，自定义 drawPath=1，覆盖段恢复 |

**真实 pane 再复现：** 200 行数据，滚动至历史中部，动态 pane 实际绘制范围为 `52…128`。行 128 返回 pane 内点列 `[(100,20),(200,30)]`，行 129 的值 229 有效；实际 attrs 最后只到行 127、自定义路径为 0。探针进入真实 `drawExtraPane`，在整个 LINE 循环完成后的首个 native 批次中止，避免后续轴文字依赖；没有伪造内部 range 字段或改动源码。

**控制流根因：** 修复 R8-03 时使用了 `attrsOf != null && i < range.last && !column[i+1].isNaN()`。`range.last` 是本次绘制窗口的末行，既不是结果列末行，也不是下一点有效性的定义。上游从完整 `result[dataIndex+1]` 构造 nextCoordinate，只检查数据有效性，没有要求下一行也出现在可见行列表。

**证据：** [本地多出的可见区条件](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:625)、[动态 pane 的实际 range 来源](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:336)、[实际通用 figure 消费](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:365)、[上游 next 取完整结果](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:116)、[上游有效点条件](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:194)、[上游可见行遍历](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/view/ChildrenView.ts:23)。

**最小处理方向：** 在现有 `drawLinePlots` 共同分支中，依据完整结果列判断下一点是否存在且有效，保持当前行的绘制遍历范围与 pane 裁剪。保留真正数据末端、NaN、前行样式和自定义段断链基准；无需扩大为全量绘制或重写渲染器。同步纠正台账 R8-03 将“i+1 在可见区”称为上游共同门禁的表述。

**不处理的影响：** 滚动或调整窗口时，合法的自定义图形会因成为末个可见行而消失，即使返回的点列仍在 pane 内；对应上游补丁时仍存在本地独有的额外条件。


### R9-02 · P2：默认段延迟到覆盖段之后提交，重合时实际覆盖顺序相反

**用途与契约：** 上游按行登记 LINE 段，再按原顺序合并连续同样式段并绘制。坐标、线宽、颜色和提交顺序共同决定结果；批量优化应保留这项顺序。

**共同输入与实测：** 三行值 `[100,110,120]`，行 0 的默认段 P0→P1 为红色、宽 6；行 1 返回完全重合的 coordinates `[(4,100),(12,110)]`，样式为蓝色、宽 6。上游实际 View 先提交红色默认 figure，随后提交蓝色覆盖 figure；本地实际消费者顺序为 `[custom-blue, default-red]`，默认批量坐标为 `[4,100,12,110]`。路径颜色实测为蓝色；默认批次对应行 0 的红色样式和几何。本轮未用 mocked Paint getter 或像素截图充当色值证据。

**控制流根因：** 自定义行在循环中立即调用 drawPath，默认段则积累到 groups，在整个循环结束后才调用 native drawLines。即使段归属和各自 styles 正确，两个绘制出口仍把默认段统一移到覆盖段之后。这个缺口在本轮之前已经存在；本轮真实 View 对照将其确认。

**证据：** [本地立即绘制覆盖段](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:640)、[循环结束后才提交默认批次](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:656)、[native 提交](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:666)、[上游按行登记段](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:195)、[上游按合并结果顺序绘制](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:254)。

**最小处理方向：** 保留按行顺序形成的连续批次；遇到自定义段时先提交它之前的默认批次，再绘制自定义段并继续。不要跨覆盖段或样式变化全局重排。无需移除批量优化，但所有出口必须遵守同一实际绘制顺序。

**不处理的影响：** 后登记的蓝色覆盖线会被本应更早绘制的红色默认线盖住，坐标与样式单项通过仍不能保证最终覆盖关系与上游相同。

## 5. 验证与台账边界

| 外部方法 | 主类型 | 结果 |
|---|---|---|
| `fixedRejectedWatermarkAndFacadeRecreation` | R8-01 修复确认 | C 为 `[300,301]` |
| `fixedDefaultLineSegmentsUseOwnerStyles` | R8-02 修复确认 | 行 `[0,1]`、两个样式组 |
| `fixedCustomLineRejectsMissingNextRow` | R8-03 原复现确认 | 下一点 NaN 时路径为 0 |
| `fixedRepeatedStateReuseKeepsMonotonicIdentityAndDropsOldFeeds` | 邻近基准 | 三次复用，epoch=7，FINAL 为 `[500,501]`，旧推送被拒 |
| `remainingVisibleRangeLastRowLosesCustomSegmentDespiteValidNextData` | R9-01 差异复现 | 可见范围缩小时覆盖段丢失，扩大后恢复 |
| `remainingDynamicPaneAlsoDropsLastVisibleCustomSegment` | R9-01 消费者复现 | 实际 range 52…128、next=229，仍未调用末行 attrs |
| `remainingMixedLineSubmissionReversesOverlappingRowOrder` | R9-02 提交顺序复现 | 先蓝色覆盖路径，再提交红色默认段 |

其余 61 项确认继承上一轮的分页/实时订阅、失败 INIT 已入队提交门禁、合法首推/重试、旧回调/ABA、样式 patch、动画、pane 布局迁移、转换、多轴/范围、BAR、figure 上下文、覆盖物格式、控点裁剪/平移、层级命中及 arc 补丁基准。与本轮 3 项修复确认、1 项新增基准、3 项差异合计 68 项。

本轮上游 View 脚本使用真实 `IndicatorView` 类和继承的 `eachChildren`；假宿主提供明确的完整 result、可见行、恒等轴和样式，录制 `createFigure(...).draw(...)` 的几何及样式。五组输入分别为有限可见范围、扩大范围、行级样式、缺失下一点、不同颜色的重合段。本次实际执行了上游 View 的准备、遍历和合并函数；验证边界仍为宿主录制，尚非完整浏览器 E2E。

本地 JVM 探针录制实际 DrawScope/native Canvas 调用和批次数组；Android 默认返回值使其能执行绘制路径，但未证明 GPU 栅格、真实 Path 像素、屏幕触摸或性能。上游同样记录图形定义和 draw 调用，未执行浏览器像素输出。

台账解析 316 行：已声明 237、行为通过 62、已接线 2、不适用 15。R8 三行可解析且引用存在，见[第八轮追踪](/Users/pauldeman/Documents/ai_workspace/KKLineChart/docs/alignment/gap-matrix.md:482)。引用校验不能发现这两项运行差异；R8-03 的原 NaN 修复与本轮可见边界差异应分别记录，不能撤销前者，也不能以其通过覆盖后者。本次未修改台账。

## 6. 当前目标收敛情况

**净收敛继续成立，且本轮证据明显强于第七、八轮：本轮覆盖内不再有已确认 P1，共同身份入口稳定，剩余已确认问题集中到 LINE 的边界与提交顺序。**

| 领域 | 当前情况 | 与最终目标的距离 |
|---|---|---|
| 来源与指标算法 | 官方快照、映射和 27 指标共同输入结果保持 | 全参数/异常边界未穷举；现有证据可持续复用 |
| 会话与提交身份 | 同 state 跨门面连续，拒绝水位与身份生命周期一致；失败、旧回调和首推基准保持 | 本轮重点链路通过；需在最终独立复验中保持 |
| pane/轴/范围 | 每实例轴、独立范围、默认/绑定模板约束保持 | 本轮未发现原范围问题回归 |
| BAR 与覆盖物 | 部分几何覆盖、平移、控点裁剪、固定层级及命中基准保持 | 本轮原发现保持关闭；设备端证据仍待补 |
| LINE | 所属行样式与缺失下一点门禁已修复 | 可见区条件 R9-01、混合段提交顺序 R9-02 尚未对齐 |
| 后续升级成本 | 修改集中于明确领域入口，arc 局部补丁复验持续通过；本轮增加真实 View 消费对照 | 没有要求全库重写的证据；仍不能保证未来任何破坏性上游变更都只需小改 |

历轮条目数为 **16→14→11→9→7→6→4→3→2**；第六、七、八轮 P1 数为 4、1、1，本轮覆盖内为 0。条目粒度与验证深度不同，不能换算完成率。更有价值的证据是 64 项历史探针全部按正确行为通过、连续 state 复用稳定，以及剩余差异已在真实消费者中定位到可见边界和混合提交顺序。

这仍不是严格单调改善：R9-01 是修复缺失值门禁时引入的边界收紧，R9-02 是此次真实 View 对照确认的既有混合提交缺口。两者的范围和严重度均比前两轮的会话停更/串写更小，当前适合进入“最后局部修正后独立验收”的阶段，不再需要按多个共同根因重整实现。

### 还需多少轮

冻结原功能范围，**建议再安排 1 轮局部修复和 1 轮独立最终验收，约 2 轮；不再把第 3 轮作为默认余量。** 这是工程估算，不是通过承诺。

进入最终验收的具体条件：

1. R9-01 在“下一点存在但不可见”时保留覆盖段；真正数据末端/NaN 不登记，前行样式与断链规则保持。
2. 默认/覆盖段保持上游所属行的实际提交顺序，重合且样式不同时仍由后登记段覆盖前段，保留连续批量优化。
3. 当前 65 项确认保持，3 项差异转为正确行为；真实动态 pane 与上游 View 使用相同输入确认结果。
4. 独立复核共享分支及旧会话链路，保持原范围、BAR、控点和层级修复，不追加新能力作为验收前置条件。
5. 补充必要的设备绘制/触摸验证。它属于尚缺的发布验证证据，单独记录，不混入本轮代码发现数量。

若修正再次引入邻近回归，或设备检查暴露新问题，再安排返修；现有证据不支持预先要求全库重写，也不支持宣称本轮已经完成原验收。

## 7. 未检查范围与最终判断

本轮未执行真机/模拟器截图、完整触摸 E2E、TalkBack、Macrobenchmark、R8、Maven 发布/consumer、图片编码集成，未重新认证已有性能数据；未逐行穷举全部 API、图形、样式和异常输入。之前搁置的空 coordinates 上游合并边界未重新裁定，不作为新增验收要求。

**本次只新增报告，未实施修复。第九轮继续且更明确地接近目标；当前确认 2 项 P2，本轮覆盖中未确认 P1，完整源码一致性验收仍未通过。已知代码风险是可见范围边界的自定义 LINE 图形缺失，以及重合段的覆盖顺序与上游相反；设备端验证仍待补。**
