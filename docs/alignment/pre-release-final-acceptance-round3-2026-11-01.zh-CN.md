# 上线前最终验收第三轮：纠正、修复与 K60U 复验

日期：2026-11-01。候选版本：0.15.1-rc1。当前发布判定：**HOLD**。

> B-06 已完成 1800.84 秒、31 次采样。崩溃/ANR 观测通过，内存趋势仍待解释。HOLD 不以 JUnit 是否全绿或单项长测存活代替发布门槛。

## 1. 结论与基线

本轮修复了像素字号重复缩放、图例行高与换行、轴标签过滤与越界、示例图表生命周期、连续无障碍切换缓存、API 23 原子操作兼容性、R8/Kotlin 元数据兼容性，以及压力夹具和计量工具的问题。

**源码对齐基线维持；设备发布验收尚未闭环。** 413 项正式 JVM 测试、72 项原第十一轮独立回归和本轮选定真机用例通过。实际截图仍发现最高价标记与顶部图例相交；B-02 的 HWUI 超时信号显著，严格帧/输入延迟、retained heap 与真人 TalkBack 证据尚不完整，不能宣告可上线。

冻结的 0.15.0 提交为 78a281b7f9c470bf7b98908443a7f29fa659a56a。本次工作基于 edc2fede9efcd9c92e1bfd589bb09aa015dc1acb 的工作区增量，候选修复未提交。没有将新代码重新包装为 0.15.0：原本地仓库的 8 个 0.15.0 Maven 制品哈希全部保持一致。

本报告是**设备/发布验证第三轮**，不将其另计为第十二轮上游源码审计。固定上游目标仍为仓库中的 KLineChart v10.1.0；本轮没有重新认证上游 main 的最新增量。

证据：[冻结制品核对](../evidence/release-candidate-round3-20261101/baseline-artifacts-unchanged.json)、[最终制品哈希](../evidence/release-candidate-round3-20261101/final-artifacts-sha256.json)、[设备安装 APK 核对](../evidence/release-candidate-round3-20261101/installed-apk-proof.json)、[候选补丁](../evidence/release-candidate-round3-20261101/candidate-production.patch)。

## 2. 已修复问题及复验

| 项目 | 原因与最终处理 | 证明范围 |
|---|---|---|
| 像素字号重复缩放 | ChartText 收到已换算的 px，原来再次按 sp 绘制。屏幕绘制按 DrawScope 将 px 换算为 sp；测量明确使用像素密度，避免继承屏幕 TextMeasurer 的导出密度。 | PixelTextStyleTest；Android 实际字体度量；普通/大字体截图；PNG 编解码。 |
| 图例拥挤与跨 pane | Styles 字号仍沿用 sp；行距改为实际字形高度，主副图标准图例按可用宽度排列换行。缓存键补入布局、密度、字体缩放，避免复用旧位置。 | TypographyLayoutTest 验证文字框；真实截图。此测试不覆盖最高价标记的避让。 |
| 轴标签缺失/越界 | 原来按值升序遍历，却假定屏幕 y 也递增，导致后续标签被过滤。统一按屏幕坐标排列，使用实际文字高度避让，边界标签在 pane 内钳制；反向轴沿用对应投影。 | AxisLabelLayoutTest 4 项；主/副图及大字体实际截图。 |
| 短容器与额外 pane 越界 | 初次布局先初始化副图默认高度；分隔线占位计入预算，极端短屏按实际可用高度压缩布局。 | PaneContainmentTest；原拖动/设高精确断言继续通过。 |
| 示例页面实例与协程释放 | 原页面重建 3 次关闭后仍留下 4 个全局图表实例。页面离开时 dispose；ChartState 使用页面拥有的 Compose 协程作用域。 | K60U 30 次创建/关闭后注册表回到基线，所采作用域均已取消。未据此认证 retained heap ≤5MiB。 |
| 连续无障碍动作摘要不刷新 | 所选索引递减与版本号递增在相加缓存键中抵消。改为独立 remember 键，宿主图例缓存同样处理。 | 真实 AccessibilityNodeInfo 连续执行两次“上一根”，每次摘要均改变；双指缩放和长按也使用真实输入。 |
| API 23 兼容 | AtomicLong.updateAndGet 确实要求更高 API；改为等价 CAS 最大值更新。Lint 对 Kotlin 内联 forEach 的若干报告经字节码检查属于误判，相关位置改为普通循环以消除歧义。 | EpochWatermarkTest 并发验证最大水位；库 Lint 无问题。未在 Android 6 真机运行。 |
| R8 元数据兼容 | 原 AGP 内嵌 R8 8.13.19 无法正确处理 Kotlin 2.4 元数据。按官方插件加载入口覆盖为 9.1.29，独立 consumer 同步配置。 | 实际 release mapping 的 compiler_version=9.1.29；最终 R8 构建和真机运行通过；旧元数据错误未再出现。 |
| 公开构造器兼容 | 修复方案中曾新增密度构造参数；二进制对照发现其会移除旧构造签名，最终取消该参数，在内部处理像素密度。 | 对本次涉及的 ChartText 公开签名进行对照，零移除。这不是全库 ABI 认证。 |

