# Compose 工程与 KLineChart 结构、逻辑一致性复审报告

- 日期：2026-10-30，Asia/Shanghai。
- 审计提交：`cc9ad7e9a14db263316941c83281ef28fbcf0c96`，工程版本 `0.6.0`；开始审计时工作区干净。
- 对照提交：上轮审计对象 `3fd49ff104021efbca2b5bebf41fcae3b3b26aa2`。
- 固定上游基线：KLineChart v10.1.0，`d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df`。
- 标准：沿用上轮 S1/S2/S3/L1/L2/L3/V1/U1，主要判断结构与逻辑是否可逐项对应、上游补丁能否局部移植；不以函数同名、台账标记或测试数量替代验收。
- 范围：core 与 Compose 的单图表数据、指标、覆盖物、pane、轴、视口、事件及同步验证链。假设行情分页与实时推送并行、宿主会切品种和修改配置，这些都是既有公开契约中的正常操作。
- 本次只新增本报告，没有修改生产代码、仓库测试、配置、上游快照或原有台账；临时审计测试位于 `/tmp`。

## 1. 结论

**本轮有明显进步，但仍未达到原有“结构与逻辑一致、便于后续局部同步”的完整验收标准。不能确认 README 所述“16 项发现全部收口”。**

相较上次，不能再把工程简单判断为“只有同名门面”：27 个指标确实已有对应文件和独立 calc；数据段出现了可对应上游的 Store；全局指标、覆盖物注册已贯通；默认指标、批量删除、参数列数、禁用开关和 scale=1 的旧复现已经修复。

仍有两类阻断：

1. **逻辑链在组合场景下不成立。** 分页会让实时订阅失效；异步提交仍能把旧回调数据写到新品种；局部 Styles 修改会覆盖无关设置；pane/absolute 转换、动态 pane、多轴和十字线仍有具体契约差异。
2. **部分结构镜像只做到文件拆分。** 覆盖物行为仍集中在按名称分支的绘制/命中函数中，轴注册仍有两套表，pane 状态和查询不是统一来源。后续补丁在这些领域仍需跨模块重新解释。

| 验收维度 | 本次判定 | 主要依据 |
|---|---|---|
| S1 来源与符号可追溯 | 部分满足 | 固定基线可靠；structure-map 存在，但仍含未回填映射及不存在的测试名 |
| S2 职责与文件结构 | 部分满足 | 27 指标拆分是真实改善；覆盖物行为和 pane/轴执行模型仍非同构 |
| S3 算法/分支形态 | 指标明显改善，整体不通过 | 27/27 有 calc，抽查主循环可对应；覆盖物模板仍缺原模板生成图形逻辑 |
| L1 数据与状态机 | 不通过 | 新增 Store 接线后仍有实时订阅代次和异步落库问题 |
| L2 默认值/返回/过滤/参数 | 不通过 | 默认指标和批量删除已修；转换、布局、局部样式、Crosshair 可选字段仍不同 |
| L3 注册到计算/绘制/命中 | 部分满足 | 指标/覆盖物全局注册贯通；轴、figure 类型、pane 内渲染仍不完整 |
| V1 可重现差分与回归 | 部分满足 | 340 个现有测试通过；临时组合测试仍复现差异；部分 parity 固化了错误预期 |
| U1 小补丁同步能力 | 有映射进展，尚不足以验收 | 真实历史补丁来源已核实，但演练记录缺少修复前失败、应用补丁后通过的证据 |

**版本基线与最新 main 分开判断：** 本地 v10.1.0 快照仍可靠；但本次查询发现 main 已有新的 arc.ts 源码修复，不能继续沿用“上游只有 README 改动”的当前结论。该修复见 R14，不用它替代本报告对固定基线的审计。

## 2. 本次核验结果与已有改进

### 2.1 实际执行结果

| 检查 | 结果 | 证明范围 |
|---|---|---|
| 官方固定提交归档 vs 本地 `third_party/.../src` | 155/155 文件逐字节相同，缺失/新增/修改均为 0 | 固定上游对照源未被本地修改 |
| 重新生成 TS 指标样本到临时目录 | 27/27 与仓库 JSON 内容一致 | oracle 样本仍可再生 |
| core 单元测试，禁用构建缓存后实际执行 | 220 项，失败/错误/跳过均为 0 | 现有 core 断言 |
| Compose debug 单元测试，同上 | 120 项，失败/错误/跳过均为 0 | 现有 Compose/门面断言 |
| 临时复审测试 | 19 项均通过：4 项确认旧问题修复，15 项确认剩余差异 | 见 §6；“复现测试通过”不代表上游验收通过 |
| `tools/alignment/check.py` v4 | 通过 | 状态枚举、文档覆盖和部分文件引用检查，不执行行为测试 |
| 上游 main compare | 3 个提交，涉及 2 个 README 与 1 个源码文件 | 新增 arc 命中修复需登记评估 |

