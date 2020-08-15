package com.anatawa12.decompiler.statementsGen

import com.anatawa12.decompiler.instructions.AllType
import com.anatawa12.decompiler.instructions.BiOp
import com.anatawa12.decompiler.instructions.ShiftOp
import com.anatawa12.decompiler.instructions.StackType
import com.anatawa12.decompiler.util.*
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Type
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodType

sealed class Value {
    var lineNumber: Int = -1
        @JvmName("setLineNumberImpl") internal set(value) {
            if (field != -2)
                field = value
        }
    val consumes = mutableSetOf<ValueProperty<*, Value>>()
    val produces = identitySetOf<Property<out Variable<*>, Value>>()

    abstract val type: Type?
    abstract val stackType: StackType

    abstract fun consumeByExpression(value: ValueProperty<*, Value>)
    abstract fun consumeByStatement(statement: ValueProperty<*, Statement>)
    abstract fun unConsumeByExpression(value: ValueProperty<*, Value>)
    abstract fun unConsumeByStatement(statement: ValueProperty<*, Statement>)

    inline fun <reified T : Value> prop(value: T, noinline expectedTypeGetter: () -> ExpectTypes) =
        prop(value, T::class.java, expectedTypeGetter)

    inline fun <reified T : Value> prop(value: T, expectedType: ExpectTypes) =
        prop(value, T::class.java, { expectedType })

    fun <T : Value> prop(value: T, type: Class<T>, expectedTypeGetter: () -> ExpectTypes) =
        ValueProperty(value, this, type, expectedTypeGetter).also { prop ->
            consumes += prop
            value.consumeByExpression(prop)
            prop.onChange = { from, to ->
                from.unConsumeByExpression(prop)
                to.consumeByExpression(prop)
            }
        }

    open fun dispose() {
        for (consume in consumes) {
            consume.value.unConsumeByExpression(consume)
        }
    }
}

class ValueProperty<T : Value, out A>(
    value: T,
    thisRef: A,
    type: Class<T>,
    private val expectedTypeGetter: () -> ExpectTypes
) :
    Property<T, A>(value, thisRef, type) {
    val expectedType get() = expectedTypeGetter()
}

enum class ExpectTypes {
    Boolean,
    Char,
    Byte,
    Short,
    AnyInteger,
    Long,
    Double,
    Float,
    Object,
    Unknown,

    ;

    companion object {
        fun by(t: Type): ExpectTypes = when (t.sort) {
            Type.BOOLEAN -> Boolean
            Type.CHAR -> Char
            Type.BYTE -> Byte
            Type.SHORT -> Short
            Type.INT -> AnyInteger
            Type.FLOAT -> Float
            Type.LONG -> Long
            Type.DOUBLE -> Double
            Type.ARRAY -> Object
            Type.OBJECT -> Object
            else -> error("unsupported type: $t")
        }
    }
}

interface IProducer
interface IStackProducer : IProducer

inline fun <V, reified T> V.mutatingProp(value: T, consumes: Boolean)
        where V : IProducer, V : Value, T : Variable<in V> = mutatingProp(value, consumes, T::class.java)

fun <V, T> V.mutatingProp(value: T, consumes: Boolean, type: Class<T>)
        where V : Value, V : IProducer, T : Variable<in V> =
    ValueProperty(value, this, type, { ExpectTypes.Unknown }).also { prop ->
        if (consumes) this.consumes += prop
        produces += prop
        if (consumes) value.consumeByExpression(prop)
        value.addProducer(this)
        prop.onChange = { from, to ->
            if (consumes) from.unConsumeByExpression(prop)
            from.removeProducer(this)
            if (consumes) to.consumeByExpression(prop)
            to.addProducer(this)
        }
    }


