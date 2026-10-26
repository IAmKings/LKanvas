plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 发布约定（本地 Maven 仓库；PRD R-16/T-32）
apply(from = rootProject.file("gradle/publishing.gradle.kts"))

// 纯 Kotlin/JVM 领域库：不依赖任何 Android/Compose 类型（PRD 4.2 模块边界）
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test"))
}
