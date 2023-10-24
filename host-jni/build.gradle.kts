import com.android.designcompose.cargoplugin.CargoBuildType
import com.android.designcompose.cargoplugin.CargoPluginExtension
import com.android.designcompose.cargoplugin.registerHostCargoTask

plugins {
        kotlin("jvm")
        `java-library`
    id("com.android.designcompose.rust-in-android")

}
// Defines the configuration for the Rust JNI build
cargo {
    crateDir.set(File(rootProject.relativePath("../crates/jni")))
    //    abi.add("x86_64") // Most Emulated Android Devices
}
val cargoHostTask =
    registerHostCargoTask(extensions.getByName<CargoPluginExtension>("cargo"), CargoBuildType.DEBUG)

sourceSets.main{
    resources.srcDir(cargoHostTask)
}