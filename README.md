# VNCCraft (Fabric)

把 VNC 远程桌面搬进 Minecraft 世界。在方块表面放置虚拟屏幕，连接真实的 VNC 服务器，通过准星与远程桌面交互。

## 架构

```
VNC 服务器 ←TCP→ Fabric 服务端 (纯隧道) ←Fabric 网络包→ Fabric 客户端 (Vernacular 解码 + DrawLib 渲染)
```

服务端不碰 VNC 协议，只做 TCP 字节转发。客户端运行完整的 VNC 协议栈，渲染到世界空间。

## 安装

1. 安装 Fabric Loader（MC 26.1.2）
2. 安装依赖：**DrawLib**（渲染库）和 Fabric API
3. 将 VNCCraft JAR 放入 `mods/` 目录

## 使用

### 工具

手持**木铲**即可操作 VNC 屏幕：

| 操作 | 方式 |
|------|------|
| 放置屏幕 | 右键方块表面 |
| 删除屏幕 | 左键已有屏幕 |
| 连接 VNC | Shift + 右键屏幕，输入主机/端口/密码 |
| 断开连接 | 同界面点击 Disconnect |
| 激光模式 | Ctrl + 滚轮切换，准星控制远程光标 |
| 滚动 | Tab + 滚轮 |

### 音频

连接页面可开启 PulseAudio 音频转发。需确保 VNC 服务器端 PulseAudio TCP 模块已加载。

## 构建

需要 JDK 25 + Gradle：

```bash
./gradlew build
```

JAR 输出在 `build/libs/`。

## 协议

AGPL-3.0
