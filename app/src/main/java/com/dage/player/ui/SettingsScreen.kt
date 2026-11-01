package com.dage.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dage.player.data.AiCovers
import com.dage.player.data.CustomCovers
import com.dage.player.data.NasCfg
import com.dage.player.data.Prefs
import com.dage.player.data.ProgressStore
import com.dage.player.data.Smb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onBack: () -> Unit, openAi: () -> Unit, openAbout: () -> Unit) {
    val mode by Prefs.themeFlow.collectAsState()
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf(Prefs.host) }
    var share by remember { mutableStateOf(Prefs.share) }
    var root by remember { mutableStateOf(Prefs.root) }
    var user by remember { mutableStateOf(Prefs.user) }
    var pass by remember { mutableStateOf(Prefs.pass) }
    var domain by remember { mutableStateOf(Prefs.domain) }
    var showPass by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(Prefs.folderView) }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testingOk by remember { mutableStateOf(false) }

    var shares by remember { mutableStateOf<List<String>?>(null) }
    var loadingShares by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearCovers by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun cfg() = NasCfg(host, share, root, user, pass, domain)

    fun save() {
        Prefs.saveNas(host, share, root, user, pass, domain)
        Smb.reload()
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = "设置", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SectionTitle("NAS 连接")
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("服务器地址") },
                placeholder = { Text("192.168.1.10 或 nas.local") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = share,
                    onValueChange = { share = it },
                    label = { Text("共享文件夹") },
                    placeholder = { Text("media") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        loadingShares = true
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) { Smb.listShares(cfg()) }
                            }
                            loadingShares = false
                            shares = result.getOrElse {
                                testResult = "读取共享失败：" + (it.message ?: "")
                                testingOk = false
                                emptyList()
                            }
                        }
                    },
                    enabled = host.isNotBlank() && !loadingShares,
                ) {
                    if (loadingShares) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("浏览")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = root,
                    onValueChange = { root = it },
                    label = { Text("子目录（可选）") },
                    placeholder = { Text("点「选择」逐级挑，也可手填") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { showPicker = true },
                    enabled = share.isNotBlank(),
                ) {
                    Text("选择")
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("用户名") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    Text(
                        text = if (showPass) "隐藏" else "显示",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { showPass = !showPass }
                            .padding(10.dp),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("域（一般不用填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) { Smb.test(cfg()) }
                            }
                            testing = false
                            testingOk = result.isSuccess
                            testResult = result.getOrElse { "连接失败：" + (it.message ?: "未知错误") }
                        }
                    },
                    enabled = !testing && host.isNotBlank(),
                ) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("测试连接")
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (testResult != null) {
                    Text(
                        text = testResult!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testingOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("保存")
            }

            SectionTitle("外观")
            PillSelector(
                options = listOf("跟随系统", "浅色", "深色"),
                selected = mode,
                onSelect = { Prefs.themeMode = it },
            )

            SectionTitle("文件夹显示")
            PillSelector(
                options = listOf("大方块", "横条列表"),
                selected = viewMode,
                onSelect = {
                    viewMode = it
                    Prefs.folderView = it
                },
            )

            SectionTitle("封面")
            var coversOn by remember { mutableStateOf(Prefs.coversEnabled) }
            val rev by Prefs.revision.collectAsState()
            val customCount = remember(rev) { CustomCovers.count(context) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("主题封面", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "按文件夹名自动匹配内置插画，关掉就只显示纯色块",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = coversOn,
                    onCheckedChange = {
                        coversOn = it
                        Prefs.coversEnabled = it
                    },
                )
            }
            if (customCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirmClearCovers = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("清空自定义封面", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "$customCount 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle("其他")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openAi() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI 封面（可选）", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (Prefs.aiEnabled) {
                            AiCovers.currentProfile()?.let { "已开启 · 接口形态：${it.label}" } ?: "已开启 · 还没测试过接口"
                        } else {
                            "用自己的 API Key，按文件夹内容生成封面"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "›",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { confirmClear = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("清空播放记录", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    "已记录 ${ProgressStore.count()} 个断点",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))
            Text(
                "大哥的播放器 v1.9.4",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "局域网直连 NAS 读取音频，文件不上传、不转存。支持 mp3 / m4a / aac / flac / wav / ogg 等格式。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "关于与免责条款 ›",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { openAbout() }
                    .padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    if (shares != null) {
        AlertDialog(
            onDismissRequest = { shares = null },
            title = { Text("选择一个共享文件夹") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (shares!!.isEmpty()) {
                        Text("没有读到共享列表，请在 NAS 里确认已开启 SMB 共享。", style = MaterialTheme.typography.bodySmall)
                    }
                    shares!!.forEach { s ->
                        Text(
                            text = s,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    share = s
                                    shares = null
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { shares = null }) { Text("关闭") }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空播放记录？") },
            text = { Text("所有音频的断点位置都会被清除，下次从头开始播放。") },
            confirmButton = {
                TextButton(onClick = {
                    ProgressStore.clear()
                    confirmClear = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }

    if (confirmClearCovers) {
        AlertDialog(
            onDismissRequest = { confirmClearCovers = false },
            title = { Text("清空自定义封面？") },
            text = { Text("所有手动设置的封面都会删掉，恢复成内置插画或纯色块。") },
            confirmButton = {
                TextButton(onClick = {
                    CustomCovers.clearAll(context)
                    confirmClearCovers = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCovers = false }) { Text("取消") }
            },
        )
    }

    if (showPicker) {
        FolderPickerDialog(
            shareRoot = NasCfg(host, share, "", user, pass, domain).baseUrl,
            initialRelative = root,
            onDismiss = { showPicker = false },
            onPick = {
                root = it
                showPicker = false
            },
        )
    }
}
