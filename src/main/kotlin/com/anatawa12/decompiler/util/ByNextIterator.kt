package com.anatawa12.decompiler.util

fun <T> T.nextSequence(next: (T) -> T): Sequence<T> = nextSequence(isEnd = { true }, next = next)

/**
 * end is exclusive
 */
fun <T> T.nextSequence(end: T, next: (T) -> T): Sequence<T> = nextSequence(isEnd = { it != end }, next = next)

/**
 * end is exclusive
 */
fun <T : Any> T.nextSequence(end: Nothing?, next: (T) -> T?): Sequence<T> =
    (this as T?).nextSequence(isEnd = { it != null }, next = { next(it as T) }) as Sequence<T>

/**
 * end is exclusive
 */
fun <T> T.nextSequence(isEnd: (T) -> Boolean, next: (T) -> T): Sequence<T> = object : Sequence<T> {
    override fun iterator(): Iterator<T> = object : Iterator<T> {
        var cur = this@nextSequence
        override fun hasNext(): Boolean = isEnd(cur)

        override fun next(): T {
            if (!hasNext()) throw NoSuchElementException()
            return cur.also { cur = next(it) }
        }
    }
}
