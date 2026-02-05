package byd.cxkcxkckx.mcserver.utils

import java.io.File

/**
 * 路径管理器 - 统一管理应用数据存储路径
 * 使用用户目录而不是程序目录，这样更新程序时不会丢失数据
 */
object PathManager {
    // 应用数据文件夹名称
    private const val APP_DATA_FOLDER = "CosMinecraftServerLauncher"
    
    /**
     * 获取应用数据根目录（在用户文件夹下）
     * Windows: C:\Users\Username\CosMinecraftServerLauncher
     * Linux/Mac: /home/username/CosMinecraftServerLauncher
     */
    fun getAppDataDirectory(): File {
        val userHome = System.getProperty("user.home")
        val appDataDir = File(userHome, APP_DATA_FOLDER)
        
        // 确保目录存在
        if (!appDataDir.exists()) {
            appDataDir.mkdirs()
            println("创建应用数据目录: ${appDataDir.absolutePath}")
        }
        
        return appDataDir
    }
    
    /**
     * 获取服务器存储目录
     * 路径: 用户目录/CosMinecraftServerLauncher/mcserver
     */
    fun getServersDirectory(): File {
        val serversDir = File(getAppDataDirectory(), "mcserver")
        
        // 确保目录存在
        if (!serversDir.exists()) {
            serversDir.mkdirs()
            println("创建服务器目录: ${serversDir.absolutePath}")
        }
        
        return serversDir
    }
    
    /**
     * 获取下载目录（与服务器目录相同）
     */
    fun getDownloadDirectory(): File {
        return getServersDirectory()
    }
    
    /**
     * 获取特定服务器的目录
     */
    fun getServerDirectory(serverName: String): File {
        return File(getServersDirectory(), serverName)
    }
    
    /**
     * 迁移旧数据（从程序目录到用户目录）
     * 如果程序目录下存在 mcserver 文件夹，询问是否迁移
     */
    fun checkAndMigrateOldData(): Boolean {
        val oldDir = File(System.getProperty("user.dir"), "mcserver")
        val newDir = getServersDirectory()
        
        // 如果旧目录存在且新目录为空，则可以迁移
        if (oldDir.exists() && oldDir.isDirectory && oldDir.listFiles()?.isNotEmpty() == true) {
            val newDirEmpty = newDir.listFiles()?.isEmpty() ?: true
            
            if (newDirEmpty) {
                println("检测到旧的 mcserver 目录: ${oldDir.absolutePath}")
                println("新的用户目录为空，建议手动迁移数据到: ${newDir.absolutePath}")
                return true
            }
        }
        
        return false
    }
    
    /**
     * 获取旧的程序目录路径（用于提示用户）
     */
    fun getOldProgramDirectory(): File {
        return File(System.getProperty("user.dir"), "mcserver")
    }
}
