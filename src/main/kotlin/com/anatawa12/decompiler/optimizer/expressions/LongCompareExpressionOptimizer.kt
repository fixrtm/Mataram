package com.anatawa12.decompiler.optimizer.expressions

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import com.anatawa12.decompiler.util.Property

object LongCompareExpressionOptimizer : IExpressionOptimizer {
    override fun optimize(expr: Property<out Value, *>, ctx: ProcessorContext): Boolean {
        val setter = expr.castInAs<ConditionValue>() ?: return false
        val compare = expr.value as? ConditionValue ?: return false
        val longComp = compare.left as? LongCompare ?: return false
        val zero = compare.right as? ConstantValue ?: return false

        if (zero.value != 0) return false

        compare.dispose()
        longComp.dispose()
        zero.dispose()

        when (compare.condition) {
            BiCondition.EQ -> setter.value = ConditionValue(BiCondition.EQ, longComp.left, longComp.right)
            BiCondition.NE -> setter.value = ConditionValue(BiCondition.NE, longComp.left, longComp.right)
            BiCondition.LE -> setter.value = ConditionValue(BiCondition.LE, longComp.left, longComp.right)
            BiCondition.LT -> setter.value = ConditionValue(BiCondition.LT, longComp.left, longComp.right)
            BiCondition.GE -> setter.value = ConditionValue(BiCondition.GE, longComp.left, longComp.right)
            BiCondition.GT -> setter.value = ConditionValue(BiCondition.GT, longComp.left, longComp.right)
        }
        return true
    }
}
