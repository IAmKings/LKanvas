# LKanvas 兼容台账（compatibility ledger）

依据 PRD 3.2/3.3 与 R-17/T-01 建立。状态取值：**已映射待实现 / 已实现已验收 / 有意差异 / 平台不适用 / 明确延期**。
1.0 前不允许任何公开能力处于「未知」状态。每项实现时回填「Kotlin 位置」与「测试」。

- 上游基线：KLineChart **v10.1.0**（`third_party/klinechart-v10.1.0/`，Apache-2.0）
- 对照 oracle：`tools/oracle/`（tsx 运行上游 calc → golden fixtures → `lkanvas-core` JVM 测试逐位置比对）
- 数值误差：`abs(a-b) <= 1e-9 + 1e-10*max(abs(a),abs(b))`（PRD 8.2）

## 1. 有意差异登记（冻结）

| ID | 主题 | 上游行为 | LKanvas 行为 | 依据 |
|---|---|---|---|---|
| D-01 | percentage 轴零基准 | 基准 close 为 0 时产出 NaN/Infinity 或虚假涨幅 | 显示「—」并提供状态；像素范围保持有限 | PRD R-06 |
| D-02 | 非线性轴上斐波那契 | 屏幕空间插值层级，标签值与线位置可能不一致 | 先按价格计算层级再投影到轴 | PRD R-09（M4 范围） |
| D-03 | 指标必需字段缺失 | 缺失字段按 0 参与计算（如 `volume ?? 0`） | 公式必需字段缺失 → Missing；输入本身为有效 0 仍按公式处理 | PRD R-08 |

## 2. 内置指标（27，PRD 6.1）

**M3 全部完成**：27/27 指标均与上游 v10.1.0 TS oracle 逐位对照通过（值与 Missing 位置一致，误差 1e-9+1e-10·max），并附手算独立样例与增量/全量一致性测试。Kotlin 位置：`lkanvas-core/src/main/kotlin/io/lkanvas/core/indicator/builtin/`。

| 编号 | 名称 | 增量能力 | 备注 |
|---|---|---|---|
| I-01 | MA | 增量 | 滚动窗口和 |
| I-02 | VOL | 增量 | 缺失 volume → Missing（D-03） |
| I-03 | MACD | 增量 | EMA 种子=前 p 根 SMA |
| I-04 | EMA | 增量 | 种子=前 p 根 SMA |
| I-05 | SMA | 增量 | 上游递推口径 (Cw+prev(N-w+1))/(N+1) |
| I-06 | BOLL | 增量 | MID 滚动和 + 窗口偏差 O(p) |
| I-07 | BBI | 增量 | 多窗口 MA 均值 |
| I-08 | BIAS | 增量 | 均值为零 → Missing（R-08 禁止 Infinity） |
| I-09 | AO | 增量 | HL/2 双窗口 |
| I-10 | MTM | 增量 | 回看派生 |
| I-11 | ROC | 增量 | 回看为零 → 0（上游显式约定） |
| I-12 | PSY | 增量 | 涨跌标志派生 |
| I-13 | KDJ | 增量 | 窗口高低 O(p) + K/D 递推（种子 50） |
| I-14 | RSI | 增量 | RMA 递推（i>=p 有效） |
| I-15 | WR | 增量 | 窗口极值，同价取 0 |
| I-16 | CCI | 增量 | TP 滚动和 + 平均偏差 |
| I-17 | DMI | **全量** | ADXR 需历史 ADX 回看 |
| I-18 | BRAR | 增量 | 四项滚动和 |
| I-19 | CR | **全量** | 带前移 MA 需序列回看；复现上游 list[-1]→undefined |
| I-20 | VR | 增量 | 缺失 volume 窗口 → Missing（D-03） |
| I-21 | EMV | 增量 | 缺失 volume → Missing；有效零量 → 0 |
| I-22 | SAR | 增量 | 趋势状态机 |
| I-23 | TRIX | 增量 | 三级 EMA + 固定大小环形历史 |
| I-24 | OBV | 增量 | 历史由 candle O(N) 派生 |
| I-25 | PVT | 增量 | volume 缺失按上游显式取 1 |
| I-26 | AVP | 增量 | 累计零量 → Missing |
| I-27 | DMA | 增量 | 双 MA 差由窗口派生 |

## 3. 覆盖物（16，PRD 6.2）

**M4 全部完成（16/16）**：Kotlin 位置 core `overlay/`（模板/几何/管理器）+ compose `draw/OverlayRenderer.kt`（渲染与命中）。全部共用生命周期状态机（创建/选中/拖动/锁定/删除恰好一次事件）、时间锚点存储、磁吸、层级命中优先级。

