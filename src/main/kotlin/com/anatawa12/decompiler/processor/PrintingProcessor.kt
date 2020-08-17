package com.anatawa12.decompiler.processor

import com.anatawa12.decompiler.instructions.AllType
import com.anatawa12.decompiler.instructions.BiOp
import com.anatawa12.decompiler.instructions.ShiftOp
import com.anatawa12.decompiler.statementsGen.*

class PrintingProcessor(private val firstLine: String = "", val showDetailed: Boolean) : IProcessor {
    override fun process(method: StatementsMethod, ctx: ProcessorContext) {
        for ((name, descriptor, signature, start, end, index) in method.localVariables) {
            print("// #")
            print(index)
            print(" : ")
            print(name)
            print(" (")
            print(descriptor)
            print(", ")
            print(signature)
            print(") from ")
            print(start)
            print(" to ")
            print(end)
            println()
        }
        println(firstLine)
        for (statement in method.beginStatement) {
            for (label in statement.labelsTargetsMe) {
                print("    ")
                print(label)
                print('(')
                print(label.usedBy.size)
                print("):")
                println()
            }
            ps(statement)
            println()
        }
    }

    private fun ps(s: Statement, indent: String = "") {
        print(indent)
        if (s.lineNumber != -2) {
            print("#")
            print(s.lineNumber)
            print(": ")
        }

        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        when (s) {
            is BlockBeginStatement -> {
                print("/// start")
            }
            is BlockEndStatement -> {
                print("/// end")
            }
            is StatementExpressionStatement -> {
                pe(s.expression)
            }
            is ConditionalGoto -> {
                print("goto ")
                print(s.label)
                print(" if ")
                pe(s.value)
            }
            is Goto -> {
                print("goto ")
                print(s.label)
            }
            is Jsr -> {
                print("jsr ")
                print(s.label)
            }
            is Ret -> {
                print("ret ")
                pe(s.variable)
            }
            is TableSwitch -> {
                print("switch (")
                pe(s.value)
                print(") ")
                print(s.min)
                print(" to ")
                print(s.max)
                print(" goto ")
                for (label in s.labels) {
                    print(label)
                    print(", ")
                }
            }
            is LookupSwitch -> {
                print("switch (")
                pe(s.value)
                print(") ")
                for ((int, label) in s.pairs) {
                    print(" if ")
                    print(int)
                    print(" goto ")
                    print(label)
                    print(", ")
                }
            }
            is ReturnValue -> {
                print("return ")
                pe(s.variable)
            }
            is ReturnVoid -> {
                print("return")
            }
            is InvokeVirtualVoid -> {
                pe(s.self)
                print('.')
                print(s.name)
                print('(')
                for (arg in s.args) {
                    pe(arg)
                    print(", ")
                }
                print(')')

                if (showDetailed) {
                    print(" // visual ")
                    print(s.owner)
                    print('.')
                    print(s.name)
                    print(':')
                    print(s.desc)
                }
            }
            is InvokeSpecialVoid -> {
                pe(s.self)
                print('.')
                print(s.name)
                print('(')
                for (arg in s.args) {
                    pe(arg)
                    print(", ")
                }
                print(')')

                if (showDetailed) {
                    print(" // special ")
                    print(s.owner)
                    print('.')
                    print(s.name)
                    print(':')
                    print(s.desc)
                }
            }
            is InvokeStaticVoid -> {
                print(s.owner)
                print('.')
                print(s.name)
                print('(')
                for (arg in s.args) {
                    pe(arg)
                    print(", ")
                }
                print(')')

                if (showDetailed) {
                    print(" // static ")
                    print(s.owner)
                    print('.')
                    print(s.name)
                    print(':')
                    print(s.desc)
                }
            }
            is InvokeInterfaceVoid -> {
                pe(s.self)
                print('.')
                print(s.name)
                print('(')
                for (arg in s.args) {
                    pe(arg)
                    print(", ")
                }
                print(')')

                if (showDetailed) {
                    print(" // interface ")
                    print(s.owner)
                    print('.')
                    print(s.name)
                    print(':')
                    print(s.desc)
                }
            }
            is InvokeDynamicVoid -> {
                print(s.name)
                print('(')
                for (arg in s.args) {
                    pe(arg)
                    print(", ")
                }
                print(')')

                print(" // dynamic ")
                print(s.bootstrapMethodHandle)
                print(s.bootstrapMethodArguments)
            }
            is ThrowException -> {
                print("throw ")
                pe(s.throws)
            }
            is MonitorEnter -> {
                print("MonitorEnter ")
                pe(s.monitorObj)
            }
            is MonitorExit -> {
                print("MonitorExit ")
                pe(s.monitorObj)
            }
            is TryBlockStart -> {
                print("TryBlockStart ")
                print(s.identifier)
            }
            is TryBlockEnd -> {
                print("TryBlockEnd ")
                print(s.identifier)
            }
            is CatchBlockStart -> {
                print("CatchBlockStart(")
                pe(s.catchVariable)
                print(") ")
                print(s.identifier.catchesInternalName)
                print(" ")
                print(s.identifier)
            }
            is InvokeStaticWithSelfVoid -> {
                pe(s.self)
                print('.')
                print(s.name)
                print('(')
                for (arg in s.args) {
                    pe(arg)
                    print(", ")
                }
                print(')')

                if (showDetailed) {
                    print(" // static ")
                    print(s.owner)
                    print('.')
                    print(s.name)
                    print(':')
                    print(s.desc)
                }
            }
            is IfElseControlFlow -> {
                val newIndent = "$indent  "
                print("if (")
                pe(s.condition)
                println(") {")
                printStatementsForBlock(s.thenBlock, newIndent)
                print(indent)
                println("} else {")
                printStatementsForBlock(s.elseBlock, newIndent)
                print(indent)
                println("}")
            }
            is WhileControlFlow -> {
                val newIndent = "$indent  "
                print("while (")
                pe(s.condition)
                println(") {")
                printStatementsForBlock(s.block, newIndent)
                println("}")
            }
            is SynchronizedFlow -> {
                val newIndent = "$indent  "
                print("synchronized (")
                pe(s.monitorObj)
                println(") {")
                printStatementsForBlock(s.block, newIndent)
                print(indent)
                println("}")
            }
            else -> error("${s.javaClass}")
        }
    }

