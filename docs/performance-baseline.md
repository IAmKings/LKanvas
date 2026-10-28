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

**余量与下一步**（T-34 第二轮输入）：P50=27ms 仍超 16.7ms 预算——
剩余热点为 candle（180 根×3 rect）+ 65 列指标线（15k lineTo/帧，stress 合成
负载 10 个 MA 参数 2..11）+ 100 覆盖物逐帧全量遍历。方向：可见区裁剪覆盖物、
指标线抽稀/分层缓存（内容随视口平移需 tile 化或 GraphicsLayer 正确用法）、
candle 同色合并 Path。**注意**：优化前后的 P50/P95 均为 UI 线程 CPU 口径
（gfxinfo frameDurationCpuMs），与 PRD 7.3 的 D1 目标同一计量定义。

## 5. 后续动作（T-34 输入）

- B-02 首轮数据 vs 门槛差距分析 → 超标项进入 T-34 优化循环（视口裁剪、覆盖物批量绘制、指标线分帧）
- B-03 容量（100k）、B-04 突发、B-05 生命周期、B-06 30 分钟热稳定按序补测（长测项单独窗口跑）
- 每轮数据回填本文件 §4 并在 compatibility.md 台账登记
