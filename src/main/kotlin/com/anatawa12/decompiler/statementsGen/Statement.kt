package com.anatawa12.decompiler.statementsGen

import com.anatawa12.decompiler.util.Property
import com.anatawa12.decompiler.util.PropertyList
import com.anatawa12.decompiler.util.identitySetOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.objectweb.asm.Handle
import org.objectweb.asm.Type
import java.util.concurrent.atomic.AtomicInteger

sealed class Statement {
    var coreSignature: MethodCoreSignature? = null

    var lineNumber: Int = -1
        private set
    val consumes = mutableSetOf<ValueProperty<*, Statement>>()
    val produces = identitySetOf<Property<out Variable<*>, Statement>>()

    open val childBlocks = listOf<BlockBeginStatement>()

    private var labelsTargetsMeBack: PersistentList<StatLabel>? = null
    var labelsTargetsMe: PersistentList<StatLabel>
        get() = labelsTargetsMeBack ?: error("not initialized")
        set(value) {
            if (labelsTargetsMeBack != null)
                labelsTargetsMeBack!!.forEach { it.at = null }
            labelsTargetsMeBack = value
            labelsTargetsMeBack!!.forEach { it.at = this }
        }
    lateinit var prev: Statement
    lateinit var next: Statement

    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
    abstract override fun toString(): String

    protected open fun onDispose() {
        for (consume in consumes) {
            consume.value.unConsumeByStatement(consume)
        }
        labelsTargetsMeBack?.forEach { it.at = null }
    }

    inline fun <reified T : Value> prop(value: T, noinline expectedTypeGetter: () -> ExpectTypes) =
        prop(value, T::class.java, expectedTypeGetter)

    inline fun <reified T : Value> prop(value: T, expectedType: ExpectTypes) =
        prop(value, T::class.java, { expectedType })

    fun <T : Value> prop(value: T, type: Class<T>, expectedTypeGetter: () -> ExpectTypes) =
        ValueProperty(value, this, type, expectedTypeGetter).also { prop ->
            consumes += prop
            value.consumeByStatement(prop)
            prop.onChange = { from, to ->
                from.unConsumeByStatement(prop)
                to.consumeByStatement(prop)
            }
        }

    fun insertPrev(stat: Statement) {
        val prev = this.prev
        prev.next = stat
        stat.prev = prev

        this.prev = stat
        stat.next = this
    }

    fun removeMe() {
        val prev = this.prev
        val next = this.next
        prev.next = next
        next.prev = prev

        dispose(this)
    }

    operator fun get(index: Int): Statement {
        var cur = this
        repeat(index) { cur = cur.next }
        return cur
    }

    open fun setLineNumber(line: Int) {
        if (lineNumber != -2)
            lineNumber = line
    }

    companion object {
        fun dispose(vararg statements: Statement) = dispose(statements.toList())
        fun dispose(statements: List<Statement>) {
            val first = statements.first()
            val last = statements.first()
            check(first.prev.next !== first)
            check(last.prev.next !== last)
            for ((prev, next) in statements.toList().zipWithNext()) {
                check(prev.next == next)
                check(next.prev == prev)
            }
            for (statement in statements) {
                statement.onDispose()
            }
        }
    }
}

inline fun <V, reified T> V.mutatingProp(value: T, consumes: Boolean)
        where V : IProducer, V : Statement, T : Variable<in V> = mutatingProp(value, consumes, T::class.java)

fun <V, T> V.mutatingProp(value: T, consumes: Boolean, type: Class<T>)
        where V : IProducer, V : Statement, T : Variable<in V> =
    ValueProperty(value, this, type, { ExpectTypes.Unknown }).also { prop ->
        if (consumes) this.consumes += prop
        produces += prop
        if (consumes) value.consumeByStatement(prop)
        value.addProducer(this)
        prop.onChange = { from, to ->
            if (consumes) from.unConsumeByStatement(prop)
            from.removeProducer(this)
            if (consumes) to.consumeByStatement(prop)
            to.addProducer(this)
        }
    }

