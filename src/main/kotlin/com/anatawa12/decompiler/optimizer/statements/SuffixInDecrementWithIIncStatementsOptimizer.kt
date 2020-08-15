package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.instructions.BiOp
import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.mutate

/**
 * stk1 = var1
 * var1 = var1 +- 1
 * to
 * stk1 = var1++--
 */
object SuffixInDecrementWithIIncStatementsOptimizer :
    IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        for (statement in statements) {
            val assign1 = statement[0].exp() as? Assign ?: continue
            val assign2 = statement[1].exp() as? Assign ?: continue

            val stk11 = assign1.variable as? StackVariable ?: continue
            val var11 = assign1.value as? LocalVariable ?: continue
            val var12 = assign2.variable as? LocalVariable ?: continue
            val biOp = assign2.value as? BiOperation ?: continue
            val var13 = biOp.left as? LocalVariable ?: continue
            val const1 = biOp.right as? ConstantValue ?: continue

            if (var11 != var12) continue
            if (var12 != var13) continue
            val isPositive = when (const1.value) {
                VConstantInt(1) -> true
                VConstantInt(-1) -> false
                else -> continue
            }

            val inDecrementType = when (biOp.op) {
                BiOp.Add -> if (isPositive) InDecrementType.SuffixIncrement else InDecrementType.SuffixDecrement
                BiOp.Sub -> if (isPositive) InDecrementType.SuffixDecrement else InDecrementType.SuffixIncrement
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
