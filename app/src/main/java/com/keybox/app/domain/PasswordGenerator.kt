package com.keybox.app.domain

import java.security.SecureRandom

/**
 * 密码生成器：随机密码 + 密码短语两种模式。
 * 必须使用安全随机源。
 */
object PasswordGenerator {

    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijkmnpqrstuvwxyz"
    private const val DIGIT = "23456789"
    private const val SYMBOL = "!@#$%^&*()-_=+[]{}?/"
    private const val AMBIGUOUS = "O0Il1|`'\",;:."

    private val random = SecureRandom()

    /** 生成随机密码。 */
    fun generateRandom(
        length: Int = 20,
        useUpper: Boolean = true,
        useLower: Boolean = true,
        useDigit: Boolean = true,
        useSymbol: Boolean = true,
        excludeAmbiguous: Boolean = false
    ): String {
        val pools = buildList {
            if (useUpper) add(UPPER)
            if (useLower) add(LOWER)
            if (useDigit) add(DIGIT)
            if (useSymbol) add(SYMBOL)
        }
        require(pools.isNotEmpty()) { "至少选择一种字符集" }
        if (excludeAmbiguous) {
            val clean = pools.map { it.filter { c -> c !in AMBIGUOUS } }
            return buildPassword(length, clean)
        }
        return buildPassword(length, pools)
    }

    private fun buildPassword(length: Int, pools: List<String>): String {
        val all = pools.joinToString("")
        val sb = StringBuilder(length)
        // 确保每类至少一个
        pools.forEach { sb.append(it[random.nextInt(it.length)]) }
        while (sb.length < length) {
            sb.append(all[random.nextInt(all.length)])
        }
        // 洗牌
        val chars = sb.toString().toCharArray()
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return String(chars)
    }

    /** 生成密码短语（Diceware 风格）。 */
    fun generatePassphrase(wordCount: Int = 4, separator: String = "-"): String {
        val words = listOf(
            "correct", "horse", "battery", "staple", "ocean", "mountain", "river",
            "forest", "silver", "golden", "purple", "orange", "rocket", "planet",
            "garden", "window", "candle", "thunder", "snowflake", "dragon",
            "laptop", "keyboard", "coffee", "music", "bridge", "harbor", "castle",
            "meadow", "falcon", "lantern", "marble", "pebble", "orchid", "voyage",
            "zephyr", "ember", "canyon", "glacier", "harbor", "island", "jungle",
            "knight", "lunar", "meteor", "nectar", "osprey", "pearl", "quartz",
            "raven", "saffron", "temple", "umbrella", "velvet", "willow", "xylophone",
            "yonder", "zebra", "amber", "basil", "cedar", "dahlia", "elder", "fern"
        )
        val chosen = (1..wordCount).map { words[random.nextInt(words.size)] }
        return chosen.joinToString(separator)
    }
}
