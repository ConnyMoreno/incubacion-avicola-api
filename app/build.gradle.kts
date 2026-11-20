plugins {
    id("com.android.application")
}

android {
    namespace = "ec.espoch.monitoreoambiental"
    compileSdk = 34

    defaultConfig {
        applicationId = "ec.espoch.monitoreoambiental"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}