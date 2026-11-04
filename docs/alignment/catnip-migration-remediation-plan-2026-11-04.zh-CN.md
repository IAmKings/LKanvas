# Catnip 迁移降级项复核与执行计划（2026-11-04）

> 本文是原始执行计划；后续实施与验收见 [修正验收记录](catnip-remediation-fixes-2026-11-04.zh-CN.md)，原状态不作为当前完成声明。

状态：**分析和计划，尚未实施修复**。本文基于两个仓库的当前源码、Catnip 实际解析的制品和定向数值实验。原迁移记录、原降级报告作为待核对的证据输入；其中“提 issue”“只能等上游”等建议不视为执行指令。

## 1. 结论与边界

可以在保持 **KLineChart v10.1.0 固定基线、core/compose/sample 模块结构、内置指标计算口径**的前提下完成本轮修复。处理分三处落地：

1. **Catnip 宿主接入**：补 Splash 释放、显式上海时区、网格和 pane 配置、恢复真实触摸测试、补数值校验与分享验证。
2. **LKanvas 已有契约补齐**：修通指标实例样式与日期 formatter 的实际消费。它们已有公开入口，当前实现没有兑现全部配置效果。
3. **Catnip 业务扩展**：交易日历继续由宿主解释，缺口标记复用自定义 overlay；LKanvas 负责坐标变换、裁剪和绘制。

原报告中的两个核心归因需要修正：**beta01 已支持十字线松手驻留，也已有 `setTimezone()`**。当前证据不足以再立“新增 sticky 模式”“新增时区 API”两个开发项。同时发现原报告低估的样式缺陷：**实例样式未被绘制消费，VOL/MACD 的柱色硬编码会覆盖 A 股配色**。[L1][L2][L3][L4]

“基线不变”在本计划中的含义：固定上游源码快照、oracle 输入和已通过的数据/公式/手势契约；把实现缺陷修回现有上游契约是允许的。对已经错误的配置效果建立失败用例后修复，并记录受影响画面，不能通过改 oracle 或扩大容差掩盖问题。Catnip 的业务风格使用显式配置，不写成库的全局默认。

本轮不新增依赖、不恢复旧渲染器、不复制内置 MA/MACD 算法到宿主、不移动模块，也不修改已发布 beta01 的 tag 或覆盖同版本制品。

## 2. 版本和证据可信度

| 对象 | 本次核对结果 |
|---|---|
| 原报告 | 对照旧宿主 `46d114d`、宿主 `9553a42`，声称逐文件核对 `v0.16.0-beta01` |
| 当前 Catnip | `37cd8a9`；该提交添加报告，适配代码仍是报告所述路径；工作区无改动 |
| 当前 LKanvas | `581d2bf4769298de919faaa961e072829fd07de9` |
| 本地两个发布 tag | `v0.16.0-beta01^{}` = `77f0df5…`；`0.16.0-beta01^{}` = `fca7692…`。两个 tag 和当前 HEAD 的 core/compose 生产源码无差异；不能把附注 tag 对象 SHA 当成源码提交 SHA |
| 宿主实际依赖图 | 离线 `dependencyInsight` 确认 compose/core 均为 `com.github.IAmKings.LKanvas:*:0.16.0-beta01`，没有解析到旧 rc 制品 |
| 实际 AAR | SHA-256 `cbfca0823bd1f2fa9174792e10e5bd87f4e5724cbd2eccf7e3488b5d5acdbdfd`，666,977 字节；与本地发布 AAR 字节一致 |
| 实际 core JAR | SHA-256 `da4ba1983410201bc64ecbc62f6efc5c48b858262a08710d26cdedd12889bfe6`；与本地发布 JAR 的 280 个条目逐项内容一致。外层 ZIP 校验和不同，未发现 class 内容差异 |
| 二进制抽查 | 对消费 AAR 解包后 `javap`，确认 `setTimezone`、`setFormatter` 与当前手势实现存在 |

以上核实了**本机 Gradle 解析结果**；没有证明用户设备当前安装的 APK 就是这一份。若真实体验与已解析源码冲突，首先核对安装包构建来源、版本和会话重建，再定位手势。

本次已执行的检查：

- Catnip `:app:dependencyInsight --dependency lkanvas --configuration debugRuntimeClasspath :core-domain:jar --offline` 通过。
- 使用当前宿主 domain JAR 和实际消费 core JAR，临时 Java 自检运行 **204 组、165,672 次逐点比较**，覆盖 MA5/10/20、MACD(12,26,9)/(5,10,3)；同时比较模板 `calc` 和 `IndicatorEngine.recompute` 输出。
- 输入为常数、递增、递减、振荡四族，长度为 `0/1/4/5/9/10/11/12/19/20/25/26/32/33/34/60/2000`；缺失掩码一致，最大绝对误差 `1.1795009413617663e-11`，检查阈值 `1e-9 + 1e-12 × max(|a|, |b|)`。
- 同一上海零点 epoch `1793635200000` 在上海日期为 `2026-11-03`，在洛杉矶为 `2026-11-02`，验证风险机制。
- 通过 GitHub 连接器读取发布 tag 的手势源码，确认与本地结论一致；同时核对 KLineChart 官方 10.1.0 文档的时区、formatter 与 overlay 契约。[W1][W2][W3][W4]

