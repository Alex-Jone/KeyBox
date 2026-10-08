package com.keybox.app.domain

/**
 * 密码强度检测：基于熵估算 + 简单规则。
 */
object PasswordStrength {

    enum class Level(val label: String) {
        WEAK("弱"), MEDIUM("中"), STRONG("强"), VERY_STRONG("很强")
    }

    fun evaluate(password: String): Pair<Level, Int> {
        if (password.isEmpty()) return Level.WEAK to 0

        var poolSize = 0
        if (password.any { it.isLowerCase() }) poolSize += 26
        if (password.any { it.isUpperCase() }) poolSize += 26
        if (password.any { it.isDigit() }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 33

        val entropy = password.length * (Math.log(poolSize.coerceAtLeast(2).toDouble()) / Math.log(2.0))

        val level = when {
            entropy < 40 -> Level.WEAK
            entropy < 60 -> Level.MEDIUM
            entropy < 80 -> Level.STRONG
            else -> Level.VERY_STRONG
        }
        return level to entropy.toInt()
    }

    /** 是否符合主密码最低要求（>= 12 位）。 */
    fun isMasterPasswordAcceptable(password: String): Boolean = password.length >= 12
}
