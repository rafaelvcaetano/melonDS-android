plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.common)

    implementation(libs.gson)
    implementation(libs.okhttp)
}