package com.dage.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 所有设置项都用 SharedPreferences 同步读写：播放数据源需要在非协程线程同步拿到凭据。
 */
object Prefs {

    private lateinit var sp: SharedPreferences

    /** 配置或主题变化时自增，UI 用它触发重载 */
    val revision = MutableStateFlow(0)

    /** 0 跟随系统 / 1 浅色 / 2 深色 */
    val themeFlow = MutableStateFlow(0)

    @Synchronized
    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("dage_prefs", Context.MODE_PRIVATE)
        themeFlow.value = themeMode
        migrateAiBaseUrl()
        migrateCoverKey()
    }

    /**
     * 封面 key 从「完整 URL 的 md5」改成「相对路径的 md5」。
     * 趁用户还没换协议，先把当前地址记下来，之后访问到哪个文件夹就把哪个的封面文件改名。
     */
    private fun migrateCoverKey() {
        if (sp.getBoolean("cover_key_v2", false)) return
        val base = nas()?.baseUrl
        if (base.isNullOrBlank()) return // 还没配置，等配置好再说
        sp.edit()
            .putString("cover_legacy_base", base)
            .putBoolean("cover_key_v2", true)
            .apply()
    }

    /** v1.9 起默认用国内节点；老版本存过的国际站地址自动换过来 */
    private fun migrateAiBaseUrl() {
        val saved = sp.getString("ai_base_url", "").orEmpty()
        if (saved.trimEnd('/') == "https://apihub.agnes-ai.com/v1") {
            sp.edit().putString("ai_base_url", defaultAiBaseUrl).remove("ai_profile").apply()
        }
    }

    private fun bump() {
        revision.value += 1
    }

    var host: String
        get() = sp.getString("host", "").orEmpty()
        set(v) {
            sp.edit().putString("host", v.trim()).apply(); bump()
        }

    var share: String
        get() = sp.getString("share", "").orEmpty()
        set(v) {
            sp.edit().putString("share", v.trim()).apply(); bump()
        }

    var root: String
        get() = sp.getString("root", "").orEmpty()
        set(v) {
            sp.edit().putString("root", v.trim()).apply(); bump()
        }

    var user: String
        get() = sp.getString("user", "").orEmpty()
        set(v) {
            sp.edit().putString("user", v.trim()).apply(); bump()
        }

    var pass: String
        get() = sp.getString("pass", "").orEmpty()
        set(v) {
            sp.edit().putString("pass", v).apply(); bump()
        }

    var domain: String
        get() = sp.getString("domain", "").orEmpty()
        set(v) {
            sp.edit().putString("domain", v.trim()).apply(); bump()
        }

    var themeMode: Int
        get() = sp.getInt("theme", 0)
        set(v) {
            sp.edit().putInt("theme", v).apply()
            themeFlow.value = v
        }

    /** 文件夹展示方式：0 大方块（两列） / 1 紧凑横条 */
    var folderView: Int
        get() = sp.getInt("folder_view", 0)
        set(v) {
            sp.edit().putInt("folder_view", v).apply(); bump()
        }

    fun pinnedFolders(): Set<String> = sp.getStringSet("pins", emptySet())?.toSet() ?: emptySet()

    fun isPinned(url: String): Boolean = pinnedFolders().contains(url)

    /** 切换顶置，返回切换后的状态 */
    fun togglePin(url: String): Boolean {
        if (url.isBlank()) return false
        val set = pinnedFolders().toMutableSet()
        val pinnedNow = if (set.contains(url)) {
            set.remove(url)
            false
        } else {
            set.add(url)
            true
        }
        sp.edit().putStringSet("pins", set).apply()
        bump()
        return pinnedNow
    }

    val configured: Boolean
        get() = host.isNotBlank() && share.isNotBlank()

    fun nas(): NasCfg? = if (!configured) null else NasCfg(
        host = host,
        share = share,
        root = root,
        user = user,
        pass = pass,
        domain = domain,
    )

    /** 当前媒体根地址，封面 key 靠它算相对路径；换协议/换地址时它变、相对路径不变 */
    val mediaBase: String
        get() = nas()?.baseUrl.orEmpty()

    /** 升级到「相对路径 key」之前用的旧地址，用于把老封面文件改名，不做就要重新生成 */
    val coverLegacyBase: String
        get() = sp.getString("cover_legacy_base", "").orEmpty()

    fun saveNas(host: String, share: String, root: String, user: String, pass: String, domain: String) {
        sp.edit()
            .putString("host", host.trim())
            .putString("share", share.trim())
            .putString("root", root.trim())
            .putString("user", user.trim())
            .putString("pass", pass)
            .putString("domain", domain.trim())
            .apply()
        bump()
    }

    /** 主题封面总开关：关掉后全部回到渐变色块 */
    var coversEnabled: Boolean
        get() = sp.getBoolean("covers_enabled", true)
        set(v) {
            sp.edit().putBoolean("covers_enabled", v).apply(); bump()
        }

    /** 自定义封面变化后通知界面刷新 */
    fun notifyCoversChanged() = bump()

    // ---------- AI 封面（BYOK） ----------

    val defaultAiBaseUrl = "https://api.agnes-ai.cn/v1"
    val defaultAiModel = "agnes-image-2.5-flash"
    val defaultAiStyle =
        "纯插画画面：扁平矢量插画，柔和马卡龙配色，圆角造型，主体居中，正方形构图，简洁背景，画面上不要出现任何文字、字母或符号。"

    var aiEnabled: Boolean
        get() = sp.getBoolean("ai_enabled", false)
        set(v) {
            sp.edit().putBoolean("ai_enabled", v).apply(); bump()
        }

    var aiBaseUrl: String
        get() = sp.getString("ai_base_url", defaultAiBaseUrl).orEmpty()
        set(v) {
            sp.edit().putString("ai_base_url", v.trim().ifBlank { defaultAiBaseUrl }).apply(); bump()
        }

    var aiModel: String
        get() = sp.getString("ai_model", defaultAiModel).orEmpty()
        set(v) {
            sp.edit().putString("ai_model", v.trim().ifBlank { defaultAiModel }).apply(); bump()
        }

    /** 尺寸档：Agnes 用 1K/2K/3K/4K，其它家可以写 1024x1024 */
    var aiSize: String
        get() = sp.getString("ai_size", "1K").orEmpty()
        set(v) {
            sp.edit().putString("ai_size", v.trim()).apply(); bump()
        }

    var aiRatio: String
        get() = sp.getString("ai_ratio", "1:1").orEmpty()
        set(v) {
            sp.edit().putString("ai_ratio", v.trim()).apply(); bump()
        }

    var aiStyle: String
        get() = sp.getString("ai_style", defaultAiStyle).orEmpty()
        set(v) {
            sp.edit().putString("ai_style", v.trim()).apply(); bump()
        }

    /** 两次请求的最小间隔（秒），免费额度下要留足余量 */
    var aiIntervalSec: Int
        get() = sp.getInt("ai_interval", 12)
        set(v) {
            sp.edit().putInt("ai_interval", v.coerceIn(3, 120)).apply(); bump()
        }

    /** API Key：只在内存里出现明文，落盘是 Keystore 加密后的密文 */
    var aiKey: String
        get() = if (sp.getString("ai_key_enc", "").isNullOrEmpty()) "" else SecureStore.decrypt(sp.getString("ai_key_enc", "")!!).orEmpty()
        set(v) {
            val enc = if (v.isBlank()) "" else SecureStore.encrypt(v.trim()).orEmpty()
            sp.edit().putString("ai_key_enc", enc).apply(); bump()
        }

    val aiReady: Boolean
        get() = aiEnabled && aiBaseUrl.isNotBlank() && aiModel.isNotBlank() && aiKey.isNotBlank()

    /**
     * 已协商成功的接口形态：openai / agnes / simple。
     * 空字符串表示还没测过，下次生成前会自动探测一次。
     */
    var aiProfile: String
        get() = sp.getString("ai_profile", "").orEmpty()
        set(v) {
            sp.edit().putString("ai_profile", v).apply(); bump()
        }

    /** 换了 Base URL / 模型 / 尺寸就认为形态失效，需要重新探测 */
    fun invalidateAiProfile() {
        sp.edit().remove("ai_profile").apply(); bump()
    }
}
