package com.keybox.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.keybox.app.domain.SecurityAnalyzer
import com.keybox.app.ui.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityCenterScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit
) {
    val items by appViewModel.repository.observeActiveItems().collectAsState(initial = emptyList())
    val report = remember(items) { SecurityAnalyzer.analyze(items) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var diagStatus by remember { mutableStateOf<String?>(null) }
    var showLogs by remember { mutableStateOf(false) }

    // 导出崩溃日志
    val exportLog = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: android.net.Uri? ->
        appViewModel.resumeAutoLock()
        if (uri != null) {
            scope.launch {
                try {
                    val app = context.applicationContext as com.keybox.app.KeyBoxApp
                    val logs = app.readCrashLogs()
                    if (logs.isEmpty()) {
                        diagStatus = "暂无崩溃日志"
                    } else {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(logs.toByteArray()) }
                        diagStatus = "日志已导出"
                    }
                } catch (e: Exception) {
                    diagStatus = "导出失败：${e.message}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安全中心") },
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
            // 总览
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (report.totalIssues == 0)
                        Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                )
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (report.totalIssues == 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                            null,
                            tint = if (report.totalIssues == 0) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (report.totalIssues == 0) "密码库很安全"
                            else "发现 ${report.totalIssues} 个安全问题",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "共 ${items.size} 个账号 · 弱密码 ${report.weakPasswords.size} · " +
                        "重复密码 ${report.duplicatePasswords.size} 组 · 长期未改 ${report.stalePasswords.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 弱密码
            if (report.weakPasswords.isNotEmpty()) {
                SectionTitle("弱密码", report.weakPasswords.size)
                report.weakPasswords.forEach { issue ->
                    IssueCard("⚠", issue.itemName, issue.detail)
                }
                Spacer(Modifier.height(16.dp))
            }

            // 重复密码
            if (report.duplicatePasswords.isNotEmpty()) {
                SectionTitle("重复密码", report.duplicatePasswords.size)
                report.duplicatePasswords.forEach { group ->
                    IssueCard(
                        "🔁",
                        "${group.itemNames.size} 个账号使用相同密码",
                        group.itemNames.joinToString("、")
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // 长期未修改
            if (report.stalePasswords.isNotEmpty()) {
                SectionTitle("长期未修改", report.stalePasswords.size)
                report.stalePasswords.forEach { issue ->
                    IssueCard("🕐", issue.itemName, issue.detail)
                }
                Spacer(Modifier.height(16.dp))
            }

            // 全部安全
            if (report.totalIssues == 0) {
                Text(
                    "没有发现弱密码、重复密码或长期未修改的密码。\n所有检查均在本地完成，密码从未上传。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "安全检查完全在本地进行，不会上传任何密码。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(24.dp))

            // ===== 诊断日志 =====
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("诊断日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "若应用闪退，可导出崩溃日志用于排查。日志仅含错误堆栈，不含任何密码明文。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showLogs = !showLogs }) {
                    Text(if (showLogs) "隐藏日志" else "查看日志")
                }
                OutlinedButton(
                    onClick = {
                        appViewModel.suspendAutoLock()
                        exportLog.launch("keybox-crash-log-${System.currentTimeMillis()}.txt")
                    }
                ) {
                    Text("导出日志")
                }
            }
            if (showLogs) {
                val app = context.applicationContext as com.keybox.app.KeyBoxApp
                val logs = remember { app.readCrashLogs() }
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        if (logs.isEmpty()) "暂无崩溃日志" else logs,
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
            diagStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Text(
        "$title（$count）",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun IssueCard(emoji: String, title: String, detail: String) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (detail.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
