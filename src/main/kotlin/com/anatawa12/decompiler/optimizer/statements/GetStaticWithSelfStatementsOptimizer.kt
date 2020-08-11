package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.mutate

/**
 * stk1 = value
 * stk2 = staticValue
 * to
 * stk2 = value.staticValue
 */
object GetStaticWithSelfStatementsOptimizer :
        IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>): Boolean {
        for (statement in statements) {
            val assign1 = statement[0].exp() as? Assign ?: continue
            val assign2 = statement[1].exp() as? Assign ?: continue

            val stk1 = assign1.variable as? StackVariable ?: continue
            val value = assign1.value
            //val stk2 = assign2.variable as? StackVariable ?: continue
            val staticValue = assign2.value as? StaticField ?: continue
            val type = value.type ?: continue
            if (type.internalName != staticValue.owner) continue
            if (stk1.identifier.consumerCount != 0) continue
            if (assign2.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue

            assign1.mainStat.removeMe()
            staticValue.dispose()

            assign2.mainStat.labelsTargetsMe = assign1.mainStat.labelsTargetsMe.mutate { it.addAll(assign2.mainStat.labelsTargetsMe) }
            assign2.value = StaticFieldWithSelf(staticValue.owner, staticValue.name, staticValue.desc, value)

            return true
        }
        return false
    }
}
