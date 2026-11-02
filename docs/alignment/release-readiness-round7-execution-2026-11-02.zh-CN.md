# 第七轮发布准备执行报告：rc5 阶段验收

日期：2026-11-02。执行依据：[第七轮计划](release-readiness-round7-plan-2026-11-02.zh-CN.md)、[R2-60-v1](../release-acceptance-r2-60.md)。**当前结论：HOLD；尚未完成双机最终验收，也未执行远程发布。**

范围说明：§1–5 保留 17:50 的 `cf37…` 阶段记录，§6 保留接回 PJZ110 后的补验及试验。**最新同包双机结果与收敛结论见 §7–8**。不同 SHA 的数据分别保留，不能合并为最终通过。

0.15.0 冻结提交及本地仓库原有 8 个制品保持不变。当前修复候选为 **0.15.1-rc5 / versionCode 5**，两台真机均安装 R8 APK **`8c5895b3c581c7e58cb3fc20e82a11b7a0cb4f363fc479bb7230e2c09032961a`**。最新 K60U 实时帧 CPU P95 19.2584ms / 正 overrun 10.9053%，压力正 overrun 12.5%，仍失败；PJZ110 对应短测各一次通过。正式双次矩阵与 heap/B06/真人 TalkBack 尚未完成，**当前不能发布**。

## 1. 已完成的修复与结构约束

| 改动 | 原因与保留的行为 | 验证 |
|---|---|---|
| warm trace 显式记录线程名 | 内置 TraceProcessor v50 未识别预先存在的 RenderThread 名称，正式 StartupTimingMetric 因筛选不到线程而缺指标。只改测试捕获配置，未改生产启动流程。 | 未改生产的 rc4 已取得 5/5；当前 rc5 再次取得 5/5，详见第 2 节。 |
| 示例窗口请求 60fps 基础档 | `MainActivity` 的真实交付路径请求 60Hz；诊断 intent 可跟随屏幕刷新率。库未修改全局刷新率。 | 当前帧用例的 display/window 快照记录了实际 60Hz SF 模式及 deadline；120Hz 增强档仍待正式补采。 |
| 指标连续路径 | 原独立 `drawLines` 的 round 端帽会重复覆盖半透明端点、重启虚线相位。按上游相邻端点及样式合并连续 Path，普通线使用 butt/miter。 | 原生半透明、NaN 断点、A→B→A 顺序、虚线四组栅格回归通过；保留行归属、attrs 替换及回调顺序。 |
| 坐标与重复准备 | 最后一步才将共享 Viewport 的 Double 坐标转 Float，修复大索引相消精度；同一实例每帧只调用一次 plotsFor。 | 新增 JVM 红/绿回归；现有 figure 几何和提交顺序断言保持。 |
| 文本与时间轴热路径 | 测宽与绘字共享有界 TextLayoutResult；时间轴每帧共用一个时区；提前跳过确定重叠的候选；按实际分支格式化日期。 | 保留字体/Locale 失效；时间缓存键包含相邻时间、间隔、时区 ID/偏移及区域，覆盖此前首标签、日线与同 ID 改偏移错误。 |
| 重绘、语义与惯性时序 | 图表自身 graphicsLayer；摘要在 semantics 阶段观察 revision；平台 OverScroller 按显示帧推进。 | 静态平移、更新后再平移、首帧/后续帧像素及连续前后根摘要通过；外部 scope 无帧时钟、手势取消回归通过。 |
| 示例报价观察范围 | 父容器只观察数量和品种，横屏报价由独立摘要观察实时数据；点击演示时读取最新数据。 | 同数量报价替换后，界面与图表摘要更新，图表边界保持；四项 workspace UI 全部通过。 |

上游参考为本地 `third_party/klinechart-v10.1.0`，连续合并规则见 `src/view/IndicatorView.ts:229`，非 smooth 端帽/连接规则见 `src/extension/figure/line.ts:140`。这是本轮相关绘制语义的对齐，不表示重新证明全部 TypeScript 与 Kotlin 逐行等价。

Store/指标引擎/门面/Renderer 的现有分层保留，没有引入另一套渲染架构或跳过真实非空用户回调。8 个相关类的公开 JVM 签名与进入本轮的 rc4 一致。**319 行对齐矩阵检查通过属于结构与引用检查，不能解释为 319 项全量行为认证。**

