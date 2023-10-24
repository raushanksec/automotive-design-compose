package com.android.designcompose.common

import java.lang.RuntimeException

object JniLoader {
    private var loaded = false

    fun loadLibrary(loader: () -> Unit) {
        if (loaded) {
            println(
                "Attempted to load libjni a second time, ignoring the attempt"
            )
            return
        }
        loader.invoke()
        // Let it throw an exception if it fails, there's nothing a user could do to remedy a
        // failure
        loaded = true
    }
    var loadAlternateImpl: (() -> Unit)? = null


    fun loadDefaultImpl() {
        if(System.getProperty("java.vendor")?.contains("Android") == true){
            loadAndroidImpl()
        }
        else if (loadAlternateImpl != null) loadAlternateImpl!!.invoke()
        else throw RuntimeException("No method for loading the JNI is set")
    }

    fun loadAndroidImpl() {
        loadLibrary { System.loadLibrary("jni") }
        println("Loaded Android JNI interface")
    }
}