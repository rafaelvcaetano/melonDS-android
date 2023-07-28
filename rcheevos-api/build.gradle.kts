plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(15)
}

dependencies {
    with(Dependencies.ThirdParty) {
        implementation(gson)
        implementation(okHttp)
    }
}