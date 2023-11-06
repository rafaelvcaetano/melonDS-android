import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
    id("org.jetbrains.kotlin.android")
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

    namespace = "me.magnum.melonds"
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
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
        vectorDrawables.useSupportLibrary = true
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
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    flavorDimensions.add("version")
    flavorDimensions.add("build")
    productFlavors {
        create("playStore") {
            dimension = "version"
            versionNameSuffix = " PS"
        }
        create("gitHub") {
            dimension = "version"
            isDefault = true
            versionNameSuffix = " GH"
        }

        create("prod") {
            dimension = "build"
            isDefault = true
        }
        create("nightly") {
            dimension = "build"
            applicationIdSuffix = ".nightly"
            versionNameSuffix = " (NIGHTLY)"
        }
    }
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true

        kotlin {
            jvmToolchain(17)
            kotlinOptions {
                freeCompilerArgs += "-opt-in=kotlin.ExperimentalUnsignedTypes"
            }
        }
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

    with(Dependencies.Modules) {
        implementation(project(masterSwitchPreference))
        implementation(project(rcheevosApi))
        implementation(project(common))
    }

    with(Dependencies.Kotlin) {
        implementation(kotlinStdlib)
    }

    // AndroidX
    with(Dependencies.AndroidX) {
        implementation(activity)
        implementation(activityCompose)
        implementation(appCompat)
        implementation(camera2)
        implementation(cameraLifecycle)
        implementation(cardView)
        implementation(constraintLayout)
        implementation(core)
        implementation(documentFile)
        implementation(fragment)
        implementation(hiltWork)
        implementation(lifecycleViewModel)
        implementation(lifecycleViewModelCompose)
        implementation(preference)
        implementation(recyclerView)
        implementation(room)
        implementation(roomKtx)
        implementation(roomRxJava)
        implementation(splashscreen)
        implementation(swipeRefreshLayout)
        implementation(work)
        implementation(workRxJava)
        implementation(material)
    }

    with(Dependencies.Compose) {
        implementation(platform(bom))
        implementation(accompanistPagerIndicators)
        implementation(accompanistSystemUiController)
        implementation(foundation)
        implementation(material)
        implementation(ui)
        implementation(uiToolingPreview)

        debugImplementation(uiTooling)
    }

    // Third-party
    with(Dependencies.ThirdParty) {
        implementation(coil)
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

    with(Dependencies.Ksp) {
        ksp(hiltCompiler)
        ksp(hiltCompilerAndroid)
        ksp(roomCompiler)
    }

    // Testing
    with(Dependencies.Testing) {
        testImplementation(junit)
    }
}

repositories {
    mavenCentral()
}
