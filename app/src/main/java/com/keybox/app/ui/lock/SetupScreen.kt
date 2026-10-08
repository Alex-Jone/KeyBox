package com.keybox.app.ui.lock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.keybox.app.domain.PasswordStrength
import com.keybox.app.ui.AppViewModel

@Composable
fun SetupScreen(appViewModel: AppViewModel) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val message by appViewModel.message.collectAsState()
    var localError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔐", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(8.dp))
        Text("KeyBox", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        Text("创建你的主密码", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("主密码（至少 12 位）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        if (password.isNotEmpty()) {
            val (level, _) = PasswordStrength.evaluate(password)
            Text(
                "强度：${level.label}",
                style = MaterialTheme.typography.bodySmall,
                color = when (level) {
                    PasswordStrength.Level.WEAK -> MaterialTheme.colorScheme.error
                    PasswordStrength.Level.MEDIUM -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                }
            )
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("再次输入") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                when {
                    password != confirm -> localError = "两次输入不一致"
                    password.length < 12 -> localError = "主密码至少需要 12 位"
                    else -> {
                        localError = null
                        appViewModel.setupMasterPassword(password)
                    }
                }
            },
            enabled = password.isNotEmpty() && confirm.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("创建密码")
        }

        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(message!!, color = MaterialTheme.colorScheme.error)
        }
        if (localError != null) {
            Spacer(Modifier.height(12.dp))
            Text(localError!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "⚠ 主密码无法找回，请妥善保存",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
