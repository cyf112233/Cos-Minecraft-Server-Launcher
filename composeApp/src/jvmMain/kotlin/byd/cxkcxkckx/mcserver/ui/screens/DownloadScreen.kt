package byd.cxkcxkckx.mcserver.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import byd.cxkcxkckx.mcserver.api.*
import byd.cxkcxkckx.mcserver.data.DownloadStatus
import byd.cxkcxkckx.mcserver.data.ServerType
import byd.cxkcxkckx.mcserver.utils.DownloadManager
import kotlinx.coroutines.launch

@Composable
fun DownloadScreen() {
    var selectedType by remember { mutableStateOf(ServerType.PAPER) }
    var versions by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedVersion by remember { mutableStateOf<String?>(null) }
    var builds by remember { mutableStateOf<List<Any>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDownloadManager by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val downloadTasks by DownloadManager.tasks.collectAsState()
    val activeCount = downloadTasks.count { 
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING 
    }
    
    // 当选择的服务器类型改变时，加载版本列表
    LaunchedEffect(selectedType) {
        isLoading = true
        errorMessage = null
        selectedVersion = null
        builds = emptyList()
        
        scope.launch {
            val result = when (selectedType) {
                ServerType.PAPER, ServerType.FOLIA, ServerType.VELOCITY, ServerType.WATERFALL -> {
                    PaperMCAPI.getVersions(selectedType)
                }
                ServerType.PURPUR -> {
                    PurpurAPI.getVersions()
                }
                ServerType.LEAVES -> {
                    LeavesAPI.getVersions()
                }
                ServerType.SPIGOT -> {
                    SpigotAPI.getVersions()
                }
                ServerType.BUNGEECORD -> {
                    BungeeCordAPI.getVersions()
                }
            }
            
            result.onSuccess {
                versions = it
                selectedVersion = it.firstOrNull()
            }.onFailure {
                errorMessage = "加载失败: ${it.message}"
                versions = emptyList()
            }
            
            isLoading = false
        }
    }
    
    // 当选择的版本改变时，加载构建列表
    LaunchedEffect(selectedVersion) {
        selectedVersion?.let { version ->
            isLoading = true
            scope.launch {
                val result = when (selectedType) {
                    ServerType.PAPER, ServerType.FOLIA, ServerType.VELOCITY, ServerType.WATERFALL -> {
                        PaperMCAPI.getVersionBuilds(selectedType, version).map { it.map { build -> build as Any } }
                    }
                    ServerType.PURPUR -> {
                        PurpurAPI.getVersionBuilds(version).map { it.map { build -> build as Any } }
                    }
                    ServerType.LEAVES -> {
                        LeavesAPI.getVersionBuilds(version).map { it.map { build -> build as Any } }
                    }
                    ServerType.SPIGOT -> {
                        SpigotAPI.getVersionBuilds(version).map { it.map { build -> build as Any } }
                    }
                    ServerType.BUNGEECORD -> {
                        BungeeCordAPI.getVersionBuilds(version).map { it.map { build -> build as Any } }
                    }
                }
                
                result.onSuccess {
                    builds = it
                }.onFailure {
                    errorMessage = "加载构建失败: ${it.message}"
                    builds = emptyList()
                }
                
                isLoading = false
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 左侧栏 - 服务器类型
            Card(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "服务器类型",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(ServerType.entries) { type ->
                            ServerTypeItem(
                                type = type,
                                isSelected = type == selectedType,
                                onClick = { selectedType = type }
                            )
                        }
                    }
                }
            }
            
            // 右侧 - 版本列表
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = selectedType.displayName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (versions.isNotEmpty()) {
                                Text(
                                    text = "${versions.size} 个版本",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (errorMessage != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "错误",
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                                Button(onClick = { selectedType = selectedType }) {
                                    Text("重试")
                                }
                            }
                        }
                    } else if (versions.isEmpty() && !isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无版本",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(versions) { version ->
                                VersionCard(
                                    serverType = selectedType,
                                    version = version,
                                    isSelected = version == selectedVersion,
                                    builds = if (version == selectedVersion) builds else emptyList(),
                                    isLoading = isLoading && version == selectedVersion,
                                    onSelect = { selectedVersion = version },
                                    onDownload = { build ->
                                        val downloadUrl = when (selectedType) {
                                            ServerType.PAPER, ServerType.FOLIA, ServerType.VELOCITY, ServerType.WATERFALL -> {
                                                PaperMCAPI.getDownloadUrl(selectedType, version, build as Int)
                                            }
                                            ServerType.PURPUR -> {
                                                PurpurAPI.getDownloadUrl(version, build as String)
                                            }
                                            ServerType.LEAVES -> {
                                                LeavesAPI.getDownloadUrl(version, build as Int)
                                            }
                                            ServerType.SPIGOT -> {
                                                SpigotAPI.getDownloadUrl(version)
                                            }
                                            ServerType.BUNGEECORD -> {
                                                BungeeCordAPI.getDownloadUrl()
                                            }
                                        }
                                        
                                        // 触发下载
                                        DownloadManager.addDownload(
                                            serverType = selectedType,
                                            version = version,
                                            build = build.toString(),
                                            downloadUrl = downloadUrl
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // 右下角浮动下载按钮
        if (downloadTasks.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showDownloadManager = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Box {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "下载管理器"
                    )
                    
                    if (activeCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 8.dp, y = (-8).dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = activeCount.toString(),
                                color = MaterialTheme.colorScheme.onError,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        // 下载管理器弹窗
        if (showDownloadManager) {
            DownloadManagerDialog(
                onDismiss = { showDownloadManager = false }
            )
        }
    }
}

@Composable
fun ServerTypeItem(
    type: ServerType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = type.displayName,
            modifier = Modifier.padding(12.dp),
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun VersionCard(
    serverType: ServerType,
    version: String,
    isSelected: Boolean,
    builds: List<Any>,
    isLoading: Boolean,
    onSelect: () -> Unit,
    onDownload: (Any) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = version,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelected && builds.isNotEmpty()) {
                        Text(
                            text = "${builds.size} 个构建",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    }
                }
            }
            
            AnimatedVisibility(visible = isSelected && builds.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    builds.take(10).forEach { build ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (build) {
                                    is Int -> "Build #$build"
                                    is String -> "Build $build"
                                    else -> "Build $build"
                                },
                                fontSize = 14.sp
                            )
                            
                            FilledTonalButton(
                                onClick = { onDownload(build) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("下载", fontSize = 12.sp)
                            }
                        }
                    }
                    
                    if (builds.size > 10) {
                        Text(
                            text = "显示最新 10 个构建，共 ${builds.size} 个",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadManagerDialog(
    onDismiss: () -> Unit
) {
    val tasks by DownloadManager.tasks.collectAsState()
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(600.dp)
                .heightIn(max = 500.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "下载管理器",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${tasks.size} 个任务",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { DownloadManager.clearCompleted() }) {
                            Text("清空已完成")
                        }
                        
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "关闭")
                        }
                    }
                }
                
                HorizontalDivider()
                
                // 任务列表
                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "空下载",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Text(
                                text = "暂无下载任务",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            DownloadTaskItem(task = task)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadTaskItem(task: byd.cxkcxkckx.mcserver.data.DownloadTask) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${task.serverType.displayName} ${task.version} - Build ${task.build}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when (task.status) {
                            DownloadStatus.PENDING -> "等待中..."
                            DownloadStatus.DOWNLOADING -> {
                                if (task.progress > 0f) {
                                    "下载中 ${(task.progress * 100).toInt()}%"
                                } else {
                                    "下载中..."
                                }
                            }
                            DownloadStatus.COMPLETED -> "已完成"
                            DownloadStatus.FAILED -> "失败: ${task.errorMessage}"
                            DownloadStatus.CANCELLED -> "已取消"
                        },
                        fontSize = 12.sp,
                        color = when (task.status) {
                            DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        }
                    )
                }
                
                IconButton(
                    onClick = { DownloadManager.removeTask(task.id) },
                    enabled = task.status != DownloadStatus.DOWNLOADING
                ) {
                    Icon(Icons.Default.Delete, "删除")
                }
            }
            
            // 只在有进度时显示进度条
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PENDING) {
                if (task.progress > 0f) {
                    // 有进度 - 显示确定进度条
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // 无进度 - 显示不确定进度条
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
