package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.statementsGen.*
import com.anatawa12.decompiler.util.Property

/**
 * stk1 = var1
 * // anything
 * anyExpression(stk1)
 * // anything
 * anyExpression(stk1)
 * // anything
 * ...
 *
 * to
 *
 * // anything
 * anyExpression(var1)
 * // anything
 * anyExpression(var1)
 * // anything
 * ...
 */
object MultiConsumerSingleProducerStackValueStatementsOptimizer : IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>): Boolean {
        for (statement1 in statements) {
            val assign = statement1.exp() as? Assign ?: continue
            val stk1 = assign.variable as? StackVariable ?: continue
            val var1 = assign.value as? LocalVariable ?: continue

            if (stk1.identifier.producerCount != 1) continue

            val consumers = mutableListOf<Property<in LocalVariable, *>>()

            for (consumer in stk1.identifier.consumers) {
                consumers += consumer.castInAs<LocalVariable>() ?: continue
            }

            for (consumer in consumers) {
                consumer.value = var1
            }

            assign.mainStat.removeMe()

            return true
        }

        return false
    }
}
