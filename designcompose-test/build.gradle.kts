import com.android.designcompose.cargoplugin.CargoBuildHostTask
import com.android.designcompose.cargoplugin.CargoBuildType
import com.android.designcompose.cargoplugin.CargoPluginExtension
import com.android.designcompose.cargoplugin.registerHostCargoTask

plugins {
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.ksp)

    //    kotlin("jvm")
    //    `java-library`
    id("com.android.designcompose.rust-in-android")
    id("designcompose.conventions.base")
}
// Defines the configuration for the Rust JNI build
cargo {
    crateDir.set(File(rootProject.relativePath("../crates/jni")))
    //    abi.add("x86_64") // Most Emulated Android Devices
}
val cargoHostTask =
    registerHostCargoTask(extensions.getByName<CargoPluginExtension>("cargo"), CargoBuildType.DEBUG)

android {
    namespace = "com.android.designcompose.test"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = "25.2.9519653"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
    }

    sourceSets.named("main") {
        resources { srcDir(cargoHostTask.flatMap { it.outLibDir}) }
        assets { srcDir(project(":helloworld-app").layout.projectDirectory.dir("src/main/assets")) }
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }


}
androidComponents.onVariants { it.sources.resources?.addGeneratedSourceDirectory(cargoHostTask, CargoBuildHostTask::outLibDir) }




// sourceSets{
//    main{
//        resources{
//            srcDir(cargoHostTask)
//        }
//    }
// }
//
dependencies {
    implementation(project(":designcompose"))
    ksp(project(":codegen"))

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.androidx.test.espresso.core)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
