package byd.cxkcxkckx.mcserver.utils

import androidx.compose.ui.graphics.Color

/**
 * ANSI 颜色代码解析器
 */
object AnsiColorParser {
    
    // ANSI 颜色映射
    private val ansiColors = mapOf(
        "30" to Color(0xFF000000), // Black
        "31" to Color(0xFFAA0000), // Red
        "32" to Color(0xFF00AA00), // Green
        "33" to Color(0xFFAAAA00), // Yellow
        "34" to Color(0xFF0000AA), // Blue
        "35" to Color(0xFFAA00AA), // Magenta
        "36" to Color(0xFF00AAAA), // Cyan
        "37" to Color(0xFFAAAAAA), // White
        
        // Bright colors
        "90" to Color(0xFF555555), // Bright Black (Gray)
        "91" to Color(0xFFFF5555), // Bright Red
        "92" to Color(0xFF55FF55), // Bright Green
        "93" to Color(0xFFFFFF55), // Bright Yellow
        "94" to Color(0xFF5555FF), // Bright Blue
        "95" to Color(0xFFFF55FF), // Bright Magenta
        "96" to Color(0xFF55FFFF), // Bright Cyan
        "97" to Color(0xFFFFFFFF), // Bright White
    )
    
    // Minecraft 颜色代码映射
    private val minecraftColors = mapOf(
        '0' to Color(0xFF000000), // Black
        '1' to Color(0xFF0000AA), // Dark Blue
        '2' to Color(0xFF00AA00), // Dark Green
        '3' to Color(0xFF00AAAA), // Dark Aqua
        '4' to Color(0xFFAA0000), // Dark Red
        '5' to Color(0xFFAA00AA), // Dark Purple
        '6' to Color(0xFFFFAA00), // Gold
        '7' to Color(0xFFAAAAAA), // Gray
        '8' to Color(0xFF555555), // Dark Gray
        '9' to Color(0xFF5555FF), // Blue
        'a' to Color(0xFF55FF55), // Green
        'b' to Color(0xFF55FFFF), // Aqua
        'c' to Color(0xFFFF5555), // Red
        'd' to Color(0xFFFF55FF), // Light Purple
        'e' to Color(0xFFFFFF55), // Yellow
        'f' to Color(0xFFFFFFFF), // White
        'r' to Color.Unspecified,  // Reset
    )
    
    data class ColoredText(
        val text: String,
        val color: Color = Color.Unspecified,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false
    )
    
    /**
     * 解析包含 ANSI 和 Minecraft 颜色代码的文本
     */
    fun parse(text: String): List<ColoredText> {
        val result = mutableListOf<ColoredText>()
        var currentText = StringBuilder()
        var currentColor = Color.Unspecified
        var bold = false
        var italic = false
        var underline = false
        
        var i = 0
        while (i < text.length) {
            when {
                // ANSI 颜色代码 \u001B[...m 或 ESC[...m
                text.startsWith("\u001B[", i) || text.startsWith("\u001b[", i) -> {
                    // 保存当前文本
                    if (currentText.isNotEmpty()) {
                        result.add(ColoredText(currentText.toString(), currentColor, bold, italic, underline))
                        currentText.clear()
                    }
                    
                    // 查找结束的 'm'
                    val endIndex = text.indexOf('m', i)
                    if (endIndex != -1) {
                        val code = text.substring(i + 2, endIndex)
                        val codes = code.split(';')
                        
                        codes.forEach { c ->
                            when (c) {
                                "0" -> {
                                    // Reset
                                    currentColor = Color.Unspecified
                                    bold = false
                                    italic = false
                                    underline = false
                                }
                                "1" -> bold = true
                                "3" -> italic = true
                                "4" -> underline = true
                                "22" -> bold = false
                                "23" -> italic = false
                                "24" -> underline = false
                                in ansiColors -> currentColor = ansiColors[c] ?: Color.Unspecified
                            }
                        }
                        
                        i = endIndex + 1
                        continue
                    }
                }
                
                // Minecraft 颜色代码 §X 或 &X
                (text[i] == '§' || text[i] == '&') && i + 1 < text.length -> {
                    // 保存当前文本
                    if (currentText.isNotEmpty()) {
                        result.add(ColoredText(currentText.toString(), currentColor, bold, italic, underline))
                        currentText.clear()
                    }
                    
                    val colorCode = text[i + 1].lowercaseChar()
                    when (colorCode) {
                        in minecraftColors -> {
                            currentColor = minecraftColors[colorCode] ?: Color.Unspecified
                        }
                        'l' -> bold = true
                        'o' -> italic = true
                        'n' -> underline = true
                        'r' -> {
                            currentColor = Color.Unspecified
                            bold = false
                            italic = false
                            underline = false
                        }
                    }
                    
                    i += 2
                    continue
                }
                
                else -> {
                    currentText.append(text[i])
                }
            }
            i++
        }
        
        // 添加剩余文本
        if (currentText.isNotEmpty()) {
            result.add(ColoredText(currentText.toString(), currentColor, bold, italic, underline))
        }
        
        return result.ifEmpty { listOf(ColoredText(text)) }
    }
    
    /**
     * 移除所有颜色代码，返回纯文本
     */
    fun stripColors(text: String): String {
        return text
            .replace(Regex("\u001B\\[[0-9;]+m"), "") // ANSI
            .replace(Regex("\u001b\\[[0-9;]+m"), "") // ANSI lowercase
            .replace(Regex("[§&][0-9a-fk-or]"), "") // Minecraft
    }
}
