package byd.cxkcxkckx.mcserver.api

import byd.cxkcxkckx.mcserver.data.ServerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * PaperMC API - 支持 Paper, Folia, Velocity, Waterfall 等
 */
object PaperMCAPI {
    private const val API_URL = "https://api.papermc.io/v2"
    
    /**
     * 获取项目的所有版本（仅正式版本）
     */
    suspend fun getVersions(serverType: ServerType): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_URL/projects/${serverType.apiName}"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MCServer/1.0")
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = Json.parseToJsonElement(response).jsonObject
            
            val versions = json["versions"]?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?.filter { isStableVersion(it) }
                ?.reversed()
                ?: emptyList()
            
            Result.success(versions)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 获取指定版本的所有构建号
     */
    suspend fun getVersionBuilds(serverType: ServerType, version: String): Result<List<Int>> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_URL/projects/${serverType.apiName}/versions/$version"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MCServer/1.0")
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = Json.parseToJsonElement(response).jsonObject
            
            val builds = json["builds"]?.jsonArray
                ?.map { it.jsonPrimitive.int }
                ?.reversed()
                ?: emptyList()
            
            Result.success(builds)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 获取下载 URL
     */
    fun getDownloadUrl(serverType: ServerType, version: String, build: Int): String {
        val projectName = serverType.apiName
        return "$API_URL/projects/$projectName/versions/$version/builds/$build/downloads/$projectName-$version-$build.jar"
    }
    
    /**
     * 获取构建信息
     */
    suspend fun getBuildInfo(serverType: ServerType, version: String, build: Int): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_URL/projects/${serverType.apiName}/versions/$version/builds/$build"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MCServer/1.0")
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = Json.parseToJsonElement(response).jsonObject
            
            Result.success(json)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    private fun isStableVersion(version: String): Boolean {
        val lowerVersion = version.lowercase()
        return !lowerVersion.contains("rc") &&
               !lowerVersion.contains("pre") &&
               !lowerVersion.contains("snapshot") &&
               !lowerVersion.contains("alpha") &&
               !lowerVersion.contains("beta") &&
               !lowerVersion.contains("experimental")
    }
}

/**
 * Purpur API
 */
object PurpurAPI {
    private const val API_URL = "https://api.purpurmc.org/v2/purpur"
    
    suspend fun getVersions(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MCServer/1.0")
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = Json.parseToJsonElement(response).jsonObject
            
            val versions = json["versions"]?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?.filter { isStableVersion(it) }
                ?.reversed()
                ?: emptyList()
            
            Result.success(versions)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun getVersionBuilds(version: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_URL/$version"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MCServer/1.0")
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = Json.parseToJsonElement(response).jsonObject
            
            val builds = json["builds"]?.jsonObject
                ?.get("all")?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?.reversed()
                ?: emptyList()
            
            Result.success(builds)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    fun getDownloadUrl(version: String, build: String): String {
        return "$API_URL/$version/$build/download"
    }
    
    private fun isStableVersion(version: String): Boolean {
        val lowerVersion = version.lowercase()
        return !lowerVersion.contains("rc") &&
               !lowerVersion.contains("pre") &&
               !lowerVersion.contains("snapshot") &&
               !lowerVersion.contains("alpha") &&
               !lowerVersion.contains("beta") &&
               !lowerVersion.contains("experimental")
    }
}

/**
 * Leaves API
 */
object LeavesAPI {
    private const val API_URL = "https://api.leavesmc.org/v2/projects/leaves"
    
    suspend fun getVersions(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MCServer/1.0")
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = Json.parseToJsonElement(response).jsonObject
            
            val versions = json["versions"]?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?.filter { isStableVersion(it) }
                ?.reversed()
                ?: emptyList()
            
            Result.success(versions)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun getVersionBuilds(version: String): Result<List<Int>> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_URL/versions/$version"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MCServer/1.0")
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = Json.parseToJsonElement(response).jsonObject
            
            val builds = json["builds"]?.jsonArray
                ?.map { it.jsonPrimitive.int }
                ?.reversed()
                ?: emptyList()
            
            Result.success(builds)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    fun getDownloadUrl(version: String, build: Int): String {
        return "$API_URL/versions/$version/builds/$build/downloads/leaves-$version-$build.jar"
    }
    
    private fun isStableVersion(version: String): Boolean {
        val lowerVersion = version.lowercase()
        return !lowerVersion.contains("rc") &&
               !lowerVersion.contains("pre") &&
               !lowerVersion.contains("snapshot") &&
               !lowerVersion.contains("alpha") &&
               !lowerVersion.contains("beta") &&
               !lowerVersion.contains("experimental")
    }
}

/**
 * Spigot API - 使用 GetBukkit API
 */
object SpigotAPI {
    private const val API_URL = "https://download.getbukkit.org/spigot"
    
    suspend fun getVersions(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            // Spigot 没有官方 API，返回常见版本
            val versions = listOf(
                "1.21.1", "1.21", "1.20.6", "1.20.4", "1.20.2", "1.20.1", "1.20",
                "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
                "1.18.2", "1.18.1", "1.18",
                "1.17.1", "1.17",
                "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1"
            )
            Result.success(versions)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun getVersionBuilds(version: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            // Spigot 只有 latest 版本
            Result.success(listOf("latest"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    fun getDownloadUrl(version: String): String {
        return "$API_URL/spigot-$version.jar"
    }
}

/**
 * BungeeCord API
 */
object BungeeCordAPI {
    private const val API_URL = "https://ci.md-5.net/job/BungeeCord/lastSuccessfulBuild/artifact/bootstrap/target"
    
    suspend fun getVersions(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            // BungeeCord 只有一个版本
            Result.success(listOf("latest"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun getVersionBuilds(version: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            Result.success(listOf("latest"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    fun getDownloadUrl(): String {
        return "$API_URL/BungeeCord.jar"
    }
}
