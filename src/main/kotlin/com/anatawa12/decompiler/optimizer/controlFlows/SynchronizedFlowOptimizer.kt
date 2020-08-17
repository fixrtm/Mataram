@file:Suppress("NestedLambdaShadowedImplicitParameter")

package com.anatawa12.decompiler.optimizer.controlFlows

import com.anatawa12.decompiler.optimizer.statements.IStatementsOptimizer
import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.*
import kotlinx.collections.immutable.persistentListOf
import org.objectweb.asm.Label

/**
 * Labels0:
 * stat00: MonitorEnter (var1 = expression)
 * stat01: TryBlockStart TryCatchBlockIdentifier(00001)
 *         statements1
 *             Labels1:
 * stat10: MonitorExit var1
 * stat11: TryBlockEnd TryCatchBlockIdentifier(00001)
 * stat12: goto one_of_Labels2:
 * stat13: TryBlockStart TryCatchBlockIdentifier(00002)
 * stat14: CatchBlockStart(var2) null TryCatchBlockIdentifier(00001)
 * stat15: CatchBlockStart(var2) null TryCatchBlockIdentifier(00002)
 * stat16: MonitorExit var1
 * stat17: TryBlockEnd TryCatchBlockIdentifier(00002)
 * stat18: throw var2
 *             Labels2:
 * stat19: unknownStat
 *
 * Labels0:
 * synchronized(expression) {
 *   statements1
 *       Labels1:
 * }
 * Labels2:
 * unknownStat
 */
object SynchronizedFlowOptimizer : IStatementsOptimizer {
    override fun optimize(statements: Iterable<Statement>, ctx: ProcessorContext): Boolean {
        loop@ for (statement in statements) {
            //@formatter:off
            val stat00: MonitorEnter    = statement[0] as? MonitorEnter ?: return false
            val stat01: TryBlockStart   = statement[1] as? TryBlockStart ?: return false
            //@formatter:on

            val monitorAssign = stat00.monitorObj as? Assign ?: return false
            val monitorVariable = monitorAssign.variable as? LocalVariable ?: return false
            val monitorExpression = monitorAssign.value

            val try1End = stat01.identifier.tryEnd!!

            //@formatter:off
            val stat10: MonitorExit     = try1End[0 - 1] as? MonitorExit ?: return false
            val stat11: TryBlockEnd     = try1End[1 - 1] as? TryBlockEnd ?: return false
            val stat12: Goto            = try1End[2 - 1] as? Goto ?: return false
            val stat13: TryBlockStart   = try1End[3 - 1] as? TryBlockStart ?: return false
            val stat14: CatchBlockStart = try1End[4 - 1] as? CatchBlockStart ?: return false
            val stat15: CatchBlockStart = try1End[5 - 1] as? CatchBlockStart ?: return false
            val stat16: MonitorExit     = try1End[6 - 1] as? MonitorExit ?: return false
            val stat17: TryBlockEnd     = try1End[7 - 1] as? TryBlockEnd ?: return false
            val stat18: ThrowException  = try1End[8 - 1] as? ThrowException ?: return false
            val stat19: Statement       = try1End[9 - 1]
            //@formatter:on

            // try-catch check
            if (stat01.identifier != stat11.identifier) return false
            if (stat13.identifier != stat17.identifier) return false
            if (stat01.identifier == stat14.identifier) {
                if (stat01.identifier != stat14.identifier) return false
                if (stat13.identifier != stat15.identifier) return false
            } else {
                if (stat01.identifier != stat15.identifier) return false
                if (stat13.identifier != stat14.identifier) return false
            }

            // monitor check
            if (stat10.monitorObj != monitorVariable) return false
            if (stat16.monitorObj != monitorVariable) return false
            if (monitorVariable.identifier.consumerCount != 2) return false
            if (monitorVariable.identifier.producerCount != 1) return false

            // label check
            val labels0 = stat00.labelsTargetsMe
            if (stat01.labelsTargetsMe.any { it.usedBy.any() }) return false

            var labels1 = stat10.labelsTargetsMe
            if (stat11.labelsTargetsMe.any { it.usedBy.any() }) return false
            if (stat12.labelsTargetsMe.any { it.usedBy.any() }) return false
            if (stat13.labelsTargetsMe.any { it.usedBy.any() }) return false
            if (stat14.labelsTargetsMe.any { it.usedBy.any() }) return false
            if (stat15.labelsTargetsMe.any { it.usedBy.any() }) return false
            if (stat16.labelsTargetsMe.any { it.usedBy.any() }) return false
            if (stat17.labelsTargetsMe.any { it.usedBy.any() }) return false
            if (stat18.labelsTargetsMe.any { it.usedBy.any() }) return false
            val labels2 = stat19.labelsTargetsMe

            // goto check
            if (stat12.label !in labels2) return false

            // run replace

            // first, select break label or make
            if (labels1.isEmpty()) {
                labels1 = persistentListOf(StatLabel(Label()))
            }
            val breakLabel = labels1.first()

            val statements1 = BlockBeginStatement.makeBlockByBeginEnd(
                stat01.next,
                stat10.prev,
            )

            stat00.removeMe()
            monitorAssign.dispose()
            stat01.removeMe()
            stat10.removeMe()
            stat11.removeMe()
            stat12.removeMe()
            stat13.removeMe()
            stat14.removeMe()
            stat15.removeMe()
            stat16.removeMe()
            stat17.removeMe()
            stat18.removeMe()

            val flow = SynchronizedFlow(
                monitorObj = monitorExpression,
                block = statements1.first,
                breakLabel = breakLabel,
            )

            flow.labelsTargetsMe = labels0
            statements1.second.labelsTargetsMe = labels1

            stat19.insertPrev(flow)

            return true
        }
        return false
    }
}
