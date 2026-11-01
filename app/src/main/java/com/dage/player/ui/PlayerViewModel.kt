package com.dage.player.ui

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.dage.player.data.Entry
import com.dage.player.data.Prefs
import com.dage.player.data.ProgressStore
import com.dage.player.data.Smb
import com.dage.player.playback.PlaybackBridge
import com.dage.player.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerState(
    val connected: Boolean = false,
    val title: String? = null,
    val subtitle: String? = null,
    val uri: String? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var ticker: Job? = null

    var lastError by mutableStateOf<String?>(null)
        private set

    init {
        connect()
    }

    private fun connect() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { c ->
                controller = c
                c.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        sync()
                    }
                })
                _state.update { it.copy(connected = true) }
                startTicker()
                sync()
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                tick()
                delay(500)
            }
        }
    }

    private fun tick() {
        val c = controller ?: return
        val item = c.currentMediaItem ?: return
        _state.update {
            it.copy(
                position = c.currentPosition.coerceAtLeast(0),
                duration = if (c.duration > 0) c.duration else ProgressStore.duration(item.mediaId),
            )
        }
    }

    private fun sync() {
        val c = controller ?: return
        val item = c.currentMediaItem
        _state.update {
            it.copy(
                title = item?.mediaMetadata?.title?.toString(),
                subtitle = item?.mediaMetadata?.artist?.toString(),
                uri = item?.mediaId,
                isPlaying = c.isPlaying,
                buffering = c.playbackState == Player.STATE_BUFFERING,
                position = c.currentPosition.coerceAtLeast(0),
                duration = if (c.duration > 0) c.duration else ProgressStore.duration(item?.mediaId.orEmpty()),
                hasNext = c.mediaItemCount > c.currentMediaItemIndex + 1,
                hasPrev = c.currentMediaItemIndex > 0,
            )
        }
    }

    /** 播放整个文件夹（自动连播），可指定从哪个文件开始 */
    fun playFolder(
        folderUrl: String,
        folderName: String,
        startFileUrl: String? = null,
        onStarted: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            lastError = null
            val files = runCatching {
                withContext(Dispatchers.IO) { Smb.list(folderUrl).filter { !it.isDir } }
            }.getOrElse { e ->
                lastError = e.message ?: "读取目录失败"
                return@launch
            }
            if (files.isEmpty()) {
                lastError = "这个文件夹里没有音频文件"
                return@launch
            }
            val index = files.indexOfFirst { it.url == startFileUrl }.let { if (it < 0) 0 else it }
            if (start(files, index, folderName)) onStarted?.invoke()
        }
    }

    fun resumeLast(onStarted: (() -> Unit)? = null) {
        val uri = ProgressStore.lastUri ?: return
        val folderUrl = uri.substringBeforeLast('/', "")
        playFolder(folderUrl, ProgressStore.lastSubtitle.orEmpty(), uri, onStarted)
    }

    private fun start(files: List<Entry>, index: Int, folderName: String): Boolean {
        val c = controller ?: return false
        val items = files.map { e ->
            MediaItem.Builder()
                .setUri(e.url)
                .setMediaId(e.url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(displayName(e.name))
                        .setArtist(folderName)
                        .build()
                )
                .build()
        }
        val position = ProgressStore.resumePosition(files[index].url)
        c.setMediaItems(items, index, position)
        c.prepare()
        c.play()
        return true
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3_000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0))
    }

    fun seekBy(delta: Long) {
        val c = controller ?: return
        c.seekTo((c.currentPosition + delta).coerceAtLeast(0))
    }

    fun setSleep(minutes: Int?, pauseAtEnd: Boolean) = PlaybackBridge.setSleep(minutes, pauseAtEnd)

    fun clearError() {
        lastError = null
    }

    fun showError(message: String) {
        lastError = message
    }

    override fun onCleared() {
        ticker?.cancel()
        controller?.release()
        controller = null
        super.onCleared()
    }
}

fun displayName(fileName: String): String {
    val i = fileName.lastIndexOf('.')
    return if (i > 0) fileName.substring(0, i) else fileName
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatLeft(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

fun currentFolder(): String = Prefs.nas()?.baseUrl.orEmpty()
