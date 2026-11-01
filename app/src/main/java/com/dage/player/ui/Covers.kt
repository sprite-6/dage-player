package com.dage.player.ui

import androidx.annotation.DrawableRes
import com.dage.player.R

/**
 * 内置主题封面：按文件夹名（及目录里的名字）关键词匹配一张插画。
 * 全部为本地资源，不联网、不额外占空间（共约 560KB）。
 */
object Covers {

    private data class Theme(@DrawableRes val res: Int, val keys: List<String>)

    // 顺序即优先级：越具体的主题越靠前，避免「睡前童话」被「童话」抢走
    private val themes = listOf(
        Theme(R.drawable.cover_sanguo, listOf("三国", "桃园", "诸葛亮", "曹操", "刘备", "关羽", "张飞", "赤壁", "草船借箭")),
        Theme(R.drawable.cover_xiyou, listOf("西游", "孙悟空", "唐僧", "取经", "八戒", "悟能", "大闹天宫")),
        Theme(R.drawable.cover_myth, listOf("神话", "女娲", "后羿", "山海经", "精卫", "夸父", "盘古", "封神", "神仙")),
        Theme(R.drawable.cover_history, listOf("历史", "上下五千年", "朝代", "中国史", "资治通鉴", "史记", "古人")),
        Theme(R.drawable.cover_idiom, listOf("成语", "典故", "谚语")),
        Theme(R.drawable.cover_fable, listOf("寓言", "伊索", "克雷洛夫", "哲理故事")),
        Theme(R.drawable.cover_bedtime, listOf("睡前", "晚安", "哄睡", "睡觉", "摇篮", "催眠")),
        Theme(R.drawable.cover_nursery, listOf("儿歌", "童谣", "手指谣", "律动", "儿童歌曲")),
        Theme(R.drawable.cover_music, listOf("音乐", "乐器", "钢琴", "小提琴", "古典", "莫扎特", "贝多芬", "交响")),
        Theme(R.drawable.cover_english, listOf("英语", "英文", "单词", "自然拼读", "分级阅读", "双语")),
        Theme(R.drawable.cover_math, listOf("数学", "算术", "奥数", "数字", "几何", "口算")),
        Theme(R.drawable.cover_detective, listOf("侦探", "推理", "破案", "福尔摩斯", "谜案", "悬疑")),
        Theme(R.drawable.cover_dinosaur, listOf("恐龙", "霸王龙", "侏罗纪", "史前", "远古")),
        Theme(R.drawable.cover_space, listOf("太空", "宇宙", "星球", "航天", "火箭", "银河", "天文")),
        Theme(R.drawable.cover_ocean, listOf("海洋", "海底", "鲸", "珊瑚", "美人鱼", "海岛", "水母")),
        Theme(R.drawable.cover_vehicle, listOf("汽车", "工程车", "挖掘机", "火车", "交通", "托马斯", "赛车", "飞机")),
        Theme(R.drawable.cover_plant, listOf("植物", "花卉", "种子", "大自然", "四季", "昆虫记")),
        Theme(R.drawable.cover_body, listOf("人体", "身体", "器官", "血液", "牙齿", "生病", "医院", "细菌")),
        Theme(R.drawable.cover_safety, listOf("安全", "消防", "防拐", "自我保护", "地震", "危险")),
        Theme(R.drawable.cover_emotion, listOf("情绪", "生气", "害怕", "友谊", "情商", "心理", "成长", "性格")),
        Theme(R.drawable.cover_habit, listOf("习惯", "礼仪", "规矩", "刷牙", "自律", "自理", "整理")),
        Theme(R.drawable.cover_science, listOf("科普", "百科", "为什么", "十万个", "科学", "物理", "化学", "实验")),
        Theme(R.drawable.cover_animal, listOf("动物", "小熊", "兔子", "狐狸", "森林", "动物园", "猫", "狗", "小猪", "猴子")),
        Theme(R.drawable.cover_fairy, listOf("童话", "公主", "王子", "灰姑娘", "白雪", "安徒生", "格林", "小王子", "城堡", "魔法")),
        // 兜底：泛指类目录（儿童故事/绘本/读物…）用"书堆"插画，避免和具体书名撞图
        Theme(R.drawable.cover_story, listOf("故事", "绘本", "读物", "有声", "合集", "专辑", "精选")),
    )

    /** 命中返回插画资源，未命中返回 null（继续用渐变色块） */
    @DrawableRes
    fun forName(name: String): Int? {
        if (name.isBlank()) return null
        val n = name.lowercase()
        for (theme in themes) {
            if (theme.keys.any { n.contains(it) }) return theme.res
        }
        return null
    }

    /** 让「小猪佩奇」这类只有动物名的目录也能命中：拿目录名 + 前几集文件名一起判断 */
    @DrawableRes
    fun forFolder(folderName: String, fileNames: List<String>): Int? {
        forName(folderName)?.let { return it }
        val joined = fileNames.take(8).joinToString(" ")
        if (joined.isBlank()) return null
        return forName(joined)
    }

    /**
     * 从目录 URL 反推可用于匹配的候选名字，近的在前：
     * smb://nas/media/儿童故事/第1季/ -> ["第1季", "儿童故事", "media"]
     * 这样「第1季」这种无意义的名字也能借用上层「儿童故事」的主题。
     */
    fun ancestorNames(folderUrl: String, depth: Int = 2): List<String> {
        if (folderUrl.isBlank()) return emptyList()
        val path = folderUrl.substringAfter("smb://", "").trimEnd('/')
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return emptyList()
        // parts[0] 是主机或主机+共享名，通常不参与匹配；从后往前取 depth 段目录名
        val names = ArrayList<String>(depth)
        var i = parts.size - 1
        var taken = 0
        while (i >= 0 && taken < depth) {
            val seg = parts[i]
            // 跳过主机段（含点号或以端口结尾）与共享段可能的干扰
            if (i > 0 && seg.contains('.')) break
            names += seg
            taken++
            i--
        }
        return names
    }

    /**
     * 带上层目录一起匹配。先按最近的名字找具体主题，
     * 再退一步找泛指主题——避免「第1季」回落到渐变色块。
     */
    @DrawableRes
    fun forFolderPath(folderUrl: String, fileNames: List<String>): Int? {
        val names = ancestorNames(folderUrl, 3)
        for (n in names) {
            forName(n)?.let { return it }
        }
        val joined = fileNames.take(8).joinToString(" ")
        if (joined.isNotBlank()) forName(joined)?.let { return it }
        return null
    }
}
