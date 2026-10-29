package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-07 verticalRayLine：垂直射线（上游 verticalRayLine.ts 镜像）。 */
object VerticalRayLineDefinition : OverlayDefinition {
    override val name = "verticalRayLine"
    override val pointCount = 2
    override val constraint = OverlayConstraint.VERTICAL
}
