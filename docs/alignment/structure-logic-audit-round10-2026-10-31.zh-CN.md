# Compose 工程与 KLineChart 结构、逻辑一致性第十轮审计及目标收敛情况

- 日期：2026-10-31（Asia/Shanghai）。
- 审计对象：`22e7095846e753cc215353a06c6a5223c936e6fd`，版本 `0.14.0`。
- 对比起点：第九轮对象 `7357d0cb2bffcdc10f79ef9147afc0e3874703e9`（`0.13.0`）。
- 固定上游：KLineChart v10.1.0，`d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df`。
- 标准不变：沿用 S1/S2/S3/L1/L2/L3/V1/U1，按职责、数据及参数契约、主循环、条件与调用顺序、真实消费者、共同输入及有限补丁可对应性验收；不按文本相似率、标点或测试数量评分。
- 工程用途与负载：纯 Kotlin 核心加 Compose 原生图表；按单图表、异步行情、多指标/轴/覆盖物、公开 state 复用、历史滚动和行级动态样式审计。
- 范围：复核 R9-01/02，追踪 LINE 边界与提交顺序，保留既有数据、轴、覆盖物和补丁基准；不追加功能。
- 修改边界：开始时工作区干净；本次只新增报告，未修改实现、正式测试、构建配置、上游快照或已有文档。验证材料位于仓库外临时目录。

## 1. 审计结论

**第十轮继续收敛，第九轮三个原始差异用例均已修复；本轮覆盖中未确认 P1，仍有 1 项 P2，完整结构与逻辑一致性验收尚未通过。**

已成立的修复包括：下一点从完整结果列读取，末个可见行的合法覆盖段不再消失；默认末行段也能延伸到窗口外的有效下一点；遇到自定义段时先提交此前默认批次，原红色默认段与蓝色覆盖段的顺序恢复正确。身份生命周期、多轴、范围、BAR、控点、层级和真实 arc 补丁等基准保持。

剩余 **R10-01** 是原 R9-02 保序契约尚未完整关闭：默认段样式 A→B→A 时，`segFor` 会复用较早的 A 组，实际提交行序变成 **0→2→1**；上游只合并相邻且同样式的段，保持 **0→1→2**。当前实现解决了默认段与覆盖段之间的顺序，仍没有保留默认段内部的样式变化顺序。

这属于第九轮已经明确要求的“不要跨覆盖段或样式变化全局重排”，不是新增功能或改变验收标准。可以确认原样例修复，也必须保留该一般性规则尚未满足的判断。

| 验收维度 | 本轮判断 | 依据 |
|---|---|---|
| S1 来源与符号可追溯 | 本轮重点范围通过 | 155 个官方源码快照一致；映射和台账引用保持，R8-03 原可见区表述已纠偏；状态仍需限定验证范围 |
| S2 职责与依赖结构 | 身份链路通过，LINE 批次仍部分通过 | 共同提交入口保持；按全局样式找旧批次仍不同于上游连续合并 |
| S3 循环、条件与调用顺序 | 部分通过 | 数据/可见边界和覆盖前提交正确；A→B→A 的默认段仍重排 |
| L1 数据与状态机 | 本轮覆盖通过 | 失败已入队门禁、同步首推、旧回调/ABA、重试、连续 state 复用保持 |
| L2 参数与公共契约 | 部分通过 | 行级样式取值正确，但提交顺序未完整保留 |
| L3 注册→计算→绘制/命中/编辑 | 部分通过 | 原 pane/轴/覆盖物链保持；实际 LINE 消费者复现 R10-01 |
| V1 差分与门禁证据 | 继续改善，尚未全通过 | 404 项正式测试通过；本地实际消费者与上游真实 View 仍有一项顺序差异 |
| U1 局部源码补丁同步 | 原 arc 演练限定通过 | 历史 before/after 和实际消费者复验保持；未外推所有领域 |

## 2. 基线和执行结果

