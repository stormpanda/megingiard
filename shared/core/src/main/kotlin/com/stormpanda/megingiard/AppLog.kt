package com.stormpanda.megingiard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.reflect.Method

private const val APPLOG_TAG_PREFIX = "Mgnrd"
private const val PRIORITY_VERBOSE = 2
private const val PRIORITY_DEBUG = 3
private const val PRIORITY_INFO = 4
private const val PRIORITY_WARN = 5
private const val PRIORITY_ERROR = 6

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
    enum class Level(
        val priority: Int,
    ) {
        VERBOSE(PRIORITY_VERBOSE),
        DEBUG(PRIORITY_DEBUG),
        INFO(PRIORITY_INFO),
        WARN(PRIORITY_WARN),
        ERROR(PRIORITY_ERROR),
        NONE(Int.MAX_VALUE),
    }

    private val _levelFlow = MutableStateFlow(Level.WARN)
    val levelFlow: StateFlow<Level> = _levelFlow.asStateFlow()

    @Volatile
    var level: Level = Level.WARN
        set(value) {
            field = value
            _levelFlow.value = value
        }

    private val logMethod: Method? by lazy {
        try {
            val clazz = Class.forName("android.util.Log")
            clazz.getMethod("println", Int::class.javaPrimitiveType, String::class.java, String::class.java)
        } catch (_: Throwable) {
            null
        }
    }

    private fun log(
        target: Level,
        tag: String,
        msg: String,
    ) {
        if (level > target) return
        val fullTag = "$APPLOG_TAG_PREFIX.$tag"
        val m = logMethod
        if (m != null) {
            try {
                m.invoke(null, target.priority, fullTag, msg)
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
        log(Level.VERBOSE, tag, msg)
    }

    fun d(
        tag: String,
        msg: String,
    ) {
        log(Level.DEBUG, tag, msg)
    }

    fun i(
        tag: String,
        msg: String,
    ) {
        log(Level.INFO, tag, msg)
    }

    fun w(
        tag: String,
        msg: String,
    ) {
        log(Level.WARN, tag, msg)
    }

    fun e(
        tag: String,
        msg: String,
    ) {
        log(Level.ERROR, tag, msg)
    }

    fun e(
        tag: String,
        msg: String,
        tr: Throwable,
    ) {
        if (level <= Level.ERROR) {
            log(Level.ERROR, tag, "$msg\n${tr.stackTraceToString()}")
        }
    }
}
