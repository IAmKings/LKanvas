# LKanvas：KLineChart 的 Kotlin/Compose 原生移植 — 实施计划（M0→M2 Alpha 优先，进度允许继续 M3）

## 0. 已定决策（未获用户答复，按推荐项执行）

- **库名**：LKanvas（PRD 暂定名转正）。模块 `lkanvas-core` / `lkanvas-compose`，包名 `io.lkanvas.*`，示例应用 `app-sample`。不自称官方 KLineChart Android 版。
- **执行范围**：M0→M2 Alpha 完整纵向链路优先，每步带测试；完成后视进度推进 M3（全部指标/轴/主图类型）。M4–M6（全部覆盖物、存档、无障碍、性能冻结、Maven 发布）本轮不承诺。
- **全程中文 commit**，每完成一个 PRD 任务提交一次，提交信息格式：`feat(core): 数据模型与输入校验（R-01/T-03）`。

## 1. 环境事实（已勘察）

- 工程目录 `KKLineChart/` 目前仅有 `docs/PRD-*.md`，从零搭建，非 git 仓库。
- 上游基线源码快照：`/Users/pauldeman/Documents/ai_workspace/LKanvas/upstream-klinechart`（v10.1.0，含 27 指标/16 覆盖物源文件与 LICENSE/NOTICE，无 .git）。
- 本机已验证工具链：Gradle 9.7.0（已缓存）、AGP 9.3.1、Kotlin 2.4.10、JDK 21 工具链（已缓存）、Android SDK platforms 34–37、Node 25（TS 对照可运行）、模拟器镜像可用（android-emulator 插件可建 AVD 并构建/安装/截图）。

## 2. 工程结构与版本

```
KKLineChart/
├── settings.gradle.kts / gradle.properties / gradle/libs.versions.toml / wrapper(9.7.0)
├── lkanvas-core/        # 纯 Kotlin JVM 库（kotlin("jvm")）：数据模型、串行 Store、会话/revision、
│                        #   指标引擎与全部指标、视口/坐标、覆盖物领域状态、存档 DTO、格式化纯逻辑
├── lkanvas-compose/     # Android 库（com.android.library + compose）：KLineChart composable、
│                        #   Canvas 绘制、手势、无障碍语义、公开接入控制（依赖 core，禁止反向依赖）
├── app-sample/          # 示例应用：本地确定性 DataSource、实时模拟、指标/主图切换演示
├── third_party/klinechart-v10.1.0/   # 上游 src + LICENSE + NOTICE 快照（oracle 对照用，来源合规）
├── tools/oracle/        # npm(tsx) 工程：直接 import 上游 TS 指标函数，对固定输入产出 golden fixtures JSON
├── docs/                # 现有 PRD + compatibility.md 兼容台账（T-01）
└── .gitignore / README.md / NOTICE
```

版本组合：Gradle 9.7.0、AGP 9.3.1、Kotlin 2.4.10、JDK 工具链 21、minSdk 23（PRD 提案值）、compileSdk 36、Compose（AndroidX BOM，取本机缓存可用稳定版）、kotlinx-coroutines、kotlinx-serialization-json（存档 DTO）、JUnit4 + kotlin-test。CI 不在本轮（本地构建+模拟器验证代替）。

## 3. 阶段化任务与提交序列

### 阶段 M0：基线与工程（T-01/T-02）
1. `git init`（main 分支）、.gitignore、README、NOTICE → **commit：chore: 初始化仓库**
2. Gradle 多模块骨架 + version catalog + wrapper，三模块空实现可编译（core JVM 测试可跑）→ **commit：搭建 Gradle 多模块工程骨架**
3. 复制上游快照到 `third_party/`（含 LICENSE/NOTICE），建 `tools/oracle`，跑通最小 TS 对照（MA 一组数据→fixtures JSON→core 测试可读取）→ **commit：引入上游 v10.1.0 基线与 TS 对照 oracle**
4. `docs/compatibility.md`：上游公开能力→Kotlin 任务/测试映射、D-01/D-02/D-03 差异登记初版 → **commit：建立兼容台账（T-01）**