sealed class Variable<Producer : IProducer> : Value() {
    abstract val leastType: Class<Producer>
    abstract fun addProducer(producer: Producer)
    abstract fun removeProducer(producer: Producer)
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : IProducer> variableCastIn(variable: Variable<*>): Variable<in T>? =
    variableCastIn(variable, T::class.java)

@Suppress("UNCHECKED_CAST")
fun <T : IProducer> variableCastIn(variable: Variable<*>, type: Class<T>): Variable<in T>? {
    return if (variable.leastType.isAssignableFrom(type)) variable as Variable<in T>
    else null
}

sealed class IdentifierVariableIdentifier<Identifier, VariableImpl, Producer>
        where Identifier : IdentifierVariableIdentifier<Identifier, VariableImpl, Producer>,
              VariableImpl : IdentifierVariable<Identifier, VariableImpl, Producer>,
              Producer : IProducer {
    val producers = identitySetOf<Producer>()
    val consumerExpressions = mutableSetOf<ValueProperty<out Value, Value>>()
    val consumerStatements = mutableSetOf<ValueProperty<out Value, Statement>>()
    val consumers get() = consumerExpressions + consumerStatements

    val id = (nextId++).toString().padStart(5, '0')
    val usedBy = identitySetOf<VariableImpl>()

    abstract var type: Type?
    abstract val stackType: StackType

    val consumerCount get() = (consumerExpressions.size + consumerStatements.size)
    val producerCount get() = producers.size

    val isSingleConsumeSingleProduce get() = consumerCount == 1 && producerCount == 1

    companion object {
        private var nextId = 1
    }
}

sealed class IdentifierVariable<Identifier, VariableImpl, Producer>(identifier: Identifier) : Variable<Producer>()
        where Identifier : IdentifierVariableIdentifier<Identifier, VariableImpl, Producer>,
              VariableImpl : IdentifierVariable<Identifier, VariableImpl, Producer>,
              Producer : IProducer {
    @Suppress("UNCHECKED_CAST")
    private val self
        get() = this as VariableImpl

    var identifier: Identifier = identifier
        set(value) {
            // Suppress for first assign
            @Suppress("SENSELESS_COMPARISON")
            if (field != null) field.usedBy.remove(this)
            field = value
            field.usedBy.add(self)
        }

    init {
        identifier.usedBy.add(self)
    }

    open fun mergeImpl(b: VariableImpl): Identifier {
        if (identifier === b.identifier) return identifier
        check(identifier.stackType == b.identifier.stackType)
        val newType = identifier.type ?: b.identifier.type
        val mergeOnto: Identifier
        val current: Identifier
        if (b.identifier.usedBy.size < identifier.usedBy.size) {
            mergeOnto = identifier
            current = b.identifier
        } else {
            mergeOnto = b.identifier
            current = identifier
        }

        mergeOnto.type = newType
        mergeOnto.consumerExpressions.addAll(current.consumerExpressions)
        mergeOnto.producers.addAll(current.producers)
        mergeOnto.consumerStatements.addAll(current.consumerStatements)
        for (localVariable in current.usedBy.toList()) {
            localVariable.identifier = mergeOnto
        }
        current.consumerStatements.clear()
        current.consumerExpressions.clear()
        current.producers.clear()
        return mergeOnto
    }

    override fun consumeByExpression(value: ValueProperty<*, Value>) {
        identifier.consumerExpressions += value
    }

    override fun consumeByStatement(statement: ValueProperty<*, Statement>) {
        identifier.consumerStatements += statement
    }

    override fun unConsumeByExpression(value: ValueProperty<*, Value>) {
        identifier.consumerExpressions -= value
    }

    override fun unConsumeByStatement(statement: ValueProperty<*, Statement>) {
        identifier.consumerStatements -= statement
    }

    override fun addProducer(producer: Producer) {
        check(identifier.producers.add(producer))
    }

    override fun removeProducer(producer: Producer) {
        check(identifier.producers.remove(producer))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IdentifierVariable<*, *, *>

        if (identifier != other.identifier) return false

        return true
    }

    override fun hashCode(): Int {
        return identifier.hashCode()
    }

    override fun toString(): String = buildString {
        append(this@IdentifierVariable.javaClass.simpleName)
        append('(')
        append(identifier.id)
        append(spInfo())
        append('(')
        append(identifier.type ?: identifier.stackType)
        append(')')
        append(')')
    }

    fun spInfo() = buildString {
        val consumeCount = identifier.consumerCount
        val produceCount = identifier.producerCount
        append('(')
        if (consumeCount == 0) append('n')
        else if (consumeCount == 1) append('s')
        else if (consumeCount <= 9) append(consumeCount)
        else append('m')
        append('C')
        if (produceCount == 0) append('n')
        else if (produceCount == 1) append('s')
        else if (produceCount <= 9) append(produceCount)
        else append('m')
        append('P')
        append(')')
    }
}

class LocalVariableIdentifier(val index: Int, override val stackType: StackType, type: Type?) :
    IdentifierVariableIdentifier<LocalVariableIdentifier, LocalVariable, IProducer>() {
    var info: LocalVariableInfo? = null
        set(value) {
            if (value != null) check(field == null)
            field = value
            type = value?.descriptor?.let(Type::getType) ?: type
        }

    constructor(index: Int, stackType: StackType) : this(index, stackType, null)
    constructor(index: Int, type: Type) : this(index, StackType.byType(type), type)

    override var type: Type? = type
        set(value) {
            if (value != null)
                require(stackType == StackType.byType(value))
            field = value
        }

    init {
        this.type = type
    }
}

class StackVariableIdentifier(override val stackType: StackType, override var type: Type?) :
    IdentifierVariableIdentifier<StackVariableIdentifier, StackVariable, IStackProducer>() {
    fun value() = StackVariable(this)
}

class LocalVariable : IdentifierVariable<LocalVariableIdentifier, LocalVariable, IProducer> {
    val index: Int get() = identifier.index
    override val stackType: StackType get() = identifier.stackType
    override val type: Type? get() = identifier.type

    constructor(index: Int, stackType: StackType) : super(LocalVariableIdentifier(index, stackType, null))

    constructor(index: Int, type: Type) : super(LocalVariableIdentifier(index, StackType.byType(type), type))

    constructor(identifier: LocalVariableIdentifier) : super(identifier)

    override val leastType: Class<IProducer> get() = IProducer::class.java

    fun merge(b: LocalVariable) {
        check(index == b.index)
        mergeImpl(b)
    }
}

class StackVariable : IdentifierVariable<StackVariableIdentifier, StackVariable, IStackProducer> {
    override val stackType: StackType get() = identifier.stackType
    override val type: Type? get() = identifier.type

