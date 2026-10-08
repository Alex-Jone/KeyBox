package com.keybox.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.keybox.app.data.repository.PasswordRepository
import com.keybox.app.domain.PasswordGenerator
import com.keybox.app.domain.PasswordStrength
import com.keybox.app.ui.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(
    appViewModel: AppViewModel,
    itemId: Long,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val categories by appViewModel.repository.observeCategories().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var favorite by remember { mutableStateOf(false) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var totpSecret by remember { mutableStateOf("") }

    // 标签状态
    val allTags by appViewModel.repository.observeTags().collectAsState(initial = emptyList())
    var selectedTagIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var newTagName by remember { mutableStateOf("") }

    // 生成器状态
    var genLength by remember { mutableStateOf(20) }
    var useUpper by remember { mutableStateOf(true) }
    var useLower by remember { mutableStateOf(true) }
    var useDigit by remember { mutableStateOf(true) }
    var useSymbol by remember { mutableStateOf(true) }
    var showGenerator by remember { mutableStateOf(false) }

    LaunchedEffect(itemId) {
        if (itemId != 0L && !loaded) {
            appViewModel.repository.getItem(itemId)?.let { item ->
                name = item.name
                url = item.url
                username = item.username
                password = item.password
                email = item.email
                phone = item.phone
                notes = item.notes
                favorite = item.favorite
                categoryId = item.categoryId
                totpSecret = item.totpSecret
            }
            selectedTagIds = appViewModel.repository.getTagsForItem(itemId).map { it.id }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == 0L) "新增账号" else "编辑账号") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { favorite = !favorite }) {
                        Icon(
                            Icons.Default.Star,
                            "收藏",
                            tint = if (favorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("名称 *") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("网站 URL") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("用户名") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            // 密码 + 生成器入口
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("密码") }, singleLine = true,
                    visualTransformation = if (showPassword)
                        androidx.compose.ui.text.input.VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        "显示"
                    )
                }
                IconButton(onClick = { showGenerator = !showGenerator }) {
                    Icon(Icons.Default.Refresh, "生成器")
                }
            }

            // 生成器面板
            if (showGenerator) {
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("密码生成器", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("长度", Modifier.width(60.dp))
                            Slider(
                                value = genLength.toFloat(),
                                onValueChange = { genLength = it.toInt() },
                                valueRange = 8f..64f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("$genLength", Modifier.width(32.dp))
                        }
                        Row {
                            CheckboxRow("大写", useUpper) { useUpper = it }
                            CheckboxRow("小写", useLower) { useLower = it }
                        }
                        Row {
                            CheckboxRow("数字", useDigit) { useDigit = it }
                            CheckboxRow("符号", useSymbol) { useSymbol = it }
                        }
                        Button(
                            onClick = {
                                password = PasswordGenerator.generateRandom(
                                    length = genLength,
                                    useUpper = useUpper, useLower = useLower,
                                    useDigit = useDigit, useSymbol = useSymbol
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("生成随机密码")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                password = PasswordGenerator.generatePassphrase(wordCount = 5, separator = "-")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("生成密码短语（易记忆）")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "密码强度：${PasswordStrength.evaluate(password).first.label}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("邮箱") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("手机号") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            // 2FA (TOTP) secret
            OutlinedTextField(
                value = totpSecret,
                onValueChange = { totpSecret = it },
                label = { Text("2FA 密钥 (TOTP Secret)") },
                placeholder = { Text("Base32 密钥，如 JBSWY3DPEHPK3PXP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "输入从服务商获取的 Base32 密钥，详情页将显示动态验证码。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))

            // 分类选择
            Text("分类", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories.size) { i ->
                    val cat = categories[i]
                    FilterChip(
                        selected = categoryId == cat.id,
                        onClick = { categoryId = cat.id },
                        label = { Text(cat.name) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 标签选择
            Text("标签", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            if (allTags.isEmpty()) {
                Text(
                    "暂无标签，可在下方新建",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allTags.size) { i ->
                        val tag = allTags[i]
                        val selected = tag.id in selectedTagIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedTagIds = if (selected) {
                                    selectedTagIds - tag.id
                                } else {
                                    selectedTagIds + tag.id
                                }
                            },
                            label = { Text(tag.name) }
                        )
                    }
                }
            }
            // 新建标签
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("新建标签") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            scope.launch {
                                val tag = appViewModel.repository.createTag(newTagName)
                                selectedTagIds = selectedTagIds + tag.id
                                newTagName = ""
                            }
                        }
                    }
                ) {
                    Text("添加")
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("备注") }, minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    scope.launch {
                        val savedId = appViewModel.repository.saveItem(
                            PasswordRepository.PasswordItem(
                                id = itemId,
                                name = name.trim(),
                                url = url.trim(),
                                username = username,
                                password = password,
                                email = email,
                                phone = phone,
                                notes = notes,
                                totpSecret = totpSecret.trim(),
                                favorite = favorite,
                                categoryId = categoryId,
                                createdAt = 0,
                                updatedAt = 0,
                                lastUsedAt = null
                            )
                        )
                        // 保存标签关联
                        appViewModel.repository.setItemTags(savedId, selectedTagIds)
                        onBack()
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
