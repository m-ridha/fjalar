package com.muhammadridha.fjalar.adapters.ringer

/** Owns one native player and at most one fallback; stale callbacks cannot replace newer audio. */
internal class AlarmAudioPlayback(
    private val createPlayer: () -> AlarmAudioPlayer,
    private val reportFailure: (String, Exception) -> Unit,
    private val onExhausted: () -> Unit = {},
) {
    private var player: AlarmAudioPlayer? = null
    private var generation = 0

    @Synchronized
    fun start(sources: List<String>, volume: Float) {
        stop()
        trySource(sources.distinct().take(2), 0, volume, generation)
    }

    @Synchronized
    fun stop() {
        generation++
        releaseCurrent()
    }

    @Synchronized
    fun setPaused(paused: Boolean) {
        try {
            player?.setPaused(paused)
        } catch (error: Exception) {
            reportFailure("Changing alarm playback focus state failed", error)
            releaseCurrent()
            onExhausted()
        }
    }

    private fun releaseCurrent() {
        val previous = player
        player = null
        try {
            previous?.release()
        } catch (error: Exception) {
            reportFailure("Releasing alarm player failed", error)
        }
    }

    private fun trySource(sources: List<String>, index: Int, volume: Float, owner: Int) {
        if (owner != generation) return
        if (index >= sources.size) {
            reportFailure("Alarm audio sources exhausted", IllegalStateException("No playable alarm tone"))
            onExhausted()
            return
        }
        var candidate: AlarmAudioPlayer? = null
        try {
            candidate = createPlayer()
            player = candidate
            candidate.start(sources[index], volume) {
                synchronized(this) {
                    if (generation == owner && player === candidate) {
                        reportFailure("Alarm player asynchronous failure", IllegalStateException("Media playback failed"))
                        releaseCurrent()
                        trySource(sources, index + 1, volume, owner)
                    }
                }
            }
        } catch (error: Exception) {
            reportFailure("Alarm audio attempt ${index + 1} failed", error)
            if (owner == generation) {
                releaseCurrent()
                trySource(sources, index + 1, volume, owner)
            }
        }
    }
}

/** Native resource boundary kept outside the domain. */
internal interface AlarmAudioPlayer {
    fun start(source: String, volume: Float, onError: () -> Unit)
    fun setPaused(paused: Boolean)
    fun release()
}