临时自检尚未成为仓库 CI 测试，未覆盖增量更新、真实行情快照、APK 安装和触摸链路；下文将这些列为执行门禁。

## 3. 原报告 16 项逐项处置

| # | 复核结论 | 处理归属与动作 | 本轮门禁 |
|---|---|---|---|
| 1 Splash 断链 | **确认宿主回归，P0**。当前唯一 `release()` 调用仍在 2.5 秒兜底 | Catnip：接回现有 `onContentVisible` 消费链；补正常就绪与超时路径测试 | 必修 |
| 2 十字线不驻留 | **原报告根因与 beta01 源码冲突**。Release 不隐藏，短按也可显示 | 先核对安装包和真实触摸；检查分隔区、父容器接管、数据 RESET、`chart.uid`/state 重建；不预设新增 sticky API | 必须实测收口，按复现结果修 |
| 3 时区依赖设备 | **风险存在，接口已经具备，P1** | Catnip：初始化 facade 时设置 `Asia/Shanghai`，保留原 UTC epoch | 必修 |
| 4 指标双源 | **永久门禁缺失属实；本次有限样本未发现公式错误，P1** | Catnip app 测试同时调用 domain 与 core；把公式、输入范围、显示精度分开验证 | 必补 |
| 5 网格虚线 | 默认风格差异；现有配置可解决 | Catnip：横竖网格均显式 `solid`；不改库默认 | 随宿主样式修 |
| 6 DIF/DEA 配色 | 差异存在；“实例 styles 两行即生效”不成立 | LKanvas：修通实例样式；Catnip：MACD 单独设蓝 DIF / 橙 DEA，MA 保留原色 | 必修；合并柱色遗漏项 |
| 7 主图比例 | 需要按实际绘图区重测；“380dp 下约 46%”不能直接作为定论 | Catnip：测量后设副图高度，必要时分别用 pane options；大字体和横屏允许最小高度约束 | 随宿主布局修 |
| 8 图内 tooltip | **源码已可判定**：`LegendMode.HIDDEN` 阻止主/副 tooltip 整套绘制 | 保留宿主完整信息头作为首轮策略，实测确认；若产品另需浮卡，再显式启用并避免重复信息 | 必须明确策略与验收 |
| 9 最新价标签 | 偏好差异，未证实缺陷 | 保留现有标签；需要旧风格时仅改宿主 `priceMark.last` 配置 | 记录为保留项 |
| 10 时间轴格式 | **已有 formatter API 未接入绘制，真实库缺陷** | LKanvas：接通已有格式链和上下文；Catnip：配置分钟 `dd HH:mm`、日/周/月 `MM-dd` | 必修 |
| 11 日历缺口 | 宿主视觉能力未迁移；不需要库内置交易日历 | Catnip：现有 `gapAfterIndex` → 无交互自定义 overlay，详见 §7 | 本轮扩展项 |
| 12 首帧日志 | 观测缺口属实 | Catnip：在就绪链记录一次阶段耗时；区分数据就绪、首次有效绘制与启动耗时 | 与 #1 合并 |
| 13 手势 E2E | **确认测试缺口，P1**。现测试仍有未经证实的厂商拦截注释 | 恢复完整触摸会话测试；API 注入保留为另一层测试 | 必补 |
| 14 beta/热修流程 | 依赖制品是已选接入策略；beta 是版本风险，不是运行时 bug | 固定已验收版本与校验和，修复在 LKanvas 独立仓库验证并发新版本，再升级 Catnip | 流程收口 |
| 15 引擎 deep-link | 单渲染器下旧引擎参数已经失去业务作用 | 关闭此项；保留有用的证券/周期/诊断入口即可 | 无需恢复 |
| 16 分享链路 | 未验证属实；现实现还会吞掉导出异常 | 先走真实分享闭环，补失败反馈和可靠的捕获就绪条件；不重做导出系统 | 必验；失败路径一并收口 |

## 4. Catnip 宿主修复包

### 4.1 Splash 与首次有效内容

根因位于宿主生命周期，不能让通用图表库依赖 `ChartReadyBus`。`SecurityDetailScreen` 目前只执行 `chartVisible = true`；`LkanvasChart` 已在数据和三指标就绪时回调，`MainActivity` 只剩超时释放。[H1][H2][H3]

