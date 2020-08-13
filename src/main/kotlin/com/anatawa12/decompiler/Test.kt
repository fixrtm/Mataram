package com.anatawa12.decompiler

import com.anatawa12.decompiler.instructions.InstructionConvertor
import com.anatawa12.decompiler.optimizer.expressions.*
import com.anatawa12.decompiler.optimizer.statements.*
import com.anatawa12.decompiler.processor.OptimizeProcessor
import com.anatawa12.decompiler.processor.PrintingProcessor
import com.anatawa12.decompiler.processor.ProcessorContext
import com.anatawa12.decompiler.processor.SetLocalVariableNameProcessor
import com.anatawa12.decompiler.signatures.SClassLoaderEnvironment
import com.anatawa12.decompiler.signatures.URLSClassLoader
import com.anatawa12.decompiler.statementsGen.StatementsGenerator
import com.anatawa12.decompiler.statementsGen.StatementsMethod
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.net.URL
import java.util.zip.ZipFile

fun main(args: Array<String>) = Test().main(args)

class Test : CliktCommand() {
    val classPath: List<String> by option("-c", "--classpath", help = "class path urls.")
        .multiple()

    val javaHome by option("--home", help = "JRE or JDK Home")
        .default(System.getProperty("java.home"), defaultForHelp = "JAVA_HOME")

    val classOrJars by argument("classes directory or jar files")
        .file(mustExist = true)
        .multiple()

    override fun run() {
        val cp = classPath.toMutableList()
        for (classOrJar in classOrJars) {
            cp += if (classOrJar.isFile) {
                "jar:" + classOrJar.toURI().toString() + "!/"
            } else {
                classOrJar.toURI().toString()
            }
        }

        val sEnv = SClassLoaderEnvironment(javaHome)
        val scl = URLSClassLoader(sEnv, cp.map(::URL))

        val ctx = ProcessorContext(sEnv, scl)
        val methods = mutableListOf<StatementsMethod>()

        for (argFile in classOrJars) {
            if (argFile.isFile) {
                val zip = ZipFile(argFile)
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    if (!entry.name.endsWith(".class")) continue
                    methods += generateForClass(zip.getInputStream(entry).readBytes(), ctx)
                }
            } else {
                for (file in argFile.walkTopDown()) {
                    if (!file.isFile) continue
                    methods += generateForClass(file.readBytes(), ctx)
                }
            }
        }
    }
}

fun generateForClass(byteArray: ByteArray, ctx: ProcessorContext): List<StatementsMethod> {
    val node = ClassNode()
    ClassReader(byteArray).accept(node, 0)
    return generateForClassNode(node, ctx)
}

fun generateForClassNode(node: ClassNode, ctx: ProcessorContext) = node.methods.map {
    generateForMethodNode(node, it, ctx)
}

fun generateForMethodNode(node: ClassNode, method: MethodNode, ctx: ProcessorContext): StatementsMethod {
    val generator = StatementsGenerator()
    val convertor = InstructionConvertor(node.name, method.access, method.name, method.desc, generator)

    if (node.name == "jp/ngt/ngtlib/event/TickProcessQueue\$DelayProcessEntry"
            && method.name == "process"
            && method.desc == "(Lnet/minecraft/world/World;)Z") {
        print("")
    }
    method.accept(convertor)

    println("/// method: ${node.name}.${method.name}:${method.desc}")

    val statementOptimizer = listOf(
            GetStaticWithSelfStatementsOptimizer,
            InvokeStaticValueWithSelfStatementsOptimizer,
            InvokeStaticVoidWithSelfStatementsOptimizer,

            NullCheckedValueStatementsOptimizer,

            SuffixInDecrementStatementsOptimizer,
            SuffixInDecrementWithIIncStatementsOptimizer,
            PrefixLocalInDecrementStatementsOptimizer,

            NewObjectStatementsOptimizer,

            AssignAndUseValueToLocalStatementsOptimizer,
            AssignAndUseValueStatementsOptimizer,

            BooleanAndAndOperationStatementsOptimizer,
            MakeCatchBlockStartSetsLocalVariableStatementsOptimizer,
            ConditionalOperatorStatementsOptimizer,
            ConditionalOperatorAfterGotoStatementsOptimizer,
            BooleanOrOrOperationStatementsOptimizer,
            MoveTryBlockEndToNextToGotoLikeStatementsOptimizer,
            NewArrayWithInitializerStatementsOptimizer,

            SingleConsumerSingleProducerStackValueStatementsOptimizer,
            SingleConsumerSingleProducerStackValueFromStackVariableStatementsOptimizer,
            MultiConsumerSingleProducerStackValueStatementsOptimizer
    )

    val expressionOptimizer = listOf<IExpressionOptimizer>(
            BiOperationAssignExpressionOptimizer,
            ShiftOperationAssignExpressionOptimizer,

            EqOrNeToZeroWithBoolOptimizer,

            FloatingCompareExpressionOptimizer,
            LongCompareExpressionOptimizer,

            DeMorganExpressionOptimizer
    )
// 3546 -> 2052
    val processors = listOf(
            SetLocalVariableNameProcessor(),
            OptimizeProcessor(statementOptimizer, expressionOptimizer),
            PrintingProcessor("// optimized", showDetailed = false),
    )

    val statementsMethod = generator.getStatementsMethod()

    for (processor in processors) {
        processor.process(statementsMethod, ctx)
    }

    return statementsMethod
    /*
    for (statement in stats) {
        println(statement)
    }
     */
}