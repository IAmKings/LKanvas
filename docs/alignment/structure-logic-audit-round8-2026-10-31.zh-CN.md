# Compose 工程与 KLineChart 结构、逻辑一致性第八轮审计及目标收敛情况

- 日期：2026-10-31（Asia/Shanghai）。
- 审计对象：`f0e68f9c540aea57c599aea9ae9a438dc8afa8da`，版本 `0.12.0`。
- 对比起点：第七轮对象 `be48e829290b5a06af8da4a1e9f147d12e721029`（`0.11.0`）。
- 固定上游：KLineChart v10.1.0，`d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df`。
- 原标准不变：按 S1/S2/S3/L1/L2/L3/V1/U1，检查领域职责、数据与参数契约、主循环、分支与调用顺序、真实消费者及有限补丁同步能力。不按跨语言标点、文本相似率、测试数量或版本号评分。
- 工程用途与负载假设：纯 Kotlin 核心加 Compose 原生图表，支持异步行情、多指标/轴/覆盖物；按单图表正常使用及公开生命周期操作审计。
- 范围：复核 R7-01…R7-04，并检查所触及链路的组合、缺失值及生命周期；没有追加功能要求。
- 修改边界：开始时工作区干净。本次只新增本报告，未修改实现、正式测试、构建配置、上游快照或已有文档；探针和生成材料均位于仓库外临时目录。

## 1. 审计结论

**第八轮继续向原目标收敛，上一轮的原始复现已全部修复；完整结构与逻辑一致性验收仍未通过。**

第七轮的 5 个差异用例，本轮全部转为正确行为并通过。修复也进入了实际共同入口：失败 INIT 的后续请求在引擎提交时被拒；绑定轴合并模板 min/max；BAR 按有效宽度和明确坐标来源合成；LINE 自定义段不再把终点接到下一默认段。此前通过的每实例轴、控点裁剪/平移、层级命中等基准继续保持。这些是明确的净进展。

本轮确认 **3 项剩余发现：1 项 P1、2 项 P2**。P1 是新提交门禁引入的生命周期回归：复用 ChartState 重建门面后，旧引擎拒绝水位会阻断新会话更新。P2 是 LINE 行段样式仍取后一行，以及自定义段缺少上游的下一点有效性门禁。

因此，不能把“R7 四项原复现均已修复”扩展成“会话与 LINE 领域已全部关闭”，也不能说当前已经只剩文档或真机验收。

| 验收维度 | 本轮判断 | 依据 |
|---|---|---|
| S1 来源与符号可追溯 | 部分通过 | 官方 155 个源码快照完全一致；既有映射保留；台账状态不能替代组合验证 |
| S2 职责与依赖结构 | 继续改善，未整体通过 | 提交门禁已到引擎共同入口；门面身份计数与引擎水位的生命周期仍分离 |
| S3 算法、循环与分支形态 | 部分通过 | 27 指标数值基准保持；LINE 的段归属、样式归属与端点条件仍未统一 |
| L1 数据与状态机 | 不通过整体验收 | 失败 INIT 已入队串写原例已修；公开 state 复用后实时更新被误拒，R8-01 |
| L2 参数与公共契约 | 部分通过 | 绑定范围及 BAR 两项部分覆盖修复成立；LINE 行样式和缺失值仍不一致 |
| L3 注册→计算→绘制/命中/编辑 | 部分通过 | 多轴、控点与层级基准保持；LINE 实际绘制仍有 R8-02/03 |
| V1 差分与门禁证据 | 部分通过 | 397 项正式测试通过；外部消费者探针仍复现 3 项问题 |
| U1 局部上游补丁同步 | 原 arc 演练限定通过 | 历史 before/after 和消费者基准保持；没有据此外推所有领域的升级成本 |

## 2. 基线与执行证据

