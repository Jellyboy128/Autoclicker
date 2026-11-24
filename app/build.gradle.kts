plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.autoclicker"
    compileSdk = 35
    signingConfigs {
        create("shared") {
            storeFile = file("debug.p12")
            storePassword = "android"
            keyAlias = "autoclicker"
            keyPassword = "android"
            storeType = "pkcs12"
        }
    }
    defaultConfig {
        applicationId = "com.example.autoclicker"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.2"
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
