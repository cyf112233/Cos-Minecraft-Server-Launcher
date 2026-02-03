package byd.cxkcxkckx.mcserver.data

enum class ServerType(val displayName: String, val apiName: String) {
    PAPER("Paper", "paper"),
    PURPUR("Purpur", "purpur"),
    LEAVES("Leaves", "leaves"),
    SPIGOT("Spigot", "spigot"),
    FOLIA("Folia", "folia"),
    BUNGEECORD("BungeeCord", "bungeecord"),
    VELOCITY("Velocity", "velocity"),
    WATERFALL("Waterfall", "waterfall")
}

data class ServerVersion(
    val version: String,
    val builds: List<Int> = emptyList()
)

data class DownloadTask(
    val id: String,
    val serverType: ServerType,
    val version: String,
    val build: String,
    val fileName: String,
    val downloadUrl: String,
    val targetPath: String,
    var progress: Float = 0f,
    var status: DownloadStatus = DownloadStatus.PENDING,
    var errorMessage: String? = null
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}
