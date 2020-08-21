package com.anatawa12.decompiler.statementsGen

import com.anatawa12.decompiler.util.nextSequence

/**
 * both ends are inclusive
 */
fun isSameStatements(aFirst: Statement, aEnd: Statement, bFirst: Statement, bEnd: Statement): Boolean {
    val aSeq = aFirst.nextSequence(aEnd.next, Statement::next)
    val bSeq = bFirst.nextSequence(bEnd.next, Statement::next)
    return StatementsComparator().isSame(aSeq, bSeq)
}

internal expect class StatementsComparator() {
    fun isSame(aSeq: Sequence<Statement>, bSeq: Sequence<Statement>): Boolean
}
