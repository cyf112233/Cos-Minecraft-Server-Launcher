# 待应用的修改清单

## 问题概述
1. **Java 路径空格问题**：当 Java 路径包含空格时（如 `C:\Program Files\Java\...`），启动命令会失败
2. **中文编码问题**：在控制台输入中文命令时显示为问号
3. **服务器列表状态刷新**：切换服务器时，列表中的运行状态不会实时更新

## 修改 1: ServerConfig.kt - 修复 Java 路径空格问题

**文件路径**: `composeApp/src/jvmMain/kotlin/byd/cxkcxkckx/mcserver/data/ServerConfig.kt`

**修改位置**: `generateStartCommand` 函数的第 24-26 行

**原代码**:
```kotlin
// Java 路径
command.add(javaPath)
```

**修改为**:
```kotlin
// Java 路径 - 如果包含空格需要用引号包裹
if (javaPath.contains(" ")) {
    command.add("\"$javaPath\"")
} else {
    command.add(javaPath)
}
```

---

## 修改 2: ServerRunner.kt - 添加中文编码支持

**文件路径**: `composeApp/src/jvmMain/kotlin/byd/cxkcxkckx/mcserver/utils/ServerRunner.kt`

### 2.1 添加导入
在文件顶部的 import 部分（第 10 行后）添加：
```kotlin
import java.nio.charset.Charset
```

### 2.2 添加系统编码检测
在 `ServerRunner` object 内部，`runningServers` 定义之后（第 38 行后）添加：
```kotlin
// 自动检测系统编码
private val systemCharset: Charset = run {
    val osName = System.getProperty("os.name").lowercase()
    when {
        osName.contains("windows") -> {
            // Windows 系统使用 GBK 编码
            try {
                Charset.forName("GBK")
            } catch (e: Exception) {
                Charsets.UTF_8
            }
        }
        else -> Charsets.UTF_8
    }
}
```

### 2.3 修改 startServer 函数
在 `startServer` 函数中进行以下修改：

**位置 1**: 在函数开始处（第 96 行后）添加日志：
```kotlin
log("Starting server: ${serverInfo.name} (${serverInfo.id})")
log("System charset: ${systemCharset.name()}")  // 添加这行
```

**位置 2**: 在创建 ProcessBuilder 之后（第 128 行后），添加环境变量设置：
```kotlin
val processBuilder = ProcessBuilder(command)
    .directory(serverDir)
    .redirectErrorStream(true)

// 添加以下代码块：
// 设置环境变量以支持 UTF-8
val env = processBuilder.environment()
val osName = System.getProperty("os.name").lowercase()
if (osName.contains("windows")) {
    // Windows 系统设置
    env["JAVA_TOOL_OPTIONS"] = "-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
} else {
    // Linux/macOS 系统设置
    env["LANG"] = "zh_CN.UTF-8"
    env["LC_ALL"] = "zh_CN.UTF-8"
}

log("Starting process...")
```

**位置 3**: 修改 I/O 流创建（第 140-142 行）：
```kotlin
// 原代码：
val outputReader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
val inputWriter = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
log("I/O streams created")

// 修改为：
// Create input/output streams with proper charset
// 输出流（读取服务器日志）使用系统编码
val outputReader = BufferedReader(InputStreamReader(process.inputStream, systemCharset))
// 输入流（发送命令）也使用系统编码以确保兼容性
val inputWriter = BufferedWriter(OutputStreamWriter(process.outputStream, systemCharset))
log("I/O streams created with charset: ${systemCharset.name()}")
```

### 2.4 修改 sendCommand 函数
在 `sendCommand` 函数中（第 272 行附近）将日志改为：
```kotlin
serverProcess.inputWriter.write(command)
serverProcess.inputWriter.newLine()
serverProcess.inputWriter.flush()
log("Command sent successfully with charset: ${systemCharset.name()}")
```

### 2.5 修改 startLogReader 函数
在 `startLogReader` 函数中（第 359 行附近）将线程启动日志改为：
```kotlin
log("Log reader thread started with charset: ${systemCharset.name()}")
```

---

## 修改 3: MainScreen.kt - 添加服务器列表状态刷新

**文件路径**: `composeApp/src/jvmMain/kotlin/byd/cxkcxkckx/mcserver/ui/screens/MainScreen.kt`

### 3.1 添加导入
在文件顶部的 import 部分添加：
```kotlin
import kotlinx.coroutines.delay
```

### 3.2 在 MainScreen 函数中添加刷新触发器
在 `MainScreen` 函数内部，状态变量定义区域（约第 20 行后）添加：
```kotlin
// 服务器列表状态刷新触发器
var serverListRefreshTrigger by remember { mutableStateOf(0) }
```

