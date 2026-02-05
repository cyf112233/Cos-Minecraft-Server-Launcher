package byd.cxkcxkckx.mcserver

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import byd.cxkcxkckx.mcserver.ui.theme.AppTheme
import byd.cxkcxkckx.mcserver.ui.screens.MainScreen
import byd.cxkcxkckx.mcserver.utils.PathManager

@Composable
fun App() {
    // 检查是否需要迁移旧数据
    var showMigrationDialog by remember { mutableStateOf(false) }
    var oldDataPath by remember { mutableStateOf("") }
    var newDataPath by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        if (PathManager.checkAndMigrateOldData()) {
            oldDataPath = PathManager.getOldProgramDirectory().absolutePath
            newDataPath = PathManager.getServersDirectory().absolutePath
            showMigrationDialog = true
        }
    }
    
    // 数据迁移提示对话框
    if (showMigrationDialog) {
        AlertDialog(
            onDismissRequest = { showMigrationDialog = false },
            title = { Text("数据存储位置已变更") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("为了防止更新时丢失服务器数据，本程序现在将数据保存在用户目录下。")
                    Text("检测到旧的程序目录下有数据，请手动迁移：")
                    Text("旧位置: $oldDataPath", style = MaterialTheme.typography.bodySmall)
                    Text("新位置: $newDataPath", style = MaterialTheme.typography.bodySmall)
                    Text("请将旧位置的 mcserver 文件夹复制到新位置，或者重新下载服务器。")
                }
            },
            confirmButton = {
                Button(onClick = { showMigrationDialog = false }) {
                    Text("我知道了")
                }
            }
        )
    }
    
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainScreen()
        }
    }
}
