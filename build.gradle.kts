plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9.0 起内置 Kotlin 支持，无需 org.jetbrains.kotlin.android 插件
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}
