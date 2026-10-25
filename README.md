# LKanvas

KLineChart 的 Android / Jetpack Compose 原生实现。目标是把上游
[KLineChart](https://github.com/klinecharts/KLineChart)（v10.1.0）的领域能力——指标、覆盖物、坐标轴、
数据接入模型——移植为纯 Kotlin 计算核心 + Compose 原生绘制的图表库。

> 本项目为独立维护的社区移植，不是 KLineChart 官方 Android 版本。

## 模块

| 模块 | 说明 |
|---|---|
| `lkanvas-core` | 纯 Kotlin/JVM 库：数据模型与校验、串行数据变更、会话/版本门禁、指标引擎、视口与坐标、覆盖物领域状态、存档 DTO |
| `lkanvas-compose` | Android Compose 库：`KLineChart` 组件、Canvas 绘制、手势、无障碍语义 |
| `app-sample` | 示例应用：本地确定性数据源、实时更新、指标与画线演示 |

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
