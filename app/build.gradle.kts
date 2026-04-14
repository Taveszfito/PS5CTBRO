plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.DueBoysenberry1226.ps5ctbro"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.DueBoysenberry1226.ps5ctbro"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.1"
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }

    ndkVersion = "27.0.12077973"

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.media:media:1.7.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation(libs.androidx.compose.ui.tooling)
}