| 编号 | 名称 | 控制点 | 状态 | 关键验收 |
|---|---|---|---|---|
| O-01 | segment | 2 | 已实现已验收 | 两点/拖端点/整体/取消/命中（真机） |
| O-02 | straightLine | 2 | 已实现已验收 | 双向延伸 Liang-Barsky 裁剪 |
| O-03 | rayLine | 2 | 已实现已验收 | 单向射线头端固定 |
| O-04 | horizontalRayLine | 2 | 已实现已验收 | 方向由第二点符号决定 |
| O-05 | horizontalSegment | 2 | 已实现已验收 | 水平约束同 y、区间有序 |
| O-06 | horizontalStraightLine | 1 | 已实现已验收 | 全宽 + 价格锚点 |
| O-07 | verticalRayLine | 2 | 已实现已验收 | 垂直射线方向 |
| O-08 | verticalSegment | 2 | 已实现已验收 | 垂直约束同 x |
| O-09 | verticalStraightLine | 1 | 已实现已验收 | 全高 + 时间锚点 |
| O-10 | parallelStraightLine | 3 | 已实现已验收 | 过第三点平行、垂直退化两竖线 |
| O-11 | priceChannelLine | 3 | 已实现已验收 | 主线+平行+间距延伸线（上游 count=1） |
| O-12 | fibonacciLine | 2 | 已实现已验收 | 比例[1,.786,.618,.5,.382,.236,0]；价格空间层级投影（D-02，真机验证） |
| O-13 | priceLine | 1 | 已实现已验收 | 水平价格线 + 价格标签 |
| O-14 | simpleAnnotation | 1 | 已实现已验收 | extendData 文本 |
| O-15 | simpleTag | 1 | 已实现已验收 | 带底色标签 |
| O-16 | brush | 连续 | 已实现已验收 | 5000 点上限恰好一次报告、4px 去抖连笔（真机验证） |

## 4. 其他能力

| 能力 | 上游位置 | 状态 |
|---|---|---|
| 主图类型 6 种（candle_solid/stroke/up_stroke/down_stroke/ohlc/area） | common/Styles.ts、view/ | **已实现（M3/T-18）**：六形态 + 可见高低标记 + area 渐变；真机验证空心蜡烛/轴切换 |
| 轴 3 种 + 自定义（normal/percentage/logarithm） | src/extension/y-axis/ | **已实现（M3/T-16、T-17）**：core `axis/YAxisSpec.kt` 统一规格；signed-log 负/零/小正值支持；percentage 零基准按 D-01；自定义轴 A-18 保护（非单调/超量/非有限降级） |
| 基础图形 7 种（circle/line/polygon/rect/text/arc/path） | src/extension/figure/ | **已实现（7/7）**：core `figure/Figures.kt` 七种不可变原语 + FigureHit 命中（圆环/边/面/角度/折线采样）+ 有限性/退化校验；compose `FigureRenderer.kt` 绘制映射，与命中同一几何 |
| 存档/恢复 | — | **schema 与导出/恢复已实现（M4/T-25）**：core `archive/ChartArchive.kt`（schemaVersion=1、1MiB/500 覆盖物上限显式拒绝、损坏整次失败原文保留、未知模板保留、创建中不导出）；A-36/A-38 基础测试通过 |
| 数据接入（loadMoreData/DataSource 语义） | Store.ts、docs/guide/ | **已实现已验收（Alpha）**（core `datasource/`：HistorySource/ChartLoader/确定性示例；原生等价而非逐字兼容） |
| PatchHistory 显式历史纠正 | — | **已实现（M3/T-15）**：A-06 测试（最早/中间/末根纠正后与全量一致） |
| 图例/tooltip/价格线/主题/格式化 | view/、common/ | **基础摘要已实现（Alpha）**（OHLC/MA 图例、末价线标签、明暗主题、时间/价格格式化）；完整 tooltip/资源扩展待 M4 |
| 数据模型/串行变更/会话门禁/视口 | Store.ts 等 | **已实现已验收（Alpha）**（core `data/`、`store/`、`compute/`、`viewport/`；A-01~A-15 相关 JVM 测试通过） |
| 手势（拖动/缩放/十字线/点击） | Event.ts | **基础已实现（Alpha）**（compose `gesture/ChartGestures.kt`；真机验证）；轴拖动缩放/pane 分隔条待 Beta |
| setStyles/createIndicator 等 JS API | Chart.ts | **平台不适用**：JS 方法名/回调签名不逐字兼容（PRD 3.2），以 Kotlin 公开控制接口等价提供 |
| DOM/CSS/CanvasRenderingContext2D/鼠标右键 | — | **平台不适用**：以 Compose 手势/无障碍等价 |
| WebView/JS 桥/任意 JS 插件运行 | — | **平台不适用**（PRD 1.2 生产路径禁用） |
| PNG 导出/诊断 | — | **已实现（M5/T-29、T-30）**：compose `export/ChartSnapshotExporter.kt`（等比缩放三 pane 完整导出、4096 上限、可选十字线、单 revision；真机验证）；core `diagnostics/ChartDiagnostics.kt`（默认关闭零开销、环形缓冲、耗时定位） |
| 存档/恢复 | — | 明确延期（M4/T-25、T-26） |
| 无障碍完整适配 | — | 明确延期（M4/T-27；Alpha 未提供语义节点，已在已知问题登记） |
| 多图联动/回放/撤销重做/云同步 | — | 明确延期（P2，PRD 3.3） |

