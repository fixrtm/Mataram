package com.anatawa12.decompiler.statementsGen

import java.util.concurrent.atomic.AtomicInteger


class TryCatchBlockIdentifier(
    val catchesInternalName: String?,
    val tryStartLabel: StatLabel,
    val tryEndLabel: StatLabel,
    val catchStartLabel: StatLabel,
) {
    var tryStart: TryBlockStart? = null
        set(value) {
            check(field == null)
            check(value != null)
            field = value
        }
    var tryEnd: TryBlockEnd? = null
        set(value) {
            check(field == null)
            check(value != null)
            field = value
        }
    var catchStart: CatchBlockStart? = null
        set(value) {
            check(field == null)
            check(value != null)
            field = value
        }

    override fun toString(): String {
        return "TryCatchBlockIdentifier($id)"
    }

    val id = nextId.getAndIncrement().toString().padStart(5, '0')

    companion object {
        private val nextId = AtomicInteger(0)
    }
}
