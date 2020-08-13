package com.anatawa12.decompiler.statementsGen

data class LocalVariableInfo(
    val name: String,
    val descriptor: String,
    val signature: String?,
    val start: StatLabel,
    val end: StatLabel,
    val index: Int,
) : IStatLabelConsumer {
    init {
    }
}
