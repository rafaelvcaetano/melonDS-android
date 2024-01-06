object Dependencies {
    object Versions {
        const val Accompanist = "0.30.1"
        const val Activity = "1.8.2"
        const val AppCompat = "1.6.1"
        const val CameraX = "1.3.1"
        const val CardView = "1.0.0"
        const val Coil = "2.2.2"
        const val CommonsCompress = "1.21"
        const val ComposeBom = "2023.10.01"
        const val ConstraintLayout = "2.1.4"
        const val Core = "1.12.0"
        const val Desugar = "2.0.2"
        const val DocumentFile = "1.0.1"
        const val Flexbox = "3.0.0"
        const val Fragment = "1.6.2"
        const val Gradle = "8.2.0"
        const val Gson = "2.8.6"
        const val HiltX = "1.1.0"
        const val Hilt = "2.48"
        const val Junit = "4.12"
        const val Kotlin = "1.9.21"
        const val KotlinxCoroutines = "1.7.3"
        const val Ksp = "1.9.0-1.0.12"
        const val LifecycleViewModel = "2.6.2"
        const val Material = "1.7.0"
        const val OkHttp = "4.11.0"
        const val Picasso = "2.71828"
        const val Preference = "1.2.1"
        const val RecyclerView = "1.3.2"
        const val Room = "2.6.1"
        const val RxAndroid = "2.1.1"
        const val RxJava = "2.2.10"
        const val Splashscreen = "1.0.0"
        const val SwipeRefreshLayout = "1.1.0"
        const val Work = "2.9.0"
        const val Markwon = "4.6.2"
        const val Retrofit = "2.9.0"
        const val Xz = "1.9"
    }

    object GradlePlugins {
        const val gradle = "com.android.tools.build:gradle:${Versions.Gradle}"
        const val hiltAndroid = "com.google.dagger:hilt-android-gradle-plugin:${Versions.Hilt}"
        const val kotlin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.Kotlin}"
        const val ksp = "com.google.devtools.ksp"
    }

    object Tools {
        const val desugarJdkLibs = "com.android.tools:desugar_jdk_libs:${Versions.Desugar}"
    }

    object Kotlin {
        const val kotlinStdlib = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin}"
        const val coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.KotlinxCoroutines}"
    }

    object AndroidX {
        const val activity = "androidx.activity:activity-ktx:${Versions.Activity}"
        const val activityCompose = "androidx.activity:activity-compose:${Versions.Activity}"
        const val appCompat = "androidx.appcompat:appcompat:${Versions.AppCompat}"
        const val camera2 = "androidx.camera:camera-camera2:${Versions.CameraX}"
        const val cameraLifecycle = "androidx.camera:camera-lifecycle:${Versions.CameraX}"
        const val cardView = "androidx.cardview:cardview:${Versions.CardView}"
        const val constraintLayout = "androidx.constraintlayout:constraintlayout:${Versions.ConstraintLayout}"
        const val core = "androidx.core:core-ktx:${Versions.Core}"
        const val documentFile = "androidx.documentfile:documentfile:${Versions.DocumentFile}"
        const val fragment = "androidx.fragment:fragment-ktx:${Versions.Fragment}"
        const val hiltWork = "androidx.hilt:hilt-work:${Versions.HiltX}"
        const val lifecycleViewModel = "androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.LifecycleViewModel}"
        const val lifecycleViewModelCompose = "androidx.lifecycle:lifecycle-viewmodel-compose:${Versions.LifecycleViewModel}"
        const val preference = "androidx.preference:preference-ktx:${Versions.Preference}"
        const val recyclerView = "androidx.recyclerview:recyclerview:${Versions.RecyclerView}"
        const val room = "androidx.room:room-runtime:${Versions.Room}"
        const val roomKtx = "androidx.room:room-ktx:${Versions.Room}"
        const val roomRxJava = "androidx.room:room-rxjava2:${Versions.Room}"
        const val splashscreen = "androidx.core:core-splashscreen:${Versions.Splashscreen}"
        const val swipeRefreshLayout = "androidx.swiperefreshlayout:swiperefreshlayout:${Versions.SwipeRefreshLayout}"
        const val work = "androidx.work:work-runtime:${Versions.Work}"
        const val material = "com.google.android.material:material:${Versions.Material}"
    }

    object Compose {
        const val bom = "androidx.compose:compose-bom:${Versions.ComposeBom}"
        const val accompanistPagerIndicators = "com.google.accompanist:accompanist-pager-indicators:${Versions.Accompanist}"
        const val accompanistSystemUiController = "com.google.accompanist:accompanist-systemuicontroller:${Versions.Accompanist}"
        const val foundation = "androidx.compose.foundation:foundation"
        const val material = "androidx.compose.material:material"
        const val ui = "androidx.compose.ui:ui"
        const val uiTooling = "androidx.compose.ui:ui-tooling"
        const val uiToolingPreview = "androidx.compose.ui:ui-tooling-preview"
    }

    object ThirdParty {
        const val coil = "io.coil-kt:coil-compose:${Versions.Coil}"
        const val flexbox = "com.google.android.flexbox:flexbox:${Versions.Flexbox}"
        const val gson = "com.google.code.gson:gson:${Versions.Gson}"
        const val hilt = "com.google.dagger:hilt-android:${Versions.Hilt}"
        const val kotlinxCoroutinesRx = "org.jetbrains.kotlinx:kotlinx-coroutines-rx2:${Versions.KotlinxCoroutines}"
        const val picasso = "com.squareup.picasso:picasso:${Versions.Picasso}"
        const val markwon = "io.noties.markwon:core:${Versions.Markwon}"
        const val markwonImagePicasso = "io.noties.markwon:image-picasso:${Versions.Markwon}"
        const val markwonLinkify = "io.noties.markwon:linkify:${Versions.Markwon}"
        const val okHttp = "com.squareup.okhttp3:okhttp:${Versions.OkHttp}"
        const val rxJava = "io.reactivex.rxjava2:rxjava:${Versions.RxJava}"
        const val rxJavaAndroid = "io.reactivex.rxjava2:rxandroid:${Versions.RxAndroid}"
        const val commonsCompress = "org.apache.commons:commons-compress:${Versions.CommonsCompress}"
        const val xz = "org.tukaani:xz:${Versions.Xz}"
    }

    object GitHub {
        const val retrofit = "com.squareup.retrofit2:adapter-rxjava2:${Versions.Retrofit}"
        const val retrofitAdapterRxJava = "com.squareup.retrofit2:adapter-rxjava2:${Versions.Retrofit}"
        const val retrofitConverterGson = "com.squareup.retrofit2:converter-gson:${Versions.Retrofit}"
    }

    object Ksp {
        const val hiltCompiler = "androidx.hilt:hilt-compiler:${Versions.HiltX}"
        const val hiltCompilerAndroid = "com.google.dagger:hilt-android-compiler:${Versions.Hilt}"
        const val roomCompiler = "androidx.room:room-compiler:${Versions.Room}"
    }

    object Modules {
        const val masterSwitchPreference = ":masterswitch"
        const val rcheevosApi = ":rcheevos-api"
        const val common = ":common"
    }

    object Testing {
        const val junit = "junit:junit:${Versions.Junit}"
    }
}