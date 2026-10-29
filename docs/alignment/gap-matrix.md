# 差距矩阵（TS ↔ Kotlin 对齐现状）v2

> A3 产物（v2 修复版：一符号一行 / 行 ID / 单字母分类 / 验收方式列 / 批次 2a·2b·2c / 纠正方法名 / 补 convertToPixel·convertFromPixel / Styles 字段级 / 统计改由脚本生成）。
> 分类：**A** 直接对齐｜**B** 适配｜**C** 平台不适用｜**D** 有意差异｜**E** 缺失。验收：**test** 单测｜**diff** 上游自动对照｜**compile** 编译即证｜**parity** 上游文档示例迁移测试｜**manual** 人工｜**—** 不适用。
> **完整性由 `tools/alignment/check.py` 强制校验**（Chart.ts 全部方法必须有行、格式违规即失败）；统计数字由该脚本输出，本文不手写。

## 批次定义与出口条件（v2）

| 批次 | 范围 | 出口条件 |
|---|---|---|
| 2a | 数据（DataLoader 适配/SymbolInfo/Period）+ 事件 9 种 + 坐标转换 + 生命周期 | 门面数据链路单测绿；**parity-1**：上游 quickstart 数据接入示例改初始化后在门面跑通 |
| 2b | 指标 CRUD + 覆盖物 CRUD | create/remove/getIndicators/getOverlays 单测绿；**parity-2**：上游 createIndicator/createOverlay 文档示例跑通 |
| 2c | 视口全量 + 配置组 + pane/轴基础 | scroll/zoom/offset/enabled 单测绿；**parity-3**：上游视口控制示例跑通 |
| 3 | 指标模板 22 字段 + 注册 API + 27 指标元数据自动 diff | diff 0 差异 |
| 4 | 覆盖物模板字段 + figure 注册 + 16 覆盖物元数据 diff | diff 0 差异 |
| 5 | Styles 树 43 接口 + 默认值对照 + tooltip features + 多轴 | 默认值 diff 0 差异；高优样式接线 |
| 6 | app-sample 对齐上游 demo + 人工验收 | demo-checklist 全绿；全部既有测试 0 失败 |

**验收方式新增 parity 定义**：取上游官方文档代码片段，仅替换初始化（`init(ds)`→`LKanvasChart(state)`），在门面上编译并通过行为断言；parity 用例纳入常规测试（防接口漂移）。


## 1. index.ts 顶层导出（上游实际 39 个：20 值符号 + 19 类型别名；`utils` 对象展开为 IDX-004~028 共 25 行；由 check.py 从 index.ts 提取校验）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 | 状态 |
|---|---|---|---|---|---|---|---|---| --- |
| IDX-001 | `init` | index.ts | 门面 init() 已建（LKanvasChart.init(state)） | B | test+parity | S | 2a | Android 无 DOM：init(state) | 已声明 |
| IDX-002 | `dispose` | index.ts | LKanvasChart.dispose 已建 | B | test | S | 2a |  | 已声明 |
| IDX-003 | `version` | index.ts | LKanvasChart.version() 已建 | B | test | S | 2a | 门面静态同名 | 已声明 |
| IDX-004 | `utils.clone` | utils | data class copy 等价 | B | compile | S | 2a | | 已声明 |
| IDX-005 | `utils.merge` | utils | LkanvasUtils.merge（浅）+ StylesMerge（深合并 skip-defaults） | B | test | S | 2a | 双轨：Map 浅合并/Styles 深合并 | 已声明 |
| IDX-006~012 | `utils.isString/isNumber/isValid/isObject/isArray/isFunction/isBoolean` | utils | Kotlin 类型系统等价 | C | compile | S | 2a | 提供 utils 对象占位 | — |
| IDX-013 | `utils.formatValue` | utils/format | LkanvasUtils.formatValue（isValid 兜底「—」） | B | test | S | 2a | | 已声明 |
| IDX-014 | `utils.formatPrecision` | utils/format | LkanvasUtils.formatPrecision（toFixed 语义，默认 2 位） | B | test | S | 2a | | 已声明 |
| IDX-015 | `utils.formatBigNumber` | utils/format | LkanvasUtils.formatBigNumber（K/M/B 分档，对齐上游 format.ts） | B | test | S | 2a | | 已声明 |
| IDX-016 | `utils.formatDate` | utils/format | LkanvasUtils.formatDate（模板占位替换，UTC） | B | test | S | 2a | 模板化 | 已声明 |
| IDX-017 | `utils.formatThousands` | utils/format | LkanvasUtils.formatThousands（整数分组，小数原样） | B | test | S | 2a | | 已声明 |
| IDX-018 | `utils.formatFoldDecimal` | utils/format | LkanvasUtils.formatFoldDecimal（0{count} 折叠标记） | B | test | S | 2a | | 已声明 |
| IDX-019 | `utils.calcTextWidth` | utils/canvas | ChartText.measureWidth（门面待包装） | B | test | S | 2a |  | 已声明 |
| IDX-020 | `utils.getLinearSlopeIntercept` | figure/line | OverlayGeometry 内联（门面待包装） | B | test | S | 2a | 抽公共 | 已声明 |
| IDX-021 | `utils.getLinearYFromSlopeIntercept` | figure/line | LkanvasUtils.getLinearYFromSlopeIntercept | B | test | S | 2a | | 已声明 |
| IDX-022 | `utils.getLinearYFromCoordinates` | figure/line | LkanvasUtils.getLinearYFromCoordinates（垂直线 null） | B | test | S | 2a | | 已声明 |
| IDX-023 | `utils.checkCoordinateOnArc` | extension/figure/arc.ts | LkanvasFigures.checkCoordinateOnArc 已建 | B | test | S | 4 | 同名包装 | 已声明 |
| IDX-024 | `utils.checkCoordinateOnCircle` | extension/figure/circle.ts | LkanvasFigures.checkCoordinateOnCircle 已建 | B | test | S | 4 | 同名包装 | 已声明 |
| IDX-025 | `utils.checkCoordinateOnLine` | extension/figure/line.ts | LkanvasFigures.checkCoordinateOnLine 已建 | B | test | S | 4 | 同名包装 | 已声明 |
| IDX-026 | `utils.checkCoordinateOnPolygon` | extension/figure/polygon.ts | LkanvasFigures.checkCoordinateOnPolygon 已建 | B | test | S | 4 | 同名包装 | 已声明 |
| IDX-027 | `utils.checkCoordinateOnRect` | extension/figure/rect.ts | LkanvasFigures.checkCoordinateOnRect 已建 | B | test | S | 4 | 同名包装 | 已声明 |
| IDX-028 | `utils.checkCoordinateOnText` | extension/figure/text.ts | LkanvasFigures.checkCoordinateOnText 已建 | B | test | S | 4 | 同名包装 | 已声明 |
| IDX-029 | `registerIndicator` | ext/indicator | LkanvasRegistry.registerIndicator 已建（全局+27 预注册） | B | test | M | 3 | 全局注册表 | 已声明 |
| IDX-030 | `getSupportedIndicators` | ext/indicator | LkanvasRegistry.getSupportedIndicators 已建 | B | test | S | 3 |  | 已声明 |
| IDX-031 | `registerOverlay` | ext/overlay | LkanvasRegistry.registerOverlay 已建 | B | test | M | 4 |  | 已声明 |
| IDX-032 | `getSupportedOverlays` | ext/overlay | LkanvasRegistry.getSupportedOverlays 已建（16） | B | test | S | 4 |  | 已声明 |
| IDX-033 | `getOverlayClass` | ext/overlay | LkanvasRegistry.getOverlayDefinition 已建 | B | test | S | 4 |  | 已声明 |
| IDX-034 | `registerFigure` | ext/figure | LkanvasFigures.registerFigure 已建（7 内置预注册） | B | test | S | 4 |  | 已声明 |
| IDX-035 | `getFigureClass` | ext/figure | LkanvasFigures.getFigureFactory 已建 | B | test | S | 4 |  | 已声明 |
| IDX-036 | `getSupportedFigures` | ext/figure | LkanvasFigures.getSupportedFigures 已建 | B | test | S | 4 |  | 已声明 |
| IDX-037 | `registerStyles` | ext/styles | StylesThemes 注册表 + LkanvasRegistry.registerStyles/getSupportedStyles；setStyles(名) 查注册表 | B | test | M | 5 | light/dark 预注册 | 已声明 |
| IDX-038 | `registerLocale` | ext/i18n | LkanvasRegistry.registerLocale 已建 | B | test | S | 3 |  | 已声明 |
| IDX-039 | `getSupportedLocales` | ext/i18n | LkanvasRegistry.getSupportedLocales 已建 | B | test | S | 3 |  | 已声明 |
| IDX-040 | `registerXAxis` | ext/x-axis | LkanvasRegistry.registerXAxis/getXAxisTemplate（AxisTemplate，normal 预注册） | B | test | M | 4 | | 已声明 |
| IDX-041 | `registerYAxis` | ext/y-axis | LkanvasRegistry.registerYAxis（normal/percentage/logarithm 预注册） | B | test | M | 4 | | 已声明 |
| IDX-042 | `registerHotkey` | ext/hotkey | 无 | C | compile | S | 2c | 浏览器键鼠 | — |
| IDX-043 | `getHotkey` | ext/hotkey | 无 | C | compile | S | 2c | | — |
| IDX-044 | `getSupportedHotkeys` | ext/hotkey | 无 | C | compile | S | 2c | | — |

