// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// ✅ Compose UI 버전 전역 변수 설정
extra["compose_ui_version"] = "1.7.0"

// ✅ 일반적으로 buildscript 블록은 필요 없음
// (plugins 블록이 상위에서 관리하므로)
// 하지만 특정 환경에서 Gradle 플러그인 버전을 직접 지정해야 한다면 ↓
buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")
    }
}
