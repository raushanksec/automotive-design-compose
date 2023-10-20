/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    kotlin("android")
    id("com.android.library")
    id("com.android.designcompose.rust-in-android")
    alias(libs.plugins.ksp) // For testing a fetch of HelloWorld
    alias(libs.plugins.designcompose) // Allows us to get the Figma Token

    // Plugins from our buildSrc
    id("designcompose.conventions.base")
    id("designcompose.conventions.publish.android")
    id("designcompose.conventions.android-test-devices")
    id("designcompose.conventions.roborazzi")
}

@Suppress("UnstableApiUsage")
android {
    namespace = "com.android.designcompose"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = "25.2.9519653"

    testFixtures { enable = true }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-proguard-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (designcompose.figmaToken.isPresent) {
            testInstrumentationRunnerArguments["FIGMA_ACCESS_TOKEN"] =
                designcompose.figmaToken.get()
        }
    }

    buildFeatures { compose = true }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
    }
    packaging {
        resources.excludes.apply {
            add("META-INF/LICENSE*")
            add("META-INF/AL2.0")
            add("META-INF/LGPL2.1")
        }
    }
}

// To simplify publishing of the entire SDK, make the DesignCompose publish tasks depend on the
// Gradle Plugin's publish tasks
// Necessary because the plugin must be in a separate Gradle build
listOf("publish", "publishToMavenLocal", "publishAllPublicationsToLocalDirRepository").forEach {
    tasks.named(it) { dependsOn(gradle.includedBuild("plugins").task(":gradle-plugin:${it}")) }
}

// Defines the configuration for the Rust JNI build
cargo {
    crateDir.set(File(rootProject.relativePath("../crates/jni")))
    abi.add("x86") // Older Emulated devices, including the ATD Android Test device
    abi.add("x86_64") // Most Emulated Android Devices
    abi.add("armeabi-v7a")
    abi.add("arm64-v8a")
}

//afterEvaluate {
//    println("outputFile: ${tasks.named("cargoBuildHostDebug").get().outputs.files.files}")
//    println("hostlibs artifacts: ${configurations.named("hostLibs").get().allArtifacts.files.files}")
//}

//afterEvaluate {
//    println(tasks.named("cargoBuildHostDebug").get().outputs.files.files) }

dependencies {
    // Our code
    api(project(":common"))
    api(project(":annotation"))
    ksp(libs.designcompose.codegen)

    // The following dependencies are required to support the code generated by
    // the codegen library, and so are included in the `api` configuration. Meaning they are
    // included in the POM for DesignCompose as transitive dependencies
    // (Ideally these would be dependencies of codegen but it does not build an android library)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.text)

    // Dependencies that
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.guavaAndroid)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(kotlin("test"))
    androidTestImplementation(libs.google.truth)
    androidTestImplementation(libs.mockk.agent)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    testFixturesImplementation(libs.androidx.test.ext.junit)
}
