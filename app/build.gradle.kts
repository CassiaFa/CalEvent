plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "fr.calevent.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.calevent.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.9.1"
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Google Play / sideload: ML Kit Text Recognition (bundled model, non-free).
        create("play") { dimension = "distribution" }
        // F-Droid: only free dependencies, OCR with Tesseract. Same applicationId (no suffix).
        create("foss") { dimension = "distribution" }
    }

    // F-Droid rejects the signed dependency blob that AGP adds to APKs and bundles by default.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // OCR engines: one per flavor, behind ocr/OcrEngine.
    "playImplementation"(libs.mlkit.text.recognition)
    "fossImplementation"(libs.tesseract4android)
}
