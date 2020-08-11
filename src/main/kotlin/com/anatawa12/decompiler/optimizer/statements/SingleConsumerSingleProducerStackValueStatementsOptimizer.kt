package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.statementsGen.*
import com.anatawa12.decompiler.util.Property
import kotlinx.collections.immutable.mutate

/**
 * stk1 = any1
 * any2 = stk1
 *
 * to
 *
 * any2 = any1
 */
object SingleConsumerSingleProducerStackValueStatementsOptimizer : IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>): Boolean {
        for (statement1 in statements) {
            val statement = statement1.exp() as? Assign ?: continue
            val stackValue = statement.variable as? StackVariable ?: continue
            val identifier = stackValue.identifier
            if (!identifier.isSingleConsumeSingleProduce) continue
            val producer = identifier.producers.single() as? Assign ?: continue
            check(producer == statement)
            val prop: Property<in Value, *>
            val rootStat: Statement
            if (identifier.consumerExpressions.isNotEmpty()) {
                val consumer = identifier.consumerExpressions.single()
                rootStat =
                        rootStatementOf(
                                consumer.thisRef
                        )
                prop = consumer.castInAs() ?: continue
            } else {
                val consumer = identifier.consumerStatements.single()
                rootStat = consumer.thisRef
                prop = consumer.castInAs() ?: continue
            }

            if (producer.mainStat.next != rootStat) continue
            if (rootStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue

            producer.mainStat.removeMe()

            val from = statement.value
            rootStat.labelsTargetsMe = rootStat.labelsTargetsMe.mutate { it.addAll(producer.mainStat.labelsTargetsMe) }
            prop.value = from

            return true
        }

        return false
    }

    private fun rootStatementOf(exp: Value): Statement {
        if (exp is ExpressionValue)
            return exp.consumerStatement?.thisRef ?: rootStatementOf(
                    exp.consumer!!.thisRef
            )
        if (exp is ExpressionVariable)
            return exp.consumerStatement?.thisRef
                    ?: exp.producer as? Statement
                    ?: exp.producer.let { it as? Value }?.let {
                        rootStatementOf(
                                it
                        )
                    }
                    ?: rootStatementOf(
                            exp.consumer!!.thisRef
                    )
        error("cannot get root statement of $exp")
    }
}
