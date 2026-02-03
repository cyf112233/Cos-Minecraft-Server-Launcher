package byd.cxkcxkckx.mcserver.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class JavaInstallation(
    val path: String,
    val version: String,
    val displayName: String
)

object JavaDetector {
    suspend fun detectJavaInstallations(): List<JavaInstallation> = withContext(Dispatchers.IO) {
        val installations = mutableListOf<JavaInstallation>()
        
        // 获取所有盘符
        val roots = File.listRoots()
        
        for (root in roots) {
            val programFiles = File(root, "Program Files")
            if (programFiles.exists() && programFiles.isDirectory) {
                findJavaInDirectory(programFiles, installations)
            }
            
            val programFilesX86 = File(root, "Program Files (x86)")
            if (programFilesX86.exists() && programFilesX86.isDirectory) {
                findJavaInDirectory(programFilesX86, installations)
            }
        }
        
        // 检查 JAVA_HOME 环境变量
        val javaHome = System.getenv("JAVA_HOME")
        if (javaHome != null) {
            val javaExe = File(javaHome, "bin/java.exe")
            if (javaExe.exists()) {
                val version = getJavaVersion(javaExe.absolutePath)
                if (version != null && installations.none { it.path == javaExe.absolutePath }) {
                    installations.add(
                        JavaInstallation(
                            path = javaExe.absolutePath,
                            version = version,
                            displayName = "Java $version (JAVA_HOME)"
                        )
                    )
                }
            }
        }
        
        // 检查 PATH 环境变量
        val pathEnv = System.getenv("PATH")
        if (pathEnv != null) {
            val paths = pathEnv.split(File.pathSeparator)
            for (path in paths) {
                val javaExe = File(path, "java.exe")
                if (javaExe.exists()) {
                    val version = getJavaVersion(javaExe.absolutePath)
                    if (version != null && installations.none { it.path == javaExe.absolutePath }) {
                        installations.add(
                            JavaInstallation(
                                path = javaExe.absolutePath,
                                version = version,
                                displayName = "Java $version (PATH)"
                            )
                        )
                    }
                }
            }
        }
        
        installations.sortedByDescending { it.version }
    }
    
    private fun findJavaInDirectory(directory: File, installations: MutableList<JavaInstallation>) {
        try {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    // 查找常见的 Java 安装目录
                    if (file.name.contains("Java", ignoreCase = true) || 
                        file.name.contains("jdk", ignoreCase = true) ||
                        file.name.contains("jre", ignoreCase = true)) {
                        
                        val javaExe = File(file, "bin/java.exe")
                        if (javaExe.exists()) {
                            val version = getJavaVersion(javaExe.absolutePath)
                            if (version != null && installations.none { it.path == javaExe.absolutePath }) {
                                installations.add(
                                    JavaInstallation(
                                        path = javaExe.absolutePath,
                                        version = version,
                                        displayName = "Java $version (${file.name})"
                                    )
                                )
                            }
                        }
                        
                        // 递归查找子目录
                        file.listFiles()?.forEach { subFile ->
                            if (subFile.isDirectory) {
                                val subJavaExe = File(subFile, "bin/java.exe")
                                if (subJavaExe.exists()) {
                                    val version = getJavaVersion(subJavaExe.absolutePath)
                                    if (version != null && installations.none { it.path == subJavaExe.absolutePath }) {
                                        installations.add(
                                            JavaInstallation(
                                                path = subJavaExe.absolutePath,
                                                version = version,
                                                displayName = "Java $version (${subFile.name})"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略权限错误等
        }
    }
    
    private fun getJavaVersion(javaPath: String): String? {
        return try {
            val process = ProcessBuilder(javaPath, "-version")
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            
            // 解析版本号
            val versionRegex = """version "(.+?)"""".toRegex()
            val match = versionRegex.find(output)
            match?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }
}
