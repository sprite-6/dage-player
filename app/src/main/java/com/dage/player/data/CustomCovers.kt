package com.dage.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * 手动给某个文件夹指定封面。图片缩成 512×512 方图存进应用私有目录，
 * 不走相册权限（用的是系统图片选择器），也不会随卸载残留。
 */
object CustomCovers {

    private const val SIZE = 512

    private fun dir(context: Context): File {
        val d = File(context.filesDir, "covers")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun fileFor(context: Context, folderUrl: String): File =
        File(dir(context), md5(norm(folderUrl)) + ".jpg")

    /** 目录 URL 可能带也可能不带结尾斜杠，统一成带斜杠再算 key */
    private fun norm(url: String): String = url.trimEnd('/') + "/"

    fun has(context: Context, folderUrl: String): Boolean {
        if (folderUrl.isBlank()) return false
        val f = fileFor(context, folderUrl)
        return f.exists() && f.length() > 0
    }

    fun count(context: Context): Int =
        dir(context).listFiles()?.count { it.isFile } ?: 0

    fun clear(context: Context, folderUrl: String): Boolean {
        val ok = fileFor(context, folderUrl).delete()
        Prefs.notifyCoversChanged()
        return ok
    }

    fun clearAll(context: Context): Int {
        var n = 0
        dir(context).listFiles()?.forEach { if (it.isFile && it.delete()) n++ }
        Prefs.notifyCoversChanged()
        return n
    }

    /** 从相册选中的 Uri 生成封面，成功返回 true */
    fun save(context: Context, folderUrl: String, uri: Uri): Boolean {
        if (folderUrl.isBlank()) return false
        return try {
            val bitmap = decodeSquare(context, uri) ?: return false
            val tmp = File(dir(context), md5(folderUrl) + ".tmp")
            FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            val target = fileFor(context, folderUrl)
            if (target.exists()) target.delete()
            val ok = tmp.renameTo(target)
            bitmap.recycle()
            Prefs.notifyCoversChanged()
            ok
        } catch (e: Exception) {
            false
        }
    }

    private fun decodeSquare(context: Context, uri: Uri): Bitmap? {
        // 第一遍只读尺寸，按需降采样，避免大图 OOM
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null

        var sample = 1
        while (minOf(w, h) / sample > SIZE * 2) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = open(context, uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val side = minOf(raw.width, raw.height)
        val x = (raw.width - side) / 2
        val y = (raw.height - side) / 2
        val cropped = Bitmap.createBitmap(raw, x, y, side, side)
        return Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true)
    }

    private fun open(context: Context, uri: Uri): InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    private fun md5(s: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
