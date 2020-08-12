package com.anatawa12.decompiler.optimizer.expressions

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import com.anatawa12.decompiler.util.Property

/**
 * (value1 cmpSome value2) cmp 0
 * to
 * value1 cmp value2
 */
object FloatingCompareExpressionOptimizer : IExpressionOptimizer {
    override fun optimize(expr: Property<out Value, *>, ctx: ProcessorContext): Boolean {
        val setterCondition = expr.castInAs<ConditionValue>() ?: return false
        val setterBooleanNot = expr.castInAs<BooleanNotValue>() ?: return false
        val compare = expr.value as? ConditionValue ?: return false
        val zero = compare.right as? ConstantValue ?: return false

        if (zero.value != 0) return false

        when (val floatComp = compare.left) {
            is FloatingCompareGreater -> {
                compare.dispose()
                floatComp.dispose()
                zero.dispose()
                when (compare.condition) {
                    BiCondition.EQ -> setterCondition.value = ConditionValue(BiCondition.EQ, floatComp.left, floatComp.right)
                    BiCondition.NE -> setterCondition.value = ConditionValue(BiCondition.NE, floatComp.left, floatComp.right)
                    BiCondition.LE -> setterCondition.value = ConditionValue(BiCondition.LE, floatComp.left, floatComp.right)
                    BiCondition.LT -> setterCondition.value = ConditionValue(BiCondition.LT, floatComp.left, floatComp.right)
                    BiCondition.GE -> setterBooleanNot.value = BooleanNotValue(ConditionValue(BiCondition.LT, floatComp.left, floatComp.right))
                    BiCondition.GT -> setterBooleanNot.value = BooleanNotValue(ConditionValue(BiCondition.LE, floatComp.left, floatComp.right))
                }
                return true
            }
            is FloatingCompareLesser -> {
                compare.dispose()
                floatComp.dispose()
                zero.dispose()
                when (compare.condition) {
                    BiCondition.EQ -> setterCondition.value = ConditionValue(BiCondition.EQ, floatComp.left, floatComp.right)
                    BiCondition.NE -> setterCondition.value = ConditionValue(BiCondition.NE, floatComp.left, floatComp.right)
                    BiCondition.GE -> setterCondition.value = ConditionValue(BiCondition.GE, floatComp.left, floatComp.right)
                    BiCondition.GT -> setterCondition.value = ConditionValue(BiCondition.GT, floatComp.left, floatComp.right)
                    BiCondition.LE -> setterBooleanNot.value = BooleanNotValue(ConditionValue(BiCondition.GT, floatComp.left, floatComp.right))
                    BiCondition.LT -> setterBooleanNot.value = BooleanNotValue(ConditionValue(BiCondition.GE, floatComp.left, floatComp.right))
                }
                return true
            }
            else -> return false
        }
    }
}
