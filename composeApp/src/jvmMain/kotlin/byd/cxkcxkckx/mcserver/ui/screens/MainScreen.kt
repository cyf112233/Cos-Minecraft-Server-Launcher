package byd.cxkcxkckx.mcserver.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import byd.cxkcxkckx.mcserver.data.ServerInfo
import byd.cxkcxkckx.mcserver.data.ServerStats
import byd.cxkcxkckx.mcserver.utils.AnsiColorParser
import byd.cxkcxkckx.mcserver.utils.ServerManager
import byd.cxkcxkckx.mcserver.utils.ServerRunner
import byd.cxkcxkckx.mcserver.utils.ServerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("首页", "下载", "设置", "更多")
    
    // Hoist all state to MainScreen level so it survives tab switches
    var servers by remember { mutableStateOf<List<ServerInfo>>(emptyList()) }
    var selectedServer by remember { mutableStateOf<ServerInfo?>(null) }
    var showConfigScreen by remember { mutableStateOf(false) }
    var showEulaDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isStarting by remember { mutableStateOf(false) }
    var isStopping by remember { mutableStateOf(false) }
    
    // Hoist server state, stats and logs to MainScreen
    var serverState by remember { mutableStateOf(ServerState.STOPPED) }
    var serverLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var serverStats by remember { mutableStateOf(ServerStats()) }
    // 服务器列表状态刷新触发器
    var serverListRefreshTrigger by remember { mutableStateOf(0) }
    // 记录上一次巡检到的服务器状态，用于检测是否从 STARTING/STOPPING 跳出
    var prevServerStates by remember { mutableStateOf<Map<String, ServerState>>(emptyMap()) }
    // 记录上一次是否处于过渡状态（用于边沿检测）
    var prevHasTransitioning by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // Subscribe to ServerRunner flows for the selected server
    LaunchedEffect(selectedServer?.id) {
        val serverId = selectedServer?.id
        println("[MainScreen] LaunchedEffect for serverId=$serverId")
        // 切换服务器时触发一次列表刷新，确保状态/日志在 UI 中同步更新
        serverListRefreshTrigger++
            if (serverId != null) {
            // Reset state when switching servers
            serverState = ServerState.STOPPED
            serverLogs = emptyList()
                serverStats = ServerStats()
            isStarting = false
            isStopping = false
            
            val stateFlow = ServerRunner.getServerState(serverId)
            val logsFlow = ServerRunner.getServerLogs(serverId)
            
            // track previous state so we can detect transitions (e.g. STARTING -> RUNNING)
            var prevState: ServerState? = null

            val stateJob = stateFlow?.let { flow ->
                launch {
                    println("[MainScreen] collecting state for $serverId")
                    flow.collect { state ->
                        println("[MainScreen] state update for $serverId -> $state")
                        // 如果从 STARTING 变为 RUNNING：不要立即刷新，改为延迟 3 秒后刷新，
                        // 以避免瞬时状态波动导致 UI 频繁变化（用户要求的“跳出自动刷新”延迟行为）。
                        if (prevState == ServerState.STARTING && state == ServerState.RUNNING) {
                            println("[MainScreen] Detected STARTING -> RUNNING for $serverId, scheduling delayed (3s) list refresh")
                            launch {
                                delay(3000)
                                println("[MainScreen] Delayed (3s) refresh for $serverId")
                                serverListRefreshTrigger++
                            }
                        }
                        serverState = state
                        prevState = state
                        if (state == ServerState.RUNNING) {
                            isStarting = false
                        }
                        if (state == ServerState.STOPPED || state == ServerState.ERROR) {
                            isStarting = false
                            isStopping = false
                        }
                    }
                }
            }
            
            val logsJob = logsFlow?.let { flow ->
                launch {
                    println("[MainScreen] collecting logs for $serverId")
                    flow.collect { logs ->
                        println("[MainScreen] logs update for $serverId -> size=${logs.size}")
                        serverLogs = logs
                    }
                }
            }

            val statsJob = ServerRunner.getServerStats(serverId)?.let { flow ->
                launch {
                    println("[MainScreen] collecting stats for $serverId")
                    flow.collect { stats ->
                        println("[MainScreen] stats update for $serverId -> tps=${stats.tps} players=${stats.onlinePlayers}")
                        serverStats = stats
                    }
                }
            }
            
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                println("[MainScreen] Cancelling collectors for $serverId")
                stateJob?.cancel()
                logsJob?.cancel()
                statsJob?.cancel()
            }
        } else {
            println("[MainScreen] no server selected, resetting state")
            serverState = ServerState.STOPPED
            serverLogs = emptyList()
            isStarting = false
            isStopping = false
        }
    }

    // 定期检查服务器状态：每10秒巡检一次
    // 逻辑：
    // - 如果当前巡检检测到有服务器处于过渡状态（STARTING/STOPPING），则每次巡检都触发刷新
    // - 如果当前为非过渡状态但上次为过渡状态（离开过渡），则触发一次刷新并更新选中服务器信息
    // - 否则不触发额外刷新
    LaunchedEffect(servers) {
        while (true) {
            delay(10000) // 每10秒检查一次

            // 检查当前是否有服务器处于过渡状态
            val hasTransitioning = servers.any { server ->
                val state = ServerRunner.getServerState(server.id)?.value
                state == ServerState.STARTING || state == ServerState.STOPPING
            }

            if (hasTransitioning) {
                // 如果当前有处于过渡的服务器，每次巡检都刷新
                println("[MainScreen] 检测到过渡状态的服务器，触发服务器列表刷新")
                serverListRefreshTrigger++
            } else if (!hasTransitioning && prevHasTransitioning) {
                // 刚刚从过渡状态离开：先触发一次立即刷新，10 秒后再触发一次以确保状态稳定
                println("[MainScreen] 检测到离开过渡状态，触发一次立即刷新并在10秒后再次刷新以稳定更新")
                serverListRefreshTrigger++
                // 在后台计划一次延迟刷新（而不是立即双触发）
                launch {
                    delay(10000)
                    println("[MainScreen] 延迟刷新（10s）触发")
                    serverListRefreshTrigger++
                }
                selectedServer?.let { sel ->
                    val s = ServerRunner.getServerState(sel.id)?.value
                    if (s != null) {
                        println("[MainScreen] 刷新选中服务器状态: ${sel.id} -> $s")
                        serverState = s
                    }
                    val l = ServerRunner.getServerLogs(sel.id)?.value
                    if (l != null) {
                        println("[MainScreen] 刷新选中服务器日志: ${sel.id} -> size=${l.size}")
                        serverLogs = l
                    }
                }
            }

            // 更新历史标志和 prevServerStates（保留历史）
            prevHasTransitioning = hasTransitioning
            prevServerStates = servers.associate { server ->
                server.id to (ServerRunner.getServerState(server.id)?.value ?: ServerState.STOPPED)
            }
            // 继续循环；LaunchedEffect 在 servers 发生变化时会重启
        }
    }

    // 响应外部或手动触发的刷新（serverListRefreshTrigger）
    // 刷新时同时更新：服务器列表、选中服务器状态、选中服务器日志
    LaunchedEffect(serverListRefreshTrigger) {
        println("[MainScreen] Refresh trigger fired: $serverListRefreshTrigger")
        scope.launch {
            try {
                // 重新扫描服务器列表
                val newServers = ServerManager.scanServers()
                println("[MainScreen] Scanned servers: size=${newServers.size}")
                servers = newServers

                // 刷新选中服务器的状态与日志（如果存在）
                selectedServer?.let { sel ->
                    val s = ServerRunner.getServerState(sel.id)?.value
                    if (s != null) {
                        println("[MainScreen] Refreshed selected server state: ${sel.id} -> $s")
                        serverState = s
                    }
                    val l = ServerRunner.getServerLogs(sel.id)?.value
                    if (l != null) {
                        println("[MainScreen] Refreshed selected server logs: ${sel.id} -> size=${l.size}")
                        serverLogs = l
                    }
                    val stats = ServerRunner.getServerStats(sel.id)?.value
                    if (stats != null) {
                        println("[MainScreen] Refreshed selected server stats: ${sel.id} -> tps=${stats.tps}")
                        serverStats = stats
                    }
                }
            } catch (e: Exception) {
                println("[MainScreen] Error during refresh: ${e.message}")
            }
        }
    }

    
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedContent(
                    targetState = "CMSL",
                    transitionSpec = {
                        fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                    }
                ) { text ->
                    Text(
                        text = text,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.width(32.dp))
                
                tabs.forEachIndexed { index, title ->
                    NavigationTab(
                        title = title,
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) + 
                    slideInHorizontally(
                        initialOffsetX = { if (targetState > initialState) 300 else -300 },
                        animationSpec = tween(300)
                    ) togetherWith
                    fadeOut(animationSpec = tween(300)) +
                    slideOutHorizontally(
                        targetOffsetX = { if (targetState > initialState) -300 else 300 },
                        animationSpec = tween(300)
                    )
                }
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> ModernHomeScreen(
                        servers = servers,
                        onServersChange = { servers = it },
                        selectedServer = selectedServer,
                        onServerSelected = { selectedServer = it },
                        showConfigScreen = showConfigScreen,
                        onShowConfigScreen = { showConfigScreen = it },
                        showEulaDialog = showEulaDialog,
                        onShowEulaDialog = { showEulaDialog = it },
                        errorMessage = errorMessage,
                        onErrorMessage = { errorMessage = it },
                        isStarting = isStarting,
                        onIsStarting = { isStarting = it },
                        isStopping = isStopping,
                        onIsStopping = { isStopping = it },
                        serverStats = serverStats,
                    serverState = serverState,
                    serverLogs = serverLogs,
                    // 手动刷新现在会立即触发一次 refresh trigger，以保证 LaunchedEffect(serverListRefreshTrigger) 立刻响应。
                    // 同时在后台进行一次 IO 扫描并把结果写回 servers（以便用户尽快看到列表变化）。
                    onManualRefresh = {
                        // 立即触发一次 refresh（同步修改），保证所有依赖该 trigger 的副作用被唤醒
                        println("[MainScreen] onManualRefresh invoked - incrementing serverListRefreshTrigger")
                        serverListRefreshTrigger++

                        // 立刻刷新选中服务器的状态与日志（同步读取 StateFlow 的当前值），
                        // 以避免 UI 在后台扫描完成前仍显示过时的启动/停止标志。
                        selectedServer?.let { sel ->
                            val s = ServerRunner.getServerState(sel.id)?.value
                            if (s != null) {
                                println("[MainScreen] onManualRefresh: selected server state -> $s")
                                serverState = s
                                // 保持 isStarting/isStopping 与实际 state 同步
                                isStarting = (s == ServerState.STARTING)
                                isStopping = (s == ServerState.STOPPING)
                            }
                            val l = ServerRunner.getServerLogs(sel.id)?.value
                            if (l != null) {
                                println("[MainScreen] onManualRefresh: selected server logs size=${l.size}")
                                serverLogs = l
                            }
                        }

                        // 异步执行扫描并更新 servers（不再在完成时再改动 trigger）
                        scope.launch {
                            try {
                                val newServers = withContext(Dispatchers.IO) { ServerManager.scanServers() }
                                println("[MainScreen] Manual refresh scanned servers: size=${newServers.size}")
                                servers = newServers
                                // 保持或更新选中服务器
                                selectedServer = selectedServer?.let { current ->
                                    newServers.find { it.id == current.id } ?: newServers.firstOrNull()
                                } ?: newServers.firstOrNull()
                            } catch (e: Exception) {
                                println("[MainScreen] Error during manual refresh: ${e.message}")
                            }
                        }
                    },
                        serverListRefreshTrigger = serverListRefreshTrigger
                    )
                    1 -> DownloadScreen()
                    2 -> SettingsScreen()
                    3 -> MoreScreen()
                }
            }
        }
    }
}

