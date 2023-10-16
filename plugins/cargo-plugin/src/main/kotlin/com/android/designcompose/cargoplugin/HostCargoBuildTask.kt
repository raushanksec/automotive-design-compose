package com.android.designcompose.cargoplugin

import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class HostCargoBuildTask  @Inject constructor(private val executor: ExecOperations) :
    BaseCargoBuildTask() {


    @TaskAction fun runCommand() {
        cargoTargetDir.get().asFile.mkdirs()

        executor.exec {
            baseExecOptions(it)
        }

        fs.copy {
            it.from(cargoTargetDir.get().dir(buildType.get().toString()))
            it.include("*.so")
            it.into(outLibDir.get().dir(buildType.get().toString()))
        }
    }
}