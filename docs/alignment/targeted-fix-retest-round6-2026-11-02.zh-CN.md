# 设备/发布第六轮：副图图例边界修复与 K60U 性能复采

日期：**2026-11-02**。候选：**0.15.1-rc4**。发布结论：**HOLD，最终验收仍未通过**。

**本轮已修复 2.0 字体短横屏下副图图例跨 pane 的渲染缺陷，并完成 K60U 当前候选的帧、冷暖启动、输入、HWUI 和容量采集。** K60U 图例和交互复验通过；帧耗时、overrun、轻载输入及部分容量样本未达标，严格暖启动指标仍不可用。

承接[第五轮报告](targeted-fix-retest-round5-2026-11-02.zh-CN.md)。**0.15.0 / 78a281b7f9c470bf7b98908443a7f29fa659a56a** 保持冻结，上游参照仍为仓库中的 **KLineChart v10.1.0**。本轮没有变更数据、指标、pane 分配或交互分发的结构逻辑。

## 1. 图例跨 pane：原因、修复与复验

### 根因与结构一致性

副图 tooltip 按原有 Flow 换行后，高度可能超过横屏短 pane。Compose 的多个 pane 共用 Canvas，副图 tooltip 绘制及 DrawOp 缓存回放均缺少所属 pane 的裁剪，因此 VOL 的第二行会进入 MACD。

上游 [DrawWidget.ts](../../third_party/klinechart-v10.1.0/src/widget/DrawWidget.ts:70) 按各 widget 的宽高更新独立 Canvas，画布边界自然限制 pane 内容。本次在现有 Compose 渲染路径补上等价边界：

- VOL/MACD tooltip 按对应 pane 与内容宽度裁剪；保留原图例取值、字号、换行游标、调用顺序与绘制层级。
- 现有 DrawOp 仅新增内部裁剪元数据，缓存重放恢复同样的边界；没有新增 sealed 子类，也没有改变公开构造签名。
- 图例按钮命中矩形取可见部分；完全被裁掉的按钮不保留相邻 pane 中的命中区域。

功能实现只涉及 [ChartRenderer.kt](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/ChartRenderer.kt:174) 和 [TooltipRenderer.kt](../../lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/TooltipRenderer.kt:41)。其余生产改动为 rc4 版本与发布坐标同步。与本轮进入状态的准确差异见[修复补丁](../evidence/targeted-retest-round6-20261102/pane-clipping.patch)、[生产文件变化清单](../evidence/targeted-retest-round6-20261102/own-production-delta.json)；5 个相关类型的公开 JVM 签名没有移除，见[API 核验](../evidence/targeted-retest-round6-20261102/tooltip-api-compatibility.json)。

### 复验结果

| 验证 | 结果与证据 |
|---|---|
| 修复前真实渲染回归 | 在 K60U 的原生 Canvas 上以 Density(4,2)、240/120/120px 三 pane 构造短横屏。扣除无图例对照帧的曲线像素后，仍检测到 VOL 图例像素 (268,409) 越过实际 VOL 下界 361px。见[修复前失败](../evidence/targeted-retest-round6-20261102/legend-red-refined-device.txt)。 |
| 修复后首帧与缓存 | 同一夹具通过：VOL 字形留在本 pane，首帧与缓存帧整图像素一致；隐藏的 VOL 按钮没有命中区域。原有真实字形、最高/最低价避让及标准/卡片图例回放等合计 **7 项通过**。见[字体原生结果](../evidence/targeted-retest-round6-20261102/legend-green-native-device.txt)。 |
| PNG 与示例路径 | PNG 导出、示例设置/主题/画线、压力数据就绪合计 **5 项通过**。见[原生结果](../evidence/targeted-retest-round6-20261102/ui-export-native-device.txt)。 |
| 最终 R8 实机 | K60U 真实字体 1.0/1.3/2.0 × 横竖屏六组截图、双指缩放、长按、连续无障碍“上一根”均通过。见[R8 交互记录](../evidence/targeted-retest-round6-20261102/k60u/ui-device.txt)及[2.0 字体横屏截图](../evidence/targeted-retest-round6-20261102/k60u/validation/ui/font-2.0-landscape.png)。 |

**验收边界：** 跨 pane 缺陷的代码修复和 K60U 复验已完成。高度不足时，超出部分遵守 pane 边界裁剪；不承诺任意小高度下仍完整展示所有图例行。PJZ110 当前未连接，Density(4,2) 原生夹具属于同类缺陷回归，不能替代该机最终 rc4 的实机复验。

