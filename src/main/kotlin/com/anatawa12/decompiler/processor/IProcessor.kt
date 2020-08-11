package com.anatawa12.decompiler.processor

import com.anatawa12.decompiler.statementsGen.StatementsMethod

interface IProcessor {
    fun process(method: StatementsMethod)
}
