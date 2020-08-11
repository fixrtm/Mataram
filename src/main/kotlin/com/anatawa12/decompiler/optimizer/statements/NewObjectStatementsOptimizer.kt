package com.anatawa12.decompiler.optimizer.statements

import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf

/*
/**
 * stk1 = (stk2 = newobj LType1;)
 * stk2.<init>(arguments)
 * to
 * stk1 = new LType1;(arguments)
 */
object NewObjectStatementsOptimizer : IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>): Boolean {
        for (statement in statements) {
            val newObjAssignAssign = statement[0].exp() as? Assign ?: continue
            val invokeSpecialVoid = statement[1] as? InvokeSpecialVoid ?: continue

            val newObjAssign = newObjAssignAssign.value as? Assign ?: continue

            val stk10 = newObjAssignAssign.variable
            val stk20 = newObjAssign.variable
            val stk21 = invokeSpecialVoid.self

            if (stk20 != stk21) continue
            if (invokeSpecialVoid.name != "<init>") continue

            newObjAssignAssign.mainStat.removeMe()
            invokeSpecialVoid.removeMe()

            val assign = Assign(stk10, NewAndCallConstructor(invokeSpecialVoid.owner, invokeSpecialVoid.desc, invokeSpecialVoid.args))

            invokeSpecialVoid.next.insertPrev(assign.stat())
            assign.mainStat.labelsTargetsMe = persistentListOf<StatLabel>().mutate {
                it.addAll(newObjAssignAssign.mainStat.labelsTargetsMe)
                it.addAll(invokeSpecialVoid.labelsTargetsMe)
            }
            return true
        }
        return false
    }
}

// */
//*

/**
 *
 * stk1 = new LType1;
 * stk2 = stk1
 * stk1.<init>(arguments)
 *
 * to
 *
 * stk2 = new LType1;(arguments)
 */
object NewObjectStatementsOptimizer :
        IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>): Boolean {
        for (statement in statements) {
            val newObjAssign = statement[0].exp() as? Assign ?: continue
            val setToStackAssign = statement[1].exp() as? Assign ?: continue
            val invokeSpecialVoid = statement[2] as? InvokeSpecialVoid ?: continue

            val newObj = newObjAssign.value as? NewObject ?: continue
            val newObjAssignTo = newObjAssign.variable as? StackVariable ?: continue
            val stk2 = setToStackAssign.variable as? StackVariable ?: continue

            if (newObjAssignTo != setToStackAssign.value) continue
            if (newObjAssignTo != invokeSpecialVoid.self) continue
            if (newObjAssignTo.identifier.consumerCount != 2) continue
            if (newObjAssignTo.identifier.producerCount != 1) continue
            if (invokeSpecialVoid.owner != newObj.type.internalName) continue

            if (setToStackAssign.mainStat.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue
            if (invokeSpecialVoid.labelsTargetsMe.any { it.usedBy.isNotEmpty() }) continue


            newObjAssign.mainStat.removeMe()
            setToStackAssign.mainStat.removeMe()
            invokeSpecialVoid.removeMe()

            val assign = Assign(stk2, NewAndCallConstructor(invokeSpecialVoid.owner, invokeSpecialVoid.desc, invokeSpecialVoid.args))

            invokeSpecialVoid.next.insertPrev(assign.stat())
            assign.mainStat.labelsTargetsMe = persistentListOf<StatLabel>().mutate {
                it.addAll(newObjAssign.mainStat.labelsTargetsMe)
                it.addAll(setToStackAssign.mainStat.labelsTargetsMe)
                it.addAll(invokeSpecialVoid.labelsTargetsMe)
            }
            return true
        }
        return false
    }
}

// */