    constructor(stackType: StackType) : super(StackVariableIdentifier(stackType, null))

    constructor(type: Type) : super(StackVariableIdentifier(StackType.byType(type), type))

    constructor(identifier: StackVariableIdentifier) : super(identifier)

    override val leastType: Class<IStackProducer> get() = IStackProducer::class.java

    fun merge(b: StackVariable) {
        mergeImpl(b)
    }

    companion object {
        fun cloneBy(id: StackVariableIdentifier) = StackVariableIdentifier(id.stackType, id.type).value()
        fun cloneBy(id: StackVariable) = StackVariableIdentifier(id.stackType, id.type).value()
    }
}

sealed class ExpressionVariable : Variable<IProducer>() {
    var consumer: ValueProperty<*, Value>? = null
    var consumerStatement: ValueProperty<*, Statement>? = null
    var producer: IProducer? = null

    override val leastType: Class<IProducer> get() = IProducer::class.java

    override fun addProducer(producer: IProducer) {
        check(this.producer == null)
        this.producer = producer
    }

    override fun removeProducer(producer: IProducer) {
        check(this.producer === producer)
        this.producer = null
    }

    override fun consumeByExpression(value: ValueProperty<*, Value>) {
        check(consumer == null && consumerStatement == null)
        consumer = value
    }

    override fun consumeByStatement(statement: ValueProperty<*, Statement>) {
        check(consumer == null && consumerStatement == null)
        consumerStatement = statement
    }

    override fun unConsumeByExpression(value: ValueProperty<*, Value>) {
        require(value == consumer)
        consumer = null
    }

    override fun unConsumeByStatement(statement: ValueProperty<*, Statement>) {
        require(statement == consumerStatement)
        consumerStatement = null
    }
}

sealed class ExpressionValue : Value() {
    val anyConsumer get() = consumer ?: consumerStatement
    var consumer: ValueProperty<*, Value>? = null
    var consumerStatement: ValueProperty<*, Statement>? = null

    override fun consumeByExpression(value: ValueProperty<*, Value>) {
        check(consumer == null && consumerStatement == null)
        consumer = value
    }

    override fun consumeByStatement(statement: ValueProperty<*, Statement>) {
        check(consumer == null && consumerStatement == null)
        consumerStatement = statement
    }

    override fun unConsumeByExpression(value: ValueProperty<*, Value>) {
        require(value == consumer)
        consumer = null
    }

    override fun unConsumeByStatement(statement: ValueProperty<*, Statement>) {
        require(statement == consumerStatement)
        consumerStatement = null
    }
}

sealed class StatementExpressionValue : ExpressionValue() {
    abstract fun setLineNumber(line: Int)

    val mainStat by lazy { consumerStatement!!.thisRef as StatementExpressionStatement }
}

data class ConstantValue(val value: VConstantValue) : ExpressionValue() {
    override val type get() = value.theType
    override val stackType get() = value.stackType
}

sealed class VConstantValue(val value: Any?, val theType: Type?, val stackType: StackType)
sealed class VConstantNumber : VConstantValue {
    val number: Number

    constructor(number: Number, theType: Type?, stackType: StackType) : super(number, theType, stackType) {
        this.number = number
    }

    constructor(number: Number, value: Any?, theType: Type?, stackType: StackType) : super(value, theType, stackType) {
        this.number = number
    }
}

object VConstantNull : VConstantValue(null, null, StackType.Object)
data class VConstantString(val string: String) :
    VConstantValue(string, Type.getType(String::class.java), StackType.Object)

data class VConstantInt(val int: Int) : VConstantNumber(int, Type.INT_TYPE, StackType.Integer)
data class VConstantLong(val long: Long) : VConstantNumber(long, Type.LONG_TYPE, StackType.Long)
data class VConstantFloat(val float: Float) : VConstantNumber(float, Type.FLOAT_TYPE, StackType.Float)
data class VConstantDouble(val double: Double) : VConstantNumber(double, Type.DOUBLE_TYPE, StackType.Double)
data class VConstantType(val type: Type) : VConstantValue(type, Type.getType(Class::class.java), StackType.Object)
data class VConstantMethodType(val type: Type) :
    VConstantValue(type, Type.getType(MethodType::class.java), StackType.Object)

data class VConstantHandle(val handle: Handle) :
    VConstantValue(handle, Type.getType(MethodHandle::class.java), StackType.Object)

// java constant value
data class VConstantByte(val byte: Byte) : VConstantNumber(byte, Type.BYTE_TYPE, StackType.Integer)
data class VConstantChar(val char: Char) : VConstantNumber(char.toInt(), char, Type.CHAR_TYPE, StackType.Integer)
data class VConstantShort(val short: Short) : VConstantNumber(short, Type.SHORT_TYPE, StackType.Integer)
data class VConstantBoolean(val boolean: Boolean) :
    VConstantNumber(if (boolean) 1 else 1, boolean, Type.BOOLEAN_TYPE, StackType.Integer)

class VConstantConstantDynamic(val dynamic: ConstantDynamic) :
    VConstantValue(dynamic, Type.getType(MethodHandle::class.java), StackType.Object)

class ArrayVariable(ary: Value, idx: Value, val elementType: AllType) : ExpressionVariable() {
    var ary by prop(ary, ExpectTypes.Object)
    var idx by prop(idx, ExpectTypes.AnyInteger)

