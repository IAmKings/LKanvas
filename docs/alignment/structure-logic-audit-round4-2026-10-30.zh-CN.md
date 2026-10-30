# Compose 工程与 KLineChart 结构、逻辑一致性第四轮审计

- 日期：2026-10-30（Asia/Shanghai）；与第三轮同日，报告独立保存。
- 审计对象：`1f1c3476159686e9dfd87f927413d25039b7ae32`，版本 `0.8.0`。
- 本轮比较起点：第三轮对象 `9ffd1281dd3feac1cc5a3be52379139ff6ef18fb`（0.7.0）；实现修改为 Batch A–F。
- 上游固定基线：KLineChart v10.1.0，`d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df`。
- 验收口径：沿用前三轮 S1/S2/S3/L1/L2/L3/V1/U1，以职责结构、领域分支、执行顺序、参数契约和局部补丁可对应性为主要标准。
- 正常使用负载：单图表、异步历史回调与实时推送、切品种/周期、多个 pane/轴、指标叠加和覆盖物编辑。包括公开支持的任意线程回调与 ChartState.externalScope。
- 改动边界：开始时工作区干净；本次只新增本报告，未修改生产实现、仓库测试、构建配置、上游快照及前三轮报告。

## 1. 验收结论

**0.8.0 有可确认的修复，但仍未达到原定的“结构与逻辑完整对齐”标准，不能确认第三轮 T01–T11 已全项关闭。**

上游来源和指标默认数值基线可靠；覆盖物核心命中入口、基础绘制排序、副图十字线和对齐工具的已知缺口也有实质修复。无需回退这些修改，更没有依据要求全库重写。

剩余的主要问题仍是**同一规则在多处保存或计算，新函数没有成为实际消费者的唯一入口**：轴转换改成所属 pane 的绝对坐标，门面却仍当成相对坐标；paneVisibleRange 进入转换但没有进入渲染；attrsOverrideFor 只有测试调用；动态拖动禁用状态分别存在门面和 ChartState。领域规则因此仍需要跨多个分支同步维护。

本报告列出 **9 项剩余发现：5 项 P1、4 项 P2**。其中既有原问题的未完成部分，也有本轮修复引出的坐标/会话问题，以及本轮扩展检查发现的圆弧绘制和固定层级命中差异；并非把原有问题全部重新登记。

| 维度 | 第四轮判断 | 主要依据 |
|---|---|---|
| S1 来源、文件与符号可追溯 | 部分通过 | 官方快照 155/155 一致，指标映射补齐到 27 项；台账仍以文件/测试存在为主，未形成完整执行关系证明 |
| S2 职责与依赖结构 | 未通过整体验收 | 新的公共入口确有增加，但 renderer、转换、手势和配置仍保留旁路或重复状态，见 Q02–Q05/Q07 |
| S3 算法与控制流形态 | 局部通过 | 27 个指标 calc 路径及真实 arc 补丁保持；figure 几何消费、固定 pane 分支与圆弧绘制方向仍不一致 |
| L1 数据状态机 | 不通过 | A→B 的原锁竞争样例已阻断；A→B→A 仍可污染，合法首笔推送也可被拒绝，见 Q01 |
| L2 公共参数与状态契约 | 部分通过 | 简单 absolute、StylesPatch、动态 pane 管理保留正确；副轴坐标、格式化与 Layout 设置仍有差异 |
| L3 注册→执行→绘制/命中/编辑 | 未通过整体验收 | 主图 figure 和覆盖物 FigureHit 已接通；attrs、副图绑定轴、编辑反算及固定层级命中仍未闭合 |
| V1 验证与发布门禁 | 部分通过 | 366 项既有测试通过；35 项临时探针仍复现差异；check.py 的通过不证明“全项收口” |
| U1 真实上游源码补丁局部同步 | 本次限定通过 | 历史 before/after 可重建；本轮覆盖物命中已使用修复后的 FigureHit，但绘制还有另一类差异，见 Q08 |

“语法尽可能一致”按跨语言移植评判：允许 Kotlin 类型、集合和 Compose Canvas 的必要替代；要求保留可定位的领域符号、分支条件、参数含义和执行顺序。不能以文件同名、注释写“同源”或新增一个未调用的 helper 代替这些要求，也不采用 TS/Kotlin 文本相似率评分。

## 2. 实际核验结果

### 2.1 上游与数值基线

