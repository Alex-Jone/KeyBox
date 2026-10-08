package com.keybox.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keybox.app.security.ClipboardHelper
import com.keybox.app.ui.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    appViewModel: AppViewModel,
    itemId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var item by remember { mutableStateOf<com.keybox.app.data.repository.PasswordRepository.PasswordItem?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var tags by remember { mutableStateOf<List<com.keybox.app.data.repository.PasswordRepository.Tag>>(emptyList()) }
    var totpCode by remember { mutableStateOf("") }
    var totpRemaining by remember { mutableStateOf(30) }

    LaunchedEffect(itemId) {
        item = appViewModel.repository.getItem(itemId)
        appViewModel.repository.markUsed(itemId)
        tags = appViewModel.repository.getTagsForItem(itemId)
    }

    // TOTP 刷新（每秒）
    LaunchedEffect(item?.totpSecret) {
        val secret = item?.totpSecret
        if (!secret.isNullOrEmpty()) {
            while (true) {
                totpCode = com.keybox.app.domain.Totp.generateCode(secret)
                totpRemaining = com.keybox.app.domain.Totp.remainingSeconds()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    val current = item
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (current.url.isNotEmpty()) {
                FieldBlock("网站", current.url)
            }
            FieldBlock("用户名", current.username, copyable = true)
            // 密码
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("密码", Modifier.width(80.dp), color = MaterialTheme.colorScheme.secondary)
                Text(
                    if (showPassword) current.password else "••••••••••••••",
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        "显示/隐藏"
                    )
                }
                IconButton(onClick = {
                    ClipboardHelper.copy(context, "密码", current.password)
                    scope.launch { }
                }) {
                    Icon(Icons.Default.ContentCopy, "复制密码")
                }
            }

            // 2FA (TOTP) 动态验证码
            if (current.totpSecret.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("2FA 验证码", Modifier.width(80.dp), color = MaterialTheme.colorScheme.secondary)
                    Text(
                        totpCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(3f, androidx.compose.ui.unit.TextUnitType.Sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        ClipboardHelper.copy(context, "2FA 验证码", totpCode)
                    }) {
                        Icon(Icons.Default.ContentCopy, "复制")
                    }
                    // 倒计时
                    Text(
                        "${totpRemaining}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (totpRemaining <= 5) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (current.email.isNotEmpty()) {
                FieldBlock("邮箱", current.email, copyable = true)
            }
            if (current.phone.isNotEmpty()) {
                FieldBlock("手机号", current.phone, copyable = true)
            }
            if (current.notes.isNotEmpty()) {
                FieldBlock("备注", current.notes)
            }

            // 标签
            if (tags.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("标签", Modifier.width(80.dp), color = MaterialTheme.colorScheme.secondary)
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(tags.size) { i ->
                            AssistChip(
                                onClick = {},
                                label = { Text(tags[i].name) }
                            )
                        }
                    }
                }
            }

            // 密码历史
            OutlinedButton(
                onClick = {
                    showHistory = !showHistory
                    if (showHistory) {
                        scope.launch {
                            history = appViewModel.repository.getPasswordHistory(itemId)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Default.History, null)
                Spacer(Modifier.width(8.dp))
                Text("密码历史")
            }
            if (showHistory) {
                if (history.isEmpty()) {
                    Text(
                        "暂无历史记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    history.forEach { (oldPwd, time) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                oldPwd,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                formatTime(time),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 删除
            OutlinedButton(
                onClick = {
                    scope.launch {
                        appViewModel.repository.moveToTrash(itemId)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text("移动到回收站")
            }
        }
    }
}

@Composable
private fun FieldBlock(label: String, value: String, copyable: Boolean = false) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.width(80.dp), color = MaterialTheme.colorScheme.secondary)
        Text(value, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (copyable) {
            IconButton(onClick = { ClipboardHelper.copy(context, label, value) }) {
                Icon(Icons.Default.ContentCopy, "复制")
            }
        }
    }
}

private fun formatTime(ts: Long): String {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(ts))
}
