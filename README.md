# LKanvas

KLineChart 的 Android / Jetpack Compose 原生实现。目标是把上游
[KLineChart](https://github.com/klinecharts/KLineChart)（v10.1.0）的领域能力——指标、覆盖物、坐标轴、
数据接入模型——移植为纯 Kotlin 计算核心 + Compose 原生绘制的图表库。

> 本项目为独立维护的社区移植，不是 KLineChart 官方 Android 版本。

## 模块

| 模块 | 说明 |
|---|---|
| `lkanvas-core` | 纯 Kotlin/JVM 库：数据模型与校验、串行数据变更、会话/版本门禁、27 种内置指标（全量与增量）、三种纵轴+自定义轴、视口坐标、覆盖物领域状态、DataSource 契约 |
| `lkanvas-compose` | Android Compose 库：`KLineChart` 组件（六种主图类型、三 pane、明暗主题）、Canvas 绘制、手势状态机、segment 画线 |
| `app-sample` | 示例应用：本地确定性行情、实时末根更新、分页、画线、图型/轴切换 |

## 当前进度（M0–M3 完成）

- **27/27 内置指标**：全部与上游 v10.1.0 TS oracle 逐位对照通过（值与 Missing 位置），附手算样例与增量/全量一致性测试
- **六种主图类型**：candle_solid / candle_stroke / candle_up_stroke / candle_down_stroke / ohlc / area
- **三种纵轴 + 自定义**：normal / logarithm（带符号 log）/ percentage（零基准按 D-01）
- **数据链路**：Reset/Replace/Upsert/Append/Prepend/PatchHistory、会话门禁、分页加载状态机
- **交互**：平移/双指缩放/长按十字线/点击/基础惯性；segment 画线（创建/拖点/整体拖/锁定/删除）
- 待 M4–M6：其余 15 种覆盖物、存档恢复、无障碍、性能冻结、Maven 发布（见兼容台账）

## 上游基线

- 上游版本：KLineChart **v10.1.0**（快照见 `third_party/klinechart-v10.1.0/`，仅用于开发期 TS 对照 oracle，不进入发布制品）。
- 功能对齐范围、差异登记与验收口径见 [PRD](docs/PRD-KLineChart-Kotlin-Compose.zh-CN.md) 与
  [兼容台账](docs/compatibility.md)。

## 构建

```bash
./gradlew :lkanvas-core:test          # 纯 JVM 测试（数据/指标/坐标）
./gradlew :app-sample:assembleDebug   # 构建示例应用
```

要求：JDK 21、Android SDK（compileSdk 36，minSdk 23）。

## 许可证

本项目基于 Apache License 2.0 发布；派生自 KLineChart 的算法与几何实现见 `NOTICE` 与兼容台账中的来源映射。
