package com.anatawa12.decompiler.optimizer.expressions

import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.statementsGen.InvokeVirtualValue
import com.anatawa12.decompiler.statementsGen.NewAndCallConstructor
import com.anatawa12.decompiler.statementsGen.StringContacting
import com.anatawa12.decompiler.statementsGen.Value
import com.anatawa12.decompiler.util.Property

/**
 * new StringBuilder().append(any).append(any).append(any)....toString()
 * ->
 * any + any + any + ...
 */
object StringPlusOperatorContactingExpressionOptimizer : IExpressionOptimizer {
    override fun optimize(expr: Property<out Value, *>, ctx: ProcessorContext): Boolean {
        val setter = expr.castInAs<StringContacting>() ?: return false
        val callToString = expr.value as? InvokeVirtualValue ?: return false
        // verify call toString():String
        if (callToString.name != "toString") return false
        if (callToString.desc != "()L${"java/lang/String"};") return false
        // appends
        val appends = mutableListOf<InvokeVirtualValue>()
        val anys = mutableListOf<Value>()

        var cur = callToString
        while (true) {
            cur = cur.self as? InvokeVirtualValue ?: break
            if (cur.name != "append") return false
            if (!cur.desc.endsWith(")L${"java/lang/StringBuilder"};")) return false
            if (cur.args.size != 1) return false
            appends += cur
            anys += cur.args.single()
        }

        val construct = cur.self as? NewAndCallConstructor ?: return false
        if (construct.owner != "java/lang/StringBuilder") return false
        if (construct.desc != "()V") return false

        callToString.dispose()
        for (append in appends) {
            append.dispose()
        }
        construct.dispose()

        setter.value = StringContacting(anys.asReversed())

        return true
    }
}
