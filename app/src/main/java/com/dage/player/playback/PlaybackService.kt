package com.dage.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dage.player.MainActivity
import com.dage.player.data.ProgressStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var sleepJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(SmbDataSourceFactory()))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // 缓冲上限 90 秒：网络抖动时更抗卡，起播仍只要 1 秒
                    .setBufferDurationsMs(30_000, 90_000, 1_000, 2_000)
                    .build()
            )
            .build()

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()

        PlaybackBridge.service = this

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                saveProgress()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) saveProgress()
            }
        })

        progressJob = scope.launch {
            while (isActive) {
                saveProgress()
                delay(4_000)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    private fun saveProgress() {
        val item = player.currentMediaItem ?: return
        val pos = player.currentPosition
        val dur = player.duration
        if (pos <= 0 && dur <= 0) return
        ProgressStore.save(
            uri = item.mediaId,
            positionMs = pos.coerceAtLeast(0),
            durationMs = dur.coerceAtLeast(0),
            title = item.mediaMetadata.title?.toString(),
            subtitle = item.mediaMetadata.artist?.toString(),
        )
    }

    fun setSleep(minutes: Int?, pauseAtEnd: Boolean) {
        sleepJob?.cancel()
        sleepJob = null
        player.setPauseAtEndOfMediaItems(pauseAtEnd)
        Sleep.pauseAtEnd.value = pauseAtEnd
        if (minutes == null) {
            Sleep.deadline.value = null
            return
        }
        Sleep.deadline.value = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            player.pause()
            Sleep.deadline.value = null
        }
    }

    override fun onDestroy() {
        saveProgress()
        if (PlaybackBridge.service === this) PlaybackBridge.service = null
        progressJob?.cancel()
        sleepJob?.cancel()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
