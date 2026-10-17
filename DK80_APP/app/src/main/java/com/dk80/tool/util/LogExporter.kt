package com.dk80.tool.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一键导出日志到本地文件并通过系统分享发送 */
object LogExporter {
    fun export(context: Context, logs: List<String>, filenameHint: String = "dk80_log"): Uri? {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "${filenameHint}_$ts.txt")
        file.writeText(logs.joinToString("\n"))
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun share(context: Context, uri: Uri, subject: String = "DK80 日志") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出日志").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
