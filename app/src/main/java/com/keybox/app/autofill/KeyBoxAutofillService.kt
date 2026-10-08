package com.keybox.app.autofill

import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.AutofillService
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import com.keybox.app.data.crypto.AesGcm
import com.keybox.app.data.db.KeyBoxDatabase
import com.keybox.app.data.db.PasswordItemEntity
import kotlinx.coroutines.runBlocking

/**
 * KeyBox 自动填充服务（第二阶段核心功能）。
 *
 * 跨进程 DEK 访问方案：
 *   主 App 解锁后，会把 DEK 用 Keystore 的「autofill 专用密钥」加密，
 *   写入一个主 App 与 AutofillService 共享的 SharedPreferences。
 *   AutofillService 进程读取该密文 DEK，用 Keystore 密钥解密还原。
 *
 * 该 Keystore 密钥 setUserAuthenticationRequired(false)，但仅当主 App
 * 显式启用 Autofill 时才写入 DEK；用户锁定主 App 时同步清除该 DEK 密文。
 * 这保证了：密码库密文虽对 AutofillService 可读，但只有启用 Autofill
 * 且主 App 曾解锁的前提下才能解密。
 */
class KeyBoxAutofillService : AutofillService() {

    private val db get() = KeyBoxDatabase.getInstance(this)

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: android.os.CancellationSignal,
        callback: FillCallback
    ) {
        try {
            val structure = request.fillContexts.lastOrNull()?.structure
            if (structure == null) {
                callback.onSuccess(null)
                return
            }

            // 尝试获取 DEK（由主 App 解锁后写入的 autofill 专用封装）
            val dek = AutoFillKeyStore.getDek(this) ?: run {
                callback.onSuccess(null)
                return
            }

            // 目标 App 的包名（用于域名匹配过滤）
            val targetPackage = structure.activityComponent?.packageName

            val credentials = matchCredentials(dek, targetPackage)
            dek.fill(0)

            if (credentials.isEmpty()) {
                callback.onSuccess(null)
                return
            }

            val response = buildFillResponse(credentials, structure)
            callback.onSuccess(response)
        } catch (e: Exception) {
            callback.onSuccess(null)
        }
    }

    override fun onSaveRequest(request: android.service.autofill.SaveRequest, callback: SaveCallback) {
        try {
            val context = request.fillContexts.lastOrNull()?.structure ?: run {
                callback.onSuccess()
                return
            }

            // 提取用户刚输入的用户名和密码
            val username = extractFieldValue(context, View.AUTOFILL_HINT_USERNAME)
            val password = extractFieldValue(context, View.AUTOFILL_HINT_PASSWORD)

            val dek = AutoFillKeyStore.getDek(this)
            if (dek != null && !password.isNullOrEmpty()) {
                val targetPackage = context.activityComponent?.packageName
                val targetLabel = context.activityComponent?.shortClassName ?: ""
                saveCredential(dek, targetPackage ?: "", targetLabel, username.orEmpty(), password)
                dek.fill(0)
            }
            callback.onSuccess()
        } catch (e: Exception) {
            callback.onSuccess()
        }
    }

    // ===== 内部 =====

    /**
     * 匹配凭证，按目标包名/域名过滤。
     * 匹配规则：条目 URL 的域名包含目标包名的后半段（如 github.com 匹配 com.github.android），
     * 或条目名称包含包名关键词。若无 URL 匹配，回退到全量列出（但优先展示匹配项）。
     */
    private fun matchCredentials(dek: ByteArray, targetPackage: String?): List<Credential> {
        val items = runBlocking {
            db.passwordDao().getAll().filter { it.deletedAt == null }
        }
        val all = items.mapNotNull { entity ->
            try {
                Credential(
                    id = entity.id,
                    name = entity.name,
                    url = entity.url,
                    username = AesGcm.decryptString(entity.username, dek),
                    password = AesGcm.decryptString(entity.password, dek)
                )
            } catch (e: Exception) {
                null
            }
        }

        if (targetPackage.isNullOrBlank()) return all

        // 按域名匹配优先
        val matched = all.filter { cred ->
            val domain = extractDomain(cred.url)
            domain.isNotBlank() && isDomainMatch(domain, targetPackage)
        }
        return matched.ifEmpty { all }
    }

    private fun extractDomain(url: String): String {
        val clean = url.trim().lowercase()
            .removePrefix("http://").removePrefix("https://")
            .substringBefore("/").substringBefore(":")
        return clean.substringAfterLast("@") // 去掉 user:pass@ 前缀
    }

    private fun isDomainMatch(domain: String, packageName: String): Boolean {
        // 提取包名的核心部分：com.github.android -> github
        val pkgParts = packageName.split(".")
        val core = pkgParts.firstOrNull { it.length > 2 } ?: packageName
        // 域名包含包名核心，或包名核心包含域名
        return domain.contains(core) || core.contains(domain.substringBefore("."))
    }

    private fun extractFieldValue(structure: AssistStructure, hint: String): String? {
        val id = findFieldId(structure, hint) ?: return null
        // 从 fillContexts 中查找该 id 的当前值
        // 简化：遍历 viewNode 找 text
        for (i in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(i).rootViewNode
            val value = findTextRecursive(root, hint)
            if (value != null) return value
        }
        return null
    }

    private fun findTextRecursive(node: AssistStructure.ViewNode, hint: String): String? {
        if (node.autofillHints?.contains(hint) == true) {
            node.text?.toString()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        for (i in 0 until node.childCount) {
            val found = findTextRecursive(node.getChildAt(i), hint)
            if (found != null) return found
        }
        return null
    }

    private fun saveCredential(dek: ByteArray, packageName: String, label: String, username: String, password: String) {
        val now = System.currentTimeMillis()
        val entity = PasswordItemEntity(
            name = label.ifBlank { packageName.substringAfterLast(".").ifBlank { "新账号" } },
            url = packageName,
            username = AesGcm.encryptString(username, dek),
            password = AesGcm.encryptString(password, dek),
            email = "",
            phone = "",
            totpSecret = "",
            notes = "",
            customFields = "",
            favorite = false,
            categoryId = null,
            createdAt = now,
            updatedAt = now,
            lastUsedAt = null
        )
        runBlocking { db.passwordDao().insert(entity) }
    }

    private fun buildFillResponse(
        credentials: List<Credential>,
        structure: AssistStructure
    ): FillResponse {
        val usernameId = findFieldId(structure, View.AUTOFILL_HINT_USERNAME)
        val passwordId = findFieldId(structure, View.AUTOFILL_HINT_PASSWORD)

        if (usernameId == null && passwordId == null) {
            return FillResponse.Builder().build()
        }

        val builder = FillResponse.Builder()

        credentials.forEach { cred ->
            val dsBuilder = Dataset.Builder()
            if (usernameId != null) {
                dsBuilder.setValue(usernameId, AutofillValue.forText(cred.username))
            }
            if (passwordId != null) {
                dsBuilder.setValue(passwordId, AutofillValue.forText(cred.password))
            }
            builder.addDataset(dsBuilder.build())
        }

        // 保存信息
        if (usernameId != null && passwordId != null) {
            builder.setSaveInfo(
                SaveInfo.Builder(
                    SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                    arrayOf(usernameId, passwordId)
                ).build()
            )
        }

        return builder.build()
    }

    private fun findFieldId(structure: AssistStructure, hint: String): AutofillId? {
        for (i in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(i).rootViewNode
            val found = findRecursive(root, hint)
            if (found != null) return found
        }
        return null
    }

    private fun findRecursive(node: AssistStructure.ViewNode, hint: String): AutofillId? {
        if (node.autofillHints?.contains(hint) == true) return node.autofillId
        for (i in 0 until node.childCount) {
            val found = findRecursive(node.getChildAt(i), hint)
            if (found != null) return found
        }
        return null
    }

    data class Credential(val id: Long, val name: String, val url: String, val username: String, val password: String)
}
