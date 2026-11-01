package com.dage.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 调第三方图像模型给文件夹生成封面。
 *
 * 各家都提供 OpenAI 风格的 POST /images/generations，差异只在三处：尺寸写法、比例字段、
 * response_format 的位置。所以这里不写死某一家，而是准备好三种"形态"，首次使用时自动探测，
 * 成功后把形态记进 Prefs.aiProfile，之后只用那一种，不再试错。
 */
object AiCovers {

    private const val SIZE = 512

    enum class Profile(val key: String, val label: String) {
        /** 标准 OpenAI：size=像素，response_format 放顶层 */
        OPENAI("openai", "OpenAI 标准（像素尺寸）"),

        /** Agnes 一类：size=1K/2K 档位，带 ratio，response_format 放 extra_body */
        AGNES("agnes", "档位尺寸 + extra_body（Agnes 等）"),

        /** 最精简：只给 model/prompt/size(像素)，其余都不传 */
        SIMPLE("simple", "精简模式（只传必要字段）"),
    }

    enum class Kind { AUTH, QUOTA, RATE, BAD, MODEL, NET, SERVER, OTHER }

    class AiException(val kind: Kind, message: String) : Exception(message)

    fun dir(context: Context): File {
        val d = File(context.filesDir, "covers_ai")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun fileFor(context: Context, folderUrl: String): File = File(dir(context), md5(norm(folderUrl)) + ".jpg")

    private fun norm(url: String): String = url.trimEnd('/') + "/"

    fun has(context: Context, folderUrl: String): Boolean {
        if (folderUrl.isBlank()) return false
        val f = fileFor(context, folderUrl)
        return f.exists() && f.length() > 0
    }

    fun count(context: Context): Int = dir(context).listFiles()?.count { it.isFile } ?: 0

    /**
     * 取这个文件夹用于拼提示词的标题样本。
     * 若这一层没有音频（常见于「儿童故事/小王子/xx.mp3」这种中间层），
     * 就往下找最多 2 个子目录再取，避免提示词里啥都没有。
     */
    fun sampleTitles(folderUrl: String, limit: Int = 8): List<String> {
        val entries = runCatching { Smb.list(folderUrl) }.getOrDefault(emptyList())
        val files = entries.filter { !it.isDir }
        if (files.isNotEmpty()) return files.take(limit).map { stripExt(it.name) }

        val titles = ArrayList<String>(limit)
        for (sub in entries.filter { it.isDir }.take(2)) {
            if (titles.size >= limit) break
            val inner = runCatching { Smb.list(sub.url) }.getOrDefault(emptyList())
                .filter { !it.isDir }
                .take(limit - titles.size)
                .map { stripExt(it.name) }
            titles += inner
        }
        return titles.take(limit)
    }

    private fun stripExt(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i > 0) name.substring(0, i) else name
    }

    fun clear(context: Context, folderUrl: String) {
        fileFor(context, folderUrl).delete()
        Prefs.notifyCoversChanged()
    }

    fun clearAll(context: Context): Int {
        var n = 0
        dir(context).listFiles()?.forEach { if (it.isFile && it.delete()) n++ }
        Prefs.notifyCoversChanged()
        return n
    }

    /** 新闻/资讯类的目录名特征，这类不能用儿童绘本的画风 */
    private val newsKeys = listOf(
        "新闻", "时事", "播报", "资讯", "快讯", "日报", "晚报", "朝闻", "要闻", "时政", "财经",
    )

    fun isNews(folderName: String): Boolean = newsKeys.any { folderName.contains(it) }

