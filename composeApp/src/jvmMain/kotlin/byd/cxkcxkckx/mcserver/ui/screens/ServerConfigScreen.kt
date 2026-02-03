package byd.cxkcxkckx.mcserver.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import byd.cxkcxkckx.mcserver.data.ServerConfig
import byd.cxkcxkckx.mcserver.data.ServerInfo
import byd.cxkcxkckx.mcserver.utils.ConfigManager
import byd.cxkcxkckx.mcserver.utils.JavaDetector
import byd.cxkcxkckx.mcserver.utils.JavaInstallation
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class ConfigSection {
    GENERAL,
    PLUGINS,
    CONFIG_FILES
}

@Composable
fun ServerConfigScreen(
    serverInfo: ServerInfo,
    onBack: () -> Unit,
    onSave: (ServerConfig) -> Unit
) {
    var currentSection by remember { mutableStateOf(ConfigSection.GENERAL) }
    var config by remember { mutableStateOf(serverInfo.config) }
    
    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧分类栏
        ConfigSidebar(
            currentSection = currentSection,
            onSectionChange = { currentSection = it }
        )
        
        // 右侧内容区域 - 添加动画切换
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = currentSection,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) + 
                    slideInHorizontally(
                        initialOffsetX = { if (targetState.ordinal > initialState.ordinal) 300 else -300 },
                        animationSpec = tween(300)
                    ) togetherWith
                    fadeOut(animationSpec = tween(300)) +
                    slideOutHorizontally(
                        targetOffsetX = { if (targetState.ordinal > initialState.ordinal) -300 else 300 },
                        animationSpec = tween(300)
                    )
                }
            ) { section ->
                when (section) {
                    ConfigSection.GENERAL -> GeneralConfigContent(
                        serverInfo = serverInfo,
                        config = config,
                        onConfigChange = { config = it },
                        onBack = onBack,
                        onSave = { onSave(config) }
                    )
                    ConfigSection.PLUGINS -> PluginsContent(
                        serverInfo = serverInfo,
                        onBack = onBack
                    )
                    ConfigSection.CONFIG_FILES -> ConfigFilesContent(
                        serverInfo = serverInfo,
                        onBack = onBack
                    )
                }
            }
        }
    }
}

@Composable
fun ConfigSidebar(
    currentSection: ConfigSection,
    onSectionChange: (ConfigSection) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "服务器配置",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            ConfigSidebarItem(
                icon = Icons.Default.Settings,
                label = "常规配置",
                selected = currentSection == ConfigSection.GENERAL,
                onClick = { onSectionChange(ConfigSection.GENERAL) }
            )
            
            ConfigSidebarItem(
                icon = Icons.Default.Extension,
                label = "插件列表",
                selected = currentSection == ConfigSection.PLUGINS,
                onClick = { onSectionChange(ConfigSection.PLUGINS) }
            )
            
            ConfigSidebarItem(
                icon = Icons.Default.Description,
                label = "配置文件",
                selected = currentSection == ConfigSection.CONFIG_FILES,
                onClick = { onSectionChange(ConfigSection.CONFIG_FILES) }
            )
        }
    }
}

