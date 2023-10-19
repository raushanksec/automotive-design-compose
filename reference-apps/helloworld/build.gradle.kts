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
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.ksp)
    alias(libs.plugins.designcompose)
    id("designcompose.conventions.base")
    id("designcompose.conventions.roborazzi")
}

var applicationID = "com.android.designcompose.testapp.helloworld"

@Suppress("UnstableApiUsage")
android {
    namespace = applicationID
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = applicationID
        minSdk = libs.versions.appMinSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (designcompose.figmaToken.isPresent) {
            testInstrumentationRunnerArguments["FIGMA_ACCESS_TOKEN"] =
                designcompose.figmaToken.get()
        }
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // We use a bundled debug keystore, to allow debug builds from CI to be upgradable
        named("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("debug") }

        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures { compose = true }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
    }

    packaging.resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
}

val hostLibs by
    configurations.creating {
        isCanBeConsumed = false
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))

            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.NATIVE_RUNTIME))
            attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                objects.named(LibraryElements::class.java, "hostLibs")
            )
        }
    }

// val hostLibsForTest =
//    hostLibs.resolvedConfiguration.resolvedArtifacts.joinToString(" ") { it.file.absolutePath }
//
// println(hostLibsForTest)
val hostLibsDir = layout.buildDirectory.dir("hostLibs")
println("hostLibsDir ${hostLibsDir.get()}")
//
val copyHostLibs by
    tasks.registering(Copy::class) {
        from(
            hostLibs)
//                println("Filecollection ${it.files.toString()}")
//            }
////            provider {
////                hostLibs.resolvedConfiguration.resolvedArtifacts.first().file
//////                    .also {
//////                    println("copyhostlibs: ${it}")
//////                }
////            }
//        )
        eachFile{
            println("copying file $sourceName")
        }
        into(hostLibsDir)
////        this.dependsOn(hostLibs.getTaskDependencyFrodmProjectDependency())
//        doLast{
//            println("haaaaloo")
//            println("DoLast: ${this.outputs.files.files}")
        }
//    }

android {
    testOptions.unitTests.all {
        it.systemProperty("java.library.path", hostLibsDir.get().toString())
//        it.systemProperty("java.library.path", hostLibs.fileCollection().singleFile.absolutePath)
        it.dependsOn(copyHostLibs)
    }
}

//afterEvaluate {
//    println(hostLibs.dependencies.buildDependencies.toString())
//    println(hostLibs.resolvedConfiguration.resolvedArtifacts.first().file)
//    println(hostLibs.artifacts.files.files)
//}
// afterEvaluate {
//    tasks.named("testDebugUnitTest") {
//        dependsOn(tasks.named(":designcompose:cargoBuildHostDebug"))
//    }
// }

dependencies {
    implementation(project(":designcompose"))
    androidTestImplementation(testFixtures(project(":designcompose")))
    ksp(project(":codegen"))

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.material)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    //    testImplementation(project(mapOf("path" to ":designcompose", "configuration" to
    // "hostLibs")))
    hostLibs(project(":designcompose"))

    //
    // testRuntimeOnly(files("../designcompose/build/intermediates/host_rust_libs/debug/libjni.so"))

    androidTestImplementation(kotlin("test"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.google.truth)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
