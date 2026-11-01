package com.dage.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.dage.player.data.Smb
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.IOException

/**
 * 直接以随机读取方式从 NAS 读音频字节，支持 seek。
 */
class SmbDataSource : BaseDataSource(true) {

    private var raf: SmbRandomAccessFile? = null
    private var uri: Uri? = null
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val target = dataSpec.uri.toString()
        val ctx = try {
            Smb.ctx()
        } catch (e: Exception) {
            throw IOException(e.message ?: "无法连接 NAS", e)
        }
        val file = SmbFile(target, ctx)
        val length = file.length()
        val stream = SmbRandomAccessFile(file, "r")
        if (dataSpec.position > 0) stream.seek(dataSpec.position)

        raf = stream
        uri = dataSpec.uri
        opened = true
        remaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            length > 0 -> length - dataSpec.position
            else -> C.LENGTH_UNSET.toLong()
        }
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val stream = raf ?: return C.RESULT_END_OF_INPUT
        val toRead = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), remaining).toInt()
        val read = try {
            stream.read(buffer, offset, toRead)
        } catch (e: Exception) {
            throw IOException(e.message ?: "读取失败", e)
        }
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            raf?.close()
        } catch (_: Exception) {
        } finally {
            raf = null
            uri = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}

class SmbDataSourceFactory : DataSource.Factory {
    override fun createDataSource(): DataSource = SmbDataSource()
}
