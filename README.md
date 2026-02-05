# Cos Minecraft Server Launcher (CMSL)

一款基于 Kotlin Multiplatform 和 Compose Multiplatform 构建的现代化、用户友好的 Minecraft 服务器管理应用程序。

## 项目概述

Cos Minecraft Server Launcher (CMSL) 是一款跨平台桌面应用程序，旨在简化 Minecraft 服务器的下载、配置和管理流程。通过直观的图形界面和强大的功能，CMSL 让服务器管理对新手和经验丰富的用户都触手可及。

## 核心功能

### 服务器管理
- **多服务器支持**：同时管理多个服务器实例
- **一键启停**：通过可视化状态指示器快速控制服务器
- **服务器配置**：图形化界面编辑服务器属性和配置
- **实时控制台**：查看支持 ANSI 颜色的服务器日志，直接发送命令

### 服务器核心下载
- **多源支持**：从 Paper、Purpur、Folia 等热门服务器核心下载
- **版本选择**：选择特定的 Minecraft 版本和构建号
- **自动安装**：下载后自动配置服务器
- **下载管理器**：使用进度指示器追踪多个下载任务

### 插件管理
- **集成市场**：直接从 Modrinth 浏览和安装插件
- **搜索功能**：按名称、类别或功能查找插件
- **版本兼容性**：查看每个插件支持的 Minecraft 版本
- **一键安装**：直接将插件安装到您的服务器

### 配置编辑器
- **可视化编辑**：通过图形界面编辑 server.properties 和 spigot.yml
- **属性分类**：有组织的设置便于导航
- **验证功能**：输入验证防止配置错误
- **实时预览**：在应用更改前查看效果

### 高级功能
- **Java 检测**：自动检测已安装的 Java 版本
- **自定义 Java 路径**：为每个服务器指定自定义 Java 安装
- **内存管理**：配置最小和最大内存分配
- **JVM 参数**：添加自定义 JVM 参数进行性能调优
- **服务器参数**：配置服务器特定的命令行参数
- **EULA 管理**：自动 EULA 接受工作流程

## 技术栈

### 核心技术
- **Kotlin Multiplatform**：桌面应用程序的跨平台代码库
- **Compose Multiplatform**：现代声明式 UI 框架
- **Kotlin Coroutines**：响应式 UI 的异步编程
- **Material Design 3**：现代、一致的设计语言

### 架构设计
- **MVVM 模式**：清晰的关注点分离
- **状态管理**：服务器实例的集中式状态管理
- **响应式编程**：基于 Flow 的数据流实现实时更新
- **模块化设计**：组织良好的包结构便于维护

## 系统要求

### 最低要求
- **操作系统**：Windows 10/11、macOS 10.14+ 或 Linux (64位)
- **内存**：4 GB（推荐 8 GB）
- **Java**：Java 17 或更高版本（Minecraft 1.18+ 必需）
- **磁盘空间**：2 GB 可用空间（服务器需要额外空间）

### 推荐配置
- **操作系统**：Windows 11 或 macOS 12+
- **内存**：8 GB 或更多
- **Java**：Java 21（最新 LTS 版本）
- **磁盘空间**：10 GB 或更多

## 安装指南

