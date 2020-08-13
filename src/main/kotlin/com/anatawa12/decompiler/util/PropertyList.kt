package com.anatawa12.decompiler.util

class PropertyList<T, A>(val thisRef: A, private val list: List<Property<T, A>>) : MutableList<T> {
    init {
        for (property in list) {
            require(property.thisRef === thisRef) { "some property has another thisRef" }
        }
    }

    override val size: Int get() = list.size

    override fun contains(element: T): Boolean = list.find { it.value == element } != null

    override fun containsAll(elements: Collection<T>): Boolean = elements.all { it in this }

    override fun get(index: Int): T = list[index].value

    override fun indexOf(element: T): Int = list.indexOfFirst { it.value == element }

    override fun isEmpty(): Boolean = list.isEmpty()

    override fun iterator(): MutableIterator<T> = listIterator()

    override fun lastIndexOf(element: T): Int = list.indexOfLast { it.value == element }

    override fun listIterator(): MutableListIterator<T> = listIterator(0)

    override fun listIterator(index: Int): MutableListIterator<T> = PropertyListMutableListIterator(this, index)

    override fun set(index: Int, element: T): T {
        val prop = list[index]
        val old = prop.value
        prop.value = element
        return old
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> =
        PropertyList(thisRef, list.subList(fromIndex, toIndex))

    // unsupported modifications: changing the size of list
    override fun add(element: T): Boolean = throw UnsupportedOperationException("add")

    override fun add(index: Int, element: T) = throw UnsupportedOperationException("add")

    override fun addAll(index: Int, elements: Collection<T>): Boolean = throw UnsupportedOperationException("add")

    override fun addAll(elements: Collection<T>): Boolean = throw UnsupportedOperationException("add")

    override fun clear() = throw UnsupportedOperationException("clear")

    override fun remove(element: T): Boolean = throw UnsupportedOperationException("remove")

    override fun removeAll(elements: Collection<T>): Boolean = throw UnsupportedOperationException("remove")

    override fun removeAt(index: Int): T = throw UnsupportedOperationException("add")

    override fun retainAll(elements: Collection<T>): Boolean = throw UnsupportedOperationException("add")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is List<*>) return false

        if (other.size != size) return false

        for (i in indices) {
            if (other[i] != this[i]) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var hashCode = 1
        for (e in this) hashCode = 31 * hashCode + (e?.hashCode() ?: 0)
        return hashCode
    }

    override fun toString(): String = joinToString(", ", "[", "]")

    private class PropertyListMutableListIterator<T>(val list: PropertyList<T, *>, var cursor: Int) :
        MutableListIterator<T> {
        private var wasAction = NO
        override fun hasPrevious(): Boolean = 0 < cursor

        override fun nextIndex(): Int = cursor

        override fun previous(): T {
            if (cursor == 0) throw NoSuchElementException()
            wasAction = PREVIOUS
            return list[--cursor]
        }

        override fun previousIndex(): Int = cursor - 1

        override fun hasNext(): Boolean = cursor != list.size

        override fun next(): T {
            if (cursor == list.size) throw NoSuchElementException()
            wasAction = NEXT
            return list[cursor++]
        }

        override fun set(element: T) {
            if (wasAction == PREVIOUS)
                list[cursor] = element
            if (wasAction == NEXT)
                list[cursor - 1] = element
        }

        override fun add(element: T) = throw UnsupportedOperationException("add")

        override fun remove() = throw UnsupportedOperationException("remove")

        companion object {
            private const val NO = 0
            private const val PREVIOUS = 1
            private const val NEXT = 2
        }
    }
}