class BlockBeginStatement() : Statement(), Iterable<Statement> {
    init {
        labelsTargetsMe = persistentListOf()
        super.setLineNumber(-2)
    }

    fun makeList(): ImmutableList<Statement> = toImmutableList()

    override fun iterator() = object : Iterator<Statement> {
        var current: Statement = this@BlockBeginStatement
        override fun hasNext(): Boolean = current !is BlockEndStatement

        override fun next(): Statement {
            if (current is BlockEndStatement)
                throw NoSuchElementException()
            current = current.next
            return current
        }
    }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun toString(): String = "MethodBeginStatement"

    companion object {
        fun makeBlockByBeginEnd(begin: Statement, end: Statement): Pair<BlockBeginStatement, BlockEndStatement> {
            val beginStat = BlockBeginStatement()
            val endStat = BlockEndStatement()
            begin.prev.next = end.next
            end.next.prev = begin.prev

            begin.prev = beginStat
            beginStat.next = begin

            endStat.prev = end
            end.next = endStat

            return beginStat to endStat
        }
    }
}

class BlockEndStatement() : Statement() {
    init {
        labelsTargetsMe = persistentListOf()
        super.setLineNumber(-2)
    }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun toString(): String = "MethodEndStatement"
}

class StatementExpressionStatement(expression: StatementExpressionValue) : Statement() {
    var expression by prop(expression, ExpectTypes.Unknown)

    init {
        super.setLineNumber(-2)
    }

    override fun setLineNumber(line: Int) {
        expression.setLineNumber(line)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StatementExpressionStatement

        if (expression != other.expression) return false

        return true
    }

    override fun hashCode(): Int = expression.hashCode()

    override fun toString(): String = "StatementExpressionStatement($expression)"

    override fun onDispose() {
        super.onDispose()
        expression.dispose()
    }
}

fun Statement.exp(): StatementExpressionValue? = (this as? StatementExpressionStatement)?.expression

fun StatementExpressionValue.stat() = StatementExpressionStatement(this)

/**
 * jump if true
 */
class ConditionalGoto(value: Value, val label: StatLabel) : Statement(), IStatLabelConsumer {
    var value by prop(value, ExpectTypes.Boolean)

    init {
        label.usedBy += this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConditionalGoto

        if (value != other.value) return false
        if (label != other.label) return false

        return true
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + label.hashCode()
        return result
    }

    override fun toString(): String {
        return "ConditionalGoto1(value=$value, label=$label)"
    }

    override fun onDispose() {
        super.onDispose()
        label.usedBy -= this
    }
}

data class Goto(val label: StatLabel) : Statement(), IStatLabelConsumer {
    init {
        label.usedBy += this
    }

    override fun onDispose() {
        super.onDispose()
        label.usedBy -= this
    }
}

data class Jsr(val label: StatLabel) : Statement(), IStatLabelConsumer {
    init {
        label.usedBy += this
    }

    override fun onDispose() {
        super.onDispose()
        label.usedBy -= this
    }
}

class Ret(val variable: LocalVariable) : Statement() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Ret

        if (variable != other.variable) return false

        return true
    }

    override fun hashCode(): Int {
        return variable.hashCode()
    }

    override fun toString(): String {
        return "Ret(variable=$variable)"
    }
}

