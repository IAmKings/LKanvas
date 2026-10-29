package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-11 priceChannelLine：价格通道（上游 priceChannelLine.ts 镜像）。 */
object PriceChannelLineDefinition : OverlayDefinition {
    override val name = "priceChannelLine"
    override val pointCount = 3
}
