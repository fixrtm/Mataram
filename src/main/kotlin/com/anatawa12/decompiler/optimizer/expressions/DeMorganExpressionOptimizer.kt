package com.anatawa12.decompiler.optimizer.expressions

import com.anatawa12.decompiler.instructions.StackType
import com.anatawa12.decompiler.statementsGen.*
import com.anatawa12.decompiler.util.Property

/**
 * !(EasyInvertExpressions)
 *
 * to
 *
 * inverted EasyInvertExpressions
 *
 * EasyInvertExpressions:
 * - !(any expression)
 *   -> any expression
 * - EasyNotExpressions && EasyNotExpressions
 *   -> !EasyNotExpressions || !EasyNotExpressions
 * - EasyNotExpressions || EasyNotExpressions
 *   -> !EasyNotExpressions && !EasyNotExpressions
 * - integer compare integer
 *   -> integer invertedCompare integer
 * - long compare long
 *   -> long invertedCompare long
 * - object compare object
 *   -> object invertedCompare object
 */
object DeMorganExpressionOptimizer : IExpressionOptimizer {
    override fun optimize(expr: Property<out Value, *>): Boolean {
        val setter = expr.castInAs<Value>() ?: return false
        val notExpr = expr.value as? BooleanNotValue ?: return false
        val eiExpr = notExpr.value

        if (!isEasyInvertExpressions(eiExpr)) return false

        notExpr.dispose()
        eiExpr.dispose()

        when (eiExpr) {
            is BooleanNotValue -> {
                setter.value = eiExpr.value
            }
            is BooleanAndAndOperation -> {
                setter.value = BooleanOrOrOperation(BooleanNotValue(eiExpr.left), BooleanNotValue(eiExpr.right))
            }
            is BooleanOrOrOperation -> {
                setter.value = BooleanAndAndOperation(BooleanNotValue(eiExpr.left), BooleanNotValue(eiExpr.right))
            }
            is ConditionValue -> {
                setter.value = when (eiExpr.condition) {
                    BiCondition.EQ -> ConditionValue(BiCondition.NE, eiExpr.left, eiExpr.right)
                    BiCondition.NE -> ConditionValue(BiCondition.EQ, eiExpr.left, eiExpr.right)
                    BiCondition.LE -> ConditionValue(BiCondition.GT, eiExpr.left, eiExpr.right)
                    BiCondition.LT -> ConditionValue(BiCondition.GE, eiExpr.left, eiExpr.right)
                    BiCondition.GE -> ConditionValue(BiCondition.LT, eiExpr.left, eiExpr.right)
                    BiCondition.GT -> ConditionValue(BiCondition.LE, eiExpr.left, eiExpr.right)
                }
            }
            else -> error("EasyInvertExpressions but not implemented")
        }
        return true
    }

    private fun isEasyInvertExpressions(expr: Value): Boolean = when (expr) {
        is BooleanNotValue -> true
        is BooleanAndAndOperation -> isEasyInvertExpressions(expr.left) && isEasyInvertExpressions(expr.right)
        is BooleanOrOrOperation -> isEasyInvertExpressions(expr.left) && isEasyInvertExpressions(expr.right)
        is ConditionValue -> when (expr.left.stackType) {
            StackType.Integer -> true
            StackType.Long -> true
            StackType.Object -> true
            StackType.Double -> false
            StackType.Float -> false
        }
        else -> false
    }
}
