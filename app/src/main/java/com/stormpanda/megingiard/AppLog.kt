package com.stormpanda.megingiard

import android.util.Log

private const val APPLOG_TAG_PREFIX = "Mgnrd"

/**
 * Unified logging facade for Megingiard.
 *
 * The active level is controlled at runtime via [SettingsManager.logLevel].
 * Messages below the active level are suppressed entirely, so there is zero
 * overhead in production at the default [Level.WARN] setting.
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