| 核验 | 本次结果 | 证明范围 |
|---|---|---|
| 官方固定提交归档 vs 本地 third_party 的 src | 155/155 文件逐字节一致；修改、缺失、额外均为 0 | 固定对照快照真实、未被改写 |
| 上游 TS 指标 oracle 重新生成到临时目录 | 27/27 JSON 与仓库 fixture 一致 | 默认样本可再生；不等于所有参数边界均已证明 |
| core 既有测试 | 222 项，失败/错误/跳过均为 0 | 当前 core 断言 |
| Compose debug 既有单元测试 | 144 项，失败/错误/跳过均为 0 | 当前 JVM 可测试分支；合计 366 项 |
| 第四轮外部临时测试 | 35 项通过；按主断言分为 20 项基准/修复确认、15 项差异复现 | 详见 §5；差异用例通过表示成功复现错误行为 |
| check.py | 退出成功；287 行被解析，R01–R14 全部在内 | 文档枚举、覆盖和引用存在检查 |
| 结构映射单独复核 | 指标 27 项，缺项/额外项均为 0；validate_structure_map 无错误 | 第三轮缺的 2 项与未调用问题已修 |

既有测试禁用构建缓存并实际执行，结果在运行临时测试之前单独保存：

```sh
./gradlew :lkanvas-core:cleanTest :lkanvas-compose:cleanTestDebugUnitTest \
  :lkanvas-core:test :lkanvas-compose:testDebugUnitTest \
  --no-build-cache --console=plain
python3 -B tools/alignment/check.py
```

