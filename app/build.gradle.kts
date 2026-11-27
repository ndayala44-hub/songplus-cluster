plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.songplus.cluster"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.songplus.cluster"
        minSdk = 28          // DiLink 3.0 head units run Android 10 (API 29); 28 keeps older units in reach
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
    }

    // Optional platform-key signing. This is the LEGITIMATE route to the signature-level BYDAUTO_* permissions:
    // the OS grants them only to an app signed with the same certificate the ROM trusts as "platform". Supply the
    // keystore via -PPLATFORM_KEYSTORE=... (or the PLATFORM_KEYSTORE env var). When absent, builds use the normal
    // debug key and the app runs exactly as before (OBD2 + simulation still work). See docs/PLATFORM_SIGNING.md.
    val platformKsPath = (project.findProperty("PLATFORM_KEYSTORE") as String?) ?: System.getenv("PLATFORM_KEYSTORE")
    val hasPlatformKs = platformKsPath != null && file(platformKsPath).exists()
    signingConfigs {
        if (hasPlatformKs) {
            create("platform") {
                storeFile = file(platformKsPath!!)
                storePassword = (project.findProperty("PLATFORM_KS_PASSWORD") as String?) ?: System.getenv("PLATFORM_KS_PASSWORD") ?: "android"
                keyAlias = (project.findProperty("PLATFORM_KEY_ALIAS") as String?) ?: System.getenv("PLATFORM_KEY_ALIAS") ?: "platform"
                keyPassword = (project.findProperty("PLATFORM_KEY_PASSWORD") as String?) ?: System.getenv("PLATFORM_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        debug {
            if (hasPlatformKs) signingConfig = signingConfigs.getByName("platform")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasPlatformKs) signingConfig = signingConfigs.getByName("platform")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
