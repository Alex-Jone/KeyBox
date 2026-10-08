package com.keybox.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.keybox.app.data.backup.CsvImporter
import com.keybox.app.data.backup.KbxFormat
import com.keybox.app.data.repository.PasswordRepository
import com.keybox.app.ui.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var exportPwd by remember { mutableStateOf("") }
    var importPwd by remember { mutableStateOf("") }

    // 导出：选择保存位置
    val createExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        appViewModel.resumeAutoLock()
        if (uri != null) {
            scope.launch {
                status = "正在导出..."
                try {
                    val result = exportToUri(context, uri, exportPwd)
                    status = result
                    if (result.startsWith("导出成功")) {
                        appViewModel.recordBackupDone()
                    }
                } catch (e: Exception) {
                    status = "导出失败：${e.message}"
                }
            }
        }
    }

    // 导入 .kbx
    val pickKbx = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        appViewModel.resumeAutoLock()
        if (uri != null) {
            scope.launch {
                status = "正在导入..."
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                    if (bytes == null) {
                        status = "无法读取文件"
                    } else {
                        val decoded = KbxFormat.decode(bytes, importPwd.toCharArray())
                        if (decoded == null) {
                            status = "解密失败：主密码错误或文件损坏"
                        } else {
                            val result = appViewModel.repository.importAll(
                                decoded.items, decoded.categories,
                                PasswordRepository.ImportMode.MERGE
                            )
                            status = "导入成功：新增 ${result.added} 条，更新 ${result.updated} 条，跳过 ${result.skipped} 条"
                        }
                    }
                } catch (e: Exception) {
                    status = "导入失败：${e.message}"
                }
            }
        }
    }

    // 导入 CSV
    val pickCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        appViewModel.resumeAutoLock()
        if (uri != null) {
            scope.launch {
                status = "正在导入 CSV..."
                try {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    if (text == null) {
                        status = "无法读取文件"
                    } else {
                        val entities = CsvImporter.parse(text, (context as com.keybox.app.KeyBoxApp).crypto)
                        val result = appViewModel.repository.importAll(
                            entities, emptyList(),
                            PasswordRepository.ImportMode.MERGE
                        )
                        status = "CSV 导入成功：新增 ${result.added} 条。请删除源 CSV 文件。"
                    }
                } catch (e: Exception) {
                    status = "CSV 导入失败：${e.message}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备份与迁移") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ===== 导出 =====
            Text("导出加密备份 (.kbx)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "备份使用独立随机盐 + 更强加密参数，需输入主密码验证。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = exportPwd, onValueChange = { exportPwd = it },
                label = { Text("主密码（验证）") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { appViewModel.suspendAutoLock(); createExport.launch("keybox-backup-${System.currentTimeMillis()}.kbx") },
                enabled = exportPwd.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("导出备份")
            }

            Spacer(Modifier.height(24.dp))

            // ===== 导入 kbx =====
            Text("导入备份 (.kbx)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = importPwd, onValueChange = { importPwd = it },
                label = { Text("导出该备份时使用的主密码") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { appViewModel.suspendAutoLock(); pickKbx.launch(arrayOf("application/octet-stream", "*/*")) },
                enabled = importPwd.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("选择 .kbx 文件导入")
            }

            Spacer(Modifier.height(24.dp))

            // ===== 导入 CSV =====
            Text("从 CSV 导入（迁移）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "支持浏览器/其他管理器导出的 CSV。仅支持导入，不提供明文导出。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { appViewModel.suspendAutoLock(); pickCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("选择 CSV 文件")
            }

            status?.let {
                Spacer(Modifier.height(24.dp))
                Card(Modifier.fillMaxWidth()) {
                    Text(it, Modifier.padding(16.dp))
                }
            }
        }
    }
}

private suspend fun exportToUri(
    context: android.content.Context,
    uri: Uri,
    password: String
): String {
    val app = context.applicationContext as com.keybox.app.KeyBoxApp
    val crypto = app.crypto
    if (!crypto.isUnlocked()) return "未解锁"

    val db = com.keybox.app.data.db.KeyBoxDatabase.getInstance(context)

    // 验证主密码正确性
    if (!crypto.verify(password.toCharArray())) {
        return "主密码错误，无法导出"
    }

    // 直接从 DB 读密文实体（保持密文状态，无需解密重加密）
    val items = db.passwordDao().getAll()
    val categories = db.categoryDao().getAll()
    val allHistory = db.passwordHistoryDao().getAll()

    val bytes = KbxFormat.encode(items, categories, allHistory, password.toCharArray())
    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        ?: return "无法写入文件"

    return "导出成功。备份已加密，请妥善保管。"
}
