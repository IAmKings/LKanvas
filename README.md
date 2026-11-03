# LKanvas

[![Maven Central](https://img.shields.io/maven-central/v/io.github.iamkings/lkanvas-compose)](https://central.sonatype.com/artifact/io.github.iamkings/lkanvas-compose)
[![JitPack](https://jitpack.io/v/IAmKings/LKanvas.svg)](https://jitpack.io/#IAmKings/LKanvas/0.16.0-beta01)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

**LKanvas** 是 [KLineChart](https://github.com/klinecharts/KLineChart)（v10.1.0）的 Android / Jetpack Compose 原生实现——纯 Kotlin 计算核心 + Compose Canvas 绘制的 K 线图表库，指标、覆盖物、坐标轴与数据接入模型与上游同名 API 对齐，WebView 迁移者可按上游文档习惯直接调用。

> 本项目为独立维护的社区移植，不是 KLineChart 官方 Android 版本。
> 当前处于 **0.16.0-beta01 内测**阶段，API 仍可能随 rc 版本调整；发布决策与已知缺口见[发布决策文档](docs/alignment/release-decision-go-2026-11-04.zh-CN.md)。

## 特性

- **27 种内置指标**：全量/增量双路径，与上游 TS oracle 逐位对照（值与 Missing 位置一致）
- **16 种覆盖物（画线工具）**：统一生命周期状态机，命中、编辑与磁吸，brush 5000 点上限
- **六种主图类型**：candle_solid / candle_hollow / candle_up_down / ohlc / area 等上游同名类型与逐根配色
- **坐标轴完整版**：normal / logarithm / percentage / 自定义变换轴；多轴（独立列、inside、LEFT 位置、reverse、删除保护）
- **多窗格**：主图 / 副图 / 动态指标 pane，独立渲染与值域、分隔条拖动、排序，pane 配置入存档
- **数据接入**：DataLoader 契约（init / forward / backward + subscribeBar）、双向分页、请求代次与会话门禁（切品种不串数据）
- **手势与交互**：平移、双指缩放、长按十字线、点击回调、平台 OverScroller 惯性曲线、pane 分隔条与轴区缩放
- **样式体系**：样式注册表、全字段 Styles 与全可空局部补丁 StylesPatch、明暗主题、格式化链（精度/千分位/折叠）
- **导出与诊断**：PNG 等比三窗格导出、getConvertPictureUrl、可开关零开销诊断收集器
- **无障碍**：TalkBack 摘要（OHLCV + 方向词）与自定义无障碍动作
- **存档**：版本化 schema 导出/恢复，恢复门禁防空状态覆盖

## 模块

| 模块 | 说明 |
|---|---|
| `lkanvas-core` | 纯 Kotlin/JVM 领域库：数据模型与校验、串行数据变更、指标引擎、坐标换算、覆盖物状态，不依赖任何 Android/Compose 类型 |
| `lkanvas-compose` | Android Compose 组件库：`KLineChart` 组件、Canvas 渲染管线、手势状态机、导出 |
| `app-sample` | 示例应用：行情试用工作台，覆盖库全部能力 |

## 接入

要求：**minSdk 23**、JVM 17 字节码；Compose 集成（验证组合：Compose BOM 2026.06.01 + Kotlin 2.4.x）。

**Maven Central（推荐，无需额外仓库）**

```kotlin
dependencies {
    implementation("io.github.iamkings:lkanvas-compose:0.16.0-beta01") // AAR，自动传递 lkanvas-core
}
```

**JitPack（备用，零凭据）**

```kotlin
// settings.gradle.kts
repositories { maven("https://jitpack.io") }

// build.gradle.kts
dependencies {
    implementation("com.github.IAmKings.LKanvas:lkanvas-compose:0.16.0-beta01")
}
```

**GitHub Packages（封闭内测，需 `read:packages` PAT）**

```kotlin
// settings.gradle.kts
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/IAmKings/LKanvas")
        credentials {
            username = "<GitHub 用户名>"
            password = "<read:packages PAT>"
        }
    }
}
// 依赖坐标同 Maven Central
```

> **R8 提示**：AAR 含 Kotlin 2.4 元数据，宿主开启 minify 建议 AGP ≥ 8.13.20；旧 AGP 可参考本项目 `settings.gradle.kts` 的 R8 9.1.29 注入方式。

## 快速上手

从 KLineChart (WebView / JS) 迁移：API 与上游同名同语义，仅初始化方式不同——

```kotlin
// TS: const chart = init('container', { locale: 'zh-CN' })
val chart = LKanvasChart.init()                    // 门面持有内核与 Compose 状态

// TS: chart.setDataLoader({ getBars({type, timestamp, callback}) {...} })
chart.setDataLoader(object : DataLoader {
    override fun getBars(params: DataLoader.GetBarsParams) { /* init/forward/backward 分页 */ }
    override fun subscribeBar(params: DataLoader.SubscribeBarParams) { /* 实时推送 */ }
})

// TS: chart.createIndicator({ name: 'MA', calcParams: [5] })
chart.createIndicator("MA", calcParams = listOf(5.0))

// TS: chart.createOverlay({ name: 'segment', points: [...] })
chart.createOverlay(OverlayCreate("segment", points = listOf(...)))

// TS: chart.setStyles('dark') / subscribeAction('onCrosshairChange', cb)
chart.setStyles("dark")
chart.subscribeAction(ActionType.ON_CROSSHAIR_CHANGE) { }
```

把 `chart.state` 交给 Compose 组件 `KLineChart(state)` 即可渲染；创建指标/覆盖物前无需预置（严格上游默认语义，差异登记见兼容台账）。

## 文档

| 文档 | 内容 |
|---|---|
| [DESIGN.md](DESIGN.md) | 设计规范与品牌 |
| [docs/alignment/gap-matrix.md](docs/alignment/gap-matrix.md) | 上游 API 对齐矩阵（257 行，未对齐项 E=0） |
| [docs/compatibility.md](docs/compatibility.md) | 兼容台账：有意差异登记、指标/覆盖物清单、版本变更记录 |
| [docs/performance-baseline.md](docs/performance-baseline.md) | 性能门槛与真机实测基线 |
| [docs/PRD-KLineChart-Kotlin-Compose.zh-CN.md](docs/PRD-KLineChart-Kotlin-Compose.zh-CN.md) | 产品需求文档 |
| [docs/alignment/release-decision-go-2026-11-04.zh-CN.md](docs/alignment/release-decision-go-2026-11-04.zh-CN.md) | 发布决策与内测范围 |
| [docs/design/brand/final/USAGE.md](docs/design/brand/final/USAGE.md) | Logo 与品牌使用说明 |

## 示例应用

`app-sample` 是行情试用工作台（本地模拟行情，不联网）：深浅主题、`1m ~ 1d` 周期切换、27 指标、画线工具、图型/轴切换、PNG 与诊断导出。

**免构建试用**：从 [Releases](https://github.com/IAmKings/LKanvas/releases) 下载 `LKanvas-sample-<version>.apk`（R8 release 构建、debug 签名，允许「安装未知来源应用」后直接侧载；每次推 `v*` tag 由 CI 自动构建发布）。

本地构建运行：

```bash
./gradlew :app-sample:installDebug
```

## 构建

```bash
./gradlew :lkanvas-core:test          # 纯 JVM 测试（数据/指标/坐标）
./gradlew :app-sample:assembleDebug   # 构建示例应用
./gradlew publish                     # 本地 Maven 制品 → build/maven-repo
```

要求：JDK 21、Android SDK（compileSdk 36，minSdk 23）。

## 反馈

内测期问题请提 [issue](https://github.com/IAmKings/LKanvas/issues)，附环境信息与 `LKanvasChart.version()` 返回值；模板见 `.github/ISSUE_TEMPLATE/`：

- **缺陷反馈**（bug_report）：库或示例的缺陷
- **上游兼容性反馈**（compatibility_report）：与上游 KLineChart 行为不一致

## 上游基线

上游版本：KLineChart **v10.1.0**（快照见 `third_party/klinechart-v10.1.0/`，仅用于开发期行为对照，不进入发布制品）。

## 许可证

[Apache License 2.0](LICENSE)。派生自 KLineChart 的算法与几何实现见 [NOTICE](NOTICE) 与[兼容台账](docs/compatibility.md)的来源映射。
