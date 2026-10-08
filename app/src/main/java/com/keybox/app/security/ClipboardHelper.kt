package com.keybox.app.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/**
 * 剪贴板管理：复制后定时清除，Android 13+ 标记敏感内容。
 */
object ClipboardHelper {

    fun copy(context: Context, label: String, text: String, clearAfterSeconds: Int = 30) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        cm.setPrimaryClip(clip)

        // 定时清除（仅当剪贴板内容仍是本次复制的内容时）
        if (clearAfterSeconds > 0) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.postDelayed({
                val current = cm.primaryClip
                if (current != null && current.itemCount > 0 &&
                    current.getItemAt(0).text.toString() == text) {
                    cm.clearPrimaryClip()
                }
            }, clearAfterSeconds * 1000L)
        }
    }
}
