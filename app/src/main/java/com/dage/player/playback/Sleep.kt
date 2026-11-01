package com.dage.player.playback

import kotlinx.coroutines.flow.MutableStateFlow

/** 睡眠定时的共享状态（UI 与播放服务同进程） */
object Sleep {

    /** 定时到点的时间戳，null 表示没有定时 */
    val deadline = MutableStateFlow<Long?>(null)

    /** 播完当前集就停 */
    val pauseAtEnd = MutableStateFlow(false)

    fun remainingMs(): Long {
        val d = deadline.value ?: return 0
        return (d - System.currentTimeMillis()).coerceAtLeast(0)
    }
}

object PlaybackBridge {
    @Volatile
    var service: PlaybackService? = null

    fun setSleep(minutes: Int?, pauseAtEnd: Boolean) {
        service?.setSleep(minutes, pauseAtEnd)
    }
}
