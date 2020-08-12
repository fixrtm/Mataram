package com.anatawa12.decompiler.optimizer.expressions

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import com.anatawa12.decompiler.util.Property

/**
 * variable1 = (variable1 op value1)
 *
 * variable1 op= value1
 */
object BiOperationAssignExpressionOptimizer : IExpressionOptimizer {
    override fun optimize(expr: Property<out Value, *>, ctx: ProcessorContext): Boolean {
        val biOpAssignProp = expr.castInAs<BiOperationAssignedValue>() ?: return false
        val assign = expr.value as? Assign ?: return false
        val biOp = assign.value as? BiOperation ?: return false
        val variable10 = variableCastIn<BiOperationAssignedValue>(assign.variable) ?: return false
        val variable11 = biOp.left
        val value10 = biOp.right
        if (variable10 != variable11) return false

        assign.dispose()
        biOp.dispose()
        variable11.dispose()

        biOpAssignProp.value = BiOperationAssignedValue(biOp.op, variable10, value10)
        return true
    }
}
