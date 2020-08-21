package com.anatawa12.decompiler.generator

import java.io.File

fun main(args: Array<String>) {
    val templateType = args[0]
    if (templateType == "processesFile") {
        File(args[1]).forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            println("running $line")
            doMain(line.split(' '))
        }
    } else {
        doMain(args.asList())
    }
}

fun doMain(args: List<String>) {
    val templateType = args[0]
    val resultFileName = args[1]

    val file = generators[templateType]
        .let { it ?: error("generator for $templateType not found") }
        .invoke(args.drop(2))

    File(resultFileName)
        .apply { parentFile.mkdirs() }
        .writeText(file)
}

val generators: Map<String, (List<String>) -> String> = mapOf(
    "statement" to ::generateStatement,
    "expression" to ::generateExpression,
    "statement_comparator" to ::generateStatementComparator,
)
