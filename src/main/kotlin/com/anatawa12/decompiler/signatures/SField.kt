package com.anatawa12.decompiler.signatures

import org.objectweb.asm.Opcodes

class SField {
    constructor(
        declaringClass: SClass,
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        value: Any?,
    ) {
        this.declaringClass = declaringClass
        this.flags = access
        this.name = name
        this.descriptor = desc
        this.constantValue = value
    }

    private val flags: Int

    val isStatic: Boolean get() = flags and Opcodes.ACC_STATIC != 0

    val declaringClass: SClass
    val name: String
    val modifiers get() = flags and 0xFFFF
    val isEnumConstant get() = flags and Opcodes.ACC_ENUM != 0
    val isSynthetic get() = flags and Opcodes.ACC_SYNTHETIC != 0

    /** is cannot resolve, returns null */
    val type: SClass? by lazy(::computeType)
    val descriptor: String
    // val genericType: SType

    val constantValue: Any?

    private fun computeType() = declaringClass.environment.forDescriptor(declaringClass.classLoader, descriptor)
}