原有测试命令：

```sh
./gradlew :lkanvas-core:cleanTest :lkanvas-compose:cleanTestDebugUnitTest \
  :lkanvas-core:test :lkanvas-compose:testDebugUnitTest \
  --no-build-cache --console=plain
```

生产源码规模：core 81 个 Kotlin 文件、6,783 行；Compose 25 个文件、6,514 行。文件拆分和代码增加不单独作为通过或扣分依据。

### 2.2 已确认的修复

- **27 个指标均拆到 `extension/indicator/`，并且 27 个都覆写 calc。** MA 恢复了“输出后减滑出值”的顺序；全量引擎执行 calc，Scratch 用于增量边界状态。现有 oracle 和双路径测试通过。这解决了上轮“没有可对照全量入口”的主要问题。见 [MovingAverage.kt:33](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/extension/indicator/MovingAverage.kt:33)、[IndicatorEngine.kt:282](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/indicator/IndicatorEngine.kt:282)。
- **指标输出列已按实例参数生成。** 五个周期的 MA 能输出五列，没有再出现原来的 ma5 错误。
- **指标/覆盖物全局注册已进入创建查询。** 本轮临时用例确认，在既有图表中注册自定义指标后也能创建。
- **初始不再预置 MA/VOL/MACD，批量删除不再保留内置实例。** 临时用例确认创建两个 RSI 后无参删除可清空实例及动态 pane。
- **程序滚动/缩放开关和 scale=1 的旧问题已修复。** 两个开关在 ChartState 共用入口检查；zoomAtDataIndex 不再预先居中。
- **交互式覆盖物创建配置已应用。** paneId、groupId、lock、visible 的上轮复现已通过。
- **主轴 log 转换、reverse、格式链与部分模板回调已接线。** 这些是真实新增的消费者；以下发现针对剩余路径，不能解读为上述工作没有发生。
- **原 meta 测试的自证问题已修。** shouldOhlc/shouldFormatBigNumber 现在读取 Kotlin 属性比较，函数型 figures 也开始比较顺序。见 [IndicatorMetaDiffTest.kt:146](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/test/kotlin/io/lkanvas/core/indicator/builtin/IndicatorMetaDiffTest.kt:146)。

## 3. 上轮 16 项逐项复核

“已修复”仅指原发现所述问题在本次检查范围内已解决；不表示其所在模块的所有分支均已验收。“部分修复”会列出当前余项，而非原样重复旧结论。

| 原编号 | 原问题 | 本次状态 | 实现进展与剩余证据 |
|---|---|---|---|
| SLA-001 | 工具只验台账，不验实现 | 部分修复 | 新增状态和文件引用检查，但跳过 SLA 行证据检查，仍不执行测试；R12 |
| SLA-002 | 只有 Scratch，无规范 calc | 已修复（所检范围） | 27 文件/27 calc，全量调用 calc，oracle/双路径通过；仍需按具体上游补丁维护两条路径 |
| SLA-003 | 固定 pane/PRICE 限制 | 部分修复 | 新增动态 pane，main 移除 PRICE 限制；固定副图、柱图类型、可见性与 pane 查询仍有缺口；R07/R08 |
| SLA-004 | 全局注册与创建断开 | 部分修复 | 指标/覆盖物已贯通；公开轴注册与实际构造仍不同表；R06 |
| SLA-005 | 无分页入口/more 方向错 | 部分修复 | 同方向 more 已正确、手势范围变化能触发请求；init/数据提交后的边界链缺失，分页又使实时失效；R01/R03 |
| SLA-006 | 切会话串数据、未退订 | 部分修复 | 退订与加载重置顺序、代次检查已增加；异步提交阶段未绑定/复核上下文；R02 |
| SLA-007 | CRUD 默认/批量/覆写不同 | 部分修复 | 默认实例、默认 isStack、全删已修；override paneId 不维护 pane/轴生命周期；R07 |
| SLA-008 | 动态参数列未重建 | 已修复 | plotsFor(params) 分配列，五周期 MA 临时用例通过 |
| SLA-009 | 显式恢复默认值被吞 | 部分修复 | 不再跳过默认值，但完整 Styles 对象仍无属性存在信息，修成了“无关字段被默认值覆盖”；R04 |
| SLA-010 | 多轴只存记录 | 部分修复 | 主 pane 额外轴有绘制路径；公开注册、模板作用域、副 pane 轴仍不一致；R06 |
| SLA-011 | 开关/格式/Styles 无消费者 | 部分修复 | 共用滚动缩放入口、格式链和 candle.type 已接线；XAxis、轴区/锚点等仍有独立状态路径；R06/R11 |
| SLA-012 | 转换与绘制轴不同源 | 部分修复 | 主轴 log/普通映射改善；pane filter 被忽略，absolute 被解释为索引开关；R05 |
| SLA-013 | 模板回调不执行、创建丢配置 | 部分修复 | 创建配置、自定义模板基础钩子已接；内置同名替换、轴图形钩子、覆盖物所属 pane 仍不完整；R09 |
| SLA-014 | 视口/API 七子项不一致 | 部分修复 | scale=1、独立 offset、to 钳制有改进；索引/缩放动画为空或忽略，layout 仍是自定义缩减模型；R07/R11 |
| SLA-015 | executeAction/payload | 部分修复 | 嵌套 payload、写状态和抑制回发已修；null 和仅 x 的 Crosshair 仍不符合契约；R10 |
| SLA-016 | 验证与同步证据不足 | 部分修复 | meta 断言改善，新增 parity-4~8 和历史补丁映射；组合场景、真正双端差分与补丁前后证据仍不足；R12/R13 |

