package com.anatawa12.decompiler.util

import kotlin.reflect.KProperty

class Property<T, out A> constructor(value: T, val thisRef: A, val type: Class<T>) {
    var value: T = value
        set(value) {
            onChange(field, value)
            field = value
        }
    var onChange: (from: T, to: T) -> Unit = { _, _ -> }

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun getValue(thisRef: Any?, property: KProperty<*>) = value

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    override fun toString(): String {
        return "Property<$type>(value=$value, this=$thisRef)"
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T1> castInAs(): Property<in T1, A>? {
        return if (type.isAssignableFrom(T1::class.java)) this as Property<in T1, A> else null
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T1> castAs(): Property<T1, A>? {
        return if (T1::class.java == type) this as Property<T1, A> else null
    }
}
