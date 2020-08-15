package com.anatawa12.decompiler.statementsGen

import kotlinx.collections.immutable.ImmutableList

class StatementsMethod(
    var beginStatement: MethodBeginStatement,
    var endStatement: MethodEndStatement,
    var localVariables: ImmutableList<LocalVariableInfo>,
    val signature: MethodCoreSignature,
)
