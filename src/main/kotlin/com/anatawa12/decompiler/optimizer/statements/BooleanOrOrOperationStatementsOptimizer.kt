package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.BooleanOrOrOperation
import com.anatawa12.decompiler.statementsGen.ConditionalGoto
import com.anatawa12.decompiler.statementsGen.Statement
import com.anatawa12.decompiler.statementsGen.TemporaryExpressionValue
import kotlinx.collections.immutable.mutate

/**
 * goto L1 if condition1
 * goto L1 if condition2
 * to
 * goto L1 if condition1 || condition2
 */
object BooleanOrOrOperationStatementsOptimizer :
        IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        for (statement in statements) {
            val conditionalGoto1 = statement as? ConditionalGoto ?: continue
            val conditionalGoto2 = conditionalGoto1.next as? ConditionalGoto ?: continue

            if (conditionalGoto1.label != conditionalGoto2.label) continue
            if (conditionalGoto2.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue

            val prev = conditionalGoto1.prev
            prev.next = conditionalGoto2
            conditionalGoto2.prev = prev

            Statement.dispose(conditionalGoto1)

            val condition1 = conditionalGoto1.value
            val condition2 = conditionalGoto2.value
            val andAnd = BooleanOrOrOperation(
                    left = TemporaryExpressionValue,
                    right = TemporaryExpressionValue,
            )
            conditionalGoto2.value = andAnd
            andAnd.left = condition1
            andAnd.right = condition2
            conditionalGoto2.labelsTargetsMe = conditionalGoto1.labelsTargetsMe.mutate { it.addAll(conditionalGoto2.labelsTargetsMe) }
            conditionalGoto1.label.usedBy.remove(conditionalGoto1)

            return true
        }
        return false
    }
}
