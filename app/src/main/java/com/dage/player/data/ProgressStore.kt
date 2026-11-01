package com.dage.player.data

import android.content.Context
import android.content.SharedPreferences

/** 记录每个文件的播放位置，用于断点续播 */
object ProgressStore {

    private lateinit var sp: SharedPreferences

    @Synchronized
    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("dage_progress", Context.MODE_PRIVATE)
    }

    fun save(uri: String, positionMs: Long, durationMs: Long, title: String?, subtitle: String?) {
        if (uri.isBlank()) return
        sp.edit()
            .putLong("p:$uri", positionMs)
            .putLong("d:$uri", durationMs)
            .putString("last", uri)
            .putLong("lastPos", positionMs)
            .putString("lastTitle", title)
            .putString("lastSub", subtitle)
            .apply()
    }

    fun position(uri: String): Long = sp.getLong("p:$uri", 0L)

    fun duration(uri: String): Long = sp.getLong("d:$uri", 0L)

    fun fraction(uri: String): Float {
        val d = duration(uri)
        if (d <= 0) return 0f
        return (position(uri).toFloat() / d).coerceIn(0f, 1f)
    }

    /** 剩余不足 5 秒视为听完了，从头开始 */
    fun resumePosition(uri: String): Long {
        val p = position(uri)
        val d = duration(uri)
        if (p > 0 && d > 0 && p > d - 5000) return 0L
        if (p < 3000) return 0L
        return p
    }

    val lastUri: String? get() = sp.getString("last", null)

    val lastTitle: String? get() = sp.getString("lastTitle", null)

    val lastSubtitle: String? get() = sp.getString("lastSub", null)

    fun lastPosition(): Long = sp.getLong("lastPos", 0L)

    fun count(): Int = sp.all.keys.count { it.startsWith("p:") }

    fun clear() {
        sp.edit().clear().apply()
    }
}
