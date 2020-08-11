package com.anatawa12.decompiler.statementsGen

import com.anatawa12.decompiler.instructions.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.Type

class StatementsGenerator : InstructionVisitor {
    private val locals = mutableListOf<LocalVariable?>()

    private fun local(variable: Int): LocalVariable = locals[variable] ?: error("local not found: #$variable")
    private fun local(variable: Int, local: LocalVariable?) {
        while (locals.lastIndex < variable) locals.add(null)
        locals[variable] = local
    }

    private val start = MethodBeginStatement()
    private val end = MethodEndStatement()

    init {
        start.next = end
        end.prev = start
    }

    private operator fun Statement.unaryPlus() {
        if (nextLabels.any { it.id == "12957" }) {
            print("")
        }
        this.labelsTargetsMe = nextLabels.toPersistentList()
        nextLabels.clear()
        end.insertPrev(this)
    }

    private val localVariables = mutableListOf<LocalVariableInfo>()

    fun getStatementsMethod() = StatementsMethod(
            beginStatement = start,
            endStatement = end,
            localVariables = localVariables.toImmutableList(),
    )

    private var wasGoto = true

    private val stack = mutableListOf<StackVariable>()
    private fun push(vararg variable: StackVariable) {
        for (stackVariable in variable) {
            stack.add(stackVariable)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun pops(count: Int): List<StackVariable> = List(count) { stack.removeLast() }

    private fun check(stackType: StackType, vararg variables: StackVariable) = check(stackType, variables.toList())

    private fun check(stackType: StackType, variables: List<StackVariable>) {
        for (variable in variables) {
            check(variable.stackType == stackType)
        }
    }

    override fun nop() {
        preInsn()
    }

    override fun const(value: Any?) {
        preInsn()
        val s = StackVariable(StackType.byValue(value))
        +Assign(s, ConstantValue(value)).stat()
        push(s)
    }

    override fun load(variable: Int, type: StackType) {
        preInsn()
        val s = StackVariable(type)
        +Assign(s, local(variable)).stat()
        push(s)
    }

    override fun aload(type: AllType) {
        preInsn()
        val (idx, ary) = pops(2)
        val s = StackVariable(type.stackType)
        +Assign(s, ArrayVariable(ary, idx, type)).stat()
        push(s)
    }

    override fun store(variable: Int, type: StackType) {
        preInsn()
        val l = LocalVariable(variable, type)
        local(variable, l)
        val (p) = pops(1)
        check(type, p)
        +Assign(l, p).stat()
    }

    override fun astore(type: AllType) {
        preInsn()
        val (value, idx, ary) = pops(3)
        +Assign(ArrayVariable(ary, idx, type), value).stat()
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
        val s1 = StackVariable(p1)
        val s2 = StackVariable(p1)
        +Assign(s1, p1).stat()
        +Assign(s2, p1).stat()
        push(s1, s2)
    }

    override fun dup2() {
        preInsn()
        val (p1, p2) = pops(2)
        val s1 = StackVariable(p2)
        val s2 = StackVariable(p1)
        val s3 = StackVariable(p2)
        val s4 = StackVariable(p1)
        +Assign(s1, p2).stat()
        +Assign(s2, p1).stat()
        +Assign(s3, p2).stat()
        +Assign(s4, p1).stat()
        push(s1, s2, s3, s4)
    }

    override fun dupX1() {
        preInsn()
        val (p1, x1) = pops(2)
        val s1 = StackVariable(p1)
        val s2 = StackVariable(x1)
        val s3 = StackVariable(p1)
        +Assign(s1, p1).stat()
        +Assign(s2, x1).stat()
        +Assign(s3, p1).stat()
        push(s1, s2, s3)
    }

    override fun dupX2() {
        preInsn()
        val (p1, x1, x2) = pops(3)
        val s1 = StackVariable(p1)
        val s2 = StackVariable(x2)
        val s3 = StackVariable(x1)
        val s4 = StackVariable(p1)
        +Assign(s1, p1).stat()
        +Assign(s2, x2).stat()
        +Assign(s3, x1).stat()
        +Assign(s4, p1).stat()
        push(s1, s2, s3, s4)
    }

    override fun dup2X1() {
        preInsn()
        val (p1, p2, x1) = pops(3)
        val s1 = StackVariable(p2)
        val s2 = StackVariable(p1)
        val s3 = StackVariable(x1)
        val s4 = StackVariable(p2)
        val s5 = StackVariable(p1)
        +Assign(s1, p2).stat()
        +Assign(s2, p1).stat()
        +Assign(s3, x1).stat()
        +Assign(s4, p2).stat()
        +Assign(s5, p1).stat()
        push(s1, s2, s3, s4, s5)
    }

    override fun dup2X2() {
        preInsn()
        val (p1, p2, x1, x2) = pops(4)
        val s1 = StackVariable(p2)
        val s2 = StackVariable(p1)
        val s3 = StackVariable(x2)
        val s4 = StackVariable(x1)
        val s5 = StackVariable(p2)
        val s6 = StackVariable(p1)
        +Assign(s1, p2).stat()
        +Assign(s2, p1).stat()
        +Assign(s3, x2).stat()
        +Assign(s4, x1).stat()
        +Assign(s5, p2).stat()
        +Assign(s6, p1).stat()
        push(s1, s2, s3, s4, s5, s6)
    }

    override fun swap() {
        preInsn()
        val (p1, p2) = pops(2)
        val s1 = StackVariable(p2)
        val s2 = StackVariable(p1)
        +Assign(s1, p1).stat()
        +Assign(s2, p2).stat()
        push(s1, s2)
    }

    override fun biOp(op: BiOp, type: NumericType) {
        preInsn()
        val (right, left) = pops(2)
        check(type.stackType, right, left)
        val s1 = StackVariable(type.stackType)
        +Assign(s1, BiOperation(op, left, right)).stat()
        push(s1)
    }

    override fun shiftOp(op: ShiftOp, type: NumericType) {
        preInsn()
        val (shift, value) = pops(2)
        check(StackType.Integer, shift)
        check(type.stackType, value)
        val s1 = StackVariable(type.stackType)
        +Assign(s1, ShiftOperation(op, value, shift)).stat()
        push(s1)
    }

    override fun neg(type: NumericType) {
        preInsn()
        val (value) = pops(1)
        check(type.stackType, value)
        val s1 = StackVariable(type.stackType)
        +Assign(s1, NegativeOperation(value)).stat()
        push(s1)
    }

    override fun iinc(variable: Int, increment: Int) {
        preInsn()
        val l = LocalVariable(variable, StackType.Integer)
        check(StackType.Integer == local(variable).stackType)
        local(variable, l)
        +Assign(l, BiOperation(BiOp.Add, local(variable), ConstantValue(increment))).stat()
    }

    override fun cast(cast: InsnConvert) {
        preInsn()
        val (p1) = pops(1)
        val s1 = StackVariable(cast.to.stackType)
        check(cast.from.stackType, p1)
        +Assign(s1, CastValue(cast, p1)).stat()
        push(s1)
    }

    override fun lcmp() {
        preInsn()
        val (right, left) = pops(2)
        check(StackType.Long, left, right)
        val s1 = StackVariable(StackType.Integer)
        +Assign(s1, LongCompare(left, right)).stat()
        push(s1)
    }

    override fun cmpl(type: NumericType) {
        preInsn()
        val (right, left) = pops(2)
        check(type.stackType, left, right)
        val s1 = StackVariable(StackType.Integer)
        +Assign(s1, FloatingCompareLesser(left, right)).stat()
        push(s1)
    }

    override fun cmpg(type: NumericType) {
        preInsn()
        val (right, left) = pops(2)
        check(type.stackType, left, right)
        val s1 = StackVariable(StackType.Integer)
        +Assign(s1, FloatingCompareGreater(left, right)).stat()
        push(s1)
    }

    override fun conditionalGoto(insnCondition: InsnCondition, label: Label) {
        preInsn()
        val (p1) = pops(1)
        check(insnCondition.input, p1)
        val conditionValue = when (insnCondition) {
            InsnCondition.EQ -> ConditionValue(BiCondition.EQ, p1, ConstantValue(0))
            InsnCondition.NE -> ConditionValue(BiCondition.NE, p1, ConstantValue(0))
            InsnCondition.LT -> ConditionValue(BiCondition.LT, p1, ConstantValue(0))
            InsnCondition.GE -> ConditionValue(BiCondition.GE, p1, ConstantValue(0))
            InsnCondition.GT -> ConditionValue(BiCondition.GT, p1, ConstantValue(0))
            InsnCondition.LE -> ConditionValue(BiCondition.LE, p1, ConstantValue(0))
            InsnCondition.Null -> ConditionValue(BiCondition.EQ, p1, ConstantValue(null))
            InsnCondition.NonNull -> ConditionValue(BiCondition.NE, p1, ConstantValue(null))
        }
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
        +ConditionalGoto(ConditionValue(condition, left, right), getStatLabel(label))
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
        +Ret(local(variable))
        wasGoto = true
    }

    override fun tableswitch(min: Int, max: Int, default: Label, labels: List<Label>) {
        preInsn()
        val (value) = pops(1)
        check(StackType.Integer, value)
        +TableSwitch(min, max, getStatLabel(default), labels.map(::getStatLabel), value)

        applyStackInfo(getStatLabel(default))
        labels.forEach { applyStackInfo(getStatLabel(it)) }
    }

    override fun lookupswitch(default: Label, pairs: List<Pair<Int, Label>>) {
        preInsn()
        val (value) = pops(1)
        check(StackType.Integer, value)
        +LookupSwitch(getStatLabel(default), pairs.map { (a, b) -> a to getStatLabel(b) }, value)

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
            +ReturnValue(p1)
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
        +Assign(StaticField(owner, name, descriptor), p1).stat()
    }

    override fun getfield(owner: String, name: String, descriptor: String) {
        preInsn()
        val (self) = pops(1)
        check(StackType.Object, self)
        val s1 = StackVariable(StackType.byDesc(descriptor))
        +Assign(s1, InstanceField(owner, name, descriptor, self)).stat()
        push(s1)
    }

    override fun putfield(owner: String, name: String, descriptor: String) {
        preInsn()
        val (p1, self) = pops(2)
        check(StackType.byDesc(descriptor), p1)
        check(StackType.Object, self)
        +Assign(InstanceField(owner, name, descriptor, self), p1).stat()
    }

    override fun invokevirtual(owner: String, name: String, descriptor: String, isInterface: Boolean) {
        preInsn()
        val args = pops(Type.getArgumentTypes(descriptor).size).asReversed()
        val (self) = pops(1)
        check(StackType.Object, self)
        if (descriptor.last() == 'V') {
            +InvokeVirtualVoid(owner, name, descriptor, isInterface, self, args)
        } else {
            val s1 = StackVariable(Type.getReturnType(descriptor))
            +Assign(s1, InvokeVirtualValue(owner, name, descriptor, isInterface, self, args)).stat()
            push(s1)
        }
    }

    override fun invokespecial(owner: String, name: String, descriptor: String, isInterface: Boolean) {
        preInsn()
        val args = pops(Type.getArgumentTypes(descriptor).size).asReversed()
        val (self) = pops(1)
        check(StackType.Object, self)
        if (descriptor.last() == 'V') {
            +InvokeSpecialVoid(owner, name, descriptor, isInterface, self, args)
        } else {
            val s1 = StackVariable(Type.getReturnType(descriptor))
            +Assign(s1, InvokeSpecialValue(owner, name, descriptor, isInterface, self, args)).stat()
            push(s1)
        }
    }

    override fun invokestatic(owner: String, name: String, descriptor: String, isInterface: Boolean) {
        preInsn()
        val args = pops(Type.getArgumentTypes(descriptor).size).asReversed()
        if (descriptor.last() == 'V') {
            +InvokeStaticVoid(owner, name, descriptor, isInterface, args)
        } else {
            val s1 = StackVariable(Type.getReturnType(descriptor))
            +Assign(s1, InvokeStaticValue(owner, name, descriptor, isInterface, args)).stat()
            push(s1)
        }
    }

    override fun invokeinterface(owner: String, name: String, descriptor: String) {
        preInsn()
        val args = pops(Type.getArgumentTypes(descriptor).size).asReversed()
        val (self) = pops(1)
        check(StackType.Object, self)
        if (descriptor.last() == 'V') {
            +InvokeInterfaceVoid(owner, name, descriptor, self, args)
        } else {
            val s1 = StackVariable(Type.getReturnType(descriptor))
            +Assign(s1, InvokeInterfaceValue(owner, name, descriptor, self, args)).stat()
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
            +InvokeDynamicVoid(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments, args)
        } else {
            val s1 = StackVariable(Type.getReturnType(descriptor))
            +Assign(s1, InvokeDynamicValue(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments, args)).stat()
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
        +Assign(s1, NewArray(type, size)).stat()
        push(s1)
    }

    override fun arraylength() {
        preInsn()
        val (value) = pops(1)
        check(StackType.Object, value)
        val s1 = StackVariable(StackType.Integer)
        +Assign(s1, ArrayLength(value)).stat()
        push(s1)
    }

    override fun athrow() {
        preInsn()
        val (value) = pops(1)
        check(StackType.Object, value)
        +ThrowException(value)
        wasGoto = true
    }

    override fun checkcast(type: Type) {
        preInsn()
        val (value) = pops(1)
        check(StackType.Object, value)
        val s1 = StackVariable(StackType.Object)
        +Assign(s1, CheckCast(value, type)).stat()
        push(s1)
    }

    override fun instanceOf(type: Type) {
        preInsn()
        val (value) = pops(1)
        check(StackType.Object, value)
        val s1 = StackVariable(StackType.Integer)
        +Assign(s1, InstanceOf(value, type)).stat()
        push(s1)
    }

    override fun monitorenter() {
        preInsn()
        val (value) = pops(1)
        check(StackType.Object, value)
        +MonitorEnter(value)
    }

    override fun monitorexit() {
        preInsn()
        val (value) = pops(1)
        check(StackType.Object, value)
        +MonitorExit(value)
    }

    override fun multianewarray(descriptor: String, numDimensions: Int) {
        preInsn()
        val values = pops(numDimensions).asReversed()
        check(StackType.Integer, values)

        val s1 = StackVariable(Type.getType(descriptor))
        +Assign(s1, MultiANewArray(Type.getType(descriptor), values)).stat()
        push(s1)
    }

    private val nextLabels = mutableListOf<StatLabel>()

    override fun mark(label: Label) {
        nextLabels += getStatLabel(label)
    }

    override fun frame(locals: List<FrameElement>, stacks: List<FrameElement>) {
        if (wasGoto) {
            this.locals.clear()
            this.stack.clear()
            var index = 0
            for (local in locals) {
                when (local) {
                    FrameElement.Top -> local(index, null)
                    FrameElement.Integer -> local(index, LocalVariable(index, StackType.Integer))
                    FrameElement.Float -> local(index, LocalVariable(index, StackType.Float))
                    FrameElement.Long -> local(index, LocalVariable(index, StackType.Long))
                    FrameElement.Double -> local(index, LocalVariable(index, StackType.Double))
                    FrameElement.Null -> local(index, LocalVariable(index, StackType.Object))
                    FrameElement.UninitializedThis -> local(index, LocalVariable(index, StackType.Object))
                    is FrameElement.RefType -> local(
                            index,
                            LocalVariable(index, Type.getObjectType(local.internalName))
                    )
                    is FrameElement.Uninitialized -> local(index, LocalVariable(index, StackType.Object))
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
        val startAt = startStat.at
        val endAt = endStat.at
        val handlerAt = handlerStat.at

        val identifier = TryCatchBlockIdentifier(catchesInternalName)

        if (startAt == null) startTryCatches.computeIfAbsent(startStat) { mutableSetOf() }.add(identifier)
        else startAt.insertPrev(TryBlockStart(identifier))

        if (endAt == null) endTryCatches.computeIfAbsent(endStat) { mutableSetOf() }.add(identifier)
        else endAt.insertPrev(TryBlockEnd(identifier))

        if (handlerAt == null) {
            handlerTryCatches.computeIfAbsent(handlerStat) { mutableSetOf() }.add(identifier)
        } else {
            val stackInfo = stackInfos[handlerStat]!!.single()
            check(stackInfo.isDefinition)
            handlerAt.insertPrev(CatchBlockStart(identifier, stackInfo.stack.last()))
        }
    }

    override fun endInstructions() {
        +MethodEndStatement()
    }

    private fun verifyLocal(index: Int, localVariable: LocalVariable?) {
        if (localVariable == null) {
            locals[index] = null
            return
        }
        val real = local(index)
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
                end.insertPrev(TryBlockStart(it))
            }
            handlerTryCatches[nextLabel]?.forEach {
                end.insertPrev(CatchBlockStart(it, stack.last()))
            }
        }
        wasGoto = false
    }

    private fun applyStackInfo(label: StatLabel, isDefinition: Boolean = false) {
        val definitionInfo = StackInfo(end.prev, locals.toImmutableList(), stack.toImmutableList(), isDefinition)
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
    ) {
        init {
            if (isDefinition) {
                print("")
            }
        }

        fun applyTo(definitionInfo: StackInfo) {
            check(isDefinition)
            check(!definitionInfo.isDefinition)
            //check(definitionInfo.locals.size == locals.size)
            //check(definitionInfo.stack.size == stack.size)
            for ((a, b) in locals.zip(definitionInfo.locals)) {
                if (a?.identifier?.id == "67841" || b?.identifier?.id == "67841") {
                    print("")
                }
                if (a != null) {
                    require(b != null)
                    a.merge(b)
                }
            }
            for ((a, b) in stack.zip(definitionInfo.stack)) {
                a.merge(b)
            }
        }
    }
}
