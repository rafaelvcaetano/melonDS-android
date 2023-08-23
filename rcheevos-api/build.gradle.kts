plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    with(Dependencies.Modules) {
        implementation(project(common))
    }

    with(Dependencies.ThirdParty) {
        implementation(gson)
        implementation(okHttp)
    }
}