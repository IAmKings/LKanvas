# 0.11.0「第六轮复审收口」实施计划（M01-M06 全项 + 台账修复）

## Context

第六方审计 `structure-logic-audit-round6-2026-10-31.zh-CN.md` 对 0.10.0（`f0b8dc0`）提出 6 项发现（M01-M06：4×P1、2×P2），55 项探针中 8 项复现差异。规划前经 2 个核查代理逐项验证：**六项全部成立**，另确认台账缺 N06 行 + 两处注释与实现不符。审计确认 0.10.0 的 N01-N07 成果真实（原 ABA/首推/非法 INIT 单例/坐标/格式/arc 全部保留，不回退）；本轮核心主题仍是「同一规则多处保存」——轴选择在实例循环外、控制点在裁剪块外且拥有全局第一遍特权、失败清理无归属。三项设计决策采用推荐方案：M01 单调代次+归属复核、M03 BAR 四键+LINE coordinates、M06 单一 z 序遍历。版本 0.11.0。

## 一、问题总账（已验证）

| # | 问题（验证结论） | 修复方向 |
|---|---|---|
| M01 | applyInit Rejected 分支 `decrementAndGet` 改写全局代次 + 无归属 `unsubscribeData` 退订较新 C 会话 | 捕获 `myEpoch`；Rejected 分支先复核仍属当前会话再清理；代次单调不回退 |
| M02 | drawExtraPane 的 boundAxis 在实例循环外 firstOrNull、y 单一函数传全部实例；drawIndicatorPaneInstances 不读绑定轴 | 轴解析移入实例循环，每实例按 yAxisId 构建专属 y（未绑定回退普通线性） |
| M03 | BAR attrs：x 被当中心（上游=左边界）、y/height 不消费；LINE attrs 零消费（structure-map 登记理由不成立） | BAR 按类型合并 attrs（默认几何+attrs 覆盖，x=左边界、消费 y/height）；drawLinePlots 支持 coordinates 覆盖（有 attrs 逐点、无 attrs 批量快路径） |
| M04 | 控制点在 clipRect/translate 块外（0243715 只改坐标为局部、块未移入）→ 副图控点画进主图 | 控制点块移入 clipRect 内、改回绝对坐标（最小修复） |
| M05 | paneVisibleRange 不消费模板 minValue/maxValue（VOL minValue=0 恒值 100 得 (99,101) 不含 0） | 范围收口处合并可见指标的 min/max 约束（上游 YAxis.ts:163-172/222-224 语义） |
| M06 | hitTest 控制点第一遍全局特权——低层固定控点抢占被高层遮住的位置 | 控制点并入单一 z 降序遍历（每实例先控点后线身，随实例 z 竞争） |
| 台账 | gap-matrix 缺 N06 行 + 455 行空行截断表格 | 补 N06 行（注明 M04 回归）+ 修格式 |

## 二、实施批次（每批收口：全量测试绿 + 提交）

### Batch A：会话失败归属（M01，P1 最高优先）

**A1** `LKanvasChart.applyInit`：`val myEpoch = dataEpoch.incrementAndGet()` 捕获自身身份；协程内 reset 结果 Rejected 时先复核 `dataEpoch.get() == myEpoch`（仍属当前会话）才执行 `store.unsubscribeData()` 清理；**删除 `decrementAndGet()`**（代次单调，用失效机制代替回滚——保 Q01-b 首推语义：myEpoch 匹配时 Reset 成功、后续推送正常）。

**A2** `Store.unsubscribeData` 增归属参数（可选 `expectedEpoch: Long? = null`——Host 传入；Store 内部记录 epoch 快照或仅依赖 Host 侧复核；最小修复只做 Host 侧门禁，Store 签名不动）。

**A3 parity-13**：审计 M01 复现（A 就绪 → B 非法 INIT 提交 → 切 C 合法 INIT → 调度后 C 推送仍生效、unsubscribeBar 记录含归属不误退 C）；ABA/首推/单 B 失败既有 parity-11/12 基准保持。

### Batch B：per-instance 轴投影（M02，P1）

**B1** `drawExtraPane`：轴解析移入实例循环——每实例按 `inst.yAxisId` 查 `extraYAxisMeta`（且 paneId 匹配），有绑定轴则用 `extraAxisRange(inst.yAxisId,…)` + `effectiveExtraAxisSpec` + reverse 构建专属 y；未绑定（yAxisId="main" 或轴不存在/不属于该 pane）回退普通线性 + paneVisibleRange；删除循环外 firstOrNull 单 y。绑定轴值域改 `extraAxisRange(aId,…)`（与 axisValueToY 同式）。

**B2** `drawIndicatorPaneInstances`（vol/macd 入口）同样 per-instance：有绑定轴实例用其 spec/reverse/范围投影。

**B3** 网格/标签：主轴网格用未绑定实例的范围或 paneVisibleRange；绑定轴实例的轴标签列消费该轴（若主图模式已有则复用）。

**B4 测试**：同 pane 双实例双轴（reverse+normal）各自投影正确（N02 复现）；内置 vol 绑定 reverse RSI 轴后投影正确；未绑定轴不影响默认实例。

### Batch C：figure 几何契约（M03，P1）

**C1 BAR attrs 合并**：`drawBarPlots` 改为「默认几何 + attrs 按类型覆盖」——先算默认 (x=candleCenterX-aw/2 左边界, y=min(vy,baseY), width=bodyWidth, height=|vy-baseY|)，再以 attrs 提供的键覆盖：`x`=左边界（不再减半宽）、`y`=顶边（+paneTop）、`width`、`height`（+paneTop 语义同 y）；无 attrs 走现有快路径不变。

