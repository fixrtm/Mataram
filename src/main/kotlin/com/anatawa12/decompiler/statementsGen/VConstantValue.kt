package com.anatawa12.decompiler.statementsGen

import com.anatawa12.decompiler.instructions.StackType
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Type
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodType

sealed class VConstantValue(val value: Any?, val theType: Type?, val stackType: StackType)
sealed class VConstantNumber : VConstantValue {
    val number: Number

    constructor(number: Number, theType: Type?, stackType: StackType) : super(number, theType, stackType) {
        this.number = number
    }

    constructor(number: Number, value: Any?, theType: Type?, stackType: StackType) : super(value, theType, stackType) {
        this.number = number
    }
}

object VConstantNull : VConstantValue(null, null, StackType.Object)
data class VConstantString(val string: String) :
    VConstantValue(string, Type.getType(String::class.java), StackType.Object)

data class VConstantInt(val int: Int) : VConstantNumber(int, Type.INT_TYPE, StackType.Integer)
data class VConstantLong(val long: Long) : VConstantNumber(long, Type.LONG_TYPE, StackType.Long)
data class VConstantFloat(val float: Float) : VConstantNumber(float, Type.FLOAT_TYPE, StackType.Float)
data class VConstantDouble(val double: Double) : VConstantNumber(double, Type.DOUBLE_TYPE, StackType.Double)
data class VConstantType(val type: Type) : VConstantValue(type, Type.getType(Class::class.java), StackType.Object)
data class VConstantMethodType(val type: Type) :
    VConstantValue(type, Type.getType(MethodType::class.java), StackType.Object)

data class VConstantHandle(val handle: Handle) :
    VConstantValue(handle, Type.getType(MethodHandle::class.java), StackType.Object)

// java constant value
data class VConstantByte(val byte: Byte) : VConstantNumber(byte, Type.BYTE_TYPE, StackType.Integer)
data class VConstantChar(val char: Char) : VConstantNumber(char.toInt(), char, Type.CHAR_TYPE, StackType.Integer)
data class VConstantShort(val short: Short) : VConstantNumber(short, Type.SHORT_TYPE, StackType.Integer)
data class VConstantBoolean(val boolean: Boolean) :
    VConstantNumber(if (boolean) 1 else 1, boolean, Type.BOOLEAN_TYPE, StackType.Integer)

class VConstantConstantDynamic(val dynamic: ConstantDynamic) :
    VConstantValue(dynamic, Type.getType(MethodHandle::class.java), StackType.Object)