### 阶段 M1：数据与坐标基础（T-03~T-06，全部 JVM 测试）
5. **T-03 数据模型与校验（R-01）**：`Candle(timestamp:Long, open/high/low/close:Double, volume/turnover:Double?)`，OHLC 有限性与 low≤min(o,c)≤max(o,c)≤high、递增时间戳、重复/逆序/NaN 定位错误（操作+索引+原因），整次提交原子性。测试覆盖 A-01/A-02 与 F-01/F-05/F-06 子集 → **commit**
6. **T-04 串行数据变更（R-02 基础）**：Store 单线程串行提交；Reset/ReplaceLatest/AppendBatch/PrependHistory；每次成功返回 revision 回执；同时间戳 upsert 语义（等于末根替换、大于追加、小于报错）；分页边界完全重复幂等去重、同时间戳不同内容冲突报错；有界接收队列、容量上限默认 100k 超限显式拒绝；补历史保持锚点不跳变。测试 A-03/A-04/A-05 → **commit**
7. **T-05 会话门禁（R-03）**：Reset/切 MarketKey 生成新 sessionId；异步计算任务绑定 sessionId+dataRevision+实例配置版本；旧 session 结果一律丢弃（即使先发后至）；结果只在受控串行上下文提交；RenderSnapshot 版本一致性。测试 A-07/A-08/A-09 → **commit**
8. **T-06 视口与基础坐标（R-05 + 线性轴）**：barSpacing/gap/candle 中心/content bounds 契约（px 与 dp 分离）；滑动、缩放（触点/末根锚点）、min/max barSpacing、右留白、scrollToTimestamp、回到最新；时间↔索引↔像素 Double 往返（误差≤0.5px 预算）；前插历史时间戳锚点保持。线性轴（normal）范围与刻度计算（含全同值/空数据的有限默认范围）。测试 A-12/A-13/A-14 → **commit**

### 阶段 M2 Alpha：完整纵向链路（T-07~T-11、T-19、T-24 部分、T-10）
9. **T-09 指标引擎与增量契约（R-08 框架）**：IndicatorDefinition（模板注册、calc 语义、输出 key、plots、precision）/IndicatorInstance（实例 ID、参数、pane/axis 绑定、独立状态，不共享可变状态）；Missing 与有效值区分（不自动当 0、不输出 Infinity）；全量接口 + 增量接口（上一确定状态基线，末根替换从上一根重算、追加才推进基状态）；缓存键=session+revision+定义/参数版本；参数变更失效；抛错实例标记 Error 不拖垮其他。测试 A-24/A-25 基础（固定随机变更序列增量 vs 全量一致性、同名不同参数实例隔离）→ **commit**
10. **T-11 MA/VOL/MACD（I-01~I-03）**：严格按上游公式（MA 简单均值；VOL 均量线+涨跌色；MACD 的 EMA12/EMA26 种子=首值、DIF/DEA、柱=2*(DIF-DEA)），先手算样例钉死预期，再用 tools/oracle 生成 TS 对照 fixtures（有效值位置+Missing 掩码一并对照）；增量实现以探针证明不遍历全历史（工作量随参数而非 N）。测试 A-22/A-23 → **commit**
11. **T-07 原生 candle 绘制（R-07）**：`KLineChart(state, config)` composable；pane 布局（主图+VOL+MACD）；DrawScope 绘制 candle_solid（涨跌色/影线/实体）、网格、轴标签、最后价格线；只绘可见区间（viewport 裁剪），无逐 candle Composable 节点；Double 计算只在最后一步转 Float；明/暗内置主题。测试：JVM 侧几何断言 + 截图验证 → **commit**
12. **T-08 基础触摸与十字线（R-10/R-11）**：pointerInput 状态机——单指拖动、双指缩放、长按出十字线（所有 pane 对齐同一数据时间、当前 pane 显示本轴 y 值）、点击 candle 事件、第二指加入/取消/失焦结束交互；惯性滚动（基础版）；与父容器（LazyColumn/Pager）手势共存策略可配置。图例摘要（MA/VOL/MACD 数值、Missing 显示"—"）、时间/价格基础格式化（时区正确、UTC 存储不随显示变）。测试 A-31/A-32 基础 + 模拟器实操 → **commit**
13. **T-19 绘图框架与 segment（R-09 框架 + O-01）**：OverlayDefinition/Instance（稳定 ID、控制点、样式、锁定/可见、层级）；状态机 Idle/Creating/Selected/Dragging/Completed/Cancelled，事件恰好一次；时间锚点存储（非裸 index）；磁吸（normal/weak/strong）；命中优先级（控制点>选中对象>其他对象>图表拖动）；line/point/text 三个基础图形；segment 两点创建/拖端点/整体拖动/取消/删除。测试 A-27/A-28 基础 → **commit**
14. **T-10 DataSource 与加载状态（R-04）**：历史分页（双向 hasMore 独立、单游标防重复）+ 实时订阅契约接口；加载/空/失败/重试/断流状态可区分，分页失败保留图表与视口；本地确定性 DataSource 示例（固定种子随机行情）。测试 A-10/A-11 → **commit**
15. **app-sample 集成 + 模拟器验证**：示例 App 演示基础图表、实时末根更新、追加、分页加载、切品种、MA/VOL/MACD、segment 画线；用 android-emulator 插件构建安装、截图确认首帧/交互正确 → **commit：示例应用与 Alpha 链路集成验证**
16. 更新 compatibility.md 状态（已实现已验收/有意差异/延期）→ **commit：更新兼容台账至 Alpha 状态**

