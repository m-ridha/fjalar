package com.muhammadridha.fjalar.adapters.ringer

/** Each native focus request receives its own callback; abandonment invalidates queued events. */
internal class AlarmAudioFocusOwnership(private val lock: Any = Any()) {
    private var generation = 0L

    fun beginRequest(onChange: (Int) -> Unit): (Int) -> Unit = synchronized(lock) {
        val owner = ++generation
        val callback: (Int) -> Unit = { change ->
            synchronized(lock) {
                if (owner == generation) onChange(change)
            }
        }
        callback
    }

    fun invalidate() {
        synchronized(lock) {
            generation++
        }
    }
}
