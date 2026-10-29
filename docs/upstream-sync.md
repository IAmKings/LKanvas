# 上游同步记录（PRD R-17/T-33）

上游基线：KLineChart **v10.1.0**（快照 `third_party/klinechart-v10.1.0/`）。
检查频率建议：每月一次与自身发布前一次（PRD 12.2，数据正确性修复优先评估）。

## 演练 1（2026-10-26，A-48）

| 项 | 内容 |
|---|---|
| 检查方式 | GitHub compare `v10.1.0...main`（来源见下方链接） |
| 候选提交 | `e52ec09`「docs: readme」（liihuu，2026-10-21），v10.1.0 之后 main 上**唯一**提交 |
| 变更文件 | 2 个（README 相关文档，非 src/） |
| 分类 | **文档类 → 不适用**（无源码/行为影响，无需复现与移植） |
| 行为复现 | 不适用（无行为变更可复现） |
| 移植决定 | 不移植（LKanvas 独立 README，内容不同） |
| 回归 | 无需回归；基线仍为 v10.1.0，兼容台账不变 |
| 结论 | 上游 v10.1.0 后暂无数据正确性/交互/性能类补丁；下一次检查继续以 compare 页为入口 |

## 演练流程模板（PRD 12.2）

1. 读取固定版本间 diff（`https://github.com/klinecharts/KLineChart/compare/<基线>...main`）
2. 分类影响：数值/数据正确性、交互、性能、新功能、浏览器专属、破坏性变更、文档
3. 浏览器专属与文档类 → 通常不适用；数据正确性类必须复现并优先评估移植
4. 移植项：登记原提交、影响能力（台账编号）、Kotlin 改动、独立/差分回归用例
5. 更新本记录与 compatibility.md，写入发行说明

## 链接

- 比较：https://github.com/klinecharts/KLineChart/compare/v10.1.0...main
- Releases：https://github.com/klinecharts/KLineChart/releases
- Change Log：https://klinecharts.com/en-US/guide/changelog

## 演练 2（2026-10-28，对齐批次完成复核）

| 项 | 内容 |
|---|---|
| 检查方式 | GitHub compare v10.1.0...main（同演练 1） |
| 结论 | 上游仍无新源码提交；本轮对齐以 v10.1.0 静态快照为唯一对照完成 |
| 矩阵同步 | Chart.ts 79 方法/Indicator 23 字段/Overlay 24 字段/Styles 43 接口全部入矩阵并推进（E 156→55） |
| 后续检查 | 每月 compare；上游发布新版本时优先跑 tools/alignment/check.py + 两个 meta diff 测试 |

## 演练 3（2026-10-29，0.5.0 发版例行 compare）

| 项 | 内容 |
|---|---|
| 检查方式 | GitHub compare v10.1.0...main（WebFetch，本地快照为静态非 git） |
| 结论 | v10.1.0 之后 main 仍仅 1 个提交 e52ec09「[docs: readme]」（2026-10-21，2 files changed）——文档类不适用，不移植，基线不变（与演练 1/2 结论一致） |
| 版本状态 | 本项目 0.6.0 基于上游 v10.1.0 静态快照完成结构与逻辑对齐（审计 16 项发现全部收口；矩阵台账 + gap-matrix SLA-001~016 行为通过） |
| 后续检查 | 维持每月 compare；上游发布 v10.2+ 或出现源码提交时：优先跑 tools/alignment/check.py + 两个 meta diff 测试，数据正确性类提交必须复现并优先评估移植 |


## 演练 4（2026-10-30，真实源码补丁移植演练——U1 验收）

**补丁**：上游 `95a6f2ad6811`（2024-02）`fix: fix executeAction may cause subscribeAction to trigger infinitely.`
（v10.1.0 基线已包含该修复；本演练按审计 §8 第 7 项要求验证"给定真实补丁可定位有限本地改动"，非首次移植。）

| 项 | 内容 |
|---|---|
| 补丁范围 | `src/Chart.ts`（+1/-1）、`src/Store.ts`（+11/-7）、`src/Event.ts`（+4/-4）——executeAction(OnCrosshairChange) 对 setCrosshair 传 `{ notExecuteAction: true }`；Store.setCrosshair 增加 options（notInvalidate/notExecuteAction）；内部调用从位置参数 `true` 改对象选项 |
| 修复语义 | 宿主手动 executeAction 设置十字线时**抑制反向广播**（避免订阅者内再触发造成无限递归）；内部触摸路径以 notInvalidate 跳过重复失效 |
| 符号映射 | Chart.ts executeAction case OnCrosshairChange → `lkanvas-compose/chart/LKanvasChart.kt#executeAction`（ON_CROSSHAIR_CHANGE 分支：写状态 + 不广播）；Store.ts setCrosshair(crosshair, options) → `lkanvas-core/common` 契约 + `compose/state/ChartState.kt#applyCrosshairAction`（写 crosshairPosition/viewRevision，无广播）；Store.ts crosshairChange 广播 → `LKanvasChart.kt#emit`（内部变化直发订阅者） |
| 移植结论 | 当前 0.6.0 代码已包含该修复语义（Phase 7/SLA-015 镜像 v10.1.0）：executeAction 抑制广播、内部 showCrosshairAt 照常广播——与补丁后行为一致 |
| 回归证据 | `Parity8ViewportEventsTest.executeAction写十字线状态且抑制反向广播`（抑制断言 + 内部广播断言 + 状态写入断言）；`LKanvasChartTest.subscribe_execute_unsubscribe事件恰好一次` |
| 残余差异 | notInvalidate 优化映射到平台失效模型（Compose viewRevision 单一失效路径，无需逐调用点跳过）——差异登记 gap-matrix（平台层） |
| 受影响符号 | 本地 3 个（LKanvasChart.executeAction/emit、ChartState.applyCrosshairAction）——补丁可定位为有限改动，无需重写数据/渲染模块（U1 达成） |
