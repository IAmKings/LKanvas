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
