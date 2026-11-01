# 第三方组件与许可

本项目自身代码以 [MIT](LICENSE) 许可发布。下列第三方组件按各自许可证使用，
分发本项目的构建产物（APK）时请一并遵守这些条款。

## 运行时依赖

| 组件 | 版本 | 许可证 | 说明 |
| --- | --- | --- | --- |
| androidx.compose（BOM / ui / material3 / material-icons-extended） | 2024.06.00 | Apache-2.0 | |
| androidx.activity:activity-compose | 1.9.1 | Apache-2.0 | |
| androidx.lifecycle:lifecycle-runtime-compose / lifecycle-viewmodel-compose | 2.8.4 | Apache-2.0 | |
| androidx.navigation:navigation-compose | 2.7.7 | Apache-2.0 | |
| androidx.core:core-ktx | 1.13.1 | Apache-2.0 | |
| androidx.media3:media3-exoplayer / media3-session | 1.4.1 | Apache-2.0 | 播放内核与会话 |
| **eu.agno3.jcifs:jcifs-ng** | 2.1.10 | **LGPL-2.1** | SMB 客户端，见下方说明 |
| org.bouncycastle:bcprov-jdk18on | 1.78.1 | Bouncy Castle Licence | 密码学原语，MIT 风格 |
| org.slf4j:slf4j-api | 1.7.36 | MIT | 日志门面 |
| com.android.tools:desugar_jdk_libs | 2.0.4 | Apache-2.0 | Java 8+ API 脱糖 |
| Gradle Wrapper | 8.7 | Apache-2.0 | 构建工具 |

## 关于 jcifs-ng（LGPL-2.1）

这是本项目唯一需要留意的许可证：

- **只在源码中声明 Maven 依赖**：不涉及分发该库的二进制，无额外义务。
- **分发了 APK**：LGPL-2.1 第 6 条要求使用者有能力替换该库的版本。本项目源码完整公开、
  可自行重新构建，满足这一要求；若要更严格合规，可在发布页附上构建说明或提供未静态链接的中间产物。
- **不会传染**你的代码：LGPL 允许本项目代码保持 MIT（或其他）许可，无需开源衍生作品。

## 美术资源

`app/src/main/res/drawable-nodpi/` 下的 25 张封面插画（512×512 WebP）由**生成式 AI 生成后经人工筛选**
产出，未使用任何第三方素材库或既有 IP 的美术资源，随项目以 MIT 许可一并提供。

应用图标为纯几何矢量图形（`ic_launcher_foreground.xml`），非生成素材。

`ui/Covers.kt` 中的关键词表（如"三国""小猪佩奇"等）仅用于**字符串匹配**以提高内置封面命中率，
不包含也不代表任何第三方美术资源或商标授权；使用者若以此匹配到受保护内容，需自行承担相应责任。

> 说明：生成式 AI 产出内容的权利归属，在不同服务商条款与司法辖区下可能存在差异。
> 本项目声明对上述插画主张 MIT 许可，但不构成对任何第三方权利的担保。

## 免责

本项目为个人自用工具，与任何内容提供方、NAS 厂商、模型服务商均无关联。
使用者需自行确保所播放音频内容的合法性。