最小修改：在现有 `onVisible` 消费处同时释放 Splash，保留 2.5 秒兜底。在同一位置补一次性耗时事件，不新增启动总线。现回调是“数据与指标就绪”，不能直接把它命名为“像素已经提交”。

执行时需核对 Android Splash 的绘制拦截时序：若确需首次有效绘制信号，复用图表现有外层绘制包装，记录一次完成并在安全阶段通知；不能让“等待首帧释放 Splash”与“Splash 拦住首帧”互相等待。正常恢复要以现有数据就绪链可靠放行，画面尾部由骨架屏承接。

验收：本地已有数据的恢复页在就绪后下一次正常检查即释放，不固定等满 2.5 秒；空数据、加载失败、导航离开走明确空态/错误态或原兜底；连续两次 Activity 生命周期不出现旧会话误放行。普通首页启动和调试直达不新增等待。

### 4.2 时区和输入映射

在 facade 初始化、喂数据之前调用现有 `setTimezone("Asia/Shanghai")`。映射继续按上海交易时间转 epoch；禁止把 epoch 加减八小时来适配显示，也不改设备全局时区。[H4][L2]

宿主映射测试验证日/周/月交易日期和分钟时刻、严格递增 timestamp、分钟标签缺失/格式错误、volume/amount 的 null 与真实零区别。当前映射仅验证 `tradingDate` 非降序，分钟时刻由字符串恢复，因此需额外验证最终 timestamp，而不是只测试日期列表有序。

验收矩阵：设备时区上海 / UTC / 洛杉矶；日线、跨日分钟、周/月各有样本；时间轴、信息头、语义摘要显示同一交易日期，不改变数据数量、OHLCV 和会话键。

### 4.3 视觉、布局与 tooltip

- 网格：在既有 MaterialTheme → Styles 映射中把两个方向都设为 `solid`；当前桥接统一使用水平线 style，本需求两方向一致可直接满足。[H2][L5]
- MACD：等待 §5 的库修复后，给实例独立配置蓝/橙两条线；不要交换全局 MA palette。VOL 涨跌按 candle 的比较规则、MACD 柱按正负取色，都用宿主红涨绿跌，零值用中性色。[H5]
- 布局：先测量 `KLineChart` 的实际高度。详情页骨架容器、padding、信息头和内层固定高度会共同影响剩余绘图区，不能把卡片 380dp 当成纯图表高度。把图表高度来源统一到一处，再调整 pane。[H1][H2]
- 默认目标为主图约占绘图区 55%；若要求恢复非等高 VOL/MACD，则扣除实际时间轴/分隔线后以 `55:17:21` 归一分配。`subPaneHeight` 只能表达同高副图，精确非等高使用现有 pane options，布局就绪后以 px 设置，避免重复设置覆盖用户拖拽。[L6]
- 信息策略首轮保留宿主 `ChartInfoHeader` + 内置 tooltip 隐藏；十字线、轴上数值标签、头部 OHLC/MA 跟随一起验收。不能期望仅改 `showRule` 就绕过 `LegendMode.HIDDEN`。[H6][L7]
- 最新价标签保留。字体 1.0/1.3/2.0、横竖屏和窄宽度下验证数字不覆盖绘图区、图表整体不越界；空间不足时服从库已有最小布局规则，不强保百分比。

### 4.4 真实触摸与会话排查

beta01 当前交互是短按/长按显示、松手保持、触点近处继续检查、远处按下关闭。它与旧宿主“任意单击清除”并非完全相同，验收应采用当前 LKanvas 契约，不为旧习惯覆盖库手势。[L1]

现有仪表测试通过 `executeAction` 验证头部和语义，不足以证明触摸有效。新增完整 `down → 超时/移动 → up` 的触摸用例；长按等待需要推进实际事件时间，不能把单次 shell `motionevent` 当成一次完整手势。[H7]

至少覆盖：

1. 主图空白处短按松手后可见，头部索引对应落点；长按松手后仍可见。
2. 从驻留点附近拖动，头部与图内线同步；远处触摸后关闭。
3. VOL/MACD 中心触摸、横向平移、双指缩放、父列表纵向滚动。
4. 分隔边界两侧落点：当前热区为 **±14px**，不是报告写的 ±14dp；在该区分隔拖动优先。不能仅以边界长按没出现十字线判定全局驻留失败。
5. 普通重组不丢十字线；真正换数据会话/周期按当前约定清除。若复现瞬时消失，记录 `chart.uid`、sessionId、INIT/RESET 次数和父容器消费，先修多余重载/状态重建。

只有在实际 beta01、正确坐标、稳定数据会话下仍复现库状态机缺陷，才追加一个带失败用例的库 bugfix；不预先添加 sticky 开关或宿主第二套 pointerInput。

