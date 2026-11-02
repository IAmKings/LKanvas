# 设备/发布第五轮：R8 百次切品种与第二机型复验

采集：2026-11-01 至 2026-11-02；收尾定稿：**2026-11-02**。候选：**0.15.1-rc3**。发布判定：**HOLD，最终验收未通过**。

本轮承接[第四轮报告](targeted-fix-retest-round4-2026-11-01.zh-CN.md)。冻结源码基线仍为 **0.15.0 / 78a281b7f9c470bf7b98908443a7f29fa659a56a**，上游目标仍为仓库中的 KLineChart v10.1.0。新增设备为 **OnePlus PJZ110，Android 17 / API 37，1440×3168、640dpi**。收尾时 K60U 重新连接，另补做同一 rc3 R8 包的 30 次建销与 100 次切品种。K60U 的帧、启动、输入和长测未在本轮复采，第四轮相应结果仅保留为历史证据。

## 1. 修复与结构/逻辑一致性

- **API 23 兼容性**：新示例界面两处 `Iterable.forEach` 被 Lint 识别为 API 24 调用，改为同序 `for` 循环；未提高 minSdk，也未增加生产依赖。此结论是静态兼容检查，未认证 Android 6 真机。
- **真实切品种入口**：示例增加 DEMO / DEMO-B / DEMO-C 循环切换按钮，市场标题读取实际 MarketKey。沿用公开 `setSymbol → Store.resetData → DataLoader` 路径，未替换成仅修改标题或直接重置测试数据。
- **像素文字布局复用**：测量与绘制共享普通标签的布局结果，避免两套缓存项。原生单槽缓存回归在修复前失败、修复后通过。放不下的长文本回到原绘制路径；短文本、长文本及换行文本在字体 1.0/2.0 下逐像素对照原绘制器。未据微观缓存命中宣称整帧性能显著改善。
- **版本标识一致**：修正 Compose 门面仍返回 rc1、core/制品已为 rc2 的不一致，候选统一为 rc3，增加门面与 core 版本相等的回归断言。
- **测试兼容性**：横竖屏校验改用 UiAutomation 直接截图，保留实际尺寸和连续竖屏检查，避免新系统无法读取 shell 所有截图文件的问题。warm 准备阶段增加 500ms Home 稳定等待，仍保留每个预热/测量对同 PID、五个独立原生指标要求；未修改计时区间和阈值。

本轮库与示例生产增量为 7 个文件（不含测试与验证配置），其中 core 仅版本常量变化，库的行为增量集中在 `ChartText`。Store、指标计算、市场会话协议、覆盖物模型与 figure 批次顺序均未重写。示例界面的既有重设计保留。ChartText 原构造与公开签名无移除；此范围不是全库 ABI 或全部插件认证。

证据：[本轮独立生产补丁](../evidence/targeted-retest-round5-20261101/own-production.patch)、[补丁前后哈希核对](../evidence/targeted-retest-round5-20261101/own-production-delta.json)、[缓存修复前失败](../evidence/targeted-retest-round5-20261101/pixel-layout-cache-red.txt)。

## 2. 发布变体百次切品种

同签名 instrumentation 在最终 **非 debuggable、R8 + 资源压缩、debug 本地签名** APK 内执行 30 次 Activity 建销，并在最后一个 Activity 中通过真实按钮完成 100 次切品种。每次均断言同一 facade、实际 MarketKey、递增 session、至少 500 根重载数据及新的快照；不按点击次数直接判定成功。

销毁后检查 Activity/facade/ChartState、旧快照与旧数据列表共 290 个弱引用，并对自身真实 heap 按本 APK 的 R8 mapping 进行 Shark 分析。没有增加 keep 规则或修改生产 debuggable/profileable 标志；测试所需 stdlib/tracing 仍只放入测试 APK。

**最终 R8 百次切品种通过**：100/100 次市场与新会话核对；同一 facade；30 次建销；290/290 个弱引用释放；registryAfter=0。按实际 mapping 的目标 ChartState/LKanvasChart/MainActivity 实例均为 0，目标 retained=0 bytes。该数值仅指所列销毁目标，不是整个进程没有内存占用。

[逐次市场/会话记录](../evidence/targeted-retest-round5-20261101/r8-final/release-market-switches.json)、[原生四个发布用例](../evidence/targeted-retest-round5-20261101/final-r8-device.txt)、[Shark 最终分析](../evidence/targeted-retest-round5-20261101/r8-final/heap-analysis.txt)、[实际 R8 映射](../evidence/targeted-retest-round5-20261101/r8-probe.json)。

