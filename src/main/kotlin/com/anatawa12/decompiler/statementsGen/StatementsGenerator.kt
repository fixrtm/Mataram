package com.anatawa12.decompiler.statementsGen

import com.anatawa12.decompiler.instructions.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.Type

class StatementsGenerator(val coreSignature: MethodCoreSignature) : InstructionVisitor {
    private val locals = mutableListOf<LocalVariable?>()

    private fun local(variable: Int) = locals[variable]?.identifier ?: error("local not found: #$variable")
    private fun local(variable: Int, local: LocalVariableIdentifier?) {
        while (locals.lastIndex < variable) locals.add(null)
        locals[variable] = local?.let(::LocalVariable)
    }

    private val start = BlockBeginStatement()
    private val end = BlockEndStatement()
    private var currentLineNumber: Int = -1

    init {
        start.next = end
        end.prev = start
    }

    private operator fun Statement.unaryPlus() {
        this.labelsTargetsMe = nextLabels.toPersistentList()
        nextLabels.clear()
        this.coreSignature = this@StatementsGenerator.coreSignature
        setLineNumber(currentLineNumber)
        end.insertPrev(this)
    }

    private val localVariables = mutableListOf<LocalVariableInfo>()

    fun getStatementsMethod() = StatementsMethod(
        beginStatement = start,
        endStatement = end,
        localVariables = localVariables.toImmutableList(),
        signature = coreSignature,
    )

    private var wasGoto = true

    private val stack = mutableListOf<StackVariable>()
    private fun push(vararg variable: StackVariable) {
        for (stackVariable in variable) {
            stack.add(StackVariable(stackVariable.identifier))
        }
    }

    override fun localVariable(
        name: String,
        descriptor: String,
        signature: String?,
        start: Label,
        end: Label,
        index: Int
    ) {
        localVariables += LocalVariableInfo(name, descriptor, signature, getStatLabel(start), getStatLabel(end), index)
    }

    private val startTryCatches = mutableMapOf<StatLabel, MutableSet<TryCatchBlockIdentifier>>()
    private val endTryCatches = mutableMapOf<StatLabel, MutableSet<TryCatchBlockIdentifier>>()
    private val handlerTryCatches = mutableMapOf<StatLabel, MutableSet<TryCatchBlockIdentifier>>()
    override fun tryCatchBlock(start: Label, end: Label, handler: Label, catchesInternalName: String?) {
        val startStat = getStatLabel(start)
        val endStat = getStatLabel(end)
        val handlerStat = getStatLabel(handler)

        val identifier = TryCatchBlockIdentifier(
            catchesInternalName = catchesInternalName,
            tryStartLabel = startStat,
            tryEndLabel = endStat,
            catchStartLabel = handlerStat,
        )

        startTryCatches.computeIfAbsent(startStat) { mutableSetOf() }.add(identifier)
        endTryCatches.computeIfAbsent(endStat) { mutableSetOf() }.add(identifier)
        handlerTryCatches.computeIfAbsent(handlerStat) { mutableSetOf() }.add(identifier)
    }

    /// instructions

    @OptIn(ExperimentalStdlibApi::class)
    private fun pops(count: Int) = List(count) { stack.removeLast().identifier }

    private fun check(stackType: StackType, vararg variables: StackVariableIdentifier) =
        check(stackType, variables.toList())

    private fun check(stackType: StackType, variables: List<StackVariableIdentifier>) {
        for (variable in variables) {
            check(variable.stackType == stackType)
        }
    }

    override fun nop() {
        preInsn()
    }

    override fun const(value: InsnConstantValue) {
        preInsn()
        val s = StackVariable(value.stackType)
        val vConst = when (value) {
            InsnConstantNull -> VConstantNull
            is InsnConstantString -> VConstantString(value.string)
            is InsnConstantInt -> VConstantInt(value.int)
            is InsnConstantLong -> VConstantLong(value.long)
            is InsnConstantFloat -> VConstantFloat(value.float)
            is InsnConstantDouble -> VConstantDouble(value.double)
            is InsnConstantType -> VConstantType(value.type)
            is InsnConstantMethodType -> VConstantMethodType(value.type)
            is InsnConstantHandle -> VConstantHandle(value.handle)
            is InsnConstantConstantDynamic -> VConstantConstantDynamic(value.dynamic)
        }
        +Assign(s, ConstantValue(vConst)).stat()
        push(s)
    }

