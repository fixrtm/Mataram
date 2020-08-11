package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.statementsGen.*

/**
 * TryBlockEnd identifier
 * goto-like instruction
 * to
 * goto-like instruction
 * TryBlockEnd identifier
 *
 * goto-like instruction:
 *   goto L0
 *   throw value
 *   return
 *   return value
 */
object MoveTryBlockEndToNextToGotoLikeStatementsOptimizer :
        IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>): Boolean {
        for (statement in statements) {
            val tryBlockEnd = statement as? TryBlockEnd ?: continue
            val gotoLike = when (val next = tryBlockEnd.next) {
                is Goto -> next
                is ThrowException -> next
                is ReturnValue -> next
                is ReturnVoid -> next
                else -> continue
            }

            val prev = tryBlockEnd.prev
            val next = gotoLike.next

            prev.next = gotoLike
            gotoLike.prev = prev

            gotoLike.next = tryBlockEnd
            tryBlockEnd.prev = gotoLike

            tryBlockEnd.next = next
            next.prev = tryBlockEnd
            return true
        }
        return false
    }
}