## 2. Chart 方法（79，一方法一行）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 | 状态 |
|---|---|---|---|---|---|---|---|---| --- |
| CH-001 | `getDrawPaneById` | Chart.ts:208 | PaneHeights 简化 pane 模型 | B | compile | S | 2c | 等价 getPaneBounding(paneId) | 已声明 |
| CH-002 | `getContainer` | Chart.ts:219 | 无 DOM；Android 等价 getBounding() | C | compile | S | 2c | 容器尺寸查询替代 | — |
| CH-003 | `getChartStore` | Chart.ts:223 | 架构决策：内核不暴露 | D | — | — | — | 门面封装内核 | — |
| CH-004 | `getXAxisPane` | Chart.ts:227 | 架构决策：内核不暴露 | D | — | — | — |  | — |
| CH-005 | `getDrawPanes` | Chart.ts:231 | 固定三 pane | B | compile | S | 2c |  | 已声明 |
| CH-006 | `getSeparatorPanes` | Chart.ts:235 | adjustPane 分隔条手势 | B | compile | S | 2c |  | 已声明 |
| CH-007 | `layout` | Chart.ts:239 | onLayout 自动布局 | B | test | S | 2c |  | 已声明 |
| CH-008 | `updatePane` | Chart.ts:518 | viewRevision 驱动重绘 | B | test | S | 2c |  | 已声明 |
| CH-009 | `getDom` | Chart.ts:530 | 无 DOM | C | compile | S | 2c | Android 无等价，文档标注 | — |
| CH-010 | `getSize` | Chart.ts:553 | paneHeightsPx/contentWidthPx | B | test | S | 2c | getBounding(paneId?) 等价 | 已声明 |
| CH-011 | `setSymbol` | Chart.ts:584 | MarketKey（缺精度字段） | B | test | S | 2a | SymbolInfo 精度旁路存储 | 已声明 |
| CH-012 | `getSymbol` | Chart.ts:591 | 同上 | B | test | S | 2a |  | 已声明 |
| CH-013 | `setPeriod` | Chart.ts:595 | MarketKey.period 字符串 | B | test | S | 2a | Period{type,milliseconds} 类型化 | 已声明 |
| CH-014 | `getPeriod` | Chart.ts:602 | 同上 | B | test | S | 2a |  | 已声明 |
| CH-015 | `setStyles` | Chart.ts:606 | 门面 setStyles(String/Styles)+Styles→Theme 桥接已建（真机双向切换） | B | test | M | 5 | 字符串主题+DeepPartial merge | 已声明 |
| CH-016 | `getStyles` | Chart.ts:612 | 已建（Styles 树全量返回） | B | test | S | 5 |  | 已声明 |
| CH-017 | `setLayoutOptions` | Chart.ts:616 | ChartLayoutConfig 部分 | B | compile | M | 5 | barSpaceLimit/pane/yAxis | 已声明 |
| CH-018 | `getLayoutOptions` | Chart.ts:620 | 同上 | B | compile | S | 5 |  | 已声明 |
| CH-019 | `setFormatter` | Chart.ts:624 | 门面 set/get 已建（Formatter 类型） | B | test | S | 2c | Formatter 接口对齐 | 已声明 |
| CH-020 | `getFormatter` | Chart.ts:630 | 已建 | B | test | S | 2c |  | 已声明 |
| CH-021 | `setLocale` | Chart.ts:634 | 门面 setLocale/getLocale 已建（zh-CN/en-US 映射 ChartLocale） | B | test | S | 2c | setLocale('en-US')→EN_US | 已声明 |
| CH-022 | `getLocale` | Chart.ts:640 | 已建 | B | test | S | 2c |  | 已声明 |
| CH-023 | `setTimezone` | Chart.ts:644 | 门面 set/get + state.timezoneId → ChartText 时间标签渲染消费（0.3.0） | B | test | S | 2c |  | 已声明 |
| CH-024 | `getTimezone` | Chart.ts:650 | 已建 | B | test | S | 2c |  | 已声明 |
| CH-025 | `setThousandsSeparator` | Chart.ts:654 | 门面 set/get 已建（ThousandsSeparator 类型） | B | test | S | 2c | {sign} 可配 | 已声明 |
| CH-026 | `getThousandsSeparator` | Chart.ts:660 | 已建 | B | test | S | 2c |  | 已声明 |
| CH-027 | `setDecimalFold` | Chart.ts:664 | 门面 set/get 已建（DecimalFold 类型） | B | test | S | 2c | {threshold:3} 默认 | 已声明 |
| CH-028 | `getDecimalFold` | Chart.ts:670 | 已建 | B | test | S | 2c |  | 已声明 |
| CH-029 | `setHotkey` | Chart.ts:674 | 浏览器键鼠 | C | compile | S | 2c | Android 外接键鼠 P2 | — |
| CH-030 | `getHotkey` | Chart.ts:678 | 无 | C | compile | S | 2c |  | — |
| CH-031 | `getHotKey` | Chart.ts:682 | 无（旧拼写别名） | C | compile | S | 2c |  | — |
| CH-032 | `setOffsetRightDistance` | Chart.ts:697 | 门面 set/get 已建（px↔槽位） | B | test | S | 2c | px↔槽位换算 | 已声明 |
| CH-033 | `getOffsetRightDistance` | Chart.ts:701 | 已建 | B | test | S | 2c |  | 已声明 |
| CH-034 | `setMaxOffsetLeftDistance` | Chart.ts:705 | 门面 set 已建（overscroll） | B | test | S | 2c |  | 已声明 |
| CH-035 | `setMaxOffsetRightDistance` | Chart.ts:713 | 门面 set 已建 | B | test | S | 2c |  | 已声明 |
| CH-036 | `setLeftMinVisibleBarCount` | Chart.ts:721 | Viewport.leftMinVisibleBarCount + clamp 强制 + getLeftMinVisibleBarCount | B | test | S | 2c | 滚动下界钳制 | 已声明 |
| CH-037 | `setRightMinVisibleBarCount` | Chart.ts:729 | Viewport.rightMinVisibleBarCount + clamp 强制 + getRightMinVisibleBarCount | B | test | S | 2c | | 已声明 |
| CH-038 | `setBarSpace` | Chart.ts:737 | barSpacing 直改 | B | test | S | 2c | BarSpace{bar,halfBar,gapBar,halfGapBar} | 已声明 |
| CH-039 | `getBarSpace` | Chart.ts:741 | 门面 getBarSpace 已建（BarSpace 四值） | B | test | S | 2c |  | 已声明 |
| CH-040 | `getVisibleRange` | Chart.ts:745 | visibleRange 扩展（缺 realFrom/realTo） | B | test | S | 2a | 结构补 realFrom/realTo | 已声明 |
| CH-041 | `resetData` | Chart.ts:789 | ChartDataStore.reset | B | test | S | 2a |  | 已声明 |
| CH-042 | `getDataList` | Chart.ts:793 | dataSnapshot.candles | B | test | S | 2a |  | 已声明 |
| CH-043 | `setDataLoader` | Chart.ts:797 | ChartLoader+HistorySource 构造注入 | B | test+parity | M | 2a | 运行时替换+四 type 适配（init/forward/backward/update） | 已声明 |
| CH-044 | `createIndicator` | Chart.ts:802 | createIndicator isStack 语义接线：false=替换 pane 内全部非内置实例（上游 Store.addIndicator 同语义）、true=叠加；removeIndicator 改全删匹配 | B | test+parity | M | 2b | isStack 依赖 pane 模型（批次 5） | 已声明 |
| CH-045 | `overrideIndicator` | Chart.ts:837 | 门面 overrideIndicator(id, calcParams, visible) 已建 | B | test | M | 2b | styles/visible/extendData… | 已声明 |
| CH-046 | `getIndicators` | Chart.ts:863 | 门面 getIndicators(filter) 已建 | B | test | S | 2b | filter{id,name,paneId,isStack} | 已声明 |
| CH-047 | `removeIndicator` | Chart.ts:867 | 门面 removeIndicator(filter) 已建 | B | test | M | 2b |  | 已声明 |
| CH-048 | `createOverlay` | Chart.ts:903 | 门面 createOverlay(含数组/编程式+交互式) 已建 | B | test+parity | M | 2b | 数组重载+直接完成 | 已声明 |
| CH-049 | `getOverlays` | Chart.ts:939 | 门面 getOverlays(filter) 已建 | B | test | S | 2b | filter{id,groupId,name,paneId} | 已声明 |
| CH-050 | `overrideOverlay` | Chart.ts:943 | 门面 overrideOverlay(id, OverlayOverride) 已建 | B | test | M | 2b | points/styles/extendData | 已声明 |
| CH-051 | `removeOverlay` | Chart.ts:947 | 门面 removeOverlay(filter/id) 已建 | B | test | S | 2b | 批量 | 已声明 |
| CH-052 | `setPaneOptions` | Chart.ts:951 | 门面 setPaneOptions 已建（main/vol/macd 高度，35% 保底钳制） | B | test | M | 2c | pane{id,height,axis?} | 已声明 |
| CH-053 | `createYAxis` | Chart.ts:1003 | 门面记录层 + 0.4.0 渲染消费：paneId=main 自定义轴同步 ChartState.extraYAxisMeta，绑定指标独立值域+独立标签列 | B | test | L | 5 | 绘制端单轴，多轴渲染随批次 5+ | 已声明 |
| CH-054 | `removeYAxis` | Chart.ts:1025 | 删除保护 + syncExtraAxes 同步渲染源（移除后轴/标签消失） | B | test | S | 5 | | 已声明 |
| CH-055 | `getYAxes` | Chart.ts:1057 | getYAxes 过滤 + 渲染同步（同上） | B | compile | S | 5 | | 已声明 |
| CH-056 | `overrideYAxis` | Chart.ts:1079 | overrideYAxis（reverse 等）+ syncExtraAxes（翻转值域即时生效） | B | test | S | 5 | | 已声明 |
| CH-057 | `overrideXAxis` | Chart.ts:1096 | overrideXAxis：XAxisOverride 覆写记录 | B | test | S | 5 | 绘制端消费随批次 5+ | 已声明 |
| CH-058 | `getPaneOptions` | Chart.ts:1106 | 门面 getPaneOptions 已建 | B | test | S | 2c |  | 已声明 |
| CH-059 | `setZoomEnabled` | Chart.ts:1114 | 门面 set/is 已建 | B | test | S | 2c | 手势层 | 已声明 |
| CH-060 | `isZoomEnabled` | Chart.ts:1118 | 已建 | B | test | S | 2c |  | 已声明 |
| CH-061 | `setZoomAnchor` | Chart.ts:1122 | 门面 set/get 已建（ZoomAnchor 类型） | B | test | S | 2c | cursor/last_bar 语义等价 | 已声明 |
| CH-062 | `getZoomAnchor` | Chart.ts:1126 | 已建 | B | test | S | 2c |  | 已声明 |
| CH-063 | `setScrollEnabled` | Chart.ts:1130 | 门面 set/is 已建 | B | test | S | 2c |  | 已声明 |
| CH-064 | `isScrollEnabled` | Chart.ts:1134 | 已建 | B | test | S | 2c |  | 已声明 |
| CH-065 | `scrollByDistance` | Chart.ts:1138 | 门面 scrollByDistance 已建（动画待） | B | test | M | 2c | Animatable 动画 | 已声明 |
| CH-066 | `scrollToRealTime` | Chart.ts:1154 | scrollToLatest | B | test | S | 2c |  | 已声明 |
| CH-067 | `scrollToDataIndex` | Chart.ts:1161 | scrollToIndex | B | test | S | 2c |  | 已声明 |
| CH-068 | `scrollToTimestamp` | Chart.ts:1166 | scrollToTimestamp | A | test | S | 2c |  | 已声明 |
| CH-069 | `zoomAtCoordinate` | Chart.ts:1171 | zoomBy(anchorX) | B | test | S | 2c |  | 已声明 |
| CH-070 | `zoomAtDataIndex` | Chart.ts:1191 | 门面 zoomAtDataIndex 已建 | B | test | S | 2c |  | 已声明 |
| CH-071 | `zoomAtTimestamp` | Chart.ts:1196 | 门面 zoomAtTimestamp 已建 | B | test | S | 2c |  | 已声明 |
| CH-072 | `convertToPixel` | Chart.ts:1201 | overlayPointToPx（单点） | B | test+parity | M | 2a | 数组+ConvertFilter | 已声明 |
| CH-073 | `convertFromPixel` | Chart.ts:1232 | overlayPointFromPx（单点） | B | test+parity | M | 2a |  | 已声明 |
| CH-074 | `executeAction` | Chart.ts:1261 | 门面 executeAction 已建 | B | test | S | 2a |  | 已声明 |
| CH-075 | `subscribeAction` | Chart.ts:1278 | 门面 subscribeAction 已建（9 种） | B | test+parity | M | 2a | 9 种 ActionType | 已声明 |
| CH-076 | `unsubscribeAction` | Chart.ts:1282 | 门面 unsubscribeAction 已建 | B | test | S | 2a |  | 已声明 |
| CH-077 | `getConvertPictureUrl` | Chart.ts:1286 | ChartSnapshotExporter（PNG only） | B | test | S | 2a | 补 jpeg/bmp+backgroundColor | 已声明 |
| CH-078 | `resize` | Chart.ts:1315 | onLayout 自动 | A | test | — | — |  | 已声明 |
| CH-079 | `destroy` | Chart.ts:1327 | 门面 destroy 已建（退订+scope.cancel） | B | test | S | 2a | engine.close+scope.cancel | 已声明 |

