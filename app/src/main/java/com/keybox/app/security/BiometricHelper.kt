package com.keybox.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 生物识别管理器（纯身份认证模式）。
 *
 * 解锁流程：BiometricPrompt 确认身份 → 认证成功后调用方用 Keystore 密钥解密 DEK。
 * 不使用 CryptoObject，避免 setUserAuthenticationRequired 密钥在 enable 阶段
 * 抛 UserNotAuthenticatedException 导致闪退。
 */
object BiometricHelper {

    fun canAuthenticate(context: Context): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("取消")
            .build()

        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            onError(e.message ?: "生物识别不可用")
        }
    }
}
