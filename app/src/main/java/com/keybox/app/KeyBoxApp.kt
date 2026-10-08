package com.keybox.app

import android.app.Application
import android.os.Build
import android.util.Log
import com.keybox.app.data.crypto.CryptoManager
import com.keybox.app.data.db.KeyBoxDatabase
import com.keybox.app.data.repository.PasswordRepository
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class KeyBoxApp : Application() {

    lateinit var crypto: CryptoManager
        private set
    lateinit var repository: PasswordRepository
        private set

    /** 崩溃日志目录（无 adb 时用于诊断闪退）。 */
    fun crashLogDir(): File = File(filesDir, "crash_logs").apply { mkdirs() }

    /** 读取全部崩溃日志内容（按时间升序拼接）。 */
    fun readCrashLogs(): String {
        val dir = crashLogDir()
        val files = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
        if (files.isEmpty()) return ""
        return buildString {
            files.forEach { f ->
                append("===== ").append(f.name).append(" =====\n")
                append(f.readText()).append('\n')
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        crypto = CryptoManager(this)
        val db = KeyBoxDatabase.getInstance(this)
        repository = PasswordRepository(db, crypto)
    }

    /**
     * 安装全局崩溃日志捕获，将堆栈写入应用私有目录，
     * 便于在无 adb 环境时诊断闪退原因。
     */
    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val log = buildString {
                    append("Time: ").append(System.currentTimeMillis()).append('\n')
                    append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                    append("Thread: ").append(thread.name).append('\n')
                    append("Exception: ").append(throwable.toString()).append('\n')
                    append(sw.toString())
                }
                val dir = File(filesDir, "crash_logs")
                dir.mkdirs()
                File(dir, "crash_${System.currentTimeMillis()}.txt").writeText(log)
                Log.e("KeyBox", "Crash captured:\n$log")
            } catch (_: Exception) {
                // 捕获失败不影响默认处理
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}

