package com.anatawa12.decompiler.util

infix fun <T, R> Sequence<T>.zipEither(other: Sequence<R>): Sequence<Pair<T?, R?>> {
    return ZipEitherSequence(this, other) { t1, t2 -> t1 to t2 }
}

internal class ZipEitherSequence<T1, T2, V>
constructor(
    private val sequence1: Sequence<T1>,
    private val sequence2: Sequence<T2>,
    private val transform: (T1?, T2?) -> V
) : Sequence<V> {
    override fun iterator(): Iterator<V> = object : Iterator<V> {
        val iterator1 = sequence1.iterator()
        val iterator2 = sequence2.iterator()
        override fun next(): V {
            return transform(
                if (iterator1.hasNext()) iterator1.next() else null,
                if (iterator2.hasNext()) iterator2.next() else null
            )
        }

        override fun hasNext(): Boolean {
            return iterator1.hasNext() || iterator2.hasNext()
        }
    }
}
