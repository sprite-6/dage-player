package com.dage.player.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.dage.player.data.AiCovers
import com.dage.player.data.CustomCovers
import com.dage.player.data.Prefs

private val coverCache = LruCache<String, ImageBitmap>(24)

private fun load(key: String, path: () -> String?): ImageBitmap? {
    coverCache.get(key)?.let { return it }
    val file = path() ?: return null
    return runCatching {
        BitmapFactory.decodeFile(file)?.asImageBitmap()
    }.getOrNull()?.also { coverCache.put(key, it) }
}

/** 用户手动指定的封面 */
@Composable
fun rememberCustomCover(folderUrl: String): ImageBitmap? {
    if (folderUrl.isBlank()) return null
    val context = LocalContext.current
    val file = CustomCovers.fileFor(context, folderUrl)
    if (!file.exists() || file.length() == 0L) return null
    val key = "p#" + folderUrl + "#" + file.lastModified() + "#" + file.length()
    return remember(key) { load(key) { file.absolutePath } }
}

/** AI 生成的封面 */
@Composable
fun rememberAiCover(folderUrl: String): ImageBitmap? {
    if (folderUrl.isBlank()) return null
    val context = LocalContext.current
    val file = AiCovers.fileFor(context, folderUrl)
    if (!file.exists() || file.length() == 0L) return null
    val key = "a#" + folderUrl + "#" + file.lastModified() + "#" + file.length()
    return remember(key) { load(key) { file.absolutePath } }
}

/** 实际展示的封面：手动 > AI（内置主题图由调用方判断） */
@Composable
fun rememberFolderCover(folderUrl: String): ImageBitmap? =
    rememberCustomCover(folderUrl) ?: rememberAiCover(folderUrl)

// ---------------- 逐层向上找封面 ----------------

/** 封面可能是"矢量插画"或"位图"，播放页要按两种方式渲染 */
sealed interface CoverArt {
    data class Bitmap(val image: ImageBitmap) : CoverArt
    data class Resource(@DrawableRes val res: Int) : CoverArt
}

/**
 * 从目录 URL 逐层向上取候选（最近的在最前），不越过 App 配置的媒体根目录。
 * 例：smb://nas/media/儿童故事/第1季/ -> [第1季, 儿童故事, media]
 */
fun coverUrlCandidates(folderUrl: String, maxUp: Int = 3): List<String> {
    if (folderUrl.isBlank()) return emptyList()
    val base = runCatching { Prefs.nas()?.baseUrl }.getOrNull().orEmpty()
        .let { if (it.isNotEmpty() && !it.endsWith("/")) "$it/" else it }
    val out = ArrayList<String>(maxUp + 1)
    var cur = if (folderUrl.endsWith("/")) folderUrl else "$folderUrl/"
    out += cur
    var n = 0
    while (n < maxUp) {
        val trimmed = cur.trimEnd('/')
        val idx = trimmed.lastIndexOf('/')
        if (idx <= 0) break
        val up = trimmed.substring(0, idx + 1)
        if (up == cur) break
        if (base.isNotEmpty() && !up.startsWith(base)) break
        out += up
        cur = up
        n++
    }
    return out
}

/** 把候选目录的封面文件状态拼成字符串，作为 remember 的刷新依据 */
private fun coverStamp(context: Context, folderUrl: String): String = buildString {
    for (u in coverUrlCandidates(folderUrl)) {
        val c = CustomCovers.fileFor(context, u)
        val a = AiCovers.fileFor(context, u)
        append('|').append(c.lastModified()).append(':').append(c.length())
        append('/').append(a.lastModified()).append(':').append(a.length())
    }
}

/**
 * 播放页专用：按「当前目录封面 → 当前目录内置插画 → 上层目录封面 → 宽松匹配」依次回落。
 * 解决「封面生成在父目录、音频在子目录」时播放页看不到图的问题。
 */
private fun resolvePlaybackCover(
    context: Context,
    folderUrl: String,
    fileTitle: String?,
): CoverArt? {
    val candidates = coverUrlCandidates(folderUrl)
    for ((i, u) in candidates.withIndex()) {
        val c = CustomCovers.fileFor(context, u)
        if (c.exists() && c.length() > 0L) {
            load("p#$u#${c.lastModified()}#${c.length()}") { c.absolutePath }?.let {
                return CoverArt.Bitmap(it)
            }
        }
        val a = AiCovers.fileFor(context, u)
        if (a.exists() && a.length() > 0L) {
            load("a#$u#${a.lastModified()}#${a.length()}") { a.absolutePath }?.let {
                return CoverArt.Bitmap(it)
            }
        }
        // 只有当前这一层允许"内置插画插队"：子目录自己有主题图时优先用它，
        // 而不是被父目录的封面顶掉
        if (i == 0 && Prefs.coversEnabled) {
            val name = u.trimEnd('/').substringAfterLast('/')
            Covers.forName(name)?.let { return CoverArt.Resource(it) }
        }
    }
    if (!Prefs.coversEnabled) return null
    return Covers.forFolderPath(folderUrl, listOfNotNull(fileTitle))?.let { CoverArt.Resource(it) }
}

@Composable
fun rememberPlaybackCover(folderUrl: String, fileTitle: String?): CoverArt? {
    if (folderUrl.isBlank()) return null
    val context = LocalContext.current
    val enabled = Prefs.coversEnabled
    val key = folderUrl + "|" + (fileTitle ?: "") + "|" + enabled + "|" + coverStamp(context, folderUrl)
    return remember(key) { resolvePlaybackCover(context, folderUrl, fileTitle) }
}
