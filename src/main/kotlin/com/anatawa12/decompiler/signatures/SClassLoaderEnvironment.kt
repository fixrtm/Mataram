package com.anatawa12.decompiler.signatures

import java.io.File
import java.net.URL

class SClassLoaderEnvironment(
    javaHomePath: String,
) {
    private val javaHomeFile: File = File(javaHomePath)
    private val jreFile: File

    internal val boostrapLoaderImpl: SClassLoader
    val systemClassLoader: SClassLoader

    init {
        if (javaHomeFile.resolve("jre").exists()) {
            jreFile = javaHomeFile.resolve("jre")
        } else {
            jreFile = javaHomeFile
        }

        require(jreFile.resolve("lib").exists()) { "JAVA_HOME/lib not found." }
        require(jreFile.resolve("lib").isDirectory) { "JAVA_HOME/lib is not directory." }

        val libJarUrls = jreFile.resolve("lib")
            .listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile }
            .filter { it.extension == "jar" }
            .map { URL("jar:${it.toURI()}!/") }
            .toList()

        val extJarUrls = jreFile.resolve("lib/ext")
            .listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile }
            .filter { it.extension == "jar" }
            .map { URL("jar:${it.toURI()}!/") }
            .toList()

        boostrapLoaderImpl = object : URLSClassLoader(this, libJarUrls) {
            @Synchronized
            override fun loadClassOrNull(name: String): SClass? {
                val loaded = findLoadedClass(name)
                if (loaded != null) return loaded
                return findClass(name)
            }
        }
        systemClassLoader = URLSClassLoader(this, boostrapLoaderImpl, extJarUrls)
    }

    fun forName(loader: SClassLoader?, name: String): SClass = (loader ?: boostrapLoaderImpl).loadClass(name)
    fun forNameOrNull(loader: SClassLoader?, name: String): SClass? =
        (loader ?: boostrapLoaderImpl).loadClassOrNull(name)

    fun forDescriptor(loader: SClassLoader?, desc: String): SClass {
        var i = 0
        var arrayDimensions = 0
        while (desc[i++] == '[') arrayDimensions++
        i--

        var theClass = when (desc[i]) {
            'Z' -> booleanType
            'B' -> byteType
            'C' -> charType
            'D' -> doubleType
            'F' -> floatType
            'I' -> intType
            'J' -> longType
            'S' -> shortType
            'V' -> voidType
            'L' -> forName(loader, desc.substring(i + 1, desc.length - 1).replace('/', '.'))
            else -> throw IllegalArgumentException("invalid class")
        }

        repeat(arrayDimensions) {
            theClass = theClass.arrayClass
        }

        return theClass
    }

    fun forDescriptorOrNull(loader: SClassLoader?, desc: String): SClass? {
        var i = 0
        var arrayDimensions = 0
        while (desc[i++] == '[') arrayDimensions++
        i--

        var theClass = when (desc[i]) {
            'Z' -> booleanType
            'B' -> byteType
            'C' -> charType
            'D' -> doubleType
            'F' -> floatType
            'I' -> intType
            'J' -> longType
            'S' -> shortType
            'V' -> voidType
            'L' -> forNameOrNull(loader, desc.substring(i + 1, desc.length - 1).replace('/', '.'))
            else -> throw IllegalArgumentException("invalid class")
        }

        repeat(arrayDimensions) {
            theClass = theClass?.arrayClass
        }

        return theClass
    }

    val voidType = SClass(this, "V")
    val booleanType = SClass(this, "Z")
    val byteType = SClass(this, "B")
    val charType = SClass(this, "C")
    val doubleType = SClass(this, "D")
    val floatType = SClass(this, "F")
    val intType = SClass(this, "I")
    val longType = SClass(this, "J")
    val shortType = SClass(this, "S")
}
