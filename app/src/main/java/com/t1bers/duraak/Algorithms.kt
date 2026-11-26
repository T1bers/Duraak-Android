package com.t1bers.duraak

import kotlin.random.Random

/** Sorting and shuffling algorithms used by the card engine and benchmark. */
object Sorts {
    /** Stable merge sort: O(n log n) time and O(n) auxiliary space. */
    fun <T> mergeSort(items: List<T>, key: (T) -> Int): List<T> {
        if (items.size < 2) return items.toList()
        val middle = items.size / 2
        val left = mergeSort(items.subList(0, middle), key)
        val right = mergeSort(items.subList(middle, items.size), key)
        val result = ArrayList<T>(items.size)
        var i = 0
        var j = 0
        while (i < left.size && j < right.size) {
            if (key(left[i]) <= key(right[j])) result.add(left[i++]) else result.add(right[j++])
        }
        while (i < left.size) result.add(left[i++])
        while (j < right.size) result.add(right[j++])
        return result
    }

    /** Stable counting sort for integer keys in 0 until range. */
    fun <T> countingSort(items: List<T>, range: Int, key: (T) -> Int): List<T> {
        require(range > 0)
        val counts = IntArray(range)
        items.forEach { counts[key(it).also { k -> require(k in 0 until range) }]++ }
        for (i in 1 until range) counts[i] += counts[i - 1]
        val output = arrayOfNulls<Any?>(items.size)
        for (i in items.indices.reversed()) {
            val item = items[i]
            val k = key(item)
            output[--counts[k]] = item
        }
        @Suppress("UNCHECKED_CAST")
        return output.map { it as T }
    }

    /** Fisher–Yates shuffle: O(n) time, unbiased for a uniform RNG. */
    fun <T> fisherYates(items: MutableList<T>, random: Random = Random.Default) {
        for (i in items.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = items[i]
            items[i] = items[j]
            items[j] = temp
        }
    }
}

object AlgorithmBenchmark {
    fun run(): String {
        val output = StringBuilder("Merge Sort vs Counting Sort\nAverage of 10 runs\n\n")
        for (n in listOf(36, 1_000, 10_000)) {
            val base = List(n) { Random.nextInt(36) }
            var mergeNanos = 0L
            var countingNanos = 0L
            repeat(10) {
                var start = System.nanoTime()
                val merge = Sorts.mergeSort(base) { it }
                mergeNanos += System.nanoTime() - start
                start = System.nanoTime()
                val counting = Sorts.countingSort(base, 36) { it }
                countingNanos += System.nanoTime() - start
                check(merge == counting)
            }
            output.append("n=$n\n")
            output.append("  merge: %.3f ms\n".format(mergeNanos / 10.0 / 1_000_000.0))
            output.append("  counting: %.3f ms\n\n".format(countingNanos / 10.0 / 1_000_000.0))
        }
        return output.toString()
    }
}
