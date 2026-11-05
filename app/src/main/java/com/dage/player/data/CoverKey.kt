package com.dage.player.data

import java.io.File

/**
 * 封面文件名用的 key。
 *
 * 用「媒体根目录下的相对路径」而不是完整 URL：
 * 换协议（smb→https）或换地址（内网→公网）之后，完整 URL 变了，
 * 若继续拿它做 md5，已生成的封面会全部对不上、看起来像"全丢了"。
 * 相对路径在家在外都一样，所以 key 稳定。
 */
object CoverKey {

    /** 把任意形式的 URL 折成相对路径，如 "儿童故事/第1季/" */
    fun of(url: String, base: String = Prefs.mediaBase): String {
        val u = url.trim()
        if (u.isBlank()) return ""
        val b = base.trim().trimEnd('/')
        if (b.isNotEmpty() && u.startsWith("$b/")) {
            return u.removePrefix("$b/").trimEnd('/') + "/"
        }
        // base 对不上时的兜底：剥掉 scheme://host/ 之后的部分
        val noScheme = if (u.contains("://")) u.substringAfter("://") else u
        val afterHost = if (noScheme.contains('/')) noScheme.substringAfter('/') else noScheme
        return afterHost.trimEnd('/') + "/"
    }

    /**
     * 取某个文件夹的封面文件。新 key 不存在时顺手把旧 key 的文件改名过来，
     * 这样升级后不用重新生成（也就不会白花额度）。
     */
    fun fileFor(dir: File, folderUrl: String, md5: (String) -> String): File {
        val rel = of(folderUrl)
        val target = File(dir, md5(rel) + ".jpg")
        if (!target.exists() || target.length() == 0L) migrateLegacy(dir, rel, target, md5)
        return target
    }

    private fun migrateLegacy(dir: File, rel: String, target: File, md5: (String) -> String) {
        val legacy = Prefs.coverLegacyBase
        if (legacy.isBlank() || rel.isBlank()) return
        // 旧实现：md5(完整 URL)，URL = 旧 base + 相对路径
        val old = File(dir, md5(legacy.trimEnd('/') + "/" + rel) + ".jpg")
        if (!old.exists() || old.length() == 0L) return
        runCatching {
            old.copyTo(target, overwrite = true)
            old.delete()
        }
    }
}
