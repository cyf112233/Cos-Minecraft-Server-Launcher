# 构建说明 (Build Instructions)

## 项目概述
Cos Minecraft Server Launcher (CMSL) - 使用 Compose Multiplatform 构建的现代化 Minecraft 服务器启动器

## 环境要求

### 必需
- JDK 17 或更高版本
- Gradle 8.0+ (使用 Gradle Wrapper，无需手动安装)

### 可选
- IntelliJ IDEA 2024.1+ (推荐用于开发)
- Android Studio (如果需要 Android 支持)

## 开发运行

### 运行应用
```bash
# Windows
gradlew.bat run

# macOS/Linux
./gradlew run
```

### 热重载开发模式
```bash
# Windows
gradlew.bat composeHotRun

# macOS/Linux
./gradlew composeHotRun
```

## 构建发行版

### Windows (MSI 安装包)
```bash
# Windows
gradlew.bat packageMsi

# macOS/Linux
./gradlew packageMsi
```

生成的文件位置：
- `composeApp/build/compose/binaries/main/msi/`
- 文件名：`Cos Minecraft Server Launcher-1.0.0.msi`

**Windows 安装包特性：**
- ✅ 自动在桌面创建快捷方式（名称：Cos Minecraft Server Launcher）
- ✅ 在开始菜单创建快捷方式
- ✅ 允许用户选择安装目录
- ✅ 支持系统级安装（所有用户）
- ✅ 支持升级安装（保留配置）

### macOS (DMG 磁盘映像)
```bash
./gradlew packageDmg
```

生成的文件位置：
- `composeApp/build/compose/binaries/main/dmg/`
- 文件名：`Cos Minecraft Server Launcher-1.0.0.dmg`

### Linux (DEB 包)
```bash
./gradlew packageDeb
```

生成的文件位置：
- `composeApp/build/compose/binaries/main/deb/`
- 文件名：`cos-minecraft-server-launcher_1.0.0-1_amd64.deb`

## 构建所有平台

```bash
# Windows
gradlew.bat packageDistributionForCurrentOS

# macOS/Linux
./gradlew packageDistributionForCurrentOS
```

## 清理构建

```bash
# Windows
gradlew.bat clean

# macOS/Linux
./gradlew clean
```

## 打包配置

打包配置在 `composeApp/build.gradle.kts` 中：

```kotlin
compose.desktop {
    application {
        mainClass = "byd.cxkcxkckx.mcserver.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Cos Minecraft Server Launcher"
            packageVersion = "1.0.0"
            
            windows {
                shortcut = true              // 创建桌面快捷方式
                menu = true                  // 创建开始菜单快捷方式
                menuGroup = "Cos Minecraft Server Launcher"
                dirChooser = true           // 允许选择安装目录
                perUserInstall = false      // 系统级安装
            }
        }
    }
}
```

## 常见问题

### Q: Windows Defender 报告安全警告
A: 这是因为应用没有数字签名。可以通过以下方式解决：
- 点击"更多信息" -> "仍要运行"
- 或者购买代码签名证书对应用进行签名

### Q: macOS 提示"无法打开，因为它来自身份不明的开发者"
A: 右键点击应用 -> 选择"打开" -> 点击"打开"按钮

### Q: 如何修改版本号？
A: 编辑 `composeApp/build.gradle.kts` 中的 `packageVersion`

### Q: 如何添加应用图标？
A: 在 `composeApp/src/jvmMain/resources/` 目录下放置图标文件：
- Windows: `icon.ico`
- macOS: `icon.icns`
- Linux: `icon.png`

## 发布清单

在发布新版本前，请确保：

- [ ] 更新版本号 (`packageVersion`)
- [ ] 更新 CHANGELOG.md
- [ ] 测试所有平台的构建
- [ ] 验证桌面快捷方式创建正确
- [ ] 测试升级安装（如果是升级版本）
- [ ] 准备发布说明
- [ ] 创建 Git 标签

## 技术栈

- **UI 框架**: Compose Multiplatform
- **语言**: Kotlin
- **构建工具**: Gradle 8.x
- **JVM 版本**: 17+
- **序列化**: Kotlinx Serialization, Jackson (YAML)
- **协程**: Kotlinx Coroutines

## 项目结构

```
mcserver/
├── composeApp/
│   ├── src/
│   │   └── jvmMain/
│   │       ├── kotlin/
│   │       │   └── byd/cxkcxkckx/mcserver/
│   │       │       ├── api/          # API 客户端
│   │       │       ├── data/         # 数据模型
│   │       │       ├── ui/           # UI 组件
│   │       │       └── utils/        # 工具类
│   │       └── resources/            # 资源文件
│   └── build.gradle.kts
├── gradle/
├── gradlew
├── gradlew.bat
└── settings.gradle.kts
```

## 联系方式

- GitHub: https://github.com/cyf112233/Cos-Minecraft-Server-Launcher
- Issues: https://github.com/cyf112233/Cos-Minecraft-Server-Launcher/issues

## 许可证

请查看 LICENSE 文件了解详情。
