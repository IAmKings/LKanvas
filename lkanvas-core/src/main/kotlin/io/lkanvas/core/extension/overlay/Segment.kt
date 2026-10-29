package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-01 segment：两点线段（上游 extension/overlay/segment.ts 镜像）。 */
object SegmentDefinition : OverlayDefinition {
    override val name = "segment"
    override val pointCount = 2
}
