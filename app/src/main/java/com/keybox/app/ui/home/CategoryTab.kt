package com.keybox.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keybox.app.data.repository.PasswordRepository
import com.keybox.app.ui.AppViewModel
import com.keybox.app.ui.components.AvatarColors
import com.keybox.app.ui.theme.BrandBlue
import kotlinx.coroutines.launch

/** 分类管理 Tab：分类列表（含条目数）+ 点按展开该分类下的账号。 */
@Composable
fun CategoryTab(
    appViewModel: AppViewModel,
    onOpenItem: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories by appViewModel.repository.observeCategories().collectAsState(initial = emptyList())
    val tags by appViewModel.repository.observeTags().collectAsState(initial = emptyList())
    val allItems by appViewModel.repository.observeActiveItems().collectAsState(initial = emptyList())

    var expandedCategoryId by remember { mutableStateOf<Long?>(null) }
    var expandedTagId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "分类管理",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // ===== 分类列表 =====
        items(categories, key = { it.id }) { cat ->
            val count = allItems.count { it.categoryId == cat.id }
            val expanded = expandedCategoryId == cat.id
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedCategoryId = if (expanded) null else cat.id }
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(AvatarColors.colorFor(cat.name), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                cat.name.take(1),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            cat.name,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "$count",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (expanded) {
                        val catItems = allItems.filter { it.categoryId == cat.id }
                        catItems.forEach { item ->
                            CategoryItemRow(item, onClick = { onOpenItem(item.id) })
                        }
                        if (catItems.isEmpty()) {
                            Text(
                                "该分类下暂无账号",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 68.dp, bottom = 12.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        // ===== 标签 =====
        if (tags.isNotEmpty()) {
            item {
                Text(
                    "标签",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            items(tags, key = { "tag_${it.id}" }) { tag ->
                val expanded = expandedTagId == tag.id
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedTagId = if (expanded) null else tag.id }
                ) {
                    Column {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BrandBlue.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    "# ${tag.name}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = BrandBlue,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (expanded) {
                            var tagItems by remember(tag.id) {
                                mutableStateOf<List<PasswordRepository.PasswordItem>>(emptyList())
                            }
                            LaunchedEffect(tag.id) {
                                tagItems = appViewModel.repository.getItemsByTag(tag.id)
                            }
                            tagItems.forEach { item ->
                                CategoryItemRow(item, onClick = { onOpenItem(item.id) })
                            }
                            if (tagItems.isEmpty()) {
                                Text(
                                    "该标签下暂无账号",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 14.dp, bottom = 12.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun CategoryItemRow(
    item: PasswordRepository.PasswordItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 68.dp, end = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(AvatarColors.colorFor(item.name), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                item.name.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            item.name,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (item.favorite) {
            Icon(
                Icons.Default.Star,
                null,
                modifier = Modifier.size(14.dp),
                tint = Color(0xFFF2B705)
            )
        }
    }
}
