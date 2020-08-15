package com.anatawa12.decompiler.instructions

import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Type

sealed class InsnConstantValue(val value: Any?, val stackType: StackType)
object InsnConstantNull : InsnConstantValue(null, StackType.Object)
class InsnConstantString(val string: String) : InsnConstantValue(string, StackType.Object)
class InsnConstantInt(val int: Int) : InsnConstantValue(int, StackType.Integer)
class InsnConstantLong(val long: Long) : InsnConstantValue(long, StackType.Long)
class InsnConstantFloat(val float: Float) : InsnConstantValue(float, StackType.Float)
class InsnConstantDouble(val double: Double) : InsnConstantValue(double, StackType.Double)
class InsnConstantType(val type: Type) : InsnConstantValue(type, StackType.Object)
class InsnConstantMethodType(val type: Type) : InsnConstantValue(type, StackType.Object)
class InsnConstantHandle(val handle: Handle) : InsnConstantValue(handle, StackType.Object)
class InsnConstantConstantDynamic(val dynamic: ConstantDynamic) : InsnConstantValue(dynamic, StackType.Object)
