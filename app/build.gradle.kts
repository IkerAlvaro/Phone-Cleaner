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
// Modo estricto (-Pstrict.release=true o CI=true): un release sin admob.properties falla en vez de avisar.
val strictRelease = providers.gradleProperty("strict.release").orNull == "true" ||
    providers.environmentVariable("CI").orNull == "true"
if (admobProps.isEmpty) {
    logger.warn("Phone Cleaner: no hay admob.properties; la versión release usará los anuncios de PRUEBA de Google.")
}
val missingAdmob = admobProps.isEmpty

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
        minSdk = 24
        targetSdk = 36
        // Versión desde gradle.properties (o -PappVersionCode=… -PappVersionName=…).
        versionCode = providers.gradleProperty("appVersionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("appVersionName").orNull ?: "1.0.0"
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

    packaging {
        resources {
            // Metadatos innecesarios en el APK. Se conserva META-INF/services/** (service loaders de coroutines/Media3).
            excludes += listOf(
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "kotlin/**",
                "META-INF/*.version",
                "META-INF/*.kotlin_module",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
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
        // aapt2 necesita la carpeta mipmap-anydpi-v26 para los iconos adaptativos aunque con minSdk 24 lint la considere obsoleta en algunos casos.
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
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.common)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing.ktx)
    // Instala el perfil de arranque (baseline-prof.txt): menos compilación JIT al abrir la app.
    implementation(libs.androidx.profileinstaller)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.coil.test)
}

// Fallo explícito del release sin IDs reales de AdMob solo en modo estricto.
tasks.matching { it.name in setOf("assembleRelease", "bundleRelease") }.configureEach {
    val fail = strictRelease && missingAdmob
    doFirst {
        if (fail) throw GradleException(
            "Phone Cleaner: falta admob.properties (modo estricto: -Pstrict.release=true o CI=true). " +
                "Crea admob.properties con appId, nativeUnitId e interstitialUnitId. Ver README > Monetización."
        )
    }
}

// Informes del compilador de Compose (estabilidad/recomposición): -Pcompose.reports=true
if (providers.gradleProperty("compose.reports").orNull == "true") {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_metrics")
    }
}