代码差异以任务进入时的工作区快照为基准，避免把用户已有改动归成本轮新增：[准确补丁](../evidence/release-readiness-round7-20261102/candidate-vs-entry.patch)、[已有文件变化清单](../evidence/release-readiness-round7-20261102/candidate-source-changed-since-entry.json)、[当前源码快照](../evidence/release-readiness-round7-20261102/candidate-source-snapshot.zip)。新增原生回归源文件也包含在快照内。

## 2. `cf37…` 阶段同包 K60U 结果（历史）

以下帧结果均来自官方 AndroidX FrameTiming；输入采用事件 ID→HWUI frame ID→SurfaceFlinger 实际呈现精确关联。字体 1.0、竖屏、实际 60fps 基础路径；样本与阈值不跨候选混算。

| 项目 | 当前 rc5 结果 | 判定及范围 |
|---|---|---|
| S0 默认实时行情帧 | 491 个匹配帧；CPU P95 **16.698154ms**；正 overrun **41/491 = 8.350305%** | 超过 16ms / 5%，**FAIL**。 |
| S1 静态轻载帧 | 483 个匹配帧；CPU P95 **13.077177ms**；正 overrun **8/483 = 1.656315%** | 本次短测通过；尚未取得两次独立通过。 |
| S2 静态压力帧 | 479 个匹配帧；CPU P95 **14.334377ms**；正 overrun **60/479 = 12.526096%** | CPU 达标，正 overrun 超过 10%，**FAIL**。 |
| 严格 warm TTFD | 5/5；214.341846、187.423154、202.879385、204.686461、205.310769ms | inclusive P95 **212.535631ms ≤500ms，通过本机当前包此项**；预热/测量 PID 一致断言通过。 |
| S1 轻载输入 | 100/100；P95 **78.545046ms**，最大 79.207477ms | 本次 ≤80ms；余量较小，仍缺第二次独立采集。 |
| S2 压力输入 | 100/100；P95 **78.852946ms**，最大 79.592789ms | 本次 ≤100ms；仍缺第二次独立采集。 |
| 冷启动 TTFD | 5/5；469.775077、494.086769、476.214462、476.318154、457.715077ms | inclusive P95 **490.533046ms ≤1500ms，通过本机当前包此项**；TTID 单独留存。 |
| S3 100k 容量 | 5 个不同 PID；全量注入就绪 1868、1837、1689、1736、1743ms；首批 1000 根 28–47ms | inclusive P95 **1861.8ms ≤2500ms，通过本机当前包容量子项**。不是 TTFD；B04 突发收敛仍未测。 |
| R8 字体与交互 | 六组 1.0/1.3/2.0 × 横竖屏截图方向正确；双指缩放、长按和连续两次无障碍“上一根”通过 | 六图已逐一目视检查，未见副图图例跨 pane。2.0 竖屏 VOL 末行受 pane 裁剪，不承诺小高度下所有图例行完整可见。真人 TalkBack 尚未执行。 |

当前帧与输入只是修复阶段短测。由于 S0/S2 失败，本轮没有声称已冻结可放行的最终候选，没有启动依赖短测通过的双机 heap/30 分钟最终长测。

证据目录：[当前 K60U 分项采集](../evidence/release-readiness-round7-20261102/6D8H6TXSDYXC85PZ)、[warm 汇总](../evidence/release-readiness-round7-20261102/candidate-warm-summary.json)、[冷启动汇总](../evidence/release-readiness-round7-20261102/candidate-cold-summary.json)、[容量汇总](../evidence/release-readiness-round7-20261102/candidate-capacity-summary.json)、[2.0 字体横屏](../evidence/release-readiness-round7-20261102/6D8H6TXSDYXC85PZ/rc5-quote-scope-ui-matrix/validation/ui/font-2.0-landscape.png)。每个 case 的 `collection.json` 绑定 APK、runner 哈希和即时归档 trace；只采用与该 case 对应的本次数据。随目录整体复制的历史 validation 文件不自动计入本次通过项。

## 3. 归因与收敛程度

**有明确进展，但还没有证明稳定达标。** 原来的 warm“0/5 指标不可用”已定位并关闭 K60U 当前包的计量缺口。普通静态使用的帧短测已通过，压力 CPU 已低于 16ms；实时行情更新帧和压力呈现尾部仍阻塞。

