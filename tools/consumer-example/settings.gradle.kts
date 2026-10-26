// 独立消费者验证项目（PRD A-46/T-31）：仅从 Maven 制品接入 LKanvas，
// 不允许 project dependency 偷用 internal 接口。
//
// 构建方式（工程根目录）：
//   cd tools/consumer-example && ../gradlew assembleDebug
//   （gradlew 软链到根工程 wrapper；仓库指向根工程 build/maven-repo）
rootProject.name = "lkanvas-consumer-example"
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*") } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        maven(url = uri("../../build/maven-repo")) // 本地发布的 LKanvas 制品
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*") } }
        mavenCentral()
    }
}

include(":consumer")
