package com.anatawa12.decompiler.processor

import com.anatawa12.decompiler.statementsGen.*

class SetLocalVariableNameProcessor : IProcessor {
    private val wasLabel = mutableSetOf<StatLabel>()

    override fun process(method: StatementsMethod, ctx: ProcessorContext) {
        for (statement in method.beginStatement) {
            wasLabel += statement.labelsTargetsMe
            for (local in getAllLocalVariable(statement)) {
                val info = method.localVariables.asSequence()
                        .filter { it.index == local.index }
                        .filter { it.start in wasLabel }
                        .filter { it.end !in wasLabel }
                        .firstOrNull() ?: continue
                if (local.identifier.info == null) {
                    local.identifier.info = info
                } else {
                    val infoOld = local.identifier.info!!
                    if (infoOld.start in wasLabel && infoOld.end !in wasLabel) {
                        check(local.identifier.info == info)
                    } else {
                        // the infoOld is out of range so check name and type
                        check(infoOld.name == info.name)
                        check(infoOld.descriptor == info.descriptor)
                        check(infoOld.signature == info.signature)
                        check(infoOld.index == info.index)
                    }
                }
            }
        }
    }

    private fun getAllLocalVariable(statement: Statement) = sequence<LocalVariable> {
        for (consume in statement.consumes) {
            yieldAll(getAllLocalVariable(consume.value))
        }
    }

    private fun getAllLocalVariable(value: Value): Sequence<LocalVariable> = sequence<LocalVariable> {
        if (value is LocalVariable) yield(value)
        for (consume in value.consumes) {
            yieldAll(getAllLocalVariable(consume.value))
        }
        when (value) {
            is Assign -> getAllLocalVariable(value.variable)
        }
    }
}
