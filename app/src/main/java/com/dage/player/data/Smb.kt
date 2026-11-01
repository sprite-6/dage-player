package com.dage.player.data

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.IOException
import java.util.Properties

data class NasCfg(
    val host: String,
    val share: String,
    val root: String,
    val user: String,
    val pass: String,
    val domain: String,
) {
    val hostOnly: String get() = host.trim().removePrefix("smb://").trimEnd('/')

    val baseUrl: String
        get() {
            val h = hostOnly
            val s = share.trim().trim('/')
            val r = root.trim().trim('/')
            val sb = StringBuilder("smb://").append(h).append('/')
            if (s.isNotEmpty()) sb.append(s).append('/')
            if (r.isNotEmpty()) sb.append(r).append('/')
            return sb.toString()
        }

    val key: String get() = "$host|$share|$user|${pass.hashCode()}|$domain"
}

data class Entry(
    val name: String,
    val isDir: Boolean,
    val url: String,
    val size: Long,
)

object Smb {

    private val versions = listOf(
        "SMB202" to "SMB302",
        "SMB210" to "SMB210",
        "SMB1" to "SMB1",
    )

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cached: CIFSContext? = null

    private val listingCache = HashMap<String, Pair<Long, List<Entry>>>()

    private val audioExt = setOf(
        "mp3", "m4a", "m4b", "aac", "flac", "wav", "wave", "ogg", "oga", "opus", "mka", "m4r",
    )

    fun reload() {
        cached = null
        cachedKey = null
        synchronized(listingCache) { listingCache.clear() }
    }

    fun isAudio(name: String): Boolean {
        val i = name.lastIndexOf('.')
        if (i < 0) return false
        return name.substring(i + 1).lowercase() in audioExt
    }

    private fun buildCfg(cfg: NasCfg, minV: String, maxV: String): CIFSContext {
        val p = Properties()
        p["jcifs.smb.client.enableSMB2"] = "true"
        p["jcifs.smb.client.dfs.disabled"] = "true"
        p["jcifs.smb.client.responseTimeout"] = "30000"
        p["jcifs.smb.client.soTimeout"] = "60000"
        p["jcifs.netbios.cachePolicy"] = "600"
        p["jcifs.smb.client.minVersion"] = minV
        p["jcifs.smb.client.maxVersion"] = maxV
        p["jcifs.resolveOrder"] = "DNS"
        val base = BaseContext(PropertyConfiguration(p))
        val auth = NtlmPasswordAuthenticator(
            cfg.domain.ifBlank { null },
            cfg.user.ifBlank { "guest" },
            cfg.pass,
        )
        return base.withCredentials(auth)
    }

    /** 依次尝试 SMB3 -> SMB2.1 -> SMB1，第一个能列出目录的作为本次会话连接 */
    fun ctx(cfg: NasCfg = Prefs.nas() ?: throw IOException("还没有配置 NAS")): CIFSContext {
        val old = cached
        if (old != null && cachedKey == cfg.key) return old
        var last: Exception? = null
        for ((minV, maxV) in versions) {
            try {
                val c = buildCfg(cfg, minV, maxV)
                val f = SmbFile(cfg.baseUrl, c)
                f.listFiles()
                cached = c
                cachedKey = cfg.key
                return c
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException(last?.message ?: "无法连接 NAS", last)
    }

    fun list(url: String, useCache: Boolean = true): List<Entry> {
        if (useCache) {
            synchronized(listingCache) {
                val hit = listingCache[url]
                if (hit != null && System.currentTimeMillis() - hit.first < 60_000) return hit.second
            }
        }
        val dir = if (url.endsWith("/")) url else "$url/"
        val f = SmbFile(dir, ctx())
        val entries = f.listFiles().mapNotNull { child ->
            val isDir = child.isDirectory
            val raw = child.name ?: return@mapNotNull null
            val name = raw.trimEnd('/')
            if (name.isBlank()) return@mapNotNull null
            if (!isDir && !isAudio(name)) return@mapNotNull null
            var u = child.url.toString()
            if (isDir && !u.endsWith("/")) u += "/"
            Entry(name = name, isDir = isDir, url = u, size = if (isDir) 0L else runCatching { child.length() }.getOrDefault(0L))
        }.sortedWith(compareByDescending<Entry> { it.isDir }.thenComparator { a, b -> naturalCompare(a.name, b.name) })
        synchronized(listingCache) { listingCache[url] = System.currentTimeMillis() to entries }
        return entries
    }

    /** 列出服务器上的共享文件夹，用于首次配置 */
    fun listShares(cfg: NasCfg): List<String> {
        var last: Exception? = null
        for ((minV, maxV) in versions) {
            try {
                val c = buildCfg(cfg, minV, maxV)
                val root = SmbFile("smb://${cfg.hostOnly}/", c)
                return root.listFiles()
                    .mapNotNull { it.name?.trimEnd('/') }
                    .filter { it.isNotBlank() }
                    .sorted()
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException(last?.message ?: "无法读取共享列表", last)
    }

    fun test(cfg: NasCfg): String {
        val url = if (cfg.share.isBlank()) "smb://${cfg.hostOnly}/" else cfg.baseUrl
        val c = ctx(cfg)
        val files = SmbFile(url, c).listFiles()
        val dirs = files.count { it.isDirectory }
        val audio = files.count { !it.isDirectory && isAudio(it.name ?: "") }
        return "连接成功：${dirs} 个文件夹，${audio} 个音频文件"
    }
}

/** 自然排序：第2集 排在 第10集 前面 */
fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            val si = i
            val sj = j
            while (i < a.length && a[i].isDigit()) i++
            while (j < b.length && b[j].isDigit()) j++
            val na = a.substring(si, i).trimStart('0')
            val nb = b.substring(sj, j).trimStart('0')
            if (na.length != nb.length) return na.length - nb.length
            val c = na.compareTo(nb)
            if (c != 0) return c
        } else {
            val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (c != 0) return c
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}
