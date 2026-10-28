plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Secrets are read only from environment variables (GitHub Secrets). Nothing is stored in the repo.
val ksPath: String? = System.getenv("KEYSTORE_PATH")
val ksPass: String? = System.getenv("KEYSTORE_PASSWORD")
val ksAlias: String? = System.getenv("KEY_ALIAS")
val ksKeyPass: String? = System.getenv("KEY_PASSWORD")
val hasSigning = !ksPath.isNullOrBlank() && file(ksPath).exists() &&
    !ksPass.isNullOrBlank() && !ksAlias.isNullOrBlank() && !ksKeyPass.isNullOrBlank()

// Start.io: ads are OFF unless STARTIO_APP_ID is provided AND ADS_ENABLED=true
val startioAppId: String = System.getenv("STARTIO_APP_ID") ?: ""
val adsEnabled: Boolean = (System.getenv("ADS_ENABLED") ?: "false").equals("true", ignoreCase = true)

android {
    namespace = "com.kashef.textsearch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kashef.textsearch"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "0.2.2-phase2"
        buildConfigField("boolean", "ADS_ENABLED", adsEnabled.toString())
        buildConfigField("String", "STARTIO_APP_ID", "\"$startioAppId\"")
    }

    signingConfigs {
        if (hasSigning) {
            create("kashef") {
                storeFile = file(ksPath!!)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksKeyPass
            }
        }
    }

    buildTypes {
        debug {
            if (hasSigning) signingConfig = signingConfigs.getByName("kashef")
        }
        release {
            isMinifyEnabled = false
            if (hasSigning) signingConfig = signingConfigs.getByName("kashef")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Start.io SDK (prepared; initialized only when ads are enabled and an App ID is supplied)
    implementation("com.startapp:inapp-sdk:5.3.1")
}
