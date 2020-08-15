package com.anatawa12.decompiler.optimizer.expressions

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import com.anatawa12.decompiler.util.Property
import org.objectweb.asm.Type

/**
 * booleanExp == 0
 * to
 * !booleanExp
 *
 *
 * booleanExp != 0
 * to
 * booleanExp
 */
object EqOrNeToZeroWithBoolOptimizer : IExpressionOptimizer {
    override fun optimize(expr: Property<out Value, *>, ctx: ProcessorContext): Boolean {
        val eq = expr.value as? ConditionValue ?: return false
        val boolExp = eq.left
        val zero = eq.right as? ConstantValue ?: return false
        if (boolExp.type != Type.BOOLEAN_TYPE) return false
        if (zero.value != VConstantInt(0) && zero.value != VConstantBoolean(false)) return false

        val setter = expr.castInAs<Value>() ?: return false

        val shouldNot = when (eq.condition) {
            BiCondition.LT -> return false
            BiCondition.GE -> return false
            BiCondition.GT -> return false
            BiCondition.LE -> return false
            BiCondition.EQ -> true
            BiCondition.NE -> false
        }

        eq.dispose()

        setter.value = if (shouldNot) BooleanNotValue(boolExp) else boolExp

        return true
    }
}
