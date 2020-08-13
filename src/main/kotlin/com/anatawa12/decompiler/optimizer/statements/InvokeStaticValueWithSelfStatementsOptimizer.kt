package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.mutate

/**
 * stk1 = value
 * stk2 = invokeStatic(args)
 * to
 * stk2 = value.invokeStatic(args)
 */
object InvokeStaticValueWithSelfStatementsOptimizer :
    IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        for (statement in statements) {
            val assign1 = statement[0].exp() as? Assign ?: continue
            val assign2 = statement[1].exp() as? Assign ?: continue

            val stk1 = assign1.variable as? StackVariable ?: continue
            val value = assign1.value
            //val stk2 = assign2.variable as? StackVariable ?: continue
            val invokeStatic = assign2.value as? InvokeStaticValue ?: continue
            val type = value.type ?: continue
            if (type.internalName != invokeStatic.owner) continue
            if (stk1.identifier.consumerCount != 0) continue
            if (assign2.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue

            assign1.mainStat.removeMe()
            invokeStatic.dispose()

            assign2.mainStat.labelsTargetsMe =
                assign1.mainStat.labelsTargetsMe.mutate { it.addAll(assign2.mainStat.labelsTargetsMe) }
            assign2.value = InvokeStaticWithSelfValue(
                invokeStatic.owner, invokeStatic.name, invokeStatic.desc,
                invokeStatic.isInterface, value, invokeStatic.args
            )

            return true
        }
        return false
    }
}
