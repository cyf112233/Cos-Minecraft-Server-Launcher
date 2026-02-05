package byd.cxkcxkckx.mcserver.utils

import byd.cxkcxkckx.mcserver.data.ServerStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Centralized server state manager
 * Manages all server states, logs, and stats in a single source of truth
 */
object ServerStateManager {
    private const val DEBUG = true
    
    private val _serverStates = MutableStateFlow<Map<String, ServerStateInfo>>(emptyMap())
    val serverStates: StateFlow<Map<String, ServerStateInfo>> = _serverStates.asStateFlow()
    
    /**
     * Complete state information for a single server
     */
    data class ServerStateInfo(
        val state: ServerState = ServerState.STOPPED,
        val logs: List<String> = emptyList(),
        val stats: ServerStats = ServerStats(),
        val lastUpdate: Long = System.currentTimeMillis(),
        val isTransitioning: Boolean = false // Track if in STARTING/STOPPING state
    )
    
    private fun log(message: String) {
        if (DEBUG) {
            println("[ServerStateManager] $message")
        }
    }
    
    /**
     * Update server state atomically
     */
    fun updateState(serverId: String, newState: ServerState) {
        _serverStates.update { current ->
            val existing = current[serverId] ?: ServerStateInfo()
            val isTransitioning = newState == ServerState.STARTING || newState == ServerState.STOPPING
            
            // Prevent redundant updates
            if (existing.state == newState && existing.isTransitioning == isTransitioning) {
                log("State unchanged for $serverId: $newState")
                return@update current
            }
            
            log("State update: $serverId -> $newState (transitioning=$isTransitioning)")
            current + (serverId to existing.copy(
                state = newState,
                lastUpdate = System.currentTimeMillis(),
                isTransitioning = isTransitioning
            ))
        }
    }
    
    /**
     * Append logs atomically (keeps last 1000 lines)
     */
    fun appendLog(serverId: String, logLine: String) {
        _serverStates.update { current ->
            val existing = current[serverId] ?: ServerStateInfo()
            val newLogs = (existing.logs + logLine).takeLast(1000)
            
            current + (serverId to existing.copy(
                logs = newLogs,
                lastUpdate = System.currentTimeMillis()
            ))
        }
    }
    
    /**
     * Update stats atomically
     */
    fun updateStats(serverId: String, stats: ServerStats) {
        _serverStates.update { current ->
            val existing = current[serverId] ?: ServerStateInfo()
            
            // Only update if stats actually changed
            if (existing.stats == stats) {
                return@update current
            }
            
            current + (serverId to existing.copy(
                stats = stats,
                lastUpdate = System.currentTimeMillis()
            ))
        }
    }
    
    /**
     * Update stats partially (merge with existing)
     */
    fun updateStatsPartial(serverId: String, update: (ServerStats) -> ServerStats) {
        _serverStates.update { current ->
            val existing = current[serverId] ?: ServerStateInfo()
            val newStats = update(existing.stats)
            
            if (existing.stats == newStats) {
                return@update current
            }
            
            current + (serverId to existing.copy(
                stats = newStats,
                lastUpdate = System.currentTimeMillis()
            ))
        }
    }
    
    /**
     * Get current state for a server
     */
    fun getState(serverId: String): ServerState {
        return _serverStates.value[serverId]?.state ?: ServerState.STOPPED
    }
    
    /**
     * Get current state info for a server
     */
    fun getStateInfo(serverId: String): ServerStateInfo? {
        return _serverStates.value[serverId]
    }
    
    /**
     * Check if server is in transitioning state
     */
    fun isTransitioning(serverId: String): Boolean {
        return _serverStates.value[serverId]?.isTransitioning ?: false
    }
    
    /**
     * Remove server from state management (when server is deleted)
     */
    fun removeServer(serverId: String) {
        _serverStates.update { current ->
            log("Removing server: $serverId")
            current - serverId
        }
    }
    
    /**
     * Clear all logs for a server
     */
    fun clearLogs(serverId: String) {
        _serverStates.update { current ->
            val existing = current[serverId] ?: return@update current
            current + (serverId to existing.copy(
                logs = emptyList(),
                lastUpdate = System.currentTimeMillis()
            ))
        }
    }
    
    /**
     * Reset server to initial state (for cleanup)
     */
    fun resetServer(serverId: String) {
        _serverStates.update { current ->
            log("Resetting server: $serverId")
            current + (serverId to ServerStateInfo(
                state = ServerState.STOPPED,
                logs = emptyList(),
                stats = ServerStats(),
                lastUpdate = System.currentTimeMillis(),
                isTransitioning = false
            ))
        }
    }
    
    /**
     * Get all servers currently being tracked
     */
    fun getAllServerIds(): Set<String> {
        return _serverStates.value.keys
    }
    
    /**
     * Get count of servers in each state
     */
    fun getStateCounts(): Map<ServerState, Int> {
        return _serverStates.value.values
            .groupBy { it.state }
            .mapValues { it.value.size }
    }
    
    /**
     * Get system metrics (memory and CPU usage)
     */
    fun getSystemMetrics(): SystemMetrics {
        val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        
        // Try to get system-level metrics if available
        val (totalMemoryGB, usedMemoryGB, cpuUsage) = if (osBean is com.sun.management.OperatingSystemMXBean) {
            // Get total physical memory
            val totalPhysicalMemory = osBean.totalMemorySize
            val freePhysicalMemory = osBean.freeMemorySize
            val usedPhysicalMemory = totalPhysicalMemory - freePhysicalMemory
            
            // Get system CPU load (not just process)
            val systemCpuLoad = osBean.cpuLoad * 100.0
            
            Triple(
                totalPhysicalMemory.toDouble() / (1024 * 1024 * 1024),
                usedPhysicalMemory.toDouble() / (1024 * 1024 * 1024),
                systemCpuLoad.coerceIn(0.0, 100.0)
            )
        } else {
            // Fallback to runtime memory if OS bean not available
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            
            Triple(
                totalMemory.toDouble() / (1024 * 1024 * 1024),
                usedMemory.toDouble() / (1024 * 1024 * 1024),
                0.0
            )
        }
        
        // Running servers count
        val runningCount = _serverStates.value.values.count { 
            it.state == ServerState.RUNNING 
        }
        
        return SystemMetrics(
            systemMemoryUsedGB = usedMemoryGB,
            systemMemoryTotalGB = totalMemoryGB,
            systemCpuUsage = cpuUsage,
            runningServersCount = runningCount
        )
    }
    
    data class SystemMetrics(
        val systemMemoryUsedGB: Double,
        val systemMemoryTotalGB: Double,
        val systemCpuUsage: Double,
        val runningServersCount: Int
    )
}
