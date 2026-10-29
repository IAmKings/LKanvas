package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-04 horizontalRayLine：水平射线（上游 horizontalRayLine.ts 镜像）。 */
object HorizontalRayLineDefinition : OverlayDefinition {
    override val name = "horizontalRayLine"
    override val pointCount = 2
    override val constraint = OverlayConstraint.HORIZONTAL
}
