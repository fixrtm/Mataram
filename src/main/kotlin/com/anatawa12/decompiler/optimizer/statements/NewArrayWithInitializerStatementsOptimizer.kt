package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.persistentListOf

/**
 * stk0 = newArray type\[constant\]
 * stk1 = stk0
 * stk0\[0\] = value0
 * stk2 = stk1
 * stk1\[1\] = value1
 * ...
 * stkN = stkN-1
 * stkN-1\[N-1\] = valueN-1
 *
 * to
 * stkN = newArray type[] { value0, value2, ..., valueN-1 }
 */
object NewArrayWithInitializerStatementsOptimizer :
    IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        root@ for (statement in statements) {
            val assign1 = statement[0].exp() as? Assign ?: continue@root
            val newArray = assign1.value as? NewArray ?: continue@root
            val arraySizeValue = newArray.size as? ConstantValue ?: continue@root
            val arraySize = arraySizeValue.value as? Int ?: continue@root
            var lastValue = assign1.variable as? StackVariable ?: continue@root
            var lastStat = assign1
            if (arraySize == 0) continue@root

            val variables = mutableListOf<Value>()
            val stats = mutableListOf(assign1)

            for (i in 0 until arraySize) {
                val assign2 = lastStat.mainStat.next.exp() as? Assign ?: continue@root
                val assign3 = assign2.mainStat.next.exp() as? Assign ?: continue@root
                val assign2To = assign2.variable as? StackVariable ?: continue@root
                val assign2From = assign2.value as? StackVariable ?: continue@root
                val assign3To = assign3.variable as? ArrayVariable ?: continue@root
                val assign3From = assign3.value
                val assign3ToAry = assign3To.ary as? StackVariable ?: continue@root
                val assign3ToIdx = assign3To.idx as? ConstantValue ?: continue@root

                if (assign2From != lastValue) continue@root
                if (assign3ToAry != lastValue) continue@root
                if (assign3ToIdx.value != i) continue@root

                if (assign2.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue@root
                if (assign3.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue@root

                variables += assign3From
                lastValue = assign2To
                lastStat = assign3
                stats += assign2
                stats += assign3
            }

            for (stat in stats) {
                stat.mainStat.removeMe()
            }

            val assign = Assign(
                lastValue, NewArrayWithInitializerValue(
                    elementType = newArray.element,
                    arrayInitializer = variables
                )
            )
            lastStat.mainStat.next.insertPrev(assign.stat())

            assign.mainStat.labelsTargetsMe =
                stats.flatMapTo(persistentListOf<StatLabel>().builder()) { it.mainStat.labelsTargetsMe }.build()

            return true
        }
        return false
    }
}
