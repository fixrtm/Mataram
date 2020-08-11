package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.instructions.BiOp
import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.mutate

/**
 * stk1 = variable
 * stk2 = stk1
 * variable = convert(stk1 +- 1)
 * to
 * stk2 = variable++--
 */
object SuffixInDecrementStatementsOptimizer :
        IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>): Boolean {
        for (statement in statements) {
            val assign1 = statement[0].exp() as? Assign ?: continue
            val assign2 = statement[1].exp() as? Assign ?: continue
            val assign3 = statement[2].exp() as? Assign ?: continue
            val stk11 = assign1.variable as? StackVariable ?: continue
            val variable1 = assign1.value
            val stk21 = assign2.variable as? StackVariable ?: continue
            val stk12 = assign2.value as? StackVariable ?: continue
            val variable2 = variableCastIn<InDecrementValue>(assign3.variable)
            val convertOrAdd = assign3.value

            if (stk11 != stk12) continue
            if (variable1 != variable2) continue

            val biOp: BiOperation
            val willDisposeValue = mutableListOf<Value>()

            when (convertOrAdd) {
                is CastValue -> {
                    biOp = convertOrAdd.value as? BiOperation ?: continue

                    willDisposeValue.add(convertOrAdd)
                    willDisposeValue.add(biOp)
                }
                is BiOperation -> {
                    biOp = convertOrAdd

                    willDisposeValue.add(biOp)
                }
                else -> continue
            }
            val stk13 = biOp.left as? StackVariable ?: continue
            val const1 = biOp.right as? ConstantValue ?: continue

            if (stk12 != stk13) continue
            if (const1.value != 1) continue
            val inDecrementType = when (biOp.op) {
                BiOp.Add -> InDecrementType.SuffixIncrement
                BiOp.Sub -> InDecrementType.SuffixDecrement
                else -> continue
            }

            if (stk11.identifier.consumerCount != 2) continue
            if (stk11.identifier.producerCount != 1) continue

            if (assign2.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue
            if (assign3.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue

            assign1.mainStat.removeMe()
            assign2.mainStat.removeMe()
            assign3.mainStat.removeMe()
            willDisposeValue.forEach { it.dispose() }
            variable1.dispose()
            val next = assign3.mainStat.next

            val assign = Assign(stk21, InDecrementValue(inDecrementType, variable2))
            next.insertPrev(assign.stat())
            assign.mainStat.labelsTargetsMe = assign1.mainStat.labelsTargetsMe.mutate {
                it.addAll(assign2.mainStat.labelsTargetsMe)
                it.addAll(assign3.mainStat.labelsTargetsMe)
            }
            return true
        }
        return false
    }
}