## 4. 必须修复的逻辑与结构阻断

### R01 · P1：一次历史分页就会永久停掉当前实时订阅

**这段代码负责什么：** Store 使用 generation 丢弃旧会话请求和推送。

**问题与复现：** processDataLoad 每次请求都 `++generation`，实时订阅却一直捕获 INIT 请求的 gen。首次 FORWARD/BACKWARD 请求一发出，同品种现有订阅的所有推送都会被 `gen != generation` 丢弃；分页完成也没有重新订阅。临时用例中，200 根数据后触发 FORWARD，再发第 201 根，数据仍为 200 根。

**证据：** [Store.kt:128](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/store/Store.kt:128)、[Store.kt:150](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/store/Store.kt:150)。上游分页不会替换或废弃同会话实时订阅：[上游 Store.ts:779](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/Store.ts:779)。

**最小修复：** 区分会话/订阅代次与单次分页请求；普通分页不能使同一会话的 feed token 失效。

**不修复的影响：** 用户向左查看历史后，图表可能不再更新实时行情。代次增强改变了上游正常生命周期。

### R02 · P1：回调通过代次检查后，旧行情仍可被重新标记为新品种

**这段代码负责什么：** Store 校验请求，Host 再通过协程提交到 ChartState/ChartEngine。

**问题与复现：** Host.applyInit 启动另一个协程，真正执行时才取 `currentKey()`，并立即向 Store 返回成功。切 B 的任务已排队、A 回调在该任务执行前返回时，A 能通过旧代次检查，数据提交却排在 B 切换之后，最终成为 key=B、内容=A。

临时测试按公开接口顺序执行 `setSymbol(B)` → A callback → 调度队列，得到 B 的 symbol 与 marketKey、A 的 close=99，B 请求仍在等待。

**证据：** [LKanvasChart.kt:65](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:65)、[Store.kt:146](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/store/Store.kt:146)、[LKanvasChart.kt:426](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:426)、[LKanvasChart.kt:475](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:475)。

**最小修复：** 将回调上下文绑定到请求的 key/会话，在唯一串行应用入口复核；Host 应返回真正提交结果，不能先宣称成功、再按可变当前上下文异步提交。

**不修复的影响：** 上轮的数据正确性风险仍存在，只是触发窗口改变。锁住 Store 方法也不能保护队列里尚未执行的应用动作。

### R03 · P1：数据加载成功没有进入完整的范围调整/自动分页流程

**这段代码负责什么：** 上游每次有效 addData 后调整可见范围，再判定是否继续拉取历史或未来数据。

**问题与复现：** 当前只有 panBy/zoomAt 调用 emitRangeChange。Store.onDataAdjusted 没有在数据实际提交完成后触发边界检查，engine.onSnapshot 也没有发送该事件。INIT 只返回一根、more=true，即使视口已经处于边界，仍只出现一次 INIT；需要额外手势才能触发分页。后续分页填不满窗口时也缺少自动继续加载链。

**证据：** [Store.kt:201](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/store/Store.kt:201)、[LKanvasChart.kt:453](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:453)、[ChartState.kt:742](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:742)、[ChartState.kt:844](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:844)；对照 [上游 Store.ts:672](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/Store.ts:672)、[上游 Store.ts:771](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/Store.ts:771)。

