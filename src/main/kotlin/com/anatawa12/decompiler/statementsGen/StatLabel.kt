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

    override fun toString(): String = "L_$id"

    companion object {
        private var nextId = 1
    }
}

interface IStatLabelConsumer