### 3.3 添加定期刷新逻辑
在 `MainScreen` 函数中，`LaunchedEffect(selectedServer?.id)` 之前添加：
```kotlin
// 定期检查服务器状态：每10秒巡检一次
// 如果检测到有服务器处于 STARTING 或 STOPPING，则持续触发刷新（每次巡检触发）
// 如果检测到没有任何服务器处于过渡状态，则先触发一次刷新，然后退出巡检循环
LaunchedEffect(servers) {
    while (true) {
        delay(10000) // 每10秒检查一次

        // 检查当前是否有服务器处于过渡状态
        val hasTransitioning = servers.any { server ->
            val state = ServerRunner.getServerState(server.id)?.value
            state == ServerState.STARTING || state == ServerState.STOPPING
        }

        if (hasTransitioning) {
            println("[MainScreen] 检测到过渡状态的服务器，触发服务器列表刷新")
            serverListRefreshTrigger++
            // 继续巡检
            prevServerStates = servers.associate { server ->
                server.id to (ServerRunner.getServerState(server.id)?.value ?: ServerState.STOPPED)
            }
            continue
        } else {
            // 没有过渡状态：先做一次刷新，然后退出循环
            println("[MainScreen] 未检测到过渡状态的服务器，执行一次刷新后退出巡检")
            serverListRefreshTrigger++
            // 更新选中服务器的状态/日志一次以确保 UI 为最新
            selectedServer?.let { sel ->
                val s = ServerRunner.getServerState(sel.id)?.value
                if (s != null) {
                    println("[MainScreen] 刷新选中服务器状态: ${sel.id} -> $s")
                }
                val l = ServerRunner.getServerLogs(sel.id)?.value
                if (l != null) {
                    println("[MainScreen] 刷新选中服务器日志: ${sel.id} -> size=${l.size}")
                }
            }
            break
        }
    }
}
```

### 3.4 修改 ModernHomeScreen 函数签名
在 `ModernHomeScreen` 函数的参数列表最后添加：
```kotlin
serverListRefreshTrigger: Int  // 添加这个参数
```

### 3.5 在 MainScreen 中传递 refreshTrigger
在调用 `ModernHomeScreen` 时，添加参数：
```kotlin
serverListRefreshTrigger = serverListRefreshTrigger  // 添加这行
```

### 3.6 修改 ServerSelector 函数签名
在 `ServerSelector` 函数的参数列表最后添加：
```kotlin
refreshTrigger: Int  // 添加这个参数
```

### 3.7 在 ModernHomeScreen 中传递 refreshTrigger
在调用 `ServerSelector` 时，添加参数：
```kotlin
refreshTrigger = serverListRefreshTrigger  // 添加这行
```

### 3.8 修改 ServerSelector 中的服务器列表渲染
在 `ServerSelector` 函数中，服务器列表的 `forEach` 循环（约第 180 行）修改为：
```kotlin
servers.forEach { server ->
    // 使用 key 确保每个服务器项在 refreshTrigger 变化时重新组合
    key(server.id, refreshTrigger) {
        ServerListItem(
            server = server,
            isSelected = server == selectedServer,
            isRunning = ServerRunner.isServerRunning(server.id),
            onClick = { ... },
            onConfigClick = { ... }
        )
    }
}
```

---

## 验证修改

修改完成后，请测试以下功能：

1. **Java 路径空格**：
   - 设置 Java 路径为包含空格的路径（如 `C:\Program Files\Java\jdk-17\bin\java.exe`）
   - 启动服务器，应该能正常启动

2. **中文编码**：
   - 启动服务器后，在控制台输入中文命令（如 `say 你好`）
   - 应该能在日志中看到正确的中文，而不是问号

3. **服务器列表刷新**：
   - 启动服务器 A
   - 切换到服务器 B
   - 服务器 A 应该显示"运行中"标签和绿色圆点
   - 每10秒自动刷新状态

---

## 注意事项

1. 所有修改都是向后兼容的，不会影响现有功能
2. 编码检测是自动的，Windows 使用 GBK，其他系统使用 UTF-8
3. 服务器列表刷新只在有服务器处于过渡状态时触发，不会影响性能
4. 修改后建议重新编译项目：`./gradlew build`

---

## 如果遇到问题

如果修改后出现编译错误：
1. 检查导入语句是否正确添加
2. 检查函数签名是否匹配
3. 检查大括号和缩进是否正确
4. 使用 IDE 的"格式化代码"功能

如果运行时出现问题：
1. 查看控制台日志，搜索 `[ServerRunner]` 和 `[MainScreen]` 前缀
2. 确认编码设置是否生效（日志会显示使用的 charset）
3. 测试简单的英文命令是否正常工作
