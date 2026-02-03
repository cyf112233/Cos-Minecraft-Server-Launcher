package byd.cxkcxkckx.mcserver.data

import java.util.UUID

data class ServerInfo(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val version: String = "Unknown",
    val type: String = "Unknown", // Paper, Spigot, Vanilla, etc.
    val config: ServerConfig = ServerConfig(serverId = id)
)
