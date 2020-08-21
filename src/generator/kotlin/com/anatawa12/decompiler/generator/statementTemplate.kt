package com.anatawa12.decompiler.generator

fun parseStatementTemplate(text: String): StatTemplate {
    val lines = text.lines().iterator()
    val actualHeader = buildString {
        for (line in lines) {
            if (line == "%%") break
            appendLine(line)
        }
    }
    val expectHeader = buildString {
        for (line in lines) {
            if (line == "%%") break
            appendLine(line)
        }
    }
    val elements = mutableListOf<StatElem>()
    while (lines.hasNext()) {
        val line = lines.next()
        val action = line.substringBefore(' ')

        elements += when (action) {
            "" -> continue
            "#cls" -> readClass(line, lines)
            "#section-comment" -> StatElemSectionComment(line.substringAfter(' '))
            "#src" -> {
                StatElemSrc(buildString {
                    for (line in lines) {
                        if (line == "%%") break
                        appendLine(line)
                    }
                }, buildString {
                    for (line in lines) {
                        if (line == "#end") break
                        appendLine(line)
                    }
                })
            }
            else -> error("unknown action $action")
        }
    }
    return StatTemplate(
        actualHeader = actualHeader,
        expectHeader = expectHeader,
        elements = elements,
    )
}

private fun readClass(line: String, lines: Iterator<String>): StatElemCls {
    var noLine = false
    val implements = mutableListOf<String>()
    var superClass = "Statement"
    val elements = mutableListOf<StatClsElem>()

    val options = line.split(' ')
    val clsName = options[1]
    for (option in options.drop(2)) {
        when (option) {
            "no_line" -> noLine = true
            "stack_producer" -> implements += "IStackProducer"
            "label_consumer" -> implements += "IStatLabelConsumer"
            "flow" -> superClass = "JavaControlFlowStatement"
            else -> error("unknown class option: $option")
        }
    }

    for (line in lines) {
        val elems = line.trim().split(' ')
        elements += when (val action = elems[0]) {
            "#end" -> break
            "val" -> {
                val (_, valName, type1) = elems
                when (type1) {
                    "value" -> StatClsElemValueField(
                        true,
                        null,
                        valName,
                        elems.drop(3).takeUnless { it.isEmpty() }?.joinToString(" ") ?: "Value"
                    )
                    "values" -> {
                        var i = 3
                        val expect = when (val expectType = elems[i++]) {
                            "types_getter" -> ValueExpectType.TypeGetter(buildString {
                                while (elems[i++] != "%%")
                                    append(elems[i - 1] + " ")
                            })
                            else -> error("invalid expect type: $expectType")
                        }
                        StatClsElemValueField(
                            true,
                            expect,
                            valName,
                            (elems.drop(i).takeUnless { it.isEmpty() }?.joinToString(" ")
                                ?: "Value").let { "List<$it>" })
                    }

                    else -> readField(true, valName, type1, elems)
                }
            }
            "var" -> {
                val (_, valName, type1) = elems
                val attributes = mutableListOf<String>()
                when (type1) {
                    "value" -> {
                        var i = 3
                        val expect = when (val expectType = elems[i++]) {
                            "boolean" -> ValueExpectType.Constant("ExpectTypes.Boolean")
                            "char" -> ValueExpectType.Constant("ExpectTypes.Char")
                            "byte" -> ValueExpectType.Constant("ExpectTypes.Byte")
                            "short" -> ValueExpectType.Constant("ExpectTypes.Short")
                            "integer" -> ValueExpectType.Constant("ExpectTypes.AnyInteger")
                            "long" -> ValueExpectType.Constant("ExpectTypes.Long")
                            "double" -> ValueExpectType.Constant("ExpectTypes.Double")
                            "float" -> ValueExpectType.Constant("ExpectTypes.Float")
                            "object" -> ValueExpectType.Constant("ExpectTypes.Object")
                            "unknown" -> ValueExpectType.Constant("ExpectTypes.Unknown")
                            "type_getter" -> ValueExpectType.TypeGetter(buildString {
                                while (elems[i++] != "%%")
                                    append(elems[i - 1] + " ")
                            })
                            "provide" -> {
                                attributes += "provide"
                                null
                            }
                            "provide_consumes" -> {
                                attributes += "provide_consumes"
                                null
                            }
                            else -> error("invalid expect type: $expectType")
                        }
                        StatClsElemValueField(
                            false,
                            expect,
                            valName,
                            elems.drop(i).takeUnless { it.isEmpty() }?.joinToString(" ") ?: "Value",
                            attributes
                        )
                    }
                    else -> readField(false, valName, type1, elems)
                }
            }
            else -> error("unknown class element: '$action'")
        }
    }

    return StatElemCls(
        noLine = noLine,
        implements = implements,
        superClass = superClass,
        name = clsName,
        elements = elements,
    )
}

private fun readField(isVal: Boolean, valName: String, type1: String, elems: List<String>) = when (type1) {
    "int" -> StatClsElemSimpleField(isVal, valName, tInt)
    "boolean" -> StatClsElemSimpleField(isVal, valName, tBoolean)
    "string" -> StatClsElemSimpleField(isVal, valName, tString)

    "label" -> StatClsElemSimpleField(isVal, valName, tLabel, elems.drop(3))
    "labels" -> StatClsElemSimpleField(isVal, valName, tLabels, elems.drop(3))

    "int_label_pairs" -> StatClsElemSimpleField(isVal, valName, tIntLabelPairs)

    "any" -> StatClsElemSimpleField(isVal, valName, elems[3])

    "block_begin" -> StatClsElemSimpleField(isVal, valName, tBlockBegin)
    else -> error("unknown type: $type1")
}

data class StatTemplate(
    val actualHeader: String,
    val expectHeader: String,
    val elements: List<StatElem>,
)

sealed class StatElem

data class StatElemSectionComment(val comment: String) : StatElem()

data class StatElemSrc(val actual: String, val expect: String) : StatElem()

data class StatElemCls(
    val noLine: Boolean,
    val implements: List<String>,
    val superClass: String,
    val name: String,
    val elements: List<StatClsElem>,
) : StatElem()

const val tInt = "Int"
const val tBoolean = "Boolean"
const val tString = "String"
const val tLabel = "StatLabel"
const val tLabels = "List<StatLabel>"
const val tIntLabelPairs = "List<Pair<Int, StatLabel>>"
const val tBlockBegin = "BlockBeginStatement"

sealed class StatClsElem

data class StatClsElemSimpleField(
    val isVal: Boolean,
    val name: String,
    val typeName: String,
    val attributes: List<String> = listOf(),
) : StatClsElem()

data class StatClsElemValueField(
    val isVal: Boolean,
    val expect: ValueExpectType?,
    val name: String,
    val typeName: String,
    val attributes: List<String> = listOf(),
) : StatClsElem()

sealed class ValueExpectType {
    data class TypeGetter(val exp: String) : ValueExpectType()
    data class Constant(val exp: String) : ValueExpectType()
}

