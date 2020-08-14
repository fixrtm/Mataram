package com.anatawa12.decompiler.instructions

import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.Type

interface InstructionVisitor {
    fun nop()
    fun const(value: Any?)
    fun load(variable: Int, type: StackType)
    fun aload(type: AllType)
    fun store(variable: Int, type: StackType)
    fun astore(type: AllType)
    fun pop()
    fun pop2()
    fun dup()
    fun dup2()
    fun dupX1()
    fun dupX2()
    fun dup2X1()
    fun dup2X2()
    fun swap()
    fun biOp(op: BiOp, type: NumericType)
    fun shiftOp(op: ShiftOp, type: NumericType)
    fun neg(type: NumericType)
    fun iinc(variable: Int, increment: Int)

    fun cast(cast: InsnConvert)
    fun lcmp()
    fun cmpl(type: NumericType)
    fun cmpg(type: NumericType)
    fun conditionalGoto(insnCondition: InsnCondition, label: Label)
    fun biConditionalGoto(conditionInsn: InsnBiCondition, label: Label)
    fun goTo(label: Label)

    fun jsr(label: Label)
    fun ret(variable: Int)
    fun tableswitch(min: Int, max: Int, default: Label, labels: List<Label>)
    fun lookupswitch(default: Label, pairs: List<Pair<Int, Label>>)
    fun areturn(type: StackType?)
    fun getstatic(owner: String, name: String, descriptor: String)
    fun putstatic(owner: String, name: String, descriptor: String)
    fun getfield(owner: String, name: String, descriptor: String)
    fun putfield(owner: String, name: String, descriptor: String)
    fun invokevirtual(owner: String, name: String, descriptor: String, isInterface: Boolean)
    fun invokespecial(owner: String, name: String, descriptor: String, isInterface: Boolean)
    fun invokestatic(owner: String, name: String, descriptor: String, isInterface: Boolean)
    fun invokeinterface(owner: String, name: String, descriptor: String)
    fun invokedynamic(
        name: String,
        descriptor: String,
        bootstrapMethodHandle: Handle,
        bootstrapMethodArguments: List<Any>,
    )

    fun anew(type: Type)
    fun newarray(type: Type)
    fun arraylength()
    fun athrow()
    fun checkcast(type: Type)
    fun instanceOf(type: Type)
    fun monitorenter()
    fun monitorexit()
    fun multianewarray(descriptor: String, numDimensions: Int)
    fun mark(label: Label)
    fun markLine(line: Int)

    fun frame(locals: List<FrameElement>, stacks: List<FrameElement>)
    fun localVariable(name: String, descriptor: String, signature: String?, start: Label, end: Label, index: Int)
    fun tryCatchBlock(start: Label, end: Label, handler: Label, catchesInternalName: String?)

    fun endInstructions()
}