本轮最早实际 60Hz 候选的压力 CPU P95 为 18.6966ms、正 overrun 73.78%；当前为 14.3344ms、12.53%。这表示相同负载/发布模式下的阶段进展，**不是严格受控 A/B 优化百分比**。不能直接把上一轮 120Hz 下的 100% 与现在 60Hz 的数值当成同模式优化证明。

连续 Path 的诊断 trace 显示 RenderThread 绘制平均耗时下降；但 Double 精度修复后、文本缓存后等多次短测数值并非单调改善。所有 rc5 帧短测保留于[完整短测台账](../evidence/release-readiness-round7-20261102/frame-preflight-ledger.json)。历史接近门槛的 **10.2941% 仍是失败**，未四舍五入成 10% 或选优替代当前包。

当前实时 trace 的主线程帧分析显示，带布局的帧平均主线程耗时约 **14.49ms**，其余约 **6.08ms**；行情文字更新的重组与测量是优先排查方向。此为宿主 TraceProcessor v59 的相关性诊断，不能替换 AndroidX 内置 v50 的官方帧门槛结果。温度快照的当前 HAL CPU 约 44℃、热状态 0；缓存栏中的历史 85℃读数不能据此解释为正在降频。

已撤回无充分收益或像素不一致的实验：tooltip 独立 GraphicsLayer 缓存、早期 FloatArray 全量预分配及临时方法采样。正式源码没有残留 `[DEBUG-r7]`/方法追踪插桩。原生像素夹具改为相同位置、互斥绘制，保留 ≤1 通道差断言；未通过放宽像素或性能门槛制造绿灯。

## 4. 回归、兼容与发布链

| 检查 | 结果与边界 |
|---|---|
| JVM | **416 = core 222 + compose 194**，0 failure/error/skip。 |
| K60U 原生 | **20 项全部通过**：字体 11、绘制像素 1、指标线栅格 1、fling 1、PNG 1、workspace 4、压力就绪 1。Debug 仪器回归与 R8 性能结果分开。 |
| lint | app-sample / compose release lint 通过。 |
| API/对齐检查 | 8 个关键类公开 JVM 签名无增删；对齐矩阵格式、引用和覆盖检查通过。 |
| rc5 本地发布 | core/compose 共 8 个主要制品已发布到 `build/maven-repo`；独立 consumer clean 后离线 Debug/Release 编译通过，实际解析的两个制品 SHA 与本地 rc5 相同。consumer 是编译验证 library，**不是独立运行或独立 R8 应用认证**；R8 由示例 release 验证。未执行远程发布。 |
| 0.15.0 | 原有本地仓库 8 个制品全部哈希一致，未重新发布或覆盖。 |
| API 23 | 自建 Android 6 arm64 模拟器安装同一当前 R8 包，安装字节一致；1000 根行情、OHLC 语义、实际平移、新导出 PNG 1080×1600、无 FATAL 均通过。应用以普通 UID 运行；模拟器 adbd root 仅用于读取自身缓存导出。不是 Android 6 实机认证。任务自建模拟器已关闭并清理其 AVD，共享 SDK 镜像保留。 |

主要证据：[JVM 统计](../evidence/release-readiness-round7-20261102/candidate-host-counts.json)、[原生 20 项](../evidence/release-readiness-round7-20261102/candidate-native20.txt)、[API 核对](../evidence/release-readiness-round7-20261102/candidate-api-compatibility.json)、[consumer 实际制品](../evidence/release-readiness-round7-20261102/candidate-consumer-artifact-verification.txt)、[rc5 制品清单](../evidence/release-readiness-round7-20261102/candidate-published-artifacts.json)、[冻结基线核验](../evidence/release-readiness-round7-20261102/baseline-artifacts-current.json)、[API 23 当前包](../evidence/release-readiness-round7-20261102/api23-quote-scope-smoke/summary.json)。

## 5. 尚未关闭的放行项与后续顺序