| 检查 | 结果 | 证明范围 |
|---|---|---|
| core 正式 JVM 测试 | 222 项，失败/错误/跳过均 0 | 当前正式测试覆盖的行为 |
| Compose 正式 JVM 测试 | 175 项，失败/错误/跳过均 0 | 当前正式测试覆盖的行为，共计 397 项 |
| 外部 `audit.EighthAuditTest` | 64 项，失败/错误/跳过均 0 | 61 项基准/修复确认；3 项以差异断言成功复现本报告问题 |
| 第七轮原差异探针 | 5 项全部修复 | 已入队推送、绑定范围、height-only、width-only、混合 LINE 桥线 |
| 引擎提交门禁补验 | Update/Append/Prepend 均返回 `SESSION_MISMATCH` | 失败归属拒绝覆盖三个数据入口；新合法 epoch 的 C 更新到 `[300,301]` |
| TS oracle 重生成 | 27 份 JSON，解析后与仓库 fixture 全部一致 | 固定上游真实 calc 与共同输入基准 |
| 官方快照比对 | 官方 155、本地 155；修改/缺失/新增均 0 | `third_party/klinechart-v10.1.0/src` 来源完整 |
| 台账检查 | 通过，313 行 | 七面覆盖、格式、签名和引用存在性；不证明运行语义 |
| 真实 arc 补丁复验 | 通过 | 历史源码端点 false、当前 true；实际命中消费者及负角跨度基准保持 |

正式测试先清理结果并执行，计数保存后才加载外部探针。外部探针中的差异断言没有写回正式测试，也没有将“错误成功复现”计为健康行为。

```sh
./gradlew :lkanvas-core:cleanTest :lkanvas-compose:cleanTestDebugUnitTest \
  :lkanvas-core:test :lkanvas-compose:testDebugUnitTest \
  --no-build-cache --console=plain

./gradlew -I /tmp/lkanvas-audit-round8-20261031/audit.init.gradle \
  :lkanvas-compose:testDebugUnitTest --tests 'audit.EighthAuditTest' \
  --no-build-cache --console=plain

python3 -B tools/alignment/check.py
```

复现材料：[正式测试计数](/tmp/lkanvas-audit-round8-20261031/baseline-results.json)、[外部探针源码](/tmp/lkanvas-audit-round8-20261031/src/audit/EighthAuditTest.kt)、[结果与完整方法清单](/tmp/lkanvas-audit-round8-20261031/probe-results.json)、[快照比对](/tmp/lkanvas-audit-round8-20261031/snapshot.json)、[指标 oracle 生成器](/tmp/lkanvas-audit-round8-20261031/gen-fixtures.mts)、[指标 oracle 比对](/tmp/lkanvas-audit-round8-20261031/oracle-results.json)、[LINE 上游辅助验证](/tmp/lkanvas-audit-round8-20261031/line-oracle.mts)、[LINE 辅助输出](/tmp/lkanvas-audit-round8-20261031/line-oracle.json)、[台账输出](/tmp/lkanvas-audit-round8-20261031/alignment.log)。临时目录可能被清理，下文保留关键输入、预期与实测。

### 上游当前状态

