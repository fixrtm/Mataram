package com.anatawa12.decompiler.processor

import com.anatawa12.decompiler.optimizer.expressions.IExpressionOptimizer
import com.anatawa12.decompiler.optimizer.statements.IStatementsOptimizer
import com.anatawa12.decompiler.statementsGen.Statement
import com.anatawa12.decompiler.statementsGen.StatementsMethod
import com.anatawa12.decompiler.statementsGen.Value
import com.anatawa12.decompiler.util.Property

class OptimizeProcessor(
    private val statementOptimizer: List<IStatementsOptimizer>,
    private val expressionOptimizer: List<IExpressionOptimizer>,
) : IProcessor {
    override fun process(method: StatementsMethod, ctx: ProcessorContext) {
        while (true) {
            val modified = doOptimize(method.beginStatement, ctx)
            if (!modified)
                break
        }
    }

    private fun doOptimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        for (optimizer in statementOptimizer) {
            val modified = optimizer.optimize(statements, ctx)
            if (modified)
                return true
        }

        for (statement in statements) {
            for (childBlock in statement.childBlocks) {
                val modified = doOptimize(childBlock, ctx)
                if (modified)
                    return true
            }
        }

        // statement optimizer cannot process so run expression optimizer

        var modified = false
        for (statement in statements) {
            for (produce in statement.produces) {
                if (optimize(produce, ctx)) modified = true
            }
            for (consume in statement.consumes) {
                if (optimize(consume, ctx)) modified = true
            }
        }
        return modified
    }

    private fun optimize(expr: Property<out Value, *>, ctx: ProcessorContext): Boolean {
        var modified = false
        while (true) {
            if (doOptimize(expr, ctx)) modified = true
            else break
        }
        val exp = expr.value

        for (produce in exp.produces) {
            if (optimize(produce, ctx)) modified = true
        }
        for (consume in exp.consumes) {
            if (optimize(consume, ctx)) modified = true
        }
        return modified
    }

    private fun doOptimize(expr: Property<out Value, *>, ctx: ProcessorContext): Boolean {
        for (optimizer in expressionOptimizer) {
            val modified = optimizer.optimize(expr, ctx)
            if (modified)
                return true
        }

        return false
    }
}
