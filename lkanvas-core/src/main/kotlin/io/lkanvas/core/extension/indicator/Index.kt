package io.lkanvas.core.extension.indicator

import io.lkanvas.core.indicator.IndicatorDefinition

/**
 * 指标模板全局注册表（上游 `src/extension/indicator/index.ts` 镜像，SLA-004）。
 *
 * 上游：`extensions.forEach(indicator => indicators[indicator.name] = IndicatorImp.extend(indicator))`，
 * `registerIndicator` / `getIndicatorClass` / `getSupportedIndicators` 操作**同一张表**，
 * 图表创建经 `getIndicatorClass` 查询（Store.ts:1397）。本地对齐：注册与创建
 * 共用本表（ChartState.createIndicator 回退查询），宿主自定义模板注册后
 * 既有/新建图表均可创建（审计发现 4 收口）。
 *
 * 同名注册：上游直接覆盖；本地 [register] 默认拒绝静默覆盖（PRD R-14），
 * `replace=true` 显式替换——差异登记 gap-matrix（R-14 更严格）。
 */
object IndicatorTemplates {

    private val indicators = LinkedHashMap<String, IndicatorDefinition>()

    /** 上游 registerIndicator：注册/替换模板（同名冲突见类注释）。 */
    @Synchronized
    fun register(definition: IndicatorDefinition, replace: Boolean = false): Boolean {
        if (!replace && indicators.containsKey(definition.name)) return false
        indicators[definition.name] = definition
        return true
    }

    /** 上游 getIndicatorClass：按名查模板（图表创建的统一入口）。 */
    @Synchronized
    fun get(name: String): IndicatorDefinition? = indicators[name]

    /** 上游 getSupportedIndicators。 */
    @Synchronized
    fun supported(): List<String> = indicators.keys.toList()

    init {
        // 内置 27 模板（上游 extensions 数组注册顺序）
        register(AveragePriceIndicator())
        register(AwesomeOscillatorIndicator())
        register(BiasIndicator())
        register(BollingerBandsIndicator())
        register(BrarIndicator())
        register(BullAndBearIndexIndicator())
        register(CommodityChannelIndexIndicator())
        register(CurrentRatioIndicator())
        register(DifferentOfMovingAverageIndicator())
        register(DirectionalMovementIndexIndicator())
        register(EaseOfMovementValueIndicator())
        register(ExponentialMovingAverageIndicator())
        register(MomentumIndicator())
        register(MovingAverageIndicator())
        register(MacdIndicator())
        register(OnBalanceVolumeIndicator())
        register(PriceAndVolumeTrendIndicator())
        register(PsychologicalLineIndicator())
        register(RateOfChangeIndicator())
        register(RelativeStrengthIndexIndicator())
        register(SimpleMovingAverageIndicator())
        register(StochIndicator())
        register(StopAndReverseIndicator())
        register(TrixIndicator())
        register(VolumeIndicator())
        register(VolumeRatioIndicator())
        register(WilliamsRIndicator())
    }
}
