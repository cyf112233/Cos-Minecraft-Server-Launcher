package byd.cxkcxkckx.mcserver.utils

import byd.cxkcxkckx.mcserver.data.ServerConfig
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

object ConfigManager {
    private val yamlMapper = ObjectMapper(YAMLFactory()).apply {
        registerKotlinModule()
    }
    
    private const val CONFIG_FILE_NAME = "mcserver.config.yml"
    
    /**
     * 从服务器目录加载配置文件
     * @param serverPath 服务器目录路径
     * @return ServerConfig 配置对象，如果文件不存在则返回默认配置
     */
    fun loadConfig(serverPath: String): ServerConfig {
        val configFile = File(serverPath, CONFIG_FILE_NAME)
        
        return try {
            if (configFile.exists()) {
                yamlMapper.readValue(configFile, ServerConfig::class.java)
            } else {
                // 如果配置文件不存在，创建默认配置
                val defaultConfig = ServerConfig()
                saveConfig(serverPath, defaultConfig)
                defaultConfig
            }
        } catch (e: Exception) {
            println("加载配置文件失败: ${e.message}")
            e.printStackTrace()
            ServerConfig()
        }
    }
    
    /**
     * 保存配置到服务器目录
     * @param serverPath 服务器目录路径
     * @param config 要保存的配置对象
     * @return Boolean 是否保存成功
     */
    fun saveConfig(serverPath: String, config: ServerConfig): Boolean {
        val configFile = File(serverPath, CONFIG_FILE_NAME)
        
        return try {
            // 确保目录存在
            configFile.parentFile?.mkdirs()
            
            // 写入 YAML 文件
            yamlMapper.writerWithDefaultPrettyPrinter()
                .writeValue(configFile, config)
            
            println("配置已保存到: ${configFile.absolutePath}")
            true
        } catch (e: Exception) {
            println("保存配置文件失败: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 检查配置文件是否存在
     * @param serverPath 服务器目录路径
     * @return Boolean 配置文件是否存在
     */
    fun configExists(serverPath: String): Boolean {
        val configFile = File(serverPath, CONFIG_FILE_NAME)
        return configFile.exists()
    }
    
    /**
     * 删除配置文件
     * @param serverPath 服务器目录路径
     * @return Boolean 是否删除成功
     */
    fun deleteConfig(serverPath: String): Boolean {
        val configFile = File(serverPath, CONFIG_FILE_NAME)
        return try {
            if (configFile.exists()) {
                configFile.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            println("删除配置文件失败: ${e.message}")
            false
        }
    }
    
    /**
     * 获取配置文件路径
     * @param serverPath 服务器目录路径
     * @return String 配置文件的完整路径
     */
    fun getConfigPath(serverPath: String): String {
        return File(serverPath, CONFIG_FILE_NAME).absolutePath
    }
}
