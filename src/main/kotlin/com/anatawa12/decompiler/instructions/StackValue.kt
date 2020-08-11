package com.anatawa12.decompiler.instructions

import org.objectweb.asm.Label
import org.objectweb.asm.Type

enum class StackType {
    Integer,
    Long,
    Double,
    Float,
    Object,

    ;

    companion object {
        fun byType(type: Type) = when (type.sort) {
            Type.BOOLEAN -> Integer
            Type.CHAR -> Integer
            Type.BYTE -> Integer
            Type.SHORT -> Integer
            Type.INT -> Integer
            Type.FLOAT -> Float
            Type.LONG -> Long
            Type.DOUBLE -> Double
            Type.ARRAY -> Object
            Type.OBJECT -> Object
            else -> error("invalid type for value type")
        }

        fun byValue(value: Any?): StackType = when (value) {
            is Boolean -> Integer
            is Char -> Integer
            is Byte -> Integer
            is Short -> Integer
            is Int -> Integer
            is kotlin.Float -> Float
            is kotlin.Long -> Long
            is kotlin.Double -> Double
            else -> Object
        }

        fun byDesc(descriptor: String): StackType = when (descriptor[0]) {
            'Z' -> Integer
            'C' -> Integer
            'B' -> Integer
            'S' -> Integer
            'I' -> Integer
            'F' -> Float
            'J' -> Long
            'D' -> Double
            '[' -> Object
            'L' -> Object
            else -> error("invalid type for value type")
        }
    }
}

enum class AllType(val stackType: StackType, val asmType: Type?) {
    Boolean(StackType.Integer, Type.BOOLEAN_TYPE),
    Char(StackType.Integer, Type.CHAR_TYPE),
    Byte(StackType.Integer, Type.BYTE_TYPE),
    Short(StackType.Integer, Type.SHORT_TYPE),
    Integer(StackType.Integer, Type.INT_TYPE),
    Long(StackType.Long, Type.LONG_TYPE),
    Double(StackType.Double, Type.DOUBLE_TYPE),
    Float(StackType.Float, Type.FLOAT_TYPE),
    Object(StackType.Object, null),

    ;

    companion object {
    }
}

enum class BiOp {
    Add,
    Sub,
    Mul,
    Div,
    Rem,
    And,
    Or,
    Xor,
}

enum class ShiftOp {
    Shl,
    Shr,
    UShr,
}

enum class NumericType(val stackType: StackType) {
    Integer(StackType.Integer),
    Long(StackType.Long),
    Float(StackType.Float),
    Double(StackType.Double),
}

enum class InsnConvert(val from: NumericType, val to: AllType) {
    I2L(NumericType.Integer, AllType.Long),
    I2F(NumericType.Integer, AllType.Float),
    I2D(NumericType.Integer, AllType.Double),
    L2I(NumericType.Long, AllType.Integer),
    L2F(NumericType.Long, AllType.Float),
    L2D(NumericType.Long, AllType.Double),
    F2I(NumericType.Float, AllType.Integer),
    F2L(NumericType.Float, AllType.Long),
    F2D(NumericType.Float, AllType.Double),
    D2I(NumericType.Double, AllType.Integer),
    D2L(NumericType.Double, AllType.Long),
    D2F(NumericType.Double, AllType.Float),
    I2B(NumericType.Integer, AllType.Byte),
    I2C(NumericType.Integer, AllType.Char),
    I2S(NumericType.Integer, AllType.Short),
}

enum class InsnCondition(val input: StackType) {
    EQ(StackType.Integer),
    NE(StackType.Integer),
    LT(StackType.Integer),
    GE(StackType.Integer),
    GT(StackType.Integer),
    LE(StackType.Integer),
    Null(StackType.Object),
    NonNull(StackType.Object),
}

enum class InsnBiCondition(val input: StackType) {
    ICmpEQ(StackType.Integer),
    ICmpNE(StackType.Integer),
    ICmpLT(StackType.Integer),
    ICmpGE(StackType.Integer),
    ICmpGT(StackType.Integer),
    ICmpLE(StackType.Integer),
    ACmpEQ(StackType.Object),
    ACmpNE(StackType.Object),
}

sealed class FrameElement {
    object Top : FrameElement()
    object Integer : FrameElement()
    object Float : FrameElement()
    object Long : FrameElement()
    object Double : FrameElement()
    object Null : FrameElement()
    object UninitializedThis : FrameElement()
    class RefType(val internalName: String) : FrameElement()
    class Uninitialized(val newAt: Label) : FrameElement()

    companion object {
        fun byType(type: Type) = when (type.sort) {
            Type.BOOLEAN -> Integer
            Type.CHAR -> Integer
            Type.BYTE -> Integer
            Type.SHORT -> Integer
            Type.INT -> Integer
            Type.FLOAT -> Float
            Type.LONG -> Long
            Type.DOUBLE -> Double
            Type.ARRAY -> RefType(type.internalName)
            Type.OBJECT -> RefType(type.internalName)
            else -> error("invalid sort")
        }
    }
}
