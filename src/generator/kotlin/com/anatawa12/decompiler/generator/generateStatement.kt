package com.anatawa12.decompiler.generator

import java.io.File

fun generateStatement(args: List<String>): String {
    val (generateType, file) = args
    val (actualHeader, expectHeader, elements) = parseStatementTemplate(File(file).readText())

    return when (generateType) {
        "decl_expect" -> {
            buildString {
                appendLine(expectHeader)
                appendLine()
                for (element in elements) {
                    when (element) {
                        is StatElemSectionComment -> {
                            appendLine("///// ${element.comment}")
                        }
                        is StatElemSrc -> {
                            appendLine(element.expect)
                        }
                        is StatElemCls -> {
                            val cls = element as StatElemCls
                            appendLine("expect class ${cls.name} (")
                            for (clsElem in cls.elements) {
                                when (clsElem) {
                                    is StatClsElemSimpleField -> appendLine("    ${clsElem.name}: ${clsElem.typeName}, ")
                                    is StatClsElemValueField -> appendLine("    ${clsElem.name}: ${clsElem.typeName}, ")
                                }
                            }
                            append(") : ${cls.superClass}")
                            if (cls.implements.isNotEmpty()) {
                                append(", ")
                                cls.implements.joinTo(this)
                            }
                            appendLine(" {")
                            for (clsElem in cls.elements) {
                                when (clsElem) {
                                    is StatClsElemSimpleField -> if (clsElem.isVal) {
                                        appendLine("    val ${clsElem.name}: ${clsElem.typeName}")
                                    } else {
                                        appendLine("    var ${clsElem.name}: ${clsElem.typeName}")
                                    }
                                    is StatClsElemValueField -> if (clsElem.isVal) {
                                        if (clsElem.typeName.startsWith("List<") && clsElem.typeName.endsWith(">")) {
                                            val elementType = clsElem.typeName.removePrefix("List<").removeSuffix(">")
                                            val propsName = clsElem.name.removeSuffix("s") + "Props"
                                            appendLine("    val ${propsName}: List<ValueProperty<${elementType}, Statement>>")
                                            appendLine("    val ${clsElem.name}: PropertyList<${elementType}, Statement>")
                                        } else {
                                            appendLine("    val ${clsElem.name}: ${clsElem.typeName}")
                                        }
                                    } else {
                                        appendLine("    var ${clsElem.name}: ${clsElem.typeName}")
                                    }
                                }
                            }
                            appendLine("}")
                            appendLine()
                        }
                    }
                }
            }
        }
        "decl_actual" -> {
            buildString {
                appendLine(actualHeader)
                appendLine()
                for (element in elements) {
                    when (element) {
                        is StatElemSectionComment -> {
                            appendLine("///// ${element.comment}")
                        }
                        is StatElemSrc -> {
                            appendLine(element.actual)
                        }
                        is StatElemCls -> {
                            val cls: StatElemCls = element
                            appendLine("actual class ${cls.name} actual constructor(")
                            for (clsElem in cls.elements) {
                                when (clsElem) {
                                    is StatClsElemSimpleField -> if (clsElem.isVal) {
                                        appendLine("    actual val ${clsElem.name}: ${clsElem.typeName}, ")
                                    } else {
                                        appendLine("    actual var ${clsElem.name}: ${clsElem.typeName}, ")
                                    }
                                    is StatClsElemValueField -> if (clsElem.isVal) {
                                        if (clsElem.typeName.startsWith("List<") && clsElem.typeName.endsWith(">")) {
                                            appendLine("    ${clsElem.name}: ${clsElem.typeName}, ")
                                        } else {
                                            appendLine("    actual val ${clsElem.name}: ${clsElem.typeName}, ")
                                        }
                                    } else {
                                        appendLine("    ${clsElem.name}: ${clsElem.typeName}, ")
                                    }
                                }
                            }
                            append(") : ${cls.superClass}()")
                            if (cls.implements.isNotEmpty()) {
                                append(", ")
                                cls.implements.joinTo(this)
                            }
                            appendLine(" {")
                            for (clsElem in cls.elements) {
                                when (clsElem) {
                                    is StatClsElemSimpleField -> {
                                        // nop for actual: impl in argument
                                    }
                                    is StatClsElemValueField -> if (clsElem.isVal) {
                                        if (clsElem.typeName.startsWith("List<") && clsElem.typeName.endsWith(">")) {
                                            val elementType = clsElem.typeName.removePrefix("List<").removeSuffix(">")
                                            val propsName = clsElem.name.removeSuffix("s") + "Props"

                                            append("    actual val ${propsName}: List<ValueProperty<${elementType}, Statement>> = ")
                                            val initializer = when (val expect = clsElem.expect!!) {
                                                is ValueExpectType.TypeGetter -> "${clsElem.name}.zip(${expect.exp}).map { (v, t) -> prop(v, t) }"
                                                is ValueExpectType.Constant -> "${clsElem.name}.map { prop(it, ${expect.exp}) }"
                                            }
                                            append(initializer)
                                            appendLine()
                                            appendLine("    actual val ${clsElem.name}: PropertyList<${elementType}, Statement> = PropertyList(this, ${propsName})")
                                        } else {
                                            // nop for actual: impl in argument
                                        }
                                    } else {
                                        append("    actual var ${clsElem.name}: ${clsElem.typeName} by ")
                                        if ("provide" in clsElem.attributes) {
                                            append("mutatingProp(${clsElem.name}, consumes = false)")
                                        } else if ("provide_consumes" in clsElem.attributes) {
                                            append("mutatingProp(${clsElem.name}, consumes = true)")
                                        } else {
                                            val expectType = when (val expect = clsElem.expect!!) {
                                                is ValueExpectType.TypeGetter -> expect.exp
                                                is ValueExpectType.Constant -> expect.exp
                                            }
                                            append("prop(${clsElem.name}, $expectType)")
                                        }
                                        appendLine()
                                    }
                                }
                            }
                            if (cls.superClass == "JavaControlFlowStatement") {
                                appendLine("    override val childBlocks: List<BlockBeginStatement> get() = listOf(")

                                for (clsElemSimpleField in cls.elements.asSequence()
                                    .filterIsInstance<StatClsElemSimpleField>()
                                    .filter { it.typeName == "BlockBeginStatement" }) {
                                    appendLine("        ${clsElemSimpleField.name}, ")
                                }
                                appendLine("    )")
                            }
                            appendLine()
                            appendLine("    init {")
                            if (cls.noLine)
                                appendLine("        super.setLineNumber(-2)")

                            for (clsElemSimpleField in cls.elements.asSequence()
                                .filterIsInstance<StatClsElemSimpleField>()) {
                                when (clsElemSimpleField.typeName) {
                                    tLabel -> if ("unused" !in clsElemSimpleField.attributes)
                                        appendLine("        ${clsElemSimpleField.name}.usedBy += this")
                                    tLabels -> if ("unused" !in clsElemSimpleField.attributes)
                                        appendLine("        ${clsElemSimpleField.name}.forEach { it.usedBy -= this }")
                                    tIntLabelPairs -> if ("unused" !in clsElemSimpleField.attributes)
                                        appendLine("        ${clsElemSimpleField.name}.forEach { it.second.usedBy -= this }")
                                }
                            }
                            appendLine("    }")
                            appendLine()
                            appendLine("    override fun onDispose() {")
                            appendLine("        super.onDispose()")
                            for (clsElemSimpleField in cls.elements.asSequence()
                                .filterIsInstance<StatClsElemSimpleField>()) {
                                when (clsElemSimpleField.typeName) {
                                    tLabel -> if ("unused" !in clsElemSimpleField.attributes)
                                        appendLine("        ${clsElemSimpleField.name}.usedBy -= this")
                                    tLabels -> if ("unused" !in clsElemSimpleField.attributes)
                                        appendLine("        ${clsElemSimpleField.name}.forEach { it.usedBy -= this }")
                                    tIntLabelPairs -> if ("unused" !in clsElemSimpleField.attributes)
                                        appendLine("        ${clsElemSimpleField.name}.forEach { it.second.usedBy -= this }")
                                }
                            }
                            appendLine("    }")
                            appendLine()
                            appendLine("    override fun equals(other: Any?): Boolean {")
                            appendLine("        if (this === other) return true")
                            appendLine("        if (javaClass != other?.javaClass) return false")
                            appendLine("        other as ${cls.name}")
                            for (clsElem in cls.elements) {
                                when (clsElem) {
                                    is StatClsElemSimpleField -> appendLine("        if (this.${clsElem.name} != other.${clsElem.name}) return false")
                                    is StatClsElemValueField -> appendLine("        if (this.${clsElem.name} != other.${clsElem.name}) return false")
                                }
                            }
                            appendLine("        return true")
                            appendLine("    }")
                            appendLine()
                            appendLine("    override fun hashCode(): Int {")
                            appendLine("        var result = 0")
                            for (clsElem in cls.elements) {
                                when (clsElem) {
                                    is StatClsElemSimpleField -> appendLine("        result = 31 * result + this.${clsElem.name}.hashCode()")
                                    is StatClsElemValueField -> appendLine("        result = 31 * result + this.${clsElem.name}.hashCode()")
                                }
                            }
                            appendLine("        return result")
                            appendLine("    }")
                            appendLine()
                            appendLine("    override fun toString(): String = (\"${cls.name}\"")
                            for ((i, clsElem) in cls.elements.withIndex()) {
                                val prefix = if (i == 0) "(" else ", "
                                when (clsElem) {
                                    is StatClsElemSimpleField -> appendLine("        + \"$prefix${clsElem.name}=\" + this.${clsElem.name}")
                                    is StatClsElemValueField -> appendLine("        + \"$prefix${clsElem.name}=\" + this.${clsElem.name}")
                                }
                            }
                            appendLine("        + \")\")")
                            appendLine()
                            appendLine("}")
                            appendLine()
                        }
                    }
                }
            }
        }
        else -> error("invalid generate type: $generateType")
    }
}
