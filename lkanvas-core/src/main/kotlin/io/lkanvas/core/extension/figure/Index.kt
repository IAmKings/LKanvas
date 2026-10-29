package io.lkanvas.core.extension.figure

/**
 * 图形模板全局注册表（上游 `extension/figure/index.ts` 镜像，SLA-004/013）。
 * 图形为不可变原语（core/figure/Figures.kt），绘制由平台层 FigureRenderer 承担；
 * 本表登记图形类型与命中开关（宿主自定义 figure 经 registerFigure 参与
 * Overlay 模板 createPointFigures 的图形命中判定）。
 */
object FigureTemplates {

    /** 图形模板：名称 + 是否参与命中（上游 figure.checkEventOn）。 */
    interface FigureTemplate {
        val name: String
        val checkEventOn: Boolean get() = true
    }

    private val templates = LinkedHashMap<String, FigureTemplate>()

    @Synchronized
    fun register(template: FigureTemplate, replace: Boolean = false): Boolean {
        if (!replace && templates.containsKey(template.name)) return false
        templates[template.name] = template
        return true
    }

    /** 按类型名查模板（UpperCamelCase：Circle/Line/Rect/Polygon/Arc/Text/Path）。 */
    @Synchronized
    fun get(name: String): FigureTemplate? = templates[name]

    @Synchronized
    fun supported(): List<String> = templates.keys.toList()

    init {
        listOf("Circle", "Line", "Rect", "Polygon", "Arc", "Text", "Path").forEach { name ->
            register(object : FigureTemplate {
                override val name = name
            })
        }
    }
}
