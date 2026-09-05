// Top-level build file: declares plugins so app/build.gradle.kts can apply
// them without repeating version numbers (the versions live in
// gradle/libs.versions.toml).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
