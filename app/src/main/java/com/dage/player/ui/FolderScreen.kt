package com.dage.player.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dage.player.data.AiCoverRunner
import com.dage.player.data.AiCovers
import com.dage.player.data.CustomCovers
import com.dage.player.data.Entry
import com.dage.player.data.Prefs
import com.dage.player.data.ProgressStore
import com.dage.player.data.Smb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FolderScreen(
    vm: PlayerViewModel,
    url: String,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    openPlayer: () -> Unit,
) {
    val rev by Prefs.revision.collectAsState()
    val ps by vm.state.collectAsState()
    var entries by remember(url) { mutableStateOf<List<Entry>>(emptyList()) }
    var loading by remember(url) { mutableStateOf(false) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var reload by remember(url) { mutableIntStateOf(0) }

    val title = remember(url) {
        val clean = url.trimEnd('/')
        clean.substringAfterLast('/').ifBlank { "共享目录" }
    }
    val pinnable = Prefs.configured && url.isNotBlank() && url != Prefs.nas()?.baseUrl
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCoverMenu by remember(url) { mutableStateOf(false) }
    var showAiDialog by remember(url) { mutableStateOf(false) }
    var aiHint by remember(url) { mutableStateOf("") }
    var coverSaving by remember(url) { mutableStateOf(false) }
    val hasCustomCover = remember(url, rev) { CustomCovers.has(context, url) }
    val hasAiCover = remember(url, rev) { AiCovers.has(context, url) }
    val aiProgress by AiCoverRunner.progress.collectAsState()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            coverSaving = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) { CustomCovers.save(context, url, uri) }
                coverSaving = false
                if (!ok) vm.showError("这张图片读不出来，换一张试试")
            }
        }
    }

    LaunchedEffect(url, rev, reload) {
        loading = true
        error = null
        runCatching { withContext(Dispatchers.IO) { Smb.list(url) } }
            .onSuccess { entries = it }
            .onFailure { error = it.message ?: "读取失败" }
        loading = false
    }

    val files = entries.filter { !it.isDir }
    val indexOf = remember(files) { files.withIndex().associate { it.value.url to it.index + 1 } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = title, onBack = onBack) {
            if (pinnable) {
                IconButton(onClick = { showCoverMenu = true }) {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = "换封面",
                        tint = if (hasCustomCover || hasAiCover) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val pinned = Prefs.isPinned(url)
                IconButton(onClick = { Prefs.togglePin(url) }) {
                    Icon(
                        if (pinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (pinned) "取消顶置" else "顶置到首页",
                        tint = if (pinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (files.isNotEmpty()) {
                IconButton(onClick = { vm.playFolder(url, title, files.firstOrNull()?.url) { openPlayer() } }) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "从头播放",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (aiProgress.running || aiProgress.message != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (aiProgress.running) {
                        "AI 生成中 ${aiProgress.done}/${aiProgress.total}" +
                            (aiProgress.currentName?.let { " · $it" } ?: "")
                    } else {
                        aiProgress.message.orEmpty()
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (aiProgress.running) {
                    TextButton(onClick = { AiCoverRunner.cancel() }) {
                        Text("取消", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        when {
            loading && entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            error != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("打不开这个文件夹", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text(
                    error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { reload++ }) { Text("重试") }
            }

            entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("这里什么都没有", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 24.dp),
            ) {
                items(entries, key = { it.url }) { entry ->
                    if (entry.isDir) {
                        DirRow(entry.name, entry.url) { onOpenFolder(entry.url) }
                    } else {
                        val active = ps.uri == entry.url
                        FileRow(
                            index = indexOf[entry.url] ?: 0,
                            name = displayName(entry.name),
                            active = active,
                            playing = active && ps.isPlaying,
                            progress = ProgressStore.fraction(entry.url),
                            onClick = { vm.playFolder(url, title, entry.url) { openPlayer() } },
                        )
                    }
                }
            }
        }
    }

    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            title = { Text("AI 生成封面") },
            text = {
                Column {
                    Text(
                        "默认会用这个文件夹里的前 8 个标题让模型自己归纳主题。" +
                            "如果这些内容主题比较杂（比如新闻、合集），可以在下面直接写一句想要的画面，写了就只按这句画。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = aiHint,
                        onValueChange = { if (it.length <= 120) aiHint = it },
                        label = { Text("画面描述（可留空）") },
                        placeholder = { Text("例：新闻演播室、麦克风、城市天际线，蓝灰调") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (aiHint.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "已填描述 · 将忽略文件名，只按这句画",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val hint = aiHint.trim()
                    showAiDialog = false
                    scope.launch {
                        // 这一层可能只有子文件夹（如「儿童故事/小王子/xx.mp3」），往下取一层标题
                        val titles = withContext(Dispatchers.IO) {
                            if (files.isNotEmpty()) files.take(8).map { displayName(it.name) }
                            else AiCovers.sampleTitles(url, 8)
                        }
                        AiCoverRunner.start(
                            context,
                            listOf(
                                AiCoverRunner.Task(
                                    url = url,
                                    name = title,
                                    files = titles,
                                    hint = hint,
                                )
                            ),
                        )
                    }
                }) { Text("开始生成") }            },
            dismissButton = {
                TextButton(onClick = { showAiDialog = false }) { Text("取消") }
            },
        )
    }

    if (coverSaving) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("正在保存封面…") },
            text = { Text("图片会自动裁成正方形并压缩。") },
        )
    }

    if (showCoverMenu) {
        AlertDialog(
            onDismissRequest = { showCoverMenu = false },
            title = { Text("「$title」的封面") },
            text = {
                Column {
                    Text(
                        text = "从相册选一张图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showCoverMenu = false
                                pickImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (Prefs.aiEnabled) {
                        Text(
                            text = if (hasAiCover) "AI 重新生成封面" else "AI 生成封面",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showCoverMenu = false
                                    if (!Prefs.aiReady) {
                                        vm.showError("请先在「设置 → AI 封面」里填好接口和 API Key")
                                    } else {
                                        aiHint = ""
                                        showAiDialog = true
                                    }
                                }
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = if (files.isNotEmpty()) {
                                "可用标题：${files.take(3).joinToString("、") { displayName(it.name) }}…"
                            } else {
                                "这是分类目录，会自动读取子文件夹里的标题"
                            },
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                        Text(
                            text = "会消耗一次第三方额度，慢的话等十几秒",
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (hasCustomCover || hasAiCover) {
                        Text(
                            text = "恢复默认（用内置插画或渐变色）",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    CustomCovers.clear(context, url)
                                    AiCovers.clear(context, url)
                                    showCoverMenu = false
                                }
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCoverMenu = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DirRow(name: String, url: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val custom = rememberFolderCover(url)
            val builtIn = if (custom == null && Prefs.coversEnabled) {
                Covers.forFolderPath(url, listOf(name))
            } else null
            if (custom != null || builtIn != null) {
                Image(
                    painter = if (custom != null) BitmapPainter(custom) else painterResource(builtIn!!),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(if (custom != null) 1f else 1.1f),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .size(20.dp)
                .padding(end = 2.dp),
        )
    }
}

@Composable
private fun FileRow(
    index: Int,
    name: String,
    active: Boolean,
    playing: Boolean,
    progress: Float,
    onClick: () -> Unit,
) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        text = "%02d".format(index),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                fontWeight = if (active || playing) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (active && playing) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (progress > 0.03f && progress < 0.97f && !active) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 62.dp, end = 16.dp)
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                trackColor = Color.Transparent,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
