package com.anatawa12.decompiler.statementsGen


fun Statement.exp(): StatementExpressionValue? = (this as? StatementExpressionStatement)?.expression

fun StatementExpressionValue.stat() = StatementExpressionStatement(this)

