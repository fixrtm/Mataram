package com.anatawa12.decompiler.util

import java.util.*

fun <T> identitySetOf(): MutableSet<T> = Collections.newSetFromMap<T>(IdentityHashMap())

fun <T> sameOrEitherNullOrNull(a: T, b: T): T = when {
    a == null -> b
    b == null -> a
    a == b -> a
    else -> throw IllegalArgumentException("a and b are not same")
}

fun <T> unsafeLazy(initializer: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.NONE, initializer)
