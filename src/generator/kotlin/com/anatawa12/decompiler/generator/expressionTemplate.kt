package com.anatawa12.decompiler.generator

fun parseExpressionTemplate(text: String): ExpTemplate {
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
    val elements = mutableListOf<ExpElem>()
    while (lines.hasNext()) {
        val line = lines.next()
        val action = line.substringBefore(' ')

        elements += when (action) {
            "" -> continue
            "#cls" -> readClass(line, lines)
            "#section-comment" -> ExpElemSectionComment(line.substringAfter(' '))
            "#src" -> {
                ExpElemSrc(buildString {
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
    return ExpTemplate(
        actualHeader = actualHeader,
        expectHeader = expectHeader,
        elements = elements,
    )
}

private fun readClass(line: String, lines: Iterator<String>): ExpElemCls {
    var noLine = false
    val implements = mutableListOf<String>()
    val superClass: String
    val elements = mutableListOf<ExpClsElem>()
    val verifiers = mutableListOf<String>()
    var type: TypeOrStackType? = null
    var stackType: TypeOrStackType? = null

    val options = line.split(' ')
    val clsName = options[1]
    superClass = when (val option = options[2]) {
        "exp_var" -> "ExpressionVariable"
        "exp_val" -> "ExpressionValue"
        "stat_exp_val" -> "StatementExpressionValue"
        else -> error("unknown super class: $option")
    }
    for (option in options.drop(3)) {
        when (option) {
            "no_line" -> noLine = true
            "stack_producer" -> implements += "IStackProducer"
            "producer" -> implements += "IProducer"
            "label_consumer" -> implements += "IExpLabelConsumer"
            else -> error("unknown class option: $option")
        }
    }

    for (line in lines) {
        if (line.isBlank()) continue
        val elems = line.trim().split(' ')
        when (val action = elems[0]) {
            "#end" -> break
            "val" -> {
                val (_, valName, type1) = elems
                elements += when (type1) {
                    "value" -> ExpClsElemValueField(
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
                            "type_getter" -> ValueExpectType.Constant(buildString {
                                while (elems[i++] != "%%")
                                    append(elems[i - 1] + " ")
                            })
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
                            else -> error("invalid expect type: $expectType")
                        }
                        ExpClsElemValueField(
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
                elements += when (type1) {
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
                        ExpClsElemValueField(
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
            "type" -> {
                var i = 1
                type = when (val typeStr = elems[i++]) {
                    "boolean" -> TypeOrStackType.TypeGetter("Type.BOOLEAN_TYPE")
                    "char" -> TypeOrStackType.TypeGetter("Type.CHAR_TYPE")
                    "byte" -> TypeOrStackType.TypeGetter("Type.BYTE_TYPE")
                    "short" -> TypeOrStackType.TypeGetter("Type.SHORT_TYPE")
                    "integer" -> TypeOrStackType.TypeGetter("Type.INT_TYPE")
                    "long" -> TypeOrStackType.TypeGetter("Type.LONG_TYPE")
                    "double" -> TypeOrStackType.TypeGetter("Type.DOUBLE_TYPE")
                    "float" -> TypeOrStackType.TypeGetter("Type.FLOAT_TYPE")
                    "const" -> TypeOrStackType.Constant(buildString {
                        while (elems[i++] != "%%")
                            append(elems[i - 1] + " ")
                    })
                    "get" -> TypeOrStackType.TypeGetter(buildString {
                        while (elems[i++] != "%%")
                            append(elems[i - 1] + " ")
                    })
                    "lazy" -> TypeOrStackType.Lazy(buildString {
                        while (elems[i++] != "%%")
                            append(elems[i - 1] + " ")
                    })
                    else -> error("invalid expect type: $typeStr")
                }
            }
            "stack" -> {
                var i = 1
                stackType = when (val typeStr = elems[i++]) {
                    "integer" -> TypeOrStackType.TypeGetter("StackType.Integer")
                    "long" -> TypeOrStackType.TypeGetter("StackType.Long")
                    "double" -> TypeOrStackType.TypeGetter("StackType.Double")
                    "float" -> TypeOrStackType.TypeGetter("StackType.Float")
                    "object" -> TypeOrStackType.TypeGetter("StackType.Object")
                    "const" -> TypeOrStackType.Constant(buildString {
                        while (elems[i++] != "%%")
                            append(elems[i - 1] + " ")
                    })
                    "get" -> TypeOrStackType.TypeGetter(buildString {
                        while (elems[i++] != "%%")
                            append(elems[i - 1] + " ")
                    })
                    "lazy" -> TypeOrStackType.Lazy(buildString {
                        while (elems[i++] != "%%")
                            append(elems[i - 1] + " ")
                    })
                    else -> error("invalid expect type: $typeStr")
                }
            }
            "verify" -> {
                var i = 1
                verifiers += buildString {
                    while (elems[i++] != "%%")
                        append(elems[i - 1] + " ")
                }
            }
            "#src" -> {
                elements += ExpClsElemSrc(buildString {
                    for (line in lines) {
                        if (line.trim() == "#end") break
                        appendLine(line)
                    }
                })
            }
            else -> error("unknown class element: '$action'")
        }
    }

    return ExpElemCls(
        noLine = noLine,
        implements = implements,
        superClass = superClass,
        name = clsName,
        elements = elements,
        verifiers = verifiers,
        type = type ?: error("type is required"),
        stackType = stackType ?: error("stack is required"),
    )
}

private fun readField(isVal: Boolean, valName: String, type1: String, elems: List<String>) = when (type1) {
    "int" -> ExpClsElemSimpleField(isVal, valName, tInt)
    "boolean" -> ExpClsElemSimpleField(isVal, valName, tBoolean)
    "string" -> ExpClsElemSimpleField(isVal, valName, tString)

    "label" -> ExpClsElemSimpleField(isVal, valName, tLabel, elems.drop(3))
    "labels" -> ExpClsElemSimpleField(isVal, valName, tLabels, elems.drop(3))

    "int_label_pairs" -> ExpClsElemSimpleField(isVal, valName, tIntLabelPairs)

    "any" -> ExpClsElemSimpleField(isVal, valName, elems[3])
    else -> error("unknown type: $type1")
}

data class ExpTemplate(
    val actualHeader: String,
    val expectHeader: String,
    val elements: List<ExpElem>,
)

sealed class ExpElem

data class ExpElemSectionComment(val comment: String) : ExpElem()

data class ExpElemSrc(val actual: String, val expect: String) : ExpElem()

data class ExpElemCls(
    val noLine: Boolean,
    val implements: List<String>,
    val superClass: String,
    val name: String,
    val elements: List<ExpClsElem>,
    val verifiers: List<String>,
    val type: TypeOrStackType,
    val stackType: TypeOrStackType,
) : ExpElem()

sealed class ExpClsElem

data class ExpClsElemSimpleField(
    val isVal: Boolean,
    val name: String,
    val typeName: String,
    val attributes: List<String> = listOf(),
) : ExpClsElem()

data class ExpClsElemValueField(
    val isVal: Boolean,
    val expect: ValueExpectType?,
    val name: String,
    val typeName: String,
    val attributes: List<String> = listOf(),
) : ExpClsElem()

data class ExpClsElemSrc(
    val src: String
) : ExpClsElem()

sealed class TypeOrStackType {
    data class Constant(val exp: String) : TypeOrStackType()
    data class TypeGetter(val exp: String) : TypeOrStackType()
    data class Lazy(val exp: String) : TypeOrStackType()
}
