package com.keybox.app.ui.lock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.keybox.app.security.BiometricHelper
import com.keybox.app.ui.AppViewModel
import androidx.fragment.app.FragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreen(appViewModel: AppViewModel) {
    var password by remember { mutableStateOf("") }
    val message by appViewModel.message.collectAsState()
    val context = LocalContext.current

    // 生物识别可用性：已启用且密钥仍有效
    val showBiometric = appViewModel.biometricAvailable(context) &&
        appViewModel.biometricEnabled() &&
        appViewModel.biometricKeyValid()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔐", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(8.dp))
        Text("KeyBox", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        Text("输入主密码", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("主密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { appViewModel.unlock(password) },
            enabled = password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("解锁")
        }

        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(message!!, color = MaterialTheme.colorScheme.error)
        }

        if (showBiometric) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = {
                    val activity = context.findFragmentActivity() ?: return@OutlinedButton
                    BiometricHelper.showPrompt(
                        activity = activity,
                        title = "解锁 KeyBox",
                        subtitle = "使用指纹或面部识别解锁",
                        onSuccess = {
                            appViewModel.biometricUnlock()
                        },
                        onError = { err ->
                            appViewModel.showMessage(err)
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("指纹 / 面部识别")
            }
        }
    }
}

/** 解包 Context 找到 FragmentActivity（可能被 ContextWrapper 包装）。 */
private fun android.content.Context.findFragmentActivity(): FragmentActivity? {
    var ctx: android.content.Context? = this
    while (ctx != null) {
        if (ctx is FragmentActivity) return ctx
        ctx = if (ctx is android.content.ContextWrapper) ctx.baseContext else null
    }
    return null
}
