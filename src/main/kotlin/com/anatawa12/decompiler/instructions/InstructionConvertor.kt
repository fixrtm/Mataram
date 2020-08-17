package com.anatawa12.decompiler.instructions

import org.objectweb.asm.*
import org.objectweb.asm.commons.InstructionAdapter
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode

object InstructionConvertor {
    fun convertFromNode(
        classInternalName: String,
        node: MethodNode,
        visitor: InstructionVisitor,
    ) {
        val lineNumberTable = node.instructions.asSequence()
            .filterIsInstance<LineNumberNode>()
            .map { it.start.label to it.line }
            .toMap()

        for (instruction in node.instructions) {
            when (instruction) {
                is LocalVariableNode -> {
                    visitor.localVariable(
                        name = instruction.name,
                        descriptor = instruction.desc,
                        signature = instruction.signature,
                        start = instruction.start.label,
                        end = instruction.end.label,
                        index = instruction.index,
                    )
                }
                is TryCatchBlockNode -> {
                    visitor.tryCatchBlock(
                        start = instruction.start.label,
                        end = instruction.end.label,
                        handler = instruction.handler.label,
                        catchesInternalName = instruction.type,
                    )
                }
            }
        }

        val runner = InstructionConvertorRunner(
            classInternalName,
            node.access,
            node.name,
            node.desc,
            lineNumberTable,
            visitor,
        )

        node.accept(runner)
    }
}

