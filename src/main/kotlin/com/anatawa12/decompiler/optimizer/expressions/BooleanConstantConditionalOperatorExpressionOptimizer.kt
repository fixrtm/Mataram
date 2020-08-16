package com.anatawa12.decompiler.optimizer.expressions

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import com.anatawa12.decompiler.util.Property

/**
 * condition ? true : false
 * -> condition
 *
 * condition ? false : true
 * -> !condition
 */
object BooleanConstantConditionalOperatorExpressionOptimizer : IExpressionOptimizer {
    @Suppress("SimplifyBooleanWithConstants")
    override fun optimize(expr: Property<out Value, *>, ctx: ProcessorContext): Boolean {
        val setter = expr.castInAs<Value>() ?: return false
        val conditional = expr.value as? ConditionalOperatorValue ?: return false
        val condition = conditional.condition
        val constantIfTrue = conditional.ifTrue
            .let { it as? ConstantValue }?.value as? VConstantBoolean ?: return false
        val constantIfFalse = conditional.ifFalse
            .let { it as? ConstantValue }?.value as? VConstantBoolean ?: return false

        val invert: Boolean
        if (constantIfTrue.boolean == true) {
            if (constantIfFalse.boolean == false) {
                invert = false
            } else {
                return false
            }
        } else {
            if (constantIfFalse.boolean == true) {
                invert = true
            } else {
                return false
            }
        }

        conditional.dispose()
        conditional.ifTrue.dispose()
        conditional.ifFalse.dispose()

        setter.value = if (invert) BooleanNotValue(condition) else condition

        return true
    }
}
