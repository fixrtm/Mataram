package com.anatawa12.decompiler.optimizer.controlFlows

import com.anatawa12.decompiler.optimizer.statements.IStatementsOptimizer
import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*

/**
 * goto L_01 if a
 * statements1
 * goto L_02
 * L_01:
 * statements2
 * L_02:
 *
 * to
 *
 * if (!a) { // breakLabel: L_02
 *   statements1
 * } else {
 *   L_01:
 *   statements2
 * }
 * L_02:
 */
object IfElseControlFlowOptimizer : IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        loop@ for (statement in statements) {
            val conditionalGoto = statement as? ConditionalGoto ?: continue@loop
            val label01 = conditionalGoto.label
            val label01At = label01.at!!
            val goto = label01At.prev as? Goto ?: continue@loop
            val label02 = goto.label
            val label02At = label02.at!!

            if (!isContainsAfter(conditionalGoto, label01At)) continue@loop
            if (!isContainsAfter(goto, label02At)) continue@loop

            val statements1 = BlockBeginStatement.makeBlockByBeginEnd(
                begin = conditionalGoto.next,
                end = goto.prev,
            )
            val statements2 = BlockBeginStatement.makeBlockByBeginEnd(
                begin = label01At,
                end = label02At.prev,
            )

            /*
             * main: 
             *   goto L_01 if a
             *   goto L_02
             *   L_02:
             *   // unknown here
             * statements1 instance:
             *   statements1
             * statements2 instance:
             *   L_01:
             *   statements2
             */

            conditionalGoto.removeMe()
            goto.removeMe()


            val ifElse = IfElseControlFlow(
                condition = BooleanNotValue(conditionalGoto.value),
                thenBlock = statements1.first,
                elseBlock = statements2.first,
                breakLabel = label02,
            )

            goto.next.insertPrev(ifElse)

            ifElse.labelsTargetsMe = conditionalGoto.labelsTargetsMe
            statements1.second.labelsTargetsMe = goto.labelsTargetsMe
            return true
        }
        return false
    }
}