### 阶段 M3（视进度推进，每项独立提交，可中断续作）
17. T-15 PatchHistory 显式历史纠正（后缀失效重算，A-06）
18. T-12 均值/窗口指标组：EMA/SMA(上游递推口径)/BOLL/BIAS/BAIS…/AO/MTM/ROC/PSY（I-04~I-12）
19. T-13 振荡/方向/量指标组：KDJ/RSI/WR/CCI/DMI/BRAR/CR/VR/EMV（I-13~I-21）
20. T-14 递推/累计指标组：SAR/TRIX/OBV/PVT/AVP/DMA（I-22~I-27，含历史修正与状态回退）
    （每个指标：公式冻结说明+手算样例+oracle fixtures+Missing 位置对照+增量或全量声明）
21. T-16/T-17 完整 pane（增删/排序/调高）+ percentage（首可见 close 基准，零基准按 D-01 显"—"）+ logarithm（sign(v)*log10(1+|v|) signed-log）+ 自定义轴保护（A-16/A-17/A-18）
22. T-18 剩余五种主图（candle_stroke/up_stroke/down_stroke/ohlc/area）+ 完整样式（价格标记、可见最高最低、面积填充、图例操作）（A-19/A-20/A-21）

## 4. 验证策略（对应 PRD 三层）

1. **独立正确性**：每个指标先手算固定样例写入 JVM 测试，防复制上游错误。
2. **TS 对照**：tools/oracle 用 tsx 直接运行 third_party 上游源码对同一输入产出 golden fixtures（值+Missing 掩码），core 测试逐位置比对，误差 `1e-9+1e-10*max`；D-01/D-03 差异项使用独立预期。
3. **设备验收**：app-sample 经 android-emulator 插件安装运行，截图核对绘制/手势/十字线；JVM 不可验证的项在台账标注"待设备验收"。

每阶段跑 `./gradlew :lkanvas-core:test :lkanvas-compose:test :app-sample:assembleDebug` 全绿后才 commit。

## 5. 关键边界与风险处理

- 不照搬上游 Store/View/Widget DOM 结构，按 PRD 4.2 领域模型重设计；仅映射领域行为与公式。
- 上游指标"缺失字段默认 0"→ 本项目 Missing（D-03）；percentage 零基准显"—"（D-01）；非线性轴斐波那契按价格投影（D-02，M4 范围）。三项均登记台账。
- 指针回调内不做同步全量计算/IO；指标计算在后台调度器、结果经 session/revision 门禁提交。
- 本轮明确不做（留后续会话，遵循 PRD 里程碑）：全部 16 覆盖物（仅 segment）、存档/恢复、完整无障碍、PNG 导出、性能冻结与 Maven 发布。
- 若 Alpha 阶段发现数据/坐标/性能路径不成立（PRD 10.1），先修基础再扩大范围，不"以后优化"式跳过。