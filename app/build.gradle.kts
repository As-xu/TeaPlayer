plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.teaplayer.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.teaplayer"
        minSdk = 26
        targetSdk = 35
        // D6：手机/TV 同 applicationId、多 APK；versionCode 分段（手机 1xxx，TV 2xxx）
        versionCode = 1000
        versionName = "0.1.0-mobile"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":feature"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:net"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
}
