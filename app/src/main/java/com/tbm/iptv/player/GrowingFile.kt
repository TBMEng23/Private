package com.tbm.iptv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.RandomAccessFile

/**
 * קורא קובץ הקלטה שעדיין נכתב. כשמגיע לסוף – מחכה לנתונים חדשים כל עוד ההקלטה פעילה.
 * כך צופים בערוץ מתוך ההקלטה עם חיבור אחד בלבד לספק (והשהיית שידור חי עובדת).
 */
class GrowingFileDataSource(private val file: File, private val active: () -> Boolean) : BaseDataSource(false) {
    private var raf: RandomAccessFile? = null
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val r = RandomAccessFile(file, "r")
        r.seek(dataSpec.position)
        raf = r
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val r = raf ?: return C.RESULT_END_OF_INPUT
        var n = r.read(buffer, offset, length)
        var waited = 0
        while (n <= 0) {
            if (!active() && waited > 600) return C.RESULT_END_OF_INPUT
            if (waited > 20_000) return C.RESULT_END_OF_INPUT
            Thread.sleep(150)
            waited += 150
            n = r.read(buffer, offset, length)
        }
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try { raf?.close() } finally {
            raf = null
            if (uri != null) { uri = null; transferEnded() }
        }
    }

    class Factory(private val file: File, private val active: () -> Boolean) : DataSource.Factory {
        override fun createDataSource(): DataSource = GrowingFileDataSource(file, active)
    }
}