    override val type get() = ary.type?.toString()?.substring(1)?.let { Type.getType(it) }
    override val stackType get() = elementType.stackType

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArrayVariable

        if (ary != other.ary) return false
        if (idx != other.idx) return false
        if (elementType != other.elementType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ary.hashCode()
        result = 31 * result + idx.hashCode()
        result = 31 * result + elementType.hashCode()
        return result
    }

    override fun toString(): String {
        return "GetArray(ary=$ary, idx=$idx, elementType=$elementType)"
    }
}

class BiOperation(val op: BiOp, left: Value, right: Value) : ExpressionValue() {
    var left by prop(left, ::computeExpected)
    var right by prop(right, ::computeExpected)

    override val type
        get() = when (stackType) {
            StackType.Integer -> Type.INT_TYPE
            StackType.Long -> Type.LONG_TYPE
            StackType.Double -> Type.DOUBLE_TYPE
            StackType.Float -> Type.FLOAT_TYPE
            StackType.Object -> error("object is not valid type for bi operation")
        }
    override val stackType get() = sameOrEitherNullOrNull(left.stackType, right.stackType)

    init {
        require(stackType != StackType.Object)
    }

    private fun computeExpected() = when (anyConsumer?.expectedType ?: ExpectTypes.Unknown) {
        ExpectTypes.Boolean -> ExpectTypes.Boolean
        ExpectTypes.Char -> error("cannot expect char for bi operation")
        ExpectTypes.Byte -> error("cannot expect byte for bi operation")
        ExpectTypes.Short -> error("cannot expect short for bi operation")
        ExpectTypes.AnyInteger -> ExpectTypes.AnyInteger
        ExpectTypes.Long -> ExpectTypes.Long
        ExpectTypes.Double -> ExpectTypes.Double
        ExpectTypes.Float -> ExpectTypes.Float
        ExpectTypes.Object -> error("cannot expect object for bi operation")
        ExpectTypes.Unknown -> ExpectTypes.Unknown
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BiOperation

        if (op != other.op) return false
        if (left != other.left) return false
        if (right != other.right) return false

        return true
    }

    override fun hashCode(): Int {
        var result = op.hashCode()
        result = 31 * result + left.hashCode()
        result = 31 * result + right.hashCode()
        return result
    }

    override fun toString(): String {
        return "BiOperation(op=$op, left=$left, right=$right)"
    }
}

class ShiftOperation(val op: ShiftOp, value: Value, shift: Value) : ExpressionValue() {
    var value by prop(value, ::computeExpected)
    var shift by prop(shift, ExpectTypes.AnyInteger)

    override val type get() = value.type
    override val stackType get() = value.stackType

    private fun computeExpected() = when (anyConsumer?.expectedType ?: ExpectTypes.Unknown) {
        ExpectTypes.Boolean -> error("cannot expect boolean for shift operation")
        ExpectTypes.Char -> error("cannot expect char for shift operation")
        ExpectTypes.Byte -> error("cannot expect byte for shift operation")
        ExpectTypes.Short -> error("cannot expect short for shift operation")
        ExpectTypes.AnyInteger -> ExpectTypes.AnyInteger
        ExpectTypes.Long -> ExpectTypes.Long
        ExpectTypes.Double -> ExpectTypes.Double
        ExpectTypes.Float -> ExpectTypes.Float
        ExpectTypes.Object -> error("cannot expect object for shift operation")
        ExpectTypes.Unknown -> ExpectTypes.Unknown
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ShiftOperation

        if (op != other.op) return false
        if (value != other.value) return false
        if (shift != other.shift) return false

        return true
    }

    override fun hashCode(): Int {
        var result = op.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + shift.hashCode()
        return result
    }

    override fun toString(): String {
        return "ShiftOperation(op=$op, value=$value, shift=$shift)"
    }
}

class NegativeOperation(value: Value) : ExpressionValue() {
    val value by prop(value, ::computeExpected)

    override val type get() = value.type
    override val stackType get() = value.stackType

    private fun computeExpected() = when (anyConsumer?.expectedType ?: ExpectTypes.Unknown) {
        ExpectTypes.Boolean -> error("cannot expect boolean for negative operation")
        ExpectTypes.Char -> ExpectTypes.Char
        ExpectTypes.Byte -> ExpectTypes.Byte
        ExpectTypes.Short -> ExpectTypes.Short
        ExpectTypes.AnyInteger -> ExpectTypes.AnyInteger
        ExpectTypes.Long -> ExpectTypes.Long
        ExpectTypes.Double -> ExpectTypes.Double
        ExpectTypes.Float -> ExpectTypes.Float
        ExpectTypes.Object -> error("cannot expect object for negative operation")
        ExpectTypes.Unknown -> ExpectTypes.Unknown
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NegativeOperation

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        return "NegativeOperation(value=$value)"
    }
}

class CastValue(val castTo: AllType, value: Value) : ExpressionValue() {
    val value by prop(value, ExpectTypes.Unknown)

