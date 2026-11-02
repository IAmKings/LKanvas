# 性能基线（P1 性能线：Macrobenchmark 骨架与门槛冻结）

> **当前验收（2026-11-02，设备/发布第七轮双机续验）**：0.15.0 保持冻结，候选为 0.15.1-rc5（APK `8c5895b3…`）。当前同包K60U实时帧CPU P95 19.2584ms / 正overrun 10.9053%，压力正overrun 12.5%，仍失败；PJZ110对应两项各一次短测通过。双机字体、API23模拟器冒烟、JVM417与本地制品链通过。最终双次矩阵、heap/B06/真人TalkBack尚未完成，发布仍 **HOLD**。见 §4.z7 与[第七轮执行报告 §7–8](alignment/release-readiness-round7-execution-2026-11-02.zh-CN.md)。旧APK数值不替代当前认证。

> 执行安排：[第七轮发布放行任务计划](alignment/release-readiness-round7-plan-2026-11-02.zh-CN.md)。新发布档已在观察结果前锁定为 [R2-60-v1](release-acceptance-r2-60.md)；原 D1 冻结值与历史 HOLD 保留。

> PRD 7.2/7.3 的落地文档（0.4.x 独立线，不阻塞已完成的 0.3.0 发版）。
> 本文档承载：冻结设备登记、PRD 7.3 门槛冻结值、B-02 扩展负载首轮实测数据、
> 与门槛的差距与后续动作（T-34 优化循环输入）。

## 1. 冻结设备与运行环境

| 项 | 值 |
|---|---|
| 冻结设备 | 小米 23078PND5G（serial 6D8H6TXSDYXC85PZ），corot，Android 14（SDK 34），8 核 3.35GHz，16GB——2026-10-28 短暂断连后**已回归**，首轮实测在本机采集（期间 OnePlus PJZ110 仅作真机功能验证临时设备，未采性能数据） |
| 目标变体 | `:app-sample` 的 `benchmark` buildType（initWith release，非 debuggable + profileable，debug 签名；**当前 isMinifyEnabled=false**——原因见 §4 计量兼容性） |
| 工具链 | androidx.benchmark 1.4.1（Macrobenchmark JUnit4）、AGP 8.13.2 |
| 负载纪律 | 种子固定 2026（LocalFeed）；B-02 固定负载 50k candle + 10 MA 实例 + 100 segment 覆盖物，intent extras 注入，静态不推 tick（压力来自滚动/缩放交互，PRD 7.2） |

**P1 骨架带来的关键修复**：stress 首轮暴露**纯视口变化不重绘缺陷**——Canvas 绘制
lambda 未订阅 `viewRevision`，静态数据（无实时 tick）宿主平移/缩放完全不刷新；
此前一直被样例 200ms tick 掩盖。已在 KLineChart 绘制作用域补订阅（0.4.0 随发版）。

## 2. 门槛冻结（PRD 7.3 目标值，实测校准前）

PRD 7.3 全表在本项目冻结如下（D1 = 本项目目标档；校准前不作为 PASS/FAIL 判据，
先记录实测并定义支持范围）：

| 指标 | D1 目标 | 计量定义 |
|---|---|---|
| B-01 热进程首帧 P95 | ≤500 ms | 挂载图表至主图+必需指标首帧 |
| 冷进程到完整图表 P95 | ≤1,500 ms | 含进程/Compose 初始化（样例口径） |
| frameDurationCpuMs P95 | ≤10 ms | Macrobenchmark（非 GPU 总帧耗时） |
| 超帧比例 | ≤1% | frameOverrunMs>0 帧占比 |
| 输入到视觉反馈 P95 | ≤50 ms | trace/FrameTimeline |
| 内置增量计算 P95 | ≤4 ms | MA/VOL/MACD 后台计算（B-01 JVM 初测已达标：P95=0.270ms） |
| B-03 100k 冷加载就绪 P95 | ≤2,000 ms | 单独记录计算/绘制分项 |
| B-02 持续交互（扩展负载） | 超时帧≤5%、输入 P95≤100 ms | 50k/10 指标/100 覆盖物滚动 |
| B-04 突发收敛 | ≤2 s | 最后已接受操作→最终一致快照 |
| B-05 释放 | 0 确定泄漏 + retained≤5 MiB | 30 建销/100 切品种 |
| B-06 | 无崩溃/ANR/无界增长 | 30 分钟热稳定 |

已登记的范围差异：B-02 的「6 pane」维度以固定三 pane 布局 + 10 指标近似
（动态 pane 模型为 0.4.x 余量），实测报告按实际布局口径披露。

## 3. 测试资产（:benchmark 模块）

| 测试 | 计量 | 负载 | 轮次 |
|---|---|---|---|
| `StartupBenchmark.coldStartup` | StartupTimingMetric | 默认 500 根 + 三内置指标 | 5 iterations，COLD |
| `B02StressBenchmark.stressScroll` | FrameTimingMetric | 50k candle + 10 MA + 100 segment（extras 注入） | 5 iterations × 20 次全宽滑动，WARM |

运行方式：`./gradlew :benchmark:connectedBenchmarkAndroidTest`
（输出 JSON 位于 `benchmark/build/outputs/connected_android_test_additional_output/`）。