class TableSwitch(val min: Int, val max: Int, val default: StatLabel, val labels: List<StatLabel>, value: Value) :
    Statement(), IStatLabelConsumer {
    var value by prop(value, ExpectTypes.AnyInteger)

    init {
        default.usedBy += this
        labels.forEach { it.usedBy += this }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TableSwitch

        if (min != other.min) return false
        if (max != other.max) return false
        if (default != other.default) return false
        if (labels != other.labels) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = min
        result = 31 * result + max
        result = 31 * result + default.hashCode()
        result = 31 * result + labels.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

    override fun toString() = "TableSwitch(min=$min, max=$max, default=$default, labels=$labels, value=$value)"
}

class LookupSwitch(val default: StatLabel, val pairs: List<Pair<Int, StatLabel>>, value: Value) : Statement(),
    IStatLabelConsumer {
    var value by prop(value, ExpectTypes.AnyInteger)

    init {
        default.usedBy += this
        pairs.forEach { it.second.usedBy += this }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LookupSwitch

        if (default != other.default) return false
        if (pairs != other.pairs) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = default.hashCode()
        result = 31 * result + pairs.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

    override fun toString() = "LookupSwitch(default=$default, pairs=$pairs, value=$value)"
}

// switch
class ReturnValue(value: Value) : Statement() {
    var variable by prop(value) {
        coreSignature?.let { ExpectTypes.by(Type.getReturnType(it.desc)) } ?: ExpectTypes.Unknown
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReturnValue

        if (variable != other.variable) return false

        return true
    }

    override fun hashCode(): Int {
        return variable.hashCode()
    }

    override fun toString() = "ReturnValue(variable=$variable)"
}

class ReturnVoid() : Statement() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    override fun toString() = "ReturnVoid()"
}

class InvokeVirtualVoid(
    val owner: String,
    val name: String,
    val desc: String,
    val isInterface: Boolean,
    self: Value,
    args: List<Value>
) : Statement() {
    var self by prop(self, ExpectTypes.Object)
    val argProps = args.zip(Type.getArgumentTypes(desc)).map { (v, t) -> prop(v, ExpectTypes.by(t)) }
    var args = PropertyList(this, argProps)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvokeVirtualVoid

        if (owner != other.owner) return false
        if (name != other.name) return false
        if (desc != other.desc) return false
        if (isInterface != other.isInterface) return false
        if (self != other.self) return false
        if (args != other.args) return false

        return true
    }

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + desc.hashCode()
        result = 31 * result + isInterface.hashCode()
        result = 31 * result + self.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }

    override fun toString(): String {
        return "InvokeVirtualVoid(owner='$owner', name='$name', desc='$desc', isInterface=$isInterface, self=$self, args=$args)"
    }
}

class InvokeSpecialVoid(
    val owner: String,
    val name: String,
    val desc: String,
    val isInterface: Boolean,
    self: Value,
    args: List<Value>
) : Statement() {
    var self by prop(self, ExpectTypes.Object)
    val argProps = args.zip(Type.getArgumentTypes(desc)).map { (v, t) -> prop(v, ExpectTypes.by(t)) }
    var args = PropertyList(this, argProps)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvokeSpecialVoid

        if (owner != other.owner) return false
        if (name != other.name) return false
        if (desc != other.desc) return false
        if (isInterface != other.isInterface) return false
        if (self != other.self) return false
        if (args != other.args) return false

        return true
    }

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + desc.hashCode()
        result = 31 * result + isInterface.hashCode()
        result = 31 * result + self.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }

    override fun toString(): String {
        return "InvokeSpecialVoid(owner='$owner', name='$name', desc='$desc', isInterface=$isInterface, self=$self, args=$args)"
    }
}

class InvokeStaticVoid(
    val owner: String,
    val name: String,
    val desc: String,
    val isInterface: Boolean,
    args: List<Value>
) : Statement() {
    val argProps = args.zip(Type.getArgumentTypes(desc)).map { (v, t) -> prop(v, ExpectTypes.by(t)) }
    var args = PropertyList(this, argProps)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvokeStaticVoid

        if (owner != other.owner) return false
        if (name != other.name) return false
        if (desc != other.desc) return false
        if (isInterface != other.isInterface) return false
        if (args != other.args) return false

        return true
    }

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + desc.hashCode()
        result = 31 * result + isInterface.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }

    override fun toString(): String {
        return "InvokeStaticVoid(owner='$owner', name='$name', desc='$desc', isInterface=$isInterface, args=$args)"
    }
}

class InvokeInterfaceVoid(val owner: String, val name: String, val desc: String, self: Value, args: List<Value>) :
    Statement() {
    var self by prop(self, ExpectTypes.Object)
    val argProps = args.zip(Type.getArgumentTypes(desc)).map { (v, t) -> prop(v, ExpectTypes.by(t)) }
    var args = PropertyList(this, argProps)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvokeInterfaceVoid

        if (owner != other.owner) return false
        if (name != other.name) return false
        if (desc != other.desc) return false
        if (self != other.self) return false
        if (args != other.args) return false

        return true
    }

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + desc.hashCode()
        result = 31 * result + self.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }

    override fun toString(): String {
        return "InvokeInterfaceVoid(owner='$owner', name='$name', desc='$desc', self=$self, args=$args)"
    }
}

