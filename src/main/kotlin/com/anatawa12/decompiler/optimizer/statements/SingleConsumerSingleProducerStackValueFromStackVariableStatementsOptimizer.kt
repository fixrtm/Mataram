package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import com.anatawa12.decompiler.util.Property
import kotlinx.collections.immutable.mutate

/**
 * stk1 = stk2
 * // no labels which is used.
 * someExpression(stk1)
 * to
 * someExpression(stk2)
 */
object SingleConsumerSingleProducerStackValueFromStackVariableStatementsOptimizer : IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        root@ for (statement1 in statements) {
            val statement = statement1.exp() as? Assign ?: continue@root
            val stk1 = statement.variable as? StackVariable ?: continue@root
            val stk2 = statement.value as? StackVariable ?: continue@root
            if (!stk1.identifier.isSingleConsumeSingleProduce) continue@root

            /// get usage
            val producer = stk1.identifier.producers.single() as? Assign ?: continue@root
            check(producer == statement)
            val prop: Property<in Value, *>
            val someExpStat: Statement
            if (stk1.identifier.consumerExpressions.isNotEmpty()) {
                val consumer = stk1.identifier.consumerExpressions.single()
                someExpStat = rootStatementOf(consumer.thisRef)
                prop = consumer.castInAs() ?: continue@root
            } else {
                val consumer = stk1.identifier.consumerStatements.single()
                someExpStat = consumer.thisRef
                prop = consumer.castInAs() ?: continue@root
            }

            // check no labels which is used.
            do { // we cannot use run because continue cannot be used beyond
                var cur: Statement = producer.mainStat
                while (cur != someExpStat) {
                    cur = cur.next
                    if (cur.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue@root
                }
            } while (false)

            producer.mainStat.removeMe()
            producer.mainStat.next.labelsTargetsMe = producer.mainStat.labelsTargetsMe.mutate {
                it.addAll(producer.mainStat.next.labelsTargetsMe)
            }

            val from = stk2
            prop.value = from

            return true
        }

        return false
    }

    private fun rootStatementOf(exp: Value): Statement {
        if (exp is ExpressionValue)
            return exp.consumerStatement?.thisRef ?: rootStatementOf(exp.consumer!!.thisRef)
        if (exp is ExpressionVariable)
            return exp.consumerStatement?.thisRef
                ?: exp.producer as? Statement
                ?: exp.producer.let { it as? Value }?.let(::rootStatementOf)
                ?: rootStatementOf(exp.consumer!!.thisRef)
        error("cannot get root statement of $exp")
    }
}
