import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing is driven by a gitignored keystore.properties at the repo root
// (storeFile/storePassword/keyAlias/keyPassword). When it's absent (e.g. a fresh
// clone or CI without the secret), the release build still ASSEMBLES but the APK
// is UNSIGNED (app-release-unsigned.apk) — it cannot be installed until signed,
// and must never be published to the update channel. Only build a release on a
// machine that has the keystore.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.hive.hiragana"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hive.hiragana"
        minSdk = 26
        targetSdk = 34
        versionCode = 17
        versionName = "0.3.2"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the release keystore when configured; otherwise leave unset,
            // producing an UNSIGNED release APK (must be signed before install —
            // see the note at the top of this file), rather than failing the build.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        // Generates BuildConfig.VERSION_NAME / VERSION_CODE for the updater's
        // "am I older than the latest release?" comparison.
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // On-device handwriting recognition (Japanese). Downloads its model at
    // runtime; the scoring feature degrades to geometry-only if unavailable.
    implementation(libs.mlkit.digital.ink)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