## 3. 事件 payload（9 种 ActionType）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 | 状态 |
|---|---|---|---|---|---|---|---|---| --- |
| EVT-001 | `onZoom`（payload `{scale}`） | Store.ts:1247 | onZoom{scale} 接线（缩放手势后 emit） | B | test | S | 2a |  | 已声明 |
| EVT-002 | `onScroll`（`{distance}`） | Store.ts:946 | onScroll{distance} 接线 | B | test | S | 2a |  | 已声明 |
| EVT-003 | `onVisibleRangeChange`（`VisibleRange`） | Store.ts:742 | onVisibleRangeChange 接线 | B | test | S | 2a |  | 已声明 |
| EVT-004 | `onCrosshairChange`（`Crosshair`） | Store.ts:1309 | onCrosshairChange 接线（Crosshair payload） | B | test | S | 2a | payload 结构补 | 已声明 |
| EVT-005 | `onCandleBarClick`（`VisibleRangeData`） | CandleBarView.ts:35 | onCandleBarClick 接线（VisibleRangeData payload） | B | test | S | 2a | payload 补 prev/next | 已声明 |
| EVT-006 | `onPaneDrag`（`{paneId}`） | SeparatorWidget.ts:149 | onPaneDrag 接线（{paneId}） | B | test | S | 2a |  | 已声明 |
| EVT-007 | `onCandleTooltipFeatureClick` | tooltip | tooltip features 图标渲染+命中 → ON_CANDLE_TOOLTIP_FEATURE_CLICK（0.3.0） | B | test | M | 5 | 依赖批次 5 | 已声明 |
| EVT-008 | `onIndicatorTooltipFeatureClick` | tooltip | 副图 features 命中 → ON_INDICATOR_TOOLTIP_FEATURE_CLICK（0.3.0） | B | test | M | 5 | 同上 | 已声明 |
| EVT-009 | `onCrosshairFeatureClick` | crosshair | features 命中 → ON_CROSSHAIR_FEATURE_CLICK（0.3.0） | B | test | M | 5 | 同上 | 已声明 |


