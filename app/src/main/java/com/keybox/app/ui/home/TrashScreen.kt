package com.keybox.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.keybox.app.ui.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val items by appViewModel.repository.observeTrashItems().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回收站") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("回收站为空", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item {
                    Text(
                        "删除的条目将保留 30 天，之后自动彻底删除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(items, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = {
                            scope.launch {
                                appViewModel.repository.restoreFromTrash(item.id)
                            }
                        }) {
                            Icon(Icons.Default.Restore, "恢复")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                appViewModel.repository.permanentlyDelete(item.id)
                            }
                        }) {
                            Icon(Icons.Default.DeleteForever, "彻底删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