## 2. 候选、设备与计量纪律

- 最终 APK SHA-256：`7daf9e69957c3f2fc89f45f4f53f18ce7cfff016d12911c494bb060f1bd0e94b`。R8 9.1.29，资源压缩，非 debuggable，本地 debug 签名；不是生产商店签名。本轮未变更 release 的 profileable 配置。
- 手动安装 release 与独立 benchmark APK；未用 connectedBenchmark 替换成未压缩目标。每项采集前都核对已安装 APK 哈希，见[逐项采集记录](../evidence/targeted-retest-round6-20261102/k60u/collection-status.json)。
- K60U / 23078PND5G / corot，Android 14 / API 34，1220×2712、480dpi，120Hz；性能计量字体 1.0、竖屏。设备接电、满电，AndroidX 环境报告 `cpuLocked=true`。环境快照随每项保存；本轮未修改 CPU、刷新率或后台策略。
- 帧/HWUI 压力负载：静态 50k candle、10 个 MA 实例（含默认 MA，另有 VOL/MACD）、100 segment、三 pane、20 次滑动。输入轻载为静态 1000/1 MA/0 overlay，压力输入为 50k/10 MA/100 overlay，各组独立 force-stop 后采集。
- CPU 帧耗时、正 overrun 比例、普通 HWUI janky、输入到实际呈现分别判定。门槛沿用冻结标准，不因结果调整。

[最终 APK](../evidence/targeted-retest-round6-20261102/final-rc4-release.apk)、[制品哈希](../evidence/targeted-retest-round6-20261102/final-artifacts-sha256.json)、[恢复与安装核验](../evidence/targeted-retest-round6-20261102/final-device.json)。

## 3. K60U 当前候选性能结果

| 项目 | 本轮结果 | 沿用门槛与判定 |
|---|---|---|
| 官方 FrameTiming 第一次 | 409 个匹配帧；CPU P95 **18.34ms**；overrun P95 11.64ms；正 overrun **409/409 = 100%** | CPU ≤10ms、正 overrun ≤5%，两项均 **失败**。 |
| 官方 FrameTiming 独立复采 | 404 个匹配帧；CPU P95 **18.66ms**；overrun P95 11.79ms；正 overrun **404/404 = 100%** | 两项仍 **失败**。分母为 AndroidX 匹配帧，不是全部硬件帧。 |
| 冷启动 TTFD，5 次 | **510.32、500.92、517.14、509.89、490.56ms** | 完整 5/5，样本 P95 **515.77ms ≤1500ms**，**通过**；TTID 单独保存。 |
| 严格暖启动 | 五个独立参数样本全部 `Unable to read any metrics during benchmark`；**0/5 有效** | 需要 5/5 有效 TTFD、P95 ≤500ms；**未通过，指标不可用**。 |
| 轻载输入到呈现 | **100/100** 完整关联；P95 **70.21ms**，max 78.43ms | P95 ≤50ms，**失败**。 |
| 压力输入到呈现 | **100/100** 完整关联；P95 **70.33ms**，max 82.95ms | P95 ≤100ms，此子项 **通过**。 |
| 独立 HWUI | 550 帧；janky 401（72.91%）；P95 **21ms** | 补充诊断，不替代官方 CPU/overrun 或输入门槛。 |
| 100k/10 MA/100 overlay 容量，5 次 | 注入就绪 **1896、1989、2147、2130、2045ms**；首批 1000 根 33–45ms | 样本 P95 **2143.6ms >2000ms**，容量 **未通过**；2 个样本在门槛内、3 个超出。注入就绪不是 TTFD。 |

初采帧 CPU P95 18.18/18.33ms 和初采冷启动 JSON 保留在[初采参考目录](../evidence/targeted-retest-round6-20261102/k60u/reference-without-raw)。采集工具在下一组初始化时清理了此前原始 trace；上表采用随后逐组即时归档的两组帧与五次冷启动，全部保留原始 trace，未与初采混算。

启动与容量的五样本 P95 使用 inclusive 插值，样本量有限；容量采用 nearest-rank 时为 2147ms，判定相同。

轻载分析器因超过门槛返回失败，属于有效数据的数值失败；两组输入均有 100 个精确事件 ID → HWUI frame ID → SurfaceFlinger 实际呈现关联，并验证单一进程和时钟校准。没有用时间邻近帧、最大帧时长或缺失样本补值替代。

