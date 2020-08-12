package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.mutate

/**
 * stk1 = value
 * invokeStatic(args)
 * to
 * value.invokeStatic(args)
 */
object InvokeStaticVoidWithSelfStatementsOptimizer :
        IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        for (statement in statements) {
            val assign = statement[0].exp() as? Assign ?: continue
            val invokeStatic = statement[1] as? InvokeStaticVoid ?: continue

            val stk1 = assign.variable as? StackVariable ?: continue
            val value = assign.value
            val type = value.type ?: continue

            if (type.internalName != invokeStatic.owner) continue
            if (stk1.identifier.consumerCount != 0) continue
            if (invokeStatic.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue

            assign.mainStat.removeMe()
            invokeStatic.removeMe()

            val static = InvokeStaticWithSelfVoid(invokeStatic.owner, invokeStatic.name, invokeStatic.desc,
                    invokeStatic.isInterface, value, invokeStatic.args)

            invokeStatic.next.insertPrev(static)
            static.labelsTargetsMe = assign.mainStat.labelsTargetsMe.mutate { it.addAll(invokeStatic.labelsTargetsMe) }

            return true
        }
        return false
    }
}