### 下载
从 [Releases](https://github.com/cyf112233/Cos-Minecraft-Server-Launcher/releases) 页面下载最新版本。

### Windows
1. 下载 `.exe` 安装程序
2. 运行安装程序并按照设置向导操作
3. 从开始菜单或桌面快捷方式启动 CMSL

### macOS
1. 下载 `.dmg` 文件
2. 打开 DMG 并将 CMSL 拖到应用程序文件夹
3. 从应用程序启动 CMSL

### Linux
1. 下载 `.deb` 或 `.rpm` 包（其他发行版使用 `.tar.gz`）
2. 使用包管理器安装
3. 从应用程序菜单启动 CMSL

## 快速入门

### 创建您的第一个服务器

1. **导航到下载选项卡**
   - 点击主导航栏中的"下载"选项卡

2. **选择服务器核心**
   - 从 Paper、Purpur、Folia 或其他可用服务器核心中选择
   - 选择您想要的 Minecraft 版本

3. **配置服务器**
   - 为服务器输入名称
   - 选择安装位置（默认为用户目录）
   - 点击"下载"开始

4. **启动服务器**
   - 返回"首页"选项卡
   - 从列表中选择新创建的服务器
   - 点击"启动服务器"来运行
   - 在提示时接受 EULA

### 管理服务器

#### 启动服务器
- 从列表中选择服务器
- 点击"启动服务器"按钮
- 监控控制台输出查看启动进度

#### 停止服务器
- 点击"正常关闭"进行优雅关机
- 仅在服务器无响应时使用"强制关闭"

#### 发送命令
- 在控制台输入框中输入命令
- 按 Enter 或点击发送按钮
- 命令直接发送到服务器控制台

### 安装插件

1. **打开市场**
   - 点击"市场"选项卡

2. **搜索插件**
   - 使用搜索栏查找特定插件
   - 浏览推荐插件

3. **安装插件**
   - 点击插件查看详情
   - 选择与服务器兼容的版本
   - 选择目标服务器
   - 点击"安装"

## 配置说明

### 服务器配置

#### 基本设置
- **服务器名称**：服务器列表中的显示名称
- **内存分配**：最小/最大内存（MB）
- **Java 路径**：Java 可执行文件路径
- **服务器类型**：Paper、Purpur 等

#### 高级设置
- **自定义 JVM 参数**：额外的 Java 标志
- **服务器参数**：Minecraft 服务器参数
- **无 GUI 模式**：禁用服务器 GUI（默认启用以更好集成）
- **自动重启**：崩溃时自动重启

### 应用程序设置

#### 文件位置
- **服务器目录**：`%UserProfile%\CosMinecraftServerLauncher\mcserver\`（Windows）
- **服务器目录**：`~/CosMinecraftServerLauncher/mcserver/`（macOS/Linux）
- **配置文件**：与服务器文件一起存储

## 项目结构

```
Cos-Minecraft-Server-Launcher/
├── composeApp/
│   ├── src/jvmMain/kotlin/byd/cxkcxkckx/mcserver/
│   │   ├── api/              # API 客户端（PaperMC、Modrinth）
│   │   ├── data/             # 数据模型和 DTO
│   │   ├── ui/               # UI 组件和界面
│   │   │   ├── screens/      # 主应用程序界面
│   │   │   └── theme/        # 主题和样式
│   │   └── utils/            # 工具类和管理器
│   └── build.gradle.kts      # 应用构建配置
├── gradle/                   # Gradle wrapper 文件
├── build.gradle.kts          # 根构建配置
├── settings.gradle.kts       # 项目设置
└── LICENSE                   # 项目许可证
```

## 从源码构建

### 前置要求
- JDK 17 或更高版本
- Gradle 8.0 或更高版本（通过 wrapper 包含）

### 构建步骤

1. **克隆仓库**
   ```bash
   git clone https://github.com/cyf112233/Cos-Minecraft-Server-Launcher.git
   cd Cos-Minecraft-Server-Launcher
   ```

2. **构建项目**
   ```bash
   ./gradlew build
   ```

3. **运行应用程序**
   ```bash
   ./gradlew run
   ```

4. **创建分发包**
   ```bash
   ./gradlew packageDistributionForCurrentOS
   ```

## 贡献指南

欢迎贡献！请遵循以下指南：

### 如何贡献

1. **Fork 仓库**
   - 在 GitHub 上创建此仓库的 fork

2. **创建功能分支**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **进行更改**
   - 编写干净、文档良好的代码
   - 遵循 Kotlin 编码规范
   - 为新功能添加测试

4. **提交更改**
   ```bash
   git commit -m "Add: 简要描述更改"
   ```

5. **推送到您的 Fork**
   ```bash
   git push origin feature/your-feature-name
   ```

6. **提交 Pull Request**
   - 打开带有清晰描述的 pull request
   - 引用任何相关问题

### 代码规范
- 遵循 Kotlin 官方风格指南
- 使用有意义的变量和函数名
- 为复杂逻辑编写注释
- 保持函数简洁专注

## 故障排除

### 服务器无法启动
- 验证 Java 已安装并可访问
- 检查服务器端口（默认 25565）是否可用
- 查看控制台日志查找错误消息
- 确保已接受 EULA

### 下载失败
- 检查互联网连接
- 验证下载源是否可访问
- 尝试不同的服务器核心或版本
- 检查可用磁盘空间

### 插件安装问题
- 验证插件与服务器版本兼容
- 检查 plugins 文件夹是否存在
- 确保有足够的权限
- 查看控制台日志查找插件错误

### 性能问题
- 在服务器配置中增加分配内存
- 更新到最新 Java 版本
- 调整 JVM 参数进行优化
- 关闭不必要的应用程序

## 常见问题

**问：CMSL 支持 Forge 或 Fabric 等模组服务器吗？**  
答：目前 CMSL 专注于基于插件的服务器（Paper、Purpur、Folia）。模组服务器支持计划在未来版本中推出。

**问：我可以将现有服务器导入 CMSL 吗？**  
答：可以，您可以手动将服务器文件放在 CMSL 服务器目录中，它们会自动被检测到。

**问：CMSL 是免费使用的吗？**  
答：是的，CMSL 在 MIT 许可证下完全免费且开源。

**问：如何更新我的服务器核心？**  
答：通过下载选项卡下载新版本，并替换服务器目录中的旧服务器 JAR 文件。

**问：我可以同时运行多个服务器吗？**  
答：可以，只要它们使用不同的端口，您可以运行多个服务器。

## 开源许可

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件。

## 致谢

### 第三方 API
- **PaperMC API**：服务器核心下载和版本信息
- **Modrinth API**：插件市场和元数据

### 技术框架
- **Compose Multiplatform**：UI 框架
- **Kotlin Coroutines**：异步编程
- **kotlinx.serialization**：JSON 解析
- **Ktor**：HTTP 客户端

### 社区
感谢所有通过反馈、错误报告和功能建议帮助改进 CMSL 的贡献者和用户。

## 支持

### 问题报告
- GitHub Issues：[报告 Bug](https://github.com/cyf112233/Cos-Minecraft-Server-Launcher/issues)
- 功能请求：[请求功能](https://github.com/cyf112233/Cos-Minecraft-Server-Launcher/issues)

### 文档
- 更多文档和指南可在 [Wiki](https://github.com/cyf112233/Cos-Minecraft-Server-Launcher/wiki) 中找到

### 社区
- 讨论：[GitHub Discussions](https://github.com/cyf112233/Cos-Minecraft-Server-Launcher/discussions)

---

**使用 Kotlin 和 Compose Multiplatform 构建**

Copyright (c) 2026 Cos Minecraft Server Launcher Project
