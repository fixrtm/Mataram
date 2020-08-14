package com.anatawa12.decompiler.instructions

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.InstructionAdapter
import org.objectweb.asm.tree.MethodNode

internal class InstructionConvertorTest : StringSpec() {
    init {
        val visitor = mockk<InstructionVisitor>()

        every { visitor.const(any()) } returns Unit
        every { visitor.frame(any(), any()) } returns Unit
        every { visitor.endInstructions() } returns Unit

        "pop long" {
            val node = makeNode { convertor ->
                convertor.lconst(10)
                convertor.pop()
            }
            shouldThrow<IllegalStateException> {
                InstructionConvertor.convertFromNode("", node, visitor)
            }
        }

        "pop double" {
            val node = makeNode { convertor ->
                convertor.dconst(10.0)
                convertor.pop()
            }
            shouldThrow<IllegalStateException> {
                InstructionConvertor.convertFromNode("", node, visitor)
            }
        }

        "pop2 long" {
            every { visitor.pop() } returns Unit
            val node = makeNode { convertor ->
                convertor.lconst(10)
                convertor.pop2()
            }
            InstructionConvertor.convertFromNode("", node, visitor)
            verify {
                visitor.pop()
            }
        }

        "pop2 double" {
            every { visitor.pop() } returns Unit
            val node = makeNode { convertor ->
                convertor.dconst(10.0)
                convertor.pop2()
            }
            InstructionConvertor.convertFromNode("", node, visitor)
            verify {
                visitor.pop()
            }
        }
    }

    private fun makeNode(block: (convertor: InstructionAdapter) -> Unit): MethodNode {
        val node = MethodNode(Opcodes.ACC_STATIC, "", "()V", null, null)
        node.visitCode()
        block(InstructionAdapter(node))
        node.visitMaxs(0, 0)
        return node
    }
}