| 检查 | 本轮结果 | 证明范围 |
|---|---|---|
| core 正式 JVM 测试 | 222 项，失败/错误/跳过均 0 | 当前正式测试覆盖 |
| Compose 正式 JVM 测试 | 182 项，失败/错误/跳过均 0 | 正式测试合计 404 项 |
| 外部 `audit.TenthAuditTest` | 71 项，失败/错误/跳过均 0 | 70 项确认基准/修复，1 项以差异断言成功复现 R10-01 |
| 第九轮三个原差异用例 | 全部修复 | 有限可见范围、真实动态 pane、默认/覆盖重合顺序 |
| 上游真实 View 录制 | 7 组输入 | 真实 IndicatorView/ChildrenView、准备、行级样式、合并和 figure draw 路径；宿主与绘制出口录制 |
| TS 指标 oracle 重生成 | 27 份 JSON，与仓库 fixture 解析后全部一致 | 固定上游真实 calc 与共同输入 |
| 官方快照比对 | 官方 155、本地 155；修改/缺失/新增均 0 | `third_party/klinechart-v10.1.0/src` 来源完整 |
| 台账检查 | 通过，318 行 | 七面覆盖、格式、签名和引用存在性；不证明运行语义 |
| 真实 arc 补丁复验 | 通过 | 历史原端点 false、当前 true；实际命中消费者和负角跨度基准保持 |

正式测试先清理结果后运行并保存计数，随后才加载外部探针。外部差异断言仅用于复现，没有写回正式测试去固化错误行为，也没有计作健康行为。

```sh
./gradlew :lkanvas-core:cleanTest :lkanvas-compose:cleanTestDebugUnitTest \
  :lkanvas-core:test :lkanvas-compose:testDebugUnitTest \
  --no-build-cache --console=plain

./gradlew -I /tmp/lkanvas-audit-round10-20261031/audit.init.gradle \
  :lkanvas-compose:testDebugUnitTest --tests 'audit.TenthAuditTest' \
  --no-build-cache --console=plain

tools/oracle/node_modules/.bin/tsx /tmp/lkanvas-audit-round10-20261031/view-oracle.mts
python3 -B tools/alignment/check.py
```

材料：[正式测试计数](/tmp/lkanvas-audit-round10-20261031/baseline-results.json)、[外部探针源码](/tmp/lkanvas-audit-round10-20261031/src/audit/TenthAuditTest.kt)、[结果与完整方法清单](/tmp/lkanvas-audit-round10-20261031/probe-results.json)、[上游 View 脚本](/tmp/lkanvas-audit-round10-20261031/view-oracle.mts)、[上游 View 输出](/tmp/lkanvas-audit-round10-20261031/view-oracle.json)、[快照比对](/tmp/lkanvas-audit-round10-20261031/snapshot.json)、[指标生成器](/tmp/lkanvas-audit-round10-20261031/gen-fixtures.mts)、[指标比对](/tmp/lkanvas-audit-round10-20261031/oracle-results.json)、[台账输出](/tmp/lkanvas-audit-round10-20261031/alignment.log)。临时目录可能被清理，下文保留关键输入、预期及实测。

### 当前上游

