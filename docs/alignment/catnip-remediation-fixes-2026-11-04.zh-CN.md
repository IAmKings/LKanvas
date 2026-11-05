# Catnip 降级项复核修正与验收（2026-11-04—05）

本轮按[审查报告](catnip-remediation-review-2026-11-04.zh-CN.md)修正实现与验收证据。
分支：两个仓库均为 `codex/0.16.0-remediation-review-fixes`。
比较起点：LKanvas `231d285aa2e0111b96f92835101acdf66cbc1145`；
Catnip `4e0b2c447dc4142ca9dcc1038fd0d192589678bf`。
以下为发布前验收证据；2026-11-05 后续 main 合并与 beta02 标签发布按用户指令执行。
既有 `third_party/klinechart-v10.1.0` 无 diff，模块结构与指标公式无变化。

## 已修正

| 审查项 | 修正及证据 |
|---|---|
| A1 日期 token | 使用固定上游 Period 的 SECOND–YEAR 模板与场景；公开回调可直接委托库日期工具，不再混用 Java pattern |
| A2 LINE 槽 | 按 LINE 独立计数、同类型循环、每实例从 0 开始；BAR 不消耗线色槽 |
| A3 BAR 槽 | 每元素为完整三色槽，支持多个 BAR 及循环；typed、Map、Patch、sample、宿主配置同步修正 |
| A4 源码兼容 | ChartTheme 参数移至末尾；保留公开 PaneRenderers 旧入口；旧位置及旧方法调用编译通过；二进制限制单列 |
| B1 宿主日期 | 分钟轴 DD HH:mm、日/周/月轴 MM-DD；tooltip 保留完整日期；显式 Asia/Shanghai |
| B2 增量门禁 | 实际 IndicatorEngine 路径覆盖 RESET、APPEND、REPLACE_LATEST、参数改变及短序列 RESET；与同输入 domain 对照，保留掩码与容差 |
| B3 触摸门禁 | 长按保持按下、推进超时后即断言可见，再抬指验证驻留；生产父滚动使用隔离 fixture，严格断言清除，无 assume 跳过 |
| C1 图形/图例 | 共用逐图形颜色解析；无动态回调的 BAR 采用中性色，不增加自动正负分色规则 |
| C2 格式失效 | formatter/timezone/locale/period 变更触发重绘；tooltip cache 含格式版本；真实栅格及 PNG 验证运行时切换 |
| C3 分享循环 | 有界重试，首次成功立即返回，仅未就绪重试，取消继续传播；请求绑定实际 ChartData，水印取同一输入 |
| 复查补充：部分样式 | 保留未填写颜色的数组槽，防止后续槽移位。Map 数组整体替代全局，不补入全局其他槽的颜色 |
| 复查补充：提前分享 | 按当前 uid 的内容就绪状态启用按钮；录制完成回调检查实际 candle 数量与指标就绪 |
| 设备补充：主题未消费 | 宿主把 Styles 传给组件但未桥接 ChartTheme；补齐既有 StylesThemeBridge，内容就绪含样式初始化，新增主图实际主题色像素断言 |
| 输入会话身份 | uid 纳入周期和分钟时间标签，避免相同 OHLC 在不同周期/时间下复用旧 facade；补相同 bars、不同周期/标签及稳定性测试 |
| 设备补充：缺口不可见 | 宿主 Line 描述误传二维点对，库协议要求四个字段；改为 x0/y0/x1/y1 字段排列。实例存在不等于已绘制，旧几何单测同步修正 |

## 验证结果

| 范围 | 结果 |
|---|---|
| LKanvas JVM | core **230** + compose **237**，0 失败、0 跳过；新增 UpstreamConsumptionRegressionTest 10 项 |
| Catnip JVM | app **29** + domain **39** + data **97**，0 失败、0 跳过 |
| LKanvas 设备 | **24 通过**：实例栅格 3、tooltip/formatter 4、实际手势 1、画线输入 4、字体布局 12 |
| Catnip 设备 | 真实触摸 **6 通过、0 跳过**；宿主手势 **4 通过**；GapCaptureTest **1 通过**：三 pane 像素、PNG 往返、水印和缺口处短按不抢占 |
| 独立消费 | tools/consumer-example 仅 Maven 接入，compileDebugKotlin 通过 |
| 旧源码消费者 | 旧 ChartTheme 位置参数 + figureColors 三参 + styleOverrideFor 单参编译通过 |
| 发布构建 | sample Release/R8、compose lintRelease 通过 |
| 差距矩阵 | check.py 通过；319 行。IND-014/017、CH-019/020 改回已接线，备注限定颜色和实际日期场景 |

库端 formatter 设备测试包含实际绘制与 PNG 导出回调、UTC→上海切换后的缓存图等于新绘制图，
并与切换前画面不同。未把 helper 返回值测试当作最终画面证据。

