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

package com.android.designcompose.cargoplugin

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.LibraryVariant
import com.android.builder.model.PROPERTY_BUILD_ABI
import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.configurationcache.extensions.capitalized
inline fun <reified T : Named> Project.namedAttribute(value: String) = objects.named(T::class.java, value)

/**
 * Cargo plugin
 *
 * Creates and wires up tasks for compiling Rust code with the NDK. Currently only supports Android
 * libraries.
 */
@Suppress("unused")
class CargoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val cargoExtension = project.initializeExtension()
        // Filter the ABIs using configurable Gradle properties
        val activeAbis = getActiveAbis(cargoExtension.abi, project)

        project.configurations.create("hostLibs") {
            it.isCanBeConsumed = true
            it.isCanBeResolved = false
            it.attributes {attr ->
                attr.attribute(Category.CATEGORY_ATTRIBUTE,project.namedAttribute(Category.LIBRARY))
                attr.attribute(Usage.USAGE_ATTRIBUTE, project.namedAttribute(Usage.NATIVE_RUNTIME))
                attr.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.namedAttribute("hostLibs"))
                // Add MachineArchitecture and OS attributes
            }
        }
//        project.afterEvaluate {
            val cargoDebugHostTask =
                project.registerHostCargoTask(cargoExtension, CargoBuildType.DEBUG)
            val cargoReleaseHostTask =
                project.registerHostCargoTask(cargoExtension, CargoBuildType.RELEASE)

            project.artifacts { artifacts ->
                artifacts.add("hostLibs", cargoDebugHostTask)
            }
        println("hello")
//        }

        // withPlugin(String) will do the action once the plugin is applied, or immediately
        // if the plugin is already applied
        project.pluginManager.withPlugin("com.android.library") {
            project.extensions
                .getByType(LibraryAndroidComponentsExtension::class.java)
                .configureCargoPlugin(
                    project,
                    cargoExtension,
                    activeAbis,
                )
        }
    }

    private fun LibraryAndroidComponentsExtension.configureCargoPlugin(
        project: Project,
        cargoExtension: CargoPluginExtension,
        activeAbis: Provider<Set<String>>
    ) {
        val ndkDir = this.findNdkDirectory(project)

        finalizeDsl { dsl ->
            dsl.testOptions.unitTests.all { testTask ->
                val buildType =
                    if (testTask.name.contains("debug", ignoreCase = true)) CargoBuildType.DEBUG
                    else CargoBuildType.RELEASE

                testTask.systemProperty(
                    "java.library.path",
                    cargoExtension.hostLibsOut.dir(buildType.toString()).get().toString()
                )
                testTask.dependsOn(
                    project.tasks.named(
                        "$cargoBuildHostTaskBaseName${buildType.toString().capitalized()}"
                    )
                )
            }
        }

        // Create one task per variant and ABI
        onVariants { variant ->
            val buildType =
                if (variant.buildType == "release") CargoBuildType.RELEASE else CargoBuildType.DEBUG
            cargoExtension.abi.get().forEach { abi ->
                val cargoTask =
                    project.registerAndroidCargoTask(
                        cargoExtension,
                        buildType,
                        variant.minSdk.apiLevel,
                        abi,
                        ndkDir
                    )

                // If building a release or the ABI is active, add the task to the build
                if (variant.buildType == "release" || activeAbis.get().contains(abi)) {
                    addDependencyOnTask(variant, cargoTask, project)
                }
            }
        }
    }

    /**
     * Add dependency on task
     *
     * @param variant The build variant
     * @param cargoTask The task to add
     * @param project The full project
     */
    private fun addDependencyOnTask(
        variant: LibraryVariant,
        cargoTask: TaskProvider<CargoBuildAndroidTask>,
        project: Project
    ) {
        with(variant.sources.jniLibs) {
            if (this != null) {
                // Add the result to the variant's JNILibs sources. This is all we need to
                // do to make sure the JNILibs are compiled and included in the library
                this.addGeneratedSourceDirectory(cargoTask, CargoBuildAndroidTask::outLibDir)
            } else
                project.logger.error(
                    "No JniLibs configured by Android Gradle Plugin, Cargo tasks may not run"
                )
        }
    }

    /**
     * Get active abis
     *
     * @param configuredAbis The list of ABIs configured for the plugin
     * @param project The project to work on
     * @return
     */
    private fun getActiveAbis(
        configuredAbis: Provider<MutableSet<String>>,
        project: Project
    ): Provider<Set<String>> =
        configuredAbis.map {
            if (project.findProperty(PROPERTY_ALLOW_ABI_OVERRIDE)?.toString() == "true") {
                selectActiveAbis(
                    configuredAbis.get(),
                    project.findProperty(PROPERTY_BUILD_ABI)?.toString(),
                    project.findProperty(PROPERTY_ABI_FILTER)?.toString()
                )
            } else {
                it
            }
        }

    /**
     * Find ndk directory for a given Android configuration
     *
     * Given the configured android.ndkVersion, will return the path to the directory containing it.
     * We should be able to remove this once b/278740309 is fixed.
     *
     * @param project
     * @param this@findNdkDirectory Android Components Extension
     * @return
     */
    @Suppress("UnstableApiUsage")
    private fun LibraryAndroidComponentsExtension.findNdkDirectory(
        project: Project
    ): DirectoryProperty {
        val ndkDir = project.objects.directoryProperty()

        finalizeDsl { androidDsl ->
            val ndkVersion =
                androidDsl.ndkVersion ?: throw GradleException("android.ndkVersion must be set!")            // https://blog.rust-lang.org/2023/01/09/android-ndk-update-r25.html
            if (ndkVersion.substringBefore(".").toInt() < 25)
                throw GradleException("ndkVersion must be at least r25")
            ndkDir.set(sdkComponents.sdkDirectory.map { it.dir("ndk/$ndkVersion") })
        }
        return ndkDir
    }
}

/**
 * Select active abis
 *
 * @param configuredAbis The Abis configured in the plugin
 * @param androidInjectedAbis The Abis injected by an Android Studio Build
 * @param abiFilter The Abi filter set via gradle property
 * @return
 */
internal fun selectActiveAbis(
    configuredAbis: Set<String>,
    androidInjectedAbis: String?,
    abiFilter: String?
): Set<String> {
    return if (androidInjectedAbis != null) {
        // Android injects two ABIs for emulators. The first is the architecture of the host, the
        // second is the architecture of the device that the AVD is emulating. We just want the
        // host's architecture
        val abi = androidInjectedAbis.split(",").first()
        if (configuredAbis.contains(abi)) setOf(abi)
        else throw GradleException("Unknown injected build ABI: $abi")
    } else if (abiFilter != null) {
        abiFilter
            .split(",")
            .map {
                if (!configuredAbis.contains(it)) throw GradleException("Unknown abiOverride: $it")
                else it
            }
            .toSet()
    } else configuredAbis
}