@Composable
fun NavigationTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .padding(4.dp)
            .scale(scale)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) 
                MaterialTheme.colorScheme.onPrimaryContainer 
            else 
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun ModernHomeScreen(
    servers: List<ServerInfo>,
    onServersChange: (List<ServerInfo>) -> Unit,
    selectedServer: ServerInfo?,
    onServerSelected: (ServerInfo?) -> Unit,
    showConfigScreen: Boolean,
    onShowConfigScreen: (Boolean) -> Unit,
    showEulaDialog: Boolean,
    onShowEulaDialog: (Boolean) -> Unit,
    errorMessage: String?,
    onErrorMessage: (String?) -> Unit,
    isStarting: Boolean,
    onIsStarting: (Boolean) -> Unit,
    isStopping: Boolean,
    onIsStopping: (Boolean) -> Unit,
    serverStats: ServerStats,
    serverState: ServerState,
    serverLogs: List<String>,
    onManualRefresh: () -> Unit,
    serverListRefreshTrigger: Int
) {
    val scope = rememberCoroutineScope()
    
    // Load servers
    fun loadServers() {
        scope.launch {
            val newServers = ServerManager.scanServers()
            onServersChange(newServers)
            // Preserve or update selected server
            onServerSelected(selectedServer?.let { current ->
                newServers.find { it.id == current.id } ?: newServers.firstOrNull()
            } ?: newServers.firstOrNull())
        }
    }
    
    LaunchedEffect(Unit) {
        loadServers()
    }
    
    // EULA dialog
    if (showEulaDialog && selectedServer != null) {
        EulaDialog(
            onAccept = {
                scope.launch {
                    onIsStarting(true)
                    val success = ServerRunner.acceptEula(selectedServer.path)
                    if (success) {
                        onShowEulaDialog(false)
                        val result = ServerRunner.startServer(selectedServer)
                        result.onFailure { error ->
                            onErrorMessage("启动失败: ${error.message}")
                            onIsStarting(false)
                        }
                    } else {
                        onErrorMessage("EULA 接受失败")
                        onIsStarting(false)
                    }
                }
            },
            onDismiss = { 
                onShowEulaDialog(false)
                onIsStarting(false)
            }
        )
    }
    
    // Error dialog
    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { onErrorMessage(null) },
            title = { Text("错误") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { onErrorMessage(null) }) {
                    Text("确定")
                }
            }
        )
    }
    
    if (showConfigScreen && selectedServer != null) {
        ServerConfigScreen(
            serverInfo = selectedServer,
            onBack = { onShowConfigScreen(false) },
            onSave = { newConfig ->
                val updated = selectedServer.copy(config = newConfig)
                onServerSelected(updated)
                onServersChange(servers.map { if (it.id == updated.id) updated else it })
                loadServers()
            }
        )
    } else {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val isCompact = maxWidth < 900.dp
            
            if (isCompact) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ServerSelector(
                        servers = servers,
                        selectedServer = selectedServer,
                        onServerSelected = onServerSelected,
                        onConfigClick = { onShowConfigScreen(true) },
                        // 手动刷新已在 MainScreen 层实现为一个立即生效的操作，直接调用即可
                        onRefresh = { onManualRefresh() },
                        refreshTrigger = serverListRefreshTrigger
                    )
                    
                    ModernServerControl(
                        serverState = serverState,
                        selectedServer = selectedServer,
                        isStarting = isStarting,
                        isStopping = isStopping,
                        onManualRefresh = onManualRefresh,
                        onStartClick = {
                            if (!isStarting && serverState == ServerState.STOPPED) {
                                scope.launch {
                                    selectedServer?.let { server ->
                                        onIsStarting(true)
                                        val eulaAccepted = ServerRunner.checkEula(server.path)
                                        if (!eulaAccepted) {
                                            onShowEulaDialog(true)
                                        } else {
                                            val result = ServerRunner.startServer(server)
                                            result.onFailure { error ->
                                                onErrorMessage("启动失败: ${error.message}")
                                                onIsStarting(false)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onForceKillClick = {
                            scope.launch {
                                selectedServer?.let { server ->
                                    ServerRunner.forceKillServer(server.id)
                                    onIsStarting(false)
                                    onIsStopping(false)
                                }
                            }
                        },
                        onStopClick = {
                            if (!isStopping && serverState == ServerState.RUNNING) {
                                scope.launch {
                                    selectedServer?.let { server ->
                                        onIsStopping(true)
                                        ServerRunner.stopServer(server.id)
                                    }
                                }
                            }
                        }
                    )
                    
                    ModernStatsGrid(serverStats = serverStats)
                    
                    ModernConsolePanel(
                        logs = serverLogs,
                        serverRunning = serverState == ServerState.RUNNING,
                        onSendCommand = { command ->
                            scope.launch {
                                selectedServer?.let { server ->
                                    ServerRunner.sendCommand(server.id, command)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ServerSelector(
                            servers = servers,
                            selectedServer = selectedServer,
                            onServerSelected = onServerSelected,
                            onConfigClick = { onShowConfigScreen(true) },
                        onRefresh = { onManualRefresh() },
                            refreshTrigger = serverListRefreshTrigger
                        )
                        
                        ModernServerControl(
                            serverState = serverState,
                            selectedServer = selectedServer,
                            isStarting = isStarting,
                            isStopping = isStopping,
                            onManualRefresh = onManualRefresh,
                            onStartClick = {
                                if (!isStarting && serverState == ServerState.STOPPED) {
                                    scope.launch {
                                        selectedServer?.let { server ->
                                            onIsStarting(true)
                                            val eulaAccepted = ServerRunner.checkEula(server.path)
                                            if (!eulaAccepted) {
                                                onShowEulaDialog(true)
                                            } else {
                                                val result = ServerRunner.startServer(server)
                                                result.onFailure { error ->
                                                    onErrorMessage("启动失败: ${error.message}")
                                                    onIsStarting(false)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            onForceKillClick = {
                                scope.launch {
                                    selectedServer?.let { server ->
                                        ServerRunner.forceKillServer(server.id)
                                        onIsStarting(false)
                                        onIsStopping(false)
                                    }
                                }
                            },
                            onStopClick = {
                                if (!isStopping && serverState == ServerState.RUNNING) {
                                    scope.launch {
                                        selectedServer?.let { server ->
                                            onIsStopping(true)
                                            ServerRunner.stopServer(server.id)
                                        }
                                    }
                                }
                            }
                        )
                        ModernStatsGrid(serverStats = serverStats)
                    }
                    
                    ModernConsolePanel(
                        logs = serverLogs,
                        serverRunning = serverState == ServerState.RUNNING,
                        onSendCommand = { command ->
                            scope.launch {
                                selectedServer?.let { server ->
                                    ServerRunner.sendCommand(server.id, command)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
fun EulaDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "EULA",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Minecraft 最终用户许可协议",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "在启动服务器之前，您需要同意 Minecraft 最终用户许可协议（EULA）。",
                    fontSize = 14.sp
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "EULA 链接：",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "https://www.minecraft.net/zh-hans/eula",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Text(
                    text = "点击【同意】按钮即表示您已阅读并同意 Minecraft EULA 的所有条款。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("同意并启动")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
fun ServerSelector(
    servers: List<ServerInfo>,
    selectedServer: ServerInfo?,
    onServerSelected: (ServerInfo?) -> Unit,
    onConfigClick: () -> Unit,
    onRefresh: () -> Unit,
    refreshTrigger: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "服务器列表",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                IconButton(
                    onClick = {
                        println("[MainScreen] Refresh servers requested")
                        onRefresh()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            if (servers.isEmpty()) {
                Text(
                    text = "暂无服务器，请在 mcserver 文件夹中添加服务器",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    servers.forEach { server ->
                        // 使用 key 确保每个服务器项在 refreshTrigger 变化时重新组合
                        key(server.id, refreshTrigger) {
                            ServerListItem(
                                server = server,
                                isSelected = server == selectedServer,
                                isRunning = ServerRunner.isServerRunning(server.id),
                                onClick = {
                                    println("[MainScreen] Server selected: ${server.name} (${server.id})")
                                    onServerSelected(server)
                                },
                                onConfigClick = {
                                    println("[MainScreen] Open config for: ${server.name} (${server.id})")
                                    onServerSelected(server)
                                    onConfigClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServerListItem(
    server: ServerInfo,
    isSelected: Boolean,
    isRunning: Boolean,
    onClick: () -> Unit,
    onConfigClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else 
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRunning) 
                                MaterialTheme.colorScheme.primary
                            else if (isSelected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                        )
                )
                
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = server.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (isRunning) {
                            Text(
                                text = "运行中",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${server.type} ${server.version}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            
            IconButton(
                onClick = { onConfigClick() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "配置",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun ModernServerControl(
    serverState: ServerState,
    selectedServer: ServerInfo?,
    isStarting: Boolean,
    isStopping: Boolean,
    onManualRefresh: () -> Unit,
    onStartClick: () -> Unit,
    onForceKillClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "服务器状态",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    AnimatedContent(
                        targetState = when {
                            isStarting -> "启动中"
                            isStopping -> "停止中"
                            else -> when (serverState) {
                                ServerState.RUNNING -> "运行中"
                                ServerState.STOPPED -> "已停止"
                                ServerState.STARTING -> "启动中"
                                ServerState.STOPPING -> "停止中"
                                ServerState.ERROR -> "错误"
                            }
                        },
                        transitionSpec = {
                            fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                        }
                    ) { statusText ->
                        Text(
                            text = statusText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIndicator(
                        state = when {
                            isStarting || isStopping -> ServerState.STARTING
                            else -> serverState
                        }
                    )
                    IconButton(
                        onClick = { onManualRefresh() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新状态",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            AnimatedContent(
                targetState = Triple(serverState, isStarting, isStopping),
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) + 
                    expandVertically(animationSpec = tween(300)) togetherWith
                    fadeOut(animationSpec = tween(300)) +
                    shrinkVertically(animationSpec = tween(300))
                }
            ) { (state, starting, stopping) ->
                when {
                    starting || stopping -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = if (starting) "服务器启动中..." else "服务器停止中...",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // 强制关闭按钮
                            Button(
                                onClick = onForceKillClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "强制关闭",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "强制关闭",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    state == ServerState.STOPPED || state == ServerState.ERROR -> {
                        Button(
                            onClick = onStartClick,
                            enabled = selectedServer != null && !starting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (starting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("启动中...")
                            } else {
                                Text(
                                    if (selectedServer != null) "启动服务器" else "请先选择服务器",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    state == ServerState.RUNNING -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 强制关闭按钮
                            Button(
                                onClick = onForceKillClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "强制关闭",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "强制关闭",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            
                            Button(
                                onClick = onStopClick,
                                enabled = !stopping,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                if (stopping) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("停止中...")
                                } else {
                                    Text(
                                        "正常关闭",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    state == ServerState.STARTING || state == ServerState.STOPPING -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                when (state) {
                                    ServerState.STARTING -> "服务器启动中..."
                                    ServerState.STOPPING -> "服务器停止中..."
                                    else -> ""
                                },
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // 强制关闭按钮
                            Button(
                                onClick = onForceKillClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "强制关闭",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "强制关闭",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(state: ServerState) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == ServerState.RUNNING) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = Modifier
            .size(14.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                when (state) {
                    ServerState.RUNNING -> MaterialTheme.colorScheme.primary
                    ServerState.STOPPED -> MaterialTheme.colorScheme.error
                    ServerState.STARTING, ServerState.STOPPING -> MaterialTheme.colorScheme.tertiary
                    ServerState.ERROR -> MaterialTheme.colorScheme.error
                }
            )
    )
}

@Composable
fun ModernStatsGrid(serverStats: ServerStats) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                label = "在线玩家",
                value = serverStats.onlinePlayers.toString(),
                subValue = "/ ${serverStats.maxPlayers}",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "TPS",
                value = String.format("%.1f", serverStats.tps),
                subValue = "",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                label = "内存",
                value = "${serverStats.usedMemoryMB} MB",
                subValue = "/ ${serverStats.maxMemoryMB} MB",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "运行时间",
                value = serverStats.uptimeFormatted,
                subValue = "",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    subValue: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    slideInVertically { it } + fadeIn() togetherWith
                    slideOutVertically { -it } + fadeOut()
                }
            ) { animatedValue ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = animatedValue,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (subValue.isNotEmpty()) {
                        Text(
                            text = " $subValue",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernConsolePanel(
    logs: List<String>,
    serverRunning: Boolean,
    onSendCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var commandInput by remember { mutableStateOf("") }
    val defaultColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "控制台",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    AnimatedContent(
                        targetState = logs.size,
                        transitionSpec = {
                            slideInVertically { it } + fadeIn() togetherWith
                            slideOutVertically { -it } + fadeOut()
                        }
                    ) { count ->
                        Text(
                            text = "$count 条日志",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                
                // 清空按钮已移除；日志由 ServerRunner 管理清理
            }
            
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            
            val listState = rememberLazyListState()
            
            LaunchedEffect(logs.size) {
                if (logs.isNotEmpty()) {
                    listState.animateScrollToItem(logs.size - 1)
                }
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (logs.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "空日志",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "暂无日志",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "启动服务器后日志将显示在这里",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = logs,
                            key = { index, _ -> index }
                        ) { _, log ->
                            val coloredParts = remember(log) { AnsiColorParser.parse(log) }

                            Text(
                                text = buildAnnotatedString {
                                    coloredParts.forEach { part ->
                                        withStyle(
                                            style = SpanStyle(
                                                color = if (part.color != androidx.compose.ui.graphics.Color.Unspecified)
                                                    part.color
                                                else
                                                    defaultColor,
                                                fontWeight = if (part.bold) FontWeight.Bold else FontWeight.Normal,
                                                fontStyle = if (part.italic) FontStyle.Italic else FontStyle.Normal,
                                                textDecoration = if (part.underline) TextDecoration.Underline else null
                                            )
                                        ) {
                                            append(part.text)
                                        }
                                    }
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入命令...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    enabled = serverRunning
                )
                
                IconButton(
                    onClick = {
                        if (commandInput.isNotBlank()) {
                            onSendCommand(commandInput)
                            commandInput = ""
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    enabled = serverRunning && commandInput.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送"
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "即将推出",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun MoreScreen() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "更多",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "更多功能",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "即将推出",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}
