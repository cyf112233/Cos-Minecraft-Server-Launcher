package byd.cxkcxkckx.mcserver.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import byd.cxkcxkckx.mcserver.data.ServerInfo
import byd.cxkcxkckx.mcserver.data.ServerStats
import byd.cxkcxkckx.mcserver.data.DownloadTask
import byd.cxkcxkckx.mcserver.data.DownloadStatus
import byd.cxkcxkckx.mcserver.utils.AnsiColorParser
import byd.cxkcxkckx.mcserver.utils.ServerManager
import byd.cxkcxkckx.mcserver.utils.ServerRunner
import byd.cxkcxkckx.mcserver.utils.ServerState
import byd.cxkcxkckx.mcserver.utils.ServerStateManager
import byd.cxkcxkckx.mcserver.utils.DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO
import org.jetbrains.skia.Image as SkiaImage

// Image loader with caching
object IconLoader {
    private val cache = mutableMapOf<String, ImageBitmap?>()
    
    suspend fun load(url: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        
        // Check cache first
        cache[url]?.let { return@withContext it }
        
        try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val bufferedImage = ImageIO.read(connection.getInputStream())
            
            if (bufferedImage != null) {
                val bitmap = bufferedImageToImageBitmap(bufferedImage)
                cache[url] = bitmap
                bitmap
            } else {
                cache[url] = null
                null
            }
        } catch (e: Exception) {
            println("Failed to load icon from $url: ${e.message}")
            cache[url] = null
            null
        }
    }
    
    private fun bufferedImageToImageBitmap(bufferedImage: BufferedImage): ImageBitmap {
        val width = bufferedImage.width
        val height = bufferedImage.height
        val pixels = IntArray(width * height)
        bufferedImage.getRGB(0, 0, width, height, pixels, 0, width)
        
        // Convert Int ARGB pixels -> ByteArray RGBA expected by Skia
        val bytes = ByteArray(width * height * 4)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            // pixel is ARGB (alpha in high byte)
            bytes[i * 4] = ((pixel shr 16) and 0xFF).toByte()     // R
            bytes[i * 4 + 1] = ((pixel shr 8) and 0xFF).toByte()  // G
            bytes[i * 4 + 2] = (pixel and 0xFF).toByte()          // B
            bytes[i * 4 + 3] = ((pixel shr 24) and 0xFF).toByte() // A
        }

        val skiaImage = SkiaImage.makeRaster(
            imageInfo = org.jetbrains.skia.ImageInfo.makeS32(width, height, org.jetbrains.skia.ColorAlphaType.UNPREMUL),
            bytes = bytes,
            rowBytes = width * 4
        )

        return skiaImage.asImageBitmap()
    }
}

@Composable
fun MainScreen() {
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("首页", "下载", "市场", "更多")
    
    // Hoist all state to MainScreen level so it survives tab switches
    var servers by remember { mutableStateOf<List<ServerInfo>>(emptyList()) }
    var selectedServer by remember { mutableStateOf<ServerInfo?>(null) }
    var showConfigScreen by remember { mutableStateOf(false) }
    var showEulaDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isStarting by remember { mutableStateOf(false) }
    var isStopping by remember { mutableStateOf(false) }
    
    // Use centralized state manager for all server states
    val allServerStates by ServerStateManager.serverStates.collectAsState()
    
    // Derive state for selected server
    val selectedServerState by remember {
        derivedStateOf {
            selectedServer?.id?.let { allServerStates[it] }
        }
    }
    
    var serverListRefreshTrigger by remember { mutableStateOf(0) }
    
    val scope = rememberCoroutineScope()
    
    // Track transitioning state for UI flags
    LaunchedEffect(selectedServer?.id, selectedServerState) {
        val state = selectedServerState?.state ?: ServerState.STOPPED
        when (state) {
            ServerState.STARTING -> {
                isStarting = true
                isStopping = false
            }
            ServerState.STOPPING -> {
                isStarting = false
                isStopping = true
            }
            ServerState.RUNNING -> {
                isStarting = false
                isStopping = false
            }
            ServerState.STOPPED, ServerState.ERROR -> {
                isStarting = false
                isStopping = false
            }
        }
    }

    // Unified periodic refresh - check every 5 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            
            // Check if any server is transitioning
            val hasTransitioning = allServerStates.values.any { it.isTransitioning }
            
            if (hasTransitioning) {
                println("[MainScreen] Transitioning servers detected, refreshing server list")
                serverListRefreshTrigger++
            }
        }
    }

    // Respond to manual refresh triggers
    LaunchedEffect(serverListRefreshTrigger) {
        println("[MainScreen] Refresh trigger fired: $serverListRefreshTrigger")
        scope.launch {
            try {
                val newServers = ServerManager.scanServers()
                println("[MainScreen] Scanned servers: size=${newServers.size}")
                servers = newServers

                // Preserve or update selected server
                selectedServer = selectedServer?.let { current ->
                    newServers.find { it.id == current.id } ?: newServers.firstOrNull()
                } ?: newServers.firstOrNull()
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
                        serverStateInfo = selectedServerState,
                        onManualRefresh = {
                            println("[MainScreen] onManualRefresh invoked")
                            serverListRefreshTrigger++
                        },
                        serverListRefreshTrigger = serverListRefreshTrigger
                    )
                    1 -> DownloadScreen()
                    2 -> MarketScreen(
                        servers = servers,
                        selectedServer = selectedServer,
                        onManualRefresh = { serverListRefreshTrigger++ }
                    )
                    3 -> MoreScreen()
                }
            }
        }
    }
}

