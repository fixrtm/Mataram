package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*

/**
 * stk0 = statementExpression
 * to
 * statementExpression
 */
object NoConsumeSingleProducerStatementExpressionStatementOptimizer : IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        for (statement in statements) {
            val assign = statement.exp() as? Assign ?: continue
            val stk0 = assign.variable as? StackVariable ?: continue
            val statementExpression = assign.value as? StatementExpressionValue ?: continue

            if (stk0.identifier.consumerCount != 0) continue
            if (stk0.identifier.producerCount != 1) continue

            assign.mainStat.removeMe()

            assign.mainStat.next.insertPrev(statementExpression.stat())
            statementExpression.mainStat.labelsTargetsMe = assign.mainStat.labelsTargetsMe

            return true
        }
        return false
    }
}
