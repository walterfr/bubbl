import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bubbl.reader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bubbl.reader"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // OpenCV traz libs nativas de todos os ABIs; no release só ARM (celulares).
            // O debug mantém todos p/ rodar em emulador x86_64.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = true
        htmlReport = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.opencv:opencv:4.10.0")

    // Pan/zoom + tiling de imagens grandes de mangá
    implementation("com.davemorrissey.labs:subsampling-scale-image-view-androidx:3.10.0")
    // CBR (RAR). PDF usa PdfRenderer nativo, CBZ/EPUB usam java.util.zip.
    implementation("com.github.junrar:junrar:7.5.5")

    testImplementation("junit:junit:4.13.2")
}