@Composable
fun ConfigSidebarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    // 添加缩放动画
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
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
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标动画
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                }
            ) { isSelected ->
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            // 文本动画
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    fadeIn() + expandHorizontally() togetherWith fadeOut() + shrinkHorizontally()
                }
            ) { isSelected ->
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun GeneralConfigContent(
    serverInfo: ServerInfo,
    config: ServerConfig,
    onConfigChange: (ServerConfig) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    var showSaveSuccess by remember { mutableStateOf(false) }
    var showJavaDialog by remember { mutableStateOf(false) }
    var javaInstallations by remember { mutableStateOf<List<JavaInstallation>>(emptyList()) }
    var isDetectingJava by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 顶部标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Column {
                    Text(
                        text = serverInfo.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${serverInfo.type} ${serverInfo.version}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
            
            Button(
                onClick = {
                    ConfigManager.saveConfig(serverInfo.path, config)
                    showSaveSuccess = true
                    onSave()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "保存",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存配置")
            }
        }
        
        // 成功提示
        AnimatedVisibility(
            visible = showSaveSuccess,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "成功",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "配置已保存",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(3000)
                showSaveSuccess = false
            }
        }
        
        // 内存设置
        ConfigCard(
            title = "内存设置",
            icon = Icons.Default.Memory
        ) {
            // 最小内存滑动条
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最小内存",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${config.minMemory} MB",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Slider(
                    value = config.minMemory.toFloat(),
                    onValueChange = { 
                        val newValue = it.roundToInt()
                        if (newValue <= config.maxMemory) {
                            onConfigChange(config.copy(minMemory = newValue))
                        }
                    },
                    valueRange = 512f..16384f,
                    steps = 30,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("512 MB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text("16 GB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 最大内存滑动条
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最大内存",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${config.maxMemory} MB",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Slider(
                    value = config.maxMemory.toFloat(),
                    onValueChange = { 
                        val newValue = it.roundToInt()
                        if (newValue >= config.minMemory) {
                            onConfigChange(config.copy(maxMemory = newValue))
                        }
                    },
                    valueRange = 512f..16384f,
                    steps = 30,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("512 MB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text("16 GB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
        
        // Java 设置
        ConfigCard(
            title = "Java 设置",
            icon = Icons.Default.Code
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = config.javaPath,
                    onValueChange = { onConfigChange(config.copy(javaPath = it)) },
                    label = { Text("Java 路径") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("例如: C:\\Program Files\\Java\\jdk-17\\bin\\java.exe") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                showJavaDialog = true
                                isDetectingJava = true
                                scope.launch {
                                    javaInstallations = JavaDetector.detectJavaInstallations()
                                    isDetectingJava = false
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "自动检测"
                            )
                        }
                    }
                )
                
                Text(
                    text = "提示：可以手动输入路径，或点击右侧图标自动检测",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        
        // 启动选项
        ConfigCard(
            title = "启动选项",
            icon = Icons.Default.Rocket
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "启用图形界面 (GUI)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "关闭后服务器将以命令行模式运行",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = !config.noGui,
                        onCheckedChange = {
                            onConfigChange(config.copy(noGui = !it))
                        }
                    )
                }
                
                Divider()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "安全模式",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "启用后服务器将以安全模式启动",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = config.safeMode,
                        onCheckedChange = {
                            onConfigChange(config.copy(safeMode = it))
                        }
                    )
                }
                
                Divider()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "自动重启",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "服务器崩溃后自动重启",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = config.autoRestart,
                        onCheckedChange = {
                            onConfigChange(config.copy(autoRestart = it))
                        }
                    )
                }
            }
        }
        
        // 高级参数
        ConfigCard(
            title = "高级参数",
            icon = Icons.Default.Settings
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = config.customJvmArgs,
                    onValueChange = { onConfigChange(config.copy(customJvmArgs = it)) },
                    label = { Text("自定义 JVM 参数") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    placeholder = { Text("例如: -XX:+UseG1GC -XX:MaxGCPauseMillis=200") }
                )
                
                OutlinedTextField(
                    value = config.customServerArgs,
                    onValueChange = { onConfigChange(config.copy(customServerArgs = it)) },
                    label = { Text("自定义服务器参数") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    placeholder = { Text("例如: --port 25565 --online-mode true") }
                )
            }
        }
        
        // 命令预览
        ConfigCard(
            title = "启动命令预览",
            icon = Icons.Default.Terminal
        ) {
            val command = config.getCommandString("server.jar")
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = command,
                    modifier = Modifier.padding(16.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
    
    // Java 选择对话框
    if (showJavaDialog) {
        JavaSelectionDialog(
            javaInstallations = javaInstallations,
            isDetecting = isDetectingJava,
            currentPath = config.javaPath,
            onSelect = { installation ->
                onConfigChange(config.copy(javaPath = installation.path))
                showJavaDialog = false
            },
            onCustom = {
                // 这里可以打开文件选择器
                showJavaDialog = false
            },
            onDismiss = { showJavaDialog = false }
        )
    }
}

@Composable
fun JavaSelectionDialog(
    javaInstallations: List<JavaInstallation>,
    isDetecting: Boolean,
    currentPath: String,
    onSelect: (JavaInstallation) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择 Java 版本",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isDetecting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("正在检测 Java 安装...")
                    }
                } else if (javaInstallations.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "警告",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "未检测到 Java 安装，请手动指定路径",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else {
                    javaInstallations.forEach { installation ->
                        JavaInstallationItem(
                            installation = installation,
                            isSelected = installation.path == currentPath,
                            onClick = { onSelect(installation) }
                        )
                    }
                }
                
                // 自定义选项
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Surface(
                    onClick = onCustom,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "自定义",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "自定义路径...",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun JavaInstallationItem(
    installation: JavaInstallation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surface,
        border = if (isSelected) 
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "已选择",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = installation.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            Text(
                text = installation.path,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun PluginsContent(
    serverInfo: ServerInfo,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                text = "插件列表",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = "插件",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Text(
                    text = "插件管理功能",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "即将推出",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun ConfigFilesContent(
    serverInfo: ServerInfo,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                text = "配置文件编辑",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "配置文件",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Text(
                    text = "配置文件编辑功能",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "即将推出",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun ConfigCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            content()
        }
    }
}
