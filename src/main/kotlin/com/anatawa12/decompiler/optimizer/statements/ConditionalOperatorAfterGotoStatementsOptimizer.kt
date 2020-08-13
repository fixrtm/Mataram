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
 * goto L_2
 * to
 * stk1 = (!condition ? value1 : value2)
 * goto L_2
 *
 * so
 * stk0: one produce, two consume
 */
object ConditionalOperatorAfterGotoStatementsOptimizer :
    IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        for (statement in statements) {
            val conditionalGoto = statement[0] as? ConditionalGoto ?: continue
            val assignFalse = statement[1].exp() as? Assign ?: continue
            val goto1 = statement[2] as? Goto ?: continue
            val assignTrue = statement[3].exp() as? Assign ?: continue
            val goto2 = statement[4] as? Goto ?: continue

            val assignTarget = assignFalse.variable as? StackVariable ?: continue

            if (conditionalGoto.label !in assignTrue.mainStat.labelsTargetsMe) continue
            if (assignTarget != assignTrue.variable) continue

            if (assignFalse.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue
            if (goto1.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue
            if (assignTrue.mainStat.labelsTargetsMe.any { it.usedBy.any { it !== conditionalGoto } }) continue
            if (goto2.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue

            conditionalGoto.removeMe()
            assignFalse.mainStat.removeMe()
            goto1.removeMe()
            assignTrue.mainStat.removeMe()

            val assign = Assign(
                assignTarget, ConditionalOperatorValue(
                    condition = BooleanNotValue(conditionalGoto.value),
                    ifTrue = assignFalse.value,
                    ifFalse = assignTrue.value,
                )
            )
            goto2.insertPrev(assign.stat())
            assign.mainStat.labelsTargetsMe = persistentListOf<StatLabel>().builder().apply {
                addAll(conditionalGoto.labelsTargetsMe)
                addAll(assignFalse.mainStat.labelsTargetsMe)
                addAll(goto1.labelsTargetsMe)
                addAll(assignTrue.mainStat.labelsTargetsMe)
            }.build()


            conditionalGoto.label.usedBy.remove(conditionalGoto)
            goto1.label.usedBy.remove(goto1)

            return true
        }
        return false
    }
}