## 5. LKanvas 修复包 A：指标样式完整消费

### 5.1 已证实的问题

`createIndicator(styles)` / `overrideIndicator(styles)` 把对象保存到实例，但 `PaneRenderers` 与 tooltip 没有读取该实例样式；图形颜色来自全局 theme。内置 VOL/MACD 的 `PlotDef.styles` 又直接返回固定红/绿颜色，覆盖宿主主题。[L3][L4]

当前模型也不完整：`IndicatorStyle.bars/circles` 使用的 `PolygonStyle` 只有单色；上游指标柱模型有 `upColor/downColor/noChangeColor`。只补一行读取 `inst.styles` 不能完整解决问题。[L8]

### 5.2 实现范围

在现有 styles/figure 管线内完成，计算路径保持原样：

1. 复用已有 `IndicatorStyle` / `IndicatorStylePatch.applyTo`，规范 `styles: Any?` 支持的对象形态与验证；无法识别的形态按现有兼容策略处理并测试，避免未经校验的强转。
2. 补齐指标柱/圆的三色表达。保留当前 `PolygonStyle` 调用和公开参数类型的可用性；优先在指标样式边界增加兼容表示并归一化，不直接替换已有 `List<PolygonStyle>` 使消费方失编。**三色模型、默认回退和旧单色写法的解释须在这一个修复包中写成契约及用例，不能留给 Catnip 猜测。**
3. 建一处有效图形样式解析，按上游 `全局样式 → 实例覆盖 → figure 动态覆盖`，LINE/BAR 分类型取对应样式槽。图形绘制和 legend 共用结果，禁止按 MA/VOL/MACD 名称在 renderer 中打补丁。[U1]
4. 现有 `PlotDef.styles` 支持 Map 与旧 Function1/2/4；保留这些调用。为内置动态样式提供包含已解析三色与前/当前结果的上下文，使用 core 类型或中性值，避免 core 依赖 Compose。[L4]
5. VOL/MACD 通过上下文读取有效三色；未配置时保持既定默认，显式 A 股设置时生效。MACD 数值、EMA 种子、预热、增量计算全部不动。[U2][U3]
6. 同步主图、副图、动态 pane、tooltip、导出路径；样式覆盖变化要触发重绘和 tooltip 缓存失效。当前 cache key 没有实例 styles，不能只修解析而让旧 legend 继续回放。[L7]

不应把“末尾新增 Kotlin 默认参数”直接称为二进制兼容；构造器和 `copy` 的 JVM 签名仍可能改变。保留既有公开调用入口，并用独立消费工程验证源码和已编译调用；如确需 ABI 改动，必须登记为新预发布版本的兼容变化。[L8]

### 5.3 验收

- 未传实例样式时，已有内置 MA/VOL/MACD 的默认颜色和已通过的计算结果保持。
- MACD DIF 蓝、DEA 橙，MA5 橙、MA10 蓝；两个 MACD 实例互不污染。
- VOL 涨/跌/平、MACD 正/负/零按配置生效；选择比较规则的 fixture 与上游一致。
- 每个 legend 值的颜色等于对应图形，override 后无需重建图表立即更新，截图与 PNG 同步。
- 旧样式对象、旧自定义回调仍可用；混合 LINE/BAR 和多 pane 的取色顺序有行为测试。

额外发现：上游 MACD 动态样式还按前/当前值选择 stroke/fill，当前库 BAR 管线固定填充。这是另一个既有对齐缺口，不是 Catnip 本次换色所必需。本轮优先修颜色与实例覆盖，登记描边差异供后续修复；不能据此宣称整个指标视觉面已完全对齐。

## 6. LKanvas 修复包 B：日期 formatter 接线

### 6.1 已证实的问题

门面已有 `Formatter.formatDate(timestamp, template, type)`；`syncFormatConfig()` 把它转成两参数函数，`type` 恒为 null。时间轴、tooltip 和语义仍直接调用 `ChartText`，因此配了 formatter 也不能改变时间轴输出。[L2][L9]

上游同一 formatter 会收到 `xAxis` / `tooltip` / `crosshair` 场景，时区通过当前格式环境体现。该修复属于兑现现有上游契约，不需要新增一套 A 股时间轴 API。[W2][U4]

### 6.2 实现范围

