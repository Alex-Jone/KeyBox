package com.keybox.app.ui.components

import androidx.compose.ui.graphics.Color

/**
 * 根据名称哈希生成确定性的头像底色（对齐设计稿的彩色圆形账号图标）。
 * 同一名称永远得到同一颜色。
 */
object AvatarColors {

    private val palette = listOf(
        Color(0xFF2D5BF0), // 蓝
        Color(0xFF22B07D), // 绿
        Color(0xFF7B61FF), // 紫
        Color(0xFFFF8A3D), // 橙
        Color(0xFFF2557A), // 玫红
        Color(0xFF00B8D9), // 青
        Color(0xFFF2B705), // 金黄
        Color(0xFF5A6B8C), // 蓝灰
        Color(0xFFE5484D), // 红
        Color(0xFF3E9B4F)  // 深绿
    )

    fun colorFor(name: String): Color {
        if (name.isEmpty()) return palette[0]
        val hash = name.fold(0) { acc, c -> acc * 31 + c.code }
        return palette[(hash and Int.MAX_VALUE) % palette.size]
    }
}