## 4. 首轮实测（2026-10-29，小米 23078PND5G）

### 4.1 计量兼容性（对照实验留档）

| 实验 | 结果 |
|---|---|
| FrameTimingMetric + 非 debuggable 构建（minify 开/关两组） | **不可采集**：perfetto trace 无该 app RenderThread `doFrame` 切片（trace 中其他进程与 Choreographer 正常）——MIUI 14 vendor 限制 |
| FrameTimingMetric + debug 构建 | 未测（debug 口径违反 PRD 7.2 非 debuggable 纪律，不采信） |
| `dumpsys gfxinfo` + minify 构建 | **不可见**（Total frames=0，画面正常渲染） |
| `dumpsys gfxinfo` + 非 minify benchmark 构建 | **可用**（帧统计完整） |
| StartupTimingMetric（Macrobenchmark） | 可用（不依赖 renderthread slices） |

**口径冻结**（PRD 7.3「旧系统使用冻结的等价方法，不混算」）：
- B-01 启动：Macrobenchmark StartupTimingMetric（benchmark 变体，R8 压缩恢复后重采）
- B-02 帧耗时：gfxinfo 等价口径（**非压缩** benchmark 变体）；FrameTimingMetric 测试保留
  （B02StressBenchmark @Ignore），设备/系统兼容后恢复并废弃等价口径

### 4.2 B-01 冷启动（StartupTimingMetric，5 轮）

| 指标 | 实测 | D1 门槛 | 结论 |
|---|---|---|---|
| timeToInitialDisplayMs | min=372.2 / median=**387.6** / max=392.9 | 冷进程到完整图表 P95 ≤1500 ms | **远优于门槛（3.9 倍余量）** |

（数据源：`io.lkanvas.benchmark-benchmarkData.json`，compilationMode=verify，
2026-10-28 采集；原始 perfetto trace 已留存 outputs 目录。）

### 4.3 B-02 扩展负载滚动（gfxinfo 等价口径，20 次全宽滑动）

负载：50k candle + 10 MA 实例 + 100 segment 覆盖物（stress_mode intent 注入）。

| 指标 | 实测 | D1 门槛 | 结论 |
|---|---|---|---|
| 帧数 / 超帧截止 | 262 帧 / **261 janky（99.6%）** | 超时帧 ≤5% | **不达（严重）** |
| frameDurationCpuMs P50 | **69 ms** | ≤10 ms | 6.9 倍超 |
| P90 / P95 / P99 | 81 / **85** / 101 ms | 输入 P95 ≤100 ms | P95=85 达（但整体超帧） |
| GPU P50 / P95 | 6 / 8 ms | — | **GPU 不是瓶颈** |

### 4.4 T-34 优化循环第一轮（2026-10-29，同口径复测）

**分段定位方法**（可复用）：临时 intent 开关逐项禁用绘制路径 + gfxinfo 对比——
禁 tooltip 实测 53→26ms，锁定 tooltip 为最大热点（逐帧 130+ 行 measure+drawText）。

**落地优化三项**（全部低风险纯 Kotlin，无新 API 依赖）：
1. `rememberTextMeasurer(cacheSize = 256)`：tooltip/轴每帧大量文本测量，
   TextMeasurer 默认 LRU(8) 全 miss 反复布局
2. 时间标签缓存：`timeLabel/timeAxisLabel` 每帧对可见区每根 candle 做
   Calendar+format，按 (ts,tz) 缓存（上限 4096 清空）
3. **tooltip DrawOp 录制回放**：tooltip 内容只依赖 (数据快照, 锚点 idx, styles)，
   位置固定 pane 顶部不随视口平移——录制帧绘制+收集 DrawOp（Text/RoundRect/
   StrokePath），稳定帧逐条回放（仅 drawText，measure 全部命中缓存）。
   注：曾尝试 Compose 1.11 GraphicsLayer record/drawLayer，在 DrawScope 上下文
   存在自引用环（StackOverflowError），弃用；修正了初版 key 含 viewRevision
   的语义错误（平移帧每帧变 key 导致永远处于录制态、tooltip 消失）

**复测结果（20 次全宽滑动，同 §4.3 口径）**：

| 指标 | 优化前 | 优化后 | 变化 |
|---|---|---|---|
| 帧数 | 262 | 507 | 帧吞吐 +94% |
| 超帧截止比例 | 99.6% | **59.6%** | -40pp |
| frameDurationCpuMs P50 | 69 ms | **27 ms** | **-61%** |
| P90 / P95 | 81 / 85 ms | 40 / **44 ms** | -51% / -48% |
| P99 | 101 ms | 48 ms | -52% |
| GPU P50 / P95 | 6 / 8 ms | 6 / 8 ms | GPU 全程非瓶颈 |

### 4.5 T-34 优化循环第二轮（2026-10-29，同口径复测）

**分段定位**（临时 DiagFlags 三组开关）：全开 27ms / 禁指标线 **14ms**（指标线
占 ~13ms——65 列 × 240 点的 Path 构建+drawPath）/ 禁 candle 26ms / 禁覆盖物
26ms——candle 与覆盖物各仅 ~1ms，不值得优化。

