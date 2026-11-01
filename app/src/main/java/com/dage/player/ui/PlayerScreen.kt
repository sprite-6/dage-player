package com.dage.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dage.player.data.Prefs
import com.dage.player.playback.Sleep
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val ps by vm.state.collectAsState()
    val deadline by Sleep.deadline.collectAsState()
    val pauseAtEnd by Sleep.pauseAtEnd.collectAsState()
    var showSleep by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }

    val duration = ps.duration.coerceAtLeast(0L)
    val sliderValue = dragging ?: if (duration > 0) (ps.position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownPosition = if (dragging != null) (dragging!! * duration).toLong() else ps.position

    val remaining by produceState(0L, deadline) {
        while (deadline != null) {
            value = Sleep.remainingMs()
            delay(1000)
        }
        value = 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        ScreenHeader(title = "正在播放", onBack = onBack) {
            IconButton(onClick = { showSleep = true }) {
                Icon(
                    Icons.Rounded.Bedtime,
                    contentDescription = "睡眠定时",
                    tint = if (deadline != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        val folderUrl = ps.uri?.let { if (it.contains('/')) it.substringBeforeLast('/') + "/" else "" }.orEmpty()
        // 封面可能生成在父目录（音频在子文件夹里），这里会逐层向上找
        val art = rememberPlaybackCover(folderUrl, ps.title)

        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .widthIn(max = 320.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(folderGradient(ps.subtitle ?: ps.title ?: "故事"))
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            when (art) {
                is CoverArt.Bitmap -> Image(
                    painter = BitmapPainter(art.image),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                is CoverArt.Resource -> Image(
                    painter = painterResource(art.res),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.12f),
                    contentScale = ContentScale.Crop,
                )
                null -> Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = if (isDark()) Color(0xFFF0EDE7) else Color(0xFF26231F),
                    modifier = Modifier.size(96.dp),
                )
            }
            if (ps.buffering) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f))
                )
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        Spacer(Modifier.height(34.dp))

        Text(
            text = ps.title ?: "还没有在播放",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = ps.subtitle.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(18.dp))

        Slider(
            value = sliderValue,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { vm.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                formatTime(shownPosition),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatTime(duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.previous() }) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "上一集",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = { vm.seekBy(-10_000) }) {
                Icon(
                    Icons.Rounded.Replay10,
                    contentDescription = "后退10秒",
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            ) {
                IconButton(onClick = { vm.toggle() }, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (ps.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            IconButton(onClick = { vm.seekBy(10_000) }) {
                Icon(
                    Icons.Rounded.Forward10,
                    contentDescription = "前进10秒",
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.next() }, enabled = ps.hasNext) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "下一集",
                    modifier = Modifier.size(36.dp),
                    tint = if (ps.hasNext) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.outline,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (deadline != null || pauseAtEnd) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { showSleep = true }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Bedtime,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (deadline != null || pauseAtEnd) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(7.dp))
                Text(
                    text = when {
                        deadline != null -> "剩 ${formatLeft(remaining)} 停止"
                        pauseAtEnd -> "播完本集停止"
                        else -> "睡眠定时"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (deadline != null || pauseAtEnd) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = "自动连播本文件夹的下一集",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }

    if (showSleep) {
        val minutes = listOf(15, 30, 45, 60, 90)
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("睡眠定时", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    minutes.forEach { m ->
                        Text(
                            text = "$m 分钟后停止",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    vm.setSleep(m, false)
                                    showSleep = false
                                }
                                .padding(vertical = 13.dp, horizontal = 8.dp),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = "播完当前这集停止",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                vm.setSleep(null, true)
                                showSleep = false
                            }
                            .padding(vertical = 13.dp, horizontal = 8.dp),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setSleep(null, false)
                    showSleep = false
                }) { Text("关闭定时") }
            },
            dismissButton = {
                TextButton(onClick = { showSleep = false }) { Text("取消") }
            },
        )
    }
}