## 4. 公共类型

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 | 状态 |
|---|---|---|---|---|---|---|---|---| --- |
| TYP-001 | `KLineData` | Data.ts | Candle 同名同义 | A | existing | — | — | | 已声明 |
| TYP-002 | `Crosshair` | Crosshair.ts | Crosshair 类型已建（chart/ChartTypes.kt） | B | test | S | 2a | 字段级见 ts-inventory §5 | 已声明 |
| TYP-003 | `BarSpace{bar,halfBar,gapBar,halfGapBar}` | BarSpace.ts | ChartTypes.BarSpace{bar,halfBar,gapBar,halfGapBar} | B | test | S | 2c | | 已声明 |
| TYP-004 | `VisibleRange{from,to,realFrom,realTo}` | VisibleRange.ts | VisibleRange{from,to,realFrom,realTo} 已建 | B | test | S | 2a |  | 已声明 |
| TYP-005 | `VisibleRangeData` | Data.ts:36 | VisibleRangeData 已建（prev/data/next） | B | test | S | 2a | 事件 payload 用 | 已声明 |
| TYP-006 | `SymbolInfo` | SymbolInfo.ts | SymbolInfo 已建（ticker/precision 默认 2） | B | test | S | 2a |  | 已声明 |
| TYP-007 | `Period` | Period.ts | Period 已建（type+milliseconds+toMillis） | B | test | S | 2a |  | 已声明 |
| TYP-008 | `DataLoader`（含 DataLoadMore 双向） | DataLoader.ts | DataLoader 已建（init/forward/backward+subscribeBar；v10 无 update type） | B | test+parity | M | 2a | 四 type 对齐 | 已声明 |
| TYP-009 | `CandleType` 六值 | Styles.ts:242 | MainChartType 同名 | A | existing | — | — | | 已声明 |
| TYP-010 | `LineType/PolygonType/PathType/TooltipShowRule/TooltipShowType/TooltipFeaturePosition/FeatureType/FormatDateType/ZoomAnchorType` | Styles.ts 等 | 同名枚举 11 个（LineType/PolygonType/PathType/TooltipShowRule/ShowType/FeatureType/FeaturePosition/CandleType/FormatDateType/ZoomAnchorType/DataLoadType），带 value 字段 | B | compile | S | 5 | 树内字段沿用 String 常量，枚举迁移随批次 5+ | 已声明 |
| TYP-011 | `IndicatorSeries` 三值 | Indicator.ts:34 | IndicatorSeries（PRICE/VOLUME/NORMAL） | A | existing | — | — | 字符串值对齐在批次 3 | 已声明 |
| TYP-012 | `OverlayMode` 三值 | Overlay.ts:29 | MagnetMode | B | test | S | 2b | 门面同名枚举 | 已声明 |
| TYP-013 | `OverlayDrawingMode` | Overlay.ts:44 | continuous 布尔 | B | test | S | 4 | | 已声明 |
| TYP-014 | `ActionType` 九值 | Action.ts:19 | ActionType 九值枚举已建 + subscribe/execute | B | test | S | 2a |  | 已声明 |


