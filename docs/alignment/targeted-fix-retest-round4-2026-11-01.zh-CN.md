# 设备/发布第四轮：未关闭项定向修复与复验

日期：2026-11-01。候选：**0.15.1-rc2**。发布判定：**HOLD，最终验收未通过**。

本轮已修复最高/最低价文字与图例的碰撞，补齐真人 TalkBack、真实 heap、30 分钟内存归因及严格帧/输入计量。**帧性能与轻载输入延迟实际超标，严格 warm 五样本仍不完整，不能上线。** R8 的 100 次切品种场景尚未单独执行，也不记为通过。

本报告承接[第三轮最终验收报告](pre-release-final-acceptance-round3-2026-11-01.zh-CN.md)，不是第十二轮上游源码全库审计。源码冻结基线仍为 **0.15.0 / 78a281b7f9c470bf7b98908443a7f29fa659a56a**，上游目标仍为仓库中的 **KLineChart v10.1.0**。原有工作区修复保留，没有提交、覆盖冻结版本或远端发布。

## 1. 原未关闭项的当前状态

| 原项目 | 本轮结论 | 证据及边界 |
|---|---|---|
| P2 最高价标记与图例相交 | **已修复、复验通过** | 真实字框避让；原生 12 配置覆盖字体 1.0/1.3/2.0、横竖尺寸及正反轴；包含首次录制与缓存回放。实际 K60U 六种字体/方向截图另行检查。 |
| R-13 真人 TalkBack | **人工验收通过** | 用户确认五项均通过、朗读清晰且无焦点问题；最后候选的连续无障碍动作自动回归也通过。人工测试后仅调整渲染录制/绘制层级，无障碍摘要与动作逻辑未改。 |
| B-06 内存趋势 | **本机既定 30 分钟协议通过** | 1800.88s、31 点同 PID、0 FATAL、0 应用 ANR/crash；暖机后前后半段分项基本持平，后台回收可释放图形内存。不是无限时长或其他设备认证。 |
| B-05 retained heap | **真实 heap 缺证据项已补齐；场景仍有边界** | 最终非 debuggable R8 APK 的 30 次建销，目标对象 retained=0；Debug 的 30 建销及 100 切品种也为 0。**R8 100 切品种未单测，不将 Debug 结果冒充发布变体全场景通过。** |
| P1 B-02 流畅性 | **未关闭，实际超标** | 取得 AndroidX 原生 FrameTiming；CPU P95=22.95/21.67ms，明显高于记录的 10ms 目标；匹配样本的 overrun 比例仍远超 5%。 |
| 输入到反馈 | **轻载失败；压力子项通过** | 两组均 100/100 完整事件关联：轻载 P95=69.02ms >50；压力 P95=77.09ms ≤100。不能用压力较宽门槛替代轻载门槛。 |
| 严格 warm StartupTimingMetric | **未关闭** | 五个独立样本仅 0/1 有效（178.17/188.01ms），2/3/4 明确缺指标失败。没有计算全量 P95，也没有接受等价计时豁免。 |

## 2. 代码修复与结构/逻辑一致性

### 2.1 最高/最低价标记

原生完整渲染用例首先复现失败：普通字体、1220px 宽度时，标记像素落入图例字框。修复后，标记基于真实文字尺寸避让主图 tooltip 的实际占用矩形，包含换行、卡片和功能图标；水平方向遵循上游按锚点处于左右半区决定文字朝向，反向轴沿用对应的高低位置关系。

为了首帧就取得真实字框，主 tooltip 在 Compose 边界先录制布局操作，再在原 tooltip 层回放。同一实现负责直接绘制与录制；缓存未命中时数据源只求值一次。标记仍在原 `drawMainPane` 位置绘制，**标记在主 pane 内的原调用点，以及 pane、覆盖物、时间轴/tooltip 的原提交顺序保留**。没有改价格投影、最高/最低值选择、指标计算或 figure 批次顺序。

极小 pane 若已没有能容纳文字的空位，保留钳制后的标记，不以隐藏标记或缩小字号制造通过。本轮关闭的是所列配置的碰撞问题，不声称任意拥挤布局均能无碰撞。

