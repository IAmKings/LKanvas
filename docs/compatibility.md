# LKanvas 兼容台账（compatibility ledger）

依据 PRD 3.2/3.3 与 R-17/T-01 建立。状态取值：**已映射待实现 / 已实现已验收 / 有意差异 / 平台不适用 / 明确延期**。
1.0 前不允许任何公开能力处于「未知」状态。每项实现时回填「Kotlin 位置」与「测试」。

- 上游基线：KLineChart **v10.1.0**（`third_party/klinechart-v10.1.0/`，Apache-2.0）
- 对照 oracle：`tools/oracle/`（tsx 运行上游 calc → golden fixtures → `lkanvas-core` JVM 测试逐位置比对）
- 数值误差：`abs(a-b) <= 1e-9 + 1e-10*max(abs(a),abs(b))`（PRD 8.2）

## 1. 有意差异登记（冻结）

| ID | 主题 | 上游行为 | LKanvas 行为 | 依据 |
|---|---|---|---|---|
| D-01 | percentage 轴零基准 | 基准 close 为 0 时产出 NaN/Infinity 或虚假涨幅 | 显示「—」并提供状态；像素范围保持有限 | PRD R-06 |
| D-02 | 非线性轴上斐波那契 | 屏幕空间插值层级，标签值与线位置可能不一致 | 先按价格计算层级再投影到轴 | PRD R-09（M4 范围） |
| D-03 | 指标必需字段缺失 | 缺失字段按 0 参与计算（如 `volume ?? 0`） | 公式必需字段缺失 → Missing；输入本身为有效 0 仍按公式处理 | PRD R-08 |

## 2. 内置指标（27，PRD 6.1）

| 编号 | 名称 | 上游文件（src/extension/indicator/） | 状态 | Kotlin 位置 | 测试 |
|---|---|---|---|---|---|
| I-01 | MA | movingAverage.ts | 已映射待实现（Alpha） | 待 T-11 | 待 T-11 |
| I-02 | VOL | volume.ts | 已映射待实现（Alpha） | 待 T-11 | 待 T-11 |
| I-03 | MACD | movingAverageConvergenceDivergence.ts | 已映射待实现（Alpha） | 待 T-11 | 待 T-11 |
| I-04 | EMA | exponentialMovingAverage.ts | 已映射待实现（M3） | 待 T-12 | 待 T-12 |
| I-05 | SMA | simpleMovingAverage.ts | 已映射待实现（M3） | 待 T-12 | 待 T-12 |
| I-06 | BOLL | bollingerBands.ts | 已映射待实现（M3） | 待 T-12 | 待 T-12 |
| I-07 | BBI | bullAndBearIndex.ts | 已映射待实现（M3） | 待 T-12 | 待 T-12 |
| I-08 | BIAS | bias.ts | 已映射待实现（M3） | 待 T-12 | 待 T-12 |
| I-09 | AO | awesomeOscillator.ts | 已映射待实现（M3） | 待 T-12 | 待 T-12 |
| I-10 | MTM | momentum.ts | 已映射待实现（M3） | 待 T-12 | 待 T-12 |
| I-11 | ROC | rateOfChange.ts | 已映射待实现（M3） | 待 T-12 | 待 T-12 |
| I-12 | PSY | psychologicalLine.ts | 已映射待实现（M3） | 待 T-12 | 待 T-12 |
| I-13 | KDJ | stoch.ts | 已映射待实现（M3） | 待 T-13 | 待 T-13 |
| I-14 | RSI | relativeStrengthIndex.ts | 已映射待实现（M3） | 待 T-13 | 待 T-13 |
| I-15 | WR | williamsR.ts | 已映射待实现（M3） | 待 T-13 | 待 T-13 |
| I-16 | CCI | commodityChannelIndex.ts | 已映射待实现（M3） | 待 T-13 | 待 T-13 |
| I-17 | DMI | directionalMovementIndex.ts | 已映射待实现（M3） | 待 T-13 | 待 T-13 |
| I-18 | BRAR | brar.ts | 已映射待实现（M3） | 待 T-13 | 待 T-13 |
| I-19 | CR | currentRatio.ts | 已映射待实现（M3） | 待 T-13 | 待 T-13 |
| I-20 | VR | volumeRatio.ts | 已映射待实现（M3） | 待 T-13 | 待 T-13 |
| I-21 | EMV | easeOfMovementValue.ts | 已映射待实现（M3） | 待 T-13 | 待 T-13 |
| I-22 | SAR | stopAndReverse.ts | 已映射待实现（M3） | 待 T-14 | 待 T-14 |
| I-23 | TRIX | tripleExponentiallySmoothedAverage.ts | 已映射待实现（M3） | 待 T-14 | 待 T-14 |
| I-24 | OBV | onBalanceVolume.ts | 已映射待实现（M3） | 待 T-14 | 待 T-14 |
| I-25 | PVT | priceAndVolumeTrend.ts | 已映射待实现（M3） | 待 T-14 | 待 T-14 |
| I-26 | AVP | averagePrice.ts | 已映射待实现（M3） | 待 T-14 | 待 T-14 |
| I-27 | DMA | differentOfMovingAverage.ts | 已映射待实现（M3） | 待 T-14 | 待 T-14 |

## 3. 覆盖物（16，PRD 6.2）

上游文件：`src/extension/overlay/`（segment、straightLine、rayLine、horizontalRayLine、horizontalSegment、horizontalStraightLine、verticalRayLine、verticalSegment、verticalStraightLine、parallelStraightLine、priceChannelLine、fibonacciLine、priceLine、simpleAnnotation、simpleTag、brush）。

全部状态：**已映射待实现**（O-01 segment 为 Alpha/T-19；其余 Beta/T-20~T-22）。

## 4. 其他能力

| 能力 | 上游位置 | 状态 |
|---|---|---|
| 主图类型 6 种（candle_solid/stroke/up_stroke/down_stroke/ohlc/area） | common/Styles.ts、view/ | 已映射待实现（Alpha candle_solid；其余 M3/T-18） |
| 轴 3 种 + 自定义（normal/percentage/logarithm） | src/extension/y-axis/ | 已映射待实现（Alpha normal；M3/T-16、T-17） |
| 基础图形 7 种（circle/line/polygon/rect/text/arc/path） | src/extension/figure/ | 已映射待实现（Alpha line/point/text 等价物；Beta 全量） |
| 数据接入（loadMoreData/DataSource 语义） | Store.ts、docs/guide/ | 已映射待实现（T-10，原生等价而非逐字兼容） |
| 图例/tooltip/价格线/主题/格式化 | view/、common/ | 已映射待实现（Alpha 摘要；M3 完整） |
| setStyles/createIndicator 等 JS API | Chart.ts | **平台不适用**：JS 方法名/回调签名不逐字兼容（PRD 3.2），以 Kotlin 公开控制接口等价提供 |
| DOM/CSS/CanvasRenderingContext2D/鼠标右键 | — | **平台不适用**：以 Compose 手势/无障碍等价 |
| WebView/JS 桥/任意 JS 插件运行 | — | **平台不适用**（PRD 1.2 生产路径禁用） |
| PNG 导出/诊断 | — | 明确延期（M5/T-29、T-30） |
| 存档/恢复 | — | 明确延期（M4/T-25、T-26） |
| 多图联动/回放/撤销重做/云同步 | — | 明确延期（P2，PRD 3.3） |

## 5. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-25 | 初版：基线固定 v10.1.0；27 指标/16 覆盖物/其他能力登记；D-01/D-02/D-03 差异预登记 |