本次在线复核的上游 main 仍为 `66dd45b31e93b57a4c75a3e0b98bc2a8393df36e`，相对基线 ahead 3：2 个 README 文件与 `src/extension/figure/arc.ts` 有变更；没有发现第三轮之后的其他源码补丁。参见[固定到本次上游 HEAD 的官方 compare](https://github.com/klinecharts/KLineChart/compare/d5bb26f706a6bd8f67eed4ecbbf22cb67a0725df...66dd45b31e93b57a4c75a3e0b98bc2a8393df36e)。当前已知 arc 修复被本地采纳，不代表其余领域已与上游完整对齐。

### 2.2 本轮可以保留的修复

1. **A→B 的原锁竞争用例不再写错数据。** INIT 的 requestGen 在 Store 锁内复核；实时路径对不同 key 的二次检查也阻断了第三轮直接样例。真正携带过期 token 的请求会被 ChartEngine 拒绝，门禁本身存在且有效。
2. **轴模板默认字段进入构造。** reverse/inside/positionLeft/scrollZoomEnabled/gap 已桥接到 core，再作为 createYAxis 的默认值。getYAxisTemplate 回读仍有遗漏，见 Q02。
3. **纯折线叠加转换已修复原样例。** 同 pane 的 MA(5)+BOLL(20,10)，门面相对 y≈45.917786，与 renderer 使用的全列公共值域一致。不能继续把“只取第一实例”作为所有动态 pane 的现状。
4. **覆盖物模板的局部坐标已贯通。** 外部 draw 调用确认模板收到 pane 高度及减去 top 的 pixelPoints；原 verticalStraightLine 从整图顶开始的边界错误已修。
5. **动态 pane 十字线位置与归属已修复原样例。** 触摸状态入口与 executeAction 均保留 RSI pane 中的 y 和 paneId；标签调用也已改为副 pane 反算。
6. **主图和主图额外轴已接入 drawInstanceFigures。** 原来这两个入口直接按折线画 BAR 的调用分支已替换。固定 VOL/MACD 和 attrs 仍不完整，见 Q05。
7. **priceLine 的锚点几何已修复。** x=200、width=600 时现在从 200→600，原全宽 0→600 样例不再成立。
8. **覆盖物 arc 命中已复用 core。** 圆心不再被当成弧线命中，第三轮 T08 的实际入口复现通过；旧 hitFigureShape 已删除。
9. **基础绘制顺序已修复。** 实际模板遍历从 `[high,low]` 改为 `[low,high]`；命中仍按高到低。固定层级被选中后的问题另见 Q09。
10. **内置拖动开关回读和工具接入已修。** vol=false 可以读回；R 行确实被解析，结构检查确实被 main 调用，指标映射补齐。图片白背景现在无条件合成，bmp 改为明确不支持；这两项仅静态复核，未执行 bitmap 集成测试。

## 3. 第三轮 T01–T11 逐项复核

| 原编号 | 第四轮状态 | 判定依据 |
|---|---|---|
| T01 写入时会话归属 | 部分修复，仍阻断 | 原 A→B 锁竞争、显式过期 token 已阻断；ABA 及 INIT/首推提交顺序仍有问题，Q01 |
| T02 副 pane 轴上下文 | 部分修复，仍阻断 | metadata 有 paneId，构造默认字段已修；坐标系接错、renderer 未消费轴，回读丢默认字段，Q02 |
| T03 叠加指标共享值域 | 部分修复 | 纯线叠加原样例通过；带 BAR 的转换与渲染仍不同，Q03 |
| T04 覆盖物 pane 生命周期 | 部分修复，仍阻断 | 模板局部 bounds/pixelPoints 正确；逆变换重复加 top，创建上下文及裁剪仍有缺口，Q04 |
| T05 动态 pane 十字线 | 已修复所检原复现 | y 不再截回主图，paneId 保留，标签改用 pane 反算；不外推副轴绑定场景，后者依赖 Q02 |
| T06 通用 figure 管线 | 部分修复 | main/主图额外轴已接入；attrs helper 未接入、VOL/MACD 专用旁路仍在，Q05 |
| T07 priceLine 几何/格式 | 几何已修，格式未关闭 | 锚点→右界通过；主图精度仍写死，千分位与折叠未消费，Q06 |
| T08 arc 命中旁路 | 已修复原命中复现 | 实际覆盖物命中复用 FigureHit，圆心误命中消失；另发现 end<start 的绘制差异，Q08 |
| T09 绘制/命中层级 | 基础排序已修，交互契约仍有差异 | 绘制升序通过；selected+fixedZLevel 会失去命中，Q09 |
| T10 Layout/图片契约 | 部分修复 | 内置 flags 可回读，背景合成分支已改；动态禁用状态不共用，动态高度设置仍被忽略，Q07 |
| T11 解析/结构校验/缺项 | 原三处缺口已修 | R01–R14 实际解析、validate_structure_map 已调用、27 指标映射集合齐全；不把引用存在检查等同结构行为验收 |

本轮不撤销第三轮已确认的 StylesPatch、分页订阅代次分离、初始自动续拉、pane CRUD 和动画修复；相应基准探针仍通过。

## 4. 剩余发现与最小处理方向

### Q01 · P1：会话身份仍未稳定贯穿订阅、异步队列与实际写入

**职责：** Store 判定请求归属，Host 向 ChartEngine 的串行消费者提交同一身份的数据。

**问题：** addUpdate 的锁内复核没有接收捕获的 subscriptionGeneration，currentSubscriptionGenFor 只比较 key，相同 key 就返回当前代次。因此 A→B→A 无法区分两次 A。Host 的 `engineSessionId()` 又在 scope.launch 内读取；它代表协程运行时的引擎状态，并不是发起请求时的不可变会话身份。Reset 尚未执行时，新数据也可能被赋予旧 sessionId。

**运行复现：**

- 让第一次 A 的推送通过锁外检查后等待 Store 锁，锁内完成 B 及第二次 A 的 INIT。释放旧推送后，第二次 A 的 close 列表从 `[300]` 变为 `[300,999]`；旧推送仍落库。
- INIT close=100，subscribeBar 立即推送 ts=2000、close=999，让 Reset 与 Upsert 在引擎处理前先入队。最终只剩 `[100]`，合法首推被拒绝。同样结果可经公开 ChartState(externalScope) 构造复现，门面立即执行、引擎延后串行处理即可；没有修改生产会话字段。这是受支持的异步调度，不表示默认主线程下每次同步订阅必定丢数据。
- 原 A INIT 在锁内被拒绝后，回调仍无条件调用旧 loader.subscribeBar；本次受控序列的订阅记录为 `[B,A]`。旧 A 的订阅动作发生在 B 退订旧上下文之后，拒绝数据并未阻止过期生命周期副作用。

**证据：** [Store.kt:169](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/store/Store.kt:169)、[Store.kt:250](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/store/Store.kt:250)、[LKanvasChart.kt:469](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:469)、[ChartEngine.kt:239](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/compute/ChartEngine.kt:239)。Parity10SessionGateTest 的迟到回调场景主要在切换后才进入首次检查，未覆盖本次锁等待 ABA 和提交前首推交错。

**最小处理方向：** 在会话建立/配置变更时确定身份，捕获并随 INIT、分页、实时请求传到现有引擎写入入口；锁内比较捕获代次，避免用 key 相等代替代次相等。订阅动作使用同一门禁及真实提交结果，处理好新 INIT 与首推的顺序；不需要另造第二个数据引擎。

**不修复的影响：** 旧行情污染新会话，合法新行情也可能静默丢失；本地增强状态机尚不能称为整体正确。

### Q02 · P1：副轴内部改成绝对坐标，门面仍按相对坐标处理；renderer 仍忽略绑定轴

**职责：** 同一 pane/轴的转换、绘制和标签应共享轴规则及一套坐标约定。

**问题：** axisValueToY 现在用 pane 的绝对 top/bottom 投影，返回绝对 y；convertToPixel 仍将它命名为 yRel，并在 absolute=true 时再加 top。axisValueAt 同样期望绝对 y，而门面传入相对值。drawExtraPane 则仍使用普通 LinearYAxis，全 pane 共用一个 y，不读取绑定轴 spec/reverse。主图过滤副轴记录的修改成立，但副图没有补上对应消费路径。

**运行复现：** RSI pane 高 80、top=481，绑定 log 轴，同一值预期相对 y≈21.143569，门面实际返回≈502.143569，恰好多一个 pane top；absolute=true 还会再加一次。直接输入正确相对 y 反算也得不到原值。另用实际 renderer 的 draw 钩子读取 yOf：绑定 log+reverse 后绘制 y=521，而同轴转换 y≈539.856431，仍不一致。

轴构造默认字段已修，但 getYAxisTemplate 只桥接 name/createSpec，注册 reverse=true 后，新轴为 true，模板回读仍为 false。

**证据：** [ChartState.kt:382](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:382)、[LKanvasChart.kt:295](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:295)、[LKanvasChart.kt:325](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:325)、[PaneRenderers.kt:332](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:332)、[LkanvasRegistry.kt:154](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LkanvasRegistry.kt:154)。

**最小处理方向：** 明确内部绝对/局部坐标的边界，在门面只转换一次；按实际绑定轴给每个实例提供投影与标签，构造/查询复用完整配置。不能仅在 metadata 加 paneId 后让下游继续按普通轴绘制。

**不修复的影响：** 副轴上图形、宿主定位、十字线/覆盖物上下文不能一致；轴补丁仍需修改两套执行规则。

### Q03 · P1：共享值域函数没有进入渲染，带 BAR 的 pane 又产生两套范围

**职责：** 同 pane 的实例、基线与轴范围应统一决定转换和绘制的值域。

**问题：** paneVisibleRange 已供 paneValueToY/paneValueAt 使用，但 drawExtraPane 仍自己扫描全部列。新函数把有 BAR 的实例纳入 0 基线，renderer 没有相同规则。全仓调用搜索确认 renderer 没有调用 paneVisibleRange；“三处共用”的注释与调用关系不符。

**运行复现：** 动态 pane 的 VOL 各可见列均为 100。转换范围为 `(0,100)`，100 映射到 pane 顶 y=481；实际 renderer 范围仍按恒值扩成 `(99,101)`，draw 钩子收到的 yOf(100)=521，相差 40 px。纯线 MA+BOLL 的原样例现在正确，不能据此推断 BAR 也正确。

**证据：** [ChartState.kt:437](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:437)、[ChartState.kt:503](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:503)、[PaneRenderers.kt:332](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:332)。

**最小处理方向：** renderer 使用同一份范围结果，按 figure.baseValue、minValue/maxValue 和轴规则处理基线，删除重复扫描分支；保留已正确的全部可见实例聚合。

**不修复的影响：** 数值转换和实际柱图仍不一致；每次上游范围规则更新，仍需手工同步两份算法。

### Q04 · P1：覆盖物输入反算重复加 pane top，创建及裁剪也未使用完整上下文

**职责：** 覆盖物创建、拖动、绘制和命中应使用实例所属 pane 的同一坐标系统。

**问题：** 手势传入的 y 是整图绝对坐标，overlayPointFromPx 直接交给期望相对 y 的 paneValueAt；后者再加 top。已修改的拖动调用虽然传了 paneId，数值仍错误。普通 CREATE 分支的 dragPaneId 从默认 main 开始，进入创建时没有按 creating 实例初始化；brush 首点与后续采样使用的上下文也不同。

renderer 的模板局部 bounds/pixelPoints 与 translate 已修，但没有相应 pane clipRect，hitBody 也不拒绝 pane 外查询点。

**运行复现：** RSI pane 的 value=50 先转为屏幕触点，再经当前拖动所用函数并显式传入正确 paneId，反算得到≈-476.405492。另将 RSI 覆盖物设为 pane 相对 y=-20 的水平线，当前 hitTest 在 `paneTop-20` 的主图区仍返回该副图覆盖物。创建分支问题由调用链静态确认，本次未运行完整触摸 E2E。

**证据：** [ChartState.kt:516](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:516)、[ChartState.kt:586](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:586)、[ChartGestures.kt:81](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/gesture/ChartGestures.kt:81)、[ChartGestures.kt:101](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/gesture/ChartGestures.kt:101)、[ChartGestures.kt:366](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/gesture/ChartGestures.kt:366)、[OverlayRenderer.kt:98](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:98)、[OverlayRenderer.kt:262](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:262)。

**最小处理方向：** 从实例解析创建和编辑的 pane/轴，绝对触点在输入边界减一次 top；模板局部坐标在输出边界平移一次，并共享绘制裁剪和命中边界。保留已正确的模板局部坐标修改。

**不修复的影响：** 副图覆盖物一经编辑会跳值或跳位，越界图形还能干扰其他 pane；保存 paneId 仍不等于全生命周期一致。

### Q05 · P1：attrs 几何 helper 没有绘制消费者，固定 VOL/MACD 仍绕过实例通用流程

**职责：** 指标的 type、attrs、styles、实例顺序和可见性应在各 pane 以同一契约执行。

**问题：** attrsOverrideFor 已被新增测试直接调用，但生产代码没有调用它。drawInstanceFigures、drawBarPlots 和 drawLinePlots 都未消费它。固定 VOL/MACD 依然只取一个 paneResult，按 volume/macd 键绘图，不遍历该 pane 的全部实例及通用 figure，相关注释不能代替执行链。

**运行复现：** 注册仅含 BAR 的自定义模板，attrs 返回 top=5、bottom=15，并记录调用次数。单独调用 helper 有结果；经真实 drawInstanceFigures 生成 38 次矩形绘制调用时，attrs 调用次数仍为 0。已有 AxisAndInstanceFieldsTest 只验证 helper 能求值，不能捕获这条断链。

**静态确认的组合情形：** `macd` pane 以 isStack=true 再绑定 RSI，管理表可以包含两个实例，drawMacdPane 却只读取 macdPaneResult 的一个结果；后一个不会通过通用实例循环绘制。其自定义 draw/attrs/styles 也不经过动态 pane 的入口。未以真机截图复核该组合。

**证据：** [PaneRenderers.kt:699](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:699)、[PaneRenderers.kt:733](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:733)、[PaneRenderers.kt:803](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:803)、[PaneRenderers.kt:250](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:250)、[PaneRenderers.kt:286](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:286)、[AxisAndInstanceFieldsTest.kt:591](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/test/kotlin/io/lkanvas/chart/AxisAndInstanceFieldsTest.kt:591)；上游对照为 [IndicatorView.ts:134](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/view/IndicatorView.ts:134)。

VOL 的涨跌色也不是必须绕过模板的 Android 特例：上游 [volume.ts:33](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/extension/indicator/volume.ts:33) 本身就在 figure.styles 中表达，仍由通用指标绘制消费。

**最小处理方向：** 所有 pane 的全部实例复用现有 figure 执行入口，几何 attrs 真正传给图形绘制，styles 与实例配置按原契约合并；保留 main/额外轴已经接通的两处调用，消除固定 pane 的规则旁路。

**不修复的影响：** 上游模板的几何和样式补丁无法局部移植后自然生效；仍需按 pane 重新改写 renderer。

### Q06 · P2：priceLine 格式上下文仍是近似，主图精度继续写死为 2

**问题与复现：** 几何已经正确，模板改用 rawValue 标记也提供了延后格式化入口；但 resolvePricePrecision(main) 仍直接返回 2。公开设置 SymbolInfo(pricePrecision=5) 后，该函数仍返回 2。副图取的是 definition.precision，也没有使用实例精度覆盖。实际文本分支只调用 formatPrecision，未应用已配置的 thousandsSeparator/decimalFold。

因此 123.45678 在主图应按品种精度显示为 123.45678，当前分支会生成 123.46；配置千分位或小数折叠后，同一值也可能与上游/其他格式链不同。精度结果已运行验证，文本最终像素未执行集成测试。

**证据：** [OverlayRenderer.kt:125](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:125)、[OverlayRenderer.kt:149](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:149)、[上游 priceLine.ts:24](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/extension/overlay/priceLine.ts:24)。

**最小处理方向：** 将实际品种/实例精度及已有格式链传到该模板执行上下文并复用；不在 renderer 再建立一份固定精度规则。

**不修复的影响：** 同名模板仍有不同文本行为，格式补丁继续需要跨模板和 renderer 协调。

### Q07 · P2：动态 pane 的 Layout 读写与实际拖动分别维护两份状态

**问题与复现：** setLayoutOptions 写入的是 LKanvasChart.disabledExtraDrags，getLayoutOptions 也从这里回读；手势使用的 ChartState.adjustExtraPane 查询另一个同名列表。设置 RSI pane drag=false 后，getter 正确返回 false，实际调用手势使用的 adjustExtraPane(80,+30) 仍把高度改为 110。

同一动态 pane 的 paneHeights 设置也被忽略：setter 只路由 main/vol/macd，输入 `{paneId:123}` 后仍为原来的 80，尽管 getter 现在能列出动态 pane 高度。

**证据：** [LKanvasChart.kt:981](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:981)、[LKanvasChart.kt:994](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:994)、[LKanvasChart.kt:1028](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:1028)、[ChartState.kt:1258](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:1258)。

**最小处理方向：** 删除一份禁用列表，让 setter/getter/手势使用同一状态；高度设置遍历实际 pane 集合并复用现有 setPaneOptions 路由。

**不修复的影响：** 配置往返看似正确，实际交互却不遵守，宿主保存/恢复动态布局仍失败。

### Q08 · P2：arc 命中已统一，end<start 时绘制仍走相反的角度跨度

**问题与复现：** FigureHit 将起止角按顺时针取模；FigureRenderer 直接把 end-start 传给 DrawScope.drawArc。Arc(start=180°,end=90°) 的实际绘制参数为 -90°，命中使用的是顺时针 270°；同半径的 135°点在该绘制跨度内却命中 false，270°点命中 true。临时探针记录了真实 drawFigure 发出的 sweepAngle，并验证了这两个命中结果。

上游 drawArc 调用 Canvas ctx.arc 默认方向；本次采纳的补丁也明确按 increasing-angle sweep 判断。Compose 的 drawArc 使用 start+sweep 表达终点，正方向为顺时针；平台替代必须显式适配该差异。[官方 DrawScope 说明](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/drawscope/DrawScope)。

**证据：** [FigureRenderer.kt:66](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/FigureRenderer.kt:66)、[Figures.kt:87](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-core/src/main/kotlin/io/lkanvas/core/figure/Figures.kt:87)、[上游 arc.ts:42](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/extension/figure/arc.ts:42)。

**最小处理方向：** 命中、绘制及 arcSample 共享同一跨度规则，在平台调用前处理负差值及整圆；保留已经修复的公共命中入口。

**不修复的影响：** 跨零或反向起止角的自定义圆弧仍会所见与所点不同。这是本轮扩展检查发现的绘制问题，不是第三轮圆心误命中尚未修复。

### Q09 · P2：fixedZLevel 覆盖物选中后被全部命中分支跳过

**问题与复现：** 命中控制点与选中线身的两段都跳过 fixedZLevel，普通对象一段又跳过 selectedId。因此一个可见、未锁定、fixedZLevel=true 的水平线，未选中时能命中，select 后同一位置返回 null。实际 hitTest 已运行确认。

上游 fixedZLevel 的含义是保留声明层级、不临时浮起，并不禁用命中或编辑。本地把“没有选中优先级”实现成“从所有候选中移除”。

**证据：** [OverlayRenderer.kt:229](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:229)、[OverlayRenderer.kt:239](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:239)、[OverlayRenderer.kt:246](/Users/pauldeman/Documents/ai_workspace/KKLineChart/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:246)、[上游 Overlay.ts:153](/Users/pauldeman/Documents/ai_workspace/KKLineChart/third_party/klinechart-v10.1.0/src/component/Overlay.ts:153)。

**最小处理方向：** 固定层级对象保留在普通 z 顺序命中候选中，选中和控制点编辑按契约处理；不要因取消浮起而取消可交互性。

**不修复的影响：** 宿主启用固定层级后，第一次选择会使对象后续难以继续选中/拖动。基础升序绘制修复不能证明整个层级交互契约已关闭。

## 5. 临时验证清单与证据边界

外部测试类为 `audit.FourthAuditTest`，以临时 init script 注入测试源：

```sh
./gradlew -I /tmp/lkanvas-audit-round4-20261030/audit.init.gradle \
  :lkanvas-compose:testDebugUnitTest --tests 'audit.FourthAuditTest' \
  --no-build-cache --console=plain
```

以下按方法的主断言归类，共 35 项。部分差异用例同时确认已修部分，例如轴构造成功但模板回读丢字段。

| 方法 | 主类型 | 本次确认 |
|---|---|---|
| `explicitStaleSessionTokenIsRejectedByEngine` | 基准/修复 | 显式携带旧 token 的请求被引擎拒绝 |
| `fixedAnimationHasIntermediateState` | 基准/修复 | 动画有中间状态并到达目标 |
| `fixedBasicCrosshairAndClear` | 基准/修复 | x 推导索引、null 清除 |
| `fixedBuiltinDragFlagReadback` | 基准/修复 | vol=false 可回读 |
| `fixedBuiltinPaneRelayoutAfterCreation` | 基准/修复 | 布局后创建 VOL 获得非零高度 |
| `fixedDynamicCrosshairYAndPane` | 基准/修复 | 动态 pane 的 y 与归属保留 |
| `fixedInitialBoundaryLoadsWithoutGesture` | 基准/修复 | INIT 不足时无手势即续拉 |
| `fixedOldInitBlockedAcrossAToB` | 基准/修复 | 原 A→B 锁等待 INIT 不再覆盖 B 数据 |
| `fixedOldRealtimeBlockedAcrossAToB` | 基准/修复 | 原 A→B 锁等待实时样例被丢弃 |
| `fixedOverlayArcUsesPatchedCore` | 基准/修复 | 实际覆盖物圆心不再误命中 |
| `fixedPaginationKeepsRealtime` | 基准/修复 | 分页在途时实时推送仍生效 |
| `fixedPainterVisitsLowBeforeHigh` | 基准/修复 | 模板实际绘制遍历低 z→高 z |
| `fixedPaneQuerySizeAndMove` | 基准/修复 | 动态 pane 查询、设高、迁移 |
| `fixedPartialStylePreservesDarkAndRestoresDefault` | 基准/修复 | StylesPatch 保留其他字段并可恢复默认 |
| `fixedPriceLineAnchorGeometry` | 基准/修复 | priceLine 从锚点到右边界 |
| `fixedRegisteredSpecDoesNotChangeMainAxis` | 基准/修复 | 公开轴 spec 生效且不改主轴 |
| `fixedSimpleAbsoluteMeaning` | 基准/修复 | 简单 absolute 只改变 y 参考系 |
| `fixedStackedLinesConversion` | 基准/修复 | MA+BOLL 纯线公共值域一致 |
| `localPaneOverlayTemplateBoundsAreNowRelative` | 基准/修复 | 实际模板收到局部高度与局部 pixelPoints |
| `realPatchBeforeAndAfter` | 基准/修复 | 历史 arc 端点 false，当前 true |
| `axisConstructionFixedButTemplateReadbackDropsFlags` | 差异复现 | 新轴 reverse=true，模板回读 false，Q02 |
| `barPaneRangeHelperDoesNotReachRenderer` | 差异复现 | BAR 范围转换/绘制差 40 px，Q03 |
| `dynamicDragFlagReadbackIsDisconnectedFromGestureState` | 差异复现 | 回读禁用，实际拖动仍从 80 改为 110，Q07 |
| `dynamicHeightSetterIsIgnored` | 差异复现 | 动态 pane 的 123 高度设置被忽略，Q07 |
| `figureAttrsAreNotCalledByActualDrawPipeline` | 差异复现 | 真实 BAR 绘制中 attrs 调用为 0，Q05 |
| `immediateRealtimeAfterInitIsDropped` | 差异复现 | Reset 前入队的合法首推丢失，Q01 |
| `immediateRealtimeWithPublicExternalScopeIsDropped` | 差异复现 | 公开 externalScope 构造独立复现首推丢失，Q01 |
| `negativeArcSweepPaintAndHitStillDisagree` | 差异复现 | 绘制 -90°、命中使用顺时针 270°，Q08 |
| `overlayPaneInputDoubleAddsTop` | 差异复现 | 显式传 pane 仍反算错误值，Q04 |
| `priceLineFormatterStillHardcodesMainPrecision` | 差异复现 | 品种精度 5，模板执行精度仍为 2，Q06 |
| `realtimeAcceptedBeforeSwitchCanPolluteSecondA` | 差异复现 | 锁等待经过 ABA 后旧推送落入第二次 A，Q01 |
| `secondaryAxisReturnsAbsoluteAsRelative` | 差异复现 | 副轴相对结果多了 pane top，Q02 |
| `selectedFixedZOverlayBecomesUnhittable` | 差异复现 | 固定层级对象选中后同位置不再命中，Q09 |
| `subPaneOverlayCanStillHitInMainPane` | 差异复现 | 副图水平线在 paneTop-20 仍可命中，Q04 |
| `subPaneRendererIgnoresBoundLogAndReverse` | 差异复现 | 实际 renderer 的 yOf 不消费 log/reverse，Q02 |

测试源、固定历史 before 实现、最终 JUnit XML、基线测试计数、oracle 输出和快照核对记录保存在 `/tmp/lkanvas-audit-round4-20261030/`，未写入仓库。临时目录不是长期制品；报告已记录关键输入、调度条件和实际结果，后续修复应将对应期望行为转为仓库中的回归测试。

证据的具体边界：

- Q01 的 ABA 用反射取得现有 Store 锁，只固定正常线程交错；数据仍经公开回调提交，未修改生产字段。首推丢失也经公开 externalScope 构造独立复现；不把调度条件隐藏为“所有时刻必现”。
- Q02/Q03 通过实际 renderer 的公开 draw 钩子读取其 yOf，读取后中止本次绘制；没有把另一份手写映射公式当成屏幕真值，也没有完成真机像素验证。
- Q05 实际调用 drawInstanceFigures，以记录型 DrawScope 接收矩形绘制，确认 attrs 没被执行；不是只测试 helper。
- Q04/Q09 调用实际 OverlayRenderer.hitTest；手势创建的剩余分支由静态调用链确认。Q08 记录平台绘制调用参数，不等于生成截图。
- 原 before/after 演练从 `b63d173^` 的历史 Figures.kt 独立重建：-0.5°端点样例仍为旧实现 false、当前 true；本次又确认覆盖物命中入口复用了当前实现。

## 6. 台账与后续同步能力

check.py 本次解析 287 行：**已声明 237、行为通过 33、已接线 2、不适用 15；结构可对应仍为 0**。33 包含 R01–R14；本次没有发现它们再次被跳过。validate_structure_map 已在 main 执行，本次单独执行也无错误；指标映射集合已人工与上游 27 文件核对齐全。

这些是应当关闭的原工具缺陷。与此同时，脚本不运行图形与并发行为，也不检查 helper 是否真的被 renderer 调用，缩略/计划路径仍被豁免。不能把“引用文件和测试存在”解释为“对应规则已进入执行链”。本轮 README/compatibility 中“drawExtraPane 消费绑定轴”“转换/绘制同源”“attrs 几何消费”“T01–T11 全项收口”等表述，仍超出了实际证据范围。

| 领域 | 已具备的局部同步基础 | 剩余维护成本 |
|---|---|---|
| 27 指标计算 | 规范 calc、同文件 Scratch、oracle 与双路径测试保留 | 公式补丁仍要保持两路径一致；本轮未改动指标模板源码 |
| Store/Host/ChartEngine | 数据操作有固定映射，消费者已有 token 拒绝入口 | 请求代次、key、引擎 sessionId 尚未贯通同一生命周期，Q01 |
| pane/轴/坐标 | metadata 有归属，构造桥接与纯线叠加改善 | 转换与绘制仍有独立范围/轴规则，局部与绝对坐标契约分裂，Q02/Q03 |
| 覆盖物模板 | 图形生成迁入模板，局部坐标及共享命中已有真实消费者 | 输入逆变换、创建、裁剪、格式与层级仍跨文件分散，Q04/Q06/Q08/Q09 |
| figure | main、主图额外轴、动态 pane 接入同一分发函数 | attrs 与固定 pane 仍旁路，同一模板补丁不能在全部 pane 自然生效，Q05 |
| Layout | getter 含动态 pane，内置 flags 可回读 | 两份禁用列表、只设置内置高度，Q07 |
| 真实源码补丁 | arc before/after 可重建，实际覆盖物命中已复用 | 必须继续核对绘制与命中共同描述，不只复核数学原语，Q08 |

下一轮按原标准至少应验证：

1. 同一不可变会话身份进入 INIT/分页/实时的最终写入和订阅门禁，覆盖 ABA、锁等待、队列积压及 INIT 后首推；既不接纳旧数据，也不拒绝合法新数据。
2. 每个 pane/轴的范围与投影只有一个实际来源；用绘制消费者验证相对/绝对转换、log/reverse、BAR 基线与多实例组合。
3. 创建、拖动、绘制、命中共同使用实例上下文，pane 平移与裁剪只在明确边界处理。
4. attrs/styles/实例集合在全部 pane 实际执行；测试从正式渲染入口进入，不能只直接调用新增 helper。
5. 配置保存/回读与真实交互共用状态，模板精度与格式复用原契约；台账按真实执行证据更新。

验收继续判断“一个上游变更块能否定位有限本地修改，并让共同输入得到对应结果”。平台绘制无需机械复制 DOM 文件层级，但领域规则不能借平台替代之名分成互不一致的多条路径。

## 7. 未检查范围与风险

本轮深入复核了第三轮修复涉及的 Store/Host/ChartEngine、pane/轴/转换、覆盖物绘制/命中/编辑、figure 调用入口、十字线、Layout 与验收工具，并保留样式、动画及指标默认数值基准检查。

未重做真机截图、完整触摸 E2E、TalkBack、Macrobenchmark、R8、发布制品及 Maven consumer；未重新认证既有性能报告。没有逐行重证 27 个指标的所有参数边界，也没有穷举全部样式、几何和图片编码。静态发现与运行探针结果已分别标明。

**本报告未实施修复。优先风险为 Q01 的旧会话数据污染与合法首推丢失，其次为副 pane 的坐标/渲染/编辑不一致。当前适合继续做有限调用链修复，不适合把测试全绿或发版声明视为完整结构与逻辑对齐验收。**
