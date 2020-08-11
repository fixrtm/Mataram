package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.statementsGen.*

/**
 * CatchBlockStart(stk1) type identifier
 * var1 = stk1
 * to
 * CatchBlockStart(var1) type identifier
 */
object MakeCatchBlockStartSetsLocalVariableStatementsOptimizer :
        IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>): Boolean {
        root@ for (statement in statements) {
            val catchBlockStarts = mutableListOf<CatchBlockStart>()
            var cur = statement
            while (true) {
                val catchBlockStart = cur as? CatchBlockStart ?: break
                catchBlockStarts += catchBlockStart
                cur = catchBlockStart.next
            }
            if (catchBlockStarts.isEmpty()) continue@root
            val assign = cur.exp() as? Assign ?: continue@root

            val assignTo = assign.variable as? LocalVariable ?: continue@root
            val assignFrom = assign.value as? StackVariable ?: continue@root

            if (assignFrom.identifier.consumerCount != 1) continue@root
            if (assignFrom.identifier.producerCount != catchBlockStarts.size) continue@root

            for (catchBlockStart in catchBlockStarts) {
                if (catchBlockStart.catchVariable != assignFrom) continue@root
            }

            for (catchBlockStart in catchBlockStarts) {
                catchBlockStart.catchVariable = assignTo
            }

            assign.mainStat.removeMe()

            return true
        }
        return false
    }
}