@Composable
fun MarketScreen(
    servers: List<byd.cxkcxkckx.mcserver.data.ServerInfo>,
    selectedServer: byd.cxkcxkckx.mcserver.data.ServerInfo?,
    onManualRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var displayedProjects by remember { mutableStateOf<List<byd.cxkcxkckx.mcserver.api.ModrinthAPI.ModrinthProject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedProject by remember { mutableStateOf<byd.cxkcxkckx.mcserver.api.ModrinthAPI.ModrinthProject?>(null) }
    var selectedProjectVersions by remember { mutableStateOf<List<byd.cxkcxkckx.mcserver.api.ModrinthAPI.VersionInfo>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(0) }
    var canLoadMore by remember { mutableStateOf(true) }
    var showDownloadsDialog by remember { mutableStateOf(false) }
    
    // 下载任务状态（从 DownloadManager 获取）- 使用 tasks 而不是 downloadTasks
    val downloadTasks by DownloadManager.tasks.collectAsState()

    // Load recommended on first composition
    LaunchedEffect(Unit) {
        if (displayedProjects.isEmpty()) {
            scope.launch {
                try {
                    loading = true
                    val res = byd.cxkcxkckx.mcserver.api.ModrinthAPI.searchProjects("minecraft", offset = 0)
                    displayedProjects = res.getOrElse { emptyList() }
                    offset = displayedProjects.size
                    canLoadMore = displayedProjects.size >= 20
                } catch (e: Exception) {
                    error = e.message
                } finally {
                    loading = false
                }
            }
        }
    }

    fun loadMore() {
        if (!loading && canLoadMore) {
            scope.launch {
                try {
                    loading = true
                    val searchQuery = if (hasSearched && query.isNotBlank()) query else "minecraft"
                    val res = byd.cxkcxkckx.mcserver.api.ModrinthAPI.searchProjects(searchQuery, offset = offset)
                    val newProjects = res.getOrElse { emptyList() }
                    if (newProjects.isNotEmpty()) {
                        displayedProjects = displayedProjects + newProjects
                        offset += newProjects.size
                        canLoadMore = newProjects.size >= 20
                    } else {
                        canLoadMore = false
                    }
                } catch (e: Exception) {
                    error = e.message
                } finally {
                    loading = false
                }
            }
        }
    }

    // 下载任务弹窗
    if (showDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadsDialog = false },
            title = { Text("下载任务", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (downloadTasks.isEmpty()) {
                        Text(
                            "暂无下载任务",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        downloadTasks.forEach { task ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        task.fileName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    LinearProgressIndicator(
                                        progress = { task.progress },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${(task.progress * 100).toInt()}%",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = when (task.status) {
                                                DownloadStatus.PENDING -> "等待中"
                                                DownloadStatus.DOWNLOADING -> "下载中"
                                                DownloadStatus.COMPLETED -> "已完成"
                                                DownloadStatus.FAILED -> "失败"
                                                DownloadStatus.CANCELLED -> "已取消"
                                            },
                                            fontSize = 12.sp,
                                            color = when (task.status) {
                                                DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                                                DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadsDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // Show details panel if a project is selected
    if (selectedProject != null) {
        MarketProjectDetails(
            project = selectedProject!!,
            versions = selectedProjectVersions,
            servers = servers,
            onClose = { 
                selectedProject = null
                selectedProjectVersions = emptyList()
            },
            onOpenDependency = { depId ->
                scope.launch {
                    try {
                        val det = byd.cxkcxkckx.mcserver.api.ModrinthAPI.getProjectDetails(depId)
                        val vers = byd.cxkcxkckx.mcserver.api.ModrinthAPI.getProjectVersions(depId)
                        det.onSuccess { selectedProject = it }
                        vers.onSuccess { selectedProjectVersions = it }
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onInstall = { versionInfo, targetServerId ->
                scope.launch {
                    try {
                        val fileUrl = versionInfo.files.firstOrNull() ?: throw Exception("无可下载文件")
                        val server = servers.find { it.id == targetServerId } ?: throw Exception("未选择服务器")
                        val pluginsDir = java.io.File(server.path, "plugins")
                        if (!pluginsDir.exists()) pluginsDir.mkdirs()
                        val fileName = fileUrl.substringAfterLast('/')
                        val targetFile = java.io.File(pluginsDir, fileName)
                        
                        // 使用 addDownloadToPath 直接下载到指定路径，不受下载界面选择的核心影响
                        val taskId = DownloadManager.addDownloadToPath(
                            downloadUrl = fileUrl,
                            targetFilePath = targetFile.absolutePath
                        )
                        println("[市场] 已添加插件下载任务: $fileName (taskId=$taskId) -> ${targetFile.absolutePath}")
                        
                        // 显示下载任务弹窗
                        showDownloadsDialog = true
                        onManualRefresh()
                    } catch (e: Exception) {
                        error = "安装失败: ${e.message}"
                        e.printStackTrace()
                    }
                }
            }
        )
    } else {
        // Main market view with floating buttons
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
            // Search box at top (fixed height)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 2.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("搜索插件（例如: economy, jobs 等）") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    loading = true
                                    error = null
                                    hasSearched = true
                                    offset = 0
                                    val res = byd.cxkcxkckx.mcserver.api.ModrinthAPI.searchProjects(
                                        query.ifBlank { "minecraft" },
                                        offset = 0
                                    )
                                    displayedProjects = res.getOrElse { emptyList() }
                                    offset = displayedProjects.size
                                    canLoadMore = displayedProjects.size >= 20
                                } catch (e: Exception) {
                                    error = e.message
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("搜索")
                    }
                }
            }

                // Results area
                Box(modifier = Modifier.fillMaxSize()) {
                    val listState = rememberLazyListState()
                    
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (displayedProjects.isEmpty() && loading) {
                            items(3) {
                                ShimmerLoadingCard()
                            }
                        } else {
                            items(displayedProjects, key = { it.id }) { project ->
                                AnimatedPluginCard(
                                    project = project,
                                    onClick = {
                                        println("[MarketScreen] Card clicked: ${project.title}")
                                        selectedProject = project
                                        scope.launch {
                                            try {
                                                val det = byd.cxkcxkckx.mcserver.api.ModrinthAPI.getProjectDetails(project.id)
                                                val vers = byd.cxkcxkckx.mcserver.api.ModrinthAPI.getProjectVersions(project.id)
                                                det.onSuccess { selectedProject = it }
                                                vers.onSuccess { selectedProjectVersions = it }
                                            } catch (e: Exception) {
                                                error = e.message
                                            }
                                        }
                                    }
                                )
                            }
                            
                            if (canLoadMore) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (loading) {
                                            CircularProgressIndicator()
                                        } else {
                                            TextButton(onClick = { loadMore() }) {
                                                Text("加载更多")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Auto-load more when near bottom
                    LaunchedEffect(listState) {
                        snapshotFlow { listState.layoutInfo }
                            .collect { layoutInfo ->
                                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
                                if (lastVisible != null && lastVisible.index >= displayedProjects.size - 3) {
                                    loadMore()
                                }
                            }
                    }

                    error?.let { msg ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "错误: $msg",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
            
            // Floating buttons
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 返回顶部按钮
                val listState = rememberLazyListState()
                val showScrollToTop by remember {
                    derivedStateOf {
                        listState.firstVisibleItemIndex > 5
                    }
                }
                
                AnimatedVisibility(
                    visible = showScrollToTop,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "返回顶部"
                        )
                    }
                }
                
                // 下载任务按钮
                FloatingActionButton(
                    onClick = { showDownloadsDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "下载任务"
                        )
                        // 如果有活跃下载，显示徽章
                        val activeCount = downloadTasks.count { task ->
                            task.status == DownloadStatus.DOWNLOADING 
                        }
                        if (activeCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .align(Alignment.TopEnd)
                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeCount.toString(),
                                    color = MaterialTheme.colorScheme.onError,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Shimmer loading card for recommended section
@Composable
fun ShimmerLoadingCard() {
    val infiniteTransition = rememberInfiniteTransition()
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha))
                )
            }
        }
    }
}

// Animated plugin card with hover effect
@Composable
fun AnimatedPluginCard(
    project: byd.cxkcxkckx.mcserver.api.ModrinthAPI.ModrinthProject,
    onClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHovered = true
                        tryAwaitRelease()
                        isHovered = false
                    },
                    onTap = {
                        println("[AnimatedPluginCard] onTap called for ${project.title}")
                        onClick()
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHovered) 8.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with loading state
            val bmpState = remember { mutableStateOf<ImageBitmap?>(null) }
            var iconLoading by remember { mutableStateOf(true) }

            LaunchedEffect(project.iconUrl) {
                iconLoading = true
                bmpState.value = IconLoader.load(project.iconUrl)
                iconLoading = false
            }

            Box(modifier = Modifier.size(48.dp)) {
                val bmp = bmpState.value
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = project.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else if (!iconLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            project.title.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (iconLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    project.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    project.description ?: "无描述",
                    maxLines = 2,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            AnimatedVisibility(
                visible = isHovered,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MarketProjectDetails(
    project: byd.cxkcxkckx.mcserver.api.ModrinthAPI.ModrinthProject,
    versions: List<byd.cxkcxkckx.mcserver.api.ModrinthAPI.VersionInfo>,
    servers: List<ServerInfo>,
    onClose: () -> Unit,
    onOpenDependency: (String) -> Unit,
    onInstall: (byd.cxkcxkckx.mcserver.api.ModrinthAPI.VersionInfo, String) -> Unit
) {
    // Full-screen details view
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with back button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }

                    val bmpState = remember { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(project.iconUrl) {
                        bmpState.value = IconLoader.load(project.iconUrl)
                    }
                    val bmp = bmpState.value
                    if (bmp != null) {
                        Image(bitmap = bmp, contentDescription = project.title, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
                    } else {
                        Box(
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(project.title.take(1), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(project.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(project.description ?: "无描述", maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Server selector
                Text("安装到服务器", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (servers.isEmpty()) {
                    Text("没有可用服务器，请先在首页添加服务器", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    var selectedServerId by remember { mutableStateOf<String?>(servers.firstOrNull()?.id) }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        servers.forEach { s ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedServerId = s.id }
                                    .padding(8.dp)
                            ) {
                                RadioButton(selected = (selectedServerId == s.id), onClick = { selectedServerId = s.id })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(s.name)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Versions list
                    Text("可用版本", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (versions.isEmpty()) {
                        Text("暂无版本信息", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        versions.forEach { v ->
                            var expanded by remember { mutableStateOf(false) }
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { expanded = !expanded }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(v.name ?: v.id, fontWeight = FontWeight.SemiBold)
                                                // 加载器标签
                                                if (v.loaders.isNotEmpty()) {
                                                    v.loaders.forEach { loader ->
                                                        Text(
                                                            text = loader,
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier
                                                                .background(
                                                                    MaterialTheme.colorScheme.secondaryContainer,
                                                                    RoundedCornerShape(4.dp)
                                                                )
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text("游戏版本: ${v.gameVersions.joinToString(", ")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Button(onClick = {
                                            if (selectedServerId != null) onInstall(v, selectedServerId!!)
                                        }) {
                                            Text("安装")
                                        }
                                    }

                                    AnimatedVisibility(visible = expanded) {
                                        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (v.loaders.isNotEmpty()) {
                                                Text("加载器: ${v.loaders.joinToString(", ")}", fontSize = 12.sp)
                                            }
                                            if (v.files.isNotEmpty()) {
                                                Text("文件:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                v.files.forEach { f ->
                                                    Text(f, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                                                }
                                            }
                                            if (v.dependencies.isNotEmpty()) {
                                                Text("依赖:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 8.dp)) {
                                                    v.dependencies.forEach { dep ->
                                                        TextButton(onClick = { onOpenDependency(dep) }) {
                                                            Text(dep, fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
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
    serverStateInfo: ServerStateManager.ServerStateInfo?,
    onManualRefresh: () -> Unit,
    serverListRefreshTrigger: Int
) {
    // Extract values from state info
    val serverState = serverStateInfo?.state ?: ServerState.STOPPED
    val serverLogs = serverStateInfo?.logs ?: emptyList()
    val serverStats = serverStateInfo?.stats ?: ServerStats()
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
                                    
                                    // 启动持续检查机制，每10秒检查一次直到进程真正消失
                                    launch {
                                        var checkCount = 0
                                        while (checkCount < 12) { // 最多检查2分钟
                                            delay(10000) // 每10秒检查一次
                                            checkCount++
                                            
                                            val isStillRunning = ServerRunner.isServerRunning(server.id)
                                            println("[MainScreen] 强制关闭后检查 #$checkCount: ${server.name} 运行状态=$isStillRunning")
                                            
                                            onManualRefresh()
                                            
                                            if (!isStillRunning) {
                                                println("[MainScreen] 进程已确认消失，停止检查")
                                                break
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
                                        onIsStopping(true)
                                        ServerRunner.stopServer(server.id)
                                        
                                        // 启动持续检查机制，每10秒检查一次直到进程真正消失
                                        launch {
                                            var checkCount = 0
                                            while (checkCount < 12) { // 最多检查2分钟
                                                delay(10000) // 每10秒检查一次
                                                checkCount++
                                                
                                                val isStillRunning = ServerRunner.isServerRunning(server.id)
                                                println("[MainScreen] 正常关闭后检查 #$checkCount: ${server.name} 运行状态=$isStillRunning")
                                                
                                                onManualRefresh()
                                                
                                                if (!isStillRunning) {
                                                    println("[MainScreen] 进程已确认消失，停止检查")
                                                    break
                                                }
                                            }
                                        }
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
                                        
                                        // 启动持续检查机制，每10秒检查一次直到进程真正消失
                                        launch {
                                            var checkCount = 0
                                            while (checkCount < 12) { // 最多检查2分钟
                                                delay(10000) // 每10秒检查一次
                                                checkCount++
                                                
                                                val isStillRunning = ServerRunner.isServerRunning(server.id)
                                                println("[MainScreen] 强制关闭后检查 #$checkCount: ${server.name} 运行状态=$isStillRunning")
                                                
                                                onManualRefresh()
                                                
                                                if (!isStillRunning) {
                                                    println("[MainScreen] 进程已确认消失，停止检查")
                                                    break
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
                                            onIsStopping(true)
                                            ServerRunner.stopServer(server.id)
                                            
                                            // 启动持续检查机制，每10秒检查一次直到进程真正消失
                                            launch {
                                                var checkCount = 0
                                                while (checkCount < 12) { // 最多检查2分钟
                                                    delay(10000) // 每10秒检查一次
                                                    checkCount++
                                                    
                                                    val isStillRunning = ServerRunner.isServerRunning(server.id)
                                                    println("[MainScreen] 正常关闭后检查 #$checkCount: ${server.name} 运行状态=$isStillRunning")
                                                    
                                                    onManualRefresh()
                                                    
                                                    if (!isStillRunning) {
                                                        println("[MainScreen] 进程已确认消失，停止检查")
                                                        break
                                                    }
                                                }
                                            }
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "暂无服务器，请通过下载界面添加服务器",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    servers.forEach { server ->
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