**落地优化**：`drawLinePlots` 重写为 **native drawLines 批量提交**
（`drawIntoCanvas` + `Canvas.nativeCanvas.drawLines(FloatArray)`，每列一次 JNI
提交连续段，替代 15.6k 次 Path.lineTo + 65 次 drawPath；NaN 分段各自成段）。
过程修复一个端点配对 bug：drawLines 每 4 float 为一条线段，初版每点只 push
单点导致隔点连线（实线呈虚线状）——修正为每步 push (last, cur) 成对端点，
截图确认 MA 实线恢复连续。

**复测结果（20 次全宽滑动，同口径）**：

| 指标 | 第一轮后 | 第二轮后 | 累计（vs 基线） |
|---|---|---|---|
| 帧数 | 507 | 541 | 262→541（**+107%**） |
| 超帧截止比例 | 59.6% | **53.1%** | 99.6%→53.1% |
| frameDurationCpuMs P50 | 27 ms | **23 ms** | 69→23（**-67%**） |
| P90 / P95 | 40 / 44 ms | 29 / **32 ms** | 85→32（**-62%**） |
| P99 | 48 ms | 38 ms | 101→38（-62%）、GPU P95 8→5ms |

**两轮小结**：B-02 扩展负载（50k/10 指标/100 覆盖物）的 P95 帧耗时从 85ms
降至 32ms，逼近 D1 输入 P95≤100ms 的交互流畅口径；P50 与 16.7ms 预算还差
~6ms（禁指标线下限 14ms≈其余全栈成本）。**余项方向**（收益递减，按需推进）：
candle 同色合并 Path（~1ms）、覆盖物可见区裁剪（~1ms）、指标线 tile 化分层
缓存（复杂度高，留待 0.4.x 多轴渲染时统一设计）。**口径提醒**：本表全部为
gfxinfo 非压缩等价口径（PRD 7.3「不混算」），R8 压缩口径待设备兼容后补采。

## 6. B-03/B-04/B-05 首轮实测（2026-10-29，JVM + 真机混合口径）

| 项 | 实测 | D1 门槛 | 结论 |
|---|---|---|---|
| B-03 100k 冷加载就绪 | **3744ms**（100k candle + 10 MA + 100 覆盖物同步注入，注入开始→完成日志差） | ≤2000ms | **超 1.9 倍**——大头为 10 指标 × 4 列 × 100k 全量计算且在主线程同步执行；改进方向：后台线程计算 + 增量就绪（PRD 允许「期间 UI 可响应」），登记 T-34 第三轮 |
| B-03 100k 滚动帧耗时 | P50=24ms / P95=32ms（与 50k 的 22/30ms **持平**） | B-02 同口径 | **达标**——帧成本只随可见区规模变化，与数据总量无关（PRD 7.4 工作量门槛的直接证据；此前一次 113ms 读数为旧 APK 测量事故，已重测排除） |
| B-04 1000 混合操作收敛 | **158ms**（800 末根替换+100 追加+50 参数改+50 覆盖物删建，洗牌突发；JVM 内核口径） | ≤2s | **12.7 倍余量**（BurstAndLifecycleTest 断言固化） |
| B-05 30 建销/100 切品种 | 活动指标/覆盖物实例数逐周期回到基线、100 次 reset 后稳定、dispose 后不复用旧实例（JVM 活动对象计数口径） | 0 确定泄漏 | **通过（近似口径）**——retained heap ≤5MiB 的真机 heap dump 口径待后续轮次 |

**B-04 测试暴露并修复引擎真 bug**：`IndicatorEngine.recompute` 用
`definition.plots`（默认参数列集）建列，违背 PRD R-08「实际以 plotsFor(params)
为准」——实例参数个数与默认不同（如 MA(6) vs 默认 [5,10,30,60]）时产生
**永久 NaN 假列**（ma2..ma4 全列 NaN）。修复为 `plotsFor(params)`；默认参数下
`plotsFor(calcParams)==plots`，oracle 27 指标逐位对照不受影响（core 全量回归绿）。

## 7. B-06 30 分钟热稳定 + B-03 就绪后台化（2026-10-29）

### 7.1 B-06 长测（标准 B-02 负载 50k/10 指标/100 覆盖物，30 轮 × 60s 交互）

| 判定项 | 实测 | 门槛 | 结论 |
|---|---|---|---|
| 崩溃 | FATAL=0，30/30 轮同 PID 存活 | 无崩溃 | **通过** |
| ANR | 本 app 无 ANR 记录（dumpsys + logcat 双查） | 无 ANR | **通过** |
| 内存 | PSS 170~194MB 区间波动（前 5 轮均值 ~183MB、末 5 轮均值 ~180MB，后半程无增长趋势）；累计渲染 19175 帧（每轮稳定 ~640） | 队列/缓存无持续无界增长 | **通过** |

原始逐轮采样留存会话记录（/tmp/b06_result.txt 格式：round/alive/pssKB/totalFrames）。

### 7.2 B-03 就绪后台化（分批注入）

改造：首批 1k reset（指标在毫秒级小数据上全量）→ 其余 99k 经 `appendBatch`
分批（2k/批 × delay(16ms) 让帧）→ 指标走 REPLACE_APPEND 增量推进。

