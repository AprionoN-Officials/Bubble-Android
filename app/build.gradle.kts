plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bubble.screentools"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bubble.screentools"
        minSdk = 24
        targetSdk = 35
        versionCode = 10
        versionName = "1.1.1"

        val donationUrl = System.getenv("BUBBLE_DONATION_URL") ?: "https://trakteer.id/apriononurhakim"
        buildConfigField("String", "DONATION_URL", "\"$donationUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("id", "in", "en")
    }

    val releaseKeystoreFile = file("../keystore/bubble-release.jks")
    signingConfigs {
        create("release") {
            if (releaseKeystoreFile.exists()) {
                storeFile = releaseKeystoreFile
                storePassword = System.getenv("BUBBLE_KEYSTORE_PASSWORD") ?: "bubble2026"
                keyAlias = System.getenv("BUBBLE_KEY_ALIAS") ?: "bubble"
                keyPassword = System.getenv("BUBBLE_KEY_PASSWORD") ?: "bubble2026"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            if (releaseKeystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = false
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Compress native ML Kit & Tesseract .so libraries inside the APK for minimal file size
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/*.kotlin_module",
                "META-INF/*.version",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Unbundled ML Kit OCR via Google Play Services (~300 KB in APK vs ~15 MB bundled)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")

    // Unbundled ML Kit Barcode / QR Code Scanning via Google Play Services (~200 KB in APK, best accuracy)
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")

    // ML Kit On-Device Language Identification & Translation (59 languages downloaded on demand)
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.google.mlkit:translate:17.0.3")

    // Tesseract 5 OCR for custom user-imported .traineddata language models (16 KB page aligned)
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.8.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