    override val type get() = castTo.asmType
    override val stackType get() = castTo.stackType

    init {
        when (castTo) {
            AllType.Boolean -> error("invalid cast type to boolean")
            AllType.Object -> error("invalid cast type to object")
            else -> {
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CastValue

        if (castTo != other.castTo) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = castTo.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

    override fun toString(): String {
        return "CastVariable(castTo=$castTo, value=$value)"
    }
}

class LongCompare(left: Value, right: Value) : ExpressionValue() {
    var left by prop(left, ExpectTypes.Long)
    var right by prop(right, ExpectTypes.Long)

    override val type get() = Type.INT_TYPE
    override val stackType get() = StackType.Integer

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LongCompare

        if (left != other.left) return false
        if (right != other.right) return false

        return true
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + right.hashCode()
        return result
    }

    override fun toString(): String {
        return "LongCompare(left=$left, right=$right)"
    }
}

class FloatingCompareLesser(left: Value, right: Value) : ExpressionValue() {
    var left by prop(left, ExpectTypes.Unknown)
    var right by prop(right, ExpectTypes.Unknown)

    override val type get() = Type.INT_TYPE
    override val stackType get() = StackType.Integer

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FloatingCompareLesser

        if (left != other.left) return false
        if (right != other.right) return false

        return true
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + right.hashCode()
        return result
    }

    override fun toString(): String {
        return "FloatingCompareLesser(left=$left, right=$right)"
    }
}

class FloatingCompareGreater(left: Value, right: Value) : ExpressionValue() {
    var left by prop(left, ExpectTypes.Unknown)
    var right by prop(right, ExpectTypes.Unknown)

    override val type get() = Type.INT_TYPE
    override val stackType get() = StackType.Integer

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FloatingCompareGreater

        if (left != other.left) return false
        if (right != other.right) return false

        return true
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + right.hashCode()
        return result
    }

    override fun toString(): String {
        return "FloatingCompareGreater(left=$left, right=$right)"
    }
}

class ConditionValue(val condition: BiCondition, left: Value, right: Value) : ExpressionValue() {
    var left by prop(left, ExpectTypes.Unknown)
    var right by prop(right, ExpectTypes.Unknown)

    override val type get() = Type.BOOLEAN_TYPE
    override val stackType get() = StackType.Integer

    init {
        lineNumber = -2
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConditionValue

        if (condition != other.condition) return false
        if (left != other.left) return false
        if (right != other.right) return false

        return true
    }

    override fun hashCode(): Int {
        var result = condition.hashCode()
        result = 31 * result + left.hashCode()
        result = 31 * result + right.hashCode()
        return result
    }

    override fun toString() = when (condition) {
        BiCondition.EQ -> "ConditionValue($left == $right)"
        BiCondition.NE -> "ConditionValue($left != $right)"
        BiCondition.LT -> "ConditionValue($left < $right)"
        BiCondition.GE -> "ConditionValue($left >= $right)"
        BiCondition.GT -> "ConditionValue($left > $right)"
        BiCondition.LE -> "ConditionValue($left <= $right)"
    }
}

enum class BiCondition() {
    EQ,
    NE,
    LE,
    LT,
    GE,
    GT,
}

data class StaticField(val owner: String, val name: String, val desc: String) : ExpressionVariable() {
    override val type by unsafeLazy { Type.getType(desc) }
    override val stackType by unsafeLazy { StackType.byDesc(desc) }
}

class InstanceField(val owner: String, val name: String, val desc: String, self: Value) : ExpressionVariable() {
    var self by prop(self, ExpectTypes.Object)

    override val type by unsafeLazy { Type.getType(desc) }
    override val stackType by unsafeLazy { StackType.byDesc(desc) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InstanceField

        if (owner != other.owner) return false
        if (name != other.name) return false
        if (desc != other.desc) return false
        if (self != other.self) return false

        return true
    }

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + desc.hashCode()
        result = 31 * result + self.hashCode()
        return result
    }

    override fun toString(): String {
        return "InstanceField(owner='$owner', name='$name', desc='$desc', self=$self)"
    }
}

class InvokeVirtualValue(
    val owner: String,
    val name: String,
    val desc: String,
    val isInterface: Boolean,
    self: Value,
    args: List<Value>
) : ExpressionValue() {
    var self by prop(self, ExpectTypes.Object)
    val argProps = args.zip(Type.getArgumentTypes(desc)).map { (v, t) -> prop(v, ExpectTypes.by(t)) }
    val args = PropertyList(this, argProps)

    override val type by unsafeLazy { Type.getReturnType(desc) }
    override val stackType by unsafeLazy { StackType.byType(type) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvokeVirtualValue

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
        return "InvokeVirtualValue(owner='$owner', name='$name', desc='$desc', isInterface=$isInterface, self=$self, args=$args)"
    }
}