## 3. 最终性能与启动结果

| 指标 | 新机最终值 | 判定 |
|---|---|---|
| 官方 FrameTiming 第一次 | 496 个匹配样本；CPU P95 **12.22ms**；overrun P95 -0.28ms；正 overrun 21/496 = 4.23% | CPU >10ms，**失败**；≤5% overrun 子项通过。 |
| 官方 FrameTiming 独立复采 | 497 个匹配样本；CPU P95 **12.24ms**；overrun P95 -0.25ms；正 overrun 19/497 = 3.82% | CPU **仍失败**；overrun 子项通过。 |
| 冷启动 TTFD | 221.52、215.96、231.73、228.26、220.59ms | 完整 5/5，均 ≤1500ms；TTID 单独保存。 |
| 严格 warm TTFD | 71.10、75.14、69.95、68.12、97.05ms | **完整 5/5**，均 ≤500ms。每个预热/测量对保持同 PID；不是等价 ready 计时。 |
| 独立 HWUI 两次 | 558/556 帧；普通 janky 51/60；P95 15/16ms | 独立计数，与上面的匹配样本分母及输入延迟分开。 |
| 100k/10 MA/100 overlay 注入 | 1471、1440、1474、1468、1476ms；首批 1000 根 25–27ms | 五次均 ≤2000ms；注入就绪不是完整绘制 TTFD。 |

| 原独立驱动的输入计量 | 完整关联 | P95 / max | 判定 |
|---|---:|---:|---|
| 静态轻载 1000 / 1 MA / 0 overlay | 100/100 | **52.23 / 52.79ms** | **失败**，P95 >50ms。 |
| 压力 50k / 10 MA / 100 overlay | 100/100 | **51.89 / 52.82ms** | ≤100ms 子项通过。 |

为保持第四轮口径，最终采信恢复后的独立 benchmark 驱动，分别 force-stop 目标应用后执行两组测试。冻结期间曾在目标进程内驱动，辅助观察为 52.18/49.71ms；这些数据仍保留，不与独立驱动混算。两份临时复制用例移入证据目录，源码中复用原 `FinalAcceptanceUiTest` / `InputLatencyBenchmark`，避免维护两套实现。

[原驱动交互及两组输入记录](../evidence/targeted-retest-round5-20261101/standalone-device.txt)、[轻载逐样本关联](../evidence/targeted-retest-round5-20261101/standalone/validation/input-normal/joined-latency.json)、[压力逐样本关联](../evidence/targeted-retest-round5-20261101/standalone/validation/input-stress/joined-latency.json)。

[官方完整 JSON](../evidence/targeted-retest-round5-20261101/official-benchmarkData.json)、[官方独立复采](../evidence/targeted-retest-round5-20261101/official-frame-repeat.json)、[五份 warm 完整性复核](../evidence/targeted-retest-round5-20261101/official-reviewed-summary.json)、[原生七个采集用例](../evidence/targeted-retest-round5-20261101/official-background-allowed-device.txt)、[官方原始 traces 归档](../evidence/targeted-retest-round5-20261101/official-raw-traces.zip)、[容量五次](../evidence/targeted-retest-round5-20261101/capacity-ready.json)。

Macrobenchmark JSON 报告 cpuLocked=true；没有独立证明普通 HWUI 与长测使用相同的锁频条件。刷新率、热状态和设备差异保持各自证据，不将 K60U 与 PJZ110 的差值解读为算法优化百分比。

输入定义仍为合成 MOVE 事件时间到实际呈现，逐项关联 InputDispatcher ID、目标进程接收、HWUI 帧 ID 与 SurfaceFlinger 实际呈现，保留 100/100 完整性要求及 inclusive P95。压力夹具为 50k candle / **10 个 MA 总实例** / 100 segment，另含 VOL、MACD，保持三 pane；轻载夹具为静态 1000 candle / 1 MA / 0 overlay，不代表全部 500 根实时推送场景。

最初 benchmark 进程被内核冻结，cgroup.freeze=1，而常规 ActivityManager 冻结标志为 false；中止记录保留。用户确认排除测试进程后，新启动进程仍被冻结；仅将 **io.lkanvas.benchmark** 的系统后台策略临时设为“完全允许”后，官方指标才完整恢复。完成后恢复原“智能优化”策略，用户设置的冻结排除保留，生产示例策略未改。未做因素消融，不将恢复唯一归因于某一个设置。诊断曾使用 127.0.0.1 而被明文策略拒绝；AndroidX 实际使用 localhost，实测可达，额外网络配置已撤回，没有将错误假设作为根因。

