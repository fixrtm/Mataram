package com.anatawa12.decompiler.optimizer.controlFlows

import com.anatawa12.decompiler.optimizer.statements.IStatementsOptimizer
import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*

/**
 * L_01: // continue target
 * goto L_02 if a
 * statements1
 * goto L_01
 * L_02: // break target
 *
 * L_01:
 * while(!a) {
 *   statements1
 * }
 * L_02:
 */
object WhileControlFlowOptimizer : IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        loop@ for (statement in statements) {
            val conditionalGoto = statement as? ConditionalGoto ?: continue@loop
            val label02 = conditionalGoto.label
            val label02At = label02.at!!
            val goto = label02At.prev as? Goto ?: continue@loop
            val label01 = goto.label
            val label01At = label01.at!!

            if (label01 !in conditionalGoto.labelsTargetsMe) continue@loop
            if (!isContainsAfter(conditionalGoto, goto)) continue@loop
            check(label01At == conditionalGoto)

            conditionalGoto.removeMe()
            goto.removeMe()

            val statements1 = BlockBeginStatement.makeBlockByBeginEnd(
                begin = conditionalGoto.next,
                end = goto.prev,
            )
            val whileFlow = WhileControlFlow(
                condition = BooleanNotValue(conditionalGoto.value),
                block = statements1.first,
                continueLabel = label01,
                breakLabel = label02,
            )

            goto.next.insertPrev(whileFlow)

            whileFlow.labelsTargetsMe = conditionalGoto.labelsTargetsMe
            statements1.second.labelsTargetsMe = goto.labelsTargetsMe

            return true
        }
        return false
    }
}
