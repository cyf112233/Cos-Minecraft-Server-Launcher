package byd.cxkcxkckx.mcserver.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ServerConfig(
    val serverId: String = UUID.randomUUID().toString(),
    val maxMemory: Int = 2048, // MB
    val minMemory: Int = 1024, // MB
    val safeMode: Boolean = false,
    val noGui: Boolean = true,
    val autoRestart: Boolean = false,
    val customJvmArgs: String = "",
    val customServerArgs: String = "",
    val javaPath: String = "java" // Java 可执行文件路径
) {
    /**
     * 生成完整的启动命令
     */
    fun generateStartCommand(jarPath: String): List<String> {
        val command = mutableListOf<String>()
        
        // Java 路径
        // Java 路径 - 如果包含空格需要用引号包裹
        if (javaPath.contains(" ")) {
            command.add("\"$javaPath\"")
        } else {
            command.add(javaPath)
        }
        
        // 内存参数
        command.add("-Xms${minMemory}M")
        command.add("-Xmx${maxMemory}M")
        
        // 自定义 JVM 参数
        if (customJvmArgs.isNotBlank()) {
            command.addAll(customJvmArgs.split(" ").filter { it.isNotBlank() })
        }
        
        // JAR 文件
        command.add("-jar")
        command.add(jarPath)
        
        // nogui 参数
        if (noGui) {
            command.add("--nogui")
        }
        
        // 安全模式
        if (safeMode) {
            command.add("--safeMode")
        }
        
        // 自定义服务器参数
        if (customServerArgs.isNotBlank()) {
            command.addAll(customServerArgs.split(" ").filter { it.isNotBlank() })
        }
        
        return command
    }
    
    /**
     * 生成启动命令字符串（用于显示）
     */
    fun getCommandString(jarPath: String): String {
        return generateStartCommand(jarPath).joinToString(" ")
    }
}
