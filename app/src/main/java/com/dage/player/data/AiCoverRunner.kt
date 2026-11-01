package com.dage.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AI 封面的串行队列。
 * 免费额度普遍有「每分钟几次 + 每天几次」的限制，所以这里：
 * 并发恒为 1、两次请求之间强制间隔、429 自动退避重试、每日用量封顶。
 */
object AiCoverRunner {

    /** hint：用户给这个文件夹单独写的画面描述，非空时优先于文件名 */
    data class Task(
        val url: String,
        val name: String,
        val files: List<String>,
        val hint: String = "",
    )

    data class Progress(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val currentName: String? = null,
        val message: String? = null,
    )

    val progress = MutableStateFlow(Progress())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start(context: Context, tasks: List<Task>) {
        if (tasks.isEmpty()) return
        if (progress.value.running) {
            progress.value = progress.value.copy(message = "已经有一批在生成了，等它跑完")
            return
        }
        if (!Prefs.aiReady) {
            progress.value = Progress(message = "请先在设置里开启并填写 API Key")
            return
        }
        val app = context.applicationContext
        job = scope.launch {
            var done = 0
            var stop: String? = null
            var lastError: String? = null
            progress.value = Progress(running = true, total = tasks.size)

            outer@ for ((index, task) in tasks.withIndex()) {
                if (!isActive) break
                progress.value = progress.value.copy(currentName = task.name, message = null)

                var attempt = 0
                while (true) {
                    try {
                        AiCovers.generate(app, task.url, task.name, task.files, task.hint)
                        done++
                        progress.value = progress.value.copy(done = done)
                        break
                    } catch (e: AiCovers.AiException) {
                        when {
                            e.kind == AiCovers.Kind.RATE && attempt < 2 -> {
                                attempt++
                                progress.value = progress.value.copy(
                                    message = "被限流，等 60 秒后重试（第 $attempt 次）"
                                )
                                delay(60_000)
                            }
                            e.kind == AiCovers.Kind.AUTH || e.kind == AiCovers.Kind.QUOTA ||
                                e.kind == AiCovers.Kind.MODEL -> {
                                stop = e.message
                                break@outer
                            }
                            else -> {
                                lastError = "${task.name}：${e.message}"
                                progress.value = progress.value.copy(message = lastError)
                                break
                            }
                        }
                    } catch (e: Exception) {
                        lastError = "${task.name}：${e.message ?: "生成失败"}"
                        progress.value = progress.value.copy(message = lastError)
                        break
                    }
                }

                if (index < tasks.lastIndex) {
                    delay(Prefs.aiIntervalSec.coerceAtLeast(3) * 1000L)
                }
            }

            val summary = stop
                ?: when {
                    done > 0 -> "已生成 $done 张"
                    lastError != null -> lastError
                    else -> "没有生成成功"
                }
            progress.value = Progress(running = false, done = done, total = tasks.size, message = summary)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        progress.value = progress.value.copy(running = false, currentName = null, message = "已取消")
    }
}
