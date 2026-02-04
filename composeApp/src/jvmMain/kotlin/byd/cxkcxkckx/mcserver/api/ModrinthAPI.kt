package byd.cxkcxkckx.mcserver.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

object ModrinthAPI {
    data class ModrinthProject(
        val id: String,
        val title: String,
        val description: String?,
        val iconUrl: String?,
        val projectType: String? = null
    )

    private const val API_BASE = "https://api.modrinth.com/v2"

    /**
     * Search projects on Modrinth by query. 
     * Now filters to ONLY show plugins (not mods, modpacks, etc.)
     */
    suspend fun searchProjects(query: String, limit: Int = 20, offset: Int = 0): Result<List<ModrinthProject>> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            // Add facets parameter to filter by project_type:plugin
            // Modrinth facets format: [[["project_type:plugin"]]]
            val facets = java.net.URLEncoder.encode("[[\"project_type:plugin\"]]", "UTF-8")
            val url = "$API_BASE/search?query=$encoded&limit=$limit&offset=$offset&facets=$facets"
            
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "MCServer/1.0")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                return@withContext Result.failure(Exception("HTTP $code: $err"))
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = Json.parseToJsonElement(text).jsonObject

            val hits = json["hits"]?.jsonArray ?: return@withContext Result.success(emptyList())

            val projects = hits.map { hit ->
                val obj = hit.jsonObject
                val id = runCatching { obj["project_id"]?.jsonPrimitive?.content ?: obj["id"]?.jsonPrimitive?.content ?: "" }.getOrDefault("")
                val title = runCatching { obj["title"]?.jsonPrimitive?.content ?: obj["slug"]?.jsonPrimitive?.content ?: "" }.getOrDefault("")
                val description = runCatching { obj["description"]?.jsonPrimitive?.content }.getOrNull()
                val icon = runCatching { obj["icon_url"]?.jsonPrimitive?.content ?: obj["icon"]?.jsonPrimitive?.content }.getOrNull()
                val projectType = runCatching { obj["project_type"]?.jsonPrimitive?.content }.getOrNull()

                ModrinthProject(
                    id = id,
                    title = title,
                    description = description,
                    iconUrl = icon,
                    projectType = projectType
                )
            }

            Result.success(projects)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    data class VersionInfo(
        val id: String,
        val name: String?,
        val gameVersions: List<String>,
        val loaders: List<String>,
        val files: List<String>,
        val dependencies: List<String>
    )

    /**
     * Get versions for a project (includes file URLs, game versions, loaders and dependencies)
     */
    suspend fun getProjectVersions(projectId: String): Result<List<VersionInfo>> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/project/$projectId/version"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "MCServer/1.0")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                return@withContext Result.failure(Exception("HTTP $code: $err"))
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = Json.parseToJsonElement(text).jsonArray

            val versions = arr.map { ver ->
                val obj = ver.jsonObject
                val id = runCatching { obj["id"]?.jsonPrimitive?.content ?: "" }.getOrDefault("")
                val name = runCatching { obj["name"]?.jsonPrimitive?.content }.getOrNull()
                val gameVersions = runCatching { obj["game_versions"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList() }.getOrDefault(emptyList())
                val loaders = runCatching { obj["loaders"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList() }.getOrDefault(emptyList())
                val files = runCatching { obj["files"]?.jsonArray?.map { it.jsonObject["url"]?.jsonPrimitive?.content ?: "" } ?: emptyList() }.getOrDefault(emptyList())
                val dependencies = runCatching { obj["dependencies"]?.jsonArray?.map { it.jsonObject["project_id"]?.jsonPrimitive?.content ?: it.jsonPrimitive.content } ?: emptyList() }.getOrDefault(emptyList())

                VersionInfo(
                    id = id,
                    name = name,
                    gameVersions = gameVersions,
                    loaders = loaders,
                    files = files.filter { it.isNotBlank() },
                    dependencies = dependencies.filter { it.isNotBlank() }
                )
            }

            Result.success(versions)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get latest file download URL for a project (by project id). Returns Result with URL string.
     */
    suspend fun getLatestDownloadUrl(projectId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // reuse getProjectVersions and return first file of the newest version
            val versionsRes = getProjectVersions(projectId)
            versionsRes.fold(onSuccess = { versions ->
                val firstWithFile = versions.firstOrNull { it.files.isNotEmpty() }
                if (firstWithFile != null) {
                    Result.success(firstWithFile.files[0])
                } else {
                    Result.failure(Exception("No downloadable file found for project $projectId"))
                }
            }, onFailure = { err ->
                Result.failure(err)
            })
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get basic project details (title, description, icon) from Modrinth
     */
    suspend fun getProjectDetails(projectId: String): Result<ModrinthProject> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/project/$projectId"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "MCServer/1.0")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                return@withContext Result.failure(Exception("HTTP $code: $err"))
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = Json.parseToJsonElement(text).jsonObject

            val id = runCatching { obj["project_id"]?.jsonPrimitive?.content ?: obj["id"]?.jsonPrimitive?.content ?: "" }.getOrDefault("")
            val title = runCatching { obj["title"]?.jsonPrimitive?.content ?: obj["slug"]?.jsonPrimitive?.content ?: "" }.getOrDefault("")
            val description = runCatching { obj["description"]?.jsonPrimitive?.content }.getOrNull()
            val icon = runCatching { obj["icon_url"]?.jsonPrimitive?.content ?: obj["icon"]?.jsonPrimitive?.content }.getOrNull()
            val projectType = runCatching { obj["project_type"]?.jsonPrimitive?.content }.getOrNull()

            Result.success(ModrinthProject(
                id = id, 
                title = title, 
                description = description, 
                iconUrl = icon,
                projectType = projectType
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}