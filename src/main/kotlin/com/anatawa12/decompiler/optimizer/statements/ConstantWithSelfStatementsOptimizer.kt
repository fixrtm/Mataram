package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.mutate

/**
 * stk1 = value
 * stk2 = constantValue
 * to
 * stk2 = value.constantValue // constantValue is final value
 */
object ConstantWithSelfStatementsOptimizer : IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        for (statement in statements) {
            val assign1 = statement[0].exp() as? Assign ?: continue
            val assign2 = statement[1].exp() as? Assign ?: continue

            val stk1 = assign1.variable as? StackVariable ?: continue
            val value = assign1.value
            //val stk2 = assign2.variable as? StackVariable ?: continue
            val constantValue = assign2.value as? ConstantValue ?: continue
            val type = value.type ?: continue
            if (stk1.identifier.consumerCount != 0) continue
            if (assign2.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue
            val sClass = ctx.env.forDescriptorOrNull(ctx.scl, type.descriptor)
            if (sClass == null || sClass.isPrimitive) continue
            val field = sClass.fields.singleOrNull { sameConstant(it.constantValue, constantValue.value) } ?: continue

            assign1.mainStat.removeMe()
            constantValue.dispose()

            assign2.mainStat.labelsTargetsMe =
                assign1.mainStat.labelsTargetsMe.mutate { it.addAll(assign2.mainStat.labelsTargetsMe) }

            if (field.isStatic) {
                assign2.value = StaticFieldWithSelf(type.internalName, field.name, field.descriptor, value)
            } else {
                assign2.value = InstanceField(type.internalName, field.name, field.descriptor, value)
            }

            return true
        }
        return false
    }

    private fun sameConstant(a: Any?, b: Any?): Boolean {
        if (a is Long && b is Long)
            return a == b
        if (a is Number && b is Number)
            return a.toDouble().compareTo(b.toDouble()) == 0
        return a == b
    }
}
