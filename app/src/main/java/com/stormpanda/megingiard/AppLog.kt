package com.stormpanda.megingiard

import android.util.Log

private const val APPLOG_TAG_PREFIX = "Mgnrd"

/**
 * Unified logging facade for Megingiard.
 *
 * The active log level is controlled at runtime via [SettingsManager.logLevel]
 * (persisted in DataStore). Messages below the active level are suppressed
 * before the [android.util.Log] call, but note that string interpolation in
 * the message argument is always eagerly evaluated at the call site. For
 * high-frequency call paths this is acceptable since such paths must not be
 * logged at all (see AGENTS.md §5.4).
 *
 * Tags are automatically prefixed with "Mgnrd." so all app log lines are
 * easily filterable in logcat with `tag:Mgnrd`.
 */
object AppLog {

    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR, NONE }

    @Volatile var level: Level = Level.WARN

    fun v(tag: String, msg: String) {
        if (level <= Level.VERBOSE) Log.v("$APPLOG_TAG_PREFIX.$tag", msg)
    }

    fun d(tag: String, msg: String) {
        if (level <= Level.DEBUG) Log.d("$APPLOG_TAG_PREFIX.$tag", msg)
    }

    fun i(tag: String, msg: String) {
        if (level <= Level.INFO) Log.i("$APPLOG_TAG_PREFIX.$tag", msg)
    }

    fun w(tag: String, msg: String) {
        if (level <= Level.WARN) Log.w("$APPLOG_TAG_PREFIX.$tag", msg)
    }

    fun e(tag: String, msg: String) {
        if (level <= Level.ERROR) Log.e("$APPLOG_TAG_PREFIX.$tag", msg)
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        if (level <= Level.ERROR) Log.e("$APPLOG_TAG_PREFIX.$tag", msg, tr)
    }
}
