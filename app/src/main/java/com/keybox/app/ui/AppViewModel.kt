package com.keybox.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.keybox.app.KeyBoxApp
import com.keybox.app.autofill.AutoFillKeyStore
import com.keybox.app.data.crypto.CryptoManager
import com.keybox.app.data.repository.PasswordRepository
import com.keybox.app.domain.PasswordStrength
import com.keybox.app.security.BiometricHelper
import com.keybox.app.security.BiometricKeyManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 全局应用状态与解锁状态管理。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val keyBoxApp: KeyBoxApp = app as KeyBoxApp
    private val crypto: CryptoManager = keyBoxApp.crypto
    val repository: PasswordRepository = keyBoxApp.repository
    private val biometricKeyManager = BiometricKeyManager(app)

    /** 自动锁定相关。 */
    private var autoLockJob: Job? = null
    private val autoLockPrefs = app.getSharedPreferences("keybox_settings", Context.MODE_PRIVATE)

    /** 备份提醒相关。 */
    private val backupPrefs = app.getSharedPreferences("keybox_backup", Context.MODE_PRIVATE)

    /** Autofill 相关。 */
    private val autofillPrefs = app.getSharedPreferences("keybox_autofill_settings", Context.MODE_PRIVATE)

    /** 应用状态：是否需要创建主密码 / 是否已解锁。 */
    sealed class AppState {
        object NeedsSetup : AppState()
        object Locked : AppState()
        object Unlocked : AppState()
    }

    private val _state = MutableStateFlow<AppState>(
        if (crypto.isInitialized()) AppState.Locked else AppState.NeedsSetup
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun showMessage(msg: String) { _message.value = msg }

    // ===== 临时挂起后台锁定（用于文件选择器等外部 Activity） =====

    private var suspendAutoLockCount = 0

    /** 启动外部 Activity（如 SAF 文件选择器）前调用，避免返回时被误锁。 */
    fun suspendAutoLock() {
        suspendAutoLockCount++
    }

    /** 外部 Activity 返回后调用。 */
    fun resumeAutoLock() {
        if (suspendAutoLockCount > 0) suspendAutoLockCount--
    }

    /** 是否应因进入后台而锁定（有挂起的外部 Activity 时不锁）。 */
    fun shouldLockOnBackground(): Boolean = suspendAutoLockCount == 0

    // ===== 自动锁定设置 =====

    /** 自动锁定间隔（分钟）。-1 表示仅手动。 */
    fun getAutoLockMinutes(): Int = autoLockPrefs.getInt("auto_lock_minutes", 5)

    fun setAutoLockMinutes(minutes: Int) {
        autoLockPrefs.edit().putInt("auto_lock_minutes", minutes).apply()
        resetAutoLockTimer()
    }

    private fun resetAutoLockTimer() {
        autoLockJob?.cancel()
        val minutes = getAutoLockMinutes()
        if (minutes <= 0 || _state.value != AppState.Unlocked) return
        autoLockJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            lock()
        }
    }

    // ===== 主密码 =====

    /** 创建主密码。 */
    fun setupMasterPassword(password: String): Boolean {
        if (!PasswordStrength.isMasterPasswordAcceptable(password)) {
            _message.value = "主密码至少需要 12 位"
            return false
        }
        return try {
            crypto.initialize(password.toCharArray())
            viewModelScope.launch { repository.ensureDefaultCategories() }
            _state.value = AppState.Unlocked
            resetAutoLockTimer()
            true
        } catch (e: Exception) {
            _message.value = "创建失败：${e.message}"
            false
        }
    }

    /** 主密码解锁。 */
    fun unlock(password: String): Boolean {
        return if (crypto.unlock(password.toCharArray())) {
            viewModelScope.launch { repository.ensureDefaultCategories() }
            _state.value = AppState.Unlocked
            resetAutoLockTimer()
            syncAutofillDek()
            true
        } else {
            _message.value = "主密码错误"
            false
        }
    }

    // ===== Autofill =====

    /** 是否已启用 Autofill。 */
    fun autofillEnabled(): Boolean = autofillPrefs.getBoolean("enabled", false)

    /** 切换 Autofill 开关。 */
    fun setAutofillEnabled(enabled: Boolean) {
        autofillPrefs.edit().putBoolean("enabled", enabled).apply()
        if (enabled) {
            syncAutofillDek()
        } else {
            AutoFillKeyStore.clearDek(getApplication())
        }
    }

    /** 解锁后，若 Autofill 已启用，则把 DEK 写入共享存储供 Autofill 使用。 */
    private fun syncAutofillDek() {
        if (!autofillEnabled()) return
        val dek = crypto.getDek() ?: return
        AutoFillKeyStore.storeDek(getApplication(), dek)
        dek.fill(0)
    }

    // ===== 生物识别 =====

    fun biometricAvailable(context: Context): Boolean =
        BiometricHelper.canAuthenticate(context)

    /** 是否已启用生物识别解锁。 */
    fun biometricEnabled(): Boolean = biometricKeyManager.isEnabled()

    /** 生物识别密钥是否仍有效（未被 enrollment 变更作废）。 */
    fun biometricKeyValid(): Boolean = biometricKeyManager.isKeyValid()

    /**
     * 启用生物识别解锁。
     * 在已解锁状态下，用 Keystore 生物识别密钥加密 DEK 存盘。
     * @return 是否成功
     */
    fun enableBiometric(): Boolean {
        val dek = crypto.getDek() ?: return false
        val ok = biometricKeyManager.enable(dek)
        dek.fill(0)
        if (!ok) _message.value = "启用生物识别失败，请重试"
        return ok
    }

    /** 关闭生物识别解锁。 */
    fun disableBiometric() {
        biometricKeyManager.disable()
    }

    /**
     * 生物识别认证成功后调用：解密 DEK 并解锁。
     * @return 是否成功还原 DEK
     */
    fun biometricUnlock(): Boolean {
        val dek = biometricKeyManager.decryptDek()
        if (dek == null) {
            _message.value = "生物识别解锁失败，请使用主密码"
            return false
        }
        crypto.setDek(dek)
        dek.fill(0)
        _state.value = AppState.Unlocked
        resetAutoLockTimer()
        return true
    }

    // ===== 修改主密码 =====

    fun changeMasterPassword(old: String, new: String): Boolean {
        if (!PasswordStrength.isMasterPasswordAcceptable(new)) {
            _message.value = "新主密码至少需要 12 位"
            return false
        }
        return if (crypto.changeMasterPassword(old.toCharArray(), new.toCharArray())) {
            // 修改主密码后，旧生物识别绑定失效，需重新启用
            disableBiometric()
            _message.value = "主密码已修改（生物识别解锁已关闭，请重新启用）"
            true
        } else {
            _message.value = "旧主密码错误"
            false
        }
    }

    // ===== 锁定 =====

    /** 锁定。 */
    fun lock() {
        autoLockJob?.cancel()
        crypto.lock()
        // 锁定后清除 Autofill 可用的 DEK，防止独立进程继续解密
        AutoFillKeyStore.clearDek(getApplication())
        _state.value = AppState.Locked
    }

    /** 用户交互时重置自动锁定计时器。 */
    fun onUserInteraction() {
        if (_state.value == AppState.Unlocked) resetAutoLockTimer()
    }

    fun isUnlocked(): Boolean = crypto.isUnlocked()

    // ===== 备份提醒 =====

    /** 记录备份完成时间。 */
    fun recordBackupDone() {
        backupPrefs.edit().putLong("last_backup_at", System.currentTimeMillis()).apply()
    }

    /** 距上次备份的天数。null 表示从未备份。 */
    fun daysSinceBackup(): Int? {
        val last = backupPrefs.getLong("last_backup_at", 0L)
        if (last == 0L) return null
        val days = (System.currentTimeMillis() - last) / (24 * 60 * 60 * 1000L)
        return days.toInt()
    }
}