| 指标 | 改造前 | 改造后 | 门槛 |
|---|---|---|---|
| 首批可交互（UI 可响应起点） | ~3744ms（全程冻结） | **54ms** | PRD B-03「期间 UI 可响应」 |
| 全量就绪 | 3744ms | **1895ms** | ≤2000ms（D1） |

**B-03 达标**：就绪耗时 -49% 进入 D1 门槛，且就绪期间 UI 可交互。
口径：注入开始→完成日志差（含 100 覆盖物创建）；两口径均含 10 指标计算。

## 4.x 0.6.0 发版回归实测（2026-10-30，冻结设备 23078PND5G 在线实采）

| 门槛 | 口径 | 0.5.0 基线 | 0.6.0 实测 | 判定 |
|---|---|---|---|---|
| B-01 启动 | StartupTimingMetric（benchmark 变体，5 次冷启动） | median 387.6ms | **median 412.8ms**（min 393.3 / max 426.2） | ✅ D1≤1500ms 的 3.6 倍余量；较基线 +6.5%（<10% 劣化上限） |
| B-02 帧耗时 | gfxinfo 等价口径（benchmark 变体非压缩，50k/10 指标/100 覆盖物，20 次滑动，571 帧） | P50 23ms / P95 32ms | **P50 23ms / P95 32ms**（P90 30ms） | ✅ 0% 劣化（门槛 ≤10%）；0.6.0 结构镜像（calc 全量路径 + 统一轴模型）未回退优化成果 |
| B02StressBenchmark | FrameTimingMetric | @Ignore（MIUI14 vendor 限制，§4 对照实验） | 维持跳过（1 skipped） | 按冻结口径豁免，R8 帧数据待设备兼容 |

执行：`./gradlew :benchmark:connectedBenchmarkAndroidTest`（3 tests，0 failed，1 skipped）；
B-02 帧统计经 dumpsys gfxinfo 手工按协议复采（20 次滑动 reset 后 571 帧）。

## 4.y 0.7.0 发版回归（2026-10-30，豁免登记）

| 项 | 状态 |
|---|---|
| 设备状态 | 冻结设备 23078PND5G 在线但处于**安全锁屏**（wm dismiss-keyguard 无效，无法/不应绕过宿主锁屏）——冷启动 benchmark 无法采集 |
| 豁免理由 | 按既有口径（MIUI 测量冻结登记）显式豁免本轮 B-01/B-02 实采；0.6.0 基线（B-01 median 412.8ms / B-02 P50 23ms P95 32ms）留档为下轮对照 |
| 风险评估 | 本轮触碰渲染热路径的改动（R08 figure 管线/R09 覆盖物统一执行）均保留 native drawLines 批量与 FigureRenderer 原语路径；R01-R03 数据路径改动不进帧循环；预期帧耗时无结构性劣化 |
| 补测条件 | 设备解锁后执行 `./gradlew :benchmark:connectedBenchmarkAndroidTest`，P95 劣化 >10% 则进入 T-34 循环 |

## 4.z 0.8.0 发版回归（2026-10-30，豁免延续）

| 项 | 状态 |
|---|---|
| 设备状态 | 冻结设备 23078PND5G 仍处安全锁屏（同 §4.y）——B-01/B-02 实采豁免延续 |
| 风险评估 | 本轮触碰帧循环路径的改动：T03 值域计算聚合多实例（与渲染原聚合同量级）、T06 主图/额外轴入口接管线（drawLines 批量保留）、T09 覆盖物排序方向（遍历成本不变）——预期无结构性劣化 |
| 补测条件 | 同 §4.y；解锁后 `./gradlew :benchmark:connectedBenchmarkAndroidTest` 对照 0.6.0 基线（median 412.8ms / P50 23ms / P95 32ms） |

## 4.w 0.9.0 发版回归（2026-10-30，豁免延续）

| 项 | 状态 |
|---|---|
| 设备状态 | 冻结设备仍安全锁屏（同 §4.y/z）——B-01/B-02 实采豁免延续 |
| 风险评估 | 本轮触碰帧循环：VOL/MACD 改经 drawInstanceFigures（drawLines 批量保留在管线内部；VOL 柱经 drawBarPlots 批量矩形，与原 drawVolumeBars 同量级）；值域计算统一走 paneVisibleRange（一次聚合，原 renderer 自扫同量级）——预期无结构性劣化 |
| 补测条件 | 同 §4.y；解锁后 `./gradlew :benchmark:connectedBenchmarkAndroidTest` 对照 0.6.0 基线 |

## 4.u 0.10.0 发版回归（2026-10-31，豁免延续）

| 项 | 状态 |
|---|---|
| 设备状态 | 冻结设备仍安全锁屏（同 §4.y/z/w）——B-01/B-02 实采豁免延续 |
| 风险评估 | 本轮渲染改动：N02 绑定轴分组（HashMap 分桶同主图多轴块）、N04 attrs paneTop 平移（常数开销）、N06 控制点裁剪（clipRect 嵌套一层）——预期无结构性劣化 |
| 补测条件 | 同 §4.y |

