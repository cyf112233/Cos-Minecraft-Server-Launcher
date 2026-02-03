package byd.cxkcxkckx.mcserver.utils

import byd.cxkcxkckx.mcserver.data.DownloadStatus
import byd.cxkcxkckx.mcserver.data.DownloadTask
import byd.cxkcxkckx.mcserver.data.ServerType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

object DownloadManager {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // 使用当前工作目录下的 mcserver 文件夹
    private val baseDir = File(System.getProperty("user.dir"), "mcserver")
    
    init {
        baseDir.mkdirs()
        println("=== DownloadManager 初始化 ===")
        println("下载目录: ${baseDir.absolutePath}")
        println("目录是否存在: ${baseDir.exists()}")
        println("目录是否可写: ${baseDir.canWrite()}")
    }
    
    /**
     * 添加下载任务
     */
    fun addDownload(
        serverType: ServerType,
        version: String,
        build: String,
        downloadUrl: String
    ): String {
        println("\n=== 添加下载任务 ===")
        println("服务器类型: ${serverType.displayName}")
        println("版本: $version")
        println("构建: $build")
        println("下载 URL: $downloadUrl")
        
        val taskId = UUID.randomUUID().toString()
        val folderName = getFolderName(serverType, version, build)
        val targetDir = File(baseDir, folderName)
        val targetFile = File(targetDir, "server.jar")
        
        println("目标文件夹: ${targetDir.absolutePath}")
        println("目标文件: ${targetFile.absolutePath}")
        
        val task = DownloadTask(
            id = taskId,
            serverType = serverType,
            version = version,
            build = build,
            fileName = "server.jar",
            downloadUrl = downloadUrl,
            targetPath = targetFile.absolutePath,
            status = DownloadStatus.PENDING
        )
        
        _tasks.value = _tasks.value + task
        
        // 开始下载
        scope.launch {
            downloadFile(task)
        }
        
        return taskId
    }
    
    /**
     * 获取文件夹名称，如果已存在则添加序号
     */
    private fun getFolderName(serverType: ServerType, version: String, build: String): String {
        val baseName = "${serverType.displayName}-$version-$build"
        var folderName = baseName
        var counter = 1
        
        while (File(baseDir, folderName).exists()) {
            folderName = "$baseName-$counter"
            counter++
        }
        
        return folderName
    }
    
    /**
     * 下载文件
     */
    private suspend fun downloadFile(task: DownloadTask) = withContext(Dispatchers.IO) {
        try {
            println("\n=== 开始下载 ===")
            println("任务 ID: ${task.id}")
            println("下载 URL: ${task.downloadUrl}")
            
            updateTaskStatus(task.id, DownloadStatus.DOWNLOADING)
            
            val targetFile = File(task.targetPath)
            val targetDir = targetFile.parentFile
            
            println("创建目录: ${targetDir?.absolutePath}")
            val dirCreated = targetDir?.mkdirs() ?: false
            println("目录创建结果: $dirCreated (已存在也返回 false)")
            println("目录是否存在: ${targetDir?.exists()}")
            println("目录是否可写: ${targetDir?.canWrite()}")
            
            println("连接到: ${task.downloadUrl}")
            val connection = URL(task.downloadUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MCServer/1.0")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.connect()
            
            val responseCode = connection.responseCode
            println("HTTP 响应码: $responseCode")
            
            if (responseCode != 200) {
                throw Exception("HTTP 错误: $responseCode")
            }
            
            val fileSize = connection.contentLengthLong
            println("文件大小: $fileSize bytes (${fileSize / 1024 / 1024} MB)")
            
            var downloadedSize = 0L
            
            println("开始写入文件: ${targetFile.absolutePath}")
            connection.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastProgressLog = 0
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        
                        // 更新进度
                        if (fileSize > 0) {
                            val progress = (downloadedSize.toFloat() / fileSize.toFloat())
                            updateTaskProgress(task.id, progress)
                            
                            // 每 10% 打印一次进度
                            val progressPercent = (progress * 100).toInt()
                            if (progressPercent >= lastProgressLog + 10) {
                                println("下载进度: $progressPercent% ($downloadedSize / $fileSize bytes)")
                                lastProgressLog = progressPercent
                            }
                        }
                    }
                }
            }
            
            println("\n=== 下载完成 ===")
            println("文件路径: ${targetFile.absolutePath}")
            println("文件是否存在: ${targetFile.exists()}")
            println("文件大小: ${targetFile.length()} bytes")
            
            updateTaskStatus(task.id, DownloadStatus.COMPLETED)
            updateTaskProgress(task.id, 1f)
            
        } catch (e: Exception) {
            println("\n=== 下载失败 ===")
            println("错误: ${e.message}")
            e.printStackTrace()
            updateTaskStatus(task.id, DownloadStatus.FAILED, e.message)
        }
    }
    
    /**
     * 更新任务状态
     */
    private fun updateTaskStatus(taskId: String, status: DownloadStatus, errorMessage: String? = null) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) {
                task.copy(status = status, errorMessage = errorMessage)
            } else {
                task
            }
        }
    }
    
    /**
     * 更新任务进度
     */
    private fun updateTaskProgress(taskId: String, progress: Float) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) {
                task.copy(progress = progress)
            } else {
                task
            }
        }
    }
    
    /**
     * 取消下载
     */
    fun cancelDownload(taskId: String) {
        updateTaskStatus(taskId, DownloadStatus.CANCELLED)
    }
    
    /**
     * 删除任务
     */
    fun removeTask(taskId: String) {
        _tasks.value = _tasks.value.filter { it.id != taskId }
    }
    
    /**
     * 清空已完成的任务
     */
    fun clearCompleted() {
        _tasks.value = _tasks.value.filter { 
            it.status != DownloadStatus.COMPLETED && 
            it.status != DownloadStatus.FAILED &&
            it.status != DownloadStatus.CANCELLED
        }
    }
    
    /**
     * 获取活动任务数量
     */
    fun getActiveTaskCount(): Int {
        return _tasks.value.count { 
            it.status == DownloadStatus.PENDING || 
            it.status == DownloadStatus.DOWNLOADING 
        }
    }
}
