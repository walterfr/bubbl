plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bubbl.reader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bubbl.reader"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Pan/zoom + tiling de imagens grandes de mangá
    implementation("com.davemorrissey.labs:subsampling-scale-image-view-androidx:3.10.0")
    // CBR (RAR). PDF usa PdfRenderer nativo, CBZ/EPUB usam java.util.zip.
    implementation("com.github.junrar:junrar:7.5.5")

    testImplementation("junit:junit:4.13.2")
}
