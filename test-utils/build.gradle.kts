plugins {
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)

    id("designcompose.conventions.base")
}

android {
    namespace = "com.android.designcompose.test"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
}

dependencies {
    implementation(project(":host-jni"))
    implementation(project(":common"))
}
