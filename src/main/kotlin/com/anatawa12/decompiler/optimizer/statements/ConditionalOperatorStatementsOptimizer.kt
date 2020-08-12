package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.persistentListOf

/**
 * goto L_1 if condition
 * stk1 = value1
 * goto L_2
 *     L_1:
 * stk1 = value2
 *     L_2:
 * to
 * stk1 = (!condition ? value1 : value2)
 *
 * so
 * stk0: one produce, two consume
 */
object ConditionalOperatorStatementsOptimizer :
        IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        for (statement in statements) {
            val conditionalGoto = statement[0] as? ConditionalGoto ?: continue
            val assignFalse = statement[1].exp() as? Assign ?: continue
            val goto = statement[2] as? Goto ?: continue
            val assignTrue = statement[3].exp() as? Assign ?: continue
            val next = statement[4]

            val stk10 = assignFalse.variable as? StackVariable ?: continue
            val value1 = assignFalse.value
            val stk11 = assignTrue.variable as? StackVariable ?: continue
            val value2 = assignTrue.value

            if (conditionalGoto.label !in assignTrue.mainStat.labelsTargetsMe) continue
            if (goto.label !in next.labelsTargetsMe) continue
            if (stk10 != stk11) continue
            if (assignFalse.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue
            if (goto.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue
            if (assignTrue.mainStat.labelsTargetsMe.any { it.usedBy.any { it !== conditionalGoto } }) continue

            conditionalGoto.removeMe()
            assignFalse.mainStat.removeMe()
            goto.removeMe()
            assignTrue.mainStat.removeMe()

            val assign = Assign(stk10, ConditionalOperatorValue(
                    condition = BooleanNotValue(conditionalGoto.value),
                    ifTrue = value1,
                    ifFalse = value2,
            ))

            next.insertPrev(assign.stat())

            assign.mainStat.labelsTargetsMe = persistentListOf<StatLabel>().builder().apply {
                addAll(conditionalGoto.labelsTargetsMe)
                addAll(assignFalse.mainStat.labelsTargetsMe)
                addAll(goto.labelsTargetsMe)
                addAll(assignTrue.mainStat.labelsTargetsMe)
            }.build()
            conditionalGoto.label.usedBy.remove(conditionalGoto)
            goto.label.usedBy.remove(goto)

            return true
        }
        return false
    }
}
