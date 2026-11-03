// 发布约定：本地 Maven 仓库（build/maven-repo）+ Maven Central Portal 上传配套，
// POM 元数据与 sources/javadoc 伴随件、GPG 签名（凭据在位时自动启用）。
// （PRD R-16/T-32：发布制品含源码包与 API 文档基础；A-46 独立 consumer 验证输入；
// 2026-11-04 远程发布 GO 决策：docs/alignment/release-decision-go-2026-11-04.zh-CN.md）

apply(plugin = "maven-publish")
apply(plugin = "signing")

val isAndroid = project.plugins.hasPlugin("com.android.library")

// 版本单一来源（gradle.properties lkanvas.version）：缺失即失败，杜绝硬编码漂移
val lkanvasVersion: String = run {
    val raw = (project.findProperty("lkanvas.version") as String?)?.trim().orEmpty()
    if (raw.isEmpty()) {
        error("gradle.properties 缺少 lkanvas.version（版本单一来源，发布契约必填）")
    }
    raw
}

// groupId 单一来源（gradle.properties lkanvas.groupId）：缺失即失败（同版本契约）。
// JitPack 强制 com.github.User.Repo 坐标，经 -Plkanvas.groupId=... 在命令行覆盖
// （jitpack.yml install 命令传入；-P 优先级高于 gradle.properties）
val lkanvasGroup: String = run {
    val raw = (project.findProperty("lkanvas.groupId") as String?)?.trim().orEmpty()
    if (raw.isEmpty()) {
        error("gradle.properties 缺少 lkanvas.groupId（Maven Central 已验证命名空间，发布契约必填）")
    }
    raw
}

// 凭据解析：-P/gradle.properties → local.properties（gitignored）→ 环境变量
fun resolvedCredential(key: String): String? {
    (project.findProperty(key) as String?)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val localProps = java.util.Properties()
    val localFile = rootProject.file("local.properties")
    if (localFile.isFile) {
        localFile.inputStream().use { localProps.load(it) }
    }
    localProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return System.getenv(key.uppercase().replace('.', '_'))?.trim()?.takeIf { it.isNotEmpty() }
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    group = "build"
    description = "Javadoc 伴随件 stub（Central 必需；dokka 正式文档后续补）"
    archiveClassifier.set("javadoc")
    from(rootProject.layout.projectDirectory.file("gradle/javadoc-stub.html"))
}

val verifyLibraryVersions = tasks.register("verifyLibraryVersions") {
    group = "verification"
    description = "版本守护：代码/示例中的版本标识必须与 lkanvas.version 一致"
    doLast {
        val expected = lkanvasVersion
        val guardFiles = listOf(
            "lkanvas-core/src/main/kotlin/io/lkanvas/core/LKanvas.kt",
            "lkanvas-compose/src/main/kotlin/io/lkanvas/chart/LKanvasChart.kt",
            "app-sample/build.gradle.kts",
            "tools/consumer-example/consumer/build.gradle.kts",
        )
        val mismatches = guardFiles
            .map { rootProject.file(it) }
            .filter { it.isFile && !it.readText().contains(expected) }
        if (mismatches.isNotEmpty()) {
            error(
                "版本不一致：以下文件未包含 lkanvas.version=$expected —— " +
                    mismatches.joinToString { it.relativeTo(rootProject.projectDir).path },
            )
        }
    }
}

configure<org.gradle.api.publish.PublishingExtension> {
    repositories {
        maven {
            name = "LocalRepo"
            url = uri(rootProject.layout.buildDirectory.dir("maven-repo"))
        }
        // GitHub Packages（封闭内测渠道）：gpr.user/gpr.key 凭据齐备时注册；
        // 聚合 publish 会同时推 LocalRepo 与此仓库——同版本重复推送不可变，请用
        // publishAllPublicationsToGitHubPackagesRepository 定向推送
        val gprUser = resolvedCredential("gpr.user")
        val gprKey = resolvedCredential("gpr.key")
        if (gprUser != null && gprKey != null) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/IAmKings/LKanvas")
                credentials {
                    username = gprUser
                    password = gprKey
                }
            }
        }
    }

    publications {
        if (isAndroid) {
            // lkanvas-compose AAR：Android 库组件由 AGP 创建（release）
            create<MavenPublication>("lkanvas") {
                groupId = lkanvasGroup
                artifactId = "lkanvas-compose"
                version = lkanvasVersion
                afterEvaluate {
                    from(components["release"])
                }
                pomConfig(this)
            }
        } else {
            create<MavenPublication>("lkanvas") {
                groupId = lkanvasGroup
                artifactId = "lkanvas-core"
                version = lkanvasVersion
                from(components["java"])
                pomConfig(this)
            }
        }
    }
}

// 伴随件：非 Android 模块的 sources 由本脚本生成（Android 的 sources 由 AGP 组件携带）；
// javadoc stub 两个模块都必须有（Central 校验必需伴随件）。
configure<org.gradle.api.publish.PublishingExtension> {
    publications {
        named<MavenPublication>("lkanvas") {
            if (!isAndroid) {
                val sourcesJar = tasks.register<Jar>("sourcesJar") {
                    archiveClassifier.set("sources")
                    // 惰性解析：任务配置执行时 java 插件已应用
                    from(
                        project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
                            .getByName("main").allSource,
                    )
                }
                artifact(sourcesJar)
            }
            artifact(javadocJar)
        }
    }
}

// GPG 签名（Maven Central 必需）：凭据齐备时自动启用，缺省静默跳过（本地发布不强制）
val signingKeyId = resolvedCredential("signing.keyId")
val signingPassword = resolvedCredential("signing.password")
val signingKeyRing = resolvedCredential("signing.secretKeyRingFile")
if (signingKeyId != null && signingPassword != null && signingKeyRing != null) {
    configure<org.gradle.plugins.signing.SigningExtension> {
        val publications = project.extensions.getByType(org.gradle.api.publish.PublishingExtension::class.java)
            .publications
        if (publications.isNotEmpty()) {
            sign(*publications.toTypedArray())
            setRequired(true)
        }
    }
} else {
    logger.lifecycle("publishing: signing 凭据缺失（local.properties 的 signing.*），跳过 GPG 签名——仅限本地发布")
}

// 发布前版本守护（publish* 全族：本地/远端、聚合/单任务）
tasks.matching { it.name.startsWith("publish") }.configureEach {
    dependsOn(verifyLibraryVersions)
}

fun pomConfig(publication: MavenPublication) {
    publication.pom {
        name.set(if (isAndroid) "LKanvas Compose" else "LKanvas Core")
        description.set(
            if (isAndroid) "Jetpack Compose K 线图组件（LKanvas）——KLineChart 的 Kotlin/Compose 原生实现"
            else "LKanvas 领域核心：数据模型、指标引擎、坐标与覆盖物（KLineChart 的 Kotlin 原生移植）",
        )
        url.set("https://github.com/IAmKings/LKanvas")
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
            connection.set("scm:git:git://github.com/IAmKings/LKanvas.git")
            url.set("https://github.com/IAmKings/LKanvas")
        }
    }
}