1. 保留公开 `Formatter` 签名，在内部传递格式化场景，接入真实日期消费点。未设自定义 formatter 的分支继续使用当前默认标签规则，保持已有上下文缓存。
2. 统一使用 `effectiveTimeZone()`；补 `legendData()` 默认时间文案遗漏有效时区的问题。Catnip 目前用自己的 `fullLabelAt()`，该遗漏暂未影响其头部，但会影响其他消费者。[L9]
3. formatter、timezone、locale、period 更新使相关标签/tooltip 缓存和绘制失效；不要只验证 `getFormatter()` 返回了对象。
4. Catnip 用其 `ChartPeriod` 选择显示模板：分钟 `DD HH:mm` 对应旧 Java 格式 `dd HH:mm`，日/周/月 `MM-DD` 对应 `MM-dd`。模板 token 以库的既定规则解释，不把 Java 和库 token 混用。
5. 宿主 formatter 按上海时区实际格式化 UTC timestamp；库端不硬编码交易所和市场。保留数字 formatter、千分位和 decimalFold 的现有行为。

当前 `drawCrosshairLabels` 仅绘制右侧值标签，没有 x 轴时间浮签。接通 formatter 不会自动补出这个视觉元素。首轮继续由宿主信息头展示完整选中时间；如需恢复底部时间浮签，作为独立上游对齐项，按上游 crosshair.vertical.text 样式、边界和导出规则实现，使用同一格式入口，单独增加渲染测试。[L10][U4]

### 6.3 验收

- 实际画出的时间轴文字来自回调，回调收到正确 `xAxis` 场景；tooltip 在启用状态下收到 `tooltip` 场景。
- 清除 formatter 后恢复默认；运行时更换 formatter、时区、周期后不残留旧文本；跨日和标签避让依旧正确。
- 同时放两个不同时区/formatter 的图表无串扰；导出采用同一格式环境；未绘制的 crosshair 日期浮签不得虚报已验收。
- 补默认规则回归、回调调用上下文测试与真实绘制/导出证据，不能仅测状态字段。

## 7. Catnip 扩展包：交易日历缺口

### 7.1 业务含义保持

当前 `gapAfterIndex` 的含义是：相邻两根之间缺少了日历认定应有交易的日期。周末和普通休市节假日本身不会被标记；ViewModel 只在日 K 加载交易日历，分钟/周/月不做这一缺口判断。报告将“停牌/节假日提示”混称，需要修正文案，先按现有业务定义恢复图形。[H8]

数据源没有返回、停牌、下载覆盖不完整在现有集合中未必能细分，因此文案保持“数据缺口”，不自动断言为停牌。不伪造 candle，不改变等距时间轴，不根据自然日差大于一天直接画线。

### 7.2 最小实现

复用 `registerOverlay(OverlayDefinition)` 与编程创建入口，在 Catnip 适配目录放一个只显示的模板：[L11][W3]

- 两个控制点锚定缺口两侧的**真实 candle timestamp**；回调使用两点 pixel x 的均值。不要把 timestamp 中点作为单锚点，普通 overlay 会取最近 candle，导致标记贴在柱子上。
- 模板按当前 pane 的 boundingHeight 绘制，main/VOL/MACD 分别创建，库负责 pane 裁剪。没有有效数值映射的 pane 暂不绘制并测试，例如 MACD 未预热时。
- 关闭默认控制点、默认 X/Y 轴图形、磁吸和整体拖动，设置 `checkEventOn=false`，实例 `lock=true`。仅 lock 不足以保证不抢手势。
- 统一 groupId，在 symbol/period/数据会话更换时只替换这组派生标记，不删除用户画线；保留全局注册模板的幂等性。
- 当前原语 Line 不消费虚线 pathEffect。首轮模板按像素高生成有限短线段实现虚线；只处理屏内/边缘有关的 gap，避免对 2,000 根中的所有不可见 gap 生成大量线段。滚动/缩放/布局更新共用库坐标，不写第二套手势或时间轴算法。
- 派生标记从 `gapAfterIndex` 重建，不作为用户手绘作品持久化。若宿主将来导出 archive，要过滤该派生组，避免三 pane 标记占用库的 500 个覆盖物存档配额。[L12]

若性能实测证明短线段开销不可接受，再补通用 Line dash 消费；不能仅因为这一个业务需求，先引入交易日历服务、新渲染模块或 overlay 框架。

验收：正常周末/节假日不误标、真实交易日缺失有标记；日 K 换复权/数据源后正确重建，分钟午休不误标；平移/缩放后位于两根之间；标记处触摸仍进入图表手势；分享图包含标记且不覆盖标签。

## 8. 数值、分享与制品门禁

### 8.1 把临时 parity 实验变为永久测试

测试放在 **Catnip app 的 JVM 测试层**：它已经同时依赖 domain 和 compose/core，可直接比较，不必为了测试给纯业务 domain 生产模块添加图表依赖。MA/EMA 的不同浮点运算顺序会产生极小误差，本次已经观察到，因此不能要求 Double 位级相等。[H9][L13]

永久门禁包括：