    override fun load(variable: Int, type: StackType) {
        preInsn()
        val s = StackVariable(type)
        +Assign(s, LocalVariable(local(variable))).stat()
        push(s)
    }

    override fun aload(type: AllType) {
        preInsn()
        val (idx, ary) = pops(2)
        val s = StackVariable(type.stackType)
        +Assign(s, ArrayVariable(ary.value(), idx.value(), type)).stat()
        push(s)
    }

    override fun store(variable: Int, type: StackType) {
        preInsn()
        val l = LocalVariable(variable, type)
        local(variable, l.identifier)
        val (p) = pops(1)
        check(type, p)
        +Assign(l, p.value()).stat()
    }

    override fun astore(type: AllType) {
        preInsn()
        val (value, idx, ary) = pops(3)
        +Assign(ArrayVariable(ary.value(), idx.value(), type), value.value()).stat()
    }

    override fun pop() {
        preInsn()
        pops(1)
    }

    override fun pop2() {
        preInsn()
        pops(2)
    }

    override fun dup() {
        preInsn()
        val (p1) = pops(1)
        val s1 = StackVariable.cloneBy(p1)
        val s2 = StackVariable.cloneBy(p1)
        +Assign(s1, p1.value()).stat()
        +Assign(s2, p1.value()).stat()
        push(s1, s2)
    }

    override fun dup2() {
        preInsn()
        val (p1, p2) = pops(2)
        val s1 = StackVariable.cloneBy(p2)
        val s2 = StackVariable.cloneBy(p1)
        val s3 = StackVariable.cloneBy(p2)
        val s4 = StackVariable.cloneBy(p1)
        +Assign(s1, p2.value()).stat()
        +Assign(s2, p1.value()).stat()
        +Assign(s3, p2.value()).stat()
        +Assign(s4, p1.value()).stat()
        push(s1, s2, s3, s4)
    }

    override fun dupX1() {
        preInsn()
        val (p1, x1) = pops(2)
        val s1 = StackVariable.cloneBy(p1)
        val s2 = StackVariable.cloneBy(x1)
        val s3 = StackVariable.cloneBy(p1)
        +Assign(s1, p1.value()).stat()
        +Assign(s2, x1.value()).stat()
        +Assign(s3, p1.value()).stat()
        push(s1, s2, s3)
    }

    override fun dupX2() {
        preInsn()
        val (p1, x1, x2) = pops(3)
        val s1 = StackVariable.cloneBy(p1)
        val s2 = StackVariable.cloneBy(x2)
        val s3 = StackVariable.cloneBy(x1)
        val s4 = StackVariable.cloneBy(p1)
        +Assign(s1, p1.value()).stat()
        +Assign(s2, x2.value()).stat()
        +Assign(s3, x1.value()).stat()
        +Assign(s4, p1.value()).stat()
        push(s1, s2, s3, s4)
    }

    override fun dup2X1() {
        preInsn()
        val (p1, p2, x1) = pops(3)
        val s1 = StackVariable.cloneBy(p2)
        val s2 = StackVariable.cloneBy(p1)
        val s3 = StackVariable.cloneBy(x1)
        val s4 = StackVariable.cloneBy(p2)
        val s5 = StackVariable.cloneBy(p1)
        +Assign(s1, p2.value()).stat()
        +Assign(s2, p1.value()).stat()
        +Assign(s3, x1.value()).stat()
        +Assign(s4, p2.value()).stat()
        +Assign(s5, p1.value()).stat()
        push(s1, s2, s3, s4, s5)
    }

    override fun dup2X2() {
        preInsn()
        val (p1, p2, x1, x2) = pops(4)
        val s1 = StackVariable.cloneBy(p2)
        val s2 = StackVariable.cloneBy(p1)
        val s3 = StackVariable.cloneBy(x2)
        val s4 = StackVariable.cloneBy(x1)
        val s5 = StackVariable.cloneBy(p2)
        val s6 = StackVariable.cloneBy(p1)
        +Assign(s1, p2.value()).stat()
        +Assign(s2, p1.value()).stat()
        +Assign(s3, x2.value()).stat()
        +Assign(s4, x1.value()).stat()
        +Assign(s5, p2.value()).stat()
        +Assign(s6, p1.value()).stat()
        push(s1, s2, s3, s4, s5, s6)
    }

