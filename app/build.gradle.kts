plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    id("base")
}

android {
    namespace = "io.github.stupidgame.hyperionrssreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.stupidgame.hyperionrssreader"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("HYPERION_KEY_PATH")
            val ksPass = System.getenv("KEYSTORE_PASSWORD")
            val keyAl  = "hyperion"
            val keyPass= System.getenv("KEY_PASSWORD")

            // 環境変数が揃っている時だけ設定（ローカル開発で未設定でもビルドできるように）
            if (!ksPath.isNullOrBlank() && !ksPass.isNullOrBlank() && !keyAl.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = keyAl
                keyPassword = keyPass
            } else {
                // 未設定なら release 署名は設定されません（CI等で環境変数を入れてください）
                logger.warn("Release signing env vars are missing. APK will be unsigned or use default behavior.")
            }
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
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
    }
    packagingOptions {
        resources.excludes.add("META-INF/versions/9/module-info.class")
        resources.excludes.add("META-INF/INDEX.LIST")
        resources.excludes.add("META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule")
        resources.excludes.add("META-INF/groovy-release-info.properties")
        resources.excludes.add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

base {
    val appId = android.defaultConfig.applicationId
    val vName = android.defaultConfig.versionName ?: "0.0"
    val vCode = android.defaultConfig.versionCode

    // 例: io.github.stupidgame.curyendar-1.0-1-release.apk / .aab みたいな形になる
    archivesName.set("${appId}-${vName}-${vCode}")
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