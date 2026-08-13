package com.stormpanda.megingiard

private const val APPLOG_TAG_PREFIX = "Mgnrd"

/**
 * Unified logging facade for Megingiard.
 *
 * The active log level is controlled at runtime via [AppLog.level].
 * Messages below the active level are suppressed.
 *
 * Tags are automatically prefixed with "Mgnrd." so all app log lines are
 * easily filterable in logcat with `tag:Mgnrd`.
 */
object AppLog {
    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR, NONE }

    @Volatile var level: Level = Level.DEBUG

    private val logMethod: java.lang.reflect.Method? by lazy {
        try {
            val clazz = Class.forName("android.util.Log")
            clazz.getMethod("println", Int::class.javaPrimitiveType, String::class.java, String::class.java)
        } catch (_: Throwable) {
            null
        }
    }

    private val logThrowableMethod: java.lang.reflect.Method? by lazy {
        try {
            val clazz = Class.forName("android.util.Log")
            clazz.getMethod("e", String::class.java, String::class.java, Throwable::class.java)
        } catch (_: Throwable) {
            null
        }
    }

    private fun log(
        priority: Int,
        tag: String,
        msg: String,
    ) {
        val fullTag = "$APPLOG_TAG_PREFIX.$tag"
        val m = logMethod
        if (m != null) {
            try {
                m.invoke(null, priority, fullTag, msg)
                return
            } catch (_: Throwable) {
            }
        }
        println("$fullTag: $msg")
    }

    fun v(
        tag: String,
        msg: String,
    ) {
        if (level <= Level.VERBOSE) log(2, tag, msg) // 2 = Log.VERBOSE
    }

    fun d(
        tag: String,
        msg: String,
    ) {
        if (level <= Level.DEBUG) log(3, tag, msg) // 3 = Log.DEBUG
    }

    fun i(
        tag: String,
        msg: String,
    ) {
        if (level <= Level.INFO) log(4, tag, msg) // 4 = Log.INFO
    }

    fun w(
        tag: String,
        msg: String,
    ) {
        if (level <= Level.WARN) log(5, tag, msg) // 5 = Log.WARN
    }

    fun e(
        tag: String,
        msg: String,
    ) {
        if (level <= Level.ERROR) log(6, tag, msg) // 6 = Log.ERROR
    }

    fun e(
        tag: String,
        msg: String,
        tr: Throwable,
    ) {
        if (level <= Level.ERROR) {
            val fullTag = "$APPLOG_TAG_PREFIX.$tag"
            val m = logThrowableMethod
            if (m != null) {
                try {
                    m.invoke(null, fullTag, msg, tr)
                    return
                } catch (_: Throwable) {
                }
            }
            println("$fullTag: $msg\n${tr.stackTraceToString()}")
        }
    }
}
