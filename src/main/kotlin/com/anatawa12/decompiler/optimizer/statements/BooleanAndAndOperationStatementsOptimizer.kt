package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.mutate

/**
 * goto L1 if condition1
 * goto L2 if condition2
 *     L1:
 * to
 * goto L2 if !condition1 && condition2
 */
object BooleanAndAndOperationStatementsOptimizer :
        IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        for (statement in statements) {
            val conditionalGoto1 = statement as? ConditionalGoto ?: continue
            val conditionalGoto2 = conditionalGoto1.next as? ConditionalGoto ?: continue
            val next = conditionalGoto2.next

            if (conditionalGoto1.label !in next.labelsTargetsMe) continue
            if (conditionalGoto2.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue

            val prev = conditionalGoto1.prev
            prev.next = conditionalGoto2
            conditionalGoto2.prev = prev

            Statement.dispose(conditionalGoto1)

            val condition1 = conditionalGoto1.value
            val condition2 = conditionalGoto2.value
            val andAnd = BooleanAndAndOperation(
                    left = TemporaryExpressionValue,
                    right = TemporaryExpressionValue,
            )
            conditionalGoto2.value = andAnd
            andAnd.left = BooleanNotValue(condition1)
            andAnd.right = condition2
            conditionalGoto2.labelsTargetsMe = conditionalGoto1.labelsTargetsMe.mutate { it.addAll(conditionalGoto2.labelsTargetsMe) }
            conditionalGoto1.label.usedBy.remove(conditionalGoto1)

            return true
        }
        return false
    }
}
// (condition1 || condition2)
// !(!condition1 && !condition2)
// real: accuracy ==(a) null || accuracy.equals(MEDIUM.toString())
// real: !(!accuracy ==(a) null && !accuracy.equals(MEDIUM.toString()))

// accuracy ==(a) null
// !accuracy.equals(MEDIUM.toString())