class InvokeSpecialValue(
    val owner: String,
    val name: String,
    val desc: String,
    val isInterface: Boolean,
    self: Value,
    args: List<Value>
) : ExpressionValue() {
    var self by prop(self, ExpectTypes.Object)
    val argProps = args.zip(Type.getArgumentTypes(desc)).map { (v, t) -> prop(v, ExpectTypes.by(t)) }
    val args = PropertyList(this, argProps)

    override val type by unsafeLazy { Type.getReturnType(desc) }
    override val stackType by unsafeLazy { StackType.byType(type) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvokeSpecialValue

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
        return "InvokeSpecialValue(owner='$owner', name='$name', desc='$desc', isInterface=$isInterface, self=$self, args=$args)"
    }
}

class InvokeStaticValue(
    val owner: String,
    val name: String,
    val desc: String,
    val isInterface: Boolean,
    args: List<Value>
) : ExpressionValue() {
    val argProps = args.zip(Type.getArgumentTypes(desc)).map { (v, t) -> prop(v, ExpectTypes.by(t)) }
    val args = PropertyList(this, argProps)

    override val type by unsafeLazy { Type.getReturnType(desc) }
    override val stackType by unsafeLazy { StackType.byType(type) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvokeStaticValue

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
        return "InvokeStaticValue(owner='$owner', name='$name', desc='$desc', isInterface=$isInterface, args=$args)"
    }
}

class InvokeInterfaceValue(val owner: String, val name: String, val desc: String, self: Value, args: List<Value>) :
    ExpressionValue() {
    var self by prop(self, ExpectTypes.Object)
    val argProps = args.zip(Type.getArgumentTypes(desc)).map { (v, t) -> prop(v, ExpectTypes.by(t)) }
    val args = PropertyList(this, argProps)

    override val type by unsafeLazy { Type.getReturnType(desc) }
    override val stackType by unsafeLazy { StackType.byType(type) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvokeInterfaceValue

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
        return "InvokeInterfaceValue(owner='$owner', name='$name', desc='$desc', self=$self, args=$args)"
    }
}

class InvokeDynamicValue(
    val name: String,
    val descriptor: String,
    val bootstrapMethodHandle: Handle,
    val bootstrapMethodArguments: List<Any>,
    args: List<Value>,
) : ExpressionValue() {
    val argProps = args.zip(Type.getArgumentTypes(descriptor)).map { (v, t) -> prop(v, ExpectTypes.by(t)) }
    val args = PropertyList(this, argProps)

    override val type by unsafeLazy { Type.getReturnType(descriptor) }
    override val stackType by unsafeLazy { StackType.byType(type) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvokeDynamicValue

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
        return "InvokeDynamicValue(name='$name', descriptor='$descriptor', bootstrapMethodHandle=$bootstrapMethodHandle, bootstrapMethodArguments=$bootstrapMethodArguments, args=$args)"
    }
}

data class NewObject(override val type: Type) : ExpressionValue() {
    override val stackType get() = StackType.Object
}

class NewArray(val element: Type, size: Value) : ExpressionValue() {
    val size by prop(size, ExpectTypes.AnyInteger)

    override val type by unsafeLazy { Type.getType("[$element") }
    override val stackType get() = StackType.Object

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NewArray

        if (element != other.element) return false
        if (size != other.size) return false

        return true
    }

    override fun hashCode(): Int {
        var result = element.hashCode()
        result = 31 * result + size.hashCode()
        return result
    }

    override fun toString(): String {
        return "NewArray(element=$element, size=$size)"
    }
}

class ArrayLength(array: Value) : ExpressionValue() {
    var array by prop(array, ExpectTypes.Object)

    override val type get() = Type.INT_TYPE
    override val stackType get() = StackType.Integer

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArrayLength

        if (array != other.array) return false

        return true
    }

    override fun hashCode(): Int {
        return array.hashCode()
    }

    override fun toString(): String {
        return "ArrayLength(array=$array)"
    }
}

class CheckCast(value: Value, override val type: Type) : ExpressionValue() {
    var value by prop(value, ExpectTypes.Object)

    override val stackType get() = StackType.Integer

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CheckCast

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        return "CheckCast(value=$value)"
    }
}

class InstanceOf(value: Value, val classType: Type) : ExpressionValue() {
    var value by prop(value, ExpectTypes.Object)

    override val type get() = Type.BOOLEAN_TYPE
    override val stackType get() = StackType.Integer

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InstanceOf

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        return "InstanceOf(classType=$classType)"
    }
}

class MultiANewArray(val arrayType: Type, dimensionSizes: List<Value>) : ExpressionValue() {
    private val dimensionSizeProps = dimensionSizes.map { prop(it, ExpectTypes.AnyInteger) }
    val dimensionSizes = PropertyList(this, dimensionSizeProps)

    override val type get() = arrayType
    override val stackType get() = StackType.Object

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MultiANewArray

        if (arrayType != other.arrayType) return false
        if (dimensionSizes != other.dimensionSizes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = arrayType.hashCode()
        result = 31 * result + dimensionSizes.hashCode()
        return result
    }

    override fun toString(): String {
        return "MultiANewArray(arrayType=$arrayType, dimensionSizes=$dimensionSizes)"
    }
}

/// java expression variables

class NewAndCallConstructor(val owner: String, val desc: String, args: List<Value>) : ExpressionValue() {
    val argProps = args.zip(Type.getArgumentTypes(desc)).map { (v, t) -> prop(v, ExpectTypes.by(t)) }
    val args = PropertyList(this, argProps)

