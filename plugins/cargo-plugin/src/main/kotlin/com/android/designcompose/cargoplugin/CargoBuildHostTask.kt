package com.android.designcompose.cargoplugin

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class CargoBuildHostTask @Inject constructor(private val executor: ExecOperations) :
    CargoBuildTask() {

    @TaskAction
    fun runCommand() {
        cargoTargetDir.get().asFile.mkdirs()

        executor.exec { baseExecOptions(it) }

        fs.copy {
            it.from(cargoTargetDir.get().dir(buildType.get().toString()))
            it.include("*.so")
            it.into(outLibDir)
        }
    }

    companion object {
        fun makeTaskName(buildType: CargoBuildType) =
            "$cargoBuildHostTaskBaseName${buildType.toString().capitalized()}"

        fun makeTaskOutputProvider(
            cargoExtension: CargoPluginExtension,
            buildType: CargoBuildType
        ): Provider<Directory> = cargoExtension.hostLibsOut.dir(buildType.toString())
    }
}

fun Project.registerHostCargoTask(
    cargoExtension: CargoPluginExtension,
    buildType: CargoBuildType
): TaskProvider<CargoBuildHostTask> {

    return tasks.register(
        CargoBuildHostTask.makeTaskName(buildType),
        CargoBuildHostTask::class.java
    ) { task ->
        task.applyBaseConfig(cargoExtension, this, buildType)
        task.outLibDir.set(CargoBuildHostTask.makeTaskOutputProvider(cargoExtension, buildType))
    }
}
