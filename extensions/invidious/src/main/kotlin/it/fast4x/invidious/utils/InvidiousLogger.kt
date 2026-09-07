package it.fast4x.invidious.utils

object InvidiousLogger {

    fun interface Listener {
        fun onLog(tag: String, level: Level, message: String, throwable: Throwable?)
    }

    enum class Level { DEBUG, INFO, WARN, ERROR }

    private val listeners = mutableListOf<Listener>()

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun d(tag: String, message: String) = log(tag, Level.DEBUG, message)
    fun i(tag: String, message: String) = log(tag, Level.INFO, message)
    fun w(tag: String, message: String) = log(tag, Level.WARN, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(tag, Level.ERROR, message, throwable)

    private fun log(tag: String, level: Level, message: String, throwable: Throwable? = null) {
        if (listeners.isEmpty()) {
            println("[$tag] $level: $message")
            throwable?.printStackTrace()
            return
        }
        listeners.forEach { it.onLog(tag, level, message, throwable) }
    }
}
