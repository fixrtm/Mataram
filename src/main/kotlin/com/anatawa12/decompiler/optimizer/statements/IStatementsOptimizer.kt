package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.Statement

interface IStatementsOptimizer {
    fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean
}