    /**
     * 拼提示词：
     * 1. 用户填了描述（hint）就照它画，不看文件名；
     * 2. 否则取前 8 个标题，要求模型先归纳共同主题再画（而不是罗列某一集）；
     * 3. 新闻/资讯类换一套画风，避免"儿童绘本"的味道。
     */
    fun buildPrompt(folderName: String, fileNames: List<String>, hint: String = ""): String {
        if (hint.isNotBlank()) {
            return "为有声专辑《$folderName》画一张正方形封面插图。" +
                "画面内容：${hint.trim().take(120)}。${Prefs.aiStyle}"
        }

        val samples = fileNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(8)
            .joinToString("；")
            .take(240)

        return if (isNews(folderName)) {
            val list = if (samples.isBlank()) "（未提供具体标题）" else samples
            "为资讯类音频专辑《$folderName》画一张正方形封面插图。\n" +
                "这张专辑里的内容标题是：$list\n" +
                "请先自行归纳这些标题的共同主题（可能只是同一类资讯栏目），再画一张能代表" +
                "该栏目调性的图，不要照搬某一条标题的字面内容。\n" +
                "风格要求：简洁、稳重、专业，扁平矢量或轻微渐变，蓝灰、藏青或暖灰为主色，" +
                "避免卡通化、避免人物特写、不要出现任何文字、字母或符号。"
        } else {
            val list = if (samples.isBlank()) "（未提供具体标题）" else samples
            "为儿童有声读物专辑《$folderName》画一张正方形封面插图。\n" +
                "这张专辑里的内容标题是：$list\n" +
                "请先自行归纳这些标题的共同主题与场景，再画一张能代表整个专辑的插画，" +
                "不要只画其中某一集的内容。\n" +
                "${Prefs.aiStyle}"
        }
    }

    fun currentProfile(): Profile? = Profile.values().firstOrNull { it.key == Prefs.aiProfile }

    /**
     * 依次试三种形态，返回第一个成功的，并写入 Prefs.aiProfile。
     * 若给了 folderUrl，探测产生的图会顺手存成该文件夹封面（不白花一次额度）。
     */
    fun probe(
        context: Context,
        folderUrl: String? = null,
        folderName: String? = null,
        fileNames: List<String> = emptyList(),
        hint: String = "",
    ): Profile {
        if (!Prefs.aiReady) throw AiException(Kind.AUTH, "请先填好 Base URL、模型和 API Key")
        val prompt = if (folderName != null) {
            buildPrompt(folderName, fileNames, hint)
        } else {
            "一只戴红围巾的小狐狸坐在草地上，扁平矢量插画，柔和配色，简洁背景。"
        }
        var last: AiException? = null
        for (profile in Profile.values()) {
            try {
                val bytes = request(prompt, profile)
                if (bytes != null && bytes.isNotEmpty()) {
                    Prefs.aiProfile = profile.key
                    if (folderUrl != null) saveJpeg(context, folderUrl, bytes)
                    return profile
                }
                last = AiException(Kind.OTHER, "返回内容里没有图片")
            } catch (e: AiException) {
                // Key 错、余额不足、模型不对，换形态也不会好，直接抛
                if (e.kind == Kind.AUTH || e.kind == Kind.QUOTA || e.kind == Kind.MODEL) throw e
                last = e
            }
        }
        throw last ?: AiException(Kind.OTHER, "所有接口形态都失败了")
    }

    /** 生成并写入本地缓存 */
    fun generate(
        context: Context,
        folderUrl: String,
        folderName: String,
        fileNames: List<String>,
        hint: String = "",
    ) {
        if (!Prefs.aiReady) throw AiException(Kind.AUTH, "还没有配置 AI 封面")
        val profile = currentProfile()
        if (profile == null) {
            // 没测过：把探测那一次的图直接当封面
            probe(context, folderUrl, folderName, fileNames, hint)
            return
        }
        val bytes = request(buildPrompt(folderName, fileNames, hint), profile)
        if (bytes == null || bytes.isEmpty()) throw AiException(Kind.OTHER, "模型没有返回图片")
        saveJpeg(context, folderUrl, bytes)
    }

    // ---------- 协议层 ----------

    private fun buildBody(prompt: String, profile: Profile): String {
        val json = JSONObject()
            .put("model", Prefs.aiModel)
            .put("prompt", prompt)
            .put("n", 1)
        when (profile) {
            Profile.OPENAI -> json
                .put("size", pixelSize())
                .put("response_format", "b64_json")

            Profile.AGNES -> json
                .put("size", tierSize())
                .put("ratio", Prefs.aiRatio.ifBlank { "1:1" })
                .put("extra_body", JSONObject().put("response_format", "url"))

            Profile.SIMPLE -> json.put("size", pixelSize())
        }
        return json.toString()
    }