- 相同 candle 序列和参数：长度、timestamp 对应、null/NaN 缺失掩码逐点一致；有效值按 §2 的绝对+相对误差比较，固定用例保留手算结果。
- MA5/10/20，MACD 的 DIF index25 起有效、DEA/柱 index33 起有效（默认参数）；常数、单调、跳变、短序列、2,000 根、真实脱敏快照。
- `append`、`replace latest`、RESET 和参数改变后，库的增量/全量与同输入 domain 输出一致；VOL 的缺失量与真实零独立测试，不把缺失补成成交量 0。
- 输入元信息：品种、数据源、复权、周期、时间范围、数量与序列指纹相同后才执行严格数值比较。ViewModel 截取最近 2,000 根；分析仓库读取全量日线，分钟图/周月图也不等于日线分析序列。[H10]

EMA 的历史种子会传播，同一日期但不同输入起点的结果不能强行要求完全相等。首轮维持现有 2,000 根显示上限和分析语义，测试显式区分“同输入算法一致”和“不同输入不可直接比较”。如产品之后要求全历史分析与截断图表的所有历史值完全一致，需要另立宿主数据范围/预热契约；固定长度额外预热只能近似收敛，不能承诺精确一致。不要修改库 MACD 公式去迁就输入不同。

### 8.2 分享闭环

现捕获层挂在实际图表 Modifier，宿主信息头在层外。它与“整张行情卡”截图不是同一个产物，需要在验收中确认既定范围。现路径在请求后只等一帧，导出异常被 `runCatching` 吞掉；benchmark 等两帧也不能替代生产闭环。[H1][H2][H11]

执行顺序：先验证已有数据详情页 → 分享 → 非空 PNG → 正确水印 → 系统分享面板；再按结果收敛就绪条件。捕获必须绑定正确 `chart.uid`、有效尺寸和已录制内容，快速周期切换时不得把旧图配上新水印；失败显示可理解的反馈并恢复按钮。保留现有 FileProvider 临时 URI 授权。

至少验收浅/深色、两个周期、缺口图和一次未就绪/异常；确认图形、日期、复权、数据源、水印对应同一份输入；不新增第二套截图实现。

### 8.3 发布与回滚

发布库修复为新预发布版本（建议下一可用 beta 版本，执行时核对是否已被占用），保留 beta01。源代码、测试与版号在 LKanvas 仓库完成；Catnip 继续只消费正式制品，开发期可以用独立消费工程验证候选，不恢复 vendor clone。

坐标继续使用已经解析验证的渠道；如果同时迁移 Central 坐标，独立提交并对公共仓库重新解析。测试 core 和生产 compose 必须来自同一 group/version，避免 JitPack 与 Central 两份 core 同时进 classpath。固定版本、依赖校验和、tag 对应提交与 AAR/JAR 身份，比“等到 rc 再锁 tag”更有操作性。

库格式/样式修复、宿主接线、业务 overlay 分开提交；Catnip 升级有单独提交，回滚可还原依赖与绑定配置。不能静默回退到错误时区；紧急降级只针对非关键业务视觉扩展。当前发布已是 GO，历史 HOLD 不应被本计划重新解释为当前发布禁令。[L14]

## 9. 执行次序与交付

估算为单人约 **6–9 人日**，前提是构建缓存、公共制品渠道和测试设备可用；这是按范围估算，非完成日期承诺。

| 阶段 | 工作与产物 | 依赖 | 估算 |
|---|---|---|---|
| S0 复现基线 | 固定 APK/依赖身份；跑宿主真实短按/长按，保存当前画面、时区复现和 Splash 时序；修订错误归因记录 | 无；本次源码/制品检查可复用 | 0.5 日 |
| S1 宿主接入 | #1/#3/#5、信息展示策略、pane 高度、一次性日志；永久 parity 与真实触摸测试；分享失败反馈及验证 | S0；不等待新库发版 | 1–1.5 日 |
| S2 库样式消费 | 实例 styles、三色契约、旧回调兼容、图形/legend/导出同源；补失败→通过证据 | S0；使用固定上游基线 | 1.5–2.5 日 |
| S3 库格式消费 | 已有 Formatter 接线、场景、有效时区、重绘/缓存失效；补真实渲染回归 | S0；可与 S2 分开提交 | 1–1.5 日 |
| S4 业务缺口 | 宿主只读 overlay、可见范围控制、会话清理、共享截图验收 | S1；不依赖新日历 API | 1 日 |
| S5 制品集成 | 新版本独立 consumer；Catnip 升级；双机关键交互、字体/方向/时区和分享复测；更新两项目台账 | S1–S4 | 1–2 日 |

每个修复包都需包含调用处、测试、示例/文档与兼容说明。库修复触及的主要链路为：

