package com.keybox.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keybox.app.data.repository.PasswordRepository
import com.keybox.app.ui.AppViewModel
import com.keybox.app.ui.components.AvatarColors
import com.keybox.app.ui.settings.SettingsScreen
import com.keybox.app.ui.theme.BrandBlue
import com.keybox.app.ui.theme.BrandBlueGradientBottom
import com.keybox.app.ui.theme.BrandBlueGradientTop

/** 主界面：底部导航（首页 / 分类 / 设置）+ 中央 FAB。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    appViewModel: AppViewModel,
    onOpenItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSecurity: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(
                    onClick = onAddItem,
                    containerColor = BrandBlue,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Add, "新增")
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("首页") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBlue,
                        selectedTextColor = BrandBlue,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Category, null) },
                    label = { Text("分类") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBlue,
                        selectedTextColor = BrandBlue,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("设置") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBlue,
                        selectedTextColor = BrandBlue,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> HomeTab(
                appViewModel = appViewModel,
                onOpenItem = onOpenItem,
                onOpenTrash = onOpenTrash,
                onOpenSecurity = onOpenSecurity,
                modifier = Modifier.padding(padding)
            )
            1 -> CategoryTab(
                appViewModel = appViewModel,
                onOpenItem = onOpenItem,
                modifier = Modifier.padding(padding)
            )
            2 -> SettingsScreen(
                appViewModel = appViewModel,
                onBack = {},
                onBackup = onOpenBackup,
                onSecurityCenter = onOpenSecurity,
                onOpenTrash = onOpenTrash,
                showBack = false,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

// ===== 首页 Tab =====

@Composable
private fun HomeTab(
    appViewModel: AppViewModel,
    onOpenItem: (Long) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSecurity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items by appViewModel.repository.observeActiveItems().collectAsState(initial = emptyList())
    val categories by appViewModel.repository.observeCategories().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Long?>(null) }
    // 条目 → 标签名列表（用于搜索按标签名匹配）
    var tagNameMap by remember { mutableStateOf<Map<Long, List<String>>>(emptyMap()) }

    LaunchedEffect(Unit) {
        tagNameMap = appViewModel.repository.getItemTagNameMap()
    }

    val filtered = remember(items, query, selectedCategory, tagNameMap) {
        items.filter { item ->
            val tagMatch = tagNameMap[item.id]?.any { it.contains(query, ignoreCase = true) } == true
            val matchQuery = query.isBlank() ||
                tagMatch ||
                item.name.contains(query, ignoreCase = true) ||
                item.url.contains(query, ignoreCase = true) ||
                item.username.contains(query, ignoreCase = true) ||
                item.password.contains(query, ignoreCase = true) ||
                item.email.contains(query, ignoreCase = true) ||
                item.phone.contains(query, ignoreCase = true) ||
                item.notes.contains(query, ignoreCase = true)
            val matchCategory = selectedCategory == null || item.categoryId == selectedCategory
            matchQuery && matchCategory
        }
    }

    Column(modifier.fillMaxSize()) {
        // ===== 蓝色渐变头部 =====
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(BrandBlueGradientTop, BrandBlueGradientBottom)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "KeyBox",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "你的私人密码管理器",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                IconButton(onClick = onOpenSecurity) {
                    Icon(Icons.Default.Shield, "安全中心", tint = Color.White)
                }
                IconButton(onClick = onOpenTrash) {
                    Icon(Icons.Default.Delete, "回收站", tint = Color.White)
                }
            }
        }

        // ===== 内容区（上圆角白底卡片，覆盖头部下方） =====
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                // 搜索框
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索账号 / 用户名 / 网址") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = BrandBlue) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedBorderColor = BrandBlue
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )

                // 分类 chips
                if (categories.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("全部") },
                                colors = filterChipColors()
                            )
                        }
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat.id,
                                onClick = { selectedCategory = cat.id },
                                label = { Text(cat.name) },
                                colors = filterChipColors()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 账号列表
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔐", style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (items.isEmpty()) "点击右下角 + 添加第一个账号" else "没有匹配的结果",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { item ->
                            ItemRow(item = item, onClick = { onOpenItem(item.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = BrandBlue,
    selectedLabelColor = Color.White,
    containerColor = Color.White
)

@Composable
private fun ItemRow(
    item: PasswordRepository.PasswordItem,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 彩色首字母圆形图标
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(AvatarColors.colorFor(item.name), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.favorite) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "收藏",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFF2B705)
                        )
                    }
                }
                if (item.username.isNotEmpty()) {
                    Text(
                        item.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
