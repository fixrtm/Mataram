package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.instructions.BiOp
import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.mutate

/**
 * var1 = var1 +- 1
 * stk1 = var1
 * to
 * stk1 = ++--var1
 */
object PrefixLocalInDecrementStatementsOptimizer :
    IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        for (statement in statements) {
            val assign1 = statement[0].exp() as? Assign ?: continue
            val assign2 = statement[1].exp() as? Assign ?: continue

            val var11 = assign1.variable as? LocalVariable ?: continue
            val biOp = assign1.value as? BiOperation ?: continue
            val var12 = biOp.left as? LocalVariable ?: continue
            val const1 = biOp.right as? ConstantValue ?: continue
            val stk11 = assign2.variable as? StackVariable ?: continue
            val var13 = assign2.value as? LocalVariable ?: continue

            if (var11 != var12) continue
            if (var12 != var13) continue
            val isPositive = when (const1.value) {
                1 -> true
                -1 -> false
                else -> continue
            }

            val inDecrementType = when (biOp.op) {
                BiOp.Add -> if (isPositive) InDecrementType.PrefixIncrement else InDecrementType.PrefixDecrement
                BiOp.Sub -> if (isPositive) InDecrementType.PrefixDecrement else InDecrementType.PrefixIncrement
                else -> continue
            }

            if (assign2.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue

            assign1.mainStat.removeMe()
            assign2.mainStat.removeMe()
            val next = assign2.mainStat.next

            val assign = Assign(stk11, InDecrementValue(inDecrementType, var11))
            next.insertPrev(assign.stat())

            assign.mainStat.labelsTargetsMe = assign1.mainStat.labelsTargetsMe.mutate {
                it.addAll(assign2.mainStat.labelsTargetsMe)
            }
            return true
        }
        return false
    }
}
