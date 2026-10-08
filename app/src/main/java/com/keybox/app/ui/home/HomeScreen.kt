package com.keybox.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keybox.app.data.repository.PasswordRepository
import com.keybox.app.ui.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    appViewModel: AppViewModel,
    onOpenItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val items by appViewModel.repository.observeActiveItems().collectAsState(initial = emptyList())
    val categories by appViewModel.repository.observeCategories().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Long?>(null) }

    val filtered = remember(items, query, selectedCategory) {
        items.filter { item ->
            val matchQuery = query.isBlank() ||
                item.name.contains(query, ignoreCase = true) ||
                item.url.contains(query, ignoreCase = true) ||
                item.username.contains(query, ignoreCase = true)
            val matchCategory = selectedCategory == null || item.categoryId == selectedCategory
            matchQuery && matchCategory
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔐 KeyBox") },
                actions = {
                    IconButton(onClick = onOpenTrash) {
                        Icon(Icons.Default.Delete, contentDescription = "回收站")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Default.Add, contentDescription = "新增")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 搜索
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索账号、网址、用户名") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            )

            // 分类筛选
            if (categories.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("全部") }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat.id,
                            onClick = { selectedCategory = cat.id },
                            label = { Text(cat.name) }
                        )
                    }
                }
            }

            // 列表
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (items.isEmpty()) "点击右下角 + 添加你的第一个账号"
                        else "没有匹配的结果",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        ItemRow(
                            item = item,
                            onClick = { onOpenItem(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: PasswordRepository.PasswordItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 首字母圆形图标
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                item.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.favorite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "收藏",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFFFB300)
                    )
                }
            }
            if (item.username.isNotEmpty()) {
                Text(
                    item.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