本次在线查询 main 仍为 `66dd45b31e93b57a4c75a3e0b98bc2a8393df36e`，相对固定基线 ahead 3；差异仍是两个 README 和 `src/extension/figure/arc.ts`，相对第九轮没有新源码增量。参见[官方固定 SHA 比较](https://github.com/klinecharts/KLineChart/compare/d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df...66dd45b31e93b57a4c75a3e0b98bc2a8393df36e)。

固定来源和已知 arc 增量的局部对应证据保持；全部实现是否对齐另按结构与行为判断。

## 3. 第九轮 R9-01/02 复核

| 原发现 | 本轮实测及控制流变化 | 结论 |
|---|---|---|
| R9-01：末个可见行合法覆盖段被跳过 | `[100,110,120]` 遍历 `[0,1]` 时覆盖路径为 1，扩大范围仍为 1；真实动态 pane 的 range 52…128 已调用行 128 attrs，路径为 1；下一点取完整列 | 本轮覆盖范围内关闭 |
| R9-01 邻近默认段边界 | 无 attrs 时，有限窗口 `[0,1]` 和全范围 `[0,2]` 都产生 P0→P1、P1→P2；真正数据末端不越界、NaN 基准保持 | 正确补齐末行默认段，应保留 |
| R9-02：默认段晚于覆盖段提交 | 原重合用例顺序为 `[default-red,custom-blue]`；flush 后清组，后续默认段能继续产生 | 原重合用例修复；一般行序仍有 R10-01，未完整关闭 |

证据：[完整结果列下一点](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:645)、[覆盖前提交默认批次](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:656)、[默认末行段延伸](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:681)、[末尾提交](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:695)。

**本轮对旧探针的必要校准：** 一项历史桥线探针使用了 200 行完整结果但只遍历 0…2，旧预期只检查到 P2，遗漏了合法的 P2→P3。重新执行上游相同“完整结果＋可见行”协议后，确认新增末行默认段属于正确修复，本轮已补上预期。此前这个旧预期过窄，不能据它将合法延伸判为回归。

实际 pane 探针的中止点也从首个 native 批次改为覆盖路径绘制后：当前修复会先 flush，若仍在首批中止，会人为阻止随后正确的覆盖段。两项调整均位于外部临时探针，未改变正式测试或原验收目标。

## 4. 剩余发现

### R10-01 · P2：默认段 A→B→A 复用早先样式组，提交行序仍被重排

**用途与契约：** LINE 行级 styles 可以随当前指标数据变化。上游按行登记段，只合并与最后批次连续、样式相同的段；A 在 B 之后再次出现时，应建立后续 A 批次，保留原行序。

**共同输入：** 四行值 `[100,110,100,110]`，坐标为 `(4,100)`、`(12,110)`、`(20,100)`、`(28,110)`。无 coordinates 覆盖；段所属行 0/1/2 的颜色为红/蓝/红，线宽均为 6，标记关闭。

| 真实消费者 | 实际分组及提交 |
|---|---|
| 上游 IndicatorView | 红色行 0、蓝色行 1、红色行 2，三个连续批次，顺序 `[0,1,2]` |
| 本地 drawInstanceFigures→drawLinePlots | 第一个批次合并行 0/2，第二批才交行 1，顺序 `[0,2,1]` |

本地 styles 回调本身正确执行 `[0,1,2]`，差异发生在分组和实际提交，而非取值错行。native 数组实测为：

```text
批次 A: [4,100,12,110, 20,100,28,110]  # 行 0 和行 2
批次 B: [12,110,20,100]                # 行 1
```

**结构根因：** `segFor` 遍历全部 `groups.keys` 找相同 color/width，因此后来的 A 返回最早的 A 对象。`LinkedHashMap` 只保留样式首次出现顺序，无法保留段的行序。新增的 `flushGroups()` 在自定义段前和循环末尾执行；本例没有自定义段，样式变化时也不 flush，故仍被全局样式分组重排。

上游合并循环只比较 `mergeLines` 最后一项，B 已经成为最后项时，新的 A 不会回填较早 A。因此，第九轮“默认段与覆盖段保序”的原样例修复成立，但“提交顺序等于行登记顺序”的一般表述仍不成立。

**证据：** [查找全部样式组并复用](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:585)、[按组首次出现顺序提交](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:592)、[仅覆盖前主动 flush](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:656)、[上游仅比较最后合并项](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:232)、[上游按连续合并结果提交](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:254)。

**最小处理方向：** 只复用最近的连续同样式批次；遇到不同样式便结束该批次，之后 A 再出现应是新的 A 批次。默认主循环、末行补段和自定义段继续使用共同顺序；保留覆盖前 flush 和连续同样式一次提交的优化，无需移除批量绘制或重写渲染器。

**不处理的影响：** 动态样式折返时，最终绘制顺序不同于上游，段交界的重叠覆盖关系无法保证一致，后续按行样式补丁仍要适配本地独有的全局重排。本轮确认的是实际调用/提交顺序差异，未将其表述为已测得真机像素差异；按原 S2/S3/L3 标准，该差异仍阻止完整验收。

## 5. 验证与台账边界

| 外部方法 | 主类型 | 结果 |
|---|---|---|
| `fixedVisibleRangeLastRowKeepsCustomSegment` | R9-01 修复确认 | 有限/扩大范围均保留覆盖路径 |
| `fixedDynamicPaneKeepsLastVisibleCustomSegment` | R9-01 实际消费者确认 | range 52…128、next=229、末行路径为 1 |
| `fixedMixedLineSubmissionKeepsOverlappingRowOrder` | R9-02 原复现确认 | 默认红色先提交，覆盖蓝色后绘制 |
| `fixedDefaultLastVisibleSegmentUsesCompleteResultColumn` | 邻近基准 | 默认有限/完整范围段集相同 |
| `fixedConsecutiveDefaultStyleStillUsesOneBatch` | 批量优化基准 | 三个连续同样式段仍一次提交 |
| `remainingReturningDefaultStyleReordersRowsAcrossInterveningStyle` | R10-01 差异复现 | 样式回调 `[0,1,2]`，实际提交 `[0,2,1]` |

上一轮 68 项在按上游完整结果协议校准后全部按正确预期通过；再加本轮 2 项基准、1 项差异，共 71 项，其中确认 70、差异 1。差异测试成功仅表示问题被稳定复现。

上游 View 录制共 7 组：有限范围覆盖段、扩大范围、覆盖重合顺序、所属行样式、缺失下一点、默认末行延伸、A→B→A 默认样式。执行真实上游 View/ChildrenView、figure 准备、样式和合并函数，宿主提供明确的 result、可见行与恒等轴，并记录 figure draw 的几何和样式；尚非完整浏览器 E2E。

本地录制真实 DrawScope/native Canvas 参数和调用顺序，Android JVM 默认返回值用于执行路径，未用 mocked Paint getter 证明色值或像素，也未据此认证 GPU 栅格、触摸或性能。

台账解析 318 行：已声明 237、行为通过 64、已接线 2、不适用 15。[R9 追踪](/Users/pauldeman/Documents/ai_workspace/KKLineChart/docs/alignment/gap-matrix.md:490)与 R8-03 表述纠偏均已落盘。R9-01 原发现支持关闭；R9-02 的“行为通过”支持原覆盖用例，不能外推为 A→B→A 的一般保序已经通过。check.py 只验证格式和引用，本次未修改台账。

## 6. 当前目标收敛情况

**净收敛继续成立，连续两轮在本次覆盖范围内未确认 P1；剩余问题从两个 LINE 差异收窄为一个默认样式批次规则。当前已接近最后局部修正与独立验收，但尚未完成。**

| 领域 | 当前收敛证据 | 剩余距离 |
|---|---|---|
| 来源与指标算法 | 官方快照、既有映射和 27 指标共同输入结果保持 | 未穷举所有参数和异常输入 |
| 数据状态机 | 失败提交门禁、合法首推、旧回调/ABA、重试、连续 state 复用保持 | 最终独立验收中继续保留；本轮覆盖未见 P1 回归 |
| pane/轴/范围 | 每实例投影、独立轴范围、默认/绑定模板约束保持 | 本轮未发现原范围问题复发 |
| BAR/覆盖物/交互 | 部分 attrs、平移、控点裁剪和层级基准保持 | 设备端证据仍待补 |
| LINE 边界 | 覆盖段和默认段均正确读取完整下一点 | R9-01 本轮范围内关闭 |
| LINE 提交顺序 | 默认→覆盖的原顺序修复，清组后继续绘制正确 | 默认样式 A→B→A 仍非连续分组，R10-01 |
| 后续同步 | 修改集中在有限领域入口，真实 View 对照可复用，arc 局部补丁保持 | 没有全库重写的证据；任意未来破坏性变更仍不能预先保证小改 |

历轮条目数为 **16→14→11→9→7→6→4→3→2→1**；最近四轮覆盖中 P1 数为 1、1、0、0。条目粒度和验证深度不同，不能换算完成率或机械推算归零时间。

更有价值的证据是身份状态机稳定、此前入口修复未复发、边界逻辑已真正统一到完整结果列，且剩余分歧能明确定位到 `segFor` 的全局复用。当前需要按连续行段修正这一规则；继续仅为单个覆盖用例加 flush，无法证明所有样式变化都保序。

### 还需多少轮

冻结原功能范围，**仍建议 1 轮局部修复＋1 轮独立最终验收，约 2 轮工作安排；不预设额外第 3 轮。** 本轮不是已经达标，而是把剩余要求收窄到可明确复验的一个规则。这是工程估算，不是通过承诺。

最终验收条件应固定为：

1. A→B→A 提交顺序为 `[0,1,2]`；连续 A→A→A 仍可一次批量提交，样式变化只形成连续批次，不回填早先组。
2. 本轮 70 项确认保持，1 项差异转为正确行为；默认→覆盖→默认、末可见行、数据末端/NaN、所属行样式同时保持。
3. 独立复核共同批次入口和既有身份状态机，不追加新能力，也不以台账状态替代运行证据。
4. 设备绘制/触摸证据单独补齐，作为尚缺的发布验证，不混入代码发现条数。

若修正保持这些基准且独立复审未引入新差异，即可在已声明范围内给出通过结论；若产生回归或设备检查暴露新问题，再安排返修。当前没有证据要求重新设计整个库。

## 7. 未检查范围与最终判断

本轮未执行真机/模拟器截图、完整触摸 E2E、TalkBack、Macrobenchmark、R8、Maven 发布/consumer、图片编码集成，未重新认证已有性能数据；未逐行穷举全部 API、样式、图形和异常输入。先前搁置的空 coordinates 上游合并边界未重新裁定，不新增为本轮验收要求。

**本次只新增审计报告，没有实施修复。第十轮继续接近目标；当前确认 1 项 P2，本轮覆盖中未确认 P1，完整结构与逻辑一致性验收仍未通过。已知主要代码风险是默认样式折返时段提交顺序与上游不同；设备端验证仍待补。**