证据：[修复前失败](../evidence/targeted-retest-round4-20261101/marker-red.txt)、[最终原生字框/回放三项测试](../evidence/targeted-retest-round4-20261101/final-typography.txt)、[双倍字体竖屏实图](../evidence/targeted-retest-round4-20261101/final-validation/ui/font-2.0-portrait.png)、[R8 交互与六配置复验](../evidence/targeted-retest-round4-20261101/final-r8-ui-startup.txt)。

### 2.2 局部热点优化

诊断采样发现覆盖物逐点投影反复求相同主图可见值域。`ChartState.mainPaneRange` 增加单项结果缓存，以 session、revision、可见区间首尾作为失效条件；缓存只持有数值 Pair，**不持有旧 DataSnapshot/蜡烛列表**。新增回归覆盖平移、缩放、历史修正、反向轴、切市场与空数据恢复。

扩大 TextMeasurer 缓存至 512 的单变量试验未呈现明确收益，已撤回，最终仍为 256。候选 FrameTiming 两次结果与重建对照区间重叠，**不宣称整体帧性能显著改善或已无回退**。本轮保留可定位的局部优化，但 P1 没有达到放行门槛。

### 2.3 与原对齐目标的关系

Compose 生产增量集中于 6 个渲染/状态文件，见[本轮相对 rc1 源码补丁](../evidence/targeted-retest-round4-20261101/compose-round4.patch)。core 仅顺延版本标识，没有替换 Store、指标算法、数据会话、水位或覆盖物模型。受影响的 7 类公开签名对照无移除，ChartText 原构造签名保持；这不是全库 ABI 认证。

**结构与逻辑一致性回归维持，设备性能目标仍未达成。** 72 项独立回归及原有批次顺序组合继续通过；319 行七面台账检查只证明引用与覆盖合法，不等于全部 319 行行为均已认证。本轮没有重新认证上游 main 或扩大上一轮源码行为认证范围。

证据：[414 项正式计数](../evidence/targeted-retest-round4-20261101/frozen-formal-counts.json)、[正式 XML 归档](../evidence/targeted-retest-round4-20261101/frozen-formal-tests.zip)、[独立 72 项 XML](../evidence/targeted-retest-round4-20261101/layer-final-external-tests.xml)、[受影响 ABI](../evidence/targeted-retest-round4-20261101/final-affected-abi.json)、[台账校验](../evidence/targeted-retest-round4-20261101/alignment-check.txt)。

## 3. 性能计量与实际结果

设备：K60U / 23078PND5G / corot，Android 14，1220×2712 竖屏、480dpi、字体 1.0；保留起止显示状态，系统设置为 120Hz 档。使用固定种子静态负载。所有下表发布样例均为 **R8 9.1.29、非 debuggable、本地 debug 签名**。Macrobenchmark 与普通 HWUI、长测分别列示；未绕过 CPU 权限或修改系统运行时限制。

| 项目 | 最终采信值 | 验收结论 |
|---|---|---|
| 冷启动完整图表 TTFD | 450.20、452.83、457.81、432.12、448.37ms | 五个观察均 ≤1500ms；未据此认证生产首次安装分布。TTID 单独保存。 |
| 严格 FrameTiming，第 1 次 | 327 个匹配样本；CPU P95 **22.95ms**；overrun P95 **16.83ms**；327/327 个 overrun>0 | **失败**。该分母是 AndroidX 可匹配样本，不冒充全部硬件帧。 |
| 严格 FrameTiming，第 2 次 | 344 个匹配样本；CPU P95 **21.67ms**；overrun P95 **15.77ms** | **失败**，重复采样仍未达标。 |
| rc1 源码重建参考 | CPU P95 22.92ms；overrun P95 16.53ms | 与候选区间重叠；不构成显著改善证据。 |
| R8 HWUI 两次独立 B-02 | 553/552 帧，janky 442/440，P95 均 27ms | 计量可用，仍有明显超时；**不与 CPU 帧耗时或输入延迟混算**。 |
| 轻载输入 → 实际呈现 | 1000 candle / 1 MA / 0 overlay；100/100；P95 **69.02ms**，max 69.83ms | **失败**，高于 50ms。这是固定轻载 UI 夹具，不能冒充 B-01 500 根场景的全覆盖认证。 |
| 压力输入 → 实际呈现 | 50k / 10 MA 总实例 / 100 segment、三 pane；100/100；P95 **77.09ms**，max 78.31ms | 此子项 ≤100ms；**不能关闭 B-02 的 CPU/deadline 失败**。 |
| 100k 注入就绪 | 1553, 1533, 1592, 1627, 1601ms；首批 1000 根 10–11ms | 本轮五次均 ≤2000ms；注入就绪仍不等于完整绘制 TTFD。 |

