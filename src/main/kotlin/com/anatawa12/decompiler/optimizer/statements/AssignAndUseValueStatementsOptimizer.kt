package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.mutate

/**
 * stk0 = value
 * stk1 = stk0
 * any = stk0
 * to
 * stk1 = (any = value)
 *
 * so
 * stk0: one produce, two consume
 */
object AssignAndUseValueStatementsOptimizer : IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>): Boolean {
        for (stat in statements) {
            val assign1 = stat[0].exp() as? Assign ?: continue
            val assign2 = stat[1].exp() as? Assign ?: continue
            val assign3 = stat[2].exp() as? Assign ?: continue

            // verification
            val stk00 = assign1.variable as? StackVariable ?: continue
            val value = assign1.value
            val stk10 = assign2.variable as? StackVariable ?: continue
            val stk01 = assign2.value as? StackVariable ?: continue
            val any = assign3.variable
            val stk02 = assign3.value as? StackVariable ?: continue

            if (stk00 != stk01) continue
            if (stk01 != stk02) continue
            if (stk00.identifier.producerCount != 1) continue
            if (stk00.identifier.consumerCount != 2) continue
            if (assign2.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue
            if (assign3.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue
            if (any is StackVariable) continue

            // run replacement

            assign1.mainStat.removeMe()
            assign2.mainStat.removeMe()
            assign3.mainStat.removeMe()

            val assign = Assign(stk10, Assign(any, value))
            assign3.mainStat.next.insertPrev(assign.stat())

            assign.mainStat.labelsTargetsMe = assign1.mainStat.labelsTargetsMe.mutate {
                it.addAll(assign2.mainStat.labelsTargetsMe)
                it.addAll(assign3.mainStat.labelsTargetsMe)
            }

            return true
        }
        return false
    }
}
