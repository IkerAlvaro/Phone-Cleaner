import java.util.Properties

// IDs de prueba oficiales de Google AdMob: muestran anuncios "Test Ad" y nunca generan ingresos ni sanciones.
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testNativeUnitId = "ca-app-pub-3940256099942544/2247696110"
val testInterstitialUnitId = "ca-app-pub-3940256099942544/1033173712"

// IDs reales: crea admob.properties en la raíz (no se sube a git). Ver README > Monetización.
val admobProps = Properties().apply {
    val file = rootProject.file("admob.properties")
    if (file.exists()) file.inputStream().use(::load)
}
fun admob(key: String, fallback: String): String = admobProps.getProperty(key)?.takeIf { it.isNotBlank() } ?: fallback
if (admobProps.isEmpty) {
    logger.warn("Phone Cleaner: no hay admob.properties; la versión release usará los anuncios de PRUEBA de Google.")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.ikeralvaro.phonecleaner"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ikeralvaro.phonecleaner"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${admob("privacyPolicyUrl", "https://github.com/IkerAlvaro/Phone-Cleaner/blob/main/PRIVACY.md")}\"")
        buildConfigField("String", "REMOVE_ADS_PRODUCT_ID", "\"remove_ads\"")
    }

    androidResources {
        localeFilters += listOf("es", "en")
        generateLocaleConfig = true
    }

    // Firma para Google Play: crea keystore.properties en la raíz (no se sube a git). Ver README.
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        val props = Properties().apply { keystoreFile.inputStream().use(::load) }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testAdmobAppId
            buildConfigField("String", "ADMOB_NATIVE_UNIT_ID", "\"$testNativeUnitId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"$testInterstitialUnitId\"")
        }
        release {
            manifestPlaceholders["admobAppId"] = admob("appId", testAdmobAppId)
            buildConfigField("String", "ADMOB_NATIVE_UNIT_ID", "\"${admob("nativeUnitId", testNativeUnitId)}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"${admob("interstitialUnitId", testInterstitialUnitId)}\"")
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    lint {
        // Las versiones se fijan a propósito a publicaciones estables con al menos dos semanas.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // targetSdk 36 es el exigido por Google Play en 2026; se compila contra 37.
        disable += "OldTargetApi"
        // aapt2 necesita la carpeta mipmap-anydpi-v26 para los iconos adaptativos aunque minSdk sea 26.
        disable += "ObsoleteSdkInt"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.coil.test)
}
