# Catnip 降级项修复与执行完成情况审查（2026-11-04）

> 本文保留修正前审查证据；后续修正状态见 [修正验收记录](catnip-remediation-fixes-2026-11-04.zh-CN.md)。

结论：**不能认定本轮全部完成；当前 beta02 候选仍需修复后再验收。** 固定上游快照、模块边界与内置指标计算公式没有被改动，但新增样式/formatter 的行为存在上游契约偏离。宿主仍消费 beta01，S5 尚未完成，不能把库源码的配置接线等同于宿主已经获得修复。

本次仅审查并新增本文，没有修改生产代码、原执行台账、依赖版本或设备数据，也没有推送或发布。

## 1. 审查范围与方法

| 对象 | 固定比较范围 |
|---|---|
| LKanvas | `581d2bf…231d285aa2e0111b96f92835101acdf66cbc1145`，4 个新增提交 |
| Catnip | `37cd8a9…4e0b2c447dc4142ca9dcc1038fd0d192589678bf`，4 个新增提交 |
| 需求 | [迁移复核与执行计划](/Users/pauldeman/Documents/ai_workspace/LKanvas/docs/alignment/catnip-migration-remediation-plan-2026-11-04.zh-CN.md) |
| 执行声明 | [Catnip 执行台账](/Users/pauldeman/Documents/ai_workspace/Catnip/docs/LKANVAS_REMEDIATION_EXECUTION_2026-11-04.md)、[beta02 发布记录](/Users/pauldeman/Documents/ai_workspace/LKanvas/docs/alignment/0.16.0-beta02-release-notes.zh-CN.md) |
| 上游 oracle | 本地 `third_party/klinechart-v10.1.0` 固定源码，未换用其他版本 |

按规范、需求两条独立审查线检查，再以当前构建产物运行定向探针。Catnip 源码定位先使用 CodeGraph。未以代码风格偏好或纯粹假设列问题。

## 2. Standards：上游契约与兼容性

### A1 · P1：传给 formatter 的模板 token 与上游、库自带工具不兼容

位置：[ChartText.kt:161](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/ChartText.kt:161)、[ChartState.kt:236](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/state/ChartState.kt:236)。

新实现把 Java pattern `yy/MM-dd` / `MM-dd` 当成公开 formatter 模板传出；固定上游使用 `YYYY` / `DD` token，LKanvas 自带格式工具也只替换这些 token。合法消费者若在回调中调用 `LkanvasUtils.formatDate(timestamp, template)`，会得到带字面量的错误日期。

当前 core 制品独立探针实测，同一上海交易日：

```text
yy/MM-dd  => yy/11-dd
MM-dd     => 11-dd
YYYY-MM-DD => 2026-11-03
```

依据：[上游周期格式](/Users/pauldeman/Documents/ai_workspace/LKanvas/third_party/klinechart-v10.1.0/src/common/Period.ts:22)、[库格式工具](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-core/src/main/kotlin/io/lkanvas/core/common/utils/Format.kt:76)。这违反计划 §6.2 的既定 token 规则要求；现有测试反而把 Java pattern 固定成了新契约。

最小修复：公开回调传上游 token，默认未设 formatter 的输出继续按原有默认路径；Catnip 在宿主明确转换成 Java pattern。增加“公开回调委托库自带格式工具”的实际输出测试。

### A2 · P1：LINE 样式使用全部 plot 的索引，未按图形类型计数

位置：[PaneRenderers.kt:589](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:589)。

对 `[BAR, LINE, LINE]` 配置 `[蓝, 橙]` 两条线色，新实现给 BAR 消耗了第一个线色槽。独立探针得到：

```text
实际 LINE1 = 橙 FFFFA500；LINE2 = 全局蓝 FF2196F3
上游应为 LINE1 = 指定蓝 FF0000FF；LINE2 = 指定橙 FFFFA500
```

固定上游使用独立 `barCount` / `lineCount`，同类型样式列表按各自长度循环；本实现按所有 plot 序号且超出时回退全局，两个规则都不同。[上游 Indicator.ts:271](/Users/pauldeman/Documents/ai_workspace/LKanvas/third_party/klinechart-v10.1.0/src/component/Indicator.ts:271)

最小修复：有效样式解析按图形类型分开计数和循环，绘制与 legend 共同使用解析结果；补 BAR 在前、LINE 交错、样式数量少于同类型图形数量的 fixture。

