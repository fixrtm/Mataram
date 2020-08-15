package com.anatawa12.decompiler.statementsGen

import org.objectweb.asm.Opcodes

data class MethodCoreSignature(
    val owner: String,
    val access: Int,
    val name: String,
    val desc: String,
) {
    val isPublic get() = access and Opcodes.ACC_PUBLIC != 0
    val isPrivate get() = access and Opcodes.ACC_PRIVATE != 0
    val isProtected get() = access and Opcodes.ACC_PROTECTED != 0
    val isStatic get() = access and Opcodes.ACC_STATIC != 0
    val isFinal get() = access and Opcodes.ACC_FINAL != 0
    val isSynchronized get() = access and Opcodes.ACC_SYNCHRONIZED != 0
    val isBridge get() = access and Opcodes.ACC_BRIDGE != 0
    val isVarargs get() = access and Opcodes.ACC_VARARGS != 0
    val isNative get() = access and Opcodes.ACC_NATIVE != 0
    val isAbstract get() = access and Opcodes.ACC_ABSTRACT != 0
    val isStrict get() = access and Opcodes.ACC_STRICT != 0
    val isSynthetic get() = access and Opcodes.ACC_SYNTHETIC != 0
    val isMandated get() = access and Opcodes.ACC_MANDATED != 0

    override fun toString(): String = buildString {
        if (isPublic) append("public ")
        if (isPrivate) append("private ")
        if (isProtected) append("protected ")
        if (isStatic) append("static ")
        if (isFinal) append("final ")
        if (isSynchronized) append("synchronized ")
        if (isBridge) append("bridge ")
        if (isVarargs) append("varargs ")
        if (isNative) append("native ")
        if (isAbstract) append("abstract ")
        if (isStrict) append("strict ")
        if (isSynthetic) append("synthetic ")
        if (isMandated) append("mandated ")
        append(owner)
        append('.')
        append(name)
        append('/')
        append(desc)
    }
}