    override fun swap() {
        preInsn()
        val (p1, p2) = pops(2)
        val s1 = StackVariable.cloneBy(p2)
        val s2 = StackVariable.cloneBy(p1)
        +Assign(s1, p1.value()).stat()
        +Assign(s2, p2.value()).stat()
        push(s1, s2)
    }

    override fun biOp(op: BiOp, type: NumericType) {
        preInsn()
        val (right, left) = pops(2)
        check(type.stackType, right, left)
        val s1 = StackVariable(type.stackType)
        +Assign(s1, BiOperation(op, left.value(), right.value())).stat()
        push(s1)
    }

    override fun shiftOp(op: ShiftOp, type: NumericType) {
        preInsn()
        val (shift, value) = pops(2)
        check(StackType.Integer, shift)
        check(type.stackType, value)
        val s1 = StackVariable(type.stackType)
        +Assign(s1, ShiftOperation(op, value.value(), shift.value())).stat()
        push(s1)
    }

    override fun neg(type: NumericType) {
        preInsn()
        val (value) = pops(1)
        check(type.stackType, value)
        val s1 = StackVariable(type.stackType)
        +Assign(s1, NegativeOperation(value.value())).stat()
        push(s1)
    }

    override fun iinc(variable: Int, increment: Int) {
        preInsn()
        val l = LocalVariable(variable, StackType.Integer)
        check(StackType.Integer == LocalVariable(local(variable)).stackType)
        local(variable, l.identifier)
        +BiOperationAssignedValue(
            BiOp.Add,
            LocalVariable(local(variable)),
            ConstantValue(VConstantInt(increment))
        ).stat()
    }

    override fun cast(cast: InsnConvert) {
        preInsn()
        val (p1) = pops(1)
        val s1 = StackVariable(cast.to.stackType)
        check(cast.from.stackType, p1)
        +Assign(s1, CastValue(cast.to, p1.value())).stat()
        push(s1)
    }

    override fun lcmp() {
        preInsn()
        val (right, left) = pops(2)
        check(StackType.Long, left, right)
        val s1 = StackVariable(StackType.Integer)
        +Assign(s1, LongCompare(left.value(), right.value())).stat()
        push(s1)
    }

    override fun cmpl(type: NumericType) {
        preInsn()
        val (right, left) = pops(2)
        check(type.stackType, left, right)
        val s1 = StackVariable(StackType.Integer)
        +Assign(s1, FloatingCompareLesser(left.value(), right.value())).stat()
        push(s1)
    }

    override fun cmpg(type: NumericType) {
        preInsn()
        val (right, left) = pops(2)
        check(type.stackType, left, right)
        val s1 = StackVariable(StackType.Integer)
        +Assign(s1, FloatingCompareGreater(left.value(), right.value())).stat()
        push(s1)
    }

    override fun conditionalGoto(insnCondition: InsnCondition, label: Label) {
        preInsn()
        val (p1) = pops(1)
        check(insnCondition.input, p1)
        val conditionValue = when (insnCondition) {
            InsnCondition.EQ -> ConditionValue(BiCondition.EQ, p1.value(), ConstantValue(VConstantInt(0)))
            InsnCondition.NE -> ConditionValue(BiCondition.NE, p1.value(), ConstantValue(VConstantInt(0)))
            InsnCondition.LT -> ConditionValue(BiCondition.LT, p1.value(), ConstantValue(VConstantInt(0)))
            InsnCondition.GE -> ConditionValue(BiCondition.GE, p1.value(), ConstantValue(VConstantInt(0)))
            InsnCondition.GT -> ConditionValue(BiCondition.GT, p1.value(), ConstantValue(VConstantInt(0)))
            InsnCondition.LE -> ConditionValue(BiCondition.LE, p1.value(), ConstantValue(VConstantInt(0)))
            InsnCondition.Null -> ConditionValue(BiCondition.EQ, p1.value(), ConstantValue(VConstantNull))
            InsnCondition.NonNull -> ConditionValue(BiCondition.NE, p1.value(), ConstantValue(VConstantNull))
        }
        conditionValue.lineNumber = currentLineNumber
        conditionValue.left.lineNumber = currentLineNumber
        conditionValue.right.lineNumber = currentLineNumber
        +ConditionalGoto(conditionValue, getStatLabel(label))

        applyStackInfo(getStatLabel(label))
    }