@OptIn(ExperimentalStdlibApi::class)
private class InstructionConvertorRunner(
    classInternalName: String,
    access: Int,
    name: String,
    desc: String,
    val lineNumberTable: Map<Label, Int>,
    private val visitor: InstructionVisitor,
) : InstructionAdapter(Opcodes.ASM8, null) {

    private val stack = mutableListOf<JvmStackType>()

    private fun push(jvmStackType: JvmStackType) {
        stack.add(jvmStackType)
        if (jvmStackType == JvmStackType.Double)
            stack.add(JvmStackType.Double2)
        if (jvmStackType == JvmStackType.Long)
            stack.add(JvmStackType.Long2)
    }

    private fun push1(jvmStackType: JvmStackType) {
        stack.add(jvmStackType)
    }

    private fun pop1(jvmStackType: JvmStackType) {
        if (jvmStackType == JvmStackType.Double)
            pop1(JvmStackType.Double2)
        if (jvmStackType == JvmStackType.Long)
            pop1(JvmStackType.Long2)
        val last = stack.removeLast()
        check(jvmStackType == last) { "pop type and expected type is not same" }
    }

    private fun pop1(): JvmStackType = stack.removeLast()

    private fun checkTwoElements(e1: JvmStackType, e2: JvmStackType): Boolean {
        return if (e1.twoElement) {
            check(e1.other == e2) { "double or long pair is not valid" }
            true
        } else {
            false
        }
    }

    private fun numericType(type: Type) = when (type.sort) {
        Type.INT -> NumericType.Integer
        Type.FLOAT -> NumericType.Float
        Type.LONG -> NumericType.Long
        Type.DOUBLE -> NumericType.Double
        else -> error("invalid type for numeric bi operation")
    }

    private fun jvmStack(type: NumericType) = when (type) {
        NumericType.Integer -> JvmStackType.Integer
        NumericType.Long -> JvmStackType.Long
        NumericType.Float -> JvmStackType.Float
        NumericType.Double -> JvmStackType.Double
    }

    private fun jvmStack(type: StackType) = when (type) {
        StackType.Integer -> JvmStackType.Integer
        StackType.Long -> JvmStackType.Long
        StackType.Float -> JvmStackType.Float
        StackType.Double -> JvmStackType.Double
        StackType.Object -> JvmStackType.Object
    }

    private fun jvmStack(type: Type) = when (type.sort) {
        Type.BOOLEAN -> JvmStackType.Integer
        Type.CHAR -> JvmStackType.Integer
        Type.BYTE -> JvmStackType.Integer
        Type.SHORT -> JvmStackType.Integer
        Type.INT -> JvmStackType.Integer
        Type.FLOAT -> JvmStackType.Float
        Type.LONG -> JvmStackType.Long
        Type.DOUBLE -> JvmStackType.Double
        Type.ARRAY -> JvmStackType.Object
        Type.OBJECT -> JvmStackType.Object
        else -> error("invalid type for value type")
    }

    private fun stackType(type: Type) = StackType.byType(type)

    private fun allType(type: Type) = when (type.sort) {
        Type.BOOLEAN -> AllType.Boolean
        Type.CHAR -> AllType.Char
        Type.BYTE -> AllType.Byte
        Type.SHORT -> AllType.Short
        Type.INT -> AllType.Integer
        Type.FLOAT -> AllType.Float
        Type.LONG -> AllType.Long
        Type.DOUBLE -> AllType.Double
        Type.ARRAY -> AllType.Object
        Type.OBJECT -> AllType.Object
        else -> error("invalid type for value type")
    }

    override fun visitCode() {
        visitor.frame(
            prevLocals!!,
            emptyList(),
        )
    }

    override fun nop() = visitor.nop()

    override fun aconst(value: Any?) {
        push(JvmStackType.Object)
        when (value) {
            is String -> visitor.const(InsnConstantString(value))
            null -> visitor.const(InsnConstantNull)
            else -> error("")
        }
    }

    override fun iconst(value: Int) {
        push(JvmStackType.Integer)
        visitor.const(InsnConstantInt(value))
    }

    override fun lconst(value: Long) {
        push(JvmStackType.Long)
        visitor.const(InsnConstantLong(value))
    }

    override fun fconst(value: Float) {
        push(JvmStackType.Float)
        visitor.const(InsnConstantFloat(value))
    }

    override fun dconst(value: Double) {
        push(JvmStackType.Double)
        visitor.const(InsnConstantDouble(value))
    }

    override fun tconst(value: Type) {
        push(JvmStackType.Object)
        visitor.const(InsnConstantType(value))
    }

    override fun hconst(value: Handle) {
        push(JvmStackType.Object)
        visitor.const(InsnConstantHandle(value))
    }

    override fun cconst(value: ConstantDynamic) {
        push(JvmStackType.Object)
        visitor.const(InsnConstantConstantDynamic(value))
    }

    override fun load(`var`: Int, type: Type) {
        push(JvmStackType.byType(type))
        visitor.load(`var`, stackType(type))
    }

    override fun aload(type: Type) {
        pop1(JvmStackType.Integer)
        pop1(JvmStackType.Object)
        push(JvmStackType.byType(type))
        visitor.aload(allType(type))
    }

    override fun store(`var`: Int, type: Type) {
        pop1(JvmStackType.byType(type))
        visitor.store(`var`, stackType(type))
    }

    override fun astore(type: Type) {
        pop1(JvmStackType.byType(type))
        pop1(JvmStackType.Integer)
        pop1(JvmStackType.Object)
        visitor.astore(allType(type))
    }

    override fun pop() {
        check(!pop1().twoElement) { "can't pop long or double with pop instruction" }
        visitor.pop()
    }

    override fun pop2() {
        if (checkTwoElements(pop1(), pop1())) {
            visitor.pop()
        } else {
            visitor.pop2()
        }
    }

    override fun dup() {
        val e1 = pop1()
        check(!e1.twoElement) { "can't pop long or double with pop instruction" }
        push1(e1)
        push1(e1)
        visitor.dup()
    }

    override fun dup2() {
        val e1 = pop1()
        val e2 = pop1()
        push1(e2)
        push1(e1)
        push1(e2)
        push1(e1)
        if (checkTwoElements(e1, e2)) {
            check(e1.other == e2) { "double or long pair is not valid" }
            visitor.dup()
        } else {
            visitor.dup2()
        }
    }

    override fun dupX1() {
        val e1 = pop1()
        val x1 = pop1()
        check(!e1.twoElement) { "can't pop long or double with pop instruction" }
        check(!x1.twoElement) { "can't pop long or double with pop instruction" }
        visitor.dupX1()
        push1(e1)
        push1(x1)
        push1(e1)
    }

    override fun dupX2() {
        val e1 = pop1()
        val x1 = pop1()
        val x2 = pop1()
        check(!e1.twoElement) { "can't pop long or double with pop instruction" }
        push1(e1)
        push1(x2)
        push1(x1)
        push1(e1)
        if (checkTwoElements(x1, x2)) {
            visitor.dupX1()
        } else {
            visitor.dupX2()
        }
    }

    override fun dup2X1() {
        val e1 = pop1()
        val e2 = pop1()
        val x1 = pop1()
        check(!x1.twoElement) { "can't pop long or double with pop instruction" }
        push1(e2)
        push1(e1)
        push1(x1)
        push1(e2)
        push1(e1)
        if (checkTwoElements(e1, e2)) {
            visitor.dupX1()
        } else {
            visitor.dup2X1()
        }
    }

    override fun dup2X2() {
        val e1 = pop1()
        val e2 = pop1()
        val x1 = pop1()
        val x2 = pop1()
        push1(e2)
        push1(e1)
        push1(x2)
        push1(x1)
        push1(e2)
        push1(e1)
        if (checkTwoElements(e1, e2)) {
            if (checkTwoElements(x1, x2)) {
                visitor.dupX1()
            } else {
                visitor.dupX2()
            }
        } else {
            if (checkTwoElements(x1, x2)) {
                visitor.dup2X1()
            } else {
                visitor.dup2X2()
            }
        }
    }

    override fun swap() {
        val e1 = pop1()
        val x1 = pop1()
        check(!e1.twoElement) { "can't pop long or double with pop instruction" }
        check(!x1.twoElement) { "can't pop long or double with pop instruction" }
        push(e1)
        push(x1)
        visitor.swap()
    }

    private fun biOp(op: BiOp, type: Type) {
        val n = numericType(type)
        val s = jvmStack(n)
        pop1(s)
        pop1(s)
        push(s)
        visitor.biOp(op, n)
    }

    override fun add(type: Type) {
        biOp(BiOp.Add, type)
    }

    override fun sub(type: Type) {
        biOp(BiOp.Sub, type)
    }

    override fun mul(type: Type) {
        biOp(BiOp.Mul, type)
    }

    override fun div(type: Type) {
        biOp(BiOp.Div, type)
    }

    override fun rem(type: Type) {
        biOp(BiOp.Rem, type)
    }

    override fun neg(type: Type) {
        val t = numericType(type)
        val s = jvmStack(t)
        pop1(s)
        push(s)
        visitor.neg(t)
    }

    private fun shiftOp(op: ShiftOp, type: Type) {
        val n = numericType(type)
        val s = jvmStack(n)
        pop1(JvmStackType.Integer)
        pop1(s)
        push(s)
        visitor.shiftOp(op, n)
    }

    override fun shl(type: Type) {
        shiftOp(ShiftOp.Shl, type)
    }

    override fun shr(type: Type) {
        shiftOp(ShiftOp.Shr, type)
    }

    override fun ushr(type: Type) {
        shiftOp(ShiftOp.UShr, type)
    }

    override fun and(type: Type) {
        biOp(BiOp.And, type)
    }

    override fun or(type: Type) {
        biOp(BiOp.Or, type)
    }

    override fun xor(type: Type) {
        biOp(BiOp.Xor, type)
    }

    override fun iinc(`var`: Int, increment: Int) {
        visitor.iinc(`var`, increment)
    }

    override fun cast(from: Type, to: Type) {
        if (from !== to) {
            when (from) {
                Type.DOUBLE_TYPE -> when (to) {
                    Type.FLOAT_TYPE -> callConvert(InsnConvert.D2F)
                    Type.LONG_TYPE -> callConvert(InsnConvert.D2L)
                    else -> {
                        callConvert(InsnConvert.D2I)
                        cast(Type.INT_TYPE, to)
                    }
                }
                Type.FLOAT_TYPE -> when (to) {
                    Type.DOUBLE_TYPE -> callConvert(InsnConvert.F2D)
                    Type.LONG_TYPE -> callConvert(InsnConvert.F2L)
                    else -> {
                        callConvert(InsnConvert.F2I)
                        cast(Type.INT_TYPE, to)
                    }
                }
                Type.LONG_TYPE -> when (to) {
                    Type.DOUBLE_TYPE -> callConvert(InsnConvert.L2D)
                    Type.FLOAT_TYPE -> callConvert(InsnConvert.L2F)
                    else -> {
                        callConvert(InsnConvert.L2I)
                        cast(Type.INT_TYPE, to)
                    }
                }
                else -> when (to) {
                    Type.BYTE_TYPE -> callConvert(InsnConvert.I2B)
                    Type.CHAR_TYPE -> callConvert(InsnConvert.I2C)
                    Type.DOUBLE_TYPE -> callConvert(InsnConvert.I2D)
                    Type.FLOAT_TYPE -> callConvert(InsnConvert.I2F)
                    Type.LONG_TYPE -> callConvert(InsnConvert.I2L)
                    Type.SHORT_TYPE -> callConvert(InsnConvert.I2S)
                    else -> error("invalid cast")
                }
            }
        }
    }

    private fun callConvert(cast: InsnConvert) {
        pop1(jvmStack(cast.from))
        push(jvmStack(cast.to.stackType))
        visitor.cast(cast)
    }

    override fun lcmp() {
        visitor.lcmp()
        pop1(JvmStackType.Long)
        pop1(JvmStackType.Long)
        push(JvmStackType.Integer)
    }

    override fun cmpl(type: Type) {
        val n = numericType(type)
        val s = jvmStack(n)
        pop1(s)
        pop1(s)
        push(JvmStackType.Integer)
        visitor.cmpl(n)
    }

    override fun cmpg(type: Type) {
        val n = numericType(type)
        val s = jvmStack(n)
        pop1(s)
        pop1(s)
        push(JvmStackType.Integer)
        visitor.cmpg(n)
    }

    private fun ifXX(insnCondition: InsnCondition, label: Label) {
        pop1(jvmStack(insnCondition.input))
        visitor.conditionalGoto(insnCondition, label)
    }

    private fun ifBiOperation(conditionInsn: InsnBiCondition, label: Label) {
        pop1(jvmStack(conditionInsn.input))
        pop1(jvmStack(conditionInsn.input))
        visitor.biConditionalGoto(conditionInsn, label)
    }

    override fun ifeq(label: Label) {
        this.ifXX(InsnCondition.EQ, label)
    }

    override fun ifne(label: Label) {
        this.ifXX(InsnCondition.NE, label)
    }

    override fun iflt(label: Label) {
        this.ifXX(InsnCondition.LT, label)
    }

    override fun ifge(label: Label) {
        this.ifXX(InsnCondition.GE, label)
    }

    override fun ifgt(label: Label) {
        this.ifXX(InsnCondition.GT, label)
    }

    override fun ifle(label: Label) {
        this.ifXX(InsnCondition.LE, label)
    }

    override fun ificmpeq(label: Label) {
        ifBiOperation(InsnBiCondition.ICmpEQ, label)
    }

    override fun ificmpne(label: Label) {
        ifBiOperation(InsnBiCondition.ICmpNE, label)
    }

    override fun ificmplt(label: Label) {
        ifBiOperation(InsnBiCondition.ICmpLT, label)
    }

    override fun ificmpge(label: Label) {
        ifBiOperation(InsnBiCondition.ICmpGE, label)
    }

    override fun ificmpgt(label: Label) {
        ifBiOperation(InsnBiCondition.ICmpGT, label)
    }

    override fun ificmple(label: Label) {
        ifBiOperation(InsnBiCondition.ICmpLE, label)
    }

    override fun ifacmpeq(label: Label) {
        ifBiOperation(InsnBiCondition.ACmpEQ, label)
    }

    override fun ifacmpne(label: Label) {
        ifBiOperation(InsnBiCondition.ACmpNE, label)
    }

    override fun goTo(label: Label) {
        visitor.goTo(label)
    }

    override fun jsr(label: Label) {
        visitor.jsr(label)
    }

    override fun ret(variable: Int) {
        visitor.ret(variable)
    }

    override fun tableswitch(min: Int, max: Int, dflt: Label, vararg labels: Label) {
        visitor.tableswitch(min, max, dflt, labels.toList())
    }

    override fun lookupswitch(dflt: Label, keys: IntArray, labels: Array<out Label>) {
        visitor.lookupswitch(dflt, keys.zip(labels))
    }

    override fun areturn(type: Type) {
        if (type != Type.VOID_TYPE) {
            pop1(jvmStack(type))
            visitor.areturn(stackType(type))
        } else {
            visitor.areturn(null)
        }
    }

    override fun getstatic(owner: String, name: String, descriptor: String) {
        push(jvmStack(Type.getType(descriptor)))
        visitor.getstatic(owner, name, descriptor)
    }

    override fun putstatic(owner: String, name: String, descriptor: String) {
        pop1(jvmStack(Type.getType(descriptor)))
        visitor.putstatic(owner, name, descriptor)
    }

    override fun getfield(owner: String, name: String, descriptor: String) {
        pop1(JvmStackType.Object)
        push(jvmStack(Type.getType(descriptor)))
        visitor.getfield(owner, name, descriptor)
    }

    override fun putfield(owner: String, name: String, descriptor: String) {
        pop1(jvmStack(Type.getType(descriptor)))
        pop1(JvmStackType.Object)
        visitor.putfield(owner, name, descriptor)
    }

    fun invoke(descriptor: String, isInstanceCall: Boolean) {
        val t = Type.getType(descriptor)
        for (type in listOf(*t.argumentTypes).asReversed()) {
            pop1(jvmStack(type))
        }
        if (isInstanceCall) pop1(JvmStackType.Object)
        if (t.returnType != Type.VOID_TYPE)
            push(jvmStack(t.returnType))
    }

    override fun invokevirtual(owner: String, name: String, descriptor: String, isInterface: Boolean) {
        invoke(descriptor, true)
        visitor.invokevirtual(owner, name, descriptor, isInterface)
    }

    override fun invokespecial(owner: String, name: String, descriptor: String, isInterface: Boolean) {
        invoke(descriptor, true)
        visitor.invokespecial(owner, name, descriptor, isInterface)
    }

    override fun invokestatic(owner: String, name: String, descriptor: String, isInterface: Boolean) {
        invoke(descriptor, false)
        visitor.invokestatic(owner, name, descriptor, isInterface)
    }

    override fun invokeinterface(owner: String, name: String, descriptor: String) {
        invoke(descriptor, true)
        visitor.invokeinterface(owner, name, descriptor)
    }

    override fun invokedynamic(
        name: String,
        descriptor: String,
        bootstrapMethodHandle: Handle,
        bootstrapMethodArguments: Array<out Any>,
    ) {
        invoke(descriptor, false)
        visitor.invokedynamic(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments.toList())
    }

    override fun anew(type: Type) {
        push(JvmStackType.Object)
        visitor.anew(type)
    }

    override fun newarray(type: Type) {
        pop1(JvmStackType.Integer)
        push(JvmStackType.Object)
        visitor.newarray(type)
    }

    override fun arraylength() {
        pop1(JvmStackType.Object)
        push(JvmStackType.Integer)
        visitor.arraylength()
    }

    override fun athrow() {
        pop1(JvmStackType.Object)
        visitor.athrow()
    }

    override fun checkcast(type: Type) {
        pop1(JvmStackType.Object)
        push(JvmStackType.Object)
        visitor.checkcast(type)
    }

    override fun instanceOf(type: Type) {
        pop1(JvmStackType.Object)
        push(JvmStackType.Integer)
        visitor.instanceOf(type)
    }

    override fun monitorenter() {
        pop1(JvmStackType.Object)
        visitor.monitorenter()
    }

    override fun monitorexit() {
        pop1(JvmStackType.Object)
        visitor.monitorexit()
    }

    override fun multianewarray(descriptor: String, numDimensions: Int) {
        repeat(numDimensions) {
            pop1(JvmStackType.Integer)
        }
        push(JvmStackType.Object)
        visitor.multianewarray(descriptor, numDimensions)
    }

    override fun ifnull(label: Label) {
        ifXX(InsnCondition.Null, label)
    }

    override fun ifnonnull(label: Label) {
        ifXX(InsnCondition.NonNull, label)
    }

    override fun mark(label: Label) {
        val line = lineNumberTable[label]
        if (line != null) {
            visitor.markLine(line)
        }
        visitor.mark(label)
    }

    override fun visitLocalVariable(
        name: String,
        descriptor: String,
        signature: String?,
        start: Label,
        end: Label,
        index: Int
    ) {
        visitor.localVariable(name, descriptor, signature, start, end, index)
    }

    private var prevLocals: List<FrameElement>?

    init {
        when {
            (access and Opcodes.ACC_STATIC) != 0 -> {
                // static
                prevLocals = Type.getType(desc).argumentTypes.map(FrameElement.Companion::byType)
            }
            name == "<init>" -> {
                // constructor
                prevLocals = listOf(FrameElement.UninitializedThis)
                    .plus(Type.getType(desc).argumentTypes.map(FrameElement.Companion::byType))
            }
            else -> {
                // instance
                prevLocals = listOf(FrameElement.RefType(classInternalName))
                    .plus(Type.getType(desc).argumentTypes.map(FrameElement.Companion::byType))
            }
        }
    }

    override fun visitFrame(type: Int, numLocal: Int, local: Array<out Any>?, numStack: Int, stack: Array<out Any>?) {
        val locals: List<FrameElement>
        val stacks: List<FrameElement>
        when (type) {
            Opcodes.F_NEW -> {
                prevLocals = null
                locals = local!!.take(numLocal).map(::frameElement)
                stacks = stack!!.take(numStack).map(::frameElement)
            }
            Opcodes.F_SAME -> {
                locals = prevLocals!!
                stacks = emptyList()
            }
            Opcodes.F_SAME1 -> {
                locals = prevLocals!!
                stacks = listOf(frameElement(stack!![0]))
            }
            Opcodes.F_APPEND -> {
                locals = prevLocals!! + local!!.take(numLocal).map(::frameElement)
                stacks = emptyList()
            }
            Opcodes.F_CHOP -> {
                locals = prevLocals!!.dropLast(numLocal)
                stacks = emptyList()
            }
            Opcodes.F_FULL -> {
                locals = local!!.take(numLocal).map(::frameElement)
                stacks = stack!!.take(numStack).map(::frameElement)
            }
            else -> error("")
        }
        visitor.frame(locals, stacks)
        if (type != Opcodes.F_NEW) prevLocals = locals

        this.stack.clear()
        for (stacksElement in stacks) {
            when (stacksElement) {
                FrameElement.Top -> error("")
                FrameElement.Integer -> push(JvmStackType.Integer)
                FrameElement.Float -> push(JvmStackType.Float)
                FrameElement.Long -> push(JvmStackType.Long)
                FrameElement.Double -> push(JvmStackType.Double)
                FrameElement.Null -> push(JvmStackType.Object)
                FrameElement.UninitializedThis -> push(JvmStackType.Object)
                is FrameElement.RefType -> push(JvmStackType.Object)
                is FrameElement.Uninitialized -> push(JvmStackType.Object)
            }
        }
    }

    override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) {
        visitor.tryCatchBlock(start, end, handler, type)
    }

    override fun visitEnd() {
        visitor.endInstructions()
    }

    private fun frameElement(element: Any): FrameElement = when (element) {
        Opcodes.TOP -> FrameElement.Top
        Opcodes.INTEGER -> FrameElement.Integer
        Opcodes.FLOAT -> FrameElement.Float
        Opcodes.LONG -> FrameElement.Long
        Opcodes.DOUBLE -> FrameElement.Double
        Opcodes.NULL -> FrameElement.Null
        Opcodes.UNINITIALIZED_THIS -> FrameElement.UninitializedThis
        is String -> FrameElement.RefType(element)
        is Label -> FrameElement.Uninitialized(element)
        else -> error("invalid element")
    }

    private enum class JvmStackType(val twoElement: Boolean = false, val other: JvmStackType? = null) {
        Integer,
        Long(true),
        Long2(true, Long),
        Double(true),
        Double2(true, Double),
        Float,
        Object,

        ;

        companion object {
            fun byType(type: Type): JvmStackType = when (type.sort) {
                Type.ARRAY -> Object
                Type.BOOLEAN -> Integer
                Type.CHAR -> Integer
                Type.BYTE -> Integer
                Type.SHORT -> Integer
                Type.INT -> Integer
                Type.FLOAT -> Float
                Type.LONG -> Long
                Type.DOUBLE -> Double
                Type.OBJECT -> Object
                Type.VOID -> error("void is not valid for stack type")
                Type.METHOD -> error("method is not valid for stack type")
                else -> error("unknown sort of type")
            }
        }
    }
}
