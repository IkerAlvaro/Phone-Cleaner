plugins {
    alias(libs.plugins.android.application) apply false
    // Fija la versión de Kotlin usada por el soporte integrado de AGP 9.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
}
