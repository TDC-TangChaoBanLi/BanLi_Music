package com.banlimusic.app.core.common

import java.util.LinkedHashMap

/** Thread-safe LRU cache with an absolute TTL. */
class TtlCache<K, V>(
    private val maxEntries: Int,
    private val ttlMs: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val entries = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (entry.expiresAt <= now()) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = Entry(value, now() + ttlMs)
    }

    @Synchronized
    fun remove(key: K) {
        entries.remove(key)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
