package byd.cxkcxkckx.mcserver.data

data class PaperFamily(
    val id: String,
    val key: String
)

data class PaperVersion(
    val version: String,
    val builds: List<Int>
)

data class PaperBuild(
    val build: Int,
    val time: String,
    val changes: List<String>,
    val downloads: Map<String, PaperDownload>
)

data class PaperDownload(
    val name: String,
    val sha256: String
)
