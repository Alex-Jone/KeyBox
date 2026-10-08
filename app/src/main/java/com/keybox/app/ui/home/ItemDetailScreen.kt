package com.keybox.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keybox.app.security.ClipboardHelper
import com.keybox.app.ui.AppViewModel
import com.keybox.app.ui.components.AvatarColors
import com.keybox.app.ui.theme.BrandBlue
import com.keybox.app.ui.theme.BrandBlueGradientBottom
import com.keybox.app.ui.theme.BrandBlueGradientTop
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

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            // ===== 蓝色渐变头部：大头像 + 名称 =====
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(BrandBlueGradientTop, BrandBlueGradientBottom)
                        )
                    )
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // 顶栏
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, "编辑", tint = Color.White)
                        }
                    }
                    // 头像 + 名称
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                current.name.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = AvatarColors.colorFor(current.name)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            current.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (current.url.isNotEmpty()) {
                            Text(
                                current.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ===== 字段卡片（上浮圆角） =====
            Surface(
                modifier = Modifier.fillMaxWidth().offset(y = (-16).dp),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(Modifier.padding(16.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            // 密码
                            FieldRow(
                                label = "密码",
                                value = if (showPassword) current.password else "••••••••••••••"
                            ) {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        "显示/隐藏",
                                        tint = BrandBlue
                                    )
                                }
                                IconButton(onClick = {
                                    ClipboardHelper.copy(context, "密码", current.password)
                                }) {
                                    Icon(Icons.Default.ContentCopy, "复制密码", tint = BrandBlue)
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                            // 用户名
                            FieldRow(label = "用户名", value = current.username, copyable = true)

                            // 网站
                            if (current.url.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                FieldRow(label = "网站", value = current.url)
                            }

                            // 2FA
                            if (current.totpSecret.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                FieldRow(label = "2FA", value = totpCode) {
                                    Text(
                                        "${totpRemaining}s",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (totpRemaining <= 5) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    IconButton(onClick = {
                                        ClipboardHelper.copy(context, "2FA 验证码", totpCode)
                                    }) {
                                        Icon(Icons.Default.ContentCopy, "复制", tint = BrandBlue)
                                    }
                                }
                            }

                            // 邮箱
                            if (current.email.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                FieldRow(label = "邮箱", value = current.email, copyable = true)
                            }
                            // 手机号
                            if (current.phone.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                FieldRow(label = "手机号", value = current.phone, copyable = true)
                            }
                            // 备注
                            if (current.notes.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                FieldRow(label = "备注", value = current.notes)
                            }
                        }
                    }

                    // 标签
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(tags.size) { i ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BrandBlue.copy(alpha = 0.08f)
                                ) {
                                    Text(
                                        "# ${tags[i].name}",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        color = BrandBlue,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 密码历史
                    OutlinedButton(
                        onClick = {
                            showHistory = !showHistory
                            if (showHistory && history.isEmpty()) {
                                scope.launch {
                                    history = appViewModel.repository.getPasswordHistory(itemId)
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.History, null)
                        Spacer(Modifier.width(8.dp))
                        Text("密码历史")
                    }
                    if (showHistory) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                if (history.isEmpty()) {
                                    Text(
                                        "暂无历史记录",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // 删除
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                appViewModel.repository.moveToTrash(itemId)
                                onBack()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("移动到回收站")
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    copyable: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            Modifier.width(72.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            value,
            Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
        if (copyable) {
            IconButton(onClick = { ClipboardHelper.copy(context, label, value) }) {
                Icon(Icons.Default.ContentCopy, "复制", tint = BrandBlue)
            }
        }
        trailing()
    }
}

private fun formatTime(ts: Long): String {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(ts))
}