    override fun biConditionalGoto(conditionInsn: InsnBiCondition, label: Label) {
        preInsn()
        val (right, left) = pops(2)
        check(conditionInsn.input, right, left)
        val condition = when (conditionInsn) {
            InsnBiCondition.ICmpEQ -> BiCondition.EQ
            InsnBiCondition.ICmpNE -> BiCondition.NE
            InsnBiCondition.ICmpLT -> BiCondition.LT
            InsnBiCondition.ICmpGE -> BiCondition.GE
            InsnBiCondition.ICmpGT -> BiCondition.GT
            InsnBiCondition.ICmpLE -> BiCondition.LE
            InsnBiCondition.ACmpEQ -> BiCondition.EQ
            InsnBiCondition.ACmpNE -> BiCondition.NE
        }
        val conditionValue = ConditionValue(condition, left.value(), right.value())
        conditionValue.lineNumber = currentLineNumber
        conditionValue.left.lineNumber = currentLineNumber
        conditionValue.right.lineNumber = currentLineNumber
        +ConditionalGoto(conditionValue, getStatLabel(label))
        applyStackInfo(getStatLabel(label))
    }

    override fun goTo(label: Label) {
        preInsn()
        +Goto(getStatLabel(label))
        wasGoto = true

        applyStackInfo(getStatLabel(label))
    }

    override fun jsr(label: Label) {
        preInsn()
        +Jsr(getStatLabel(label))
        wasGoto = true

        applyStackInfo(getStatLabel(label))
    }

    override fun ret(variable: Int) {
        preInsn()
        +Ret(LocalVariable(local(variable)))
        wasGoto = true
    }

    override fun tableswitch(min: Int, max: Int, default: Label, labels: List<Label>) {
        preInsn()
        val (value) = pops(1)
        check(StackType.Integer, value)
        +TableSwitch(min, max, getStatLabel(default), labels.map(::getStatLabel), value.value())

        applyStackInfo(getStatLabel(default))
        labels.forEach { applyStackInfo(getStatLabel(it)) }
    }

    override fun lookupswitch(default: Label, pairs: List<Pair<Int, Label>>) {
        preInsn()
        val (value) = pops(1)
        check(StackType.Integer, value)
        +LookupSwitch(getStatLabel(default), pairs.map { (a, b) -> a to getStatLabel(b) }, value.value())

        applyStackInfo(getStatLabel(default))
        pairs.forEach { applyStackInfo(getStatLabel(it.second)) }
    }

    override fun areturn(type: StackType?) {
        preInsn()
        if (type == null) {
            +ReturnVoid()
        } else {
            val (p1) = pops(1)
            check(type, p1)
            +ReturnValue(p1.value())
        }
        wasGoto = true
    }

    override fun getstatic(owner: String, name: String, descriptor: String) {
        preInsn()
        val s1 = StackVariable(StackType.byDesc(descriptor))
        +Assign(s1, StaticField(owner, name, descriptor)).stat()
        push(s1)
    }

    override fun putstatic(owner: String, name: String, descriptor: String) {
        preInsn()
        val (p1) = pops(1)
        check(StackType.byDesc(descriptor), p1)
        +Assign(StaticField(owner, name, descriptor), p1.value()).stat()
    }

    override fun getfield(owner: String, name: String, descriptor: String) {
        preInsn()
        val (self) = pops(1)
        check(StackType.Object, self)
        val s1 = StackVariable(StackType.byDesc(descriptor))
        +Assign(s1, InstanceField(owner, name, descriptor, self.value())).stat()
        push(s1)
    }

    override fun putfield(owner: String, name: String, descriptor: String) {
        preInsn()
        val (p1, self) = pops(2)
        check(StackType.byDesc(descriptor), p1)
        check(StackType.Object, self)
        +Assign(InstanceField(owner, name, descriptor, self.value()), p1.value()).stat()
    }

