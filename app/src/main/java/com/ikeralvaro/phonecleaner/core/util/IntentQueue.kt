package com.ikeralvaro.phonecleaner.core.util

/**
 * Cola para recorrer apps una a una (detención rápida, desinstalación en lote):
 * se abre la pantalla del sistema para el elemento actual y, al volver, se pasa al siguiente.
 */
class IntentQueue<T>(items: List<T> = emptyList()) {
    var items: List<T> = items
        private set
    var index: Int = -1
        private set

    val current: T? get() = items.getOrNull(index)
    val isRunning: Boolean get() = index in items.indices
    val position: Int get() = index + 1
    val size: Int get() = items.size

    fun start(newItems: List<T>): T? {
        items = newItems
        index = if (newItems.isEmpty()) -1 else 0
        return current
    }

    /** Avanza y devuelve el siguiente elemento, o null si ya no quedan. */
    fun next(): T? {
        if (!isRunning) return null
        index++
        if (index >= items.size) {
            index = items.size
            return null
        }
        return current
    }

    fun cancel() {
        index = -1
        items = emptyList()
    }
}
