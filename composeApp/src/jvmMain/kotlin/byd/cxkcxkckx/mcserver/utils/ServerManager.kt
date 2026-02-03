package byd.cxkcxkckx.mcserver.utils

import byd.cxkcxkckx.mcserver.data.ServerInfo
import java.io.File

object ServerManager {
    private const val SERVERS_DIR = "mcserver"
    
    /**
     * 获取或创建 mcserver 目录
     */
    fun getServersDirectory(): File {
        val currentDir = System.getProperty("user.dir")
        val serversDir = File(currentDir, SERVERS_DIR)
        
        if (!serversDir.exists()) {
            serversDir.mkdirs()
            println("创建服务器目录: ${serversDir.absolutePath}")
        }
        
        return serversDir
    }
    
    /**
     * 扫描并获取所有服务器
     */
    fun scanServers(): List<ServerInfo> {
        val serversDir = getServersDirectory()
        val servers = mutableListOf<ServerInfo>()
        
        serversDir.listFiles()?.forEach { folder ->
            if (folder.isDirectory) {
                val serverInfo = detectServerInfo(folder)
                if (serverInfo != null) {
                    servers.add(serverInfo)
                }
            }
        }
        
        return servers.sortedBy { it.name }
    }
    
    /**
     * 检测服务器信息
     */
    private fun detectServerInfo(folder: File): ServerInfo? {
        // 检查是否包含服务器 jar 文件
        val jarFiles = folder.listFiles { file ->
            file.extension.equals("jar", ignoreCase = true)
        }
        
        if (jarFiles.isNullOrEmpty()) {
            return null
        }
        
        val name = folder.name
        val (type, version) = parseServerNameAndVersion(name, jarFiles)
        
        // 从 YAML 配置文件加载配置
        val config = ConfigManager.loadConfig(folder.absolutePath)
        
        return ServerInfo(
            id = folder.absolutePath,
            name = name,
            path = folder.absolutePath,
            version = version,
            type = type,
            config = config
        )
    }
    
    /**
     * 从文件夹名称和 jar 文件解析服务器类型和版本
     */
    private fun parseServerNameAndVersion(folderName: String, jarFiles: Array<File>): Pair<String, String> {
        var type = "Unknown"
        var version = "Unknown"
        
        // 从文件夹名称解析
        val lowerName = folderName.lowercase()
        when {
            lowerName.contains("paper") -> type = "Paper"
            lowerName.contains("spigot") -> type = "Spigot"
            lowerName.contains("bukkit") -> type = "Bukkit"
            lowerName.contains("forge") -> type = "Forge"
            lowerName.contains("fabric") -> type = "Fabric"
            lowerName.contains("vanilla") -> type = "Vanilla"
        }
        
        // 尝试从文件夹名称提取版本号
        val versionRegex = Regex("""(\d+\.\d+(?:\.\d+)?)""")
        val versionMatch = versionRegex.find(folderName)
        if (versionMatch != null) {
            version = versionMatch.value
        }
        
        // 从 jar 文件名解析（如果文件夹名称没有提供足够信息）
        if (type == "Unknown" || version == "Unknown") {
            jarFiles.forEach { jar ->
                val jarName = jar.nameWithoutExtension.lowercase()
                
                if (type == "Unknown") {
                    when {
                        jarName.contains("paper") -> type = "Paper"
                        jarName.contains("spigot") -> type = "Spigot"
                        jarName.contains("bukkit") -> type = "Bukkit"
                        jarName.contains("forge") -> type = "Forge"
                        jarName.contains("fabric") -> type = "Fabric"
                    }
                }
                
                if (version == "Unknown") {
                    val jarVersionMatch = versionRegex.find(jarName)
                    if (jarVersionMatch != null) {
                        version = jarVersionMatch.value
                    }
                }
            }
        }
        
        return Pair(type, version)
    }
    
    /**
     * 创建新服务器文件夹
     */
    fun createServerFolder(name: String): File {
        val serversDir = getServersDirectory()
        val serverFolder = File(serversDir, name)
        
        if (!serverFolder.exists()) {
            serverFolder.mkdirs()
        }
        
        return serverFolder
    }
    
    /**
     * 检查服务器文件夹是否有效
     */
    fun isValidServerFolder(folder: File): Boolean {
        if (!folder.exists() || !folder.isDirectory) {
            return false
        }
        
        // 检查是否有 jar 文件
        val hasJar = folder.listFiles()?.any { 
            it.extension.equals("jar", ignoreCase = true) 
        } ?: false
        
        return hasJar
    }
    
    /**
     * 更新服务器配置
     */
    fun updateServerConfig(serverInfo: ServerInfo): Boolean {
        return ConfigManager.saveConfig(serverInfo.path, serverInfo.config)
    }
    
    /**
     * 重新加载服务器配置
     */
    fun reloadServerConfig(serverInfo: ServerInfo): ServerInfo {
        val newConfig = ConfigManager.loadConfig(serverInfo.path)
        return serverInfo.copy(config = newConfig)
    }
}