    override val type by unsafeLazy { Type.getObjectType(owner) }
    override val stackType get() = StackType.Object

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NewAndCallConstructor

        if (owner != other.owner) return false
        if (desc != other.desc) return false
        if (args != other.args) return false

        return true
    }

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + desc.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }

    override fun toString(): String {
        return "NewAndCallConstructor(owner='$owner', desc='$desc', args=$args)"
    }
}

class Assign(variable: Variable<in Assign>, value: Value) : StatementExpressionValue(), IStackProducer {
    var value by prop(value) { this.variable.type?.let((ExpectTypes)::by) ?: ExpectTypes.Unknown }
    var variable by mutatingProp(variable, consumes = false)

    override val type get() = sameOrEitherNullOrNull(variable.type, value.type)
    override val stackType get() = sameOrEitherNullOrNull(variable.stackType, value.stackType)

    init {
        lineNumber = -2
    }

    override fun setLineNumber(line: Int) {
        this.lineNumber = line
        value.lineNumber = line
        variable.lineNumber = line
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Assign

        if (variable != other.variable) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = variable.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

    override fun toString(): String {
        return "AssignedValue(variable=$variable, value=$value)"
    }

    override fun dispose() {
        super.dispose()
        variable.removeProducer(this)
    }
}

class NewArrayWithInitializerValue(val elementType: Type, arrayInitializer: List<Value>) : ExpressionValue() {
    val arrayInitializerProps = arrayInitializer.map { prop(it, ExpectTypes.by(elementType)) }
    val arrayInitializer = PropertyList(this, arrayInitializerProps)

    override val type by unsafeLazy { Type.getType("[$elementType") }
    override val stackType get() = StackType.Object

    init {
        lineNumber = -2
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NewArrayWithInitializerValue

        if (elementType != other.elementType) return false
        if (arrayInitializer != other.arrayInitializer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = elementType.hashCode()
        result = 31 * result + arrayInitializer.hashCode()
        return result
    }


    override fun toString(): String {
        return "NewArrayWithInitializerValue(arrayType=$elementType, arrayInitializer=$arrayInitializer)"
    }
}

class BooleanNotValue(value: Value) : ExpressionValue() {
    var value by prop(value, ExpectTypes.Boolean)

    override val type get() = Type.BOOLEAN_TYPE
    override val stackType get() = StackType.Integer

    init {
        lineNumber = -2
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BooleanNotValue

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        return "BooleanNotValue(value=$value)"
    }
}

class ConditionalOperatorValue(
    condition: Value,
    ifTrue: Value,
    ifFalse: Value,
) : ExpressionValue() {
    var condition by prop(condition, ExpectTypes.Boolean)
    var ifTrue by prop(ifTrue) { anyConsumer?.expectedType ?: ExpectTypes.Unknown }
    var ifFalse by prop(ifFalse) { anyConsumer?.expectedType ?: ExpectTypes.Unknown }

    override val type get() = sameOrEitherNullOrNull(ifTrue.type, ifFalse.type)
    override val stackType get() = sameOrEitherNullOrNull(ifTrue.stackType, ifFalse.stackType)

    init {
        lineNumber = -2
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConditionalOperatorValue

        if (condition != other.condition) return false
        if (ifTrue != other.ifTrue) return false
        if (ifFalse != other.ifFalse) return false

        return true
    }

    override fun hashCode(): Int {
        var result = condition.hashCode()
        result = 31 * result + ifTrue.hashCode()
        result = 31 * result + ifFalse.hashCode()
        return result
    }

    override fun toString(): String {
        return "ConditionalOperatorValue(condition=$condition, ifTrue=$ifTrue, ifFalse=$ifFalse)"
    }
}

class BiOperationAssignedValue(val op: BiOp, variable: Variable<in BiOperationAssignedValue>, right: Value) :
    StatementExpressionValue(), IProducer {
    var variable by mutatingProp(variable, consumes = true)
    var right by prop(right) { this.variable.type?.let((ExpectTypes)::by) ?: ExpectTypes.Unknown }

    override val type get() = sameOrEitherNullOrNull(variable.type, right.type)
    override val stackType get() = sameOrEitherNullOrNull(variable.stackType, right.stackType)

    init {
        lineNumber = -2
    }

    override fun setLineNumber(line: Int) {
        this.lineNumber = line
        right.lineNumber = line
        variable.lineNumber = line
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BiOperationAssignedValue

        if (op != other.op) return false
        if (variable != other.variable) return false
        if (right != other.right) return false

        return true
    }

    override fun hashCode(): Int {
        var result = op.hashCode()
        result = 31 * result + variable.hashCode()
        result = 31 * result + right.hashCode()
        return result
    }

    override fun toString(): String {
        return "BiOperationAssignedValue(op=$op, variable=$variable, right=$right)"
    }

    override fun dispose() {
        super.dispose()
        variable.removeProducer(this)
    }
}

class ShiftOperationAssignedValue(val op: ShiftOp, value: Variable<in ShiftOperationAssignedValue>, shift: Value) :
    StatementExpressionValue(), IProducer {
    var value by mutatingProp(value, consumes = true)
    var shift by prop(shift, ExpectTypes.AnyInteger)

    override val type get() = value.type
    override val stackType get() = value.stackType

    init {
        lineNumber = -2
    }

    override fun setLineNumber(line: Int) {
        this.lineNumber = line
        shift.lineNumber = line
        value.lineNumber = line
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ShiftOperationAssignedValue

        if (op != other.op) return false
        if (value != other.value) return false
        if (shift != other.shift) return false

        return true
    }

    override fun hashCode(): Int {
        var result = op.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + shift.hashCode()
        return result
    }

    override fun toString(): String {
        return "ShiftOperationAssignedValue(op=$op, value=$value, shift=$shift)"
    }
}

class BooleanAndAndOperation(left: Value, right: Value) : ExpressionValue() {
    var left by prop(left, ExpectTypes.Boolean)
    var right by prop(right, ExpectTypes.Boolean)

