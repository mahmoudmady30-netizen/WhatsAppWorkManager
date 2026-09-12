import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Release signing is optional and file-based: if keystore.properties is absent (e.g. a
// fresh checkout, or CI without secrets), the release build still configures and compiles —
// it simply produces an unsigned APK, which Android Studio's "Generate Signed Bundle/APK"
// wizard (or `apksigner`) can sign afterward. Nothing here ever contains real credentials.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasKeystoreConfig = keystorePropertiesFile.exists()
if (hasKeystoreConfig) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.whatsappworkmanager.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.whatsappworkmanager.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasKeystoreConfig) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
        // A fixed, committed debug keystore — NOT a security-sensitive file (debug builds are
        // never distributed for production use; this is purely a development/CI consistency
        // convenience, the same reasoning that makes Android's own auto-generated
        // ~/.android/debug.keystore use a publicly-known password too). Without this, every
        // fresh GitHub Actions runner has no prior ~/.android/debug.keystore of its own, so
        // AGP's implicit default signing config auto-generates a brand-new, random certificate
        // on every single CI run — meaning each successive debug APK build was signed with a
        // *different* certificate than the last. Installing a new one over an already-installed
        // older debug build then fails with "App not installed" (a signature mismatch), even
        // though nothing about the app itself is actually broken. Committing one fixed keystore
        // and using it for every debug build (CI and local alike) makes every debug APK's
        // signature identical release to release, so updates always install cleanly.
        // AGP already defines an implicit signing config named "debug" automatically — trying
        // to `create("debug")` a *second* one under the same name is what "Cannot add a
        // SigningConfig with name 'debug' as a SigningConfig with that name already exists"
        // means. `getByName("debug")` reconfigures that existing implicit one in place instead
        // of declaring a conflicting new one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasKeystoreConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    // Only pulled in for AppCompatDelegate.setApplicationLocales — the per-app-language API
    // that lets a plain (non-AppCompatActivity) Compose app switch language at runtime without
    // the user leaving the app to change their phone's system language. No AppCompatActivity
    // or AppCompat theme is used anywhere else in this project.
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Security (encrypted prefs for sensitive settings)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Networking (only used if a cloud AI provider is enabled by the user)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
