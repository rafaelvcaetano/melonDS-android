// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        with(Dependencies.GradlePlugins) {
            classpath(gradle)
            classpath(hiltAndroid)
            classpath(kotlin)
        }

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

plugins {
    with(Dependencies.GradlePlugins) {
        id(ksp) version Dependencies.Versions.Ksp apply false
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
