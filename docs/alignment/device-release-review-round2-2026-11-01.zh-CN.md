# 0.15.0 设备/发布验证结果审查与第二轮验证

- 日期：2026-11-01，Asia/Shanghai。
- 冻结源码：`78a281b7f9c470bf7b98908443a7f29fa659a56a`（0.15.0）；本轮工作区 HEAD 为 `edc2fede9efcd9c92e1bfd589bb09aa015dc1acb`。
- `lkanvas-core`、`lkanvas-compose`、`app-sample` 的 Git tree 与冻结提交完全相同，见[冻结证明](../evidence/0.15.0-validation-round2-20261101/frozen-source-proof.json)。此前变更主要为记录和 benchmark 日志补充；本轮未修改实现或正式测试。
- 设备：K60U / 23078PND5G / corot，serial `6D8H6TXSDYXC85PZ`，Android 14，SDK 34，1220×2712、480dpi；USB连接、屏幕解锁，初始font_scale=1.0。
- 范围：审查 performance-baseline、demo-checklist、compatibility、历史原始结果及关键截图；据缺口执行启动、B-02完整采集、release字体可读性、本地制品/consumer下一轮验证。本轮没有重新逐项走完全部功能或长测。

## 1. 结论

**本地构建/制品/consumer路径通过；设备与性能发布验收尚未整体通过。源码审计冻结结论保持，不应将验证记录的问题直接当成库源码回归。**

上一轮的启动原始数值可追溯，但“完整图表就绪达标”“B-02无回归、豁免序列闭环”和字体2.0的总体通过表述超出了证据。本轮确认分位数解析错误，并拿到两次完整gfxinfo；同时在R8 release上复现字体放大可读性问题。

| 验证项 | 当前判定 |
|---|---|
| 冻结源码一致性 | 通过，三个生产模块tree保持 |
| 冷启动TTID样本 | 通过本轮样本检查；5次均小于1500ms，不代表完整图表P95已证明 |
| B-02帧统计可采集 | 通过，完整P50/P95已取得 |
| B-02严格性能门槛/输入延迟/相对历史无回归 | 待判定，不能由现有测试PASS或帧分位数替代 |
| R8 release构建、安装、启动 | 本轮冒烟通过，进程存活并留图；非全部功能认证 |
| 本地Maven发布、独立consumer重编 | 通过；不是远程仓库或商店发布 |
| 字体2.0可读性 | 不通过，release复现图例重叠和价格标签裁切 |
| 双指、TalkBack人工遍历、B-05 retained heap、B-06长测 | 本轮未重做，仍需明确保留证据边界 |

## 2. 对上一轮记录的审查

### V01：分位数缺失首先是解析条件错误，不能直接归因输出截断

`B02GfxInfoBenchmark.parseGfxInfo` 使用 `t.endsWith("th percentile:")`，但完整输出形如 `50th percentile: 19ms`。条件为false，后续分位数解析不会执行。追加逐行PCTL日志也无法恢复没有进入metrics的字段。

本轮通过不改测试的外部解析脚本完整读取两份原始dump，取得P50/P95。脚本有断言自检，并将普通janky、legacy janky与GPU分位数分开。见[外部解析脚本](../evidence/0.15.0-validation-round2-20261101/parse-gfxinfo.py)。正式测试暂未修改，工具修正应作为下一次允许的验证资产变更处理。

### V02：B-02“无回归/闭环”证据不足，普通与legacy字段混称

旧记录仅凭帧数和约78–79%的janky推导与旧P50状态一致。帧数及janky比例不能反推出缺失的P50/P95，也不能证明相对基线无回归。本轮完整dump分别列出普通janky约80%、legacy janky为100%，因此将普通janky称为legacy不准确。

测试当前只断言totalFrames>0，没有对P95或janky门槛作性能断言；JUnit零失败只证明脚本运行和计量可用。FrameTimingMetric仍因`@Ignore`跳过，其设备兼容性本轮未重新诊断。gfxinfo分位数不是输入到视觉反馈延迟，PRD两者定义也分别列明；参见[PRD](../PRD-KLineChart-Kotlin-Compose.zh-CN.md:440)与[Android指标说明](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)。

因此撤回“B-02无回归闭环”，保留实采数据和未解决的门槛可比性。不能擅自用新的等价阈值替代原5%要求。

### V03：TTID与完整图表就绪不可混称

