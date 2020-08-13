package com.anatawa12.decompiler.signatures

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Opcodes

class SClass {
    private val flags = 0
    internal val environment: SClassLoaderEnvironment
    val descriptor: String
    //private val signature: String? = null
    //private val signatureInfo: SClassSignature? = null

    /**
     * simple class constructor
     */
    internal constructor(environment: SClassLoaderEnvironment, classLoader: SClassLoader?, bytes: ByteArray) {
        val cr = ClassReader(bytes)

        var theName: String? = null
        var superclass: SClass? = null
        var interfacesList: List<SClass> = emptyList()
        val fields = mutableListOf<SField>()

        cr.accept(object : ClassVisitor(Opcodes.ASM8) {
            override fun visit(
                version: Int,
                access: Int,
                name: String?,
                signature: String?,
                superName: String?,
                interfaces: Array<out String>?,
            ) {
                theName = name
                superclass = superName?.let { environment.forNameOrNull(classLoader, it) }
                interfacesList = interfaces?.mapNotNull { environment.forNameOrNull(classLoader, it) }.orEmpty()
            }

            override fun visitField(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                value: Any?,
            ): FieldVisitor? {
                fields += SField(
                    this@SClass,
                    access,
                    name,
                    descriptor,
                    signature,
                    value,
                )
                return null
            }
        }, 0)

        this.environment = environment
        this.classLoader = classLoader
        this.superclass = superclass
        this.interfaces = interfacesList.toImmutableList()
        this.descriptor = "L$theName;"
        this.componentType = null
        this.fields = fields.toImmutableList()
    }

    /**
     * array class constructor
     */
    internal constructor(environment: SClassLoaderEnvironment, elementClass: SClass) {
        check(elementClass != environment.voidType) { "cannot create array of void" }
        this.environment = environment
        this.classLoader = elementClass.classLoader
        this.superclass = environment.forName(elementClass.classLoader, "java.lang.Object")
        this.interfaces = persistentListOf()
        this.descriptor = "[${elementClass.descriptor}"
        this.componentType = elementClass
        this.fields = persistentListOf()
    }

    /**
     * primitive class constructor
     */
    internal constructor(environment: SClassLoaderEnvironment, desc: String) {
        this.environment = environment
        this.classLoader = null
        this.superclass = null
        this.interfaces = persistentListOf()
        this.descriptor = desc
        this.componentType = null
        this.fields = persistentListOf()
    }

    override fun toString(): String = name

    fun isAssignableFrom(cls: SClass): Boolean {
        if (this == cls) return true
        // a instanceof B
        // B.isAssignableFrom(A)
        // return true if there is B in A's super classes
        return (cls.superclass?.isAssignableFrom(cls) ?: false)
                || (cls.interfaces.any { it.isAssignableFrom(cls) })
    }

    val isInterface get() = flags and INTERFACE != 0
    val isImmutableList get() = descriptor[0] == '['
    val isPrimitive get() = descriptor[0] != '[' && descriptor[0] != 'L'
    val isAnnotation get() = flags and ANNOTATION != 0
    val isSynthetic get() = flags and SYNTHETIC != 0

    val name: String by lazy(::computeName)

    private fun computeName(): String = when (descriptor[0]) {
        'L' -> descriptor.substring(1, descriptor.length - 1).replace('/', '.')
        '[' -> descriptor.replace('/', '.')
        'Z' -> "boolean"
        'B' -> "byte"
        'C' -> "char"
        'D' -> "double"
        'F' -> "float"
        'I' -> "int"
        'J' -> "long"
        'S' -> "short"
        else -> assertErr("invalid descriptor")
    }

    val classLoader: SClassLoader?

    //override val typeParameters: ImmutableList<STypeVariable<SClass>> get() = signatureInfo?.typeParameters ?: persistentListOf()
    val superclass: SClass?
    //val genericSuperclass: SType? get() = signatureInfo?.genericSuperclass ?: superclass

    val interfaces: ImmutableList<SClass>
    //val genericInterfaces: ImmutableList<SType> get() = signatureInfo?.genericInterfaces ?: interfaces

    val componentType: SClass?

    val modifiers: Int get() = flags and 0xFFFF

    //val enclosingMethod: SMethod?
    //val enclosingConstructor: SConstructor?
    //val declaringClass: SClass?
    //val enclosingClass: SClass?

    //val simpleName: String
    //val canonicalName: String?

    //val isAnonymousClass get(): Boolean
    //val isLocalClass get(): Boolean
    //val isMemberClass get(): Boolean

    //val annotations: ImmutableList<SAnnotation?>?
    //val classes: ImmutableList<SClass?>?
    //val constructors: ImmutableList<SConstructor?>?
    //val declaredAnnotations: ImmutableList<Annotation?>?
    //val declaredClasses: ImmutableList<SClass?>?
    //val declaredConstructors: ImmutableList<SConstructor?>?
    //val declaredFields: ImmutableList<SField?>?
    //val declaredMethods: ImmutableList<SMethod?>?
    //val enumConstants: ImmutableList<T?>?
    val fields: ImmutableList<SField>
    //val methods: ImmutableList<SMethod>

    //fun getAnnotation(annotationClass: SClass): SAnnotation?
    //fun getConstructor(vararg parameterTypes: SClass?): SConstructor?
    //fun getDeclaredConstructor(vararg parameterTypes: SClass?): SConstructor?
    //fun getDeclaredField(name: String?): SField?
    //fun getDeclaredMethod(name: String?, vararg parameterTypes: SClass?): SMethod?
    //fun getField(name: String?): SField?
    //fun getMethod(name: String?, vararg parameterTypes: SClass?): SMethod?
    //fun isAnnotationPresent(annotationClass: Class<out Annotation?>?): Boolean
    //fun isEnum(): Boolean

    internal val arrayClass: SClass by lazy(::computeArrayClass)

    private fun computeArrayClass() = SClass(environment, this)

    @Suppress("NOTHING_TO_INLINE")
    private inline fun assertErr(s: String): Nothing = throw AssertionError(s)

    companion object {
        private const val INTERFACE = Opcodes.ACC_INTERFACE
        private const val ANNOTATION = Opcodes.ACC_ANNOTATION
        private const val SYNTHETIC = Opcodes.ACC_SYNTHETIC
    }
}
