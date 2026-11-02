# LKanvas Sample 重设计与验收

2026-11-01 · 设计基线及 A「刻度 K」方向已由用户确认。

Sample 已改为以真实图表为主体的行情试用工作台，方便开发者评估库能力、复现交互与查看诊断。沿用 applicationId 和版本配置；不新增运行时依赖，不修改图表库公开 API、算法或默认主题契约。

## 交付

- [DESIGN.md](../../DESIGN.md)：视觉、排版、信息架构、交互与回归边界。
- [Logo 套件 ZIP](brand/lkanvas-brand-kit.zip)：符号、原创轮廓字标、横向与堆叠组合、深浅色/黑白、小尺寸、PNG、ICO、Android 图标资源和使用说明。
- [Logo 使用说明](brand/final/USAGE.md)与[品牌预览](brand/final/brand-preview.png)。Android 包含 23+ 矢量图标、26+ 自适应图标、33+ 单色图标。
- Debug APK：`app-sample/build/outputs/apk/debug/app-sample-debug.apk`。
- R8 release APK：`app-sample/build/outputs/apk/release/app-sample-release.apk`，沿用仓库配置的本地测试签名；不是生产签名发布。

## 最终界面与行为

默认石墨深色配冷蓝，完整支持浅色页面、图表、面板与系统栏。品种明确标为模拟指数；价格、开高低与成交量来自当前数据快照，变化标为“本根”，不伪装成交易所日行情。

默认显示 MA / VOL / MACD。周期栏真实切换 `1m / 5m / 15m / 1h / 1d` 的数据间隔，通过公开 `DataLoader` 接入，复用库的切换、退订与分页机制。默认 500 根，200ms 更新，每五次更新追加一根；历史最多补三页、每页 200 根。正常模式右侧留白改为 24dp，压力模式保持原有视口口径。

| 入口 | 可体验内容 |
|---|---|
| 右上主题图标 | 深浅主题统一切换 |
| 右上诊断图标 | 采集开关、数据/视口/版本、计算与渲染摘要、最近事件、刷新和日志导出 |
| 周期栏右侧箭头 | 回到最新 |
| 指标 | 从注册表列出全部 27 种；按主图与副图分类；真实开关状态；部分指标支持参数 B / 恢复默认 |
| 画线 | 直接选择 16 种内置工具及已注册宿主工具；图上取点，独立“完成”“取消”；选中后可删除 |
| 图表 | 六种主图、线性/对数/百分比轴，以及网格、十字线、Tooltip 等样式 |
| 更多 | 模拟推送暂停/继续、自定义图例、独立 BOLL pane、多轴 MA10、四种宿主图形示例、PNG 导出 |

面板统一为可滚动的模态底部面板，一次只打开一个，关闭按钮固定可达；不再展开到主页面挤压图表。切换面板时重置滚动位置。横屏合并品牌与报价，工具栏改为横向图标加文字；大字体也使用紧凑工具栏并收起辅助状态条，给三层图例保留空间。

PNG 与日志导出到应用缓存，成功/失败与路径在界面反馈，不声称保存到相册。PNG 保持 `1080×1600` 和原文件名。多轴示例只移除自己的 MA10 实例，不误删原有 MA。

静态压力负载保留 `stress_mode / stress_candles / stress_indicators / stress_overlays`、分批注入和就绪日志。修正了完整负载就绪条件：覆盖物集合不是快照状态，需观察 `overlayRevision` 后重新判断，否则静态负载已经完成仍可能一直不报告 fully drawn。新增独立回归覆盖此问题。

## 验证范围

设备为小米 23078PND5G，Android 14 / API 34；竖屏 1220×2712，横屏 2712×1220。8 项集成用例以 1.0 字体、竖屏执行，字体与方向另走矩阵验收。测试前后的字体与旋转配置均已恢复。

