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
| 2026-10-28 | **0.2.0 发版（对齐矩阵 E=0）**：批次 5 余量收口——utils 22 项落地（严格对齐上游 format.ts 的 K/M/B 分档与 0{count} 折叠）；Viewport 双向 minVisible 钳制（CH-036/037）；多轴五方法 createYAxis/removeYAxis/getYAxes/overrideYAxis/overrideXAxis（YAxisInfo 记录层，主 pane 默认轴与指标绑定删除保护，CH-053~057）；registerStyles/XAxis/YAxis + setStyles(名) 查注册表（IDX-037/040/041）；Indicator 实例 paneId/yAxisId/styles 全链 + PlotDef.styles/attrs 建模位（IND-002/003/014/017）；Overlay 实例 groupId/currentStep/fixedZLevel/modeSensitivity/styles 全链（OVL-020/022/026/031/036/039，修 OverlayFilter.groupId 命中方向）+ performEvent/on 事件回调族建模位（OVL-006~008/011/012/014~016）；TYP-010 十一枚举同名化。矩阵最终 **A=15/B=227/C=9/D=6，E=0，unknown=0**（check.py 七面校验）；compose 77+core 全部测试绿；app-sample assembleRelease 构建通过；**0.2.0 制品重发本地 Maven，consumer 仅凭制品重编通过（A-46 保持）** |
| 2026-10-28 | **绘制端 Styles 细粒度消费（0.2.0 后余量）**：ChartTheme 扩展 21 字段（grid show/竖线/虚线、separator 色、priceMark 显隐与高低色、末价线显隐/虚线、十字线三开关/虚线/线宽、area 线色线宽、指标线宽/虚线、lastValueMark 末值点），StylesThemeBridge 全量映射 + 渲染端（ChartRenderer/PaneRenderers）接线；Bridge 测试 +2。语义修正：上游 separator.size 为 pane 间隙语义→线宽固定细线。真机（小米 23078PND5G）验证：网格隐藏即时生效、分隔线 #76808F 变色、样式面板全走 setStyles 深合并；修复样例面板分隔线色「原样设回」逻辑（改为 #76808F↔#33353D 循环）。全量测试绿 + assembleRelease 构建通过 |
| 2026-10-28 | **0.3.0 批次 7（Tooltip 渲染完整版）**：TooltipRenderer 对齐上游 CandleTooltipView/IndicatorTooltipView——showRule 三档（上游 isDrawTooltip 语义）、showType 双形态（standard 行式/rect 卡片 title 左 value 右）、candle 图例 OHLCV+涨跌 priceMark 着色、指标组行、副图 VOL/MACD 顶部图例；features 图标渲染（path 比例折线/icon_font）+ 命中区记录→手势命中→executeTooltipFeatureClick→三 ActionType 全链（EVT-007~009 收口）；KLineChart styles 参数（null 保持 ChartTheme 行式，向后兼容）；**发现并修复 skip-defaults 深合并边界**：切回默认值被「==默认值视为未提供」吞掉→门面新增 setStylesStrict，样例面板全部开关改走严格合并（真机双向切换验证） |
| 2026-10-28 | **0.3.0 批次 8（回调/钩子消费端）**：8a OVL 事件回调族手势分发（轻点 onClick/双击检测 onDoubleClick/长按→onRightClick（右键等价映射）/onTouch 系挂接 down/move/end，OverlayEventDispatch 统一派发）；8b POINT_DRAG 委托模板 performEventPressedMove；8c IND-022 draw 钩子渲染消费（core 新增 IndicatorDrawContext，scope/palette 宿主层注入，非空时绕过 figures 管线）；8d PlotDef.styles 行级覆盖位（drawLinePlots styleOverrideOf）；8e setTimezone→state.timezoneId→时间标签渲染消费（JVM 无效时区 GMT 陷阱显式校验回退） |
| 2026-10-28 | **0.3.0 批次 9（长尾收尾）**：9a 覆盖物 TalkBack 遍历——accessibilityText 增覆盖物数量与选中项摘要（ChartLocale 新词 overlayCountTemplate/overlaySelectedTemplate/overlayPoints）、CustomAccessibilityAction 增「选中下一画线」循环遍历（selectNextOverlay）；矩阵回填 CH-023/IND-014/IND-022/OVL-011~016/EVT-007~009 消费端现状；TooltipAndCallbacksTest 6 例全绿。真机（OnePlus PJZ110，原小米断连换机登记）验证 standard/rect 双形态、涨跌着色、副图图例、面板全开关联动 |
| 2026-10-28 | **0.3.0 发版**：切版 0.3.0（publishing/LKanvasChart.VERSION/consumer 依赖）；全量测试绿 + assembleRelease 构建通过；制品重发本地 Maven（core JAR+sources / compose AAR），consumer 仅凭制品重编通过（A-46 保持）；check.py 七面校验通过（矩阵 A=15/B=227/C=9/D=6，E=0，unknown=0）。0.4.x 余量：多轴渲染消费、动态 pane/isStack、Macrobenchmark 性能线（B-02~B-06）、诊断可视化 UI、fling 曲线、figure 宿主样例 |
| 2026-10-29 | **P1 性能线启动（:benchmark 骨架 + B-01/B-02 首轮实测）**：新建 benchmark 模块（androidx.benchmark 1.4.1，targetVariant=app-sample benchmark 变体，lkanvas-compose 补 benchmark fallback）；app-sample 加 benchmark buildType（非 debuggable+profileable）与 stress 负载模式（50k candle/10 MA/100 segment intent 注入，种子固定）。**stress 首轮暴露重大渲染缺陷并修复**：Canvas 绘制未订阅 viewRevision，静态数据（无 tick）宿主平移/缩放不刷新（此前被样例 tick 掩盖）。**计量兼容性冻结**（对照实验留档）：FrameTimingMetric 在 MIUI14 对非 debuggable 构建不可采集（minify 无关）、gfxinfo 对 minify 构建不可见——按 PRD 7.3 等价方法口径：B-01=StartupTimingMetric（**median 387.6ms，D1 门槛 1500ms 的 3.9 倍余量**）；B-02=gfxinfo 非压缩口径（262 帧/P50 69ms/P95 85ms/GPU P95 8ms——**帧耗时 6.9 倍超预算、瓶颈在 UI 线程 CPU 侧，T-34 优化输入明确**）。B02StressBenchmark @Ignore 登记设备受限，兼容后恢复；详见 performance-baseline.md |
| 2026-10-29 | **T-34 优化循环第一轮（B-02 帧耗时 P50 69→27ms，-61%）**：分段定位法（intent 开关逐项禁用 + gfxinfo 对比）锁定 tooltip 为最大热点（逐帧 130+ 行 measure+drawText 占 27ms）；落地三项低风险优化——TextMeasurer cacheSize=256、时间标签 (ts,tz) 缓存、tooltip DrawOp 录制回放（内容只依赖数据快照+idx+styles，位置固定 pane 顶部；期间修正 GraphicsLayer record/drawLayer 在 Compose 1.11 DrawScope 上下文的 StackOverflowError 弃用路线与初版 key 含 viewRevision 的语义错误）。复测：P50 27ms/P95 44ms/P99 48ms/超帧 59.6%（帧吞吐 +94%，GPU 全程非瓶颈）；诊断开关已清理，全量测试绿 |
| 2026-10-29 | **T-34 优化循环第二轮（累计 P50 69→23ms，-67%；P95 85→32ms，-62%）**：分段定位（DiagFlags 三组临时开关）锁定指标线为剩余大头（禁后 27→14ms，65 列×240 点 Path 构建+drawPath；candle/覆盖物各仅 ~1ms 不优化）；drawLinePlots 重写为 native drawLines 批量提交（drawIntoCanvas + nativeCanvas.drawLines(FloatArray)，每列一次 JNI 提交，NaN 分段）；过程修复 drawLines 端点配对错位 bug（隔点连线致实线变虚线，截图对照确认恢复）。复测：P50 23ms/P95 32ms/P99 38ms/超帧 53.1%/帧数 541（累计 +107%）；诊断开关清理，全量测试绿；余项（candle 合并/覆盖物裁剪/指标线 tile 化）收益递减登记 0.4.x |
| 2026-10-29 | **0.4.0 多轴渲染 Alpha（CH-053~056/IND-003 消费端）**：门面 createYAxis/removeYAxis/overrideYAxis 同步 paneId=main 自定义轴到 ChartState.extraYAxisMeta（渲染消费源）；drawMainPane 分流——yAxisId=main 指标走主轴值域，自定义轴绑定指标经 extraAxisRange（可见区独立 min/max）+ reverse 翻转映射绘制，轴标签画在主轴列右侧的独立 gutter 列（44dp×fontScale/轴，KLineChart gutter 动态扩展）；draw 钩子在自定义轴同样生效；原生轴 pane 模型（多 pane/轴 widget 宽度自适应）仍留完整版。测试 multiAxis_syncToStateAndIndependentRange；真机（小米）验证：MA(10) 绑定自定义轴后按独立值域 [84.8,103.1] 绘制（与主图价格域 100-172 解耦）、橙色独立标签列 85.00 刻度正确、轴✓开关切换正常；矩阵回填 5 行 |
| 2026-10-29 | **B-03/B-04/B-05 首轮实测 + 引擎 plotsFor 真 bug 修复**：B-03 100k 就绪 3744ms（超 D1 1.9 倍，主线程同步全量计算所致，改进方向登记）、100k 滚动 P50=24ms 与 50k 持平（帧成本只随可见区规模——PRD 7.4 工作量门槛证据；此前 113ms 为旧 APK 测量事故重测排除）；B-04 1000 混合操作收敛 158ms（12.7 倍余量，BurstAndLifecycleTest 固化）；B-05 30 建销/100 切品种活动实例数稳定（JVM 近似口径，retained heap dump 待后续）。**B-04 测试暴露引擎真 bug 并修复**：IndicatorEngine.recompute 用 definition.plots 建列违背 PRD R-08 plotsFor(params)——自定义参数个数实例产生永久 NaN 假列；默认参数 oracle 不受影响（core 全量回归绿） |
| 2026-10-29 | **B-06 30 分钟热稳定通过 + B-03 就绪后台化达标**：B-06 长测（50k/10 指标/100 覆盖物标准负载，30 轮×60s 滚动交互）——零崩溃（FATAL=0、30 轮同 PID）、本 app 无 ANR（dumpsys+logcat 双查）、PSS 170~194MB 波动后半程无增长、累计渲染 19175 帧（每轮稳定 ~640）。B-03 分批注入改造（首批 1k reset+指标毫秒级全量 → 99k appendBatch 分批 2k/批 delay(16) 让帧，指标 REPLACE_APPEND 增量推进）：首批可交互 54ms（原全程冻结 3.7s）、全量就绪 1895ms（原 3744ms，-49% 进入 D1 2000ms 门槛）——B-03 达标（性能门槛余项仅剩 R8 压缩口径帧数据待设备兼容） |
| 2026-10-29 | **0.4.0 发版（多轴渲染 Alpha + 性能门槛全绿）**：切版 0.4.0（publishing/LKanvasChart.VERSION/consumer 依赖）；全量回归绿（core+compose 测试 + assembleRelease）；制品发布 build/maven-repo，consumer 仅凭制品重编通过（A-46 保持）；check.py 七面校验通过（E=0）。发版内容：多轴渲染 Alpha（CH-053~056/IND-003 消费端）、tooltip DrawOp 录制回放 + TextMeasurer 缓存 + 时间标签缓存（B-02 帧耗时 -67%）、指标线 native drawLines 批量提交（累计 -67%）、B-03 分批注入（首批可交互 54ms/就绪 1895ms 达标）、视口重绘订阅缺失修复、recompute plotsFor 假列修复；性能门槛 PRD 7.3 全表实测达标（B-01~B-06），余量：R8 口径帧数据、B-05 heap dump 口径、动态 pane/isStack、多轴完整版 |
| 2026-10-29 | **isStack 语义接线（CH-044 收口，0.4.0 后）**：createIndicator(isStack=false) 先移除该 pane 全部现有非内置实例再创建（上游 Store.addIndicator 同语义）、true 叠加；顺带修 removeIndicator 只删首个匹配→全删匹配（上游 forEach 全删语义）。测试 createIndicator_isStackReplaceAndStack（叠加共存/替换清除/内置保护/跨 pane 隔离）——compose 89 测试全绿 |
| 2026-10-29 | **诊断可视化（T-30 余量收口）+ 平台 fling 曲线**：诊断——渲染计时接线（lastRenderNanos/renderCount，关闭零开销不变）+ 样例 DiagnosticsPanel（采集开关/summary/最近 12 条环形缓冲/导出 cacheDir），真机实测「计算 0.04ms×33 次；绘制 12.91ms×14 帧」+ 导出写入成功；fling——手写指数衰减替换为平台 OverScroller native spline（RecyclerView 同款，KLineChart 注入、null 回退保 JVM 测试），真机惯性滚动验证通过 |
| 2026-10-29 | **figure 宿主自定义模板样例（T-30/长尾收口）**：OverlayRenderer 补 createPointFigures 钩子渲染消费（自定义模板 → Figure 原语 → FigureRenderer，styles 色与线宽消费）；钩子签名补 pixelPoints 参数（上游 coordinates 语义——渲染层换算好的控制点像素坐标）；样例 FigureSamples（circle/rect/polygon/arc 四模板注册 + 样例「图例+」按钮编程式创建）；门面 createOverlay 与 OverlayRenderer 模板解析加宿主注册表回退（此前只查 core 内置 16 表，registerOverlay 注册的自定义模板创建即 null）；映射器兼容两种 attrs 布局（每字段一子 list/单子 list 全字段）。真机验证四原语全部正确渲染（紫圆/蓝弧/绿矩形/橙多边形） |
| 2026-10-29 | **0.5.x 动态副图 pane Alpha（台账「pane 增删/排序为固定三 pane」余项收口）**：ChartState.extraPanes（自定义 paneId 列表）+ extraPaneLayout（relayout 计算的 top/bottom，排在内置 VOL/MACD 之后时间轴之前，main 高度自动压缩、35% 保底不变）；门面 createIndicator paneId 非内置时自动建 pane、removeIndicator 后 pane 空自动移除；渲染 drawExtraPane（pane 内全部指标按可见区公共值域画线列，draw 钩子同样生效）+ 网格/标签/分隔线；KLineChart 容器高按 extra 数扩展；修 mainPaneIndicatorResults 未按 paneId 过滤（BOLL 图例串入主图）。样例「图」按钮（pane_boll 上建 BOLL）；测试 dynamicSubPane_createOnBindPruneOnRemove（90 测试全绿）；真机验证第四 pane 出现/主图压缩/图例隔离/删除回收。完整版余量：动态 pane 分隔条拖动、pane 排序、存档含 extra pane 布局 |
| 2026-10-29 | **动态副图分隔条拖动（Alpha 手势补全）**：extraPaneSeparatorAt（extra pane 下边界命中，内置分隔优先）+ adjustExtraPane（per-pane 高度覆写，最小副图高钳制）；手势层 PANE_ADJUST 分支扩展（paneWhich=-1 时走 extra 路径）；**顺带修 Alpha 布局 bug：时间轴仍固定 macdBottom 与第四 pane 重叠——时间轴 top 跟随最后一个自定义 pane 下缘**。测试 dynamicSubPane_separatorHitAndDragResize（命中/优先级/拖动增高/main 压缩/最小钳制，90 测试全绿）；真机验证拖动 resize 与时间轴位置正确；BOLL pane 值域经日志核实正确（波动行情 dn 下轨 70.8 属正常形态） |
| 2026-10-29 | **pane 排序 + 存档扩展（动态 pane 完整版余项收口）**：moveExtraPane（列表序即渲染序，越界钳制/未知忽略，门面透出）；存档 schema 扩展 extraPaneIds 字段（渲染序；旧版存档无此字段缺省空不升版本号、不失败——向后兼容验证），applyRestoredArchive 回填排序（宿主据 archive.extraPaneIds 重建指标后 pane 经 ensureExtraPane 生成）。测试 moveExtraPane_reordersRenderSequence + extraPaneIds往返与旧版存档缺省；全量测试绿。动态 pane 完整版至此收口（建/删/渲染/拖动/排序/存档），余登记：pane 高度比例入存档（当前仅序） |
| 2026-10-29 | **多轴完整版：自定义变换轴消费（YAxisOverride.customAxis 桥接）**：YAxisOverride 补 customAxis（上游 AxisTemplate 的 valueToRealValue/realToValue/displayValueToText 组合桥接为 core CustomYAxis 契约）；门面 syncExtraAxes 同步到 ChartState.extraYAxisCustom；渲染端 Custom spec 分支——绑定指标值经 valueToReal 变换投影（realRange 净化）、刻度经 customTicks（A-18 净化内建）、reverse 与变换组合正确。测试 customTransformAxis_syncsAndProjects（log10 变换轴：万级窄幅 → real 域 ~4 极窄投影、override merge 语义保留/覆盖、removeYAxis 清理）；全量测试绿。多轴完整版余项：position/inside 标签列位置消费（当前仅独立右列） |
| 2026-10-29 | **多轴完整版：position/inside 标签列位置消费**：ExtraAxisMeta（reverse/inside/positionLeft）替代 Boolean meta；渲染端——inside 或 position=LEFT 的轴标签画 pane 内右缘右对齐（不占 gutter 列），RIGHT 且非 inside 占独立列（列数据此计）；KLineChart gutter 同步只数独立列轴；overrideYAxis 翻转 inside 即时生效。测试 extraAxisMeta_positionInsideSynced（三轴同步/override 翻转，并适配旧断言到新 meta）；全量测试绿。**多轴完整版至此全部收口**（独立列/inside/LEFT 位置、reverse、自定义变换轴、删除保护、排序同步） |
| 2026-10-29 | **0.5.0 发版（多轴完整版 + 动态 pane 完整版 + 长尾收口）**：切版 0.5.0（publishing/LKanvasChart.VERSION/consumer 依赖）；全量回归绿（core+compose 测试 + assembleRelease）；制品发布 build/maven-repo，consumer 仅凭制品重编通过（A-46 保持）；check.py 七面校验 E=0。发版内容：多轴完整版（独立列/inside/LEFT 位置/reverse/自定义变换轴 CustomYAxis 契约/删除保护/排序同步）、动态 pane 完整版（绑定 paneId 建删/独立渲染/分隔条拖动 per-pane 覆写/排序 moveExtraPane/存档 extraPaneIds 旧版兼容）、长尾收口（诊断可视化渲染计时+面板+导出、平台 fling OverScroller、figure 宿主模板样例、isStack 语义、removeIndicator 全删修复）。余量：R8 口径帧数据/B-05 heap dump（外部依赖）、pane 高度比例入存档（低价值）、上游新版本跟进（upstream-sync.md 每月 compare） |
| 2026-10-29 | **0.4.0 巩固回归（全绿）**：JVM——core+compose 305 测试 0 失败（含 oracle 27 指标逐位对照/增量一致性/meta diff/parity/新增多轴与 B-04/B-05 测试），assembleRelease 构建通过，consumer 0.4.0 仅凭制品重编通过，check.py 七面校验 E=0；真机（小米 23078PND5G）——B-02 负载（50k/10 指标/100 覆盖物）注入 1095ms（门槛内）、20 次滚动抽样 P50=25ms/P95=34ms（优化水平保持）、figure 四原语在负载叠加下渲染正常。0.4.0 全部计划工作与台账余项收口完毕 |

