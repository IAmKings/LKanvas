# 0.15.0 验收收尾轮实施计划（K60U 真机 + 发布路径验证）

## Context

第十一轮审计宣布**源码审计阶段收口**（零新增发现，历轮条目 16→…→0），建议冻结 `78a281b`（0.15.0）为已审计基线，剩余为验证证据缺口：设备绘制/触摸、TalkBack、Macrobenchmark（B-01/B-02 自 0.7.0 起连续豁免，最近实采 0.6.0）、R8/发布路径。K60U 真机（小米 23078PND5G，serial 6D8H6TXSDYXC85PZ，corot——即台账冻结设备）现已连接，本轮执行验收收尾。

**范围口径**：性能实采只补 B-01/B-02（既有豁免项；B-03~B-05 已有 JVM 数据、B-06 长测另行安排）；验证方式为我经 adb/UiAutomator 驱动真机 + screencap 留证；**全绿则冻结 0.15.0 不发新版本**（证据回填台账），发现具体问题才返修发 0.16.0。

**既有基建（全部复用，不新造）**：`:benchmark` 模块 3 个基准类（StartupBenchmark=B-01、B02GfxInfoBenchmark=B-02 等价口径含「计量不可得即失败」护栏、B02StressBenchmark 按 MIUI 限制保持 @Ignore）；`:app-sample` 可安装 demo（stress_mode 注入、诊断面板、PNG 导出）；`docs/alignment/demo-checklist.md` A-G 验收清单（首轮已过 A1-A4/C1-C4/D1-D5/E1-E2/F1/G3-G4，**顺延项：B2-B4 真机、C5-C7、E3-E4、F2、G1-G2** 及历轮视觉复核）；发布链 = `./gradlew publish` + `tools/consumer-example` 编译（A-46）+ app-sample release R8。

## 实施批次

### Batch 0：设备预检
- `adb devices` 确认 6D8H6TXSDYXC85PZ 在线；`adb shell input keyevent KEYCODE_WAKEUP` + `wm dismiss-keyguard`；确认屏幕解锁、SDK 34、存储充足。
- **闸门**：若安全锁屏仍无法解除（历史问题）→ 停下请用户手动解锁，不继续。

### Batch 1：设备功能验收（demo-checklist 回填）
1. `./gradlew :app-sample:assembleDebug` + `adb install -r`，启动 `io.lkanvas.sample`。
2. 经 UiAutomator 驱动逐项走查 A-G 七组（重点顺延项）：
   - A1-A4 数据加载/实时末根/追加/历史分页；B1-B4 指标创建/27 模板/移除/参数覆盖
   - C1-C7 覆盖物（16 工具、segment 拖动/删除/锁定、斐波那契、brush）
   - D1-D5 手势（平移/双指缩放/长按十字线/pane 分隔条/轴区缩放）
   - E1-E4 主图类型/轴模式/Styles 树/明暗主题
   - F1 PNG 导出（**图片编码集成**验证点）、F2 诊断开关
   - G1-G2 TalkBack/替代动作（`uiautomator dump` 验证语义树 contentDescription + customActions；MIUI 无 TalkBack 服务时如实记录语义树证据）、G3 字体 2.0、G4 横屏
3. 每个关键状态 `adb shell screencap` 留证至 `docs/evidence/0.15.0-device/`。
4. 回填 `docs/alignment/demo-checklist.md` 新节「0.15.0 真机复核记录（验收收尾）」——逐项 ✅/❌/⬜，关闭历轮顺延。

### Batch 2：B-01/B-02 性能实采（豁免补测）
1. `./gradlew :benchmark:connectedBenchmarkAndroidTest`——跑 StartupBenchmark.coldStartup（B-01，StartupTimingMetric 5×COLD）+ B02GfxInfoBenchmark.stressScroll（B-02 等价口径：50k candle + 10 MA + 100 segment，20 次滑动 + gfxinfo 解析，含护栏）；B02StressBenchmark 维持 @Ignore（MIUI 14 vendor 计量限制，豁免理由不变）。
2. 数据落 `benchmark/build/outputs/connected_android_test_additional_output/` + logcat `B02_GFXINFO` 双通道。
3. 对照门槛（§2）：B-01 热进程首帧 P95 ≤500ms；B-02 超时帧 ≤5%；对照 0.6.0 基线（412.8ms / P50 23ms、P95 32ms）——**P95 劣化 >10% → 记入 T-34 循环并按审计口径判定是否返修**。
4. 结果回填 `docs/performance-baseline.md` 新节（0.15.0 冻结基线实采，关闭自 0.7.0 起的豁免序列）。

### Batch 3：发布路径验证
1. R8：`./gradlew :app-sample:assembleRelease`（release 开 R8）→ 安装 release 包 → 冒烟启动 + 截图（README 发布链的「R8 release 真机验证」）。
2. `./gradlew publish` 重发 0.15.0 制品 + `cd tools/consumer-example && ./gradlew :consumer:build`（A-46 制品编译验证）。

### Batch 4：台账收口与冻结声明
- `docs/compatibility.md` 新条目：0.15.0 验收收尾——冻结 `78a281b` 为已审计基线；设备/触摸/性能/发布证据登记；无代码修改。
- 全部通过 → **不发新版本**；任何具体问题 → 修复批次 + 0.16.0（返修预案）。

## 关键文件
- 只读复用：`benchmark/src/main/kotlin/io/lkanvas/benchmark/*`、`app-sample/**`、`docs/alignment/demo-checklist.md`（回填）、`docs/performance-baseline.md`（回填）
- 预期修改：`docs/alignment/demo-checklist.md`、`docs/performance-baseline.md`、`docs/compatibility.md`、新增 `docs/evidence/0.15.0-device/*.png`
- **预期零源码修改**；发现问题时返修范围另定

## 验证方式
每批次产出即验证：Batch 1 清单逐项回填 + 截图证据；Batch 2 基准 JSON/gfxinfo 数据 vs 门槛表；Batch 3 R8 包真机启动 + consumer 编译绿；最终以 compatibility.md 冻结声明收口。全部命令真实执行并汇报原始结果（含失败），不预设通过。