| 检查 | 结果与依据 |
|---|---|
| Debug、测试 APK、R8 release 构建 | 通过，[构建日志](../evidence/sample-redesign-20261101/build.log)；既有 Gradle/Kotlin/R8 配置告警保留，不属于本次 UI 改造 |
| 最终集成用例 | **8/8 通过**，[完整日志](../evidence/sample-redesign-20261101/final-tests.txt) |
| 五种周期与历史页 | 初始 500 根连续；每页 200 根、最多三页；时间间隔与周期一致；重载种子价格一致 |
| 实时生命周期 | 更新保留本根历史高低价；暂停后无推送；退订后无回调 |
| UI 操作 | 真实 5m 数据、深浅切换、添加 EMA、取消与两点画线、诊断开关及导出、面积图、百分比轴、动态 BOLL、多轴 MA10 开关 |
| PNG | UI 导出尺寸和主题像素断言；既有 Android PNG 集成验证内容及导出前后布局保持 |
| 页面生命周期 | 30 次建销，作用域取消、全局 facade 注册表回到用例基线；测试夹具同时清理前后台遗留 sample 页面，避免把旧页面误计入基线 |
| 静态压力就绪 | 5000 根 / 3 MA / 10 覆盖物，完成后无需下一次行情 tick 即报告 fully drawn |
| R8 原生交互 | **通过**，双指放大/缩小、长按、六个自定义动作可见、连续两次上一根更新摘要；[日志](../evidence/sample-redesign-20261101/native-actions-test.txt)、[动作列表](../evidence/sample-redesign-20261101/accessibility-actions.txt) |
| R8 50k 压力 smoke | **通过**，50000 根 / 10 MA / 100 覆盖物，完成注入且发出 `LKanvas_READY`；[记录](../evidence/sample-redesign-20261101/release-smoke.json)、[日志](../evidence/sample-redesign-20261101/stress-50k-release.log)。这是功能 smoke，不作为性能认证 |
| 字体与方向 | 1.0 / 1.3 / 2.0 × 横竖屏，校验实际 PNG 方向、图表边界及四个工具入口；[元数据](../evidence/sample-redesign-20261101/layout-matrix.json) |
| 色彩与 Logo | 两套配色的 60 组正文/表面与两组主按钮组合达到 4.5:1；SVG 静态审计、小尺寸与反白检查见[设计检查](brand/REVIEW.md) |

测试代码分别为 `LocalFeedTest`、`SampleWorkspaceUiTest`、`ActivityLifecycleTest`、`PngExportTest`、`StressReadinessTest`。原生交互复用既有 `FinalAcceptanceUiTest`，避免另造手势测试工具。

## 截图与复现

- [深色首页](../evidence/sample-redesign-20261101/home-dark.png) · [浅色首页](../evidence/sample-redesign-20261101/home-light.png)
- [指标面板](../evidence/sample-redesign-20261101/indicators-dark.png) · [诊断面板](../evidence/sample-redesign-20261101/diagnostics-dark.png)
- [横屏](../evidence/sample-redesign-20261101/font-1.0-landscape.png) · [1.3 字体](../evidence/sample-redesign-20261101/font-1.3-portrait.png) · [2.0 字体](../evidence/sample-redesign-20261101/font-2.0-portrait.png)
- [双指缩放](../evidence/sample-redesign-20261101/pinch-out.png) · [长按](../evidence/sample-redesign-20261101/long-press.png) · [50k 压力模式](../evidence/sample-redesign-20261101/stress-50k-release.png)

最终设备已恢复普通模拟页面，诊断默认关闭；APK 对应的 [SHA-256](../evidence/sample-redesign-20261101/apk-sha256.txt) 已留档。

构建与指定集成用例：

```sh
./gradlew :app-sample:assembleDebug :app-sample:assembleRelease
./gradlew :app-sample:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.lkanvas.sample.LocalFeedTest,io.lkanvas.sample.SampleWorkspaceUiTest,io.lkanvas.sample.ActivityLifecycleTest,io.lkanvas.sample.PngExportTest,io.lkanvas.sample.StressReadinessTest
```

压力复现：

```sh
adb shell am start -S -W -n io.lkanvas.sample/.MainActivity \
  --ez stress_mode true --ei stress_candles 50000 \
  --ei stress_indicators 10 --ei stress_overlays 100
```

返回普通模拟模式使用相同启动命令并传 `--ez stress_mode false`。读取导出文件时，debug 包可使用 `adb exec-out run-as io.lkanvas.sample cat cache/lkanvas-diagnostics.txt`；release 包不依赖 `run-as` 做本轮测试。

## 未验证与保留事项

本轮不替代库发布验收，原 **HOLD** 结论保留。未重跑长时帧/输入延迟、retained heap、30 分钟热稳定或 Maven/consumer 全链路；未进行真人 TalkBack 新流程遍历、磁盘故障注入、所有 Android 版本实机认证及商标近似检索。

原有工作区中的图表渲染、设备性能、发布配置和证据改动均保留；本次实现只涉及 sample、其测试、品牌资产及演示文档。就绪信号修复是功能正确性检查，不等同于关闭严格 warm 性能指标。
