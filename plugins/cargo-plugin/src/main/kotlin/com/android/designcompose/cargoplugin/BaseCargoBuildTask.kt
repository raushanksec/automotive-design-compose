package com.android.designcompose.cargoplugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecSpec
import java.io.File
import javax.inject.Inject



enum class CargoBuildType {
    RELEASE,
    DEBUG;
    override fun toString() = if(this == RELEASE) "release" else "debug"
}

@UntrackedTask(
    because =
    "Cargo has it's own up-to-date checks. Trying to reproduce them so that we don't need to run Cargo is infeasible, and any errors will cause out-of-date code to be included"
)
abstract class BaseCargoBuildTask : DefaultTask() {
    @get:Inject
    abstract val fs: FileSystemOperations

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val rustSrcs: ConfigurableFileCollection

    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:InputFile
    abstract val cargoBin: Property<File>

    @get:Input
    abstract val hostOS: Property<String>

    @get:Input
    abstract val buildType: Property<CargoBuildType>

    @get:OutputDirectory
    abstract val outLibDir: DirectoryProperty

    @get:Internal
    abstract val cargoTargetDir: DirectoryProperty

    fun baseExecOptions(
        it: ExecSpec,
    ) {
        it.executable(cargoBin.get().absolutePath)
        it.workingDir(rustSrcs.asPath)

        it.args("build")
        it.args("--target-dir=${cargoTargetDir.get().asFile.absolutePath}")
        it.args("--quiet")
        if (buildType.get() == CargoBuildType.RELEASE) it.args("--release")
    }
}