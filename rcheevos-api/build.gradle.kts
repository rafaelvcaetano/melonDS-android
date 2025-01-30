plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.common)

    implementation(libs.gson)
    implementation(libs.okhttp)
}