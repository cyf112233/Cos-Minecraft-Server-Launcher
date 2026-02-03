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
import byd.cxkcxkckx.mcserver.utils.AnsiColorParser
import byd.cxkcxkckx.mcserver.utils.ServerManager
import byd.cxkcxkckx.mcserver.utils.ServerRunner
import byd.cxkcxkckx.mcserver.utils.ServerState
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("首页", "下载", "设置", "更多")
    
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
                    0 -> ModernHomeScreen()
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
fun ModernHomeScreen() {
    var servers by remember { mutableStateOf<List<ServerInfo>>(emptyList()) }
    var selectedServer by remember { mutableStateOf<ServerInfo?>(null) }
    var showConfigScreen by remember { mutableStateOf(false) }
    var showEulaDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isStarting by remember { mutableStateOf(false) }
    var isStopping by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // 服务器状态和日志 - 根据选中的服务器实时切换
    val serverState by produceState(ServerState.STOPPED, selectedServer?.id) {
        val serverId = selectedServer?.id
        println("[MainScreen] produceState(serverState) started for: $serverId")
        if (serverId != null) {
            // Wait for ServerRunner to expose a StateFlow for this server (it may be started later)
            while (true) {
                val flow = ServerRunner.getServerState(serverId)
                if (flow != null) {
                    // Collect actual state updates
                    flow.collect { state ->
                        println("[MainScreen] server state emitted: $state for $serverId")
                        value = state
                        if (state == ServerState.RUNNING) {
                            isStarting = false
                        }
                        if (state == ServerState.STOPPED || state == ServerState.ERROR) {
                            isStarting = false
                            isStopping = false
                        }
                    }
                    // If collect returns (flow completed), break and retry waiting
                } else {
                    // No flow yet; show STOPPED and poll until available or cancelled
                    println("[MainScreen] no state flow for $serverId yet, polling...")
                    value = ServerState.STOPPED
                    isStarting = false
                    isStopping = false
                    kotlinx.coroutines.delay(500)
                    continue
                }
                // If we reach here, either flow.collect ended; loop to re-check
            }
        } else {
            println("[MainScreen] no server selected for serverState produceState; setting STOPPED")
            value = ServerState.STOPPED
            isStarting = false
            isStopping = false
        }
    }

    val serverLogs by produceState(emptyList<String>(), selectedServer?.id) {
        val serverId = selectedServer?.id
        println("[MainScreen] produceState(serverLogs) started for: $serverId")
        if (serverId != null) {
            // Wait for logs flow to appear (server might be started after selection)
            while (true) {
                val flow = ServerRunner.getServerLogs(serverId)
                if (flow != null) {
                    flow.collect { logs ->
                        println("[MainScreen] server logs emitted: ${'$'}{logs.size} lines for $serverId; latest='${'$'}{logs.takeLast(1).firstOrNull() ?: ""}'")
                        value = logs
                    }
                } else {
                    println("[MainScreen] no logs flow for $serverId yet, polling...")
                    value = emptyList()
                    kotlinx.coroutines.delay(500)
                    continue
                }
            }
        } else {
            println("[MainScreen] no server selected for serverLogs produceState; setting empty list")
            value = emptyList()
        }
    }

    // 监听服务器状态变化，自动重置UI状态
    LaunchedEffect(serverState) {
        println("[MainScreen] LaunchedEffect(serverState) triggered: $serverState for selected=${'$'}{selectedServer?.id}")
        when (serverState) {
            ServerState.RUNNING -> {
                isStarting = false
            }
            ServerState.STOPPED, ServerState.ERROR -> {
                isStarting = false
                isStopping = false
            }
            else -> {}
        }
    }

    // 加载服务器列表
    fun loadServers() {
        scope.launch {
            servers = ServerManager.scanServers()
            selectedServer = selectedServer?.let { current ->
                servers.find { it.id == current.id } ?: servers.firstOrNull()
            } ?: servers.firstOrNull()
        }
    }
    
    LaunchedEffect(Unit) {
        loadServers()
    }

    // 当选中服务器变化时，重置局部启动/停止标志，防止不同服务器之间状态污染
    LaunchedEffect(selectedServer?.id) {
        isStarting = false
        isStopping = false
    }
    
    // EULA 对话框
    if (showEulaDialog && selectedServer != null) {
        EulaDialog(
            onAccept = {
                scope.launch {
                    isStarting = true
                    val success = ServerRunner.acceptEula(selectedServer!!.path)
                    if (success) {
                        showEulaDialog = false
                        // 启动服务器
                        val result = ServerRunner.startServer(selectedServer!!)
                        result.onFailure { error ->
                            errorMessage = "启动失败: ${error.message}"
                            isStarting = false
                        }
                    } else {
                        errorMessage = "EULA 接受失败"
                        isStarting = false
                    }
                }
            },
            onDismiss = { 
                showEulaDialog = false
                isStarting = false
            }
        )
    }
    
    // 错误提示对话框
    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("错误") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { errorMessage = null }) {
                    Text("确定")
                }
            }
        )
    }
    
    if (showConfigScreen && selectedServer != null) {
        ServerConfigScreen(
            serverInfo = selectedServer!!,
            onBack = { showConfigScreen = false },
            onSave = { newConfig ->
                selectedServer = selectedServer!!.copy(config = newConfig)
                servers = servers.map {
                    if (it.id == selectedServer!!.id) selectedServer!! else it
                }
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
                        onServerSelected = { selectedServer = it },
                        onConfigClick = { showConfigScreen = true },
                        onRefresh = { loadServers() }
                    )
                    
                    ModernServerControl(
                        serverState = serverState,
                        selectedServer = selectedServer,
                        isStarting = isStarting,
                        isStopping = isStopping,
                        onStartClick = {
                            if (!isStarting && serverState == ServerState.STOPPED) {
                                scope.launch {
                                    selectedServer?.let { server ->
                                        isStarting = true
                                        val eulaAccepted = ServerRunner.checkEula(server.path)
                                        if (!eulaAccepted) {
                                            showEulaDialog = true
                                        } else {
                                            val result = ServerRunner.startServer(server)
                                            result.onFailure { error ->
                                                errorMessage = "启动失败: ${error.message}"
                                                isStarting = false
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onStopClick = {
                            if (!isStopping && serverState == ServerState.RUNNING) {
                                scope.launch {
                                    selectedServer?.let { server ->
                                        isStopping = true
                                        ServerRunner.stopServer(server.id)
                                    }
                                }
                            }
                        },
                        onRestartClick = {
                            if (!isStopping && !isStarting && serverState == ServerState.RUNNING) {
                                scope.launch {
                                    selectedServer?.let { server ->
                                        isStopping = true
                                        ServerRunner.stopServer(server.id)
                                        kotlinx.coroutines.delay(2000)
                                        isStopping = false
                                        isStarting = true
                                        val result = ServerRunner.startServer(server)
                                        result.onFailure { error ->
                                            errorMessage = "重启失败: ${error.message}"
                                            isStarting = false
                                        }
                                    }
                                }
                            }
                        }
                    )
                    
                    ModernStatsGrid()
                    
                    ModernConsolePanel(
                        logs = serverLogs,
                        serverRunning = serverState == ServerState.RUNNING,
                        onClearClick = { /* 清空由 ServerRunner 管理 */ },
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
                            onServerSelected = { selectedServer = it },
                            onConfigClick = { showConfigScreen = true },
                            onRefresh = { loadServers() }
                        )
                        
                        ModernServerControl(
                            serverState = serverState,
                            selectedServer = selectedServer,
                            isStarting = isStarting,
                            isStopping = isStopping,
                            onStartClick = {
                                if (!isStarting && serverState == ServerState.STOPPED) {
                                    scope.launch {
                                        selectedServer?.let { server ->
                                            isStarting = true
                                            val eulaAccepted = ServerRunner.checkEula(server.path)
                                            if (!eulaAccepted) {
                                                showEulaDialog = true
                                            } else {
                                                val result = ServerRunner.startServer(server)
                                                result.onFailure { error ->
                                                    errorMessage = "启动失败: ${error.message}"
                                                    isStarting = false
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            onStopClick = {
                                if (!isStopping && serverState == ServerState.RUNNING) {
                                    scope.launch {
                                        selectedServer?.let { server ->
                                            isStopping = true
                                            ServerRunner.stopServer(server.id)
                                        }
                                    }
                                }
                            },
                            onRestartClick = {
                                if (!isStopping && !isStarting && serverState == ServerState.RUNNING) {
                                    scope.launch {
                                        selectedServer?.let { server ->
                                            isStopping = true
                                            ServerRunner.stopServer(server.id)
                                            kotlinx.coroutines.delay(2000)
                                            isStopping = false
                                            isStarting = true
                                            val result = ServerRunner.startServer(server)
                                            result.onFailure { error ->
                                                errorMessage = "重启失败: ${error.message}"
                                                isStarting = false
                                            }
                                        }
                                    }
                                }
                            }
                        )
                        ModernStatsGrid()
                    }
                    
                    ModernConsolePanel(
                        logs = serverLogs,
                        serverRunning = serverState == ServerState.RUNNING,
                        onClearClick = { /* 清空由 ServerRunner 管理 */ },
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
                    text = "点击<同意>按钮即表示您已阅读并同意 Minecraft EULA 的所有条款。",
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
    onServerSelected: (ServerInfo) -> Unit,
    onConfigClick: () -> Unit,
    onRefresh: () -> Unit
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
                        // Debug log for refresh action
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
                        ServerListItem(
                            server = server,
                            isSelected = server == selectedServer,
                            isRunning = ServerRunner.isServerRunning(server.id),
                                onClick = {
                                // Debug log for server selection
                                println("[MainScreen] Server selected: " + server.name + " (" + server.id + ")")
                                onServerSelected(server)
                            },
                            onConfigClick = {
                                // Debug log for opening config for a server
                                println("[MainScreen] Open config for: " + server.name + " (" + server.id + ")")
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
                // 运行状态指示器
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
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onRestartClick: () -> Unit
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
                
                StatusIndicator(
                    state = when {
                        isStarting || isStopping -> ServerState.STARTING
                        else -> serverState
                    }
                )
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
                            Button(
                                onClick = onRestartClick,
                                enabled = !stopping && !starting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                )
                            ) {
                                Text(
                                    "重启服务器",
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
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                if (stopping) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onError,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("停止中...")
                                } else {
                                    Text(
                                        "停止服务器",
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
fun ModernStatsGrid() {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                label = "在线玩家",
                value = "0",
                subValue = "/ 20",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "TPS",
                value = "20.0",
                subValue = "",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                label = "内存",
                value = "0 MB",
                subValue = "/ 2 GB",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "运行时间",
                value = "00:00:00",
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
    onClearClick: () -> Unit,
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
                
                FilledTonalButton(
                    onClick = onClearClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("清空", fontSize = 13.sp)
                }
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
                        // Use itemsIndexed and index as key to guarantee uniqueness even when log text repeats
                        itemsIndexed(
                            items = logs,
                            key = { index, _ -> index }
                        ) { _, log ->
                            // 解析颜色代码并显示
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
            
            // 命令输入框
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
