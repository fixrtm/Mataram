package com.anatawa12.decompiler.instructions

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.objectweb.asm.Opcodes
import java.lang.IllegalStateException

internal class InstructionConvertorTest : StringSpec() {
    init {
        val visitor = mockk<InstructionVisitor>()
        val convertor = InstructionConvertor("", Opcodes.ACC_STATIC, "", "()V", visitor)

        every { visitor.const(any()) } returns Unit

        "pop long" {
            shouldThrow<IllegalStateException> {
                convertor.lconst(10)
                convertor.pop()
            }
        }

        "pop double" {
            shouldThrow<IllegalStateException> {
                convertor.dconst(10.0)
                convertor.pop()
            }
        }

        "pop2 long" {
            every { visitor.pop() } returns Unit
            convertor.lconst(10)
            convertor.pop2()
            verify {
                visitor.pop()
            }
        }

        "pop2 double" {
            every { visitor.pop() } returns Unit
            convertor.dconst(10.0)
            convertor.pop2()
            verify {
                visitor.pop()
            }
        }
    }
}
