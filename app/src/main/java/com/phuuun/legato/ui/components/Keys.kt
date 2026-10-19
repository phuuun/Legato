package com.phuuun.legato.ui.components

/**
 * Lazy-list keys for a list that can hold the same item twice (a song queued twice): the second 7 is "7#1".
 * A key stays with its item when the list is reordered, so rows can animate.
 */
fun <T> occurrenceKeys(items: List<T>, id: (T) -> Any): List<String> {
    val seen = HashMap<Any, Int>()
    return items.map { item ->
        val k = id(item)
        val n = seen.getOrDefault(k, 0)
        seen[k] = n + 1
        "$k#$n"
    }
}