1. **P0：修复 S0 行情更新帧尾部。** 优先对报价文本布局、快照更新与图表绘制阶段做定点计时；保持 200ms/tick、真实更新/回调和大字体换行行为。不能通过暂停推送、丢行情或隐藏摘要达标。
2. **P0：修复 S2 正 overrun。** 当前 CPU 达标不代表呈现达标；围绕现有绘制路径和尾部分配继续归因，不调整 50k/10 MA/100 overlay 或采集协议。
3. 当前包冷/warm/容量已完成本机五样本子项；轻载/压力输入各一次已通过。后续有生产改动则重跑受影响项目，再重新确定最终候选字节。B04 尚未执行；本轮未关闭这一项。
4. 短测收敛后执行最终 S0/S1/S2 各两次、轻载/压力输入各两次 100/100、冷暖各 5、容量 5/B04、双机真实 heap、30 分钟 B06、字体/交互、真人 TalkBack。120Hz 增强档单列。
5. PJZ110 已在用户确认接回后重新连接，18:40 起核验并安装同一 rc5 APK 与 runner；结果见 §6。K60U 随后也接回，当前两机同时在线。旧 rc3 数据及旧 UI 的真人反馈均不能代替本轮同包认证。双机完整证据齐全后才进行 R7-09 放行审查。

不把“还需几轮”写成达标保证。当前剩余工作至少包括性能修复闭环及一轮完整同包双机终验；任何关键指标再次失败，都必须保留 HOLD 并继续定位。

上述 17:50 阶段结束时，K60U 字体/旋转设置恢复至进入时值，并返回默认实时行情界面；该阶段未修改 CPU、系统全局刷新率或后台电池策略。[历史恢复记录](../evidence/release-readiness-round7-20261102/candidate-device-restored.json)。后续双机采集、测试 runner 临时策略与恢复见 §6–7。所有本轮有效结果与未完成项由[验收证据索引](../evidence/release-readiness-round7-20261102/acceptance-index.json)按 APK 分组汇总。

## 6. PJZ110 接回后的同包补验与额外修复

### 6.1 `cf37…` 数值采集已完整通过，视觉另有失败

PJZ110 / `1fc6056b`，Android 17 / API37，1440×3168；CPU 状态与 sustained performance 以各 case 的 AndroidX context 为准，未主动修改 CPU 设置，也不根据 cpuLocked 字段推断已 root。目标 APK 与 K60U 的 `cf37…` 相同，runner SHA 同为 `4637e4378aa7b6deaf9b884ea1efb197c41e320ef0741e176326e533b1ad30b6`。窗口请求与 SF 实际授予模式均约 60Hz，系统全局 peak_refresh_rate=120.0 保留。

| PJZ110 / cf37 项目 | 两次独立结果或五样本 P95 | 判定 |
|---|---|---|
| S0 CPU / 正 overrun | 10.3892 / 10.1320ms；3/514=0.5837%、2/519=0.3854% | 两次通过。 |
| S1 CPU / 正 overrun | 9.1249 / 8.4538ms；2/507=0.3945%、0/506=0% | 两次通过。 |
| S2 CPU / 正 overrun | 8.8076 / 8.9207ms；0/488、0/511 | 两次通过。 |
| 轻载输入 | 各100/100精确关联，P95 61.1103 / 61.7590ms | 两次通过≤80ms。 |
| 压力输入 | 各100/100精确关联，P95 62.5536 / 62.4732ms | 两次通过≤100ms。 |
| 严格 warm TTFD | 5/5，79.469636、78.698489、70.938698、74.205468、73.410104ms；P95 79.3154ms | 通过≤500ms，同PID断言通过。 |
| 冷启动 TTFD | 5/5，223.677188、217.792917、217.441302、214.904427、223.991093ms；P95 223.9283ms | 通过≤1500ms。 |
| 100k容量 | 五个独立PID，1501/1452/1470/1481/1455ms；P95 1497ms，首批21–25ms | 容量子项通过≤2500ms，不是TTFD。 |

共13个有效采集 case、20份原始trace、400/400输入关联，见[同包复核汇总](../evidence/release-readiness-round7-20261102/1fc6056b/reviewed-summary.json)。这些数值通过**没有证明整机发布验收完成**：目视检查发现 2.0 字体横屏的主图最低价标签覆盖了换行 MA 图例；自动交互用例通过不等于视觉通过。B04、最终 heap/B06/真人 TalkBack 尚未执行。

首次 realtime 采集时，测试进程内核 `cgroup.freeze=1`、生产示例=0，现场见[冻结记录](../evidence/release-readiness-round7-20261102/1fc6056b/runner-frozen-initial.json)。该次被主动中止，runner 的 `Process crashed` 消息来自中止动作，不作为生产崩溃或有效性能数据。沿用此前授权的测试专用流程，只把 runner 从智能优化临时改为完全允许，原设置有 XML/JSON 留档，完成后已恢复。生产示例后台策略未改；没有修改全局冻结器或CPU设置。[设置恢复](../evidence/release-readiness-round7-20261102/1fc6056b/device-restored.json)。

