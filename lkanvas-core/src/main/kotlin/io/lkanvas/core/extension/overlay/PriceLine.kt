package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-13 priceLine：价格线（上游 priceLine.ts 镜像）。 */
object PriceLineDefinition : OverlayDefinition {
    override val name = "priceLine"
    override val pointCount = 1
    override val constraint = OverlayConstraint.HORIZONTAL
}