    /** 1K/2K 换算成像素；本来就是像素就原样用 */
    private fun pixelSize(): String {
        val raw = Prefs.aiSize.ifBlank { "1K" }.trim()
        val m = Regex("^(\\d+)[Kk]$").find(raw) ?: return raw
        val base = (m.groupValues[1].toIntOrNull() ?: 1) * 1024
        val parts = Prefs.aiRatio.ifBlank { "1:1" }.split(":").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size != 2 || parts[0] <= 0 || parts[1] <= 0) return "${base}x${base}"
        val h = (base.toLong() * parts[1] / parts[0]).toInt().coerceAtLeast(64)
        return "${base}x$h"
    }

    private fun tierSize(): String {
        val raw = Prefs.aiSize.ifBlank { "1K" }.trim()
        return if (Regex("^\\d+[Kk]$").matches(raw)) raw.uppercase() else "1K"
    }

    private fun request(prompt: String, profile: Profile): ByteArray? {
        val base = Prefs.aiBaseUrl.trimEnd('/')
        val key = Prefs.aiKey
        if (key.isBlank()) throw AiException(Kind.AUTH, "还没有填 API Key")

        val conn = (URL("$base/images/generations").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $key")
        }
        try {
            conn.outputStream.use { it.write(buildBody(prompt, profile).toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw mapHttpError(code, text, profile)
            return extractImage(text)
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException(Kind.NET, "网络异常：${e.message ?: "连接失败"}")
        } finally {
            conn.disconnect()
        }
    }

    private fun extractImage(text: String): ByteArray? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val data = root.optJSONArray("data")
        if (data != null && data.length() > 0) {
            val first = data.optJSONObject(0) ?: return null
            val b64 = first.optString("b64_json", "")
            if (b64.isNotBlank()) return runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
            val url = first.optString("url", "")
            if (url.isNotBlank()) return download(url)
        }
        // 少数实现把图放在 images / output 里
        val images = root.optJSONArray("images")
        if (images != null && images.length() > 0) {
            val first = images.opt(0)
            if (first is String) {
                return if (first.startsWith("http")) download(first)
                else runCatching { Base64.decode(first, Base64.DEFAULT) }.getOrNull()
            }
        }
        return null
    }

    private fun download(url: String): ByteArray? = runCatching {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        try {
            if (c.responseCode !in 200..299) null else c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    private fun saveJpeg(context: Context, folderUrl: String, bytes: ByteArray) {
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw AiException(Kind.OTHER, "返回的图片无法解析")
        val side = minOf(raw.width, raw.height)
        val cropped = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true)
        val target = fileFor(context, folderUrl)
        val tmp = File(dir(context), target.name + ".tmp")
        FileOutputStream(tmp).use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (target.exists()) target.delete()
        tmp.renameTo(target)
        Prefs.notifyCoversChanged()
    }

    private fun mapHttpError(code: Int, text: String, profile: Profile): AiException {
        val brief = text.take(200).replace("\n", " ")
        val lower = text.lowercase()

        // 模型名填成了对话模型：换请求形态没用，得改模型 ID
        val chatModelHint = (lower.contains("chat") && lower.contains("completions")) ||
            lower.contains("is a chat model") ||
            lower.contains("不是图像模型") ||
            lower.contains("not an image model")
        if (chatModelHint) {
            return AiException(
                Kind.MODEL,
                "模型「${Prefs.aiModel}」是对话模型，不能画图。请改用图像模型，" +
                    "例如 agnes-image-2.5-flash（注意中间的 image）。"
            )
        }
        val modelHint = lower.contains("model") &&
            (lower.contains("not exist") || lower.contains("not found") ||
                lower.contains("invalid model") || lower.contains("unknown model"))
        if (modelHint) {
            return AiException(Kind.MODEL, "服务端不认识模型「${Prefs.aiModel}」，请核对模型 ID。$brief")
        }

        return when (code) {
            401, 403 -> AiException(Kind.AUTH, "API Key 无效或没有权限（$code）")
            402 -> AiException(Kind.QUOTA, "账户余额或额度不足（402）")
            429 -> AiException(Kind.RATE, "触发限流，稍后再试（429）")
            400, 404, 422 -> AiException(
                Kind.BAD,
                "接口不接受这个请求（$code，形态：${profile.label}）$brief"
            )
            in 500..599 -> AiException(Kind.SERVER, "服务端异常（$code），稍后重试")
            else -> AiException(Kind.OTHER, "请求失败（$code）$brief")
        }
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
