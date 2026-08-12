plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hezi.juyumao"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hezi.juyumao"
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "4.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Room schema 导出位置（配合 JuYuMaoDatabase 的 exportSchema = true，供迁移测试与 schema 对比）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// AGP 9.0 起内置 Kotlin 支持，jvmTarget 用 compilerOptions 配置
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Media3（FFmpeg 扩展未发布到 Maven，DSD/APE/WavPack 由解码失败兜底处理）
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.effect)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // Paging
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Coil
    implementation(libs.coil.compose)

    // Palette (cover color extraction)
    implementation(libs.palette)

    // Glance (Widget)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // SMB
    implementation(libs.smbj) {
        exclude(group = "org.bouncycastle")
    }
    implementation(libs.jcifsng)

    // Audio Tagging (metadata extraction)
    implementation(libs.jaudiotagger)

    // mDNS
    implementation(libs.jmdns)

    // Miuix (MIUI 设计语言 UI 库，第 2 轮迭代)
    // miuix-blur 要求 minSdk 33，本项目 minSdk 29 → 不引入，GlassMorphism 保留自绘（T6 决策）
    implementation(libs.miuix.ui)
    implementation(libs.miuix.squircle)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)

    // Miuix Overlay 组件（OverlayDialog/OverlayBottomSheet/OverlayListPopup）内部使用
    // NavigationBackHandler（androidx.navigationevent 预测性返回），需要显式提供
    // LocalNavigationEventDispatcherOwner（Miuix 仅作 implementation 传递，不在 compile classpath）。
    // 版本与 Miuix 0.9.3 传递引入的 1.1.2 保持一致。
    implementation("androidx.navigationevent:navigationevent-compose-android:1.1.2")
}
