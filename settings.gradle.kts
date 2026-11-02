rootProject.name = "lkanvas"

// 网络约束（本机经验：dl.google.com TLS 不稳，镜像优先；与 kheti/ZhiBox 同源配置）
pluginManagement {
    // 官方 R8 覆盖入口；AGP 内置 8.13.19 无法处理 Kotlin 2.4 元数据。
    buildscript {
        repositories { maven("https://storage.googleapis.com/r8-releases/raw") }
        dependencies { classpath("com.android.tools:r8:9.1.29") }
    }
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

include(":lkanvas-core")
include(":lkanvas-compose")
include(":app-sample")
include(":benchmark")
