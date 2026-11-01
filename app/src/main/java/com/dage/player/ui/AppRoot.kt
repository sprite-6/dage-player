package com.dage.player.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val vm: PlayerViewModel = viewModel()
    val ps by vm.state.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showMini = !ps.title.isNullOrBlank() && route != "player"

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showMini) {
                MiniPlayer(ps, vm) { nav.navigate("player") }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = inner.calculateBottomPadding()),
        ) {
            composable("home") {
                HomeScreen(
                    vm = vm,
                    openFolder = { nav.navigate("folder?url=" + Uri.encode(it)) },
                    openSettings = { nav.navigate("settings") },
                    openPlayer = { nav.navigate("player") },
                )
            }
            composable(
                route = "folder?url={url}",
                arguments = listOf(navArgument("url") {
                    type = NavType.StringType
                    defaultValue = ""
                }),
            ) { backEntry ->
                val url = backEntry.arguments?.getString("url").orEmpty()
                FolderScreen(
                    vm = vm,
                    url = url,
                    onBack = { nav.popBackStack() },
                    onOpenFolder = { nav.navigate("folder?url=" + Uri.encode(it)) },
                    openPlayer = { nav.navigate("player") },
                )
            }
            composable("player") {
                PlayerScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    openAi = { nav.navigate("ai") },
                    openAbout = { nav.navigate("about") },
                )
            }
            composable("ai") {
                AiSettingsScreen(
                    onBack = { nav.popBackStack() },
                    openAbout = { nav.navigate("about") },
                )
            }
            composable("about") {
                AboutScreen(onBack = { nav.popBackStack() })
            }
        }
    }

    vm.lastError?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            title = { Text("出错了") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("知道了") } },
        )
    }
}

@Composable
private fun MiniPlayer(ps: PlayerState, vm: PlayerViewModel, onClick: () -> Unit) {
    val progress = if (ps.duration > 0) (ps.position.toFloat() / ps.duration).coerceIn(0f, 1f) else 0f
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(folderGradient(ps.subtitle ?: ps.title ?: "故事")),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isDark()) androidx.compose.ui.graphics.Color(0xFFF0EDE7)
                        else androidx.compose.ui.graphics.Color(0xFF26231F),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ps.title.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = ps.subtitle.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { vm.toggle() }) {
                    Icon(
                        if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (ps.isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                IconButton(onClick = { vm.next() }, enabled = ps.hasNext) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "下一集",
                        tint = if (ps.hasNext) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
