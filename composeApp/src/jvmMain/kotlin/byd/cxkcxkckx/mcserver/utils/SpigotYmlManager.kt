package byd.cxkcxkckx.mcserver.utils

import java.io.File

/**
 * spigot.yml 文件管理器
 */
object SpigotYmlManager {
    
    /**
     * 检查 spigot.yml 文件是否存在
     */
    fun exists(serverPath: String): Boolean {
        return File(serverPath, "spigot.yml").exists()
    }
    
    /**
     * 读取 spigot.yml 文件中的配置
     * 注意：这是简化版解析，只处理常用的键值对
     */
    fun load(serverPath: String): Map<String, String> {
        val file = File(serverPath, "spigot.yml")
        if (!file.exists()) {
            return emptyMap()
        }
        
        val properties = mutableMapOf<String, String>()
        
        try {
            val lines = file.readLines()
            var currentSection = ""
            
            lines.forEach { line ->
                val trimmed = line.trimStart()
                
                // 跳过注释和空行
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    return@forEach
                }
                
                // 检测章节
                if (!trimmed.startsWith(" ") && !trimmed.startsWith("-") && trimmed.contains(":")) {
                    val parts = trimmed.split(":", limit = 2)
                    if (parts.size == 2) {
                        currentSection = parts[0].trim()
                        val value = parts[1].trim()
                        if (value.isNotEmpty()) {
                            properties[currentSection] = value
                        }
                    }
                } else if (trimmed.startsWith(" ") && trimmed.contains(":")) {
                    // 子项
                    val parts = trimmed.trim().split(":", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim()
                        if (value.isNotEmpty() && currentSection.isNotEmpty()) {
                            properties["$currentSection.$key"] = value
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("读取 spigot.yml 失败: ${e.message}")
            e.printStackTrace()
        }
        
        return properties
    }
    
    /**
     * 保存 spigot.yml 文件
     * 注意：这会重新生成整个文件，可能丢失注释
     */
    fun save(serverPath: String, properties: Map<String, String>): Boolean {
        val file = File(serverPath, "spigot.yml")
        
        return try {
            // 读取原文件
            val originalLines = if (file.exists()) file.readLines() else emptyList()
            val newLines = mutableListOf<String>()
            
            // 保留文件头部注释
            originalLines.takeWhile { it.trim().isEmpty() || it.trim().startsWith("#") }
                .forEach { newLines.add(it) }
            
            // 按章节组织配置
            val sections = mutableMapOf<String, MutableMap<String, String>>()
            properties.forEach { (key, value) ->
                if (key.contains(".")) {
                    val parts = key.split(".", limit = 2)
                    sections.getOrPut(parts[0]) { mutableMapOf() }[parts[1]] = value
                } else {
                    sections.getOrPut(key) { mutableMapOf() }[""] = value
                }
            }
            
            // 写入配置
            sections.forEach { (section, items) ->
                if (items.containsKey("") && items.size == 1) {
                    // 单值配置
                    newLines.add("$section: ${items[""]}")
                } else {
                    // 多值配置
                    newLines.add("$section:")
                    items.forEach { (key, value) ->
                        if (key.isNotEmpty()) {
                            newLines.add("  $key: $value")
                        }
                    }
                }
            }
            
            file.writeText(newLines.joinToString("\n"))
            println("spigot.yml 已保存")
            true
        } catch (e: Exception) {
            println("保存 spigot.yml 失败: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 获取常用配置项定义
     */
    fun getCommonProperties(): List<PropertyDefinition> {
        return listOf(
            PropertyDefinition(
                key = "settings.bungeecord",
                displayName = "BungeeCord 模式",
                description = "启用 BungeeCord 跨服支持",
                type = PropertyType.BOOLEAN,
                defaultValue = "false"
            ),
            PropertyDefinition(
                key = "settings.restart-on-crash",
                displayName = "崩溃自动重启",
                description = "服务器崩溃后自动重启",
                type = PropertyType.BOOLEAN,
                defaultValue = "true"
            ),
            PropertyDefinition(
                key = "settings.timeout-time",
                displayName = "超时时间",
                description = "玩家超时时间（秒）",
                type = PropertyType.NUMBER,
                defaultValue = "60"
            ),
            PropertyDefinition(
                key = "settings.netty-threads",
                displayName = "网络线程数",
                description = "Netty 网络线程数量",
                type = PropertyType.NUMBER,
                defaultValue = "4"
            ),
            PropertyDefinition(
                key = "world-settings.default.mob-spawn-range",
                displayName = "生物生成范围",
                description = "生物生成范围（区块）",
                type = PropertyType.NUMBER,
                defaultValue = "8"
            ),
            PropertyDefinition(
                key = "world-settings.default.view-distance",
                displayName = "视距",
                description = "服务器视距（区块，default 表示使用 server.properties）",
                type = PropertyType.TEXT,
                defaultValue = "default"
            ),
            PropertyDefinition(
                key = "world-settings.default.simulation-distance",
                displayName = "模拟距离",
                description = "模拟距离（区块，default 表示使用 server.properties）",
                type = PropertyType.TEXT,
                defaultValue = "default"
            ),
            PropertyDefinition(
                key = "world-settings.default.item-despawn-rate",
                displayName = "物品消失时间",
                description = "物品掉落后消失时间（tick，20 tick = 1 秒）",
                type = PropertyType.NUMBER,
                defaultValue = "6000"
            ),
            PropertyDefinition(
                key = "world-settings.default.arrow-despawn-rate",
                displayName = "箭矢消失时间",
                description = "箭矢射出后消失时间（tick）",
                type = PropertyType.NUMBER,
                defaultValue = "1200"
            ),
            PropertyDefinition(
                key = "world-settings.default.nerf-spawner-mobs",
                displayName = "削弱刷怪笼生物",
                description = "刷怪笼生成的生物AI削弱",
                type = PropertyType.BOOLEAN,
                defaultValue = "false"
            )
        )
    }
}
