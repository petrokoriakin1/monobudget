package com.github.smaugfm.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class ExpiringMap<T : Any, K>(
    val expirationTime: Duration,
    private val map: ConcurrentHashMap<T, K> = ConcurrentHashMap()
) : Map<T, K> by map {
    private val coroutineScope =
        CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())

    fun add(item: T, value: K) {
        map[item] = value
        coroutineScope.launch {
            delay(expirationTime.toJavaDuration())
            map.remove(item)
        }
    }
}