生产详情页另行人工补验：日 K、周 K 均成功生成非空 PNG（135,689 / 128,814 字节），
图形随周期改变，水印分别为日 K / 周 K、前复权、eastmoney；均打开系统分享面板，
未选择接收人或发送。验收后恢复日 K，数据库逐行对比一致，integrity_check 通过。

复查 Standards、Spec 两轴分别补出了部分样式槽移位和分享提前就绪问题；修正后窄范围复查均无新增缺陷。
设备补验又实际复现缺口模板参数不匹配及主题桥接遗漏，均在宿主根因处修正。

## 制品身份与 S5 边界

Catnip 正式依赖行仍为 `com.github.IAmKings.LKanvas:*:0.16.0-beta01`。
本地验证通过外部 Gradle init 脚本，将 compose/core 一起替换为 `build/maven-repo` 的
`io.github.iamkings:*:0.16.0-beta02`。没有把 mavenLocal 或 vendor clone 加入宿主。
独立解析检查确认本地候选只有同一 group/version 的 core，并对实际解析文件与本地仓库做 SHA-256 对照：

| 制品 | SHA-256 |
|---|---|
| compose AAR | `e53847009cd567ed8b159e12a0f2a7e389f68504434e3bf94f509cfb587181d7` |
| core JAR | `c40a11b9d39f9c7257c623ddb014fc644fb4e8d5fc882782c1e9dcaba9a1fe6e` |

**本地候选验证不能替代 S5 公开发布。** 仍需既有发布流程完成 tag/JitPack，随后提交 Catnip
正式版本行升级并复测。不能把当前仓库默认 beta01 构建称作已获得全部 beta02 修复。

## 兼容性与对齐边界

- FormatConfig、ChartTheme、PolygonStyle、PolygonStylePatch 的 data class JVM 构造器/copy
  签名变化：旧源码已验证可重编，**预编译调用方必须重编译**，未承诺二进制直接替换兼容。
- 旧 PolygonStyle 单色写法保留为每图形单色槽；旧涨/跌两元素写法必须改为一个三色元素。
- Android 无 CSS `currentColor`：Map 的缺省颜色按 Canvas 黑色处理，已明确登记为平台适配，
  不声称复刻浏览器 CSS 继承。
- MACD stroke/fill、crosshair 底部日期浮签、实例 TEXT/CIRCLE/lastValueMark/tooltip 等完整
  消费仍待后续；本轮局部颜色和日期验收不代表整个上游模块完成。

## 设备数据与测试隔离

测试 runner 改用唯一目录下的 Room 与 DataStore；生产页 fixture 不再依赖或修改用户行情。
后续设备验证使用保留应用的直接 instrumentation，避免 Gradle connected 任务自动卸载目标应用。

本轮早期 connected 测试结束卸载了目标应用。已重装并逐文件恢复测试前备份；原台账中删除的
`2025-06-10`、`2026-10-20` 两根 K 线从先前原始数据库精确补回，恢复库校验通过，未重新估算行情。
恢复后再次运行隔离测试，用户数据库与设置逐文件 SHA 校验一致。
私有备份保存在 `/Users/pauldeman/.codex/backups/catnip/2026-11-04/`，未进入 Git。

## 留档与复现

完整日志、外部候选构建脚本、解析 SHA、触摸与栅格结果、日/周分享 PNG 已留在
`/Users/pauldeman/.codex/validation/lkanvas-remediation-2026-11-05/`。
[合成缺口分享图](/Users/pauldeman/.codex/validation/lkanvas-remediation-2026-11-05/catnip-gap-capture-candidate.png)、
[实际日 K 分享图](/Users/pauldeman/.codex/validation/lkanvas-remediation-2026-11-05/catnip-production-share-final.png)、
[实际周 K 分享图](/Users/pauldeman/.codex/validation/lkanvas-remediation-2026-11-05/catnip-production-share-week-final.png)。
设备命令使用 `adb shell am instrument`，没有再次使用会自动卸载目标应用的 connected 任务。

新增观察：生产分享图的 VOL 大数值右轴在宿主固定 46dp 槽内仍出现换行。
此项属于后续量轴格式/轴宽消费审计，本轮没有改变通用轴格式；不能据此宣称画面全量对齐。

> 闭环（2026-11-06）：该观察已立项为 issue #1 并在 0.16.0-beta03 收口——
> 右轴槽宽自适应（上游 `yAxis.size:'auto'` 平台移植，R11-01，见 gap-matrix §22 与
> compatibility.md 同日条目）；`Formatter.formatBigNumber` 职责域澄清亦记录于该条目。

## 未覆盖的验收

未做第二台设备、所有周期/复权/数据源组合、设备全局时区三矩阵及完整性能复测。
未公开发布；未运行旧预编译消费者的全量二进制兼容套件。