class InvokeDynamicVoid(
    val name: String,
    val descriptor: String,
    val bootstrapMethodHandle: Handle,
    val bootstrapMethodArguments: List<Any>,
    args: List<Value>,
) : Statement() {
    val argProps = args.zip(Type.getArgumentTypes(descriptor)).map { (v, t) -> prop(v, ExpectTypes.by(t)) }
    var args = PropertyList(this, argProps)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvokeDynamicVoid

        if (name != other.name) return false
        if (descriptor != other.descriptor) return false
        if (bootstrapMethodHandle != other.bootstrapMethodHandle) return false
        if (bootstrapMethodArguments != other.bootstrapMethodArguments) return false
        if (args != other.args) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + descriptor.hashCode()
        result = 31 * result + bootstrapMethodHandle.hashCode()
        result = 31 * result + bootstrapMethodArguments.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }

    override fun toString(): String {
        return "InvokeDynamicVoid(name='$name', descriptor='$descriptor', bootstrapMethodHandle=$bootstrapMethodHandle, bootstrapMethodArguments=$bootstrapMethodArguments, args=$args)"
    }
}

class ThrowException(throws: Value) : Statement() {
    var throws by prop(throws, ExpectTypes.Object)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ThrowException

        if (throws != other.throws) return false

        return true
    }

    override fun hashCode(): Int {
        return throws.hashCode()
    }

    override fun toString(): String {
        return "ThrowException(throws=$throws)"
    }
}

class MonitorEnter(monitorObj: Value) : Statement() {
    var monitorObj by prop(monitorObj, ExpectTypes.Object)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MonitorEnter

        if (monitorObj != other.monitorObj) return false

        return true
    }

    override fun hashCode(): Int {
        return monitorObj.hashCode()
    }

    override fun toString(): String {
        return "MonitorEnter(monitorObj=$monitorObj)"
    }
}

class MonitorExit(monitorObj: Value) : Statement() {
    var monitorObj by prop(monitorObj, ExpectTypes.Object)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MonitorExit

        if (monitorObj != other.monitorObj) return false

        return true
    }

    override fun hashCode(): Int {
        return monitorObj.hashCode()
    }

    override fun toString(): String {
        return "MonitorExit(monitorObj=$monitorObj)"
    }
}

/// try-catch

class TryCatchBlockIdentifier(
    val catchesInternalName: String?,
    val tryStartLabel: StatLabel,
    val tryEndLabel: StatLabel,
    val catchStartLabel: StatLabel,
) {
    var tryStart: TryBlockStart? = null
        set(value) {
            check(field == null)
            check(value != null)
            field = value
        }
    var tryEnd: TryBlockEnd? = null
        set(value) {
            check(field == null)
            check(value != null)
            field = value
        }
    var catchStart: CatchBlockStart? = null
        set(value) {
            check(field == null)
            check(value != null)
            field = value
        }

    override fun toString(): String {
        return "TryCatchBlockIdentifier($id)"
    }

    val id = nextId.getAndIncrement().toString().padStart(5, '0')

    companion object {
        private val nextId = AtomicInteger(0)
    }
}

class TryBlockStart(val identifier: TryCatchBlockIdentifier) : Statement() {
    init {
        super.setLineNumber(-2)
        identifier.tryStart = this
        labelsTargetsMe = persistentListOf()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TryBlockStart

        if (identifier != other.identifier) return false

        return true
    }

    override fun hashCode(): Int {
        return identifier.hashCode()
    }

    override fun toString(): String {
        return "TryBlockStart(identifier=$identifier)"
    }

}

class TryBlockEnd(val identifier: TryCatchBlockIdentifier) : Statement() {
    init {
        super.setLineNumber(-2)
        identifier.tryEnd = this
        labelsTargetsMe = persistentListOf()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TryBlockStart

        if (identifier != other.identifier) return false

        return true
    }