### 6.2 主图低价与换行图例遮挡已定点修复

原 `priceMarkerBounds` 仅在锚点 x 上尝试纵向避让；短 pane 的左侧被两行图例占满时，即使右侧有空间，也会退回重叠的原位置。修复保留原有纵向优先规则，仅在无纵向空位时尝试同一高度相邻的左右空位。高低价扫描、值与精度、绘制顺序、数据/回调逻辑均未改。若两个方向都无位，保留既有拥挤回退；不宣称任意极小 pane 都可完整显示所有文字。

新增真实 native Canvas 回归同时检查高价洋红、低价青色两种字形，覆盖 Density(4,2)、短主图、换行图例、首帧和缓存帧。未修复时低价像素(291,159)落在图例边界中；修复后两种标记均可见且无覆盖，首帧与缓存帧整图像素一致。[红测试](../evidence/release-readiness-round7-20261102/1fc6056b/dense-marker-red-native.txt)、[绿测试](../evidence/release-readiness-round7-20261102/1fc6056b/dense-marker-green-native.txt)。初次测试编译误用了跨模块 internal 入口，属于夹具设置失败；改用公共绘制入口在独立画布记录边界后才取得上述有效红测试，未改变生产可见性。

修复候选仍标识 rc5，APK SHA 为 **`c35b5a11e3e6d4457670acb4bc723d04127d999d46b7ab5585a00af6862b38e1`**。相对 cf37 仅有 `PriceMarkerLayout.kt` 与对应原生测试两个文件变化：[准确补丁](../evidence/release-readiness-round7-20261102/marker-clearance-vs-cf37.patch)。

- JVM416、原生21（PJZ110）、两模块release lint通过；本地rc5重新发布与独立consumer离线clean/实际制品SHA通过。cf37旧制品已另存，0.15.0原8制品未变。8个关键公开JVM类签名仍无增删。
- PJZ110 新R8六组字体/方向及交互复验已完成，2.0横屏低价标签已移出MA图例；S0一次 CPU9.4960ms、2/517=0.3868%，S2一次 CPU8.8967ms、0/487，均通过短测。没有把cf37的完整矩阵移记到c35。
- K60U 接回后对同一 c35 复采：S0 CPU15.4278ms通过，但31/480=6.4583%失败；S2 CPU14.1587ms通过，但63/479=13.1524%失败。发布仍HOLD，必须继续处理尾部呈现。

证据：[原生21项](../evidence/release-readiness-round7-20261102/1fc6056b/marker-clearance-native21.txt)、[c35 R8横屏](../evidence/release-readiness-round7-20261102/1fc6056b/rc5-marker-clearance-ui/validation/ui/font-2.0-landscape.png)、[c35源快照](../evidence/release-readiness-round7-20261102/marker-clearance-source-snapshot.zip)、[新本地制品](../evidence/release-readiness-round7-20261102/marker-clearance-published-artifacts.json)、[PJZ设置恢复](../evidence/release-readiness-round7-20261102/1fc6056b/marker-device-restored.json)。

### 6.3 三项无充分收益的性能试验已撤回

| 试验 APK 前缀 | 实际结果 | 处理 |
|---|---|---|
| `6bd3062c`，首次绘制/回放复用已测文字布局 | 相关原生与像素检查通过；K60U S0 CPU15.9309ms / 44/486=9.0535%，S2 CPU14.4325ms / 71/479=14.8225%，仍失败。布局更新帧均值约1.014→0.971ms，仅减少0.043ms。 | 撤回 helper 与试验专用回归。LRU=1探针可复现重复布局，不代表生产cache=256下存在同等收益。 |
| `81f3aad9`，整块报价摘要独立图层 | workspace四项通过；S0 CPU16.2212ms / 39/485=8.0412%，S2 CPU14.1175ms / 66/473=13.9535%，仍失败。 | 撤回。 |
| `3e5bded4`，仅价格/涨跌/OHLC数值Text独立图层 | workspace四项通过；S0 CPU15.8632ms / 37/492=7.5203%，仍失败。更新帧draw均值7.245→7.171ms，而layout2.287→2.517ms。 | 撤回；没有足够的稳定收益支持保留额外图层。 |

