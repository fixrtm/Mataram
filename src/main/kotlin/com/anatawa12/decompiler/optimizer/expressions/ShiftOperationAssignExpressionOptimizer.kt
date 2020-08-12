package com.anatawa12.decompiler.optimizer.expressions

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import com.anatawa12.decompiler.util.Property

/**
 * variable1 = (variable1 op value1)
 *
 * variable1 op= value1
 */
object ShiftOperationAssignExpressionOptimizer : IExpressionOptimizer {
    override fun optimize(expr: Property<out Value, *>, ctx: ProcessorContext): Boolean {
        val biOpAssignProp = expr.castInAs<ShiftOperationAssignedValue>() ?: return false
        val assign = expr.value as? Assign ?: return false
        val shift = assign.value as? ShiftOperation ?: return false
        val variable10 = variableCastIn<ShiftOperationAssignedValue>(assign.variable) ?: return false
        val variable11 = shift.value
        val value10 = shift.shift
        if (variable10 != variable11) return false

        assign.dispose()
        shift.dispose()
        variable11.dispose()

        biOpAssignProp.value = ShiftOperationAssignedValue(shift.op, variable10, value10)
        return true
    }
}