| 2026-10-30 | **0.6.0 发版（结构与逻辑对齐完整版——审计 16 项发现全部收口）**：**结构镜像**——27 指标/16 覆盖物模板拆分为上游同名文件（extension/indicator\|overlay/*.kt），每模板含上游逐行对照的规范 calc，引擎全量路径走 calc、Scratch 保留为增量优化路径（DualPathParityTest 27 模板双路径互验 + oracle 27/27）；**数据路径**——core/store/Store.kt 镜像上游 Store 数据段：双向分页边界触发、more 同方向更新（修复交叉赋值）、resetData 退订→变更→清 loading→init 顺序、请求代次门禁（在途切品种旧回调/推送丢弃，串品种缺陷收口）；**注册统一**——指标/覆盖物/轴/图形模板注册与创建/渲染/命中/手势共用同一全局表；**严格上游默认（破坏性变更）**——init 不预置 MA/VOL/MACD（示例显式创建）、createIndicator 缺省新建指标 pane 且 isStack=false、无内置删除保护、overrideIndicator 全字段按 filter、事件 payload 嵌套 data:{prev,current,next}、executeAction(十字线) 抑制反向广播、setStyles 显式设回默认值生效；**契约收口**——视口当前像素锚点（scale=1 无位移）/scrollToDataIndex 右锚模型/独立 maxOffset/小数 rightOffset/to 钳制/帧步进动画、log 轴 convert 与绘制同源（YAxisMath 统一）+ ConvertFilter + 主轴 reverse 入链、scroll/zoomEnabled 共用入口门禁、zoomAnchor/candle.type/逐根配色回调/格式链消费、覆盖物模板命中与交互创建配置先行应用、setLayoutOptions/getLayoutOptions/getConvertPictureUrl 同名入口；**验收**——check.py v4 四态校验（行为通过=18）、SLA-001~016 全部行为通过、真实补丁演练 4（95a6f2ad executeAction 修复映射验证）、core+compose 全量测试绿。矩阵 A=15/B=227/C=9/D=6（0.6.0 结构批次 16 行行为通过）。**发版回归**：全量测试 340（core 220+compose 120）0 失败；0.6.0 制品发布本地 Maven（core JAR+sources / compose AAR），consumer-example 适配 0.6.0 API 面（顶层 GetBarsParams/Period.ofMinutes/显式创建 VOL/MACD）后仅凭制品 compileDebugKotlin+assembleDebug 通过（A-46 保持）；demo-checklist 0.6.0 复核（单测守护项全绿、A1/B1 视觉项待下轮真机）；冻结设备 B-01 median 412.8ms（+6.5%<10%）、B-02 P50 23ms/P95 32ms（0% 劣化）双门槛 PASS（performance-baseline.md §4.x） |

| 2026-10-30 | **0.7.0 发版（复审收口——R01-R14 全项，依据 structure-logic-audit-2026-10-30）**：**数据路径**——R01 订阅会话代次与请求代次分离（分页不再杀实时订阅）、R02 检查点 key 绑定（排队切换不串品种）、R03 提交后自动范围调整/边界续拉（镜像 _addData→_adjustVisibleRange）+ init 视口 onReset 修复；**契约**——R04 StylesPatch 全可空镜像（局部补丁不重置其他分支；setStyles(Styles) 改全量替换、setStylesStrict 删除）、R05 convert 镜像上游原义（absolute=y 参考系、paneId 选 pane、timestamp 恒为时间戳、OverlayPoint 增 dataIndex）；**统一来源**——R06 轴实例表（spec 表/全 pane 同步/公开注册入构造表/额外轴不再改主轴）、R07 paneList() 单一来源（vol/macd 按需存在/动态 pane 查询设高/迁移维护/严格 paneId 无回退）、R08 统一 figure 管线（BAR 分发+visible+attrs 消费）、R09 覆盖物统一模板执行（16 模板 createPointFigures 迁入+无名称分支+pane 感知+命中/绘制共源；契约扩展 extendData 参数）；**事件**——R10 executeAction null 清除/x 推导索引/paneId 实际布局/标签同源映射；**接口**——R11 scrollToDataIndex/zoomAtCoordinate 真动画 + LayoutOptions barSpaceLimit/paneDragEnabled + getConvertPictureUrl backgroundColor；**验收**——R12 check.py SLA 行纳入证据检查 + structure-map 校验 + 台账修正；R13/R14 演练 5（上游 e829f30e arc 端点容差 before/after 移植）。**破坏性变更**：setStyles(Styles) 改全量替换、setStylesStrict 删除、createPointFigures 契约扩展 extendData、OverlayPoint 增 dataIndex、absolute 语义纠正。**发版回归**：全量测试 363（core 222+compose 141）0 失败；oracle 27/27 + 双路径互验绿；check.py v4 全量通过（行为通过=32）；0.7.0 制品发布本地 Maven + consumer 重编通过；B-01/B-02 豁免登记（冻结设备安全锁屏，见 performance-baseline.md §4.y）；demo-checklist 0.7.0 复核（单测守护全绿、真机视觉顺延解锁后批次） |

| 2026-10-30 | **0.8.0 发版（第三轮复审收口——T01-T11 全项，依据 structure-logic-audit-round3-2026-10-30）**：**写入时会话门禁（T01）**——ChangeRequest 增 expectedSessionId、ChartEngine.applyToStore 唯一写入入口执行前复核（SESSION_MISMATCH 丢弃；Reset 唯一开新会话）、Store.addData/addUpdate 锁内复核代次（TOCTOU 关闭）、Host 携带 engine.currentSessionId 快照；**pane/轴上下文单一来源（T02/T03）**——ExtraAxisMeta 增 paneId、axisValueToY/At 按轴所属 pane bounds 投影、主图额外轴循环按 paneId==main 过滤、drawExtraPane 消费绑定轴 spec/reverse、ChartState.paneVisibleRange 共享值域（转换/绘制同源，删 firstOrNull 近似；vol 量纲/macd 0 基线入域）；**编辑与十字线（T04/T05）**——overlayPointFromPx 增 paneId（值反算+磁吸 pane 感知）、手势 6 处按上下文传 pane、模板统一 pane 局部坐标（bounds/pixelPoints/translate；verticalStraightLine 不再穿主图）、十字线 y 钳制含 extras + crosshairPaneId 贯通 + 标签按所在 pane 反算；**figure 全 pane 统一（T06）**——主图叠加/主图额外轴入口接 drawInstanceFigures（BAR 分发，main 创建 MACD 柱列不再折线）、attrs=几何/styles=样式语义分离（attrsOverrideFor）；**模板契约（T07/T08/T09）**——priceLine 锚点→右边界+渲染层精度格式注入（rawValue 标记）、覆盖物命中统一走 core FigureHit（hitFigureShape 删除）、绘制 zIndex 升序/命中降序（上游排序语义）；**配置与工具（T10/T11）**——getLayoutOptions 存取同源（barSpaceLimit/paneHeights 含动态 pane/paneDragEnabled 回读）、动态 pane 拖动禁用集、getConvertPictureUrl backgroundColor 无条件合成+bmp 显式不支持、check.py 支持 R\d{2}（R01-R14 纳入解析与证据检查）、validate_structure_map 接入 main（缩略计划路径区分）、structure-map 补 2 行台账。**发版回归**：全量 366 测试（core 222+compose 144）0 失败；oracle 27/27+双路径绿；check.py 全量通过（行为通过=33，含 R01-R14）；0.8.0 制品发布本地 Maven + consumer 重编通过；B-01/B-02 豁免延续（§4.z 设备锁屏） |

| 2026-10-30 | **0.9.0 发版（第四轮复审——Q01-Q09 原复现已修，余项由 0.10.0 N01-N07 补充收口）**：**会话身份（Q01）**——dataEpoch 接受时点身份（applyInit 锁内递增；其余动作接受时捕获/执行前复核）、Store.addUpdate 锁内比对捕获 subGen（删 key hack）、拒绝的 INIT 不再 subscribeBar；**坐标/渲染（Q02/Q03）**——axisValueToY/At pane 相对坐标、门面 yAxisId 分支按轴所属 pane 单次转换、drawExtraPane 真实消费 paneVisibleRange 与绑定轴 spec/reverse（0.8.0 该编辑未落盘已修正）、getYAxisTemplate 全字段回读；**覆盖物（Q04）**——overlayPointFromPx 输入边界减 top（去双计）、CREATE 按 creating 实例初始化 pane、绘制 clipRect、命中 pane 外拒绝；**figure（Q05）**——Volume/MACD 模板 styles 回调（涨跌/正负色，上游同模型）、evaluateStyles 支持 (row,params,value,candles)、drawBarPlots 消费 attrs 几何 top/bottom、VOL/MACD pane 全实例经 drawInstanceFigures（删除专用 bars 旁路）；**契约（Q06/Q08/Q09）**——pricePrecision（setSymbol 同步）+千分位格式链、drawArc 顺时针跨度归一（与 FigureHit 同规则）、fixedZLevel 选中对象保留命中候选；**Layout（Q07）**——拖动禁用单一状态（ChartState）、setLayoutOptions 全 pane 高度路由。**验收**：全量 370 测试（core 222+compose 148）0 失败；check.py 支持 Q 编号（Q01-Q09 纳入解析与证据检查，行为通过=42）；0.9.0 制品发布 + consumer 重编通过；B-01/B-02 豁免延续（设备安全锁屏）。**表述纠偏**：0.8.0 文档中"drawExtraPane 消费绑定轴/转换绘制同源/attrs 几何消费/T01-T11 全项收口"等超出当时证据的表述已修正（对应工作本轮真实落地。**第五轮复审范围说明**：Q02 副轴投影/绘制、Q03 BAR 值域、Q04 attrs 上下文/几何、Q05 attrs 消费、Q06 格式链余项、Q07 动态 pane 状态分裂等余项在 0.10.0 由 N01-N07 补充收口——本条目原"Q01-Q09 全项"表述超出当时验证范围，已修正） |

| 2026-10-31 | **0.11.0 发版（第六轮复审收口——M01-M06 全项，依据 structure-logic-audit-round6-2026-10-31）**：**M01**——applyInit 捕获 myEpoch+myGen 受理时点快照，Rejected 分支双门归属复核（无更新 INIT 受理且无会话切换才清理；修复前 decrementAndGet 改写全局代次+无归属退订较新 C 会话；仅 epoch 门不够——审计复现中 C 回调被代次门禁丢弃、epoch 未递增而 setSymbol(C) 已推进 generation）；**M02**——ChartState.instanceBoundYOf 单一选择入口（extraAxisRange+effectiveExtraAxisSpec+reverse 与 axisValueToY 同式），drawExtraPane 轴解析移入实例循环（删除 firstOrNull 单轴）、drawIndicatorPaneInstances 固定 pane 消费绑定轴；**M03**——drawBarPlots 默认几何+attrs 上游四键覆盖（x=左边界、y=顶边、height=柱高；top/bottom bridge 键保留）+ drawLinePlots attrs.coordinates 逐点 drawPath（无 coords 行维持 drawLines 批量快路径——批量渲染与几何覆盖不互斥，structure-map 行修订）；**M04**——drawTemplate 控制点块移入 clipRect 内/translate 外、绝对坐标（修复前局部坐标画进主图顶部）；**M05**——paneVisibleRange 收口合并可见实例 minValue/maxValue（上游 YAxis.ts specifyMin/Max；VOL 0 基线入域）；**M06**——hitTest 删除控制点独立第一遍，单一 z 降序遍历每实例先控点后线身（跨实例 z 竞争；PRD R-09 表述修订、Parity5 fixedZLevel 用例按新契约重写）；**台账**——gap-matrix §16 补 N06 行+删空行截断、新增 §17；**验收**——全量 388 测试 0 失败；check.py 全量通过（R/Q/N/M 全纳入，行为通过=55）；0.11.0 制品发布本地 Maven + consumer 重编通过；B-01/B-02 豁免延续（设备安全锁屏 §4.v） |
| 2026-10-31 | **0.10.0 发版（第五轮复审收口——N01-N07 全项，依据 structure-logic-audit-round5-2026-10-31）**：**N01**——applyInit 读取 state.reset 的 ChangeResult，Rejected 时 store.unsubscribeData()（递增 subscriptionGeneration 使受理时订阅推送被代次门禁丢弃）+ applyUpdate/Backward/Forward 内核 key 门禁；**N02**——drawExtraPane 绑定轴条件加 inst.yAxisId 匹配（修复前按 paneId 选第一条轴应用于整 pane）+ extraAxisRange 值域（与 axisValueToY 同式）；**N03**——paneVisibleRange vol/macd 分支全实例聚合（修复前首实例）+ paneValueToY/At 删 vol 独立 0..maxVol 公式（统一走 paneVisibleRange）；**N04**——attrs 契约 pane 相对坐标（绘制加 paneTop 平移）+ x/width 键消费 + attrsOverrideFor 真实 value/candles + 四调用点 candles 透传；**N05**——formatPriceText 补 formatFoldDecimal（decimalFoldThreshold）+ 实例 precisionOverride 优先；**N06**——控制点绘制移入 pane 裁剪块 + hitTest 控制点段 pane 边界预检；**N07**——fixedZLevel 不再排除控制点候选（上游 Overlay.ts:153 只约束浮起）；**验收**——全量 371 测试（core 222+compose 149）0 失败；check.py 全量通过（R/Q/N 编号全纳入，行为通过=49）；0.10.0 制品发布本地 Maven + consumer 重编通过；B-01/B-02 豁免延续（设备安全锁屏 §4.w） |

## 6. 已知问题与后续计划

- 基础图形 circle/polygon/rect/arc 的宿主自定义模板样例待补（原语与命中已实现）。
- 无障碍余项：TalkBack 遍历覆盖物列表待补；字体缩放 2.0 与横屏已真机验证（A-41 达标，允许降级）。已知 S2：可见最高/最低标记在 2 倍字体下可溢出内容区（不阻断）。
- 诊断可视化（宿主诊断入口 UI/导出）待 T-30 余量；当前经 ChartDiagnostics API 读取。
- 性能门槛（B-02 容量/突发/泄漏/持续运行）尚未完成：B-01 增量路径 JVM 初测已达标（P95=0.270ms vs 4ms），Macrobenchmark 冻结设备测量（真机帧率/内存/启动）待 T-34。
- D8 构建 WARNING：Kotlin metadata 重写 "Should never be called"（R8 已知噪音，debug 运行正常）；R8 release 验证在 M6/T-31。
- ~~惯性滚动为基础指数衰减实现~~：**已接平台 OverScroller native spline 曲线**（RecyclerView/ScrollView 同款，KLineChart 注入、null 回退旧衰减保 JVM 测试可用；真机惯性滚动行为验证通过）。
- ~~pane 增删/排序为固定三 pane 布局~~：**0.5.x 动态 pane 完整版收口**（绑定 paneId 自动建删/渲染/拖动/排序/存档）；0.6.0 起主 pane 不再按 series 过滤、vol/macd pane 接受任意绑定实例（上游缺省语义）。
- PVT volume 缺失按上游显式取 1、ROC 零回看取 0、AVP 缺失按 0：上游明确代码行为，逐位对齐优先（登记备查）。
