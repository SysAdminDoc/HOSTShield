package com.hostshield.util

import android.util.Log
import com.hostshield.BuildConfig

object PrivacyLog {
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg)
    }

    fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.w(tag, msg)
    }

    fun e(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.e(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        if (BuildConfig.DEBUG) Log.e(tag, msg, tr)
    }

    fun w(tag: String, msg: String, tr: Throwable) {
        if (BuildConfig.DEBUG) Log.w(tag, msg, tr)
    }
}
