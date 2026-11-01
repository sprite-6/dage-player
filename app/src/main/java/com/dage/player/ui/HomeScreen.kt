package com.dage.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dage.player.data.Entry
import com.dage.player.data.Prefs
import com.dage.player.data.ProgressStore
import com.dage.player.data.Smb
import com.dage.player.data.naturalCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    vm: PlayerViewModel,
    openFolder: (String) -> Unit,
    openSettings: () -> Unit,
    openPlayer: () -> Unit,
) {
    val rev by Prefs.revision.collectAsState()
    var folders by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val configured = Prefs.configured

    LaunchedEffect(configured, rev, reload) {
        if (!configured) {
            folders = emptyList()
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        // reload > 0 说明是用户点刷新/重试，必须绕过 60 秒目录缓存，否则等于没点
        val forceRefresh = reload > 0
        runCatching {
            withContext(Dispatchers.IO) {
                Smb.list(Prefs.nas()!!.baseUrl, useCache = !forceRefresh)
            }
        }.onSuccess {
            folders = it.filter { e -> e.isDir }
        }.onFailure {
            folders = emptyList()
            error = it.message ?: "读取失败"
        }
        loading = false
    }

    val viewMode = Prefs.folderView
    val pinnedUrls = Prefs.pinnedFolders()
    val pinned = pinnedUrls
        .map { Entry(name = pinnedName(it), isDir = true, url = it, size = 0L) }
        .sortedWith { a, b -> naturalCompare(a.name, b.name) }
    val rest = folders.filter { it.url !in pinnedUrls }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // 顶置 + 全部，按 URL 去重，搜索时统一在这份里找
    val allFolders = remember(folders, pinnedUrls) {
        (pinned + rest).distinctBy { it.url }
    }
    val keyword = query.trim()
    val searchHits = remember(allFolders, keyword) {
        if (keyword.isBlank()) emptyList()
        else allFolders
            .filter { it.name.contains(keyword, ignoreCase = true) }
            .sortedWith { a, b -> naturalCompare(a.name, b.name) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        if (searchOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索文件夹名") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "清空",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    query = ""
                    searchOpen = false
                }) { Text("取消") }
            }
        } else {
            ScreenHeader(title = "大哥的播放器") {
                IconButton(onClick = { searchOpen = true }) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { Prefs.folderView = if (viewMode == 1) 0 else 1 }) {
                    Icon(
                        if (viewMode == 1) Icons.Rounded.GridView else Icons.Rounded.ViewList,
                        contentDescription = if (viewMode == 1) "切换为大图标" else "切换为列表",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { reload++ }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = openSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (searchOpen) {
            Column(modifier = Modifier.fillMaxSize()) {
                when {
                    keyword.isBlank() -> CenterBox {
                        Text(
                            "输入文件夹名开始搜索\n（只搜首页这一层的文件夹名）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                        )
                    }
                    searchHits.isEmpty() -> CenterBox {
                        Text(
                            "没有找到「$keyword」",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> SearchResultGrid(
                        hits = searchHits,
                        onOpen = openFolder,
                        onTogglePin = { Prefs.togglePin(it) },
                        pinnedUrls = pinnedUrls,
                    )
                }
            }
        } else {
            when {
            !configured -> SetupHint(openSettings)
            loading && folders.isEmpty() && pinned.isEmpty() -> CenterBox {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            error != null -> ErrorBox(error!!) { reload++ }
            viewMode == 1 -> FolderList(
                pinned = pinned,
                rest = rest,
                openFolder = openFolder,
                onTogglePin = { Prefs.togglePin(it) },
                onOpenContinue = { vm.resumeLast { openPlayer() } },
                hasContinue = ProgressStore.lastUri != null,
            )
            else -> FolderGrid(
                pinned = pinned,
                rest = rest,
                openFolder = openFolder,
                onTogglePin = { Prefs.togglePin(it) },
                onOpenContinue = { vm.resumeLast { openPlayer() } },
                hasContinue = ProgressStore.lastUri != null,
            )
            }
        }
    }
}

private fun pinnedName(url: String): String =
    url.trimEnd('/').substringAfterLast('/').ifBlank { url }

/** 搜索结果：统一用两列卡片，点进去就是那个目录 */
@Composable
private fun SearchResultGrid(
    hits: List<Entry>,
    onOpen: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    pinnedUrls: Set<String>,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "找到 ${hits.size} 个文件夹",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 6.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(hits, key = { "hit_" + it.url }) { entry ->
                FolderCard(
                    name = entry.name,
                    url = entry.url,
                    pinned = entry.url in pinnedUrls,
                    onClick = { onOpen(entry.url) },
                    onTogglePin = { onTogglePin(entry.url) },
                )
            }
        }
    }
}

@Composable
private fun FolderGrid(
    pinned: List<Entry>,
    rest: List<Entry>,
    openFolder: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onOpenContinue: () -> Unit,
    hasContinue: Boolean,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (hasContinue) {
            item(span = { GridItemSpan(maxLineSpan) }) { ContinueCard(onClick = onOpenContinue) }
        }
        if (pinned.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("顶置") }
            items(pinned, key = { "pin_" + it.url }) { entry ->
                FolderCard(
                    name = entry.name,
                    url = entry.url,
                    pinned = true,
                    onClick = { openFolder(entry.url) },
                    onTogglePin = { onTogglePin(entry.url) },
                )
            }
        }
        if (pinned.isNotEmpty() && rest.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("全部文件夹") }
        }
        items(rest, key = { it.url }) { entry ->
            FolderCard(
                name = entry.name,
                url = entry.url,
                pinned = false,
                onClick = { openFolder(entry.url) },
                onTogglePin = { onTogglePin(entry.url) },
            )
        }
        if (pinned.isEmpty() && rest.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "这个目录下还没有子文件夹",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FolderList(
    pinned: List<Entry>,
    rest: List<Entry>,
    openFolder: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onOpenContinue: () -> Unit,
    hasContinue: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
    ) {
        if (hasContinue) {
            item { ContinueCard(onClick = onOpenContinue) }
        }
        if (pinned.isNotEmpty()) {
            item { SectionLabel("顶置") }
            items(pinned, key = { "pin_" + it.url }) { entry ->
                FolderRow(
                    name = entry.name,
                    url = entry.url,
                    pinned = true,
                    onClick = { openFolder(entry.url) },
                    onTogglePin = { onTogglePin(entry.url) },
                )
            }
        }
        if (pinned.isNotEmpty() && rest.isNotEmpty()) {
            item { SectionLabel("全部文件夹") }
        }
        items(rest, key = { it.url }) { entry ->
            FolderRow(
                name = entry.name,
                url = entry.url,
                pinned = false,
                onClick = { openFolder(entry.url) },
                onTogglePin = { onTogglePin(entry.url) },
            )
        }
        if (pinned.isEmpty() && rest.isEmpty()) {
            item {
                Text(
                    text = "这个目录下还没有子文件夹",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun PinButton(
    pinned: Boolean,
    onTogglePin: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onTogglePin),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (pinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = if (pinned) "取消顶置" else "顶置",
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun FolderCard(
    name: String,
    url: String,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val custom = rememberFolderCover(url)
    // 用 URL 反推上层目录名一起匹配：`儿童故事/第1季` 这种也能借到「儿童故事」的主题图
    val builtIn = if (custom == null && Prefs.coversEnabled) {
        Covers.forFolderPath(url, listOf(name))
    } else null
    val hasCover = custom != null || builtIn != null
    val textColor = when {
        hasCover -> Color.White
        isDark() -> Color(0xFFF0EDE7)
        else -> Color(0xFF26231F)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.12f)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        if (hasCover) {
            val painter = if (custom != null) BitmapPainter(custom) else painterResource(builtIn!!)
            // 内置插画放大一点裁切：画面更满，同时把图角水印推出可视区；自定义图不裁
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(if (custom != null) 1f else 1.14f),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.40f to Color.Transparent,
                            0.66f to Color.Black.copy(alpha = 0.20f),
                            1f to Color.Black.copy(alpha = 0.64f),
                        )
                    ),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(folderGradient(name)))
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(if (hasCover) Color.Black.copy(alpha = 0.26f) else Color.Black.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.9f),
                modifier = Modifier.size(21.dp),
            )
        }
        PinButton(
            pinned = pinned,
            onTogglePin = onTogglePin,
            tint = textColor.copy(alpha = if (pinned) 1f else 0.55f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )
        Text(
            text = name,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            color = textColor,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FolderRow(
    name: String,
    url: String,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val custom = rememberFolderCover(url)
            val builtIn = if (custom == null && Prefs.coversEnabled) Covers.forName(name) else null
            if (custom != null || builtIn != null) {
                Image(
                    painter = if (custom != null) BitmapPainter(custom) else painterResource(builtIn!!),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(if (custom != null) 1f else 1.12f),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(folderGradient(name)),
                ) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = if (isDark()) Color(0xFFF0EDE7) else Color(0xFF26231F),
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
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PinButton(
            pinned = pinned,
            onTogglePin = onTogglePin,
            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ContinueCard(onClick: () -> Unit) {
    val title = ProgressStore.lastTitle
        ?: ProgressStore.lastUri?.substringAfterLast('/')?.let { displayName(it) }
        ?: ""
    val subtitle = ProgressStore.lastSubtitle.orEmpty()
    val position = ProgressStore.lastPosition()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "继续收听",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (position > 0) {
                Text(
                    text = "上次听到 ${formatTime(position)}" + if (subtitle.isNotBlank()) " · $subtitle" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "继续播放",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun SetupHint(openSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("还没有连接 NAS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "填一下 NAS 地址、共享目录和账号，\n就能直接在手机上听里面的故事了。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = openSettings,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text("去设置")
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "连不上 NAS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text("重试") }
    }
}