试验 APK、mapping、补丁、trace 和失败数值均保留，不挑选其中最低数字作为当前成绩。工作源码已恢复到下节 `8c…` 的264项哈希，SampleWorkspace 中没有这两项报价图层试验，文字 helper 也没有残留。

另以外部 Gradle init 构建了仅 manifest 增加 profileable 的诊断包 `2549450e…`；DEX 与 c35 相同。第一次方法采样因目标被测试清理而未冲刷，0字节文件无效；提前停止后得到1,392,290字节有效采样，主线程86,653条记录、未匹配exit=0。原生文字、Path和快照读取有可观样本权重，但采样有额外开销，ART方法跟踪不覆盖全部RenderThread原生工作，R8合并类别名也须按mapping核对。**该诊断包及其耗时不计正式性能门槛。**

## 7. 最新保留修复与同包双机复验：`8c5895b3…`

### 7.1 快照重复读取定点清理

`ChartState.paneValueToY` 在委托 `mainPaneYOf` / `paneVisibleRange` 前再次读取 dataSnapshot、检查空数据与可见区；被委托函数已拥有这些保护。只删除3行重复保护，不新增缓存、字段或公开API，不改变坐标公式和调用顺序。

新增 `MainPaneRangeTest.projectingOverlayPointsObservesEachSnapshotOnce` 使用真实 Snapshot.observe 观察200个覆盖物点：修复前600次读取的红测试，修复后200次；投影结果保持。该改动减少已证明的重复读取，**不代表已解决整帧性能**；S0没有覆盖物，不能把它的采样变化归因于此改动。

相对 cf37，本次设备续验最终只保留两个生产文件和两个对应测试文件的变化，即价格标签避让及投影快照清理；原 Store/Renderer/figure/回调结构继续保留。见[4文件清单](../evidence/release-readiness-round7-20261102/device-continuation-source-delta.json)、[准确补丁](../evidence/release-readiness-round7-20261102/device-continuation-vs-cf37.patch)、[红测试](../evidence/release-readiness-round7-20261102/projection-snapshot-red.txt)。

### 7.2 最新性能短测

以下四组绑定同一非debuggable、非profileable、R8+资源压缩APK `8c5895b3c581c7e58cb3fc20e82a11b7a0cb4f363fc479bb7230e2c09032961a` 和同一runner `4637e437…`。每台手机内部串行采集，两机可以同时运行；实际60Hz基础路径、字体1.0竖屏、原负载与阈值不变。

| 设备 / 负载 | CPU P95 / 门槛16ms | 正overrun / 门槛 | 判定 |
|---|---|---|---|
| K60U / S0实时 | **19.258404ms** | **53/486=10.905350%** / 5% | 两项失败。 |
| K60U / S2压力 | 13.773315ms | **60/480=12.5%** / 10% | 呈现失败。 |
| PJZ110 / S0实时 | 10.103645ms | 4/508=0.787402% / 5% | 一次短测通过，未满足双次正式要求。 |
| PJZ110 / S2压力 | 9.059870ms | 1/507=0.197239% / 10% | 一次短测通过，未满足双次正式要求。 |

K60U 实时帧在 c35 与8c及撤回试验间存在波动，没有证明稳定改善，更不能以历史一次CPU≤16ms关闭问题。压力正overrun仍在约12.5%附近，距离10%门槛尚差2.5个百分点。实时帧还需要CPU P95下降约3.26ms，正overrun下降约5.91个百分点；这些只是本次样本与门槛的差，不是可线性预测的优化工作量。

四组官方JSON、完整trace、精确分母和环境见[按APK分组索引](../evidence/release-readiness-round7-20261102/acceptance-index.json)。cf37的PJZ110完整数值矩阵保留历史认证范围，未转移到8c。当前8c尚未采集正式S1、输入、冷暖启动和容量全矩阵，不用旧包的通过数补齐。

### 7.3 正确性、兼容与制品链

