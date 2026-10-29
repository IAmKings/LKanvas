package io.lkanvas.core.common

/**
 * 上游 `common/SymbolInfo.ts` 的 Kotlin 镜像：品种信息。
 * `precision` 默认 2（上游 SymbolDefaultPrecisionConstants）。
 */
data class SymbolInfo(
    val ticker: String,
    val pricePrecision: Int = 2,
    val volumePrecision: Int = 2,
) {
    init {
        require(pricePrecision >= 0 && volumePrecision >= 0) { "精度须非负" }
    }
}
