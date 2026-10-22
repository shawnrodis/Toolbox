plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.shawnrodis.toolbox"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shawnrodis.toolbox"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Parity pass: extended icon set for the source's Font Awesome tool icons
    // (globe, gas pump, plane, PDF, sticky note, ... drawn equivalents).
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation shell
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Native replacement for localStorage / simple key-value persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Phase 3 — PDF engine: PdfBox-Android (tom-roush port of Apache PDFBox).
    // Chosen over iText (licence) and PdfDocument alone (cannot merge/split/
    // rotate/copy pages or add invisible OCR text layers). Rendering for
    // previews / PDF-to-images uses the framework PdfRenderer.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Phase 3 — Offline OCR: ML Kit Text Recognition (bundled Latin model),
    // the native equivalent of the web app's Tesseract.js local engine.
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // Phase 3 — Sync background scheduling (WorkManager; 15-min periodic
    // floor is an Android platform limit, see SyncEngine).
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Phase 3 — EXIF orientation for Square Pic photo loading.
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Local unit tests only (never packaged): SkyFlight parser verification.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
