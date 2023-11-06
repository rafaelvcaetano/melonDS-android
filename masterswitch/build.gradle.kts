plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.smp.masterswitchpreference"
    compileSdk = AppConfig.compileSdkVersion

    defaultConfig {
        minSdk = AppConfig.minSdkVersion
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        kotlin {
            jvmToolchain(17)
        }
    }
}

dependencies {
    with(Dependencies.Kotlin) {
        implementation(kotlinStdlib)
    }

    with(Dependencies.AndroidX) {
        implementation(appCompat)
        implementation(core)
        implementation(preference)
    }
}