**最小修复：** 在真实数据提交及视口调整完成后统一通知范围、执行边界检查；保留 loading 和 more 防重复门禁。

**不修复的影响：** 手势测试能通过，但数据状态机与上游不是同一调用顺序，自动补齐和可见范围事件均不完整。

### R04 · P1：Styles 改成严格覆盖后，局部补丁会重置其他配置

**这段代码负责什么：** 模拟上游 DeepPartial 的“仅合并实际提供属性”。

**问题与复现：** merge 不再跳过默认值，但 Styles 及子类仍是带完整默认字段的 data class；传 `Styles(grid=GridStyle(show=false))` 时，未填写的 candle/axis/separator 也被看成已提供。临时测试先设 dark，再只隐藏 grid，separator 从暗色回到 Styles() 默认浅色。

**证据：** [Merge.kt:25](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/common/utils/Merge.kt:25)、[StylesMerge.kt:13](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/styles/StylesMerge.kt:13)、[StylesMergeTest.kt:13](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/test/kotlin/io/lkanvas/chart/styles/StylesMergeTest.kt:13)。新的测试通过 round-trip copy 携带全部旧值，绕开了最常见的局部补丁场景；上游只遍历提供的 key：[typeChecks.ts:16](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/common/utils/typeChecks.ts:16)。

**最小修复：** patch 必须保留字段存在信息；在“没提供”和“显式提供默认值”之间建立可表达区别。恢复 skip-defaults 或要求所有宿主手工深 copy 都不能同时满足两种语义。

**不修复的影响：** 上轮单向恢复问题修复了，但产生跨分支样式重置；样式代码仍不能按同一调用意图迁移。

### R05 · P1：ConvertFilter.absolute 被赋予错误含义，paneId 实际未参与转换

**这段代码负责什么：** 数据点与某 pane、某轴的局部/图表绝对像素坐标之间转换。

**问题与复现：** 上游 absolute 控制 y 是否加减 pane 的 bounding.top，timestamp 始终是时间戳，索引另有 Point.dataIndex。本地 absolute=true 把 timestamp 转 Int 当索引；paneId 不参与选择 pane/轴/边界，convertFromPixel 也不处理 absolute。

临时测试中，相同时间戳仅切 absolute 就改变 x；同一点传 main 与 RSI pane 得到完全相同坐标。现有 Parity6 甚至断言“absolute 按索引解释”，把错误语义固化成绿色测试。

**证据：** [LKanvasChart.kt:259](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:259)、[LKanvasChart.kt:269](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:269)、[Parity6AxisConvertTest.kt:124](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/test/kotlin/io/lkanvas/chart/parity/Parity6AxisConvertTest.kt:124)；对照 [上游 Chart.ts:1201](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/Chart.ts:1201)。

**最小修复：** 使用包含 timestamp/dataIndex/value 的数据点契约，保持 absolute 原义；按 paneId 与 yAxisId 选取实际绘制轴和布局边界。相同时间戳切 absolute 不应改变 x。

**不修复的影响：** 主轴 log 的单独往返通过，也无法保证副 pane 画线、联动或绝对坐标可用。这是领域逻辑差异，不是语言语法适配。

### R06 · P1：轴注册、轴实例和绘制规格仍不是统一来源

**这段代码负责什么：** 公开 registerYAxis/createYAxis/overrideYAxis 应管理同一组真正参与绘制的轴对象。

**问题与复现：**

- 公开 `LkanvasRegistry.registerYAxis` 写自己的 yAxisTemplates；createYAxis 查 core.extension.yaxis.YAxisTemplates。自定义注册可查到，创建却没有 templateSpec，也不继承 reverse。
- 创建额外 logarithm 轴会直接改 `state.mainAxisSpec`，影响原 candle 主轴；额外轴自身除 customAxis 外按 Normal 投影，保存的 templateSpec 没有进入该额外轴转换。
- syncExtraAxes 只同步 paneId=main 的轴；动态 RSI pane 创建的 reverse 轴能被查询，但不在执行映射中。
- overrideXAxis 仍只改 xAxisState；该状态没有绘制/转换消费者。

前三项均有临时测试复现。主轴 reverse 的旧例已修复，不能因此将这些轴路径一并判为通过。

**证据：** [LkanvasRegistry.kt:121](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LkanvasRegistry.kt:121)、[YAxisTemplates:9](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/extension/yaxis/Index.kt:9)、[LKanvasChart.kt:966](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:966)、[LKanvasChart.kt:988](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:988)、[ChartState.kt:379](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:379)、[LKanvasChart.kt:1063](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:1063)。

