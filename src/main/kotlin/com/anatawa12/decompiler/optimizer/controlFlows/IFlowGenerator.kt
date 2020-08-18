package com.anatawa12.decompiler.optimizer.controlFlows

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.Statement
import com.anatawa12.decompiler.statementsGen.StatementsMethod

interface IFlowGenerator {
    /**
     * @return returns true if generated one or more flows
     */
    fun generate(statements: Iterable<Statement>, method: StatementsMethod, ctx: ProcessorContext): Boolean
}
