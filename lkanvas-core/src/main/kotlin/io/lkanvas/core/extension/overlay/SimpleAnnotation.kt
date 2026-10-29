package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayDefinition

/** O-14 simpleAnnotation：简单注解（上游 simpleAnnotation.ts 镜像）。 */
object SimpleAnnotationDefinition : OverlayDefinition {
    override val name = "simpleAnnotation"
    override val pointCount = 1
    override val needDefaultPointFigure = false
    override val needDefaultXAxisFigure = false
    override val needDefaultYAxisFigure = false
}
