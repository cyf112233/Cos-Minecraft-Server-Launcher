package byd.cxkcxkckx.mcserver.api

import byd.cxkcxkckx.mcserver.data.PaperFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

object PaperAPI {
    private const val GRAPHQL_URL = "https://fill.papermc.io/graphql"
    private const val API_URL = "https://api.papermc.io/v2"
    
    /**
     * 获取 Paper 版本家族列表（通过 GraphQL）
     */
    suspend fun getFamilies(): Result<List<PaperFamily>> = withContext(Dispatchers.IO) {
        try {
            val query = """{"query":"query { project(id: \"paper\") { families { id key } } }"}"""
            
            val connection = URL(GRAPHQL_URL).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/graphql-response+json, application/json")
                setRequestProperty("User-Agent", "MCServer/1.0")
            }
            
            connection.outputStream.use { it.write(query.toByteArray(Charsets.UTF_8)) }
            
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                println("GraphQL Error ($responseCode): $errorStream")
                return@withContext Result.failure(Exception("HTTP $responseCode: $errorStream"))
            }
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = Json.parseToJsonElement(response).jsonObject
            
            val families = json["data"]?.jsonObject
                ?.get("project")?.jsonObject
                ?.get("families")?.jsonArray
                ?.map { family ->
                    val obj = family.jsonObject
                    PaperFamily(
                        id = obj["id"]?.jsonPrimitive?.content ?: "",
                        key = obj["key"]?.jsonPrimitive?.content ?: ""
                    )
                } ?: emptyList()
            
            Result.success(families)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 获取指定版本的所有构建号
     */
    suspend fun getVersionBuilds(version: String): Result<List<Int>> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_URL/projects/paper/versions/$version"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MCServer/1.0")
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = Json.parseToJsonElement(response).jsonObject
            
            val builds = json["builds"]?.jsonArray
                ?.map { it.jsonPrimitive.int }
                ?: emptyList()
            
            Result.success(builds)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 获取指定版本的所有可用版本号
     */
    suspend fun getVersions(family: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_URL/projects/paper/version_group/$family"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MCServer/1.0")
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = Json.parseToJsonElement(response).jsonObject
            
            val versions = json["versions"]?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?: emptyList()
            
            Result.success(versions)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 获取下载 URL
     */
    fun getDownloadUrl(version: String, build: Int): String {
        return "$API_URL/projects/paper/versions/$version/builds/$build/downloads/paper-$version-$build.jar"
    }
    
    /**
     * 获取构建信息
     */
    suspend fun getBuildInfo(version: String, build: Int): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_URL/projects/paper/versions/$version/builds/$build"
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
}