**最小修复：** 统一公开注册与构造表；将 spec、pane、reverse 和几何边界留在同一轴实例/上下文，由绘制与转换消费。创建额外轴不应通过改全局主轴来模拟生效。

**不修复的影响：** 仍有多套状态必须手工同步，上游轴修复不能定位到单一职责实现。

### R07 · P1：动态 pane 已能创建，但查询、覆写与生命周期仍停留在三 pane 模型

**这段代码负责什么：** 指标的 pane 归属、pane 创建删除、布局与公共查询应保持一致。

**问题与复现：** 创建 RSI 后 extraPanes 有新 pane，但 getPaneOptions(newPane) 返回空，setPaneOptions 对它没有效果；overrideIndicator(paneId="new_pane") 只改实例字段，不创建目标 pane、不清理旧 pane。新目标没有渲染布局，原空 pane 继续保留。

固定 main/vol/macd 也始终参与布局；createIndicator 缺省新建 pane 后，VOL/MACD 还可能经 resultOfName 回退到固定副图，形成和上游不同的重复归属。

**证据：** [LKanvasChart.kt:517](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:517)、[ChartState.kt:124](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:124)、[LKanvasChart.kt:850](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:850)、[ChartState.kt:910](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:910)、[ChartRenderer.kt:97](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/ChartRenderer.kt:97)。

**最小修复：** 让查询、布局、覆写迁移和删除共同使用实际 pane 集合。示例的固定副图配置应成为显式初始布局，而不是库内另一套无法通过公共查询维护的 pane。

**不修复的影响：** 默认创建 RSI 的简单测试可以通过，动态迁移、尺寸管理和布局相关上游补丁仍需修改多个不一致的状态来源。

### R08 · P1：通用指标渲染仍将柱图当折线，且未完整消费可见性/figure 契约

**这段代码负责什么：** 上游按每个 figure.type、attrs、styles 与实例状态生成图形。

**问题与具体情形：** drawExtraPane 的默认路径将每列直接交给 drawLinePlots，未按 PlotType.BAR 分发；MACD 缺省新 pane 时，macd 柱会走折线。drawExtraPane 取实例时也未过滤 visible，因此隐藏已有结果的指标仍可被画出；PlotDef.attrs、实例样式和 shouldUpdate 等尚无完整消费者。

主 pane 按 yAxisId 分流后同样不是完整 figure 类型管线，内置 VOL/MACD 分支仍有专用规则。此项结论来自静态调用链，未声称本次做过屏幕截图比对。

**证据：** [PaneRenderers.kt:315](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:315)、[PaneRenderers.kt:343](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:343)、[PaneRenderers.kt:686](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:686)、[IndicatorEngine.kt:25](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/indicator/IndicatorEngine.kt:25)；对照 [上游 IndicatorView.ts:134](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:134)。

**最小修复：** 复用一条按实例与 figure 描述驱动的渲染流程，统一可见性、类型、attrs 和样式；平台原语可替换，图形类型与领域分支必须保留。

**不修复的影响：** 27 个计算模板通过不等于 27 个显示模板完成。上游修改 figure 配置时仍可能需要改本地专用 renderer。

### R09 · P1：覆盖物仅拆文件，内置行为仍由名称分支控制，所属 pane 也未进入绘制

**这段代码负责什么：** 模板生成图形，实例绑定 pane，绘制/命中执行同一模板结果。

**问题与具体情形：** Segment.kt 只有 name/pointCount，上游 segment 的 createPointFigures 没有迁入。renderer 先按 segment 等名字执行内置分支，只有 else 才执行注册模板的 createPointFigures；以 replace=true 替换 segment，创建可查到新定义，图形仍走旧 segment 分支。

此外 drawOverlays 仍统一使用主 pane 边界及 main 轴，不按 effectivePaneId 选择坐标；createXAxisFigures/createYAxisFigures 目前只有声明，没有生产调用。zIndex 命中排序与实际绘制遍历也未共用顺序。

**证据：** [Segment.kt:7](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/extension/overlay/Segment.kt:7)、[上游 segment.ts:17](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/extension/overlay/segment.ts:17)、[OverlayRenderer.kt:47](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:47)、[OverlayRenderer.kt:86](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:86)、[OverlayRenderer.kt:133](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:133)、[OverlayDefinition.kt:69](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/overlay/OverlayDefinition.kt:69)。

**最小修复：** 将内置图形生成规则放回各模板，统一执行模板的绘制/命中描述，并按实例 pane 建立上下文；保留 OverlayGeometry 作为模板可复用的数学助手。

