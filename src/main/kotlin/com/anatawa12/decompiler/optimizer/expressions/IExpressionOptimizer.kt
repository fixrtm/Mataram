package com.anatawa12.decompiler.optimizer.expressions

import com.anatawa12.decompiler.statementsGen.Value
import com.anatawa12.decompiler.util.Property

interface IExpressionOptimizer {
    fun optimize(expr: Property<out Value, *>): Boolean
}
