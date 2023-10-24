package com.android.designcompose.test

import android.annotation.SuppressLint
import com.android.designcompose.common.JniLoader
import java.io.File
import kotlin.io.path.createTempDirectory


@SuppressLint("UnsafeDynamicallyLoadedCode") //Used in test code only
fun JniLoader.loadFromJar() {
   loadAlternateImpl= {

       val jniFromResources = javaClass.getResourceAsStream("/libjni.so")!!
       val tmpdir = createTempDirectory()
       val exportedJniFile = File(tmpdir.toFile(), "libjni.so")
       jniFromResources.copyTo(exportedJniFile.outputStream())

       System.load(exportedJniFile.absolutePath)

   }}