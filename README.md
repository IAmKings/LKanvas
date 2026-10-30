# LKanvas

KLineChart 的 Android / Jetpack Compose 原生实现。目标是把上游
[KLineChart](https://github.com/klinecharts/KLineChart)（v10.1.0）的领域能力——指标、覆盖物、坐标轴、
数据接入模型——移植为纯 Kotlin 计算核心 + Compose 原生绘制的图表库。

> 本项目为独立维护的社区移植，不是 KLineChart 官方 Android 版本。

## 模块

| 模块 | 说明 |
|---|---|
| `lkanvas-core` | 纯 Kotlin/JVM 库：数据模型与校验、串行数据变更、会话/版本门禁、27 种内置指标（全量与增量）、三种纵轴+自定义轴、视口坐标、覆盖物领域状态、DataSource 契约 |
| `lkanvas-compose` | Android Compose 库：`KLineChart` 组件（六种主图类型、三 pane、明暗主题）、Canvas 绘制、手势状态机、segment 画线 |
| `app-sample` | 示例应用：本地确定性行情、实时末根更新、分页、画线、图型/轴切换 |

## 当前进度（0.5.0：多轴完整版 + 动态 pane 完整版）

- **27/27 内置指标**：全部与上游 v10.1.0 TS oracle 逐位对照通过（值与 Missing 位置），附手算样例与增量/全量一致性测试
- **16/16 覆盖物**：共用生命周期状态机，brush 5000 点上限；斐波那契价格空间投影（D-02）
- **六种主图类型 + 三种轴 + 自定义轴**；基础图形 **7/7** 原语（命中+绘制）
- **数据链路**：Reset/Replace/Upsert/Append/Prepend/PatchHistory、会话门禁、分页加载状态机
- **交互**：平移/双指缩放/长按十字线/点击/惯性/pane 分隔条/轴区缩放
- **存档**：版本化 schema 导出/恢复、恢复门禁（防空状态覆盖）
- **无障碍**：TalkBack 摘要（OHLCV+方向词+MA）与五个替代动作
- **PNG 导出**：等比缩放三 pane 完整导出（真机验证）；**诊断**：可开关零开销收集器
- **发布链路**：Maven 本地制品（core JAR+sources / compose AAR）、R8 release 真机验证、独立 consumer 仅凭制品编译通过（A-46）
- **0.6.0 结构与逻辑对齐完整版**（审计 16 项发现全部收口，验收依据 `docs/alignment/structure-logic-audit-2026-10-28.zh-CN.md` + gap-matrix SLA-001~016）：
  - **结构镜像**：27 指标 / 16 覆盖物模板拆分为上游同名文件（`extension/indicator|overlay/*.kt` ↔ `src/extension/*/*.ts`），每模板含上游逐行对照的规范 `calc`；全量路径走 calc、Scratch 增量为优化路径，27 模板双路径互验逐位一致（DualPathParityTest）+ oracle 27/27
  - **数据路径**：`core/store/Store.kt` 镜像上游 Store 数据段——双向分页边界触发、more 同方向更新、resetData 退订→变更→清 loading→init 顺序、请求代次门禁（本地增强：在途切品种旧回调/推送丢弃）
  - **注册统一**：指标/覆盖物/轴/图形模板注册与创建/渲染/命中/手势事件共用同一张全局表（宿主注册端到端可用）
  - **严格上游默认**（破坏性变更，见 compatibility.md）：init 不预置指标、createIndicator 缺省新建指标 pane 且 isStack=false、overrideIndicator 全字段、payload 嵌套 data:{prev,current,next}、executeAction 抑制反向广播
  - **契约收口**：视口锚点/右锚滚动模型/独立 maxOffset/小数 rightOffset/to 钳制/动画；log 轴 convert 与绘制同源；ConvertFilter；scroll/zoomEnabled 共用入口；candle.type/逐根配色/格式链消费；覆盖物模板命中与交互创建配置
  - **对齐矩阵**：check.py v4 四态校验通过（行为通过=18），SLA-001~016 审计项全部「行为通过」；真实源码补丁演练 4（upstream-sync.md）证明补丁可定位有限本地改动- **0.6.0 结构与逻辑对齐完整版**（审计 16 项发现全部收口，验收依据 `docs/alignment/structure-logic-audit-2026-10-28.zh-CN.md` + gap-matrix SLA-001~016）：
  - **结构镜像**：27 指标 / 16 覆盖物模板拆分为上游同名文件（`extension/indicator|overlay/*.kt` ↔ `src/extension/*/*.ts`），每模板含上游逐行对照的规范 `calc`；全量路径走 calc、Scratch 增量为优化路径，27 模板双路径互验逐位一致（DualPathParityTest）+ oracle 27/27
  - **数据路径**：`core/store/Store.kt` 镜像上游 Store 数据段——双向分页边界触发、more 同方向更新、resetData 退订→变更→清 loading→init 顺序、请求代次门禁（本地增强：在途切品种旧回调/推送丢弃）
  - **注册统一**：指标/覆盖物/轴/图形模板注册与创建/渲染/命中/手势事件共用同一张全局表（宿主注册端到端可用）
  - **严格上游默认**（破坏性变更，见 compatibility.md）：init 不预置指标、createIndicator 缺省新建指标 pane 且 isStack=false、overrideIndicator 全字段、payload 嵌套 data:{prev,current,next}、executeAction 抑制反向广播
  - **契约收口**：视口锚点/右锚滚动模型/独立 maxOffset/小数 rightOffset/to 钳制/动画；log 轴 convert 与绘制同源；ConvertFilter；scroll/zoomEnabled 共用入口；candle.type/逐根配色/格式链消费；覆盖物模板命中与交互创建配置
  - **对齐矩阵**：check.py v4 四态校验通过（行为通过=18），SLA-001~016 审计项全部「行为通过」；真实源码补丁演练 4（upstream-sync.md）证明补丁可定位有限本地改动
- **0.7.0 复审收口**（复审 R01-R14 全项，依据 `docs/alignment/structure-logic-audit-2026-10-30.zh-CN.md` + gap-matrix §14）：数据路径组合正确性（分页不再杀实时订阅、检查点 key 绑定防串品种、提交后自动续拉——parity-9 组合差分）；StylesPatch 全可空镜像（局部补丁不重置其他分支）；convert 上游原义（absolute=y 参考系/paneId 选 pane）；轴实例表/pane 集合单一来源；统一 figure 管线（BAR 分发+visible+attrs）与覆盖物统一模板执行（16 模板图形生成迁入、无名称分支、pane 感知）；Crosshair 语义（null 清除/x 推导索引）；动画真过渡与 LayoutOptions 补齐；arc 端点容差采纳（演练 5 before/after）；check.py SLA 行纳入证据检查
- **0.8.0 第三轮复审收口**（T01-T11 全项，依据 `docs/alignment/structure-logic-audit-round3-2026-10-30.zh-CN.md` + gap-matrix §14 复审追踪）：写入时会话门禁（expectedSessionId 随 ChangeRequest 到引擎唯一写入入口复核，A→B→A 不回退/不污染——parity-10 组合差分）；pane/轴上下文单一来源（轴按所属 pane 投影、paneVisibleRange 共享值域转换=渲染、副 pane 轴不再进主图）；覆盖物编辑与十字线 pane 上下文（手势反算/局部坐标/副图十字线与标签）；figure 管线全 pane 统一（main/额外轴 BAR 分发、attrs=几何/styles=样式分离）；priceLine 上游几何+精度格式注入、覆盖物命中统一 core FigureHit、绘制升序/命中降序；LayoutOptions 存取同源、backgroundColor 无条件合成、check.py 支持 R 编号并接入 structure-map 校验
- **0.9.0 第四轮复审收口**（Q01-Q09 全项，依据 `docs/alignment/structure-logic-audit-round4-2026-10-30.zh-CN.md` + gap-matrix §15）：接受时点会话身份 dataEpoch（ABA 旧推送丢弃/INIT 后首推不丢/拒绝的 INIT 不订阅——parity-11）；副轴坐标相对化与门面单次转换、drawExtraPane 真实消费共享值域与绑定轴、轴模板回读全字段；覆盖物输入反算去双 top、CREATE 上下文、pane 裁剪与命中边界；attrs 几何进 drawBarPlots、VOL/MACD 全实例走统一 figure 管线（涨跌色经模板 styles 回调——上游同模型）；priceLine 品种精度+千分位格式链；arc 绘制跨度与命中共用顺时针规则；fixedZLevel 不浮起但不禁命中；动态 pane 拖动禁用单一状态与全 pane 高度路由。同时修正 0.8.0 文档中超出证据的表述（drawExtraPane 绑定轴消费等在 0.8.0 实际未落盘，本轮已真实落地）
- **0.10.0 第五轮复审收口**（N01-N07 全项，依据 `docs/alignment/structure-logic-audit-round5-2026-10-31.zh-CN.md` + gap-matrix §16）：N01 INIT 真实提交结果反馈（Rejected 退订+key 门禁——非法 INIT 后新推送不写旧会话）；N02 副图绑定轴 per-instance（未绑定轴不改变默认投影）；N03 内置 pane 值域全实例聚合；N04 attrs pane 相对坐标+真实上下文+x/width 键；N05 小数折叠+实例精度覆盖；N06 控制点 pane 裁剪；N07 fixedZLevel 不禁编辑
- **Tooltip 渲染完整版（0.3.0）**：showRule/showType/features 全消费（对齐上游 CandleTooltipView），feature 图标命中→三种 ActionType；样式面板全部开关联动
- **回调消费端（0.3.0）**：覆盖物 onClick/onDoubleClick/onRightClick(长按)/onTouch 系手势分发、performEventPressedMove 委托、indicator draw 钩子（IndicatorDrawContext）、时区接线；setStylesStrict 修复切回默认值边界
- **多轴渲染 Alpha（0.4.0）**：createYAxis/removeYAxis/overrideYAxis 渲染消费——绑定自定义轴的指标按独立值域绘制并获独立标签列（reverse 即时生效）
- **性能门槛全绿（0.4.0，PRD 7.3）**：B-01 启动 387.6ms（3.9 倍余量）、B-02 扩展负载帧耗时 P50 69→23ms（-67%）、B-03 100k 就绪 1895ms 进入门槛 + 首批可交互 54ms、B-04 突发收敛 158ms（12.7 倍）、B-06 30 分钟热稳定零崩溃零 ANR 内存稳定；过程修复 3 个真缺陷（视口重绘订阅缺失/plotsFor 假列/tooltip 热点）
- **多轴完整版（0.5.0）**：position/inside 标签位置、reverse、自定义变换轴（CustomYAxis 契约）、删除保护、排序同步
- **动态 pane 完整版（0.5.0）**：指标绑定 paneId 自动建删副图、独立渲染（公共值域/draw 钩子）、分隔条拖动（per-pane 覆写）、排序（moveExtraPane）、存档 extraPaneIds（旧版兼容）
- **长尾收口（0.5.0）**：诊断可视化（渲染计时+面板+导出）、平台 fling（OverScroller native spline）、figure 宿主自定义模板样例、isStack 语义、removeIndicator 全删修复
- **B-01 初测**：10k candle 三指标末根更新 P95=0.270ms（D1 目标 4ms 的 1/15）
- 待 1.0.x：外部依赖项（R8 压缩口径帧数据待 MIUI perfetto 兼容、B-05 retained heap 真机 dump 口径）、pane 高度比例入存档（低价值）、上游新版本跟进（见 upstream-sync.md 每月 compare）

## 从 KLineChart (WebView) 迁移到 LKanvas

WebView 迁移者按上游文档习惯调用同名 API，仅初始化方式不同：

```kotlin
// TS: const chart = init('container', { locale: 'zh-CN' })
val chart = LKanvasChart.init()                    // 门面持有内核与 Compose 状态

// TS: chart.setDataLoader({ getBars({type, timestamp, callback}) {...} })
chart.setDataLoader(object : DataLoader {
    override fun getBars(params: DataLoader.GetBarsParams) { /* init/forward/backward */ }
    override fun subscribeBar(params: DataLoader.SubscribeBarParams) { /* 实时推送 */ }
})

// TS: chart.createIndicator({ name: 'MA', calcParams: [5] })
chart.createIndicator("MA", calcParams = listOf(5.0))

// TS: chart.createOverlay({ name: 'segment', points: [...] })
chart.createOverlay(OverlayCreate("segment", points = listOf(...)))

// TS: chart.setStyles('dark') / subscribeAction('onCrosshairChange', cb)
chart.setStyles("dark")
chart.subscribeAction(ActionType.ON_CROSSHAIR_CHANGE) { }
```

- 完整 API 面与上游对应关系：`docs/alignment/gap-matrix.md`（257 行，**E=0**，0 unknown）
- parity 用例：上游文档示例的迁移测试见 `lkanvas-compose/src/test/.../parity/`
- 迁移章节完整版随 0.2.0 发布（本文上方示例 + parity 用例即最小迁移面；多轴/事件回调族细节见 gap-matrix）

## 上游基线
## 上游基线

- 上游版本：KLineChart **v10.1.0**（快照见 `third_party/klinechart-v10.1.0/`，仅用于开发期 TS 对照 oracle，不进入发布制品）。
- 功能对齐范围、差异登记与验收口径见 [PRD](docs/PRD-KLineChart-Kotlin-Compose.zh-CN.md) 与
  [兼容台账](docs/compatibility.md)。

## 构建

```bash
./gradlew :lkanvas-core:test          # 纯 JVM 测试（数据/指标/坐标）
./gradlew :app-sample:assembleDebug   # 构建示例应用
```

要求：JDK 21、Android SDK（compileSdk 36，minSdk 23）。

## 许可证

本项目基于 Apache License 2.0 发布；派生自 KLineChart 的算法与几何实现见 `NOTICE` 与兼容台账中的来源映射。
