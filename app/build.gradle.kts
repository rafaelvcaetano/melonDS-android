import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
}

android {
    signingConfigs {
        create("release") {
            val props = gradleLocalProperties(rootDir)
            storeFile = file(props["MELONDS_KEYSTORE"] as String)
            storePassword = props["MELONDS_KEYSTORE_PASSWORD"] as String
            keyAlias = props["MELONDS_KEY_ALIAS"] as String
            keyPassword = props["MELONDS_KEY_PASSWORD"] as String
        }
    }

    compileSdk = AppConfig.compileSdkVersion
    ndkVersion = AppConfig.ndkVersion
    defaultConfig {
        applicationId = "me.magnum.melonds"
        minSdk = AppConfig.minSdkVersion
        targetSdk = AppConfig.targetSdkVersion
        versionCode = AppConfig.versionCode
        versionName = AppConfig.versionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        }
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17 -Wno-write-strings")
            }
        }
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
        vectorDrawables.useSupportLibrary = true
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += "-Xopt-in=kotlin.ExperimentalUnsignedTypes"
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            applicationIdSuffix = ".dev"
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.3.0"
    }

    flavorDimensions.add("version")
    productFlavors {
        create("playStore") {
            dimension = "version"
            versionNameSuffix = " PS"
        }
        create("gitHub") {
            dimension = "version"
            versionNameSuffix = " GH"
        }
    }
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.18.1"
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    configurations.all {
        resolutionStrategy.eachDependency {
            val requested = requested
            if (requested.group == "com.android.support") {
                if (!requested.name.startsWith("multidex")) {
                    useVersion("26.+")
                }
            }
        }
    }
}

dependencies {
    val gitHubImplementation by configurations

    with(Dependencies.Tools) {
        coreLibraryDesugaring(desugarJdkLibs)
    }

    with(Dependencies.Kotlin) {
        implementation(kotlinStdlib)
    }

    // AndroidX
    with(Dependencies.AndroidX) {
        implementation(activity)
        implementation(activityCompose)
        implementation(appCompat)
        implementation(cardView)
        implementation(constraintLayout)
        implementation(core)
        implementation(documentFile)
        implementation(fragment)
        implementation(hiltWork)
        implementation(lifecycleExtensions)
        implementation(lifecycleViewModel)
        implementation(preference)
        implementation(recyclerView)
        implementation(room)
        implementation(roomRxJava)
        implementation(splashscreen)
        implementation(swipeRefreshLayout)
        implementation(work)
        implementation(workRxJava)
        implementation(material)
    }

    with(Dependencies.Compose) {
        implementation(foundation)
        implementation(material)
        implementation(ui)
        implementation(uiToolingPreview)

        debugImplementation(uiTooling)
    }

    // Third-party
    with(Dependencies.ThirdParty) {
        implementation(project(masterSwitchPreference))
        implementation(flexbox)
        implementation(gson)
        implementation(hilt)
        implementation(kotlinxCoroutinesRx)
        implementation(picasso)
        implementation(markwon)
        implementation(markwonImagePicasso)
        implementation(markwonLinkify)
        implementation(rxJava)
        implementation(rxJavaAndroid)
        implementation(commonsCompress)
        implementation(xz)
    }

    // GitHub
    with(Dependencies.GitHub) {
        gitHubImplementation(retrofit)
        gitHubImplementation(retrofitAdapterRxJava)
        gitHubImplementation(retrofitConverterGson)
    }

    // KAPT
    with(Dependencies.Kapt) {
        kapt(hiltCompiler)
        kapt(hiltCompilerAndroid)
        kapt(roomCompiler)
    }

    // Testing
    with(Dependencies.Testing) {
        testImplementation(junit)
    }
}

repositories {
    mavenCentral()
}
