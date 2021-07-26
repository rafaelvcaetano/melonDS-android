object Dependencies {
    private object Versions {
        const val Kotlin = "1.5.10"
        const val HiltX = "1.0.0"
        const val Hilt = "2.35.1"
        const val Room = "2.3.0"
        const val Work = "2.5.0"
        const val Markwon = "4.6.2"
        const val Retrofit = "2.9.0"
    }

    object GradlePlugins {
        const val gradle = "com.android.tools.build:gradle:4.2.2"
        const val hiltAndroid = "com.google.dagger:hilt-android-gradle-plugin:${Versions.Hilt}"
        const val kotlin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.Kotlin}"
    }

    object Kotlin {
        const val kotlinStdlib = "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${Versions.Kotlin}"
    }

    object AndroidX {
        const val activity = "androidx.activity:activity-ktx:1.2.3"
        const val appCompat = "androidx.appcompat:appcompat:1.3.0"
        const val cardView = "androidx.cardview:cardview:1.0.0"
        const val constraintLayout = "androidx.constraintlayout:constraintlayout:2.0.4"
        const val core = "androidx.core:core-ktx:1.6.0"
        const val documentFile = "androidx.documentfile:documentfile:1.0.1"
        const val fragment = "androidx.fragment:fragment-ktx:1.3.5"
        const val hiltWork = "androidx.hilt:hilt-work:${Versions.HiltX}"
        const val lifecycleExtensions = "androidx.lifecycle:lifecycle-extensions:2.2.0"
        const val lifecycleViewModel = "androidx.lifecycle:lifecycle-viewmodel-ktx:2.3.1"
        const val preference = "androidx.preference:preference-ktx:1.1.1"
        const val recyclerView = "androidx.recyclerview:recyclerview:1.2.1"
        const val room = "androidx.room:room-runtime:${Versions.Room}"
        const val roomRxJava = "androidx.room:room-rxjava2:${Versions.Room}"
        const val swipeRefreshLayout = "androidx.swiperefreshlayout:swiperefreshlayout:1.1.0"
        const val work = "androidx.work:work-runtime-ktx:${Versions.Work}"
        const val workRxJava = "androidx.work:work-rxjava2:${Versions.Work}"
        const val material = "com.google.android.material:material:1.4.0"
    }

    object ThirdParty {
        const val masterSwitchPreference = "com.github.svenoaks:MasterSwitchPreference:0.9.1"
        const val flexbox = "com.google.android:flexbox:2.0.1"
        const val gson = "com.google.code.gson:gson:2.8.6"
        const val hilt = "com.google.dagger:hilt-android:${Versions.Hilt}"
        const val picasso = "com.squareup.picasso:picasso:2.71828"
        const val markwon = "io.noties.markwon:core:${Versions.Markwon}"
        const val markwonImagePicasso = "io.noties.markwon:image-picasso:${Versions.Markwon}"
        const val markwonLinkify = "io.noties.markwon:linkify:${Versions.Markwon}"
        const val rxJava = "io.reactivex.rxjava2:rxjava:2.2.10"
        const val rxJavaAndroid = "io.reactivex.rxjava2:rxandroid:2.1.1"
        const val commonsCompress = "org.apache.commons:commons-compress:1.21"
        const val xz = "org.tukaani:xz:1.9"
    }

    object GitHub {
        const val retrofit = "com.squareup.retrofit2:adapter-rxjava2:${Versions.Retrofit}"
        const val retrofitAdapterRxJava = "com.squareup.retrofit2:adapter-rxjava2:${Versions.Retrofit}"
        const val retrofitConverterGson = "com.squareup.retrofit2:converter-gson:${Versions.Retrofit}"
    }

    object Kapt {
        const val hiltCompiler = "androidx.hilt:hilt-compiler:${Versions.HiltX}"
        const val hiltCompilerAndroid = "com.google.dagger:hilt-android-compiler:${Versions.Hilt}"
        const val roomCompiler = "androidx.room:room-compiler:${Versions.Room}"
    }

    object Testing {
        const val junit = "junit:junit:4.12"
        const val junitAndroidX = "androidx.test.ext:junit:1.1.1"
        const val espresso = "androidx.test.espresso:espresso-core:3.1.0"
    }
}