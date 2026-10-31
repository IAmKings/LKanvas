// 发布约定：本地 Maven 仓库（build/maven-repo），POM 元数据与 sources 伴随件
// （PRD R-16/T-32：发布制品含源码包与 API 文档基础；A-46 独立 consumer 验证输入）

apply(plugin = "maven-publish")

val isAndroid = project.plugins.hasPlugin("com.android.library")

configure<org.gradle.api.publish.PublishingExtension> {
    repositories {
        maven {
            name = "LocalRepo"
            url = uri(rootProject.layout.buildDirectory.dir("maven-repo"))
        }
    }

    publications {
        if (isAndroid) {
            // lkanvas-compose AAR：Android 库组件由 AGP 创建（release）
            create<MavenPublication>("lkanvas") {
                groupId = "io.lkanvas"
                artifactId = "lkanvas-compose"
                version = "0.12.0"
                afterEvaluate {
                    from(components["release"])
                }
                pomConfig(this)
            }
        } else {
            create<MavenPublication>("lkanvas") {
                groupId = "io.lkanvas"
                artifactId = "lkanvas-core"
                version = "0.12.0"
                from(components["java"])
                pomConfig(this)
            }
        }
    }
}

// Java 源码/文档伴随件（仅 JVM 模块；Android 的 sources 由 AGP 组件携带）
if (!isAndroid) {
    val sourcesJar = tasks.register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        // 惰性解析：任务配置执行时 java 插件已应用
        from(
            project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
                .getByName("main").allSource,
        )
    }
    configure<org.gradle.api.publish.PublishingExtension> {
        publications {
            named<MavenPublication>("lkanvas") {
                artifact(sourcesJar)
            }
        }
    }
}

fun pomConfig(publication: MavenPublication) {
    publication.pom {
        name.set(if (isAndroid) "LKanvas Compose" else "LKanvas Core")
        description.set(
            if (isAndroid) "Jetpack Compose K 线图组件（LKanvas）——KLineChart 的 Kotlin/Compose 原生实现"
            else "LKanvas 领域核心：数据模型、指标引擎、坐标与覆盖物（KLineChart 的 Kotlin 原生移植）",
        )
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("lkanvas")
                name.set("The LKanvas Authors")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/lkanvas/lkanvas.git")
            url.set("https://github.com/lkanvas/lkanvas")
        }
    }
}