    override fun invokevirtual(owner: String, name: String, descriptor: String, isInterface: Boolean) {
        preInsn()
        val args = pops(Type.getArgumentTypes(descriptor).size).asReversed()
        val (self) = pops(1)
        check(StackType.Object, self)
        if (descriptor.last() == 'V') {
            +InvokeVirtualVoid(
                owner,
                name,
                descriptor,
                isInterface,
                self.value(),
                args.map(StackVariableIdentifier::value)
            )
        } else {
            val s1 = StackVariable(Type.getReturnType(descriptor))
            +Assign(
                s1,
                InvokeVirtualValue(
                    owner,
                    name,
                    descriptor,
                    isInterface,
                    self.value(),
                    args.map(StackVariableIdentifier::value)
                )
            ).stat()
            push(s1)
        }
    }

    override fun invokespecial(owner: String, name: String, descriptor: String, isInterface: Boolean) {
        preInsn()
        val args = pops(Type.getArgumentTypes(descriptor).size).asReversed()
        val (self) = pops(1)
        check(StackType.Object, self)
        if (descriptor.last() == 'V') {
            +InvokeSpecialVoid(
                owner,
                name,
                descriptor,
                isInterface,
                self.value(),
                args.map(StackVariableIdentifier::value)
            )
        } else {
            val s1 = StackVariable(Type.getReturnType(descriptor))
            +Assign(
                s1,
                InvokeSpecialValue(
                    owner,
                    name,
                    descriptor,
                    isInterface,
                    self.value(),
                    args.map(StackVariableIdentifier::value)
                )
            ).stat()
            push(s1)
        }
    }

    override fun invokestatic(owner: String, name: String, descriptor: String, isInterface: Boolean) {
        preInsn()
        val args = pops(Type.getArgumentTypes(descriptor).size).asReversed()
        if (descriptor.last() == 'V') {
            +InvokeStaticVoid(owner, name, descriptor, isInterface, args.map(StackVariableIdentifier::value))
        } else {
            val s1 = StackVariable(Type.getReturnType(descriptor))
            +Assign(
                s1,
                InvokeStaticValue(owner, name, descriptor, isInterface, args.map(StackVariableIdentifier::value))
            ).stat()
            push(s1)
        }
    }

    override fun invokeinterface(owner: String, name: String, descriptor: String) {
        preInsn()
        val args = pops(Type.getArgumentTypes(descriptor).size).asReversed()
        val (self) = pops(1)
        check(StackType.Object, self)
        if (descriptor.last() == 'V') {
            +InvokeInterfaceVoid(owner, name, descriptor, self.value(), args.map(StackVariableIdentifier::value))
        } else {
            val s1 = StackVariable(Type.getReturnType(descriptor))
            +Assign(
                s1,
                InvokeInterfaceValue(owner, name, descriptor, self.value(), args.map(StackVariableIdentifier::value))
            ).stat()
            push(s1)
        }
    }

    override fun invokedynamic(
        name: String,
        descriptor: String,
        bootstrapMethodHandle: Handle,
        bootstrapMethodArguments: List<Any>,
    ) {
        preInsn()
        val args = pops(Type.getArgumentTypes(descriptor).size).asReversed()
        if (descriptor.last() == 'V') {
            +InvokeDynamicVoid(
                name,
                descriptor,
                bootstrapMethodHandle,
                bootstrapMethodArguments,
                args.map(StackVariableIdentifier::value)
            )
        } else {
            val s1 = StackVariable(Type.getReturnType(descriptor))
            +Assign(
                s1,
                InvokeDynamicValue(
                    name,
                    descriptor,
                    bootstrapMethodHandle,
                    bootstrapMethodArguments,
                    args.map(StackVariableIdentifier::value)
                )
            ).stat()
            push(s1)
        }
    }

    override fun anew(type: Type) {
        preInsn()
        val s1 = StackVariable(type)
        +Assign(s1, NewObject(type)).stat()
        push(s1)
    }

    override fun newarray(type: Type) {
        preInsn()
        val (size) = pops(1)
        val s1 = StackVariable(Type.getType("[${type}"))
        +Assign(s1, NewArray(type, size.value())).stat()
        push(s1)
    }

    override fun arraylength() {
        preInsn()
        val (value) = pops(1)
        check(StackType.Object, value)
        val s1 = StackVariable(StackType.Integer)
        +Assign(s1, ArrayLength(value.value())).stat()
        push(s1)
    }

    override fun athrow() {
        preInsn()
        val (value) = pops(1)
        check(StackType.Object, value)
        +ThrowException(value.value())
        wasGoto = true
    }