### A3 · P1：BAR 数组被改成涨/跌/平三个位，无法表达上游多个 BAR 槽

位置：[IndicatorStyleResolver.kt:75](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/IndicatorStyleResolver.kt:75)、[StylesThemeBridge.kt:55](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/theme/StylesThemeBridge.kt:55)。

新规则把 `bars[0]/bars[1]/bars[2]` 解释为上涨/下跌/中性。上游每个 `bars[i]` 是一个 BAR 图形的完整三色样式，再按 BAR 图形序号循环。当前 `IndicatorStyleSpec.barColors` 也只有一个三色集合，多个 BAR 无法拥有不同样式槽。[上游 Indicator.ts:294](/Users/pauldeman/Documents/ai_workspace/LKanvas/third_party/klinechart-v10.1.0/src/component/Indicator.ts:294)

Catnip 只使用内置单柱指标时可以显示正确，但不能据此把通用实例样式认定为上游行为通过。已有归一化测试正在验证这套偏离规则，而非上游规则。

最小修复：建立上游含义的逐图形三色槽。确需保留旧单色写法时，只保留为明确的兼容适配，不让其替代规范表示；用两种 BAR 图形和不同三色槽作差分测试。无动态回调的 BAR 也应按上游默认样式处理，不能未经契约依据自动增加正负分色策略。

### A4 · P1：ChartTheme 插入参数破坏旧源码调用，发布记录误称兼容

位置：[ChartTheme.kt:37](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/theme/ChartTheme.kt:37)、[发布兼容性登记:38](/Users/pauldeman/Documents/ai_workspace/LKanvas/docs/alignment/0.16.0-beta02-release-notes.zh-CN.md:38)。

`barColorsContext` 插在既有 `macdBarDown` 与 `legendText` 之间，发布记录却称“新增末尾默认参数、源码兼容”。原先使用位置参数设置 `legendText: Color` 的合法源码，现在把 Color 传给 BarColorsContext，直接失编。

本次将同一份旧位置调用分别编译：

```text
beta01 AAR：exit=0
beta02 当前产物：exit=1
argument type mismatch: actual type is 'Color', but 'BarColorsContext?' was expected
```

最小修复：至少把新增参数移动到末尾，验证旧位置和具名调用。末尾默认参数仍不自动保证已编译调用的二进制兼容，旧构造器/copy 桥接或破坏性变化登记需单独处理。

另一个登记错误：`PaneRenderers` 及其 `figureColors` / `styleOverrideFor` 实际为 public，发布记录称其为 internal、非公开 ABI；旧 `styleOverrideFor(inst)` 调用也已因必填 theme 参数改变。应按真实可见性保留旧入口或明确登记影响，不能用文案把公开 API 变成内部 API。

规范轴合计 **4 项**；其中 A1–A3 直接影响上游契约，A4 影响升级兼容性。未发现固定快照或模块层级被改动。

## 3. Spec：需求实现和测试完整度

### B1 · P1：宿主并未实现要求的分钟/日线显示格式

位置：[LkanvasChart.kt:293](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/main/kotlin/com/localstock/app/ui/chart/lkanvas/LkanvasChart.kt:293)。

计划 §6.2 要求“按 ChartPeriod 选择分钟 `dd HH:mm`、日/周/月 `MM-dd`”。实际 `shanghaiDateFormatter` 直接把库传入的模板交给 `DateTimeFormatter.ofPattern`，不看周期或场景。

因此，即使升级 beta02，日线仍显示 `yy/MM-dd`，日内分钟仍为 `HH:mm`，原格式降级没有消失。当前还没升级，不能通过生产截图验证此配置已经有效。

最小修复：在 Catnip 按周期/场景确定显示策略，按 A1 保持库公开模板使用上游 token；对最终字符串和真实绘制文字作断言，不能只测 formatter 已注册。

### B2 · P2：永久数值门禁未覆盖计划要求的引擎增量路径

位置：[IndicatorParityTest.kt:82](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/test/kotlin/com/localstock/app/ui/chart/lkanvas/IndicatorParityTest.kt:82)。

计划 §8.1 要求“append、replace latest、RESET 和参数改变后”比较增量/全量。现新增测试全部直接调用模板 `calc()`；引擎更新路径出错时，这些测试仍会通过。

