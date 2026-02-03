package byd.cxkcxkckx.mcserver.utils

import byd.cxkcxkckx.mcserver.data.PluginInfo
import java.awt.Desktop
import java.io.File

object PluginManager {
    
    /**
     * 扫描服务器插件文件夹
     */
    fun scanPlugins(serverPath: String): List<PluginInfo> {
        val pluginsDir = File(serverPath, "plugins")
        
        if (!pluginsDir.exists() || !pluginsDir.isDirectory) {
            return emptyList()
        }
        
        val plugins = mutableListOf<PluginInfo>()
        
        pluginsDir.listFiles()?.forEach { file ->
            when {
                file.name.endsWith(".jar", ignoreCase = true) -> {
                    plugins.add(
                        PluginInfo(
                            fileName = file.name,
                            file = file,
                            isEnabled = true
                        )
                    )
                }
                file.name.endsWith(".jar.disabled", ignoreCase = true) -> {
                    plugins.add(
                        PluginInfo(
                            fileName = file.name,
                            file = file,
                            isEnabled = false
                        )
                    )
                }
            }
        }
        
        return plugins.sortedBy { it.displayName.lowercase() }
    }
    
    /**
     * 启用插件
     */
    fun enablePlugin(plugin: PluginInfo): Result<Unit> {
        return try {
            if (plugin.isEnabled) {
                return Result.success(Unit)
            }
            
            val newFile = File(plugin.file.parentFile, plugin.file.name.removeSuffix(".disabled"))
            val success = plugin.file.renameTo(newFile)
            
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("无法重命名文件"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 禁用插件
     */
    fun disablePlugin(plugin: PluginInfo): Result<Unit> {
        return try {
            if (!plugin.isEnabled) {
                return Result.success(Unit)
            }
            
            val newFile = File(plugin.file.parentFile, "${plugin.file.name}.disabled")
            val success = plugin.file.renameTo(newFile)
            
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("无法重命名文件"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 删除插件
     */
    fun deletePlugin(plugin: PluginInfo): Result<Unit> {
        return try {
            val success = plugin.file.delete()
            
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("无法删除文件"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 重命名插件
     */
    fun renamePlugin(plugin: PluginInfo, newName: String): Result<Unit> {
        return try {
            // 确保新名称有正确的扩展名
            val extension = if (plugin.isEnabled) ".jar" else ".jar.disabled"
            val finalName = if (newName.endsWith(extension)) {
                newName
            } else {
                "$newName$extension"
            }
            
            val newFile = File(plugin.file.parentFile, finalName)
            
            // 检查文件是否已存在
            if (newFile.exists()) {
                return Result.failure(Exception("文件名已存在"))
            }
            
            val success = plugin.file.renameTo(newFile)
            
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("无法重命名文件"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 打开插件文件夹
     */
    fun openPluginsFolder(serverPath: String): Result<Unit> {
        return try {
            val pluginsDir = File(serverPath, "plugins")
            
            // 如果文件夹不存在，创建它
            if (!pluginsDir.exists()) {
                pluginsDir.mkdirs()
            }
            
            // 使用 Desktop API 打开文件夹
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(pluginsDir)
                Result.success(Unit)
            } else {
                // 如果 Desktop API 不可用，尝试使用系统命令
                val os = System.getProperty("os.name").lowercase()
                val command = when {
                    os.contains("win") -> arrayOf("explorer", pluginsDir.absolutePath)
                    os.contains("mac") -> arrayOf("open", pluginsDir.absolutePath)
                    else -> arrayOf("xdg-open", pluginsDir.absolutePath)
                }
                
                Runtime.getRuntime().exec(command)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