## 5. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-25 | 初版：基线固定 v10.1.0；27 指标/16 覆盖物/其他能力登记；D-01/D-02/D-03 差异预登记 |
| 2026-10-26 | **Alpha（M0–M2）完成**：数据模型/串行变更/会话门禁/视口坐标（A-01~A-14 JVM 测试）；指标引擎+MA/VOL/MACD（oracle 逐位对照）；candle_solid 三 pane 绘制；手势/十字线/图例；覆盖物框架+segment；DataSource 契约+加载协调器；真机（小米 23078PND5G）视觉验收通过 |
| 2026-10-26 | **M3 完成**：27/27 指标（oracle 逐位对照 + 手算 + 增量一致性）；PatchHistory（A-06）；normal/logarithm/percentage/自定义轴（A-17/A-18）；六种主图类型 + 可见高低标记；真机验证图型/轴切换 |
| 2026-10-26 | **M4 核心完成（Beta）**：16/16 覆盖物（几何约束/平行通道/斐波那契 D-02/brush 5000 点上限；真机验证斐波那契与 brush 连笔）；存档 schema 与导出/恢复（A-36/A-38 基础）；手势 brush 连笔采样 |
| 2026-10-26 | **M4 收尾完成**：恢复门禁与保存事件抑制（A-37，含 compose 层接线测试）；无障碍摘要与五个替代动作（A-40 基础，真机 TalkBack content-desc 验证）；pane 分隔条拖动 + 轴区拖动缩放（A-33，真机验证）。**192 测试 0 失败** |
| 2026-10-26 | **M5 启动**：PNG 导出（T-29，ChartRenderer 可重放渲染器 + 等比缩放三 pane 完整导出，真机验证 1080×1600）；诊断设施（T-30，可开关零开销 + B-01 JVM 初测 P95=0.270ms 优于 D1 目标 15 倍） |
| 2026-10-26 | **M6 前置完成**：基础图形 7/7（core 原语+命中+compose 映射，修 arc 全圆取模坍缩 bug）；Maven 本地发布（core JAR+sources / compose AAR，Apache-2.0 POM）；app-sample R8 release（931KB）真机运行正常；独立 consumer 仅凭 Maven 制品编译通过（A-46）；上游同步演练 #1（T-33：v10.1.0 后唯一提交为文档类→不适用，见 upstream-sync.md） |
| 2026-10-26 | **T-24 完整版 + A-41**：ChartLocale 中英文词条（摘要/方向词/无障碍动作，宿主可整体替换）；LegendData + legendSlot 插槽与 LegendMode（R-11 slot 定制）；右轴留白随字体缩放 + 主 pane 35% 保底钳制（真机 font_scale=2.0 与横屏验证）。**208 测试 0 失败** |
| 2026-10-28 | **对齐计划批次 2~6 全部完成（V3 判定文件执行）**：LKanvasChart 门面（数据 DataLoader/事件 6 种/视口/配置/覆盖物与指标 CRUD/坐标转换/生命周期）；27+16 模板全注册并元数据自动 diff 0 差异；Styles 树 43 接口+默认值对照+setStyles 深合并；figure 注册 API；demo-checklist 人工验收无阻断；consumer 改门面 API 重编通过。**E 156→55**（208+? 测试全绿） |
| 2026-10-28 | **批次 5 余量完成**：StylesThemeBridge（Styles→ChartTheme 桥接+hex 解析）、CandleBarColorCallback 双支持、门面 styles Compose 可观察（修复不重组缺陷，真机双向验证）；consumer 重发制品 |

## 6. 已知问题与后续计划

- 基础图形 circle/polygon/rect/arc 的宿主自定义模板样例待补（原语与命中已实现）。
- 无障碍余项：TalkBack 遍历覆盖物列表待补；字体缩放 2.0 与横屏已真机验证（A-41 达标，允许降级）。已知 S2：可见最高/最低标记在 2 倍字体下可溢出内容区（不阻断）。
- 诊断可视化（宿主诊断入口 UI/导出）待 T-30 余量；当前经 ChartDiagnostics API 读取。
- 性能门槛（B-02 容量/突发/泄漏/持续运行）尚未完成：B-01 增量路径 JVM 初测已达标（P95=0.270ms vs 4ms），Macrobenchmark 冻结设备测量（真机帧率/内存/启动）待 T-34。
- D8 构建 WARNING：Kotlin metadata 重写 "Should never be called"（R8 已知噪音，debug 运行正常）；R8 release 验证在 M6/T-31。
- 惯性滚动为基础指数衰减实现，未接入 Android fling 动画曲线（后续优化）。
- pane 增删/排序为固定三 pane 布局（分隔条调高已支持），动态 pane 列表管理待完整版。
- PVT volume 缺失按上游显式取 1、ROC 零回看取 0、AVP 缺失按 0：上游明确代码行为，逐位对齐优先（登记备查）。