    override fun checkcast(type: Type) {
        preInsn()
        val (value) = pops(1)
        check(StackType.Object, value)
        val s1 = StackVariable(StackType.Object)
        +Assign(s1, CheckCast(value.value(), type)).stat()
        push(s1)
    }

    override fun instanceOf(type: Type) {
        preInsn()
        val (value) = pops(1)
        check(StackType.Object, value)
        val s1 = StackVariable(StackType.Integer)
        +Assign(s1, InstanceOf(value.value(), type)).stat()
        push(s1)
    }

    override fun monitorenter() {
        preInsn()
        val (value) = pops(1)
        check(StackType.Object, value)
        +MonitorEnter(value.value())
    }

    override fun monitorexit() {
        preInsn()
        val (value) = pops(1)
        check(StackType.Object, value)
        +MonitorExit(value.value())
    }

    override fun multianewarray(descriptor: String, numDimensions: Int) {
        preInsn()
        val values = pops(numDimensions).asReversed()
        check(StackType.Integer, values)

        val s1 = StackVariable(Type.getType(descriptor))
        +Assign(s1, MultiANewArray(Type.getType(descriptor), values.map(StackVariableIdentifier::value))).stat()
        push(s1)
    }

    private val nextLabels = mutableListOf<StatLabel>()

    override fun mark(label: Label) {
        nextLabels += getStatLabel(label)
    }

    override fun markLine(line: Int) {
        currentLineNumber = line
    }

    override fun frame(locals: List<FrameElement>, stacks: List<FrameElement>) {
        if (wasGoto) {
            this.locals.clear()
            this.stack.clear()
            var index = 0
            for (local in locals) {
                when (local) {
                    FrameElement.Top -> local(index, null)
                    FrameElement.Integer -> local(index, LocalVariableIdentifier(index, StackType.Integer))
                    FrameElement.Float -> local(index, LocalVariableIdentifier(index, StackType.Float))
                    FrameElement.Long -> local(index, LocalVariableIdentifier(index, StackType.Long))
                    FrameElement.Double -> local(index, LocalVariableIdentifier(index, StackType.Double))
                    FrameElement.Null -> local(index, LocalVariableIdentifier(index, StackType.Object))
                    FrameElement.UninitializedThis -> local(index, LocalVariableIdentifier(index, StackType.Object))
                    is FrameElement.RefType -> local(
                        index,
                        LocalVariableIdentifier(index, Type.getObjectType(local.internalName))
                    )
                    is FrameElement.Uninitialized -> local(index, LocalVariableIdentifier(index, StackType.Object))
                }
                if (local == FrameElement.Long || local == FrameElement.Double)
                    index++
                index++
            }
            for (stacksElement in stacks) {
                when (stacksElement) {
                    FrameElement.Top -> error("")
                    FrameElement.Integer -> push(StackVariable(StackType.Integer))
                    FrameElement.Float -> push(StackVariable(StackType.Float))
                    FrameElement.Long -> push(StackVariable(StackType.Long))
                    FrameElement.Double -> push(StackVariable(StackType.Double))
                    FrameElement.Null -> push(StackVariable(StackType.Object))
                    FrameElement.UninitializedThis -> push(StackVariable(StackType.Object))
                    is FrameElement.RefType -> push(StackVariable(StackType.Object))
                    is FrameElement.Uninitialized -> push(StackVariable(StackType.Object))
                }
            }
        } else {
            kotlin.run {
                var index = 0
                for (local in locals) {
                    when (local) {
                        FrameElement.Top -> verifyLocal(index, null)
                        FrameElement.Integer -> verifyLocal(index, LocalVariable(index, StackType.Integer))
                        FrameElement.Float -> verifyLocal(index, LocalVariable(index, StackType.Float))
                        FrameElement.Long -> verifyLocal(index, LocalVariable(index, StackType.Long))
                        FrameElement.Double -> verifyLocal(index, LocalVariable(index, StackType.Double))
                        FrameElement.Null -> verifyLocal(index, LocalVariable(index, StackType.Object))
                        FrameElement.UninitializedThis -> verifyLocal(index, LocalVariable(index, StackType.Object))
                        is FrameElement.RefType -> verifyLocal(
                            index,
                            LocalVariable(index, Type.getObjectType(local.internalName))
                        )
                        is FrameElement.Uninitialized -> verifyLocal(index, LocalVariable(index, StackType.Object))
                    }
                    if (local == FrameElement.Long || local == FrameElement.Double)
                        index++
                    index++
                }
                for (i in index..this.locals.lastIndex) {
                    @OptIn(ExperimentalStdlibApi::class)
                    this.locals.removeLast()
                }
            }
            stacks.forEachIndexed { index, stackElement ->
                when (stackElement) {
                    FrameElement.Top -> verifyStack(index, null)
                    FrameElement.Integer -> verifyStack(index, StackType.Integer)
                    FrameElement.Float -> verifyStack(index, StackType.Float)
                    FrameElement.Long -> verifyStack(index, StackType.Long)
                    FrameElement.Double -> verifyStack(index, StackType.Double)
                    FrameElement.Null -> verifyStack(index, StackType.Object)
                    FrameElement.UninitializedThis -> verifyStack(index, StackType.Object)
                    is FrameElement.RefType -> verifyStack(index, StackType.Object)
                    is FrameElement.Uninitialized -> verifyStack(index, StackType.Object)
                }
            }
            for (i in stacks.size..this.stack.lastIndex) {
                @OptIn(ExperimentalStdlibApi::class)
                this.stack.removeLast()
            }
        }
    }

