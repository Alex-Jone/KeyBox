package com.keybox.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * 生物识别管理器。
 * 支持两种模式：
 *   1. 无 cipher（仅确认身份，用于「纯快捷解锁」）
 *   2. 带 cipher（Keystore 绑定认证，认证通过后才能解密 DEK）
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
        cipher: Cipher? = null,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess(result)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (cipher != null) {
            // Keystore 绑定的认证：必须用 STRONG 且不能设置负按钮，
            // 否则会抛异常（setAllowedAuthenticators 与 setNegativeButtonText 冲突）
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } else {
            builder.setNegativeButtonText("取消")
        }

        val info = builder.build()
        try {
            if (cipher != null) {
                prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
            } else {
                prompt.authenticate(info)
            }
        } catch (e: Exception) {
            // 认证启动失败（如密钥与认证器不匹配、设备不支持强生物识别等），
            // 不崩溃，交由调用方处理（回退主密码）
            onError(e.message ?: "生物识别不可用")
        }
    }
}
