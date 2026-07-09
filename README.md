# VNCCraft (Paper)

VNCCraft Paper 服务端插件。配合 Fabric 客户端模组使用，将 VNC 远程桌面带入 Minecraft。

## 架构

```
VNC 服务器 ←TCP→ Paper 插件 (纯隧道) ←Plugin Channel→ Fabric 客户端 (Vernacular 解码 + DrawLib 渲染)
```

Paper 服务端不碰 VNC 协议，只做 TCP 字节转发 + Plugin Message 通道。客户端运行完整 VNC 协议栈。

## 安装

### 服务端

1. 将 JAR 放入 Paper 服务器 `plugins/` 目录
2. 重启服务器

### 客户端

需要安装 Fabric 客户端模组（`fabric` 分支），包含 Vernacular + DrawLib。客户端同时兼容 Paper 插件和 Fabric 服务端，自动检测。

## 使用

安装后无需额外配置。所有操作通过客户端木铲工具完成：

| 操作 | 方式 |
|------|------|
| 放置屏幕 | 手持木铲右键方块表面 |
| 删除屏幕 | 左键已有屏幕 |
| 连接 VNC | Shift + 右键屏幕，输入信息 |
| 激光模式 | Ctrl + 滚轮切换 |
| 滚动 | Tab + 滚轮 |

### 音频

开启音频需 VNC 服务器端 PulseAudio TCP 模块已加载（端口 4713）。

## 构建

需要 JDK 25 + Gradle：

```bash
./gradlew build
```

JAR 输出在 `build/libs/`。

## 协议

AGPL-3.0
