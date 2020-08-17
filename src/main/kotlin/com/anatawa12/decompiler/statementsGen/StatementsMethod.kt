package com.anatawa12.decompiler.statementsGen

import kotlinx.collections.immutable.ImmutableList

class StatementsMethod(
    var beginStatement: BlockBeginStatement,
    var endStatement: BlockEndStatement,
    var localVariables: ImmutableList<LocalVariableInfo>,
    val signature: MethodCoreSignature,
)
