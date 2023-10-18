package com.android.designcompose.cargoplugin

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.configurationcache.extensions.capitalized
import java.io.File


/**
 * Create cargo task
 *
 * @param this@createAndroidCargoTask The project to create the task in
 * @param cargoExtension Configuration for this plugin
 * @param variant Android build variant that this task will build for
 * @param abi The Android ABI to compile
 * @param ndkDir The directory containing the NDK tools
 */
fun Project.registerAndroidCargoTask(
    cargoExtension: CargoPluginExtension,
    buildType: CargoBuildType,
    compileApi: Int,
    abi: String,
    ndkDir: Provider<Directory>
): TaskProvider<CargoBuildAndroidTask> = tasks.register(
    "cargoBuild${abi.capitalized()}${buildType.toString().capitalized()}",
    CargoBuildAndroidTask::class.java
) { task ->
    task.applyCommonCargoConfig(cargoExtension, this, buildType)
    task.androidAbi.set(abi)
    task.ndkDirectory.set(ndkDir)
    task.compileApi.set(compileApi)
}

fun Project.registerHostCargoTask(
    cargoExtension: CargoPluginExtension, buildType: CargoBuildType
): TaskProvider<CargoBuildHostTask> = tasks.register(
    "cargoBuildHost${buildType.toString().capitalized()}", CargoBuildHostTask::class.java
) { task ->
    task.applyCommonCargoConfig(cargoExtension, this, buildType)
    task.outLibDir.set(cargoExtension.hostLibsOut.dir(buildType.toString()))
}

private fun CargoBuildBaseTask.applyCommonCargoConfig(
    cargoExtension: CargoPluginExtension, project: Project, theBuildType: CargoBuildType
) {
    // Set the cargoBinary location from the configured plugin extension, or default to
    // the standard install location
    cargoBin.set(cargoExtension.cargoBin.orElse(project.providers.systemProperty("user.home").map {
        File(it, ".cargo/bin/cargo")
    }))

    rustSrcs.from(cargoExtension.crateDir).filterNot { file ->
        file.name == "target"
    }

    hostOS.set(
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            if (Os.isArch("x86_64") || Os.isArch("amd64")) {
                "windows-x86_64"
            } else {
                "windows"
            }
        } else if (Os.isFamily(Os.FAMILY_MAC)) {
            "darwin-x86_64"
        } else {
            "linux-x86_64"
        }
    )

    buildType.set(theBuildType)

    cargoTargetDir.set(project.layout.buildDirectory.map { it.dir("intermediates/cargoTarget") })
    group = "build"
    // Try to get the cargo build started earlier in the build execution.
    shouldRunAfter(project.tasks.named("preBuild"))
}