相关代码：[ChartText](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/ChartText.kt)、[轴标签布局](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/AxisLabelLayout.kt)、[图例](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/TooltipRenderer.kt)、[页面生命周期与完整绘制](../../app-sample/src/main/kotlin/io/lkanvas/sample/MainActivity.kt)、[ABI 对照](../evidence/release-candidate-round3-20261101/chart-text-abi.json)。

R8 版本要求及覆盖入口依据：[Android Kotlin 支持表](https://developer.android.com/build/kotlin-support)、[R8 官方说明](https://r8.googlesource.com/r8/+/refs/heads/main/README.md)。API 兼容边界依据：[Android Java API desugaring 说明](https://developer.android.com/studio/write/java8-support)。

## 3. 验证工具与历史结论纠正

### 3.1 压力夹具不能沿用旧默认参数

旧夹具重复调用 createIndicator(MA) 时未显式指定 paneId/isStack。上游语义对齐后，该调用实际新建副图：标准参数 10 对应 10 个额外 MA pane，加原主图 MA，共 11 个 MA 实例、13 个 pane，而非文档所写的“三 pane、10 MA”。

现将原主图 MA 计入总数，其余显式指定 main + isStack=true；断言 MA 总数及不存在额外 pane。标准场景为 50k candle、10 个 MA 总实例、100 segment、固定三 pane。GUI 字体场景使用静态 1000 candle、1 个主图 MA、0 overlay。

**夹具变化形成新的测量起点。** 本轮分位数不能与旧夹具直接计算“优化百分比”或“无回退”；原 0.15.0 冻结代码和历史数据保留。

### 3.2 分位数、janky 与输入延迟

解析条件已由错误的行尾匹配改为完整 percentile 行匹配；普通 janky、legacy janky 和 GPU 分位数分别处理。测试先验证所需字段完整及帧数非零，保存包含 framestats 的完整原始输出。

JUnit 通过仅代表**计量可用**，不代表超时帧/输入延迟门槛通过。帧耗时 P95 不是输入到视觉反馈 P95。当前 R8 release 的 HWUI 已可采，本轮旧“R8 始终零帧”的描述不再适用。

### 3.3 启动必须区分首次显示与完整图表

示例通过 ReportDrawnWhen 等待数据、MA/VOL/MACD 以及压力覆盖物就绪后报告完整绘制。冷启动同时保留 TTID、TTFD。

K60U 的 warm StartupTimingMetric 曾产生 5 份 trace、只有首轮有效值；显式重建后的试验又出现无指标。失败记录保留，普通 warm 方法显式登记 Ignore，不能以一轮数字认证五轮 P95。

本轮增加**等价热进程计量**：同一 PID 内重建 Activity，测 onCreate 到 ReportDrawnWhen 触发的 reportFullyDrawn，取得 5 个有效样本并逐项断言 ≤500ms。其起止点与 StartupTimingMetric 不同，独立列示。依据：[ReportDrawnWhen](https://developer.android.com/reference/kotlin/androidx/activity/compose/package-summary)、[Macrobenchmark 指标定义](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)。

### 3.4 截图名称和尺寸也不能代替实际方向检查

过程复核发现，一部分“portrait”文件实际为横屏，另有旋转动画中的中间帧。工具已改为显式固定旋转、等待转换稳定、通过系统 screencap 采集，并断言实际图像长宽。最终选定的 6 张配置图另外逐张检查内容方向。

性能用例另行固定 1220×2712 竖屏和字体 1.0，避免手机物理摆放方向污染计量。测试完成后恢复原字号及旋转设置。

## 4. 最终选定结果

硬件：K60U / 23078PND5G / corot，Android 14，密度 480dpi。发布样例使用 R8、非 debuggable、本地 debug 签名；性能表中的 R8 与非压缩 benchmark 分列。未绕过 CPU 频率或 heap 权限限制。

| 验证 | 结果 | 判断 |
|---|---|---|
| 正式 JVM 测试 | core 222 + compose 191 = **413**，失败/错误/跳过均 0 | 通过。 |
| 第十一轮独立回归 | **72** 项，失败/错误/跳过均 0；保留 60 组/240 段批次顺序组合 | 原源码对齐回归维持。 |
| 台账七面校验 | 319 行校验通过 | 证明覆盖/引用合法，不替代行为证明。 |
| Android 原生集成 | **3** 项通过：30 建销/作用域、实际字形布局、1080×1600 PNG 编解码/内容/状态保持 | 通过相应范围。 |
| R8 交互与配置 | **1** 项复合用例通过：双指放大缩小、长按、两次无障碍切换、字体 1.0/1.3/2.0 × 横竖屏 | 交互通过；视觉仍有下述最高价标记遗留项。 |
| R8 冷启动 | 5 次 TTFD：494.5、459.0、412.5、441.8、451.7ms；median **451.7ms**，max **494.5ms** | 本机样本均低于 1500ms。TTID median=342.6ms，单独保留。 |
| R8 等价热进程完整绘制 | 同 PID 5 次：116.1、106.1、98.4、95.0、72.4ms | 本次等价口径均低于 500ms；严格 warm Macrobenchmark 仍受限。 |
| R8 B-02，竖屏三 pane | **546** 帧；普通 janky **424 / 77.66%**，legacy **546 / 100%**；P50/P90/P95/P99=**22/26/28/34ms**；HWUI deadline missed=424 | 计量可用，但超时信号明显，不能认定流畅门槛通过。 |
| 非压缩 B-02 | 独立保存最终非压缩采样及原始文件 | 与 R8 分列；非同变体/同几何样本不用于回归百分比。 |
| R8 B-03，100k/10 MA/100 segment | 5 次注入就绪 **1898、1737、1704、1807、1739ms**；首批 1000 根可交互 **32–36ms** | 本次应用计时均 ≤2000ms，最小余量 102ms；不是完整绘制 TTFD。 |
| B-04 突发与数据会话 | 既有混合操作/会话回归随正式与独立集执行通过 | JVM 范围，非真机全链路吞吐认证。 |
| 本地发布与独立 consumer | 干净、离线重新编译；实际解析 core JAR/compose AAR 哈希与候选仓库一致；Maven local 同步核对 | 通过。 |
| B-06 30 分钟 | **1800.84 秒、31 点同 PID**；PSS 107.9–148.7MiB；0 FATAL、应用 ANR/crash 事件 0；温度 38.8→37.5°C | 崩溃/ANR 观测通过；内存趋势待查，不能将 B-06 全项关闭。 |

B-06 前 5 点（分钟 0–4）PSS 均值 122.8MiB，末 5 点（26–30）141.7MiB；排除冷态起点后，前 15 点（1–15）与后 15 点（16–30）均值仍为 128.2→135.3MiB。期间多次回落，不能据单峰或 PSS 差值直接认定泄漏；但末段均值升高也不足以认证内存稳定。下一步需区分缓存暖机、原生分配驻留与保留对象。PSS 与 B-05 retained heap 是不同量，不直接混用 5MiB 门槛。

证据：[长测审查汇总](../evidence/release-candidate-round3-20261101/soak-reviewed-summary.json)、[逐分钟采样](../evidence/release-candidate-round3-20261101/soak-samples.jsonl)、[连续应用日志](../evidence/release-candidate-round3-20261101/soak-app-live.log)、[应用 ANR/crash 事件流](../evidence/release-candidate-round3-20261101/soak-anr-crash-live.log)。

冷启动框架提示无法清除 Runtime Image；后续轮次可能因运行时类加载缓存更快。上表为当前设备/编译模式的 5 次观察值，不扩展为生产首次安装的统计认证。

最终结果入口：

- [正式测试及本地发布日志](../evidence/release-candidate-round3-20261101/final-formal-tests-and-maven-local.log)、[正式计数](../evidence/release-candidate-round3-20261101/formal-test-counts.json)、[正式 XML 归档](../evidence/release-candidate-round3-20261101/formal-tests.zip)。
- [独立回归](../evidence/release-candidate-round3-20261101/accepted-final-external.log)、[Android 三项集成](../evidence/release-candidate-round3-20261101/abi-preserved-android-regressions.txt)、[稳定方向交互复验](../evidence/release-candidate-round3-20261101/accepted-final-ui-stable.txt)。
- [独立探针与输入归档](../evidence/release-candidate-round3-20261101/external-regression-replay.zip)：恢复到原 /tmp/lkanvas-audit-round11-20261031 路径即可继续使用原 init 和 72 项探针。
- [竖屏 R8 计量四项](../evidence/release-candidate-round3-20261101/final-portrait-release-metrics.txt)、[冷启动 JSON](../evidence/release-candidate-round3-20261101/final-cold-startup.json)、[等价热进程样本](../evidence/release-candidate-round3-20261101/final-portrait-evidence/warm-readiness.json)、[完整 B-02 原始计量](../evidence/release-candidate-round3-20261101/final-portrait-evidence/b02-1793506266974.txt)。
- [100k 五次就绪数据](../evidence/release-candidate-round3-20261101/capacity-ready.json)、[consumer 制品核验](../evidence/release-candidate-round3-20261101/final-consumer-verified.log)、[本地仓库一致性](../evidence/release-candidate-round3-20261101/maven-local-agreement.json)。

## 5. 尚未关闭的发布项

| 优先级/项目 | 当前证据 | 后续验收条件 |
|---|---|---|
| P1：B-02 流畅性 | HWUI 普通 janky/deadline missed=77.66%，不能只凭 P95=28ms 判定输入延迟或 5% 门槛通过。FrameTiming 复探仍报 no renderthread slices。 | 在明确的设备、刷新率和负载下，取得可解释的 deadline/CPU 帧及输入到反馈证据，并达到冻结门槛。 |
| P2：最高价标记与图例相交 | 最高价文字的 y 仅钳到 0，没有考虑图例实际占用区。普通及放大字体截图均可见局部遮挡。此项仍未修复，配置用例通过不等于视觉全项通过。 | 沿用上游标记定位约束补充避让，保留标记语义，并补真实字框/截图回归。 |
| P2：B-06 内存趋势 | 30 分钟末段 PSS 均值高于前段，尚无 GC 后/保留对象归因。没有观察到进程崩溃，但内存项不能据此关闭。 | 解释缓存/分配与保留对象趋势，并在同一协议下复验。 |
| B-05：retained heap | 全局注册表泄漏已修复，30 次页面建销/协程取消通过；未提供 retained heap ≤5MiB 的真机支配树证据。 | 按真实 heap 方法复验，区分缓存/分配波动与保留对象。 |
| R-13：真人 TalkBack | 六个自定义动作可被系统读取，连续上一根动作执行正确；未验证真人实际朗读、遍历顺序与音频体验。 | 真机 TalkBack 人工验收并保存结果。 |
| warm Macrobenchmark | 原计量缺失记录保留；本轮用独立的同进程完整绘制计时补充证据。 | 合规设备取得完整有效样本，或正式接受已说明起止点的等价口径。 |

最高价标记定位：[PaneRenderers.kt:540](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:540)；实图：[双倍字体竖屏](../evidence/release-candidate-round3-20261101/accepted-ui-stable/font-2.0-portrait.png)、[普通字体竖屏](../evidence/release-candidate-round3-20261101/accepted-ui-stable/font-1.0-portrait.png)。

FrameTiming 受限证据：[本轮实际复探](../evidence/release-candidate-round3-20261101/frame-timing-reprobe.txt)。Warm 受限证据：[五份 trace 仅首份有效的过程结果](../evidence/release-candidate-round3-20261101/final-r8-startup-and-b02.txt)、[显式重建后无指标](../evidence/release-candidate-round3-20261101/warm-main-intent-device.txt)。这些失败未被重命名为通过，也未计入最终通过集。

库 Lint 无问题；示例仍有 MissingApplicationIcon 警告。未运行 Android 6/其他厂商设备，未穷举全部指标、画线工具及异常交互，未执行远端 Maven/应用商店发布或生产签名验收。

## 6. 结构与目标收敛判断

本轮没有替换 Store、指标算法、覆盖物模型或 figure 批次管线。core 的实现变动仅为候选版本号；Compose 的改动集中于已有 Android 适配点、缓存键、像素布局和生命周期，CAS 更新保留原最大拒绝水位语义。72 项独立回归继续确认原有限窗口、顺序批次、会话与复用契约。

因此，**原源码结构/逻辑对齐成果保持，设备验证的真实性和覆盖面在收敛**。与此同时，本轮纠正了旧夹具和旧验收表述，不能再用此前“全绿/无回退”作为当前放行依据。

下一轮应围绕 B-02 性能、最高价标记避让、B-06 内存趋势、真实 heap 和 TalkBack 五组明确余项推进，保留本轮测试与原始数据作回归入口。至少仍需一次有针对性的修复复验；现有证据不足以保证性能优化只需一轮，不能用测试数量推导完成百分比。

## 7. 复现入口

在仓库根目录执行：

    ./gradlew :lkanvas-core:test :lkanvas-compose:testDebugUnitTest
    ./gradlew :app-sample:connectedDebugAndroidTest
    ./gradlew :app-sample:assembleRelease :benchmark:assembleBenchmark
    ./gradlew :app-sample:lintRelease :lkanvas-compose:lintRelease
    python3 -B tools/alignment/check.py

真机发行包需明确安装 release APK 后，以自仪器化 benchmark APK 运行选定类；使用 connectedBenchmarkAndroidTest 会安装非压缩目标，不能混称 R8：

    adb -s 6D8H6TXSDYXC85PZ install -r app-sample/build/outputs/apk/release/app-sample-release.apk
    adb -s 6D8H6TXSDYXC85PZ install -r benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk
    adb -s 6D8H6TXSDYXC85PZ shell am instrument -w -r -e class io.lkanvas.benchmark.StartupBenchmark#coldStartup,io.lkanvas.benchmark.WarmReadinessTest,io.lkanvas.benchmark.B02GfxInfoBenchmark,io.lkanvas.benchmark.GfxInfoParserTest io.lkanvas.benchmark/androidx.test.runner.AndroidJUnitRunner

长测及容量脚本：[thermal-soak.py](../evidence/release-candidate-round3-20261101/thermal-soak.py)、[capacity-ready.py](../evidence/release-candidate-round3-20261101/capacity-ready.py)。过程日志包含用于定位问题的失败试验；最终采信入口以上述索引为准。
