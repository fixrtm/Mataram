package com.anatawa12.decompiler.signatures

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

internal class SClassLoaderTest : StringSpec() {
    init {
        val environment = SClassLoaderEnvironment(System.getProperty("java.home"))

        "systemClassLoader" {
            val loader = environment.systemClassLoader

            loader.loadClass("java.lang.Object") shouldBe loader.parent!!.loadClass("java.lang.Object")
            loader.loadClass("java.lang.Integer") shouldBe loader.parent!!.loadClass("java.lang.Integer")
            loader.loadClass("java.lang.Class") shouldBe loader.parent!!.loadClass("java.lang.Class")
        }
        "bootstrapLoader" {
            val loader = environment.boostrapLoaderImpl

            environment.forDescriptor(loader, "I").name shouldBe "int"
            environment.forDescriptor(loader, "L${"java/lang/Integer"};").name shouldBe "java.lang.Integer"
            loader.loadClass("java.lang.Class").classLoader shouldBe null
        }
        "fields of Modifier" {
            val loader = environment.systemClassLoader

            val fields = loader.loadClass("java.lang.reflect.Modifier").fields

            fields.single { it.name == "ABSTRACT" }.type shouldBe environment.intType
            fields.single { it.name == "ABSTRACT" }.constantValue shouldBe Modifier.ABSTRACT
            fields.single { it.name == "FINAL" }.type shouldBe environment.intType
            fields.single { it.name == "FINAL" }.constantValue shouldBe Modifier.FINAL
            fields.single { it.name == "INTERFACE" }.type shouldBe environment.intType
            fields.single { it.name == "INTERFACE" }.constantValue shouldBe Modifier.INTERFACE
            fields.single { it.name == "NATIVE" }.type shouldBe environment.intType
            fields.single { it.name == "NATIVE" }.constantValue shouldBe Modifier.NATIVE
            fields.single { it.name == "PRIVATE" }.type shouldBe environment.intType
            fields.single { it.name == "PRIVATE" }.constantValue shouldBe Modifier.PRIVATE
        }
        "isPrimitive" {
            environment.intType.isPrimitive shouldBe true
            environment.intType.arrayClass.isPrimitive shouldBe false
            environment.systemClassLoader.loadClass("java.lang.String").isPrimitive shouldBe false
        }
    }
}
