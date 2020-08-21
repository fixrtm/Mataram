package com.anatawa12.decompiler.generator

import java.io.File

fun generateStatementComparator(args: List<String>): String {
    val (statementFile, expressionFile, templateFile) = args
    val (_, _, statElems) = parseStatementTemplate(File(statementFile).readText())
    val (_, _, expElems) = parseExpressionTemplate(File(expressionFile).readText())

    val lines = File(templateFile).readText().lines().iterator()

    val bodyElements = buildString {
        for (line in lines) {
            if (line == "%%") break
            appendLine("    $line")
        }
    }

    val stats = mutableMapOf<String, String>()
    val exprs = mutableMapOf<String, String>()

    for (line in lines) {
        if (line.isEmpty()) continue
        val (stat, type) = line.split(' ')
        when (stat) {
            "#for-stat" -> {
                stats[type] = buildString {
                    for (line in lines) {
                        if (line == "#end") break
                        appendLine("                $line")
                    }
                }
            }
            "#for-expr" -> {
                exprs[type] = buildString {
                    for (line in lines) {
                        if (line == "#end") break
                        appendLine("                $line")
                    }
                }
            }
            else -> error("unknown operation: $stat")
        }
    }

    return buildString {
        appendLine("package com.anatawa12.decompiler.statementsGen")
        appendLine("")
        appendLine("import com.anatawa12.decompiler.util.nextSequence")
        appendLine("import com.anatawa12.decompiler.util.zipEither")
        appendLine("")
        appendLine("actual internal class StatementsComparator actual constructor() {")
        append(bodyElements)
        appendLine("    actual fun isSame(aSeq: Sequence<Statement>, bSeq: Sequence<Statement>): Boolean {")
        appendLine("        for ((aStat, bStat) in aSeq zipEither bSeq) {")
        appendLine("            if (aStat == null) return false")
        appendLine("            if (bStat == null) return false")
        appendLine("            if (!isSameStat(aStat, bStat))")
        appendLine("                return false")
        appendLine("        }")
        appendLine("        return true")
        appendLine("    }")
        appendLine("")
        appendLine("    private fun isSameStat(a: Statement, b: Statement): Boolean {")
        appendLine("        if (a.javaClass !== b.javaClass) return false")
        appendLine("        return when (a) {")
        for (element in statElems) {
            when (element) {
                is StatElemSectionComment -> {
                }
                is StatElemSrc -> {
                }
                is StatElemCls -> {
                    val clsName = element.name
                    appendLine("            is $clsName -> {")
                    appendLine("                b as $clsName")
                    if (clsName in stats) {
                        append(stats[clsName]!!)
                        stats.remove(clsName)
                    } else {
                        for (clsElem in element.elements) {
                            when (clsElem) {
                                is StatClsElemSimpleField -> {
                                    when (clsElem.typeName) {
                                        tLabel -> {
                                            appendLine("                if (!isSameLabel(a.${clsElem.name}, b.${clsElem.name})) return false")
                                        }
                                        tLabels -> {
                                            appendLine("                for ((aLabel, bLabel) in a.${clsElem.name}.zip(b.${clsElem.name}))")
                                            appendLine("                    if (!isSameLabel(aLabel, bLabel)) return false")
                                        }
                                        tIntLabelPairs -> {
                                            appendLine("                for ((aPair, bPair) in a.${clsElem.name}.zip(b.${clsElem.name}))")
                                            appendLine("                    if (!isSameLabel(aPair.second, bPair.second)) return false")
                                        }
                                        tBlockBegin -> {
                                            appendLine("                if (!isSameBlock(a.${clsElem.name}, b.${clsElem.name})) return false")
                                        }
                                        else -> {
                                            appendLine("                if (a.${clsElem.name} != b.${clsElem.name}) return false")
                                        }
                                    }
                                }
                                is StatClsElemValueField -> {
                                    if (clsElem.typeName.startsWith("List")) {
                                        appendLine("                for ((aExp, bExp) in a.${clsElem.name}.zip(b.${clsElem.name}))")
                                        appendLine("                    if (!isSameExp(aExp, bExp)) return false")
                                    } else {
                                        appendLine("                if (!isSameExp(a.${clsElem.name}, b.${clsElem.name})) return false")
                                    }
                                }
                            }
                        }
                    }
                    appendLine("                return true")
                    appendLine("            }")
                }
            }
        }
        for ((clsName, src) in stats) {
            appendLine("            is $clsName -> {")
            appendLine("                b as $clsName")
            append(src)
            appendLine("                return true")
            appendLine("            }")
        }
        appendLine("        }")
        appendLine("    }")
        appendLine("")
        appendLine("    private fun isSameExp(a: Value, b: Value): Boolean {")
        appendLine("        if (a.javaClass !== b.javaClass) return false")
        appendLine("        return when (a) {")
        for (element in expElems) {
            when (element) {
                is ExpElemSectionComment -> {
                }
                is ExpElemSrc -> {
                }
                is ExpElemCls -> {
                    val clsName = element.name
                    appendLine("            is $clsName -> {")
                    appendLine("                b as $clsName")
                    if (clsName in stats) {
                        append(stats[clsName]!!)
                        stats.remove(clsName)
                    } else {
                        for (clsElem in element.elements) {
                            when (clsElem) {
                                is ExpClsElemSimpleField -> {
                                    when (clsElem.typeName) {
                                        tLabel -> {
                                            appendLine("                if (!isSameLabel(a.${clsElem.name}, b.${clsElem.name})) return false")
                                        }
                                        tLabels -> {
                                            appendLine("                for ((aLabel, bLabel) in a.${clsElem.name}.zip(b.${clsElem.name}))")
                                            appendLine("                    if (!isSameLabel(aLabel, bLabel)) return false")
                                        }
                                        tIntLabelPairs -> {
                                            appendLine("                for ((aPair, bPair) in a.${clsElem.name}.zip(b.${clsElem.name}))")
                                            appendLine("                    if (!isSameLabel(aPair.second, bPair.second)) return false")
                                        }
                                        tBlockBegin -> {
                                            appendLine("                if (!isSameBlock(a.${clsElem.name}, b.${clsElem.name})) return false")
                                        }
                                        else -> {
                                            appendLine("                if (a.${clsElem.name} != b.${clsElem.name}) return false")
                                        }
                                    }
                                }
                                is ExpClsElemValueField -> {
                                    if (clsElem.typeName.startsWith("List")) {
                                        appendLine("                for ((aExp, bExp) in a.${clsElem.name}.zip(b.${clsElem.name}))")
                                        appendLine("                    if (!isSameExp(aExp, bExp)) return false")
                                    } else {
                                        appendLine("                if (!isSameExp(a.${clsElem.name}, b.${clsElem.name})) return false")
                                    }
                                }
                            }
                        }
                    }
                    appendLine("                return true")
                    appendLine("            }")
                }
            }
        }
        for ((clsName, src) in exprs) {
            appendLine("            is $clsName -> {")
            appendLine("                b as $clsName")
            append(src)
            appendLine("                return true")
            appendLine("            }")
        }
        appendLine("        }")
        appendLine("    }")
        appendLine("")
        appendLine("    private fun isSameLabel(a: StatLabel, b: StatLabel): Boolean {")
        appendLine("        return false")
        appendLine("    }")
        appendLine("")
        appendLine("    private fun isSameBlock(a: BlockBeginStatement, b: BlockBeginStatement): Boolean {")
        appendLine("        return isSame(a.asSequence(), b.asSequence())")
        appendLine("    }")
        appendLine("}")
    }
}