## 4.v 0.11.0 发版回归（2026-10-31，豁免延续）

| 项 | 状态 |
|---|---|
| 设备状态 | 冻结设备仍安全锁屏（同 §4.y/z/w/u）——B-01/B-02 实采豁免延续 |
| 风险评估 | 本轮渲染改动：M02 轴解析移入实例循环（O(实例×轴) Map 查表，量级不变）、M03 BAR attrs 四键合并（无 attrs 快路径不变）/LINE coordinates 仅 attrs 行逐点、M04 控点块移入裁剪块内（clipRect 复用既有嵌套）、M05 范围收口处 specifyMin/Max 扫描（实例数线性）、M06 命中三段合并为单遍历（遍历次数减少）——预期无结构性劣化 |
| 补测条件 | 同 §4.y |

## 4.w 0.12.0 发版回归（2026-10-31，豁免延续）

| 项 | 状态 |
|---|---|
| 设备状态 | 冻结设备仍安全锁屏（同 §4.y/z/w/u/v）——B-01/B-02 实采豁免延续 |
| 风险评估 | 本轮改动：R7-01 引擎请求字段扩展（Long 比较，常数开销）+ 拒绝水位单变量、R7-02 extraAxisRange 聚合循环内增约束读（实例数线性）、R7-03 BAR 几何合成顺序调整（同量级计算）、R7-04 LINE 行段归属重构（段数与样式组数不变）——预期无结构性劣化 |
| 补测条件 | 同 §4.y |

## 4.x 0.13.0 发版回归（2026-10-31，豁免延续）

| 项 | 状态 |
|---|---|
| 设备状态 | 冻结设备仍安全锁屏（同 §4.y/z/w/u/v/w）——B-01/B-02 实采豁免延续 |
| 风险评估 | 本轮改动：R8-01 身份计数/水位移入 state（同量级原子操作）、R8-02 样式回调行号改前一行（同次数求值）、R8-03 自定义段端点门禁（减少无效 drawPath）——预期无结构性劣化 |
| 补测条件 | 同 §4.y |

## 4.y2 0.14.0 发版回归（2026-10-31，豁免延续）

| 项 | 状态 |
|---|---|
| 设备状态 | 冻结设备仍安全锁屏（同 §4.y/z/w/u/v/w/x）——B-01/B-02 实采豁免延续 |
| 风险评估 | 本轮改动：R9-01 门禁条件替换（同量级判断）+ 末可见行延伸段（每列至多 1 段）、R9-02 flush 拆分批次（批次总数不增、提交顺序归行序）——预期无结构性劣化 |
| 补测条件 | 同 §4.y |

## 4.y3 0.15.0 发版回归（2026-10-31，豁免延续）

| 项 | 状态 |
|---|---|
| 设备状态 | 冻结设备仍安全锁屏（同 §4.y/z/w/u/v/w/x/y2）——B-01/B-02 实采豁免延续 |
| 风险评估 | 本轮改动：批次容器 Map→List（同量级）+ segFor 末组比对（O(1)）——折返样式批次更多但与上游一致，常态连续样式批次数不变——预期无结构性劣化 |
| 补测条件 | 同 §4.y |

## 5. 后续动作（T-34 输入）

- B-02 首轮数据 vs 门槛差距分析 → 超标项进入 T-34 优化循环（视口裁剪、覆盖物批量绘制、指标线分帧）
- B-03 容量（100k）、B-04 突发、B-05 生命周期、B-06 30 分钟热稳定按序补测（长测项单独窗口跑）
- 每轮数据回填本文件 §4 并在 compatibility.md 台账登记

## 4.z 0.15.0 冻结基线实采（2026-11-01，豁免序列关闭）

> **第二轮审查更正（2026-11-01）**：下文“B-02 无回归、豁免闭环”不作为当前验收结论。分位数丢失已确认存在解析条件错误，普通/legacy janky 也被混称；本轮完整数据与判定见 §4.z2 及设备/发布第二轮报告。历史数值保留以便追溯。

设备解锁（keyguard 解除），`:benchmark:connectedBenchmarkAndroidTest` 实采（cpuLocked=true、compilationMode=verify，与非压缩 benchmark 变体口径一致）。

| 项 | 实测 | 门槛 | 结论 |
|---|---|---|---|
| B-01 冷启动 timeToInitialDisplayMs | runs=[414.3, 400.1, 430.7, 416.1, 419.2]，**median=416.1 / max=430.7** | 冷进程到完整图表 P95 ≤1500ms | **达标**（3.5 倍余量）；对 0.6.0 基线 median 412.8ms **无劣化**（+0.8%） |
| B-02 扩展负载（50k/10 指标/100 覆盖物，20 次全宽滑动） | 两跑一致：**totalFrames=551/553，janky=432/437（78-79%）** | 超时帧≤5%（FrameTimingMetric 口径，MIUI 不可用） | gfxinfo legacy「janky」（>16.7ms 记为 janky）与该门槛**不可比**：T-34 后 P50=22ms 状态下 janky 比例本应 ~78%——实测与 T-34 优化后状态**一致，无回归信号** |
| B-02 帧产出 | 551-553 帧/20 滑动（0.6.0 首轮 262 帧） | — | 帧产出 **2.1×**（同滑动次数） |
| B-02 分位数（P50/P95） | 未采得 | 输入 P95 ≤100ms | UiDevice shell 输出截断丢失分位数段（MIUI）；解锁 CPU 的手动复采（81/125-133ms）不可比（未锁频+热态，注入 8s vs 受控 1s 即证）——已登记口径边界，基准工具已补分位数逐行落 log（合规设备可采） |
| B02StressBenchmark（FrameTimingMetric） | SKIPPED（@Ignore 维持） | — | MIUI 14 vendor 限制不变，等价口径继续 |

