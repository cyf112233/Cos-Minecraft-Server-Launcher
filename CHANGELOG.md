# 更新日志 (Changelog)

所有重要的项目更改都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
并且本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.0.0] - 2026-02-04

### 新增 (Added)
- ✨ 现代化的 UI 界面，使用 Material Design 3
- 🖥️ 服务器管理功能
  - 自动扫描和识别服务器
  - 支持 Paper、Spigot、Vanilla 等服务器类型
  - 服务器启动、停止、强制关闭
  - EULA 自动处理
- 📊 实时服务器监控
  - 在线玩家数统计
  - TPS（每秒刻数）监控
  - 内存使用情况
  - 服务器运行时间
- 🎮 控制台功能
  - 实时日志显示
  - ANSI 颜色代码支持
  - 命令输入和执行
  - 日志自动滚动
- ⚙️ 服务器配置
  - JVM 参数配置
  - 内存分配设置
  - 自定义启动参数
- 📥 服务器下载功能（计划中）
- 🔧 Java 环境检测
- 💾 配置文件管理（YAML 格式）

### Windows 特性
- 🪟 MSI 安装包支持
- 🖱️ 自动创建桌面快捷方式（名称：Cos Minecraft Server Launcher）
- 📋 开始菜单快捷方式
- 📂 可选择安装目录
- 👥 支持系统级安装（所有用户）
- 🔄 支持升级安装

### macOS 特性
- 🍎 DMG 磁盘映像支持
- 📦 原生应用包

### Linux 特性
- 🐧 DEB 包支持
- 🖱️ 桌面快捷方式

### 技术特性
- ⚡ 使用 Kotlin 和 Compose Multiplatform 构建
- 🔄 协程支持，异步操作流畅
- 🎨 响应式 UI，自动适配窗口大小
- 📱 跨平台支持（Windows、macOS、Linux）

## [未来计划] (Planned)

### v1.1.0
- [ ] 服务器下载功能完善
  - Paper MC 版本下载
  - Spigot 版本下载
  - Vanilla 版本下载
- [ ] 插件管理功能
  - 插件浏览和搜索
  - 插件安装和卸载
  - 插件配置编辑
- [ ] 备份功能
  - 自动备份
  - 手动备份
  - 备份恢复

### v1.2.0
- [ ] 多服务器同时运行
- [ ] 服务器性能图表
- [ ] 更详细的日志过滤
- [ ] 主题切换（亮色/暗色）
- [ ] 多语言支持

### v2.0.0
- [ ] 远程服务器管理
- [ ] 服务器集群支持
- [ ] 高级监控面板
- [ ] 插件开发工具集成

## 已知问题 (Known Issues)

- 统计信息的准确性依赖于服务器日志格式
- 某些服务器类型可能无法正确解析 TPS 信息
- Windows Defender 可能报告安全警告（需要数字签名）

## 贡献者 (Contributors)

感谢所有为这个项目做出贡献的人！

## 许可证 (License)

本项目采用 [LICENSE](LICENSE) 中指定的许可证。

---

**注意**: 此更新日志遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 格式。