最小修复：复用现 fixture，补一个经过 `IndicatorEngine` 的 append/replace/参数更新序列，并比较同输入 domain 输出与全量结果。保持既有容差，不改公式、不放宽缺失掩码。

### B3 · P2：长按测试只能证明松手后可见，未证明长按触发成功

位置：[CrosshairTouchSessionTest.kt:89](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/androidTest/kotlin/com/localstock/app/ui/chart/CrosshairTouchSessionTest.kt:89)。

计划 §4.4 要求“down → 超时 → up”的真实触摸验证。当前仅在 up 后检查十字线。即使长按超时入口坏了，短按 Release 的显示分支仍能让测试通过。

最小修复：保持按下直至超时，在抬指之前断言十字线可见，再抬起检查驻留；确认测试推进方式与实际超时机制一致。父滚动用例同时接受清除/不变，且前置失败用 assume 跳过，也不能作为严格清除契约的证明。

需求轴合计 **3 项**；B1 是具体显示要求未落地，B2/B3 是计划要求的防回归路径缺失。未发现需要用宿主第二套手势或复制指标公式来解决的问题。

## 4. 独立复核补充发现

### C1 · P2：自定义 BAR 的图形色与 legend 色仍不一致

位置：[PaneRenderers.kt:876](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/PaneRenderers.kt:876)、[TooltipRenderer.kt:124](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/TooltipRenderer.kt:124)。

创建无 figure 动态样式的自定义 BAR，设置实例三色为红/绿/灰，正值柱在绘制端使用红色；legend 端没有使用该实例三色回退，而采用全局 palette。当前渲染录制探针结果：`柱色 FFFF0000`，`legend 色 FFF5A623`。

这直接违背计划“每个 legend 值的颜色等于对应图形”和发布记录“恒等”的声明。最小修复是复用逐图形有效样式结果供绘制与 legend 消费，不在两个调用处分别补相近的取色逻辑。

### C2 · P2：运行时切换时区不会触发重绘，tooltip 缓存也不含时区

位置：[LKanvasChart.kt:897](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt:897)、[ChartRenderer.kt:98](/Users/pauldeman/Documents/ai_workspace/LKanvas/lkanvas-compose/src/main/kotlin/io/lkanvas/compose/draw/ChartRenderer.kt:98)。

计划 §6.2 明确要求 timezone/locale/period 改变后不残留旧文案。当前只在 `syncFormatConfig()` 调用 `notifyFormatChanged()`，`setTimezone()` 仍只写普通字段。独立探针从 UTC 改上海后，`viewRevision` 仍为 3，formatConfig 身份不变。

对静态已显示图表，时区切换不触发新帧；即使随后仅移动视口导致重绘，只要 tooltip 选中索引与现 cache key 其他值相同，旧时间文案仍可回放。最小修复：时区变更驱动失效，并把有效格式上下文/版本纳入缓存；补静态图表运行时切换测试，不仅测试首次配置。

### C3 · P2：分享“最多等待十帧”实际没有正确提前结束或逐帧重试

位置：[SecurityDetailScreen.kt:308](/Users/pauldeman/Documents/ai_workspace/Catnip/app/src/main/kotlin/com/localstock/app/ui/SecurityDetailScreen.kt:308)。

`return@repeat` 只结束本次迭代，成功后仍执行十次 `toImageBitmap()` 读回；如果首帧捕获抛异常，异常又直接退出外层 runCatching，不会等待后面的就绪帧。前者增加图像读回成本，后者保留首次记录未完成时的误失败。

最小修复：用可真正 break 的有界循环，成功立即结束；仅对确切的“未就绪”情况重试，并保留协程取消语义。现以 data.uid 作 effect key 是重启，不等于记录了请求 UID；快速切换期间是否会串图，本次未实机复现，不作为已证实输出错误宣称，仍需补相应测试。

补充复核合计 **3 项**，均属于当前计划的消费链/缓存/分享验收范围。

## 5. 执行完成情况