暖启动原始 trace 中确有启动和完整绘制事件：例如 sample 0 的 Perfetto startup 表识别出 warm，trace 同时包含 activityResume、doFrame、RenderThread DrawFrame 和 reportFullyDrawn。**这些离线诊断不能冒充 AndroidX 的正式 TTFD 输出**，也不能据此认定应用暖启动耗时超标；指标提取失败的根因尚未锁定。本轮保留 0/5 判定，没有接受等价计时豁免。

本轮数值低于第四轮部分历史帧结果，但样例代码、候选及部分采集配置已有变化，且第五轮主要性能设备为 PJZ110。**不能据此计算本次裁剪修复的优化百分比或认定无性能回退。** 这轮提供的是当前候选相对冻结绝对门槛的结论。

主要证据：

- [统一复核摘要](../evidence/targeted-retest-round6-20261102/k60u/reviewed-summary.json)、[第一次官方帧 JSON](../evidence/targeted-retest-round6-20261102/k60u/frame-1-benchmarkData.json)、[第二次官方帧 JSON](../evidence/targeted-retest-round6-20261102/k60u/frame-2-benchmarkData.json)。
- [冷启动完整 JSON](../evidence/targeted-retest-round6-20261102/k60u/cold-benchmarkData.json)、[暖启动五个实际失败](../evidence/targeted-retest-round6-20261102/k60u/warm-device.txt)、[warm 原始事件诊断](../evidence/targeted-retest-round6-20261102/warm-sample0-startup-query-inputs.csv)。
- [轻载逐样本关联](../evidence/targeted-retest-round6-20261102/k60u/validation/input-normal/joined-latency.json)、[压力逐样本关联](../evidence/targeted-retest-round6-20261102/k60u/validation/input-stress/joined-latency.json)、[HWUI 原始统计摘要](../evidence/targeted-retest-round6-20261102/k60u/hwui-summary.json)、[容量五次](../evidence/targeted-retest-round6-20261102/capacity-ready.json)。

## 4. 构建、冻结与交付检查

| 检查 | 结果 |
|---|---|
| 正式 JVM | **414 = core 222 + compose 192**，0 failure/error/skip；[统计](../evidence/targeted-retest-round6-20261102/formal-counts.json)。 |
| Android 原生回归 | 字体 7 + PNG/示例/就绪 5 = **12 项通过**；与正式性能用例的暖启动失败分开统计。 |
| release / lint | R8 release、benchmark 构建与两模块 release lint 通过；示例保留 1 项 monochrome launcher icon warning，无 lint error。 |
| 本地发布与独立 consumer | rc4 发布本地仓库及 Maven local；独立 consumer 离线 clean 重编通过，核验实际解析 core/compose 制品与 rc4 发布哈希相同。见[consumer 结果](../evidence/targeted-retest-round6-20261102/consumer-offline.txt)。 |
| 上游对齐校验 | 七面矩阵格式、覆盖及引用检查通过；本轮只补齐 pane 画布边界，不是重新完成全部上游行为审计。 |
| 0.15.0 冻结 | 本地发布仓库的 **8 个原冻结制品哈希全部一致**；未覆盖旧坐标。见[冻结核验](../evidence/targeted-retest-round6-20261102/baseline-artifacts-unchanged.json)。 |
| 设备恢复 | 字体 1.0、user_rotation=0、自动旋转=1、TalkBack/无障碍设置与进入本轮时一致；独立测试进程已停止，示例恢复普通模式，最终安装 APK 哈希一致。 |

## 5. 当前收敛与剩余验收

**结构与逻辑一致性在本次修复上继续收敛：** 用 pane 裁剪恢复上游独立 Canvas 的边界语义，现有渲染、缓存与 Flow 结构继续沿用，未通过缩小字号、删减指标或重写布局规避问题。

**发布性能尚未收敛到门槛：** CPU P95 距 10ms 仍约 8.3–8.7ms；轻载输入 P95 距 50ms 约 20.2ms；正 overrun 全部超时，容量 3 个样本超出 2s。下一步应分别定位绘制/呈现耗时、轻载输入呈现延迟、100k 注入尾部以及严格暖启动指标提取，并用相同最终 R8 包重新验收，不能以本轮图例关闭推导整体放行。

本轮没有重跑 rc4 的双机 30 建销/100 切品种 heap、30 分钟 B06、Android 6 实机或新一轮真人 TalkBack；第五轮 rc3 的既有证据保留原候选范围。PJZ110 的 rc4 大字体横屏及性能仍需该机连接后补验。未进行生产签名、远程发布或 Git 提交。
