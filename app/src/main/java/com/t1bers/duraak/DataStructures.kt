package com.t1bers.duraak

/** Resizable array with geometric capacity growth. */
class DynamicArray<T> : AbstractMutableList<T>() {
    private var storage: Array<Any?> = arrayOfNulls(8)
    private var count = 0
    override val size: Int get() = count

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): T {
        checkIndex(index)
        return storage[index] as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun set(index: Int, element: T): T {
        checkIndex(index)
        val old = storage[index] as T
        storage[index] = element
        return old
    }

    override fun add(index: Int, element: T) {
        if (index !in 0..count) throw IndexOutOfBoundsException(index)
        ensureCapacity()
        for (i in count downTo index + 1) storage[i] = storage[i - 1]
        storage[index] = element
        count++
    }

    override fun removeAt(index: Int): T {
        checkIndex(index)
        @Suppress("UNCHECKED_CAST")
        val old = storage[index] as T
        for (i in index until count - 1) storage[i] = storage[i + 1]
        storage[--count] = null
        return old
    }

    override fun clear() {
        storage = arrayOfNulls(8)
        count = 0
    }

    private fun checkIndex(index: Int) {
        if (index !in 0 until count) throw IndexOutOfBoundsException(index)
    }

    private fun ensureCapacity() {
        if (count < storage.size) return
        storage = storage.copyOf(storage.size * 2)
    }
}

/** LIFO stack backed by DynamicArray. */
class Stack<T> {
    private val data = DynamicArray<T>()
    val size: Int get() = data.size
    val isEmpty: Boolean get() = data.isEmpty()
    fun push(value: T) { data.add(value) }
    fun pop(): T {
        check(data.isNotEmpty()) { "Stack is empty" }
        return data.removeAt(data.lastIndex)
    }
    fun peek(): T? = data.lastOrNull()
    fun toList(): List<T> = data.toList()
    fun clear() = data.clear()
}

/** Ring-buffer queue with O(1) enqueue/dequeue. */
class CircularQueue<T>(initialCapacity: Int = 8) {
    private var buffer: Array<Any?> = arrayOfNulls(initialCapacity.coerceAtLeast(1))
    private var head = 0
    var size: Int = 0
        private set
    val isEmpty: Boolean get() = size == 0

    fun enqueue(value: T) {
        if (size == buffer.size) grow()
        buffer[(head + size) % buffer.size] = value
        size++
    }

    @Suppress("UNCHECKED_CAST")
    fun dequeue(): T {
        check(size > 0) { "Queue is empty" }
        val value = buffer[head] as T
        buffer[head] = null
        head = (head + 1) % buffer.size
        size--
        return value
    }

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T {
        require(index in 0 until size)
        return buffer[(head + index) % buffer.size] as T
    }

    fun rotate(steps: Int = 1) {
        if (size == 0) return
        repeat(((steps % size) + size) % size) { enqueue(dequeue()) }
    }

    fun contains(value: T): Boolean = (0 until size).any { get(it) == value }

    fun remove(value: T) {
        val kept = (0 until size).map { get(it) }.filter { it != value }
        clear()
        kept.forEach(::enqueue)
    }

    fun toList(): List<T> = (0 until size).map { get(it) }
    fun clear() {
        buffer = arrayOfNulls(buffer.size)
        head = 0
        size = 0
    }

    private fun grow() {
        val next = arrayOfNulls<Any?>(buffer.size * 2)
        for (i in 0 until size) next[i] = buffer[(head + i) % buffer.size]
        buffer = next
        head = 0
    }
}

/** Separate-chaining hash table for integer keys and integer values. */
class IntHashTable(initialCapacity: Int = 16) {
    private data class Entry(val key: Int, var value: Int)
    private var buckets = Array(initialCapacity.coerceAtLeast(2)) { mutableListOf<Entry>() }
    var size: Int = 0
        private set

    private fun slot(key: Int): Int = (key * 0x9E3779B9).ushr(1) % buckets.size

    fun get(key: Int): Int? = buckets[slot(key)].firstOrNull { it.key == key }?.value
    fun contains(key: Int): Boolean = get(key) != null

    fun set(key: Int, value: Int) {
        val bucket = buckets[slot(key)]
        val found = bucket.firstOrNull { it.key == key }
        if (found != null) found.value = value else {
            bucket.add(Entry(key, value))
            size++
            if (size > buckets.size * 2) resize()
        }
    }

    fun increment(key: Int) = set(key, (get(key) ?: 0) + 1)

    fun remove(key: Int): Boolean {
        val bucket = buckets[slot(key)]
        val removed = bucket.removeAll { it.key == key }
        if (removed) size--
        return removed
    }

    fun clear() {
        buckets = Array(16) { mutableListOf<Entry>() }
        size = 0
    }

    private fun resize() {
        val old = buckets
        buckets = Array(old.size * 2) { mutableListOf<Entry>() }
        size = 0
        old.forEach { bucket -> bucket.forEach { set(it.key, it.value) } }
    }
}