本次在线查询的 main 仍为 `66dd45b31e93b57a4c75a3e0b98bc2a8393df36e`，比固定基线 ahead 3；差异为两个 README 和 `src/extension/figure/arc.ts`，相对第七轮没有新源码增量。参见[官方固定 SHA 比较](https://github.com/klinecharts/KLineChart/compare/d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df...66dd45b31e93b57a4c75a3e0b98bc2a8393df36e)。

因此，固定来源与已知 arc 增量的局部适配证据保持；这不足以证明整个 Compose 实现的全部结构、逻辑已经对齐。

## 3. 第七轮 R7-01…R7-04 逐项复核

| 原发现 | 本轮实测及结构判断 | 结论 |
|---|---|---|
| R7-01：失败 INIT 已入队推送串写 A | B Reset 失败后立即首推，A 保持 `[100]`；引擎在实际提交点检查 initEpoch，三个后续数据入口均有门禁；合法首推、旧失败后 C、ABA、重试基准保持 | 原复现已修；身份与水位跨生命周期不一致引入 R8-01，领域尚未整体关闭 |
| R7-02：绑定轴忽略模板范围 | VOL 恒值 100 的绑定轴范围由 `(100,100)` 改为 `(0,100)`，基线 y=561，落在 pane 481…561 内；逐实例、独立轴投影基准保持 | 本轮覆盖范围内关闭 |
| R7-03：BAR 部分覆盖默认几何 | height-only 的 top=481，width-only 的 left=-1；完整四字段、无 attrs、top/bottom 及非零 paneTop 基准保持 | 本轮覆盖范围内关闭 |
| R7-04：混合 LINE 自定义终点桥接 | 仅第 0 行覆盖时，自定义路径 1 次，默认数组仅 `[12,110,20,120]`，无 `(500,600)→(12,110)`；默认→覆盖→默认的正式测试也通过 | 桥线原复现关闭；LINE 行段契约仍有 R8-02/03 |

结构证据：[引擎拒绝水位与提交门禁](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/compute/ChartEngine.kt:242)、[绑定轴约束聚合](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:921)、[BAR 有效宽度先行](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:754)、[默认 y 不再重复平移](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:761)、[LINE 自定义段断开默认链](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:642)。

“关闭”对应明确发现及本次覆盖范围，不是该领域所有配置、异常路径的穷举证明。

## 4. 必须修复的剩余发现

### R8-01 · P1：复用 ChartState 后，旧拒绝水位拦住新门面的合法更新

**用途与契约：** `dispose(oldChart)` 后把保存的 `ChartState` 再传给 `LKanvasChart.init(state)`。这是公开入口，`destroy` 注释明确声明 state 可复用，不能按不支持的用法排除。

**输入与实测：** 原门面 A 成功加载 close=100；切 B，B 的 INIT 因非法 OHLC 失败。保存 state、dispose 原门面，以同一 state 创建新门面 C。C 的合法 INIT 成功，快照 key=C、数据 `[300]`；随后 C 推送 close=301，排空后仍为 `[300]`，预期 `[300,301]`。本探针只使用公开配置、生命周期及 loader 回调，没有改动内部字段。

**结构根因：** `dataEpoch` 属于门面实例，重建后从 0 开始，C 首次 INIT 为 1；`rejectedInitWatermark` 属于保存的 state 内引擎，B 留下的水位仍为 2。新 C 的 Reset 可以成功，但其 epoch=1 的 Upsert 被旧水位以 `SESSION_MISMATCH` 拒绝。Append/Prepend 使用相同门禁，也受同一生命周期错配影响；本次公开链路实测的是实时更新。

这是本轮提交门禁引入的回归。将最终拒绝判断移入引擎是正确方向，但身份生成者与水位持有者必须具有一致的生命周期，不能只在单个门面内保证单调。

**证据：** [公开 init/dispose](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:43)、[state 可复用契约](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:568)、[门面私有 epoch](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:473)、[state 持有引擎](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:53)、[引擎持久拒绝水位](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/compute/ChartEngine.kt:242)、[新请求被旧水位拒绝](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/compute/ChartEngine.kt:264)。

**最小处理方向：** 在现有 state/引擎边界统一提交身份的生命周期，或明确区分不同门面会话的身份空间。保留失败 INIT 的已入队动作拒绝、合法同步首推、旧失败不干扰新 C、ABA 和重试基准；不能仅清空水位而重新放行旧门面的在途动作。

**不处理的影响：** 图表复用状态后，初始数据看似恢复，实时行情却静默停更；反复重试是否恢复取决于旧水位大小。不能用强制丢弃整个 state 绕过已有公开契约。

### R8-02 · P2：LINE 几何段属于当前行，样式却仍取后一行

**用途与契约：** 行级 styles 根据当前指标数据决定线宽或颜色。上游同一次 `eachFigures(dataIndex)` 生成该行 styles，并用于该行的 `[current,next]` 默认段。

**输入与实测：** 三行坐标 `(4,100)`、`(12,110)`、`(20,120)`；第 0 行线宽 2，第 1/2 行线宽 4。预期段 P0→P1 用行 0 的宽 2，P1→P2 用行 1 的宽 4。本地实际 styles 回调行号为 `[1,2]`，两段被合到同一宽度组，一次 native drawLines 提交 8 个坐标数；正确样式归属应为 `[0,1]`，在此输入下为两个宽度组。

**逻辑根因：** 本轮将默认段 `(i-1,i)` 的几何归属明确为 row i-1，但实际落段仍执行 `styleOverrideOf(key,i)`。因此几何修复未延伸到同一行的样式消费者；自定义段却使用其自身行样式，两条路径继续不同。

**证据：** [默认段样式取后行](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:608)、[自定义段取本行样式](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:636)、[上游 styles 取 dataIndex](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/component/Indicator.ts:318)、[上游同次调用登记段与 styles](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:194)。

**最小处理方向：** 默认段与自定义段使用同一段归属规则取得几何和 styles，再按结果批量提交；保留已有批量优化，无需恢复逐点低效绘制。样式回调读取的 row/value/candles 也应对应段所属行。

**不处理的影响：** 线宽/颜色在转折处错后一行，混合默认和覆盖段时样式含义不同；后续上游行级补丁仍不能直接对应本地循环。

### R8-03 · P2：自定义 LINE 段没有保持上游“下一点有效”的门禁

**用途与契约：** 上游先检查当前和下一行均有有效数值，再登记该行 LINE 段；coordinates 覆盖位于这个条件之内。指标预热或缺失输出用 NaN 表示，属于已有支持场景。

**输入与实测：** 行值 `[100,110,NaN,130,140]`，仅行 1 返回自定义点列 `[(40,10),(60,20)]`。上游应仅登记行 0 和行 3 的默认段；行 1 下一点缺失，其自定义段也不得登记。本地除正确的两个默认段外，仍调用了 1 次自定义 `drawPath`。

```text
本地默认 drawLines: [4,100,12,110, 28,130,36,140]
本地额外 drawPath: 1 次（来自行 1）
上游登记行号: [0,3]，无行 1 自定义段
```

**控制流根因：** 本地循环只过滤当前 v 的 NaN，然后直接执行自定义 coordinates 分支，没有在该分支保留下一点有效性条件。第 2 行稍后清空连续状态，只能避免默认线跨断，不能撤销第 1 行已经画出的自定义段。

**证据：** [本地仅过滤当前值](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:595)、[非空自定义点列直接绘制](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:631)、[上游当前与下一点共同门禁](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:194)。当前正式测试 [NaN 组合断言](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/test/kotlin/io/lkanvas/chart/parity/Parity15FigureAttrsTest.kt:294)仍要求该自定义路径出现，不能作为与上游一致的证明。

**最小处理方向：** 在实际 LINE 行段入口统一段存在条件、默认/覆盖几何和样式归属，保留 NaN 断开及上一条合法默认段。补充缺失下一点与末端边界，不能仅靠 `prevWasFinite=false` 处理已经提交的路径。

**不处理的影响：** 缺失值附近仍出现上游不会登记的额外图形；正式测试会把这项差异固定下来。

## 5. 验证方法与证据边界

| 外部方法 | 主类型 | 结果 |
|---|---|---|
| `fixedRejectedInitDropsAlreadyQueuedPush` | 修复确认 | A 保持 `[100]` |
| `fixedBoundAxisIncludesDeclaredMinimum` | 修复确认 | 绑定范围 `(0,100)`，基线 y=561 |
| `fixedHeightOnlyBarAttrsKeepPaneOffset` | 修复确认 | top=481 |
| `fixedWidthOnlyBarAttrsCenterCorrectly` | 修复确认 | left=-1 |
| `fixedMixedLineAttrsHaveNoCustomBridge` | 修复确认 | 默认段仅 `[12,110,20,120]` |
| `fixedRejectedInitEngineGateCoversAllQueuedDataOperations` | 门禁补验 | 三入口均拒绝失败归属，C 新 epoch 更新正常 |
| `remainingRejectedWatermarkSurvivesFacadeRecreation` | 差异复现 R8-01 | 新 C 推送后仍为 `[300]` |
| `remainingDefaultLineSegmentsUseNextRowStyles` | 差异复现 R8-02 | 回调行 `[1,2]`，两段同组 |
| `remainingCustomLineStillDrawsWithoutFiniteNextRow` | 差异复现 R8-03 | 下一点 NaN 时仍多画自定义路径 |

原有 55 项确认覆盖分页/订阅、INIT 首推/重试、旧回调/ABA、样式 patch、动画、pane 迁移和布局、相对/绝对转换、多轴/叠加范围、figure 上下文、覆盖物格式、裁剪/平移、层级命中和历史 arc 补丁。与上表 6 项确认、3 项差异合计 64 项；方法和输出完整保存在结果 JSON 中。

LINE 辅助 TS 验证直接调用固定上游 `eachFigures` 与 `isNumber`；端点门禁和默认坐标规则摘录自 `IndicatorView.ts:194–200`，并非完整浏览器渲染。它确认段所属行、styles 和缺失条件，再与本地实际消费者的回调、批量分组及 drawPath 次数对照。未将这个小型摘录称为两端完整 UI E2E。

正式测试与本次绘制探针使用 Android JVM 默认返回值及录制 DrawScope/native Canvas。所证明的是实际绘制调用、几何参数、分组和裁剪/平移上下文，不是 GPU 栅格、真实 Path 像素或设备触摸。空 coordinates 的上游合并路径还存在需要独立核查的边界，本轮未将其列为验收发现，也不要求复制上游异常。

当前台账解析 313 行：已声明 237、行为通过 59、已接线 2、不适用 15。R7 编号已纳入校验，四行引用存在且能解析，见[第七轮追踪](/Users/pauldeman/Documents/ai_workspace/KKLineChart/docs/alignment/gap-matrix.md:473)。这些证据支持原复现修复，不支持将“行为通过”解释为完整组合验收；本次未修改既有台账。

## 6. 当前目标收敛情况

**从第一轮到第八轮，净收敛持续成立；第八轮解决了上一轮指出的共同入口问题，但仍有新增生命周期回归，尚未形成严格单调改善。**

| 领域 | 当前收敛证据 | 剩余差距 |
|---|---|---|
| 来源与指标算法 | 固定快照、映射、27 个真实 calc 共同输入结果保持 | 未穷举全部参数、缺失及异常边界 |
| 数据链路 | 失败已入队推送不再污染旧快照，拒绝判断真正进入引擎；三个后续数据入口覆盖 | 身份与水位跨门面/state 生命周期错配，R8-01 |
| pane/轴/范围 | 逐实例投影、独立绑定轴、默认与绑定模板约束基准均通过 | 本轮覆盖中未重现原范围问题；全配置和设备标签未穷举 |
| BAR 几何 | 完整/部分/默认覆盖及 pane 平移依赖顺序修复 | 本轮原发现关闭；不代表所有几何类型认证完成 |
| LINE 几何与样式 | 伪桥线消除，默认和覆盖段的几何归属更接近上游 | styles 归属与端点条件仍分叉，R8-02/03 |
| 覆盖物与交互 | 控点裁剪、平移、固定层级编辑、单一 z 命中基准保持 | 最终设备绘制与触摸验证尚未执行 |
| 未来同步成本 | 修改仍集中在有限领域入口，真实 arc 补丁复验保持 | 未证明任意后续补丁都能有限迁移，不能承诺以后完全不需较大适配 |

历轮发现数量为 **16→14→11→9→7→6→4→3**。第六轮为 4 项 P1，第七轮和本轮均为 1 项 P1。检查粒度及组合深度不同，这组数字只能用于历史索引，不能换算完成率或线性预测归零时间。

本轮比“4→3”更有价值的收敛证据，是 60 项历史探针全部按正确预期通过，范围与 BAR 原问题关闭，提交门禁已进入最终消费者。未完成的证据同样明确：P1 并未消失，而是从原来的数据串写变为同一身份设计在跨生命周期时误拒合法数据；LINE 仍保留“几何归前行、样式取后行”的两套规则。

这些问题仍可在现有边界内局部修正，没有证据要求重写整个库。下一步应统一身份所属生命周期与 LINE 行段定义，继续堆叠单例补丁或只改台账状态不会完成原验收。

### 预计还需几轮

冻结原功能范围，**建议以再 2 轮为目标、预留第 3 轮返修：1 轮集中根因修复，1 轮独立验收，必要时再返修。** 这是工程估算，置信度中等；本轮仍产生 P1 回归，不能因条目下降就承诺只剩一轮或必然按时归零。

下一轮应固定以下验收条件：

1. 同一 state 在失败前后、dispose/init 前后及合法/非法 INIT/首推/重试组合中，身份和最终提交规则一致；继续保留已入队拒绝、旧失败后新 C 和 ABA 基准。
2. LINE 用同一所属行决定段是否存在、默认或覆盖几何、行级样式；缺失当前/下一点、末端、默认→覆盖→默认组合保持上游原规则。
3. 本轮 61 项确认保持，3 项差异转为正确行为回归；独立复核相关共同入口，并完成必要的设备绘制/触摸验证。
4. 不撤销本轮已成立的范围、BAR、控点和层级修复，不追加新能力作为验收前置条件。

若上述根因能一次消除，现有证据支持近期进入最终验收；若再次出现身份生命周期分裂或行段规则只修一半，就无法可靠承诺剩余轮数。原标准没有变化，新增用例均来自既有公开契约。

## 7. 未检查范围与最终判断

本轮未执行真机/模拟器截图、完整触摸 E2E、TalkBack、Macrobenchmark、R8、Maven 发布/consumer、图片编码集成，未重新认证已有性能数据；未逐行穷举整个 API 面和所有图形/样式/异常输入。构建中的既有工具链警告没有作为本轮对齐发现扩展处理。

**本次仅新增报告，没有实施修复。第八轮总体继续接近目标，完整验收仍未通过。当前剩余 1 项 P1、2 项 P2；最高风险是失败后复用 ChartState 导致新会话实时更新静默停滞。**