| 检查 | 结果与限制 |
|---|---|
| JVM完整套件 | **417项通过**：core222、compose195，failure/error/skip均0；[统计](../evidence/release-readiness-round7-20261102/projection-snapshot-host-counts.json)。 |
| 原生相关回归 | K60U **21项通过**：Typography12、图层像素1、指标栅格1、fling1、PNG1、workspace4、压力就绪1。来自当前源码的Debug原生测试，不称R8性能认证；PJZ110的21项属于此前c35。 |
| 双机当前R8字体/交互 | 两机各六组字体方向、双指、长按、连续两次无障碍上一根自动用例通过；**12张字体截图逐一目视复核**，低价/换行MA覆盖未再出现，图例未跨pane。2.0竖屏VOL尾部仍按自身pane裁剪，未声称全部图例可完整显示；这也不代替真人TalkBack听感验收。见[截图复核清单](../evidence/release-readiness-round7-20261102/projection-font-visual-review.json)。 |
| API23最低路径 | 同一8c在自建ARM64 Android6模拟器安装、加载1000根、OHLC语义、实际平移、新UI导出1080×1600 PNG通过，无Fatal；应用普通UID。adbd root仅用于取私有cache证据，不是Android6实机认证。自建AVD已关闭清理，共享SDK镜像保留。 |
| 构建与API | release lint通过；8个相关公开JVM类签名无增删；撤回试验后最终assembleRelease再次生成与实测8c **完全相同字节**，264项源码/配置哈希一致。 |
| 本地发布与消费 | 最新rc5 LocalRepo八制品、独立consumer干净离线构建/实际解析SHA通过；MavenLocal八制品与LocalRepo一致，仅本地发布。历史cf37/c35制品另存档，旧manifest中的原可变路径由新索引映射到归档路径。 |
| 冻结基线 | 0.15.0原八制品再次逐一SHA核验不变，[终检](../evidence/release-readiness-round7-20261102/baseline-artifacts-final.json)。没有修改冻结坐标或覆盖基线。 |

最新[APK](../evidence/release-readiness-round7-20261102/rc5-projection-snapshot-release.apk)、[mapping](../evidence/release-readiness-round7-20261102/rc5-projection-snapshot-mapping.txt)、[源码快照](../evidence/release-readiness-round7-20261102/projection-snapshot-source-snapshot.zip)、[重建一致性](../evidence/release-readiness-round7-20261102/projection-snapshot-rebuild-hash.json)、[API23摘要](../evidence/release-readiness-round7-20261102/api23-projection-snapshot-smoke/summary.json)、[本地制品](../evidence/release-readiness-round7-20261102/projection-snapshot-published-artifacts.json)。

两台真机目前均为8c，回到默认实时行情；字体、旋转和无障碍设置已与进入值核对，PJZ110测试runner临时电池策略恢复智能优化，生产示例策略保持原样。没有修改全局刷新率或CPU设置，详见[最终设备核验](../evidence/release-readiness-round7-20261102/projection-final-device-check.json)。未提交Git、未远程发布。

## 8. 收敛情况、未关闭项与下一步

**正确性与测量在收敛，K60U性能尚未稳定收敛，发布仍HOLD。** 严格warm缺指标已解释并修复；PJZ110已取得有效同包数字，发现并关闭额外价格标签遮挡；本轮只保留有回归依据的局部修复，撤回无充分收益的复杂度。K60U相对早期100%正overrun已有改善，但模式、APK与实现经历变化，不能把它当成严格同条件的单调性能曲线。最新S0仍失败，无法可靠承诺还需一轮或几轮就会发布。

| 任务 | 当前状态 |
|---|---|
| R7-01/02/03 | 验收口径锁定、真实60Hz交付路径及暖启动计量修复有证据；最终候选的完整重采要求继续保留。 |
| **R7-04** | **未关闭**：K60U S0 CPU/呈现、S2呈现仍失败。下一修复仍须由trace归因支持，保留负载、结构、数值与回调语义。 |
| R7-05 | cf37五进程容量子项通过；当前候选最终容量/B04未完成。 |
| R7-06 | 当前源码回归、R8、API、兼容与本地制品链通过；尚不冻结为可发布最终包。 |
| R7-07 | 同包双机短测与字体/API23补验完成；完整双次性能/输入和启动容量矩阵未完成。 |
| R7-08 | 最终双机30建销/100真实切品种与Shark heap、30分钟B06、真人TalkBack未执行；没有沿用旧候选关闭。 |
| R7-09 | 当前检查点证据审查完成，结论HOLD；最终放行审查仍依赖全部必需项。 |

下一执行顺序保持原计划：先针对K60U更新帧和压力迟到帧取得可复现收益，短测达标后固定候选SHA；再完成双机S0/S1/S2各两次、两类输入各两次100/100、冷/warm各5、容量5与B04，之后执行最终R8释放/heap、B06、交互与真人TalkBack，单列120Hz增强档。所有失败样本保留，不调整R2-60-v1阈值来关闭问题。
