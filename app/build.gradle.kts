import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.hisaab.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hisaab"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "1.11.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Phones only: drops the emulator (x86) copies of ML Kit's text reader, about 23 MB.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        // Release signing comes from local.properties or environment variables; see README.
        create("release") {
            val props = Properties().apply {
                rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
            }
            val store = props.getProperty("RELEASE_STORE_FILE") ?: System.getenv("HISAAB_STORE_FILE")
            if (store != null) {
                storeFile = rootProject.file(store)
                storePassword = props.getProperty("RELEASE_STORE_PASSWORD") ?: System.getenv("HISAAB_STORE_PASSWORD")
                keyAlias = props.getProperty("RELEASE_KEY_ALIAS") ?: System.getenv("HISAAB_KEY_ALIAS")
                keyPassword = props.getProperty("RELEASE_KEY_PASSWORD") ?: System.getenv("HISAAB_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true // the app compares its version with the latest GitHub release
    }

    packaging {
        resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES", "META-INF/io.netty.versions.properties", "META-INF/*.kotlin_module",
            // Post-quantum parameter tables that BouncyCastle (via PdfBox) ships; the app never uses them.
            "org/bouncycastle/pqc/**",
            // Licence texts that JavaMail and its dependencies each ship; the mailcap/javamail.* files stay.
            "META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/NOTICE.txt", "META-INF/LICENSE.txt")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":parser-core"))
    implementation(project(":shared"))
    implementation(project(":email"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.haze)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.opencsv)
    // On-device text recognition for "Add from screenshot"; the Latin model ships inside the APK, no download.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.profileinstaller)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit4)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.sqlite.bundled)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