**豁免序列关闭**：0.7.0~0.15.0 连续登记的 B-01/B-02 豁免以本轮实采闭环；PRD 7.3 B-01 达标、B-02 以等价口径确认无回归（严格 5% 门槛待 FrameTimingMetric 可用后复判，已登记）。

## 4.z2 0.15.0 设备/发布第二轮复核（2026-11-01）

冻结源码未改：core、compose、sample 的 Git tree 均与 `78a281b` 相同；本轮 HEAD `edc2fed`。K60U（6D8H6TXSDYXC85PZ）在线解锁。

| 项目 | 本轮结果 | 当前判断 |
|---|---|---|
| StartupTimingMetric，5 次冷启动 | TTID runs=486.8/431.1/426.0/419.8/448.2ms；median=431.1，max=486.8 | TTID 样本小于1500ms；不等于完整图表就绪 P95。median 较前轮416.1约+3.6%，较0.6.0的412.8约+4.4% |
| B-02完整 dump，第1/2次 | P50=19/19ms，P95=25/26ms，P99=30/32ms | 帧耗时观测，不能代替输入延迟；严格5%门槛仍未获可比证据 |
| B-02普通janky | 447/559（79.96%）；448/559（80.14%） | 与legacy字段分开；不得直接宣布达标或无回归 |
| B-02 legacy janky | 两次均559/559（100%） | 原文将约78%解释为legacy不准确 |
| 发布路径 | release构建、本地Maven发布、独立consumer clean+全量离线重编均成功；R8 release安装/启动留图 | 本轮覆盖通过，非远程仓库或应用商店发布 |
| 字体2.0 | release包复现VOL图例重叠及价格标签裁切 | 可读性验收不通过；字体已恢复1.0，未改实现 |

采集工具 `parseGfxInfo` 的 `endsWith("th percentile:")` 无法匹配 `50th percentile: 19ms`，故分位数缺失首先是解析错误，不是已证实的输出截断。此次使用仓库外于生产模块的证据脚本解析完整原始dump，未修改冻结测试或库实现。

两次B-02复跑均使用现有UiDevice测试（50k/10指标/100覆盖物、等待22秒、20次滑动、每次25步）。dump紧接测试结束保存，包含少量尾帧（第1次测试内552帧，随后完整dump559帧）；表中全部统计使用同一完整dump，不混用分母。Startup JSON记录cpuLocked=true；B-02为普通JUnit，且shell读取CPU min/max被拒，未独立证明B-02锁频条件与历史完全相同。

当前结论：**源码冻结保持；构建与本地发布路径通过；设备/性能发布验收尚未整体通过。B-02旧“无回归”关闭结论撤回，保留为待判定。**

完整审查、后续验证条件及证据见 [设备/发布第二轮报告](alignment/device-release-review-round2-2026-11-01.zh-CN.md)。

## 4.z3 0.15.1-rc1 纠正修复后的最终设备/发布验收（2026-11-01）

冻结的 0.15.0 提交及 8 个本地 Maven 制品保持原样；本轮候选另用 0.15.1-rc1。详细来源、失败试验、结构回归与剩余发布项见[第三轮报告](alignment/pre-release-final-acceptance-round3-2026-11-01.zh-CN.md)。

**口径更正**：旧压力调用未显式指定 main/isStack，上游语义对齐后产生 10 个额外 MA pane，连同原主图 MA 实际为 11 MA/13 pane。已固定为三 pane、10 MA 总实例、50k candle、100 segment，并断言数量及不存在额外 pane。本轮另固定竖屏 1220×2712、字体 1.0。此次是新的测量起点，不与旧夹具混算优化/劣化百分比。