主机驱动的额外帧 trace 仅作诊断：20 次 125ms shell swipe，与正式 UiDevice 25 步协议分开。AndroidX 查询公式的主机转写也分开标识，不冒充官方 Macrobenchmark。中间候选的缓存改动前后诊断 CPU P95 约 19.66/19.70ms，样本池不同，不能据此宣称改善或回退。[计量公式来源](https://android.googlesource.com/platform/frameworks/support/+/dd97834aa54671ee1f56d65fa46668b4ffeb57e8/benchmark/benchmark-macro/src/main/java/androidx/benchmark/macro/perfetto/FrameTimingQuery.kt)。

## 4. 稳定性与资源释放

最终包完成 **1800.95s、31 点同 PID**，0 FATAL、0 应用 ANR/crash。PSS 范围 **79.95–126.48MiB**，电池温度 34.8→33.0°C。

| 分项 | 暖机后分钟 1–15 均值 | 分钟 16–30 均值 |
|---|---:|---:|
| 总 PSS | 101.42MiB | 104.27MiB |
| Dalvik 已分配 | 16.03MiB | 16.09MiB |
| Native 已分配 | 17.73MiB | 18.40MiB |
| GL mtrack | 0.38MiB | 0.38MiB |
| EGL mtrack | 35.77MiB | 35.77MiB |

本机既定 B-06 协议（无崩溃/ANR/无界增长）通过。PSS 均值仍有约 2.85MiB 的差值，Native 已分配约增加 0.67MiB，不写成绝对不增长；本轮只证明该 30 分钟窗口，长期平台期仍需更长观察。

同 PID 后台/trim 后 PSS **79.77→42.05MiB**；Graphics 汇总 39,364→1,024KiB，EGL mtrack 行由 36,632KiB 变为不再列出，支持后台图形资源可回收。PSS、Native allocation 与 B-05 的目标 retained 分开判断，未将它们混用为 ≤5MiB。

[31 点原始记录](../evidence/targeted-retest-round5-20261101/soak-samples.jsonl)、[分项复核](../evidence/targeted-retest-round5-20261101/soak-reviewed-summary.json)、[连续应用日志](../evidence/targeted-retest-round5-20261101/soak-app-live.log)、[应用 ANR/crash 事件窗口](../evidence/targeted-retest-round5-20261101/soak-events-live.log)、[后台回收](../evidence/targeted-retest-round5-20261101/memory-reclaim-summary.json)。


第一段长测因补齐长文本回退而主动中止，仅归档 14 点，不计为最终 30 分钟通过。期间主机磁盘不足造成 R8 构建失败；清理可重建的本地 build cache 后，重新完成最终构建与检查。没有删除生产源码、依赖下载缓存、冻结制品或测试证据，也未把工具中止记作应用崩溃。

## 5. 回归与制品

- 正式 JVM：core 222 + compose 192 = **414**，失败/错误/跳过均 0；独立第十一轮 **72** 项在最终源码上通过。
- 原生 **12 个不同用例**通过：字形/标记/录制回放、单槽布局缓存、短/长/换行文字逐像素对照、示例周期/主题/画线/设置、30 次生命周期、PNG 及压力就绪。主机磁盘不足截断首批末尾输出，前 11 项有成功状态，压力就绪另行独立通过，未将截断记录计作完整 12 项 JUnit OK。
- 发布变体的 30 建销/100 市场、交互与六配置矩阵、两组 100 次输入已留存；其中输入是计量用例，JUnit OK 不等于数值达到放行门槛。
- 库/示例 release Lint 通过，示例单色图标警告保留。rc3 本地 Maven 与 Maven local、独立 consumer 干净离线编译及实际解析 SHA 核对通过。
- 原 0.15.0 的 **8 个冻结制品哈希不变**，Git 冻结提交未改，没有提交或远端发布。
- 最终安装 R8 APK SHA-256：`458176158d15549bc57b14f9980e10bab0e7ec9d5ae6786889ea7d555efc81ff`。

[414 项正式 XML](../evidence/targeted-retest-round5-20261101/formal-tests-final.zip)、[72 项独立 XML](../evidence/targeted-retest-round5-20261101/external-72-final.xml)、[原生前 11 项成功状态](../evidence/targeted-retest-round5-20261101/native-constrained-retest.txt)、[压力就绪独立复验](../evidence/targeted-retest-round5-20261101/native-readiness-final.txt)、[最终 R8/Lint 构建](../evidence/targeted-retest-round5-20261101/build-constrained-final.txt)、[独立 consumer](../evidence/targeted-retest-round5-20261101/consumer-constrained-final.txt)、[冻结制品未改](../evidence/targeted-retest-round5-20261101/baseline-artifacts-unchanged.json)、[候选制品哈希](../evidence/targeted-retest-round5-20261101/final-artifacts-sha256.json)。

## 6. 未关闭项与目标收敛

**目标在发布变体覆盖与计量完整性上收敛，性能放行仍未达标。** 两台真机的同一 R8 包均已完成真实百次切品种；PJZ110 的严格 warm 从缺计量补齐为 5/5，30 分钟稳定性与原独立驱动也有完整证据。在本轮改动及现有回归范围内，结构/逻辑一致性维持；这不取代前十一轮源码审计，也不认证上游最新 main。

| 未关闭项 | 目前差距 | 下一轮验收条件 |
|---|---|---|
| P1 压力 CPU 帧耗时 | 两次 P95 12.22/12.24ms，约高于目标 22% | 保持原负载与 figure 顺序，原生 CPU P95 ≤10ms，并保持 overrun ≤5%。 |
| P1 轻载输入 | 独立驱动 P95 52.23ms，高于 50ms | 同样 100/100 事件/帧/呈现关联，P95 ≤50ms。 |
| P2 大字体横屏图例 | 副图图例跨 pane | 在当前实际像素尺寸、2.0 字体与横屏下补齐 pane 内排版/可达性复验。 |
| 跨机型放行覆盖 | K60U 仅补测当前 R8 建销/切品种 | 性能修复后，两台机器在同一最终候选、同一协议下复验；不沿用 K60U 旧包性能结果。 |

不能根据关闭项数量或两台不同硬件的差值推算完成百分比，也不能承诺固定剩余轮数。下一轮优先收敛渲染/输入，再完成双机同版性能复验。七面台账仍为 319 行，其中行为通过 65 行；引用完整不等于全部行为认证通过。

新增机型在 **2.0 字体横屏** 下仍可见副图图例换行后侵入相邻 pane（[最终独立驱动截图](../evidence/targeted-retest-round5-20261101/standalone/validation/ui/font-2.0-landscape.png)）。自动交互、截图尺寸和非空布局检查通过，并不证明该画面的排版通过；此项保留为未关闭问题，未用隐藏图例、缩小字体或改指标数据使其通过。

新机的真人 TalkBack 听感与完整焦点遍历未重新验收；K60U 第四轮用户五项人工通过的证明范围保留。未覆盖 Android 6、全部厂商/刷新率组合及全部自定义插件；没有执行生产签名、远端 Maven 或应用商店发布。


### K60U 收尾补测（2026-11-02）

同一 APK（SHA-256 与 PJZ110 完全一致）在 K60U 上完成 **30 建销 + 100/100 实际市场会话切换**；290 个弱引用全部释放，注册表回到 0，按同一 R8 mapping 分析，目标实例与 retained 均为 0。该补测只认证建销/切品种与释放，不扩展为 K60U 的本轮帧、启动、输入或长测通过。

[35 秒原生用例](../evidence/targeted-retest-round5-20261101/k60u-final/device-test.txt)、[100 次市场/会话记录](../evidence/targeted-retest-round5-20261101/k60u-final/release-market-switches.json)、[K60U Shark 分析](../evidence/targeted-retest-round5-20261101/k60u-final/heap-analysis.txt)、[同包及设置核对](../evidence/targeted-retest-round5-20261101/k60u-final/installed-and-restored.json)。

## 7. 收尾与证据索引

两台设备的字体、旋转、无障碍设置均与各自测试前一致，并返回普通示例页面。测试 APK 临时后台策略已恢复，用户指定的冻结排除保留。当前报告已定稿，没有提交或远端发布。

[两台设备与验收索引](../evidence/targeted-retest-round5-20261101/acceptance-index.json)、[PJZ110 设置核对](../evidence/targeted-retest-round5-20261101/installed-and-restored.json)、[本轮 ChartText ABI](../evidence/targeted-retest-round5-20261101/chart-text-abi.json)、[319 行台账校验](../evidence/targeted-retest-round5-20261101/alignment-check-final.txt)。

复现时先安装 release APK，再单独安装 benchmark APK；不要以 connectedBenchmarkAndroidTest 自动安装的非压缩目标代替 R8。PJZ110 的 benchmark 运行需临时允许测试 APK 后台执行，结束后恢复。现成固定设备脚本与原始 JSON/trace 见证据索引；脚本中的设备编号、几何尺寸与安装包哈希均属于本轮条件。