| 修复包 | 必须同步覆盖的代码面 |
|---|---|
| 宿主接入 | 详情页、适配器及映射、就绪总线调用处、现有主题、仪表宿主和测试；不改业务分析算法 |
| 指标样式 | facade/state 的实例覆盖、Styles/Patch 归一化、figure 动态样式上下文、PaneRenderers、TooltipRenderer、缓存、PNG 与独立消费工程 |
| 日期格式 | facade 的 Formatter 同步、内部 FormatConfig、ChartText/时间轴/tooltip/legend 默认时间/语义、失效机制、导出与回归测试 |
| 日历 overlay | 宿主模板注册和 group 生命周期、两个真实锚点映射、pane 坐标与可见范围、分享路径 |

最终退出标准：

1. #1/#3 与两条库配置链有可重现的前后对照；#2 使用正确制品的真实触摸验收，不再仅靠 API 注入或厂商归因。
2. 永久 parity、库 core/compose 回归、对齐矩阵检查和独立 consumer 编译通过；有公开制品解析与校验和证据。
3. 宿主 MA 与 MACD 颜色独立，A 股柱色生效；分钟日期明确；正常尺寸下 pane 比例符合约定，极端字体/横屏有记录的退化策略。
4. 缺口标记按交易日历语义出现，手势和数值不受影响；分享图片含正确图形与水印，失败有反馈。
5. 更新 Catnip 迁移/降级记录和 LKanvas 兼容台账，将“配置已声明”“绘制已消费”“设备行为已验证”分别记录；没有实测的项目保留待验证状态。

建议执行命令（在各自仓库运行；设备测试先按目标序列筛选，避免无关长测）：

```bash
# LKanvas
./gradlew :lkanvas-core:test :lkanvas-compose:test :lkanvas-compose:assembleRelease
python3 tools/alignment/check.py
# 独立 consumer 使用新版本制品编译；app-sample 做样式/formatter/导出的 Android 回归

# Catnip
./gradlew :app:testDebugUnitTest :core-domain:test :core-data:testDebugUnitTest
./gradlew :app:compileDebugKotlin
./gradlew :app:connectedDebugAndroidTest
# 用真实应用启动流程补测 Splash/生产分享；现有 ChartTestRunner 不是生产 Application
```

性能只对新增热点做有针对性的比较：格式回调/缓存、复杂指标样式和屏内缺口密集场景；沿用既有基线和设备规则。本轮不能以功能修复通过代替已有性能技术债的达标认证。

## 10. 证据索引

下列本地链接以本次 HEAD 的行号为准；后续实施会改变行号。固定上游文件保持在 v10.1.0 快照中。