    private fun printStatementsForBlock(statements: Iterable<Statement>, newIndent: String) {
        for (statement in statements) {
            for (label in statement.labelsTargetsMe) {
                print(newIndent)
                print("    ")
                print(label)
                print('(')
                print(label.usedBy.size)
                print("):")
                println()
            }
            if (statement is BlockEndStatement) continue
            ps(statement, newIndent)
            println()
        }
    }

    private fun pe(v: Value) {
        if (v.lineNumber != -2) {
            print("#")
            print(v.lineNumber)
            print(": ")
        }

        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        when (v) {
            is LocalVariable -> {
                val info = v.identifier.info
                if (info != null) {
                    print(info.name)
                    print('(')
                    print(v.identifier.index)
                    print(',')
                    print(v.identifier.id)
                    print(')')
                } else {
                    print("var")
                    print(v.identifier.index)
                    print('(')
                    print(v.identifier.id)
                    print(')')
                }
            }
            is StackVariable -> {
                print("stk" + v.identifier.id)
                print(v.spInfo())
            }
            is ConstantValue -> {
                when (val value = v.value) {
                    is VConstantInt -> print(value.int)
                    is VConstantLong -> print(value.long)
                    is VConstantFloat -> print(value.float)
                    is VConstantDouble -> print(value.double)
                    is VConstantByte -> print(value.byte)
                    is VConstantChar -> print("'${value.char}'")
                    is VConstantShort -> print(value.short)
                    is VConstantBoolean -> print(value.boolean)
                    VConstantNull -> print("null")
                    is VConstantString -> print("\"${value.string}\"")
                    is VConstantType -> print("${value.type}.class")
                    is VConstantMethodType -> print(value.type)
                    is VConstantHandle -> print(value.handle)
                    is VConstantConstantDynamic -> print(value.dynamic)
                }
            }
            is ArrayVariable -> {
                pe(v.ary)
                print('[')
                pe(v.idx)
                print(']')
            }
            is BiOperation -> {
                print('(')
                pe(v.left)
                when (v.op) {
                    BiOp.Add -> print(" + ")
                    BiOp.Sub -> print(" - ")
                    BiOp.Mul -> print(" * ")
                    BiOp.Div -> print(" / ")
                    BiOp.Rem -> print(" % ")
                    BiOp.And -> print(" & ")
                    BiOp.Or -> print(" | ")
                    BiOp.Xor -> print(" ^ ")
                }
                pe(v.right)
                print(')')
            }
            is ShiftOperation -> {
                print('(')
                pe(v.value)
                when (v.op) {
                    ShiftOp.Shl -> print(" >> ")
                    ShiftOp.Shr -> print(" << ")
                    ShiftOp.UShr -> print(" <<< ")
                }
                pe(v.shift)
                print(')')
            }
            is NegativeOperation -> {
                print("-")
                pe(v.value)
            }
            is CastValue -> {
                when (v.castTo) {
                    AllType.Boolean -> error("invalid cast type to boolean")
                    AllType.Object -> error("invalid cast type to object")
                    AllType.Char -> print("(char)")
                    AllType.Byte -> print("(byte)")
                    AllType.Short -> print("(short)")
                    AllType.Integer -> print("(int)")
                    AllType.Long -> print("(long)")
                    AllType.Double -> print("(double)")
                    AllType.Float -> print("(float)")
                }
                print('(')
                pe(v.value)
                print(')')
            }
            is LongCompare -> {
                pe(v.left)
                print(" cmpLong ")
                pe(v.right)
            }
            is FloatingCompareLesser -> {
                pe(v.left)
                print(" cmpLess ")
                pe(v.right)
            }
            is FloatingCompareGreater -> {
                pe(v.left)
                print(" cmpGreat ")
                pe(v.right)
            }
            is ConditionValue -> {
                print('(')
                pe(v.left)
                when (v.condition) {
                    BiCondition.EQ -> print(" == ")
                    BiCondition.NE -> print(" != ")
                    BiCondition.LT -> print(" < ")
                    BiCondition.GE -> print(" >= ")
                    BiCondition.GT -> print(" > ")
                    BiCondition.LE -> print(" <= ")
                }
                pe(v.right)
                print(')')
            }
            is StaticField -> {
                print(v.owner)
                print('.')
                print(v.name)

                if (showDetailed) {
                    print(" /*")
                    print(v.owner)
                    print('.')
                    print(v.name)
                    print(':')
                    print(v.desc)
                    print("*/")
                }
            }
            is InstanceField -> {
                pe(v.self)
                print('.')
                print(v.name)

                if (showDetailed) {
                    print(" /*")
                    print(v.owner)
                    print('.')
                    print(v.name)
                    print(':')
                    print(v.desc)
                    print("*/")
                }
            }
            is InvokeVirtualValue -> {
                pe(v.self)
                print('.')
                print(v.name)
                print('(')
                for (arg in v.args) {
                    pe(arg)
                    print(", ")
                }
                print(')')

                if (showDetailed) {
                    print(" /* visual ")
                    print(v.owner)
                    print('.')
                    print(v.name)
                    print(':')
                    print(v.desc)
                    print("*/")
                }
            }
            is InvokeSpecialValue -> {
                pe(v.self)
                print('.')
                print(v.name)
                print('(')
                for (arg in v.args) {
                    pe(arg)
                    print(", ")
                }
                print(')')

                if (showDetailed) {
                    print(" /* special ")
                    print(v.owner)
                    print('.')
                    print(v.name)
                    print(':')
                    print(v.desc)
                    print("*/")
                }
            }
            is InvokeStaticValue -> {
                print(v.owner)
                print('.')
                print(v.name)
                print('(')
                for (arg in v.args) {
                    pe(arg)
                    print(", ")
                }
                print(')')

                if (showDetailed) {
                    print(" /* static ")
                    print(v.owner)
                    print('.')
                    print(v.name)
                    print(':')
                    print(v.desc)
                    print("*/")
                }
            }
            is InvokeInterfaceValue -> {
                pe(v.self)
                print('.')
                print(v.name)
                print('(')
                for (arg in v.args) {
                    pe(arg)
                    print(", ")
                }
                print(')')

                if (showDetailed) {
                    print(" /* interface ")
                    print(v.owner)
                    print('.')
                    print(v.name)
                    print(':')
                    print(v.desc)
                    print("*/")
                }
            }
            is InvokeDynamicValue -> {
                print(v.name)
                print('(')
                for (arg in v.args) {
                    pe(arg)
                    print(", ")
                }
                print(')')

                print(" /*dynamic ")
                print(v.bootstrapMethodHandle)
                print(v.bootstrapMethodArguments)
                print("*/")
            }
            is NewObject -> {
                print("newobj ")
                print(v.type)
            }
            is NewArray -> {
                print("newArray ")
                print(v.element)
                print('[')
                pe(v.size)
                print(']')
            }
            is ArrayLength -> {
                pe(v.array)
                print(".length")
            }
            is CheckCast -> {
                pe(v.value)
                print(" as ")
                print(v.type)
            }
            is InstanceOf -> {
                pe(v.value)
                print(" instanceof ")
                print(v.classType)
            }
            is MultiANewArray -> {
                print("multinewarray ")
                print(v.arrayType)
                print("[")
                for (dimensionSize in v.dimensionSizes) {
                    pe(dimensionSize)
                    print(", ")
                }
                print("]")
            }
            is NewAndCallConstructor -> {
                print("new ")
                print(v.owner)
                print('(')
                for (arg in v.args) {
                    pe(arg)
                    print(", ")
                }
                print(')')

                if (showDetailed) {
                    print(" /* new ")
                    print(v.owner)
                    print(".<init>:")
                    print(v.desc)
                    print("*/")
                }
            }
            is Assign -> {
                print('(')
                pe(v.variable)
                print(" = ")
                pe(v.value)
                print(')')
            }
            is NewArrayWithInitializerValue -> {
                print("newArray ")
                print(v.elementType)
                print("[] {")
                for (value in v.arrayInitializer) {
                    pe(value)
                    print(", ")
                }
                print('}')
            }
            is BooleanNotValue -> {
                print('!')
                pe(v.value)
            }
            is ConditionalOperatorValue -> {
                pe(v.condition)
                print(" ? ")
                pe(v.ifTrue)
                print(" : ")
                pe(v.ifFalse)
            }
            is BiOperationAssignedValue -> {
                print('(')
                pe(v.variable)
                when (v.op) {
                    BiOp.Add -> print(" += ")
                    BiOp.Sub -> print(" -= ")
                    BiOp.Mul -> print(" *= ")
                    BiOp.Div -> print(" /= ")
                    BiOp.Rem -> print(" %= ")
                    BiOp.And -> print(" &= ")
                    BiOp.Or -> print(" |= ")
                    BiOp.Xor -> print(" ^= ")
                }
                pe(v.right)
                print(')')
            }
            is ShiftOperationAssignedValue -> {
                print('(')
                pe(v.value)
                when (v.op) {
                    ShiftOp.Shl -> print(" <<= ")
                    ShiftOp.Shr -> print(" >>= ")
                    ShiftOp.UShr -> print(" >>>= ")
                }
                pe(v.shift)
                print(')')
            }

            is BooleanAndAndOperation -> {
                print('(')
                pe(v.left)
                print(" && ")
                pe(v.right)
                print(')')
            }
            is BooleanOrOrOperation -> {
                print('(')
                pe(v.left)
                print(" || ")
                pe(v.right)
                print(')')
            }
            is InDecrementValue -> {
                when (v.inDecrement) {
                    InDecrementType.PrefixIncrement -> {
                        print("++")
                        pe(v.variable)
                    }
                    InDecrementType.PrefixDecrement -> {
                        print("--")
                        pe(v.variable)
                    }
                    InDecrementType.SuffixIncrement -> {
                        pe(v.variable)
                        print("++")
                    }
                    InDecrementType.SuffixDecrement -> {
                        pe(v.variable)
                        print("--")
                    }
                }
            }
            is StaticFieldWithSelf -> {
                pe(v.self)
                print('.')
                print(v.name)

                if (showDetailed) {
                    print(" /* static ")
                    print(v.owner)
                    print('.')
                    print(v.name)
                    print(':')
                    print(v.desc)
                    print("*/")
                }
            }
            is InvokeStaticWithSelfValue -> {
                pe(v.self)
                print('.')
                print(v.name)
                print('(')
                for (arg in v.args) {
                    pe(arg)
                    print(", ")
                }
                print(')')

                if (showDetailed) {
                    print(" /* static ")
                    print(v.owner)
                    print('.')
                    print(v.name)
                    print(':')
                    print(v.desc)
                    print("*/")
                }
            }
            is StringContacting -> {
                for ((i, element) in v.elements.withIndex()) {
                    if (i != 0)
                        print(" + ")
                    pe(element)
                }
            }
            is NullChecked -> {
                print("NullChecked(")
                pe(v.value)
                print(')')
            }
            else -> error("${v.javaClass}")
        }
    }
}
