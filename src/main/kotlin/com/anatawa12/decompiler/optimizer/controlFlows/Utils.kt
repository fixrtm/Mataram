package com.anatawa12.decompiler.optimizer.controlFlows

import com.anatawa12.decompiler.statementsGen.BlockEndStatement
import com.anatawa12.decompiler.statementsGen.Statement


fun isContainsAfter(after: Statement, find: Statement): Boolean {
    var cur = after
    while (cur != find) {
        if (cur is BlockEndStatement) return false
        cur = cur.next
    }
    return true
}