| 阶段 | 本次判定 | 仍需完成 |
|---|---|---|
| S0 基线复现 | 已有历史设备记录，可作对照 | 本轮未重复设备复现，不把历史记录当作新候选已验证 |
| S1 宿主接入 | **部分完成**：Splash/时区/网格/高度来源等代码已接入 | 分享重试修复、真实长按和父滚动测试、字体/方向/时区矩阵证据 |
| S2 样式消费 | **实现已有，不能验收为上游对齐通过** | A2/A3/A4、C1；补按上游 oracle 的测试，修订 IND-014/017 过宽完成声明 |
| S3 格式消费 | **实现已有，不能验收为完整通过** | A1、B1、C2；补真实绘制/导出，而非仅调用 state helper |
| S4 缺口标记 | **源码/单测已落地，设备闭环未完成** | 缺口图形、触摸不抢占、分享含标记、测试数据恢复 |
| S5 制品集成 | **未完成** | beta02 修复后发布、公共仓库解析、Catnip 升级和升级后的实际验收 |

Catnip 本次 `dependencyInsight` 实际仍解析 `com.github.IAmKings.LKanvas:lkanvas-compose/core:0.16.0-beta01`。因此 #6 指标实例色、#10 formatter 不能以宿主已修复结案。台账虽然保留了待升级说明，S5“仅剩推 tag”的描述仍低估上述实现问题。

**测试证据需要更正：** 现存设备 XML 记录 CrosshairTouchSessionTest 共 6 项、0 失败、**1 项 skipped**；跳过的是 `productionDetailParentVerticalScrollClearsCrosshair`。台账称“6 项全绿，含生产页父滚动”与该报告不一致。应写为 5 通过、1 跳过，补齐生产页面 fixture 后重跑，不能把跳过计为通过。[执行台账:28](/Users/pauldeman/Documents/ai_workspace/Catnip/docs/LKANVAS_REMEDIATION_EXECUTION_2026-11-04.md:28)

现存报告：[设备测试 XML](</Users/pauldeman/Documents/ai_workspace/Catnip/app/build/outputs/androidTest-results/connected/debug/TEST-23078PND5G - 14-_app-.xml>)。该文件是构建产物，后续运行可能覆盖；这是本次读取时的结果，不推定其他未留档运行的结果。

**验收环境尚未清理：** 台账明确记载在真机测试删除了 `2025-06-10`、`2026-10-20` 两根 K 线，恢复仍待处理。本轮没有检查或改动设备数据库，不能据此断言设备当前状态，但必须核对并恢复/确认这两处测试改动，才能完成 S4 交付。[执行台账:46](/Users/pauldeman/Documents/ai_workspace/Catnip/docs/LKANVAS_REMEDIATION_EXECUTION_2026-11-04.md:46)

## 6. 本次验证与建议收口顺序

已检查通过：

- LKanvas core/compose 的 Gradle test 任务通过，现存 JVM 报告分别为 **230 + 227**，0 失败；任务命中 UP-TO-DATE，未强制重跑所有用例。
- Catnip app/domain/data 的 JVM test 任务通过，报告分别为 **20 + 39 + 97**，0 失败；读取实际依赖图确认 beta01。
- `tools/alignment/check.py` 通过。此检查保证台账格式/引用与枚举覆盖，不证明字段含义或运行行为与上游一致。
- 固定上游目录没有 diff；core 的 MA/MACD/VOL 计算路径未被本轮样式修改改写，模块结构和依赖未扩张。
- 临时独立探针实测复现 A1、A2、A4、C1，以及 C2 的失效状态缺口；没有把探针加入仓库生产或测试源码。

建议执行顺序：

1. 先修 A1–A4，恢复上游 token、逐类型样式槽与真实兼容边界；避免继续让新测试固定私有协议。
2. 修 C1/C2，并以同一解析结果和格式上下文覆盖绘制、legend、缓存、导出。
3. 补 B1–B3、C3，修正执行台账和对齐矩阵的完成范围；保持计算公式和容差。
4. 用新候选制品完成独立消费、宿主升级、设备与分享闭环，再决定发布/合并；S4 需核对恢复测试数据。

本轮没有发现需要更换 KLineChart v10.1.0 快照的理由。当前应修实现与证据，使其回到固定上游契约；MACD stroke/fill、crosshair 时间浮签等原计划明确后续排期项继续登记，不混入“本轮已完全对齐”的声明。

未检查：本轮没有安装 APK、重跑设备/分享/Splash、检查设备数据库或跑完整性能矩阵；没有执行远程发布和预编译二进制消费者全量兼容测试。主要风险是把错误的私有样式/格式协议固化到 beta02，以及将跳过/未升级项目计入完成。