旧JSON中的5次TTID确实为414.3/400.1/430.7/416.1/419.2ms，median=416.1ms；但测试只采StartupTimingMetric的timeToInitialDisplayMs，没有图表就绪计量或reportFullyDrawn证据。TTID是首帧，TTFD才描述完整可交互状态，见[Android启动定义](https://developer.android.com/topic/performance/issues/launch-time)。

本轮继续记录TTID样本，不将median当成P95，也不把5次首帧数值标为“完整图表P95达标”。

### V04：字体2.0旧截图已经显示重叠，本轮release确认

旧27-font-scale-2截图显示VOL图例多行互相覆盖，右侧价格文字被裁切；仅确认字体放大不足以判定可读性通过。本轮未修改源码，用R8 release重做font_scale 1.0→2.0→1.0，现象再次出现。常规字体下VOL图例也与绘图区密集叠加，字体2.0明显加剧。

证据：[正常字体](../evidence/0.15.0-validation-round2-20261101/release-normal.png)、[字体2.0](../evidence/0.15.0-validation-round2-20261101/release-font2.png)、[恢复后](../evidence/0.15.0-validation-round2-20261101/release-restored.png)。这是一项已复现的设备可读性失败；库绘制、样例pane/gutter配置各自责任尚需拆分，不能直接定性为新库算法缺陷或纯demo问题。

双指缩放沿用旧人工结论、TalkBack仅有语义树，都应保留为本轮未重新人工通过。旧截图目录实际有31个PNG文件，本轮抽查关键图并复验上述问题，不声称逐一重做全部操作。

## 3. 本轮实际执行与结果

### 启动与设备测试

运行 `:benchmark:connectedBenchmarkAndroidTest`：B02GfxInfoBenchmark和StartupBenchmark执行成功，FrameTimingMetric测试维持跳过。原始结果已归档，不能将测试成功解释成性能门槛全部通过。

本轮5次TTID为 **486.8/431.1/426.0/419.8/448.2ms**，median **431.1ms**，max **486.8ms**。median相对前轮416.1约+3.6%，对0.6.0的412.8约+4.4%。这些是同指标描述性比较，未出现该样本集的1500ms超限，不外推稳定尾延迟或完整图表就绪。

当前Macrobenchmark JSON记录cpuLocked=true、compilationMode=verify；该上下文属于StartupBenchmark，不能自动证明独立普通JUnit B-02的锁频条件。

### 两次B-02定向复跑

安装同一非压缩、非debuggable benchmark APK及测试APK，直接以AndroidJUnitRunner运行现有B02GfxInfoBenchmark两次；每次保留50k/10指标/100覆盖物、等待22秒、10去10回滑动、每次25步协议。结束后立即由主机adb保存完整gfxinfo，避免UiDevice返回/解析问题以及随后的冷启动覆盖统计。

| 指标 | 第一次 | 第二次 |
|---|---:|---:|
| 完整dump帧数 | 559 | 559 |
| 普通janky | 447（79.96%） | 448（80.14%） |
| legacy janky | 559（100%） | 559（100%） |
| gfxinfo P50/P90 | 19/23ms | 19/23ms |
| gfxinfo P95/P99 | 25/30ms | 26/32ms |

这两份dump包含测试返回后的少量尾帧：第1次测试内摘要552帧，完整dump559帧。表中分位数、分子及分母全部来自同一完整dump，未与测试内摘要混算。

P95数值低于历史gfxinfo的32ms，但本轮不能独立读取CPU min/max频率（权限被拒），也未证明温度/调度等环境与历史完全一致，因此只记录本轮观测，不签署严格“无回归”结论。普通janky约80%也不能被忽略；5%截止比例和输入P95仍需可比计量。设备电池温度和CPU可读性记录已保存。

### 发布路径与设备状态

- `:app-sample:assembleRelease` 成功，release配置为minify/shrink启用；APK安装、启动、字体切换后仍存活并有截图。sample manifest的versionName仍为0.1.0，库制品坐标为0.15.0，冻结身份以Git tree与制品摘要确认。
- core/compose `publishAllPublicationsToLocalRepoRepository` 成功，产物位于本地build/maven-repo；未发送至外部仓库。
- 独立consumer仅依赖 `io.lkanvas:lkanvas-compose:0.15.0`，执行clean、离线assembleRelease、rerun-tasks成功，29项任务实际执行。这是公开API编译消费验证，不是独立消费App运行或远程下载验证。
- 已记录APK/JAR/AAR/POM/module的SHA-256。测试runner已清理，样例保留release；字体已恢复原1.0，旋转/刷新率设置未修改，没有解锁或绕过安全机制。

## 4. 可复现证据

证据根目录：[第二轮证据](../evidence/0.15.0-validation-round2-20261101)。

- [设备/构建/本地发布日志](../evidence/0.15.0-validation-round2-20261101/gradle-device-publish.log)、[consumer全量重编](../evidence/0.15.0-validation-round2-20261101/consumer-rebuild.log)。
- [B-02第一次完整dump](../evidence/0.15.0-validation-round2-20261101/b02-run1-gfxinfo.txt)、[第二次](../evidence/0.15.0-validation-round2-20261101/b02-run2-gfxinfo.txt)、[解析结果](../evidence/0.15.0-validation-round2-20261101/b02-summary.json)。
- [制品摘要](../evidence/0.15.0-validation-round2-20261101/artifacts-sha256.json)、[字体恢复](../evidence/0.15.0-validation-round2-20261101/font-restored.txt)、[CPU可读性](../evidence/0.15.0-validation-round2-20261101/cpu-readability-after.txt)。

prior目录保存运行前原始JSON/XML及必要日志，current目录保存本轮Gradle设备结果。源benchmark测试仍有解析错误，本轮结果由外部有自检的脚本从完整dump计算，不修改冻结资产来制造通过。

## 5. 下一阶段收口条件

本次已完成“先审查、再按缺口执行一轮验证”，无需把已通过的本地构建/consumer一遍遍重复。尚未完成的设备/发布门槛为：

1. 将benchmark分位数解析作为独立验证资产修正，并为真实输出、普通/legacy/GPU字段加回归；库源码继续冻结。性能断言需明确区分“可采集”和“满足阈值”。
2. 保留B-02为待判定：明确可重复的频率/温度/刷新率条件，采真正输入→视觉反馈及可比截止比例；MIUI仍不可采时，走显式兼容设备或经确认的等价方法，不能直接宣布5%通过。
3. 字体2.0布局问题拆分归属后再单独修复、在相同设备/字体/密度复验。若修改生产源码，应产生新的候选提交，不覆盖冻结0.15.0通过记录。
4. 双指、TalkBack人工遍历、完整图表就绪、retained heap和长测分别补证，不以语义树、TTID或短时进程存活替代。

**最终判断：维持0.15.0源码审计收口；构建与本地发布/消费链通过；设备可读性有已复现问题，性能与部分人工验收仍待补证，不批准“设备/发布全部通过”的结论。** 本轮未改生产代码或正式测试，也未执行远程发布、30分钟长测、双指或TalkBack人工操作。
