package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.mutate

/**
 * stk1(2csp) = value
 * stk2(?c?p) = stk1(2csp)
 * stk3(ncsp) = stk1(2csp).getClass()Ljava/lang/Class;
 * to
 * stk2 = NullChecked(value)
 */
object NullCheckedValueStatementsOptimizer : IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>): Boolean {
        for (statement in statements) {
            val assign1 = statement[0].exp() as? Assign ?: continue
            val assign2 = statement[1].exp() as? Assign ?: continue
            val assign3 = statement[2].exp() as? Assign ?: continue

            val stk10 = assign1.variable as? StackVariable ?: continue
            val value = assign1.value
            val stk20 = assign2.variable as? StackVariable ?: continue
            val stk11 = assign2.value as? StackVariable ?: continue
            val stk30 = assign3.variable as? StackVariable ?: continue
            val getClass = assign3.value as? InvokeVirtualValue ?: continue
            val stk12 = getClass.self as? StackVariable ?: continue
            val getClassName = getClass.name
            val getClassDesc = getClass.desc

            if (stk10 != stk11) continue
            if (stk11 != stk12) continue
            if (getClassName != "getClass") continue
            if (getClassDesc != "()Ljava/lang/Class;") continue
            if (assign2.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue
            if (assign3.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue
            if (stk10.identifier.consumerCount != 2) continue
            if (stk10.identifier.producerCount != 1) continue
            if (stk30.identifier.consumerCount != 0) continue

            assign1.mainStat.removeMe()
            assign2.mainStat.removeMe()
            assign3.mainStat.removeMe()

            val assign = Assign(stk20, NullChecked(value))
            assign3.mainStat.next.insertPrev(assign.stat())
            assign.mainStat.labelsTargetsMe = assign1.mainStat.labelsTargetsMe.mutate {
                it.addAll(assign2.mainStat.labelsTargetsMe)
                it.addAll(assign3.mainStat.labelsTargetsMe)
            }
        }
        return false
    }
}