    override val type get() = Type.BOOLEAN_TYPE
    override val stackType get() = StackType.Integer

    init {
        lineNumber = -2
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BooleanAndAndOperation

        if (left != other.left) return false
        if (right != other.right) return false

        return true
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + right.hashCode()
        return result
    }

    override fun toString(): String {
        return "BooleanAndAndOperation(left=$left, right=$right)"
    }
}

class BooleanOrOrOperation(left: Value, right: Value) : ExpressionValue() {
    var left by prop(left, ExpectTypes.Boolean)
    var right by prop(right, ExpectTypes.Boolean)

    override val type get() = Type.BOOLEAN_TYPE
    override val stackType get() = StackType.Integer

    init {
        lineNumber = -2
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BooleanOrOrOperation

        if (left != other.left) return false
        if (right != other.right) return false

        return true
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + right.hashCode()
        return result
    }

    override fun toString(): String {
        return "BooleanOrOrOperation(left=$left, right=$right)"
    }
}

class InDecrementValue(val inDecrement: InDecrementType, variable: Variable<in InDecrementValue>) : ExpressionValue(),
    IProducer {
    var variable by mutatingProp(variable, consumes = true)

    override val type get() = variable.type
    override val stackType get() = variable.stackType

    init {
        lineNumber = -2
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InDecrementValue

        if (inDecrement != other.inDecrement) return false
        if (variable != other.variable) return false

        return true
    }

    override fun hashCode(): Int {
        var result = inDecrement.hashCode()
        result = 31 * result + variable.hashCode()
        return result
    }

    override fun toString(): String {
        return "InDecrementValue(inDecrement=$inDecrement, variable=$variable)"
    }

    override fun dispose() {
        super.dispose()
        variable.removeProducer(this)
    }
}

enum class InDecrementType {
    PrefixIncrement,
    PrefixDecrement,
    SuffixIncrement,
    SuffixDecrement,
}

class StaticFieldWithSelf(val owner: String, val name: String, val desc: String, self: Value) : ExpressionVariable() {
    var self by prop(self, ExpectTypes.Object)

    override val type by unsafeLazy { Type.getType(desc) }
    override val stackType by unsafeLazy { StackType.byDesc(desc) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StaticFieldWithSelf

        if (owner != other.owner) return false
        if (name != other.name) return false
        if (desc != other.desc) return false
        if (self != other.self) return false

        return true
    }

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + desc.hashCode()
        result = 31 * result + self.hashCode()
        return result
    }

    override fun toString(): String {
        return "StaticFieldWithSelf(owner='$owner', name='$name', desc='$desc', self=$self)"
    }
}

class InvokeStaticWithSelfValue(
    val owner: String,
    val name: String,
    val desc: String,
    val isInterface: Boolean,
    self: Value,
    args: List<Value>
) : ExpressionValue() {
    var self by prop(self, ExpectTypes.Object)

    val argProps = args.zip(Type.getArgumentTypes(desc)).map { (v, t) -> prop(v, ExpectTypes.by(t)) }
    val args = PropertyList(this, argProps)

    override val type by unsafeLazy { Type.getReturnType(desc) }
    override val stackType by unsafeLazy { StackType.byType(type) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvokeStaticWithSelfValue

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
        return "InvokeStaticWithSelfValue(owner='$owner', name='$name', desc='$desc', isInterface=$isInterface, self=$self, args=$args)"
    }
}

/**
 * null check only for inner class creation
 */
class NullChecked(value: Value) : ExpressionValue() {
    var value by prop(value, ExpectTypes.Object)

    init {
        check(value.stackType == StackType.Object)
        lineNumber = -2
    }

    override val type: Type? get() = value.type
    override val stackType: StackType get() = value.stackType

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NullChecked

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        return "NullChecked(value=$value)"
    }
}

// temporary value for each thread

object TemporaryExpressionValue : Value() {

    override val type get() = error("not implemented")
    override val stackType get() = error("not implemented")


    override fun consumeByExpression(value: ValueProperty<*, Value>) {
        // nop
    }

    override fun consumeByStatement(statement: ValueProperty<*, Statement>) {
        // nop
    }

    override fun unConsumeByExpression(value: ValueProperty<*, Value>) {
        // nop
    }

    override fun unConsumeByStatement(statement: ValueProperty<*, Statement>) {
        // nop
    }
}