    override fun endInstructions() {
        +BlockEndStatement()
    }

    private fun verifyLocal(index: Int, localVariable: LocalVariable?) {
        if (localVariable == null) {
            locals[index] = null
            return
        }
        val real = LocalVariable(local(index))
        if (localVariable.type != null) {
            real.identifier.type = localVariable.type
        }
        check(real.stackType == localVariable.stackType)
    }

    private fun verifyStack(index: Int, stackType: StackType?) {
        val real = stack[index]
        check(real.stackType == stackType)
    }

    private val stackInfos = mutableMapOf<StatLabel, List<StackInfo>>()

    private fun preInsn() {
        for (nextLabel in nextLabels) {
            applyStackInfo(nextLabel, true)
            endTryCatches[nextLabel]?.forEach {
                end.insertPrev(TryBlockEnd(it))
            }
            startTryCatches[nextLabel]?.forEach {
                applyStackInfo(it.catchStartLabel, onlyLocals = true)
                end.insertPrev(TryBlockStart(it))
            }
            handlerTryCatches[nextLabel]?.forEach {
                end.insertPrev(CatchBlockStart(it, stack.last().identifier.value()))
            }
        }
        wasGoto = false
    }

    private fun applyStackInfo(label: StatLabel, isDefinition: Boolean = false, onlyLocals: Boolean = false) {
        val definitionInfo = StackInfo(
            end.prev,
            locals.toImmutableList(),
            stack.toImmutableList(),
            isDefinition,
            onlyLocals
        )

        val old = stackInfos[label]
        if (isDefinition) {
            if (old != null) {
                check(old.all { !it.isDefinition })
                old.forEach(definitionInfo::applyTo)
                stackInfos[label] = listOf(definitionInfo)
            }
        } else {
            if (old == null) {
                stackInfos[label] = listOf(definitionInfo)
            } else {
                if (old.size == 1 && old.single().isDefinition) {
                    old.single().applyTo(definitionInfo)
                    return
                }
                stackInfos[label] = old + definitionInfo
            }
        }
    }

    private val statLabels = mutableMapOf<Label, StatLabel>()
    private fun getStatLabel(l: Label): StatLabel = statLabels.computeIfAbsent(l, ::StatLabel)

    private class StackInfo(
        val insnAfter: Statement,
        val locals: ImmutableList<LocalVariable?>,
        val stack: ImmutableList<StackVariable>,
        val isDefinition: Boolean,
        val onlyLocals: Boolean
    ) {
        init {
            if (isDefinition) {
                require(!onlyLocals)
            }
        }

        fun applyTo(definitionInfo: StackInfo) {
            check(isDefinition)
            check(!definitionInfo.isDefinition)
            //check(definitionInfo.locals.size == locals.size)
            //check(definitionInfo.stack.size == stack.size)
            for ((a, b) in locals.zip(definitionInfo.locals)) {
                if (a != null) {
                    require(b != null)
                    a.merge(b)
                }
            }
            if (definitionInfo.onlyLocals) return
            for ((a, b) in stack.zip(definitionInfo.stack)) {
                a.merge(b)
            }
        }
    }
}
