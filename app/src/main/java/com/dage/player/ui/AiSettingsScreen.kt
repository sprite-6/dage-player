package com.dage.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import com.dage.player.data.AiCoverRunner
import com.dage.player.data.AiCovers
import com.dage.player.data.CustomCovers
import com.dage.player.data.Entry
import com.dage.player.data.Prefs
import com.dage.player.data.Smb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AiSettingsScreen(onBack: () -> Unit, openAbout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress by AiCoverRunner.progress.collectAsState()
    val rev by Prefs.revision.collectAsState()

    var enabled by remember { mutableStateOf(Prefs.aiEnabled) }
    var baseUrl by remember { mutableStateOf(Prefs.aiBaseUrl) }
    var model by remember { mutableStateOf(Prefs.aiModel) }
    var key by remember { mutableStateOf(Prefs.aiKey) }
    var size by remember { mutableStateOf(Prefs.aiSize) }
    var ratio by remember { mutableStateOf(Prefs.aiRatio) }
    var style by remember { mutableStateOf(Prefs.aiStyle) }
    var interval by remember { mutableStateOf(Prefs.aiIntervalSec.toString()) }
    var showKey by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testOk by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var dirs by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var dirsLoaded by remember { mutableStateOf(false) }
    var showRegenConfirm by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val aiCount = remember(rev) { AiCovers.count(context) }

    // 模型名看着像对话模型时提前提醒，省得白跑一次请求
    val effectiveBase = baseUrl.ifBlank { Prefs.defaultAiBaseUrl }
    val modelWarning: String? = when {
        model.isBlank() -> null
        model.contains("image", ignoreCase = true) -> null
        effectiveBase.contains("agnes", ignoreCase = true) ->
            "Agnes 的画图模型名里带 image，例如 agnes-image-2.5-flash；不带的是对话模型，会报 400"
        else -> null
    }

    LaunchedEffect(rev) {
        if (!Prefs.configured) {
            dirs = emptyList()
            dirsLoaded = true
            return@LaunchedEffect
        }
        dirs = runCatching {
            withContext(Dispatchers.IO) { Smb.list(Prefs.nas()!!.baseUrl) }
        }.getOrDefault(emptyList()).filter { it.isDir }
        dirsLoaded = true
    }

    val coveredCount = remember(dirs, rev) {
        dirs.count { AiCovers.has(context, it.url) || CustomCovers.has(context, it.url) }
    }
    val missing = remember(dirs, rev) {
        dirs.filter { !AiCovers.has(context, it.url) && !CustomCovers.has(context, it.url) }
    }

    fun save() {
        val changed = baseUrl.trim() != Prefs.aiBaseUrl ||
            model.trim() != Prefs.aiModel ||
            size.trim() != Prefs.aiSize ||
            ratio.trim() != Prefs.aiRatio
        Prefs.aiEnabled = enabled
        Prefs.aiBaseUrl = baseUrl
        Prefs.aiModel = model
        Prefs.aiKey = key
        Prefs.aiSize = size
        Prefs.aiRatio = ratio
        Prefs.aiStyle = style
        Prefs.aiIntervalSec = interval.toIntOrNull() ?: 12
        if (changed) Prefs.invalidateAiProfile()
        saved = if (changed) "已保存，接口参数变了，请重新测试一次" else "已保存"
    }

    /**
     * 批量前先逐个读目录，取前 8 个标题当提示词素材；读取过程给进度，避免看着像卡住。
     */
    fun startBatch(targets: List<Entry>) {
        if (targets.isEmpty() || scanProgress != null || progress.running) return
        scanProgress = 0 to targets.size
        scope.launch {
            val tasks = ArrayList<AiCoverRunner.Task>(targets.size)
            targets.forEachIndexed { index, dir ->
                val titles = AiCovers.sampleTitles(dir.url, 8)
                tasks += AiCoverRunner.Task(dir.url, dir.name, titles)
                scanProgress = (index + 1) to targets.size
            }
            scanProgress = null
            AiCoverRunner.start(context, tasks)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = "AI 封面", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                "用你自己申请的大模型接口，根据文件夹名和里面的内容生成封面。" +
                    "免费额度通常有频率限制，这里默认串行、每次间隔几秒，避免被限流。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp,
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用 AI 封面", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "关闭后不发起任何网络请求",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        Prefs.aiEnabled = it
                    },
                )
            }

            SectionTitle("接口")
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text(Prefs.defaultAiBaseUrl) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("图像模型 ID") },
                placeholder = { Text(Prefs.defaultAiModel) },
                isError = modelWarning != null,
                supportingText = modelWarning?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("API Key") },
                placeholder = { Text("只存在本机，加密保存") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                trailingIcon = {
                    Text(
                        text = if (showKey) "隐藏" else "显示",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { showKey = !showKey }
                            .padding(10.dp),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))
            Text("尺寸档", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            PillSelector(
                options = listOf("1K", "2K", "4K"),
                selected = listOf("1K", "2K", "4K").indexOf(size).coerceAtLeast(0),
                onSelect = { size = listOf("1K", "2K", "4K")[it] },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = ratio,
                onValueChange = { ratio = it },
                label = { Text("比例（如 1:1）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("风格")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "提示词模板",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (showStyle) "收起" else "编辑",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { showStyle = !showStyle }
                        .padding(8.dp),
                )
            }
            if (showStyle) {
                OutlinedTextField(
                    value = style,
                    onValueChange = { style = it },
                    label = { Text("追加在每张图后面的风格描述") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    style,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    lineHeight = 18.sp,
                )
            }

            SectionTitle("限流保护")
            OutlinedTextField(
                value = interval,
                onValueChange = { interval = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("请求间隔（秒）") },
                supportingText = { Text("免费额度一般有每分钟次数限制，建议 10 秒以上") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("保存设置") }
            if (saved != null) {
                Spacer(Modifier.height(6.dp))
                Text(saved!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            SectionTitle("接口测试")
            Text(
                "各家的字段写法不完全一样（尺寸是像素还是 1K 档位、比例放哪个字段、response_format 放哪）。" +
                    "点一次测试，程序会依次试三种写法，把能用的那种记下来，之后生成不再试错。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        save()
                        testing = true
                        testResult = null
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) { AiCovers.probe(context) }
                            }
                            testing = false
                            testOk = result.isSuccess
                            testResult = result.fold(
                                onSuccess = { "连接正常，已识别为：${it.label}" },
                                onFailure = { "失败：" + (it.message ?: "未知错误") },
                            )
                        }
                    },
                    enabled = !testing,
                ) {
                    Text(if (testing) "测试中…" else "测试接口")
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = AiCovers.currentProfile()?.label ?: "尚未识别",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            testResult?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (testOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }

            SectionTitle("批量生成")
            Text(
                "只给还没有封面的文件夹生成（已有 AI 封面或手动设过的会跳过），所以新加了文件夹再点一次就行，不会重复消耗额度。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    !Prefs.configured -> "还没配置 NAS"
                    !dirsLoaded -> "正在读取目录…"
                    dirs.isEmpty() -> "共享目录下没有子文件夹"
                    else -> "共 ${dirs.size} 个文件夹 · 已有封面 $coveredCount 个 · 缺 ${missing.size} 个"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        save()
                        if (!Prefs.aiReady) {
                            saved = "请先填好 Base URL、模型和 API Key"
                            return@OutlinedButton
                        }
                        val todo = missing
                        if (todo.isEmpty()) {
                            saved = "没有需要生成的文件夹"
                        } else {
                            startBatch(todo)
                        }
                    },
                    enabled = !progress.running && missing.isNotEmpty() && scanProgress == null,
                ) {
                    Text(
                        when {
                            scanProgress != null -> "读取目录中…"
                            progress.running -> "生成中…"
                            missing.isEmpty() -> "封面都齐了"
                            else -> "补齐缺失封面（${missing.size}）"
                        }
                    )
                }
                if (progress.running) {
                    OutlinedButton(onClick = { AiCoverRunner.cancel() }) { Text("取消") }
                }
            }
            Text(
                "想连已有的也重画：全部重新生成",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(enabled = !progress.running && dirs.isNotEmpty()) { showRegenConfirm = true }
                    .padding(vertical = 8.dp),
            )

            if (progress.running || progress.message != null || scanProgress != null) {
                Spacer(Modifier.height(10.dp))
                scanProgress?.let { (done, total) ->
                    LinearProgressIndicator(
                        progress = { if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "正在读取目录内容 $done/$total（要取每个文件夹里的前 8 个标题）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (progress.running && progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { (progress.done.toFloat() / progress.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${progress.done}/${progress.total}" +
                            (progress.currentName?.let { " · 正在生成：$it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                progress.message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { confirmClear = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("清空 AI 生成的封面", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    "$aiCount 张",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("关于隐私")
            Text(
                "点「生成」时会把文件夹名和部分文件名发送给你填写的服务商，不发送音频本身。" +
                    "API Key 用 Android Keystore 加密保存在本机，不会写进日志或上传；但设备被 root 或借出时仍有泄露风险，" +
                    "请自行评估，必要时到服务商控制台吊销。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "查看完整免责条款",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { openAbout() }
                    .padding(vertical = 6.dp),
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showRegenConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenConfirm = false },
            title = { Text("全部重新生成？") },
            text = { Text("会给 ${dirs.size} 个文件夹全部重画，已有封面会被覆盖，每个都要消耗一次额度。只想补新的就点「补齐缺失封面」。") },
            confirmButton = {
                TextButton(onClick = {
                    showRegenConfirm = false
                    save()
                    if (!Prefs.aiReady) {
                        saved = "请先填好 Base URL、模型和 API Key"
                    } else {
                        startBatch(dirs)
                    }
                }) { Text("全部重画") }
            },
            dismissButton = {
                TextButton(onClick = { showRegenConfirm = false }) { Text("取消") }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空 AI 封面？") },
            text = { Text("已生成的 AI 封面会全部删除，下次需要重新生成（会再消耗额度）。") },
            confirmButton = {
                TextButton(onClick = {
                    AiCovers.clearAll(context)
                    confirmClear = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}