**不修复的影响：** 16 个同名文件还不能提供 16 个行为补丁的局部落点，结构验收的关键目标尚未实现。

### R10 · P1：Crosshair 的 null 和可选字段语义仍不一致

**这段代码负责什么：** 宿主通过 executeAction 联动/清除十字线，并抑制反向广播。

**问题与复现：** null 在 cast 处直接 return，不能清掉已存在的十字线；只提供 x/y、不提供 dataIndex 时，门面默认 dataIndex=0，状态写入又使用 index 推导 x，传入的 x 被忽略。临时测试中，指向第 150 根的 x 最终落到第 0 根。

动态 pane 的触摸事件 paneId 仍通过 main/vol/macd 三段推断；tooltip feature 类型也把 vol 以外的非主 pane 归为 crosshair。主轴 reverse 后，十字线标签还直接用未翻转的 yToValue，与已翻转 candle 映射不同。

**证据：** [LKanvasChart.kt:329](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:329)、[LKanvasChart.kt:375](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:375)、[LKanvasChart.kt:394](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:394)、[ChartState.kt:491](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:491)、[ChartRenderer.kt:329](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/ChartRenderer.kt:329)；对照 [上游 Chart.ts:1261](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/Chart.ts:1261)、[上游 Store.ts:1285](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/Store.ts:1285)。

**最小修复：** 保持 null 清除、按 x 推导数据索引的上游顺序；pane 归属与标签转换使用实际 pane/轴上下文。

**不修复的影响：** 反向广播问题已修，但多图联动、清除和副 pane 事件仍需要宿主改写逻辑。

## 5. 应补强的同步与验收项

### R11 · P2：动画和布局接口仍有空实现/缩减语义

**问题与复现：** scrollToDataIndex 先瞬移，再调用 `animate(...){ }`，动画没有任何状态更新；zoomAtCoordinate 完全忽略 duration；两个 timestamp API 调用下层时也未转发 duration。临时测试确认传 1,000 ms 后，索引定位/2 倍缩放立即生效。

setLayoutOptions/getLayoutOptions 只有自定义 paneHeights，缺少上游 Layout 的 barSpaceLimit/pane/yAxis 默认配置模型；固定 pane 外无消费者。getConvertPictureUrl 的平台入口新增了，但 backgroundColor 未使用，bmp 被转成 png，不能因名称存在而视为完整同义。

**证据：** [LKanvasChart.kt:139](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:139)、[LKanvasChart.kt:169](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:169)、[LKanvasChart.kt:889](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:889)、[LKanvasChart.kt:923](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:923)；上游定义见 [Options.ts:88](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/Options.ts:88)。

**最小修复：** 动画真正驱动同一状态入口并转发 duration；配置保留上游字段及职责。无法直接等价的平台能力应明确登记限制，不以占参数位置的方式宣称完成。

**不修复的影响：** API 表面更相似，但参数与调用逻辑仍不同，SLA-014 无法完整关闭。

### R12 · P2：状态工具和结构映射尚未达到新的验收口径

**问题：** check.py v4 的文件引用校验有进步，但对 SLA- 行直接 continue；行为通过只检查测试文件存在，不检查断言或实际执行。当前 257 个能力条目中：已声明 237、已接线 2、行为通过 3、不适用 15；16 个 SLA 条目中：已接线 1、行为通过 15。没有任何“结构可对应”条目。

structure-map 仍写“其余 22 个模板待登记”，Store 映射引用的 Parity4PaginationTest/Parity4SymbolSwitchTest 并不是当前实际测试类；部分 component/common 目标文件仍是计划路径。不能从新增了映射文件推导映射已经完成。

**证据：** [check.py:139](/Users/pauldeman/Documents/ai_workspace/KKLineChart/tools/alignment/check.py:139)、[structure-map.md:39](/Users/pauldeman/Documents/ai_workspace/KKLineChart/docs/alignment/structure-map.md:39)、[structure-map.md:51](/Users/pauldeman/Documents/ai_workspace/KKLineChart/docs/alignment/structure-map.md:51)、[gap-matrix.md:394](/Users/pauldeman/Documents/ai_workspace/KKLineChart/docs/alignment/gap-matrix.md:394)。

**最小修复：** 回填真实本地符号/测试方法及差异，修正旧状态和不存在引用；将行为执行结果与结构映射分开验收。优先更正 R05 那种“测试通过但预期本身不符合上游”的用例。

**不修复的影响：** README 的完整收口声明与证据范围不符，后续维护者仍需重新查找补丁对应位置。237 个已声明条目也不能反向证明 237 项实现缺失，只能说明台账没有完成验收记录。

