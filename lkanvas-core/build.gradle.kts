plugins {
    alias(libs.plugins.kotlin.jvm)
}

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
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test"))
}