- [原迁移记录](/Users/pauldeman/Documents/ai_workspace/Catnip/docs/LKANVAS_MIGRATION_2026-11-03.md)、[原降级报告](/Users/pauldeman/Documents/ai_workspace/Catnip/docs/DEGRADATION_ANALYSIS_2026-11-04.md)。
- [H1：详情页就绪和分享链](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/main/kotlin/com/localstock/app/ui/SecurityDetailScreen.kt:267)。
- [H2：适配器初始化、数据、就绪、外部头部和样式](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/main/kotlin/com/localstock/app/ui/chart/lkanvas/LkanvasChart.kt:41)。
- [H3：Splash hold/超时](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/main/kotlin/com/localstock/app/MainActivity.kt:52)、[就绪总线](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/main/kotlin/com/localstock/app/ui/chart/ChartReadyBus.kt:12)。
- [H4：上海时间映射](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/main/kotlin/com/localstock/app/ui/chart/lkanvas/LkanvasMapping.kt:21)。
- [H5：宿主行情语义色](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/main/kotlin/com/localstock/app/ui/theme/Quote.kt:17)。
- [H6：宿主信息头](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/main/kotlin/com/localstock/app/ui/chart/ChartInfoHeader.kt:29)。
- [H7：现有仪表测试的真实覆盖范围](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/androidTest/kotlin/com/localstock/app/ui/chart/KLineChartGestureTest.kt:137)。
- [H8：gapAfterIndex 定义](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/main/kotlin/com/localstock/app/ui/chart/ChartData.kt:32)、[仅日 K 使用交易日历](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/main/kotlin/com/localstock/app/ui/ViewModels.kt:222)。
- [H9：domain 公式与种子约定](/Users/pauldeman/Documents/ai_workspace/Catnip/core-domain/src/main/kotlin/com/localstock/domain/indicator/Indicators.kt:11)。
- [H10：图表 2,000 根截断](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/main/kotlin/com/localstock/app/ui/ViewModels.kt:201)、[分析读取完整范围](/Users/pauldeman/Documents/ai_workspace/Catnip/core-data/src/main/kotlin/com/localstock/data/repository/AnalysisRepositoryImpl.kt:69)。
- [H11：分享捕获诊断宿主](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/main/kotlin/com/localstock/app/ui/chart/ChartBenchmarkActivity.kt:57)、[当前 instrumentation runner](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/androidTest/kotlin/com/localstock/app/ChartTestRunner.kt:8)。
- [L1：手势 Release 和短按显示](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/gesture/ChartGestures.kt:391)、[驻留/历史拖动已有设备测试](/Users/pauldeman/Documents/ai_workspace/LKanvas/app-sample/src/androidTest/kotlin/io/lkanvas/sample/InteractionAlignmentTest.kt:55)。
- [L2：时区公开接口](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:896)。
- [L3：实例样式入口](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:575)、[实际图形样式管线](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:757)。
- [L4：旧 styles 回调求值](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:877)、[VOL 硬编码柱色](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-core/src/main/kotlin/io/lkanvas/core/extension/indicator/Volume.kt:30)、[MACD 硬编码柱色](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-core/src/main/kotlin/io/lkanvas/core/extension/indicator/MovingAverageConvergenceDivergence.kt:28)。
- [L5：Styles → Theme 的网格消费](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/theme/StylesThemeBridge.kt:59)。
- [L6：容器布局](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/KLineChart.kt:68)、[pane options](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:970)。
- [L7：tooltip 开关、缓存与样式 key](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/ChartRenderer.kt:98)、[legend 与图形取色](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/TooltipRenderer.kt:95)。
- [L8：现有 PolygonStyle](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/styles/Styles.kt:69)、[IndicatorStyle](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/styles/Styles.kt:359)、[已有 patch 合并](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/styles/StylesPatch.kt:681)。
- [L9：formatter 仅同步状态](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:950)、[时间轴直接默认格式化](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/ChartRenderer.kt:297)、[legendData 默认时间](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:233)。
- [L10：当前 crosshair 仅绘制右侧值标签](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/ChartRenderer.kt:364)。
- [L11：自定义 overlay 绘制上下文](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-core/src/main/kotlin/io/lkanvas/core/overlay/OverlayDefinition.kt:61)、[pane 裁剪](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/OverlayRenderer.kt:79)。
- [L12：archive 配额](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-core/src/main/kotlin/io/lkanvas/core/archive/ChartArchive.kt:36)。
- [L13：库 MACD 公式](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-core/src/main/kotlin/io/lkanvas/core/extension/indicator/MovingAverageConvergenceDivergence.kt:47)、[MA 的运算顺序](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-core/src/main/kotlin/io/lkanvas/core/extension/indicator/MovingAverage.kt:34)。
- [L14：当前发布决策和已接受性能债务](/Users/pauldeman/Documents/ai_workspace/LKanvas/docs/alignment/release-decision-go-2026-11-04.zh-CN.md:1)。
- [U1：固定上游 figure 样式合成](/Users/pauldeman/Documents/ai_workspace/LKanvas/third_party/klinechart-v10.1.0/src/component/Indicator.ts:255)。
- [U2：固定上游 VOL 三色](/Users/pauldeman/Documents/ai_workspace/LKanvas/third_party/klinechart-v10.1.0/src/extension/indicator/volume.ts:35)。
- [U3：固定上游 MACD 三色与 stroke/fill](/Users/pauldeman/Documents/ai_workspace/LKanvas/third_party/klinechart-v10.1.0/src/extension/indicator/movingAverageConvergenceDivergence.ts:45)。
- [U4：固定上游轴格式上下文](/Users/pauldeman/Documents/ai_workspace/LKanvas/third_party/klinechart-v10.1.0/src/component/XAxis.ts:275)、[crosshair 日期浮签](/Users/pauldeman/Documents/ai_workspace/LKanvas/third_party/klinechart-v10.1.0/src/view/CrosshairVerticalLabelView.ts:37)。
- [W1：KLineChart 10.1.0 setTimezone 官方文档](https://klinecharts.com/api/instance/setTimezone)。
- [W2：KLineChart 10.1.0 setFormatter 官方文档](https://klinecharts.com/api/instance/setFormatter)。
- [W3：KLineChart 10.1.0 registerOverlay 官方文档](https://klinecharts.com/api/chart/registerOverlay)。
- [W4：远端 LKanvas beta01 手势源码](https://github.com/IAmKings/LKanvas/blob/v0.16.0-beta01/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/gesture/ChartGestures.kt)。

## 11. 本次未执行与仍存风险

本次未修改生产代码、原始两份报告或依赖版本，未发 issue/PR/新制品，未做安装包身份和真机触摸、Splash、分享的端到端复测，也未重跑全量回归和性能矩阵。16 项的实施状态均未因本文自动关闭。

主要风险是把已存在的能力误判成待开发功能，以及只修字段存储却继续遗漏绘制、缓存和导出。本计划以实际消费和设备行为作为退出条件；beta API/ABI、不同输入范围与既有性能债务继续明确跟踪。
