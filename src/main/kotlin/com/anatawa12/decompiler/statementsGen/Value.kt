package com.anatawa12.decompiler.statementsGen

import com.anatawa12.decompiler.instructions.StackType
import com.anatawa12.decompiler.util.Property
import com.anatawa12.decompiler.util.identitySetOf
import org.objectweb.asm.Type

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

    internal fun merge(b: Identifier): Identifier {
        if (this === b) return this
        check(this.stackType == b.stackType)

        val newType = this.type ?: b.type
        val mergeOnto: Identifier
        val current: Identifier
        @Suppress("UNCHECKED_CAST")
        if (b.usedBy.size < this.usedBy.size) {
            mergeOnto = this as Identifier
            current = b
        } else {
            mergeOnto = b
            current = this as Identifier
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

    fun spInfo() = buildString {
        val consumeCount = consumerCount
        val produceCount = producerCount
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

    companion object {
        private var nextId = 1
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

class ValueProperty<T : Value, out A>(
    value: T,
    thisRef: A,
    type: Class<T>,
    private val expectedTypeGetter: () -> ExpectTypes
) :
    Property<T, A>(value, thisRef, type) {
    val expectedType get() = expectedTypeGetter()
}

enum class BiCondition() {
    EQ,
    NE,
    LE,
    LT,
    GE,
    GT,
}

enum class InDecrementType {
    PrefixIncrement,
    PrefixDecrement,
    SuffixIncrement,
    SuffixDecrement,
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

fun ExpressionValue.computeExpectedOfBiOp() = when (anyConsumer?.expectedType ?: ExpectTypes.Unknown) {
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

fun ExpressionValue.computeExpectedOfShiftOp() = when (anyConsumer?.expectedType ?: ExpectTypes.Unknown) {
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

fun ExpressionValue.computeExpectedOfNegativeOperation() = when (anyConsumer?.expectedType ?: ExpectTypes.Unknown) {
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

fun computeTypeByStack(stackType: StackType, op: String) = when (stackType) {
    StackType.Integer -> Type.INT_TYPE
    StackType.Long -> Type.LONG_TYPE
    StackType.Double -> Type.DOUBLE_TYPE
    StackType.Float -> Type.FLOAT_TYPE
    StackType.Object -> error("object is not valid type for $op")
}