| 项目 | 最终选定结果 | 当前判定 |
|---|---|---|
| B-01 R8 冷启动 TTID | 5 次 median 342.6ms / max 394.2ms | 仅首次显示，独立列示。 |
| B-01 R8 完整图表 TTFD | 5 次 494.5/459.0/412.5/441.8/451.7ms；median 451.7 / max 494.5ms | 本机样本均低于 1500ms；存在 Runtime Image 无法清除提示。 |
| B-01 等价热进程完整绘制 | 同 PID 重建 5 次，onCreate→ReportDrawnWhen/reportFullyDrawn：116.1/106.1/98.4/95.0/72.4ms | 等价口径均低于 500ms；不是 StartupTimingMetric。原 warm 指标缺失仍登记。 |
| B-02 R8 release | 546 帧；普通 janky 424（77.66%），legacy 546（100%）；P50/P90/P95/P99=22/26/28/34ms；HWUI deadline missed=424 | **计量可用，不能放行流畅门槛**。帧 P95 不等于输入 P95。 |
| B-02 严格 FrameTiming | 本轮重新启用实跑，仍无 renderthread slices | 严格 CPU 帧/FrameTimeline 指标受限；输入 P95 尚缺。 |
| B-03 R8 100k | 5 次注入就绪 1898/1737/1704/1807/1739ms；首批可交互 32–36ms | 本次应用计时均 ≤2000ms，非完整绘制 TTFD。 |
| B-05 | 30 次页面建销后注册表回基线、作用域取消 | 原确定泄漏已修复；retained heap ≤5MiB 尚未认证。 |
| B-06 | 1800.84 秒、31 次同 PID；0 FATAL/应用 ANR/crash；PSS 107.9–148.7MiB，前5/末5均值122.8/141.7MiB；温度38.8→37.5°C | 崩溃/ANR观测通过；内存趋势待查，B-06不全项关闭。 |
| 发布链 | 413 正式测试 + 72 独立回归；R8 9.1.29；本地发布、制品哈希核验和独立 consumer 通过 | 源码/构建链维持；最终发布仍 **HOLD**。 |

当前 R8 release 的 HWUI 已可采，旧“R8 一律零帧”的结论不再适用。完整输出由基准保存，分位数解析已修复，普通/legacy/GPU 字段分离。原 warm Macrobenchmark 有“五份 trace 仅首份有效”及“无指标”失败记录，已显式保留并增加独立等价测试，未据缺样本认证五轮达标。

配置截图已加入实际长宽与稳定等待检查；双指、长按及连续无障碍动作均有真机复验。最高价标记仍与顶部图例局部相交；TalkBack 真人朗读和 heap 证据未关闭。当前报告不支持“所有视觉项关闭”或“已满足全部上线门槛”。


### 4.z4 设备/发布第四轮：定向修复复验（2026-11-01，0.15.1-rc2）

**当前发布结论仍为 HOLD。** 最高/最低价文字避让、真人 TalkBack、真实 heap 和本机 30 分钟内存趋势已有证据；帧性能与轻载输入仍超标，严格 warm 仍缺样。

- 纠正历史绝对表述：显式捕获配置已在同一非 debuggable R8 包上取得 AndroidX FrameTiming，不再将默认配置失败认定为设备全面不可采。压力 CPU P95 两次为 22.95/21.67ms，overrun 明显超标。
- 输入按事件 ID → HWUI 帧 ID → SurfaceFlinger 实际呈现逐项核对，未用帧耗时代替：轻载 100/100，P95=69.02ms（>50，失败）；压力 100/100，P95=77.09ms（≤100，仅该子项通过）。这是合成触摸稳态 MOVE 的软件链路，不含物理触控扫描。
- strict warm 五个独立样本仅 2 个有效，另外 3 个明确失败；不能按有效子集计算全量 P95。冷启动完整图表五个样本均≤458ms。
- B-06 完整 1800.88s、31 点同 PID、0 FATAL/应用 ANR/crash，暖机后前后半段分项基本持平，后台图形资源可回收。最终 R8 APK 30 次建销的目标对象 retained=0；Debug 另覆盖 100 切品种，R8 100 切品种仍未单测。

完整原始数据、失败试验隔离、结构/逻辑回归与范围说明见[第四轮报告](alignment/targeted-fix-retest-round4-2026-11-01.zh-CN.md)。不把这些结果扩展为最终上线通过。


### 4.z5 设备/发布第五轮收尾（2026-11-02，0.15.1-rc3）

**当前发布仍为 HOLD；0.15.0 冻结提交和 8 个制品哈希未改。**

- PJZ110（OnePlus，Android 17/API 37）官方 FrameTiming 两次 CPU P95=12.22/12.24ms，均 >10ms；overrun 21/496（4.23%）和 19/497（3.82%）满足该 ≤5% 子项。
- 新机严格 warm 完整 5/5，TTFD=71.10/75.14/69.95/68.12/97.05ms；cold 完整 5/5，TTFD=221.52/215.96/231.73/228.26/220.59ms。每个 warm 预热/测量对同 PID，不用等价 ready 计时替代。
- 原独立驱动输入两组均 100/100 完整关联：轻载 P95=52.23ms（>50，失败）；50k/10 MA/100 overlay 压力 P95=51.89ms（≤100，仅该子项通过）。
- 最终同一 R8 APK 在 PJZ110 与 K60U 均完成 30 建销 + 100 切品种；各 290 弱引用释放、目标实例/retained=0。K60U 此次仅补测此范围，其余性能仍是旧包历史结果。
- PJZ110 B-06：1800.95s、31 点同 PID、0 FATAL/应用 ANR/crash；PSS 79.95–126.48MiB。暖机前后半段 Native 已分配 17.73→18.40MiB，小幅增长保留观察，未写成无限时长不增长。
- 新机 2.0 字体横屏的副图图例仍会侵入相邻 pane；未把自动交互通过当作视觉验收通过。新机真人 TalkBack 与 Android 6 尚未认证。
- 生产增量保留既有结构与 figure 顺序：API 23 迭代修复、真实示例切品种入口、普通像素标签布局复用及长文本原绘制回退、版本标识一致。414 正式、72 独立回归、12 个不同原生用例通过；构建、Lint、本地发布与独立 consumer 核验完成。

