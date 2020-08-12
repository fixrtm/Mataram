package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.mutate

/**
 * variable op= value
 * stk1 = variable
 * to
 * stk1 = (variable op= value)
 */
object AssignAndUseValueToLocalStatementsOptimizer :
        IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        for (statement in statements) {
            val opAssign = statement[0].exp() as? BiOperationAssignedValue ?: continue
            val assign1 = statement[1].exp() as? Assign ?: continue

            val variable1 = variableCastIn<BiOperationAssignedValue>(opAssign.variable) ?: continue
            val value = opAssign.right
            val stk1 = assign1.variable
            val variable2 = assign1.value

            if (assign1.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue
            if (variable1 != variable2) continue

            opAssign.mainStat.removeMe()
            assign1.mainStat.removeMe()

            val assign = Assign(stk1, BiOperationAssignedValue(opAssign.op, variable1, value))
            assign1.mainStat.next.insertPrev(assign.stat())

            assign.mainStat.labelsTargetsMe = assign1.mainStat.labelsTargetsMe.mutate {
                it.addAll(opAssign.mainStat.labelsTargetsMe)
            }
            return true
        }
        return false
    }
}
