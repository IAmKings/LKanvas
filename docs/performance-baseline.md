# 性能基线（P1 性能线：Macrobenchmark 骨架与门槛冻结）

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

## 5. 后续动作（T-34 输入）

- B-02 首轮数据 vs 门槛差距分析 → 超标项进入 T-34 优化循环（视口裁剪、覆盖物批量绘制、指标线分帧）
- B-03 容量（100k）、B-04 突发、B-05 生命周期、B-06 30 分钟热稳定按序补测（长测项单独窗口跑）
- 每轮数据回填本文件 §4 并在 compatibility.md 台账登记
