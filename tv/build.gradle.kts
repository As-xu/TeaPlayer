plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.teaplayer.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.teaplayer"
        minSdk = 26
        targetSdk = 35
        // D6：TV 分段，避免与手机包互降级
        versionCode = 2000
        versionName = "0.1.0-tv"
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.tv.material)
    debugImplementation(libs.compose.ui.tooling)
}
