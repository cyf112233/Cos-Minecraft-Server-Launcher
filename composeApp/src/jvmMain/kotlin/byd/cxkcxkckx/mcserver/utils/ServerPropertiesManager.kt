package byd.cxkcxkckx.mcserver.utils

import java.io.File
import java.util.Properties

/**
 * server.properties 文件管理器
 */
object ServerPropertiesManager {
    
    /**
     * 检查 server.properties 文件是否存在
     */
    fun exists(serverPath: String): Boolean {
        return File(serverPath, "server.properties").exists()
    }
    
    /**
     * 读取 server.properties 文件
     */
    fun load(serverPath: String): Map<String, String> {
        val file = File(serverPath, "server.properties")
        if (!file.exists()) {
            return emptyMap()
        }
        
        val properties = mutableMapOf<String, String>()
        
        try {
            file.readLines().forEach { line ->
                val trimmed = line.trim()
                // 跳过注释和空行
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    return@forEach
                }
                
                // 解析键值对
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2) {
                    properties[parts[0].trim()] = parts[1].trim()
                }
            }
        } catch (e: Exception) {
            println("读取 server.properties 失败: ${e.message}")
            e.printStackTrace()
        }
        
        return properties
    }
    
    /**
     * 保存 server.properties 文件
     */
    fun save(serverPath: String, properties: Map<String, String>): Boolean {
        val file = File(serverPath, "server.properties")
        
        return try {
            val lines = mutableListOf<String>()
            lines.add("#Minecraft server properties")
            lines.add("#${java.time.LocalDateTime.now()}")
            
            // 按键排序保存
            properties.entries.sortedBy { it.key }.forEach { (key, value) ->
                lines.add("$key=$value")
            }
            
            file.writeText(lines.joinToString("\n"))
            println("server.properties 已保存")
            true
        } catch (e: Exception) {
            println("保存 server.properties 失败: ${e.message}")
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
                key = "motd",
                displayName = "服务器描述 (MOTD)",
                description = "显示在服务器列表中的描述",
                type = PropertyType.TEXT,
                defaultValue = "A Minecraft Server"
            ),
            PropertyDefinition(
                key = "server-port",
                displayName = "服务器端口",
                description = "服务器监听端口",
                type = PropertyType.NUMBER,
                defaultValue = "25565"
            ),
            PropertyDefinition(
                key = "max-players",
                displayName = "最大玩家数",
                description = "服务器最大玩家数量",
                type = PropertyType.NUMBER,
                defaultValue = "20"
            ),
            PropertyDefinition(
                key = "gamemode",
                displayName = "游戏模式",
                description = "默认游戏模式",
                type = PropertyType.SELECT,
                defaultValue = "survival",
                options = listOf("survival", "creative", "adventure", "spectator")
            ),
            PropertyDefinition(
                key = "difficulty",
                displayName = "难度",
                description = "游戏难度",
                type = PropertyType.SELECT,
                defaultValue = "easy",
                options = listOf("peaceful", "easy", "normal", "hard")
            ),
            PropertyDefinition(
                key = "online-mode",
                displayName = "正版验证",
                description = "是否启用正版验证",
                type = PropertyType.BOOLEAN,
                defaultValue = "true"
            ),
            PropertyDefinition(
                key = "white-list",
                displayName = "白名单",
                description = "是否启用白名单",
                type = PropertyType.BOOLEAN,
                defaultValue = "false"
            ),
            PropertyDefinition(
                key = "pvp",
                displayName = "PvP",
                description = "是否允许玩家对战",
                type = PropertyType.BOOLEAN,
                defaultValue = "true"
            ),
            PropertyDefinition(
                key = "allow-flight",
                displayName = "允许飞行",
                description = "是否允许玩家飞行",
                type = PropertyType.BOOLEAN,
                defaultValue = "false"
            ),
            PropertyDefinition(
                key = "view-distance",
                displayName = "视距",
                description = "服务器视距（区块）",
                type = PropertyType.NUMBER,
                defaultValue = "10"
            ),
            PropertyDefinition(
                key = "simulation-distance",
                displayName = "模拟距离",
                description = "实体和方块更新距离（区块）",
                type = PropertyType.NUMBER,
                defaultValue = "10"
            ),
            PropertyDefinition(
                key = "spawn-protection",
                displayName = "出生点保护",
                description = "出生点保护半径（方块）",
                type = PropertyType.NUMBER,
                defaultValue = "16"
            ),
            PropertyDefinition(
                key = "level-name",
                displayName = "世界名称",
                description = "世界文件夹名称",
                type = PropertyType.TEXT,
                defaultValue = "world"
            ),
            PropertyDefinition(
                key = "level-seed",
                displayName = "世界种子",
                description = "世界生成种子",
                type = PropertyType.TEXT,
                defaultValue = ""
            )
        )
    }
}

/**
 * 配置项定义
 */
data class PropertyDefinition(
    val key: String,
    val displayName: String,
    val description: String,
    val type: PropertyType,
    val defaultValue: String,
    val options: List<String> = emptyList()
)

/**
 * 配置项类型
 */
enum class PropertyType {
    TEXT,       // 文本
    NUMBER,     // 数字
    BOOLEAN,    // 布尔值
    SELECT      // 选择列表
}