FrameTiming 历史默认配置失败的记录保留。显式加入进程发现、线程命名与帧时间线采集后，本轮已取得原生指标，**撤回“该设备对所有非 debuggable 构建均不可采”的绝对判断**。没有单独做配置逐项消融，不将成功武断归因于某一个开关；AndroidX 查询本身接受 `DrawFrame%` 前缀，不能把 `DrawFrames` 的拼写直接当根因。参照 [AndroidX FrameTimingQuery](https://android.googlesource.com/platform/frameworks/support/+/dd97834aa54671ee1f56d65fa46668b4ffeb57e8/benchmark/benchmark-macro/src/main/java/androidx/benchmark/macro/perfetto/FrameTimingQuery.kt)。

rc1 对照重建的 117 项生产源码哈希、R8 mapping 与上一轮一致；重建 APK 封装哈希不同，**没有冒充原始 rc1 APK 的二进制复测**。该组仅作同协议源码等效参考，不据单次差值推导优化百分比。

证据：[官方指标原始 JSON](../evidence/targeted-retest-round4-20261101/strict-benchmarkData.json)、[五个独立 warm 的实际失败](../evidence/targeted-retest-round4-20261101/strict-metrics.txt)、[候选复采](../evidence/targeted-retest-round4-20261101/rc2-repeat-frame.json)、[rc1 重建证明](../evidence/targeted-retest-round4-20261101/rc1-rebuild-code-proof.json)、[HWUI 两次摘要](../evidence/targeted-retest-round4-20261101/final-b02-summary.json)、[容量五次](../evidence/targeted-retest-round4-20261101/capacity-ready.json)。

### 输入计量的因果链与纠正

计时定义为 **合成触摸 MOVE 的事件时间 → 实际显示帧呈现**，不是物理触控硬件扫描延迟。逐项关联注入窗口内唯一 InputDispatcher 事件 ID、目标进程接收记录、HWUI 的 InputEventId/FrameTimelineVsyncId，再与 SurfaceFlinger 实际呈现时间核对；校准单调时钟，拒绝不完整或不一致的样本。P95 使用 inclusive 线性插值，同时保存 nearest-rank 值和最大值。

首帧会归因给 DOWN，因此先完成一个不计时的滚动帧再测后续 MOVE，符合 [Android 14 InputEventAssigner](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-datastore-release/android-34/android/view/InputEventAssigner.java) 的规则。计时之后的额外手势仅用于排空延迟上报的 HWUI 呈现时间，按事件 ID 排除，不进入分位数。

复核还纠正了关联脚本的数字文件排序，以及 HWUI 尚未更新时返回旧正时间戳的问题；原始数据保留，新增真实数据回归。最终两组均 **100/100** 核对，未用相邻帧、缺样剔除或帧耗时替代。

证据：[轻载完整关联](../evidence/targeted-retest-round4-20261101/input-final-normal/joined-latency.json)、[压力完整关联](../evidence/targeted-retest-round4-20261101/input-final-stress/joined-latency.json)、[事件关联脚本](../evidence/targeted-retest-round4-20261101/analyze-input.py)。 另存[两组原始 trace 归档](../evidence/targeted-retest-round4-20261101/input-final-traces.zip)及[trace 哈希](../evidence/targeted-retest-round4-20261101/input-final-trace-hashes.json)。

## 4. 内存与释放证据

R8 压力长测 **1800.88 秒、31 点同 PID 21827**；0 FATAL、0 应用 ANR/crash 事件。PSS 范围 **115.73–140.05MiB**，温度 36.8→36.5°C。

| 分项 | 暖机后前 15 点均值（分钟 1–15） | 后 15 点均值（16–30） |
|---|---:|---:|
| 总 PSS | 130.74MiB | 130.31MiB |
| Dalvik 已分配堆 | 16.92MiB | 16.89MiB |
| Native 已分配堆 | 37.12MiB | 37.42MiB |
| GL mtrack | 24.05MiB | 24.17MiB |

上一轮前后五点 PSS 增量约 19MiB，其中约 95% 来自 GL mtrack，不能直接归因 Java 对象泄漏。本轮未复现上一轮后段 GL 台阶。长测结束后，同 PID 后台/trim 实验使 PSS **114.92→74.42MiB**，EGL 34,118KiB→0，GL 24,116→16,924KiB，支持图形资源可回收的判断。PSS 不与 retained≤5MiB 混用。

真实 heap 的证明范围：

| 变体/操作 | 结果 |
|---|---|
| Debug：30 建销 + 100 切品种 | 90 个弱引用全部释放；Shark 未发现残留 ChartState/LKanvasChart/MainActivity，目标 retained=0。 |
| **最终非 debuggable R8 APK：30 建销** | 30 个 Activity 弱引用释放；按实际 R8 mapping 的 er0/xi/MainActivity 分析，目标实例与可达保留对象均 0，目标 retained=0。 |
| R8：100 切品种 | **未执行，不记通过。** |

R8 heap 使用同签名 instrumentation 在目标进程调用自身的公开 heap dump API，**没有改生产 APK 的 debuggable/profileable 标志或加入运行时依赖**。为解决 AGP 排除目标运行库、而目标类又已被 R8 改名造成的测试启动失败，仅给测试 APK 补齐 Kotlin stdlib 与 tracing；两次启动失败在正式长测之后，单独归档，未被隐藏或算成通过。

证据：[31 点复核汇总](../evidence/targeted-retest-round4-20261101/soak-reviewed-summary.json)、[逐分钟数据](../evidence/targeted-retest-round4-20261101/soak-samples.jsonl)、[连续应用日志](../evidence/targeted-retest-round4-20261101/soak-app-live.log)、[后台回收](../evidence/targeted-retest-round4-20261101/memory-reclaim-summary.json)、[最终 R8 heap 用例](../evidence/targeted-retest-round4-20261101/release-heap-final-device.txt)、[R8 Shark 分析](../evidence/targeted-retest-round4-20261101/release-heap-analysis.txt)、[R8 原始 heap 归档](../evidence/targeted-retest-round4-20261101/release-heap-evidence.zip)、[实际类映射](../evidence/targeted-retest-round4-20261101/release-heap-class-mapping.json)、[Debug 切品种分析](../evidence/targeted-retest-round4-20261101/layer-final-heap-analysis.txt)。

## 5. 回归、制品与人工验收

- 正式 JVM：core 222 + compose 192 = **414**，失败/错误/跳过均 0；独立原第十一轮 **72**，失败/错误/跳过均 0。
- Android 原生六个不同用例范围通过：字形、标记、首次/缓存回放、PNG、生命周期、30 建销/100 切品种 heap。R8 交互复合用例及最终 R8 释放用例另列。没有将所有试验描述为“全绿”：严格 warm 三个样本失败，性能数值也未通过门槛。
- 用户真人 TalkBack 确认：时间/OHLC/指标朗读、焦点顺序、连续上一根再下一根、缩放、两条画线遍历与删除，**五项均通过**。没有把自动语义树检查代替人工听感。
- 库与示例 release Lint 通过；示例既有图标警告保留。`com.android.test` 未提供本次尝试的 benchmark Lint 任务，未把任务缺失记作 Lint 通过；benchmark 由实际编译及设备执行验证。
- rc2 本地 Maven 仓库、Maven local 与独立 consumer 的干净离线编译及实际解析哈希核对通过。原 0.15.0 的 8 个冻结制品哈希不变。
- 最终安装 APK SHA-256：`b50f47876e23cc0c4f36263888bca0b2f72e6375b1f9e9792ee97d8ff0b17aec`。长测前已冻结生产输入，后续只修测试工具；最终安装包与制品再次一致。字号、旋转和 TalkBack 均已恢复。

证据：[最终原生三项字形/回放](../evidence/targeted-retest-round4-20261101/final-typography.txt)、[其他原生回归](../evidence/targeted-retest-round4-20261101/layer-final-android.txt)、[真人反馈](../evidence/targeted-retest-round4-20261101/talkback-human-result.json)、[最终 release Lint](../evidence/targeted-retest-round4-20261101/final-release-lint.txt)、[独立 consumer](../evidence/targeted-retest-round4-20261101/final-consumer-verified.txt)、[冻结基线未改](../evidence/targeted-retest-round4-20261101/baseline-artifacts-unchanged.json)、[最终制品](../evidence/targeted-retest-round4-20261101/final-artifacts-sha256.json)、[最终安装及设置恢复](../evidence/targeted-retest-round4-20261101/final-installed-and-restored.json)。

## 6. 收敛情况与下一轮验收点

**功能和证据缺口在收敛，性能放行尚未收敛到门槛。** 最高价遮挡、人工朗读、30 分钟内存趋势及发布包真实 heap 已有明确结论；帧和输入也从“缺计量”变为可核验的数值。源码结构/逻辑回归维持，但不能据此宣告设备验收通过，也不能把关闭项数量当完成百分比。

下一轮仍需关闭：

1. **P1 绘制/呈现性能**：在保留原指标、回调和 figure 顺序契约下，降低 CPU 帧耗时与 overrun，并使轻载输入 P95≤50ms；继续保留压力输入≤100ms子项。当前没有证据保证只需一轮。
2. **严格 warm 五个完整有效样本**：当前 2/5，不以两个快样本、五份 trace 或等价 ready 计时放行。
3. **发布变体的 100 次切品种路径覆盖**：在真实使用该路径的 R8 宿主中补测；本轮的 Debug 100 次与 R8 30 建销分别保持其证明范围。

未覆盖 Android 6、其他厂商/刷新率组合、全部自定义插件与异常交互；未做生产签名、远端 Maven 或应用商店发布。此轮没有穷举全库 ABI/源码对齐，不取代原十一轮范围说明。

## 7. 复现与证据入口

先复制脚本到新的证据目录，避免覆盖本轮数据。安装 release APK 后再运行自仪器化 benchmark；直接运行 `connectedBenchmarkAndroidTest` 会安装非压缩目标，不能冒称 R8。

```sh
./gradlew :lkanvas-core:test :lkanvas-compose:testDebugUnitTest
./gradlew :app-sample:assembleRelease :benchmark:assembleBenchmark
adb -s 6D8H6TXSDYXC85PZ install -r app-sample/build/outputs/apk/release/app-sample-release.apk
adb -s 6D8H6TXSDYXC85PZ install -r benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk
adb -s 6D8H6TXSDYXC85PZ shell am instrument -w -r -e class io.lkanvas.benchmark.FrameTimingBenchmark io.lkanvas.benchmark/androidx.test.runner.AndroidJUnitRunner
```

[30 分钟固定协议脚本](../evidence/targeted-retest-round4-20261101/thermal-soak.py)、[后续短测编排](../evidence/targeted-retest-round4-20261101/post-soak.py)、[R8 heap 测试运行库配置](../evidence/targeted-retest-round4-20261101/release-heap.init.gradle)、[生产输入清单](../evidence/targeted-retest-round4-20261101/final-production-manifest.json)、[验收索引](../evidence/targeted-retest-round4-20261101/acceptance-index.json)。测试 APK 的运行库配置只作用于测试构建；按[测试运行库输入](../evidence/targeted-retest-round4-20261101/test-runtime-inputs.json)复制 stdlib、从 tracing AAR 提取 classes.jar 到两个 /tmp 路径后使用，不修改生产依赖。
