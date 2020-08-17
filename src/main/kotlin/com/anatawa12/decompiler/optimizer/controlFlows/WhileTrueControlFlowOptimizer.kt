package com.anatawa12.decompiler.optimizer.controlFlows

import com.anatawa12.decompiler.optimizer.statements.IStatementsOptimizer
import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import org.objectweb.asm.Label

/**
 * L_01: // continue target
 * statements1
 * goto L_01
 * L_02: // break target
 *
 * L_01:
 * while(true) {
 *   statements1
 * }
 * L_02:
 */
object WhileTrueControlFlowOptimizer : IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        loop@ for (statement in statements) {
            val goto = statement as? Goto ?: continue@loop
            val label01 = goto.label
            val label01At = label01.at!!

            if (!isContainsAfter(label01At, goto)) continue@loop

            goto.removeMe()
            val label02 = goto.next.labelsTargetsMe.firstOrNull() ?: kotlin.run {
                val newLabel = StatLabel(Label())
                goto.next.labelsTargetsMe += newLabel
                newLabel
            }

            val statements1 = BlockBeginStatement.makeBlockByBeginEnd(
                begin = label01At,
                end = goto.prev,
            )
            val whileFlow = WhileControlFlow(
                condition = ConstantValue(VConstantBoolean(true)),
                block = statements1.first,
                continueLabel = label01,
                breakLabel = label02,
            )

            goto.next.insertPrev(whileFlow)

            whileFlow.labelsTargetsMe = persistentListOf()
            statements1.second.labelsTargetsMe = goto.labelsTargetsMe

            return true
        }
        return false
    }
}