### R13 · P2：真实历史补丁演练只有来源和映射，缺少补丁前后验证材料

**问题：** 已在线核实历史提交 `95a6f2ad681137a4cc3910760851c8f2c988084d`，确实修改 Chart.ts/Event.ts/Store.ts，与文档范围相符。但 f96afa1 只增加 16 行同步文档，当前基线本来已包含该修复；记录没有展示失败状态、反向撤销/重新应用或前后运行结果。

[官方历史补丁](https://github.com/klinecharts/KLineChart/commit/95a6f2ad681137a4cc3910760851c8f2c988084d) 与 [本地演练记录:52](/Users/pauldeman/Documents/ai_workspace/KKLineChart/docs/upstream-sync.md:52) 能证明来源与有限符号映射；现有抑制回发测试能证明部分当前行为，但不足以单独证明完整移植演练达标。

**最小修复：** 在临时分支保存一笔真实补丁的 before/after、对应本地有限 diff、失败/通过用例结果和必要平台差异；不要求为了展示而重写已正确的实现。

**不修复的影响：** U1 仍停在维护流程描述，无法量化证明更新时不需要重新理解/改写整段实现。

### R14 · P2：最新 main 已合入 arc.ts 修复，当前实现只覆盖部分修复语义

**新增事实：** 本次 [官方固定 compare](https://github.com/klinecharts/KLineChart/compare/d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df...66dd45b31e93b57a4c75a3e0b98bc2a8393df36e) 显示 main 领先 3 个提交，除 README 外包含圆弧命中修复 `e829f30eff1f4fe23e48e338af17aae5701cc6df`，由 `66dd45b31e93b57a4c75a3e0b98bc2a8393df36e` 合并。合并时间为北京时间 2026-10-30 01:06:35；作者日期较早，不能据作者日期误判它已在先前 main 中。

本地已使用角度区间处理大弧/整圆，优于旧 v10.1.0 的端点矩形命中；但没有新增修复的端点距离容差。半径 100、0°→90°，点位于 -0.5°：距离起点约 0.873 px，在线上游新函数命中为 true，本地 FigureHit 在 2 px 容差下为 false。

**证据：** [官方源码修复](https://github.com/klinecharts/KLineChart/commit/e829f30eff1f4fe23e48e338af17aae5701cc6df)、[Figures.kt:60](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/figure/Figures.kt:60)、[Figures.kt:76](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/figure/Figures.kt:76)。本次直接运行固定 main SHA 的官方 TS 函数验证 true，并用 JVM 临时探针确认本地 false。

**最小修复：** 在同步台账登记这笔实际新增源码补丁，明确采用/差异/延期决定，增加大弧、整圆和端点容差共同输入。保留固定 v10.1.0 快照用于 oracle，不应悄悄覆盖快照来制造“最新”。

**不修复的影响：** 对固定 v10.1.0 基线可保留原版本声明，但不能声称已完整跟进当前 main。这也是下一次真实局部同步演练的合适对象。

## 6. 临时复审测试清单

临时类为 `audit.Round2AuditTest`，通过 `/tmp/lkanvas-audit-20261030/audit.init.gradle` 加载；19 项全部执行，失败/错误/跳过均为 0。其中“差异确认”类断言当前缺口确实存在。

| 方法 | 类型 | 结果含义 |
|---|---|---|
| fixedGlobalIndicatorAndFivePeriods | 修复确认 | 既有图表可创建全局自定义指标，五周期 MA 正常 |
| fixedControlsAndScaleOne | 修复确认 | 禁滚动/缩放生效，scale=1 不位移 |
| fixedCrudDeleteAll | 修复确认 | 无默认实例，两个 RSI 可全删及清 pane |
| fixedInteractiveOverlayOptions | 修复确认 | 交互创建保留 pane/group/lock/visible |
| paginationInvalidatesLiveFeed | 差异确认 | 分页后实时 candle 被丢弃；R01 |
| initDoesNotTriggerBoundaryLoad | 差异确认 | 初始边界数据不触发继续分页；R03 |
| queuedSymbolSwitchCanRelabelAcceptedOldCallback | 差异确认 | A 数据被写入 B key；R02 |
| stylePatchResetsOtherBranches | 差异确认 | 局部 grid patch 重置 dark separator；R04 |
| convertAbsoluteChangesTimestampMeaning | 差异确认 | absolute 改变时间戳的 x；R05 |
| convertIgnoresPane | 差异确认 | main 与 RSI pane 转换相同；R05 |
| publicAxisRegistrationNotUsedByCreation | 差异确认 | 公开轴注册不进实际构造表；R06 |
| secondaryLogAxisChangesMainAxis | 差异确认 | 新增额外 log 轴改变主轴；R06 |
| overrideIndicatorPaneDoesNotMaintainPaneList | 差异确认 | paneId 迁移不维护 pane 集合；R07 |
| paneQueriesAndOptionsIgnoreDynamicPanes | 差异确认 | 动态 pane 不可查询/设高；R07 |
| nullCrosshairDoesNotClear | 差异确认 | null 未清除十字线；R10 |
| xOnlyCrosshairIgnoresProvidedCoordinate | 差异确认 | 可选 dataIndex 缺省时忽略 x；R10 |
| durationDoesNotAnimateIndexScrollOrZoom | 差异确认 | 动画参数没有实际过渡；R11 |
| secondaryPaneAxisIsNotInExecutionMap | 差异确认 | 副 pane 轴不进入映射；R06 |
| arcEndpointToleranceStillMissing | 差异确认 | 与新 main 的端点容差不一致；R14 |

R08/R09 以及部分配置/事件余项通过生产声明、全部调用点检索和上游执行路径对照确认；没有将未执行的 UI 场景列为已通过截图验收。

## 7. 结构保持与后续更新的可操作结论

| 领域 | 相比上轮 | 当前补丁移植方式 | 本次接受范围 |
|---|---|---|---|
| 27 指标公式 | 显著改善 | 可以先定位同名 calc，再更新同文件 Scratch 并做差分 | 接受规范全量入口的改善；仍需逐补丁检查双路径与参数边界 |
| Store 数据流程 | 有真实结构落点 | 核心顺序可对照，但 Host 异步提交改变了顺序和所有权 | R01~R03 修复前不接受完整状态机一致 |
| 16 覆盖物 | 文件已拆、行为仍集中 | 模板文件之外还要修改 renderer/hitBody 名称分支 | 不接受“模板行为已镜像” |
| pane/轴 | 动态路径已加、统一模型未完成 | 多张表和固定三 pane 分支仍需同步修改 | R05~R09 修复前不接受职责一致 |
| common/公共类型 | 部分移到 core | 部分类型保留独立形态；Point/ConvertFilter 语义需更正 | 不接受用类型别名/同名替代完整字段语义 |
| 同步证据 | 从文档比较进展到真实历史补丁映射 | 还缺一次可复现 before/after | 接受来源核查与映射进展，不判 U1 已完成 |

建议先修数据正确性 R01/R02，再处理 R03/R04/R05，随后收敛 pane/轴/模板管线。既有 calc、全局模板表、参数列修复和测试应保留；没有必要回退本轮整体改动或再进行一次全库重写。

重新验收至少需要：

1. **组合路径通过：** init→分页→实时→切品种→旧回调；同一组输入验证请求、数据、事件和上下文，而非分别测试每个函数。
2. **结构有真实单一来源：** 注册表连接实际构造；pane 集合连接查询/布局；每个轴 spec 连接绘制/转换；每个模板连接图形生成/命中。
3. **上游语义正确的测试：** 修正 absolute、可选 Crosshair、DeepPartial 和 figure 类型的预期，不以本地实现自证。
4. **完整的符号映射和例外：** 明确哪些是真正平台替换、哪些是有意领域差异；同名文件应承载相应行为。
5. **真实补丁回归证据：** 保存有限本地 diff 与 before/after 结果，再评估是否可以关闭“后续无需大段重写”的验收项。

## 8. 未检查范围与风险

本次沿上轮主标准深入复核了变更后的门面、Store/Host、指标规范与增量入口、样式合并、pane/轴、转换、事件、覆盖物模板/绘制/命中，以及相关验证工具。27 个 calc 均存在；算法控制流抽查 MA、MACD、DMI、TRIX、VOL、OBV，数值范围依据现有 27 份 oracle 与双路径测试，不声称对所有参数/算法分支做了形式化证明。

未重跑真机截图、触控端到端、TalkBack、Macrobenchmark、R8、Maven consumer；未重新认证仓库中的性能报告。导出 bitmap、全量 Styles 默认字段、所有图形命中边界也未穷举。测试日志中的构建警告不作为结构不一致的证据。

本报告没有实施修复。现阶段最需要避免的误判是：**340 个测试通过、16 个模板文件存在、SLA 标成行为通过，并不等于这些组合行为及结构已经一致。** 当前版本已有可保留的结构改进，但仍有数据正确性及调用链阻断，应以本报告列出的具体剩余项重新收口。