    override fun hashCode(): Int {
        return identifier.hashCode()
    }

    override fun toString(): String {
        return "TryBlockEnd(identifier=$identifier)"
    }
}

class CatchBlockStart(
    val identifier: TryCatchBlockIdentifier,
    catchVariable: IdentifierVariable<*, *, in CatchBlockStart>,
) : Statement(), IStackProducer {
    var catchVariable by mutatingProp(catchVariable, consumes = false)

    init {
        super.setLineNumber(-2)
        identifier.catchStart = this
        labelsTargetsMe = persistentListOf()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TryBlockStart

        if (identifier != other.identifier) return false

        return true
    }

    override fun hashCode(): Int {
        return identifier.hashCode()
    }

    override fun toString(): String {
        return "CatchBlockStart(catchValue=$catchVariable, identifier=$identifier)"
    }

    override fun onDispose() {
        super.onDispose()
        catchVariable.removeProducer(this)
    }
}

/////////// java expression 

class InvokeStaticWithSelfVoid(
    val owner: String,
    val name: String,
    val desc: String,
    val isInterface: Boolean,
    self: Value,
    args: List<Value>
) : Statement() {
    var self by prop(self, ExpectTypes.Object)
    val argProps = args.zip(Type.getArgumentTypes(desc)).map { (v, t) -> prop(v, ExpectTypes.by(t)) }
    var args = PropertyList(this, argProps)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvokeStaticWithSelfVoid

        if (owner != other.owner) return false
        if (name != other.name) return false
        if (desc != other.desc) return false
        if (isInterface != other.isInterface) return false
        if (self != other.self) return false
        if (args != other.args) return false

        return true
    }

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + desc.hashCode()
        result = 31 * result + isInterface.hashCode()
        result = 31 * result + self.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }

    override fun toString(): String {
        return "InvokeStaticWithSelfVoid(owner='$owner', name='$name', desc='$desc', isInterface=$isInterface, self=$self, args=$args)"
    }
}

/////////// java control flow

sealed class JavaControlFlowStatement : Statement() {
    abstract override val childBlocks: List<BlockBeginStatement>
}

class IfElseControlFlow(
    condition: Value,
    val thenBlock: BlockBeginStatement,
    val elseBlock: BlockBeginStatement,
    val breakLabel: StatLabel,
) : JavaControlFlowStatement() {
    var condition by prop(condition, ExpectTypes.Boolean)
    override val childBlocks: List<BlockBeginStatement> get() = listOf(thenBlock, elseBlock)

    init {
        super.setLineNumber(-2)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IfElseControlFlow

        if (condition != other.condition) return false
        if (thenBlock != other.thenBlock) return false
        if (elseBlock != other.elseBlock) return false
        if (breakLabel != other.breakLabel) return false

        return true
    }

    override fun hashCode(): Int {
        var result = condition.hashCode()
        result = 31 * result + thenBlock.hashCode()
        result = 31 * result + elseBlock.hashCode()
        result = 31 * result + breakLabel.hashCode()
        return result
    }

    override fun toString(): String {
        return "IfElseControlFlow(condition=$condition)"
    }
}

class WhileControlFlow(
    condition: Value,
    val block: BlockBeginStatement,
    val continueLabel: StatLabel,
    val breakLabel: StatLabel,
) : JavaControlFlowStatement() {
    var condition by prop(condition, ExpectTypes.Boolean)
    override val childBlocks: List<BlockBeginStatement> get() = listOf(block)

    init {
        super.setLineNumber(-2)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WhileControlFlow

        if (condition != other.condition) return false
        if (block != other.block) return false
        if (continueLabel != other.continueLabel) return false
        if (breakLabel != other.breakLabel) return false

        return true
    }

    override fun hashCode(): Int {
        var result = condition.hashCode()
        result = 31 * result + block.hashCode()
        result = 31 * result + continueLabel.hashCode()
        result = 31 * result + breakLabel.hashCode()
        return result
    }

    override fun toString(): String {
        return "WhileControlFlow(condition=$condition)"
    }
}
