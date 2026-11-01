package com.dage.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val sections = listOf(
    "一、软件性质" to
        "本软件（大哥的播放器）为个人开发的局域网播放工具，免费提供、按“现状”交付，不对可用性、稳定性、兼容性作任何明示或默示担保。作者不保证软件无缺陷，也不承诺提供持续更新或技术支持。",

    "二、使用风险" to
        "1. 本软件仅读取你在 NAS 上已有的音视频文件，不修改、不上传原始文件。\n" +
        "2. 因网络、NAS 设置、系统升级、存储故障等原因造成的播放中断、进度记录丢失、封面丢失，作者不承担责任，请自行做好数据备份。\n" +
        "3. 睡眠定时、断点续播等功能依赖手机系统调度，可能因省电策略被系统终止，由此产生的后果由使用者自行承担。",

    "三、账号与凭据" to
        "1. NAS 的账号、密码保存在本机应用私有目录，不会上传到任何服务器。\n" +
        "2. AI 封面功能所使用的第三方 API Key 由使用者自行申请、自行承担费用。Key 在本机通过 Android Keystore 加密存储，不会写入日志、不会上传，卸载应用即失效。\n" +
        "3. 一旦他人取得你的设备或 root 权限，仍有可能读取到 Key。请在使用前自行评估风险；如怀疑泄露，应立即到服务商控制台吊销该 Key。\n" +
        "4. 你与非本机的第三方服务（如模型服务商）之间的账号、计费、服务条款等关系与作者无关。",

    "四、AI 生成内容" to
        "1. AI 封面由第三方图像模型生成，仅用于界面展示。生成结果可能不准确、不完整或不符合预期，作者不对其内容、版权状态作任何保证。\n" +
        "2. 生成时会把“文件夹名称、部分文件名”发送给你所配置的第三方服务商。请勿把包含隐私信息的目录名用于生成。\n" +
        "3. 第三方模型服务可能随时调整价格、限流、变更模型或停止服务，由此导致的功能不可用或产生费用，由使用者自行承担。\n" +
        "4. 生成内容如涉及未成年人阅读场景，请自行审核后再使用。",

    "五、使用限制" to
        "使用者不得利用本软件从事任何违反法律法规、侵犯他人权益、传播违法或有害信息的行为。因使用者的行为产生的全部法律责任由使用者自行承担。",

    "六、责任限制" to
        "在适用法律允许的最大范围内，作者不对因使用或无法使用本软件而产生的任何直接、间接、附带、特殊或惩罚性损害承担责任，包括但不限于数据丢失、设备损坏、利润损失、第三方费用。",

    "七、条款变更" to
        "本条款可能随版本更新而调整，更新后随新版本一并发布，继续使用即视为接受更新后的条款。"
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = "关于与免责条款", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                "大哥的播放器 v1.9.4",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "局域网 NAS 音频播放器 · 个人开发 · 免费使用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "下载、安装或使用本软件，即表示你已阅读并同意以下条款。如不同意，请停止使用并卸载。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                lineHeight = 19.sp,
            )

            sections.forEach { (title, body) ->
                Spacer(Modifier.height(18.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "本软件使用了以下开源组件：Jetpack Compose、Media3 ExoPlayer、jcifs-ng、BouncyCastle、Kotlin 协程。相关许可归各自作者所有。\n" +
                    "内置封面插画由 AI 生成，仅用于本软件界面展示。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
