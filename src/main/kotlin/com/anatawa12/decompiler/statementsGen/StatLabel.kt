package com.anatawa12.decompiler.statementsGen

import org.objectweb.asm.Label

class StatLabel(val real: Label) {
    val usedBy = mutableListOf<IStatLabelConsumer>()
    var at: Statement? = null
        set(value) {
            if (value != null)
                check(field == null)
            field = value
        }
    val id = (nextId++).toString().padStart(5, '0')

    var sortingIndex = Int.MIN_VALUE
        get() = field.takeUnless { it == Int.MIN_VALUE }
            ?: throw UninitializedPropertyAccessException("sortingIndex is not initialized")
        set(value) {
            if (value == Int.MIN_VALUE)
                throw IllegalArgumentException("Int.MIN_VALUE is not allowed for sortingIndex")
            field = value
        }

    override fun toString(): String = "L_$id"

    companion object {
        private var nextId = 1
    }
}

interface IStatLabelConsumer
