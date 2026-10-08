package com.keybox.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.provider.Settings
import com.keybox.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    onBackup: () -> Unit,
    onSecurityCenter: () -> Unit,
    onOpenTrash: () -> Unit = {},
    showBack: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showChangePwd by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var autoLock by remember { mutableStateOf(appViewModel.getAutoLockMinutes()) }
    var biometricEnabled by remember { mutableStateOf(appViewModel.biometricEnabled()) }
    var autofillEnabled by remember { mutableStateOf(appViewModel.autofillEnabled()) }

    val lastBackupAt by appViewModel.lastBackupAt.collectAsState()
    val biometricSupported = appViewModel.biometricAvailable(context)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 回收站入口
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("数据管理", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenTrash,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("回收站")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("备份与迁移", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    val daysSince = if (lastBackupAt == 0L) null
                        else ((System.currentTimeMillis() - lastBackupAt) / (24 * 60 * 60 * 1000L)).toInt()
                    Text(
                        when {
                            daysSince == null -> "尚未备份，建议尽快导出备份"
                            daysSince >= 30 -> "距上次备份 ${daysSince} 天，建议尽快备份"
                            else -> "距上次备份 ${daysSince} 天"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (daysSince == null || daysSince >= 30)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onBackup,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Backup, null)
                        Spacer(Modifier.width(8.dp))
                        Text("导出 / 导入备份")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 安全中心
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("安全中心", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "检查弱密码、重复密码和长期未修改的密码。\n全部在本地完成，不上传密码。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSecurityCenter,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("进入安全中心")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 自动填充
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("自动填充 (Autofill)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "在其它 App 中自动填充账号密码。\n首次使用需在系统设置中启用 KeyBox 作为自动填充服务。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("启用自动填充", Modifier.weight(1f))
                        Switch(
                            checked = autofillEnabled,
                            onCheckedChange = { checked ->
                                autofillEnabled = checked
                                appViewModel.setAutofillEnabled(checked)
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                            intent.data = android.net.Uri.parse("package:com.keybox.app")
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("前往系统设置启用")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("安全", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    // 自动锁定
                    Text("自动锁定", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val options = listOf(
                            1 to "1 分钟",
                            5 to "5 分钟",
                            15 to "15 分钟",
                            30 to "30 分钟",
                            -1 to "手动"
                        )
                        options.forEach { (value, label) ->
                            FilterChip(
                                selected = autoLock == value,
                                onClick = {
                                    autoLock = value
                                    appViewModel.setAutoLockMinutes(value)
                                },
                                label = { Text(label) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 生物识别解锁
                    if (biometricSupported) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Fingerprint, null)
                            Spacer(Modifier.width(8.dp))
                            Text("生物识别解锁", Modifier.weight(1f))
                            Switch(
                                checked = biometricEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        // 启用失败时回滚开关状态
                                        if (appViewModel.enableBiometric()) {
                                            biometricEnabled = true
                                        } else {
                                            biometricEnabled = false
                                        }
                                    } else {
                                        appViewModel.disableBiometric()
                                        biometricEnabled = false
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    OutlinedButton(
                        onClick = { showChangePwd = !showChangePwd },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("修改主密码")
                    }
                    if (showChangePwd) {
                        ChangePasswordSection(appViewModel)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { appViewModel.lock() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Lock, null)
                        Spacer(Modifier.width(8.dp))
                        Text("立即锁定")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "KeyBox v0.2.0 · 本地优先 · 数据加密存储",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun ChangePasswordSection(appViewModel: AppViewModel) {
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }

    Column {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = oldPwd, onValueChange = { oldPwd = it },
            label = { Text("当前主密码") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newPwd, onValueChange = { newPwd = it },
            label = { Text("新主密码（至少 12 位）") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirm, onValueChange = { confirm = it },
            label = { Text("再次输入新密码") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (newPwd != confirm) {
                    result = "两次输入不一致"
                } else if (appViewModel.changeMasterPassword(oldPwd, newPwd)) {
                    result = "主密码已修改"
                    oldPwd = ""; newPwd = ""; confirm = ""
                } else {
                    result = "修改失败"
                }
            },
            enabled = oldPwd.isNotEmpty() && newPwd.isNotEmpty() && confirm.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("确认修改")
        }
        result?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}
