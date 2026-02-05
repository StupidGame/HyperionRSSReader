plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.stupidgame.hyperionrssreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.stupidgame.hyperionrssreader"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("HYPERION_KEY_PATH")) // 君が生成した鍵ストアファイルのパス
            storePassword = System.getenv("KEYSTORE_PASSWORD") // 環境変数から読み込むことを推奨
            keyAlias = "hyperion" // 鍵のエイリアス
            keyPassword = System.getenv("KEY_PASSWORD") // 環境変数から読み込むことを推奨
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }

    // 新しいGradle DSLでAPKファイル名を指定する
    applicationVariants.all {
        if (buildType.name == "release") {
            outputs.all { 
                // 明示的にApkVariantOutputとして扱う
                val apkOutput = this as? com.android.build.gradle.api.ApkVariantOutput
                apkOutput?.outputFileName = "Hyperion RSS Reader.apk"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-ads:24.9.0")
    implementation("org.jsoup:jsoup:1.22.1")
    implementation("com.prof18.rssparser:rssparser:6.1.3")
    
    val room_version = "2.8.4"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
    
    // WorkManager for background updates
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}