完整证据与边界见[第五轮定稿报告](alignment/targeted-fix-retest-round5-2026-11-02.zh-CN.md)。历史段落按其当时设备/候选理解，不替代上述当前判定。

### 4.z6 设备/发布第六轮（2026-11-02，0.15.1-rc4，K60U）

**发布仍为 HOLD；0.15.0 冻结提交和本地仓库 8 个原制品哈希一致。** 副图 tooltip 及缓存重放补齐 pane 裁剪与可见命中区域，保留换行、字号、指标取值和绘制顺序。真实字形回归 7 项、PNG/示例回归 5 项、最终 R8 六组字体/方向与交互通过；PJZ110 未连接，原机 rc4 仍待补验。

| 当前 K60U / R8 rc4 计量 | 结果 | 判定 |
|---|---|---|
| 官方帧两次 | CPU P95 18.34/18.66ms；正 overrun 409/409、404/404，均 100% | CPU >10ms，overrun >5%，失败。 |
| 冷启动 TTFD | 5/5，490.56–517.14ms；样本 P95 515.77ms | ≤1500ms，通过。 |
| 严格 warm | 五个参数样本全部缺 AndroidX 指标，0/5 有效 | 未通过；没有以离线 trace 或等价计时替代。 |
| 轻载 / 压力输入 | 各 100/100 精确关联；P95 70.21 / 70.33ms | 轻载 >50ms 失败；压力 ≤100ms 子项通过。 |
| 独立 HWUI | 550 帧、janky 401、P95 21ms | 补充诊断，不与官方帧或输入混算。 |
| 100k 容量五次 | 1896/1989/2147/2130/2045ms；样本 P95 2143.6ms | >2000ms，失败；不是 TTFD。 |

正式 JVM 414 项通过，本地发布/独立 consumer 离线 clean 重编与实际制品哈希核验通过。设备设置已恢复，保留 rc4 最终 R8 包。当前帧数值不构成修复前后同源码 A/B 优化证明；本轮未重跑 rc4 双机 heap、B06 长测或真人 TalkBack。[完整报告与证据](alignment/targeted-fix-retest-round6-2026-11-02.zh-CN.md)。


### 4.z7 设备/发布第七轮阶段检查（2026-11-02，0.15.1-rc5）

**仍为 HOLD，尚未完成双机最终验收。** R2-60-v1 在本轮新测量前锁定；0.15.0 八个原制品哈希一致。warm 捕获、连续Path、Double坐标、文本时间缓存、绘制层及报价观察范围的阶段改动保留；双机续验额外修复2.0横屏低价/MA遮挡，删除投影中3行重复快照保护。无充分帧收益的文字helper与报价图层试验已撤回，原架构/回调顺序保留。

当前两台真机均为R8 APK **`8c5895b3c581c7e58cb3fc20e82a11b7a0cb4f363fc479bb7230e2c09032961a`**，实际60Hz基础路径：

| 当前8c / 负载 | 结果 | 判定与覆盖 |
|---|---|---|
| K60U / S0实时帧 | CPU P95 19.258404ms；正overrun 53/486=10.905350% | 超过16ms/5%，失败。 |
| K60U / S2压力帧 | CPU P95 13.773315ms；正overrun 60/480=12.5% | CPU通过；overrun超过10%，失败。 |
| PJZ110 / S0实时帧 | CPU P95 10.103645ms；正overrun 4/508=0.787402% | 一次短测通过，尚缺双次正式矩阵。 |
| PJZ110 / S2压力帧 | CPU P95 9.059870ms；正overrun 1/507=0.197239% | 一次短测通过，尚缺双次正式矩阵。 |

历史cf37包：K60U冷/warm各5样本P95 490.5330/212.5356ms、容量五进程P95 1861.8ms通过；轻载帧与两类输入各一次短测通过。PJZ110该包S0/S1/S2各两次、两类输入各两次共400/400、冷/warm/容量五样本均数值通过，但发现大字体横屏价格标签遮挡。后续c35已修复遮挡，当前8c保留修复；这些历史数值不转移到8c，不拼接最好结果放行。

当前JVM417、K60U原生21、release lint、8类公开JVM签名、本地rc5 LocalRepo/MavenLocal及独立consumer离线clean/实际制品SHA核验通过，最终重建字节与实测8c完全一致。两机各六组字体方向及双指/长按/连续前根动作自动复验通过，12张字体截图已逐一检查；2.0竖屏VOL尾行仍会在自身pane内裁剪。API23 ARM64模拟器同包安装/行情/平移/新PNG通过，不称Android6实机认证。最终双机性能与输入双次矩阵、启动/容量/B04、释放heap、B06及真人TalkBack仍待执行。设备设置与PJZ测试runner临时后台策略已恢复。

[第七轮执行报告](alignment/release-readiness-round7-execution-2026-11-02.zh-CN.md)、[验收证据索引](evidence/release-readiness-round7-20261102/acceptance-index.json)。
