// 根工程：插件在各模块按需应用，这里只做公共任务配置
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed", "skipped")
        showStackTraces = true
    }
}
