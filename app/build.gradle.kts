import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.wax"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.wax"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProps = Properties().also { props ->
            rootProject.file("local.properties").takeIf { it.exists() }
                ?.inputStream()?.use { props.load(it) }
        }
        buildConfigField("String", "SPOTIFY_CLIENT_ID",
            "\"${localProps.getProperty("SPOTIFY_CLIENT_ID", "")}\"")
        buildConfigField("String", "SPOTIFY_REDIRECT_URI",
            "\"${localProps.getProperty("SPOTIFY_REDIRECT_URI", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Export Room schema for migration tracking
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    // Lifecycle & ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Networking (Spotify API)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.coroutines.android)

    // DataStore (user preferences)
    implementation(libs.datastore.preferences)

    // Coil (album artwork loading and caching)
    implementation(libs.coil.compose)

    // Room (album history + user preferences DB)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager (weekly album scheduling)
    implementation(libs.work.runtime.ktx)

    // Splash Screen
    implementation(libs.core.splashscreen)

    // Palette (dynamic vinyl color from album art)
    implementation(libs.palette.ktx)

    // Media3 (MediaSession for lock screen turntable notification)
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer)
    // MediaSessionCompat / MediaStyle (transitive impl dep of media3-session; must be explicit)
    implementation(libs.media.compat)

    // Spotify Android SDK (local AAR)
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
