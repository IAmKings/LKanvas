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
  - **对齐矩阵**：check.py v4 四态校验通过（行为通过=18），SLA-001~016 审计项全部「行为通过」；真实源码补丁演练 4（upstream-sync.md）证明补丁可定位有限本地改动
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