## 5. 指标模板字段（22，逐字段）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 | 状态 |
|---|---|---|---|---|---|---|---|---| --- |
| IND-001 | `id` | component/Indicator.ts | IndicatorInstance.id | A | existing | — | — | | 已声明 |
| IND-002 | `paneId` | component/Indicator.ts | IndicatorInstance.paneId + createIndicator(paneId) | B | test | S | 3 | | 已声明 |
| IND-003 | `yAxisId` | component/Indicator.ts | yAxisId → 渲染端分流：main 走主轴值域，自定义轴走 extraAxisRange 独立值域（0.4.0） | B | test | S | 5 | 多轴 CH-053~057 | 已声明 |
| IND-004 | `name` | component/Indicator.ts | 有 | A | existing | — | — | | 已声明 |
| IND-005 | `shortName` | component/Indicator.ts | 有 | A | existing | — | — | | 已声明 |
| IND-006 | `precision` | component/Indicator.ts | 有 | A | existing | — | — | | 已声明 |
| IND-007 | `calcParams` | component/Indicator.ts | 有 | A | existing | — | — | | 已声明 |
| IND-008 | `shouldOhlc` | component/Indicator.ts | 已建：IndicatorDefinition.shouldOhlc（6 模板覆写 true） | B | diff | S | 3 | MA 等 5 个模板为 true | 已声明 |
| IND-009 | `shouldFormatBigNumber` | component/Indicator.ts | 已建：shouldFormatBigNumber（VOL=true） | B | diff | S | 3 | VOL 为 true | 已声明 |
| IND-010 | `visible` | component/Indicator.ts | 已建：Definition.visible + Instance.visible | B | test | S | 2b |  | 已声明 |
| IND-011 | `zLevel` | component/Indicator.ts | 已建：Definition.zLevel（默认 0） | B | test | S | 3 |  | 已声明 |
| IND-012 | `extendData` | component/Indicator.ts | 已建：Definition.extendData | B | test | M | 3 |  | 已声明 |
| IND-013 | `series` | component/Indicator.ts | 已建：series 枚举（meta diff 断言值映射 undefined→normal） | B | diff | — | 3 | 值字符串化（price） | 已声明 |
| IND-014 | `figures`（含 styles/attrs 回调） | component/Indicator.ts | PlotDef.styles/attrs 回调 + drawLinePlots styleOverrideOf 行级覆盖（0.3.0） | B | diff | M | 3 | 绘制端消费随批次 5+ | 已声明 |
| IND-015 | `minValue` | component/Indicator.ts | 已建：minValue（VOL=0 覆写） | B | diff | S | 3 | VOL=0 | 已声明 |
| IND-016 | `maxValue` | component/Indicator.ts | 已建：maxValue（默认 null） | B | diff | S | 3 |  | 已声明 |
| IND-017 | `styles` | component/Indicator.ts | IndicatorInstance.styles + createIndicator(styles) | B | test | M | 5 | | 已声明 |
| IND-018 | `shouldUpdate` | component/Indicator.ts | 已建：shouldUpdate（默认 true；PRD R-08） | B | test | S | 3 |  | 已声明 |
| IND-019 | `calc` | component/Indicator.ts | 引擎等价（oracle 逐位） | A | diff | — | — | L1 已达标 | 已声明 |
| IND-020 | `regenerateFigures` | component/Indicator.ts | 已建：plotsFor（meta diff 断言 figures 顺序） | B | diff | S | 3 | 改名兼容 | 已声明 |
| IND-021 | `createTooltipDataSource` | component/Indicator.ts | IndicatorDefinition.createTooltipDataSource 钩子（行级 Map） | B | test | M | 5 | | 已声明 |
| IND-022 | `draw` | component/Indicator.ts | IndicatorDrawContext（scope/palette 注入）+ 渲染端 draw 钩子绕过 figures 管线（0.3.0） | B | test | M | 5 | 自定义绘制消费随批次 5+ | 已声明 |
| IND-023 | 27 个内置模板元数据整体 | extension/indicator/*.ts | F1 自动 diff 已建 | B | diff | M | 3 | F1 自动 diff | 已声明 |
| IND-024 | `result`（calc 输出数组，getIndicators 返回体） | component/Indicator.ts | 已建：IndicatorResult 列存（门面视图转换待 2c） | B | test | S | 2b | 结构等价（行存↔列存），门面做视图转换 | 已声明 |


## 6. 覆盖物模板字段（逐字段）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 | 状态 |
|---|---|---|---|---|---|---|---|---| --- |
| OVL-001 | `name` | component/Overlay.ts | 有 | A | existing | — | — | | 已声明 |
| OVL-002 | `totalStep` | component/Overlay.ts | 已建：Definition.totalStep（=pointCount+1，meta diff 16/16 断言） | B | diff | S | 4 | 兼容读取 | 已声明 |
| OVL-003 | `needDefaultPointFigure` | component/Overlay.ts | 已建（meta diff 断言；simpleAnnotation/tag/brush=false） | B | diff | S | 4 |  | 已声明 |
| OVL-004 | `needDefaultXAxisFigure` | component/Overlay.ts | 已建（meta diff 断言） | B | diff | S | 4 |  | 已声明 |
| OVL-005 | `needDefaultYAxisFigure` | component/Overlay.ts | 已建（meta diff 断言） | B | diff | S | 4 |  | 已声明 |
| OVL-006 | `createPointFigures` | component/Overlay.ts | OverlayDefinition.createPointFigures 钩子（Figure 原语 Triple 建模） | B | test | L | 4 | 内置仍走现渲染器 | 已声明 |
| OVL-007 | `createXAxisFigures` | component/Overlay.ts | OverlayDefinition.createXAxisFigures 钩子 | B | test | M | 4 | | 已声明 |
| OVL-008 | `createYAxisFigures` | component/Overlay.ts | OverlayDefinition.createYAxisFigures 钩子 | B | test | M | 4 | | 已声明 |
| OVL-009 | `checkEventOn` | component/Overlay.ts | 已建：Definition.checkEventOn | B | test | S | 4 |  | 已声明 |
| OVL-010 | `drawingMode(step/continuous)` | component/Overlay.ts | continuous 布尔 | B | diff | S | 4 | | 已声明 |
| OVL-011 | `performEventPressedMove` | component/Overlay.ts | 手势 POINT_DRAG 委托 performEventPressedMove（0.3.0） | B | test | M | 4 | | 已声明 |
| OVL-012 | `performEventMoveForDrawing` | component/Overlay.ts | CREATING 绘制中移动建模（点击式创建无拖动预览，委托位保留） | B | test | M | 4 | | 已声明 |
| OVL-013 | `onRemoved` | component/Overlay.ts | OverlayEvent.Removed | B | test | S | 4 | | 已声明 |
| OVL-014 | `onClick/onDoubleClick/onRightClick` | component/Overlay.ts | 轻点/双击检测/长按→onRightClick 手势分发（OverlayEventDispatch，0.3.0） | B | test | M | 4 | 手势端分发随批次 5+ | 已声明 |
| OVL-015 | `onMouseDown/Enter/Leave/Move/Up` | component/Overlay.ts | onMouse 系→触控生命周期映射分发（0.3.0） | B | test | M | 4 | | 已声明 |
| OVL-016 | `onTouchStart/Move/End` | component/Overlay.ts | onTouch 系 down/move/end 手势分发（0.3.0） | B | test | M | 4 | | 已声明 |
| OVL-017 | `onPressedMove` | component/Overlay.ts | DRAGGING 状态 | B | test | S | 4 | | 已声明 |
| OVL-018 | `extendData` | component/Overlay.ts | 已建：Definition.extendData | B | test | S | 4 |  | 已声明 |
| OVL-019 | 16 个内置模板元数据整体 | extension/overlay/*.ts | F1 自动 diff 已建 | B | diff | S | 4 | F1 自动 diff | 已声明 |
| OVL-020 | `groupId` | component/Overlay.ts | OverlayInstance.groupId + createOverlay/overrideOverlay/filter 全链 | B | test | S | 2b | | 已声明 |
### Overlay 实例接口字段（24，v3 补盘点）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 | 状态 |
|---|---|---|---|---|---|---|---|---| --- |
| OVL-021 | `id` | component/Overlay.ts | OverlayInstance.id | B | test | S | 2b |  | 已声明 |
| OVL-022 | `groupId` | component/Overlay.ts | 实例面 groupId 暴露（OverlayInfo.groupId + OverlayFilter(groupId) 命中） | B | test | S | 4 | 与 OVL-020 同实现 | 已声明 |
| OVL-023 | `paneId` | component/Overlay.ts | paneId 固定 main | B | test | S | 2b |  | 已声明 |
| OVL-024 | `name` | component/Overlay.ts | templateName | B | test | S | 2b |  | 已声明 |
| OVL-025 | `totalStep` | component/Overlay.ts | 已建：Definition.totalStep（=pointCount+1，meta diff 16/16 断言） | B | diff | S | 4 | 已由模板行覆盖，实例面暴露 | 已声明 |
| OVL-026 | `currentStep` | component/Overlay.ts | OverlayInstance.currentStep getter + OverlayInfo.currentStep | B | test | S | 2b | points.size 推导 | 已声明 |
| OVL-027 | `drawingMode` | component/Overlay.ts | 已建：continuous 布尔（meta diff 断言 brush=continuous） | B | diff | S | 2b |  | 已声明 |
| OVL-028 | `lock` | component/Overlay.ts | OverlayInstance.locked 已建 | B | test | S | 2b |  | 已声明 |
| OVL-029 | `visible` | component/Overlay.ts | 已建：Definition.visible + Instance.visible | B | test | S | 2b |  | 已声明 |
| OVL-030 | `zLevel` | component/Overlay.ts | 已建：Definition.zLevel（默认 0） | B | test | S | 2b |  | 已声明 |
| OVL-031 | `fixedZLevel` | component/Overlay.ts | OverlayInstance.fixedZLevel + create/override/info 全链 | B | test | S | 4 | | 已声明 |
| OVL-032 | `needDefaultPointFigure` | component/Overlay.ts | 已建（meta diff 断言；simpleAnnotation/tag/brush=false） | B | diff | S | 4 | 已由模板行覆盖，实例面暴露 | 已声明 |
| OVL-033 | `needDefaultXAxisFigure` | component/Overlay.ts | 已建（meta diff 断言） | B | diff | S | 4 | 已由模板行覆盖，实例面暴露 | 已声明 |
| OVL-034 | `needDefaultYAxisFigure` | component/Overlay.ts | 已建（meta diff 断言） | B | diff | S | 4 | 已由模板行覆盖，实例面暴露 | 已声明 |
| OVL-035 | `mode` | component/Overlay.ts | magnetMode + 门面 OverlayMode 枚举已建 | B | test | S | 2b | OverlayMode 枚举 | 已声明 |
| OVL-036 | `modeSensitivity` | component/Overlay.ts | OverlayInstance.modeSensitivity（默认 8）+ create/override/info 全链 | B | diff | S | 4 | 上游默认值等价 | 已声明 |
| OVL-037 | `points` | component/Overlay.ts | OverlayInstance.points（时间锚点）已建 | A | existing | S | 2b | 锚点结构等价 | 已声明 |
| OVL-038 | `extendData` | component/Overlay.ts | 已建：Definition.extendData | B | test | S | 4 | 已由模板行覆盖，实例面暴露 | 已声明 |
| OVL-039 | `styles` | component/Overlay.ts | OverlayInstance.styles + createOverlay/override/info 全链 | B | test | S | 4 | | 已声明 |
| OVL-040 | `createPointFigures` | component/Overlay.ts | 内置渲染（OVL-006 | B | test | S | 4 | 已由模板行覆盖，实例面暴露 | 已声明 |
| OVL-041 | `createXAxisFigures` | component/Overlay.ts | 实例面 createXAxisFigures（Definition 钩子 + 渲染查询位） | B | test | S | 4 | 与 OVL-007 同实现 | 已声明 |
| OVL-042 | `createYAxisFigures` | component/Overlay.ts | 实例面 createYAxisFigures（Definition 钩子 + 渲染查询位） | B | test | S | 4 | 与 OVL-008 同实现 | 已声明 |
| OVL-043 | `performEventPressedMove` | component/Overlay.ts | 手势内置（OVL-011 | B | test | S | 4 | 已由模板行覆盖，实例面暴露 | 已声明 |
| OVL-044 | `performEventMoveForDrawing` | component/Overlay.ts | 手势内置（OVL-012 | B | test | S | 4 | 已由模板行覆盖，实例面暴露 | 已声明 |
## 7. Styles 接口（43，一接口一行；字段级见 ts-inventory §6）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 | 状态 |
|---|---|---|---|---|---|---|---|---| --- |
| STY-001 | `Margin` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-002 | `Padding` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-003 | `Offset` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-004 | `LineStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-005 | `SmoothLineStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-006 | `StateLineStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-007 | `PathStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-008 | `PolygonStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-009 | `RectStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-010 | `TextStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-011 | `StateTextStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-012 | `ChangeColor` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-013 | `GradientColor` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-014 | `FeaturePathStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-015 | `FeatureIconFontStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-016 | `FeatureStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-017 | `GridStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-018 | `TooltipLegendChild` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-019 | `TooltipLegend` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-020 | `TooltipFeatureStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-021 | `TooltipStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-022 | `CandleAreaPointStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-023 | `CandleAreaStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-024 | `CandleHighLowPriceMarkStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-025 | `CandleLastPriceMarkStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-026 | `CandlePriceMarkStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-027 | `CandleTooltipRectStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-028 | `CandleTooltipStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-029 | `CandleBarColor` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-030 | `CandleBarColorCallbackReturnStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-031 | `CandleStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-032 | `IndicatorLastValueMarkStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-033 | `IndicatorTooltipStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-034 | `IndicatorStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-035 | `AxisTickLineStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-036 | `AxisTickTextStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-037 | `AxisStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-038 | `CrosshairDirectionStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-039 | `CrosshairStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-040 | `OverlayPointStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-041 | `OverlayStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-042 | `SeparatorStyle` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | S | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |
| STY-043 | `Styles` | Styles.ts | 已建：chart/styles/Styles.kt data class（字段级对齐 ts-inventory §6） | B | diff | M | 5 | 批次 5 字段级展开+默认值对照 | 已声明 |

## 8. 数值语义（L1，防回归项）

| ID | TS 符号 | 源 | Kotlin 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 | 状态 |
|---|---|---|---|---|---|---|---|---| --- |
| NUM-001 | 27 指标 calc 数值 | extension/indicator/* | oracle 逐位对照已达标 | A | diff | — | — | 既有 fixtures 防回归 | 已声明 |
| NUM-002 | percentage 零基准行为 | y-axis/percentage.ts | 「—」+有限范围 | D | existing | — | — | 有意差异 D-01 | — |
| NUM-003 | 斐波那契价格投影 | overlay/fibonacciLine.ts | 价格空间算层级 | D | existing | — | — | 有意差异 D-02 | — |
| NUM-004 | 缺失字段语义 | extension/indicator/* | Missing（NaN） | D | existing | — | — | 有意差异 D-03 | — |
| NUM-005 | 视口数据↔像素往返 | — | ≤0.5px 达标 | A | test | — | — | A-12 既有 | 已声明 |
| NUM-006 | OHLC 输入校验 | — | 严于上游（定位错误） | D | existing | — | — | PRD R-01 | — |

---

## 9. 批次依赖与执行顺序（v3）

```
2a（数据+事件+坐标转换+生命周期，44 行）
 └─> 2b（指标/覆盖物 CRUD，9+10 行）── 无跨批依赖，可与 2c 并行
 └─> 2c（视口+配置，48 行）
       └─> 3（指标模板+注册 API）──依赖 2b 的实例管理
             └─> 4（覆盖物模板+figure）
                   └─> 5（Styles 树 57 行；多轴依赖 pane 基础；EVT-007~009 依赖本批 tooltip）
                         └─> 6（示例对齐+人工验收，收口）
```
硬依赖仅两条：3 依赖 2b；5 依赖 2c（pane/轴基础）。2a/2b/2c 之间无阻塞，按人力可并行。

### 执行状态（收口）

- 批次 2a/2b/2c：✅（parity-1/2/3 全绿）
- 批次 3/4：✅（两 meta diff 0 差异）
- 批次 5：✅（Styles 43 接口 + 默认值对照 + setStyles 语义；绘制端渐进消费继续）
- 批次 6：✅（demo-checklist 人工验收无阻断；consumer 门面重编通过）
- 批次 5 余量收口（0.2.0 发版前）：✅ —— utils 22 项落地（LkanvasUtils + 逐项测试，K/M/B 分档与 0{count} 折叠对齐上游 format.ts）；Viewport 双向 minVisible 钳制；多轴五方法（YAxisInfo 记录层 + 主轴/指标绑定删除保护）；registerStyles/XAxis/YAxis（样式名解析查注册表）；Indicator 实例 paneId/yAxisId/styles 全链 + PlotDef.styles/attrs 建模位；Overlay 实例 groupId/currentStep/fixedZLevel/modeSensitivity/styles 全链 + performEvent/on 事件回调族建模位；TYP-010 十一枚举同名化
- 最终：**E=0**（A=15 / B=227 / C=9 / D=6；unknown=0；check.py 七面校验通过）

## 10. 工作量与日历估算（v3：S=0.5d / M=1.5d / L=3d，单人折算）

| 批次 | 行数 | S/M/L 分布 | 人日 | 说明 |
|---|---|---|---|---|
| 2a | 44 | S27/M8/L0 | 25.5 | DataLoader 适配与事件接线为主 |
| 2b | 19 | S12/M5/L0 | 21.0 | 含 IND-024 与 OVL 实例字段行 |
| 2c | 48 | S34/M12/L1 | 55.0 | 最大单批；多轴 createYAxis L 级滑入 5 的占位可减 |
| 3 | 5 | S2/M3/L0 | 5.5 | 含 IND-023 元数据 diff（M） |
| 4 | 10 | S6/M3/L1 | 12.0 | createPointFigures 为 L |
| 5 | 57 | S12/M43/L2 | 76.0 | 最大总量；Styles 43 接口+tooltip+多轴 |
| 6 | — | 人工验收 | 3.0 | demo-checklist 执行 |
| **合计** | | | **≈198 人日（单人）** | 双人并行（2a/2b+2c 分线）日历约 4~4.5 个月 |

> 估算口径：纯开发+自测；含每批 parity 用例编写，不含上游新版本变更（v10.1.0 后暂无）。

## 11. parity 用例定义（v3 具体化，纳入常规测试）

| 用例 | 上游文档来源 | 门面写法 | 断言 |
|---|---|---|---|
| parity-1（2a 出口） | docs/guide「快速上手/数据接入」getBars+subscribeBar 示例 | 同名 `setDataLoader(object : DataLoader{...})`，仅 init()→LKanvasChart 构造 | 100 根渲染就绪；update 推进末根；forward 触发 prepend |
| parity-2（2b 出口） | docs/guide「指标」「覆盖物」createIndicator('MACD')/createOverlay({name:'segment',points:[...]}) | 同名调用 | 返回 instanceId；getIndicators/getOverlays 可查；removeIndicator 清理 |
| parity-3（2c 出口） | docs API「视口控制」scrollToDataIndex/zoomAtCoordinate/setBarSpace 示例 | 同名调用 | 每项有单测断言（索引/缩放比/BarSpace 值） |

实现位置：`lkanvas-compose/src/test/.../parity/Parity1DataLoader.kt` 等；上游片段以注释注明出处 URL。

## 12. Definition of Done（本轮对齐完成判据）

1. `python3 tools/alignment/check.py` 通过且矩阵 E 计数 = 0（全部转 A/B/C/D）
2. parity-1/2/3 纳入 `./gradlew test` 常规回归且绿
3. 既有 208 测试 + 27 指标 oracle diff 保持 0 失败（L1 防回归）
4. 独立 consumer（tools/consumer-example）改用门面 API 重新编译通过
5. 台账 compatibility.md 与 upstream-sync.md 同步更新；D 类差异均有 ID 与测试

## 13. 发布判定（0.2.0）

- 上述 DoD 全部满足；批次 2a+2b+2c+3 完成为最低发版面（WebView 迁移者可用数据/指标/覆盖物/视口主链路）
- 批次 4/5 允许随后以 0.3.x 递进（样式树与高级模板不阻塞 0.2.0）
- 版本号与制品：io.lkanvas:*:0.2.0 发布至本地 Maven；README 增加「从 KLineChart(WebView) 迁移」章节并引用 parity 用例为证

## 14. 结构审计发现追踪（v4 新增；来源：structure-logic-audit-2026-10-28.zh-CN.md §5/§6 共 16 项）

> 四态定义（「状态」列）：**已声明** 仅登记/仅类型存在｜**已接线** 生产代码存在调用链（须引用 `.kt` 符号路径，check.py 校验文件存在）｜**行为通过** 已接线 + 存在差分/行为测试引用（须引用 `*Test`，check.py 校验测试文件存在）｜**结构可对应** 行为通过 + structure-map.md 已登记上游↔本地逐符号映射，上游变更块可定位有限本地改动。E=0 或分类 B 不再视为完成态；发版判定以状态分布为准。

| ID | 审计发现 | 处理批次 | 备注 | 状态 |
|---|---|---|---|---|
| SLA-001 | 验收工具只证台账覆盖，不证实现一致 | 06 | check.py v4 四态校验 + structure-map.md | 已接线 |
| SLA-002 | 指标执行模型 Scratch 状态机，无可对照 calc | 06 | calc 规范入口 + Scratch 优化双路径互验 | 已声明 |
| SLA-003 | 动态 pane/通用绘制被固定三 pane 替代 | 06 | 默认 pane 语义、PRICE 过滤、vol/macd 通用化 | 已声明 |
| SLA-004 | 全局模板注册与创建路径断开 | 06 | 指标路径统一注册源；轴/locale 注册消费 | 已声明 |
| SLA-005 | 门面分页无触发入口，more 方向交叉 | 06 | Store 镜像 _adjustVisibleRange 边界触发 | 已声明 |
| SLA-006 | 切品种无退订/清 loading/代次，串品种 | 06 | Store resetData 顺序 + 请求代次门禁 | 已声明 |
| SLA-007 | 指标 CRUD 默认实例/批量/替换语义 | 06 | 严格上游默认（破坏性变更已批准） | 已声明 |
| SLA-008 | 参数改变未按实例重生成输出列 | 06 | plotsFor 已修（4a58aea），补守护测试 | 已声明 |
| SLA-009 | Styles 默认值被误当未提供 | 06 | Merge 镜像 typeChecks.ts 提供即合并 | 已声明 |
| SLA-010 | 多轴只管理记录，未进坐标/渲染链 | 06 | 主轴覆写入链、createYAxis 校验 pane | 已声明 |
| SLA-011 | 开关/格式/部分 Styles 无消费者 | 06 | scroll/zoom 开关、格式链、candle.type、逐根回调 | 已声明 |
| SLA-012 | 数据与像素转换未共用绘制轴模型 | 06 | 单一轴实例 + ConvertFilter | 已声明 |
| SLA-013 | 覆盖物模板回调未执行，交互创建丢配置 | 06 | 模板钩子唯一执行来源 + 命中/事件回退注册表 | 已声明 |
| SLA-014 | 视口与公共 API 契约差异（7 子项） | 06 | 锚点/边界/动画/钳制/layoutOptions | 已声明 |
| SLA-015 | executeAction 与事件 payload 语义 | 06 | 十字线写入 + 抑制广播 + 嵌套 payload | 已声明 |
| SLA-016 | 测试不足为结构/逻辑发布门禁 | 06 | 差分套件 + 逐模板属性断言 + 补丁演练 | 已声明 |

## 统计

**本文不手写统计**——数字由 `tools/alignment/check.py` 输出（分类/批次计数 + 完整性校验），避免 v1 手写失真。校验失败（格式违规/方法缺行/枚举非法）即退出非 0。
