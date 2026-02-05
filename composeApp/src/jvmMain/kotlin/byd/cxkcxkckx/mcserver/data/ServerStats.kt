package byd.cxkcxkckx.mcserver.data

/**
 * Server statistics data
 */
data class ServerStats(
    val onlinePlayers: Int = 0,
    val maxPlayers: Int = 20,
    val tps: Double = 20.0,
    val usedMemoryMB: Long = 0,
    val maxMemoryMB: Long = 0,
    val uptimeSeconds: Long = 0,
    val systemMemoryUsedMB: Long = 0,
    val systemMemoryTotalMB: Long = 0,
    val systemCpuUsage: Double = 0.0,
    val runningServersCount: Int = 0
) {
    val uptimeFormatted: String
        get() {
            val hours = uptimeSeconds / 3600
            val minutes = (uptimeSeconds % 3600) / 60
            val seconds = uptimeSeconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    
    val memoryUsagePercent: Float
        get() = if (maxMemoryMB > 0) (usedMemoryMB.toFloat() / maxMemoryMB) else 0f
    
    val systemMemoryUsagePercent: Float
        get() = if (systemMemoryTotalMB > 0) (systemMemoryUsedMB.toFloat() / systemMemoryTotalMB) else 0f
}
