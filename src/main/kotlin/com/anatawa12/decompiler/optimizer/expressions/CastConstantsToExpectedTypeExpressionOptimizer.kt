package com.anatawa12.decompiler.optimizer.expressions

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import com.anatawa12.decompiler.util.Property

object CastConstantsToExpectedTypeExpressionOptimizer : IExpressionOptimizer {
    override fun optimize(expr: Property<out Value, *>, ctx: ProcessorContext): Boolean {
        val setter = expr.castInAs<ConstantValue>() ?: return false
        val constant = expr.value as? ConstantValue ?: return false
        val inferType = expr.let { it as? ValueProperty<*, *> }?.expectedType ?: return false

        val castedValue = if (inferType == ExpectTypes.Unknown) null
        else when (val v = constant.value) {
            VConstantNull,
            is VConstantString,
            is VConstantType,
            is VConstantMethodType,
            is VConstantHandle,
            is VConstantConstantDynamic,
            -> when (inferType) {
                ExpectTypes.Object -> null
                else -> cannotCastError(v, inferType)
            }
            is VConstantInt -> when (inferType) {
                ExpectTypes.Boolean -> VConstantBoolean(v.int != 0)
                ExpectTypes.Char -> VConstantChar(v.int.toChar())
                ExpectTypes.Byte -> VConstantByte(v.int.toByte())
                ExpectTypes.Short -> VConstantShort(v.int.toShort())
                ExpectTypes.AnyInteger -> null
                else -> cannotCastError(v, inferType)
            }
            is VConstantLong -> when (inferType) {
                ExpectTypes.Long -> null
                else -> cannotCastError(v, inferType)
            }
            is VConstantFloat -> when (inferType) {
                ExpectTypes.Float -> null
                else -> cannotCastError(v, inferType)
            }
            is VConstantDouble -> when (inferType) {
                ExpectTypes.Double -> null
                else -> cannotCastError(v, inferType)
            }
            is VConstantByte -> when (inferType) {
                ExpectTypes.Byte -> null
                else -> cannotCastError(v, inferType)
            }
            is VConstantChar -> when (inferType) {
                ExpectTypes.Char -> null
                else -> cannotCastError(v, inferType)
            }
            is VConstantShort -> when (inferType) {
                ExpectTypes.Short -> null
                else -> cannotCastError(v, inferType)
            }
            is VConstantBoolean -> when (inferType) {
                ExpectTypes.Boolean -> null
                else -> cannotCastError(v, inferType)
            }
        }

        @Suppress("FoldInitializerAndIfToElvis")
        if (castedValue == null) return false

        val newConstantValue = ConstantValue(castedValue)
        newConstantValue.lineNumber = constant.lineNumber
        setter.value = newConstantValue
        constant.dispose()

        return true
    }

    private fun cannotCastError(v: VConstantValue, inferType: ExpectTypes): Nothing {
        error("cannot cast ${v.javaClass.simpleName.removePrefix("VConstant")} to $inferType")
    }
}