**C2 LINE attrs.coordinates**：`drawLinePlots` 增可选 attrs 几何参数——有 `coordinates` 时按点列绘制（逐点 lineTo 路径），无 attrs 维持 drawLines 批量快路径；structure-map LINE 行登记理由同步修订（批量渲染与几何覆盖不互斥）。

**C3 注释纠偏**：PaneRenderers.kt:335-337（N02 注释）、:712-715（M03 注释）修正为与实现一致。

**C4 测试**：{x:20,y:30,width:7,height:9} 零偏移绘制 [20,30,7,9]（M03 复现收口）；LINE coordinates 覆盖；无 attrs 批量快路径回归。

### Batch D：控制点层级与裁剪（M04+M06，P1+P2）

**D1 控制点移入裁剪块（M04）**：把控制点 if 块移入 clipRect 内（translate 外），坐标改回绝对 `Offset(it.x, it.y)`（clipRect 边界已是绝对值）——修复「局部坐标直接画到整图」的回归。

**D2 单一 z 序命中（M06）**：hitTest 合并为单一 z 降序遍历——每实例先判控点（选中且非连续模板）再判线身；删除独立控制点第一遍。同实例内控点优先于线身（上游同层 point figure 后画先命中语义）；跨实例按 z 竞争（z=10 对象先于 z=0 控点命中）。

**D3 注释修正**：PRD R-09「控制点全局优先」表述改为「同实例内控点优先于线身、跨实例按 z 序」。

**D4 测试**：M04 复现（控点绘制坐标=命中坐标）；M06 复现（z=10 宽线覆盖 z=0 控点时命中高层）；单对象端点编辑保持（N07 基准）。

### Batch E：范围约束与台账（M05 + 台账修复）

**E1 minValue/maxValue（M05）**：`paneVisibleRange` 范围收口处（return 前）合并可见指标的 `definition.minValue/maxValue`（非 null 时 `lo = min(lo, minValue)`、`hi = max(hi, maxValue)`，对齐上游 YAxis.ts:222-224）——三分支统一在实例循环内取。

**E2 台账修复**：gap-matrix §16 补 N06 行（注明 M04 回归已修）+ 删 455 行空行；新增 §17 M01-M06 追踪节。

**E3 测试**：VOL minValue=0 恒值 100 → 范围含 0（M05 复现 a）；minValue=-50/maxValue=500 BAR → 范围 (-50,500)（M05 复现 b）。

### Batch F：0.11.0 发版回归

- check.py 解析扩展 `M\d{2}`（延续 R/Q/N 先例）；gap-matrix §17 M 行引用补全。
- 全量测试（--no-build-cache）+ oracle + 双路径 + parity-4~13。
- README/compatibility：0.11.0 条目 + 表述纠偏审计 §6 指出项。
- 发布 0.11.0 制品 + consumer 重编；B-01/B-02 豁免延续（§4.u 口径；本轮渲染分组/attrs 改动风险评估登记）。
- 版本切 0.11.0。

## 三、关键文件

- compose：`chart/LKanvasChart.kt`（A1 applyInit 归属复核）、`compose/state/ChartState.kt`（E1 范围约束）、`compose/draw/PaneRenderers.kt`（B1-B2 per-instance 轴、C1-C2 attrs 合并、C3 注释）、`compose/draw/OverlayRenderer.kt`（D1-D3 控点/层级）
- 测试：`parity/Parity13SessionOwnershipTest.kt`（新）、Parity5/6 增强为主
- 工具/文档：`tools/alignment/check.py`（M 前缀）、`gap-matrix.md`（§16 修 + §17）、`structure-map.md`（C2 LINE 行修订）、`README.md`、`compatibility.md`

## 四、风险与对策

- **D2 命中顺序变更可能破坏既有选中行为**：单对象端点编辑（N07 基准）与 Parity5 用例守护；审计确认上游即为 z 序竞争模型。
- **C2 LINE coordinates 逐点绘制性能**：有 attrs 时才走逐点路径（无 attrs 批量快路径不变）——正常指标无 attrs 不受影响。
- **B1 per-instance 分组**：轴解析从循环外移入循环内为 O(实例×轴) 查表（extraYAxisMeta 为 Map，O(1) 查询）——量级不变。
- 顺序：A（正确性）→B（轴单一来源）→C（figure 契约）→D（控点层级）→E（约束+台账）→F（发版）。

## 五、验收对照（审计 §7.2 五条）

| 审计要求 | 落点 |
|---|---|
| 异步失败只清理发起会话，代次单调 | A1-A3 |
| 轴选择与投影在实例/分组内，固定与动态 pane 复用 | B1-B2 |
| 默认几何与 attrs 上游含义，控点裁剪/平移/层级与命中一致 | C1-C2 + D1-D2 |
| 范围入口执行模板约束，真实消费者验证 | E1 |
| 根因修复后独立复审 | F 发版回归 + N/M 台账 |

## 验证

每批：`./gradlew :lkanvas-core:test :lkanvas-compose:testDebugUnitTest`；收口：`--no-build-cache` 全量 + `python3 tools/alignment/check.py`（M 行纳入）+ publish/consumer 重编；设备解锁后补 connectedBenchmarkAndroidTest 与真机视觉项（台账登记为待办条件）。