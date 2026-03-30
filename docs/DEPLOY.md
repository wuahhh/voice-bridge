# Voice Bridge - 语音控制系统

让三星S20通过本地语音识别控制Mac上的OpenClaw。

## 系统架构

```
┌──────────────┐      WebSocket      ┌──────────────┐
│  Samsung S20 │  <─────────────────>  │   Mac Mini   │
│  (Android)   │     实时语音指令     │  (OpenClaw)  │
│   Vosk ASR   │  <─────────────────> │ FastAPI服务  │
└──────────────┘     TTS音频响应      └──────────────┘
```

## 特性

- ✅ **本地语音识别** - 使用Vosk离线ASR，无需联网
- ✅ **低延迟通信** - WebSocket实时传输，<100ms延迟
- ✅ **自动重连** - 网络断开后自动恢复连接
- ✅ **语音反馈** - TTS音频实时播放执行结果
- ✅ **隐私保护** - 所有语音数据本地处理

---

## Mac端部署

### 1. 环境要求

- macOS 12.0+
- Python 3.9+
- OpenClaw CLI已安装并配置

### 2. 安装依赖

```bash
cd voice-bridge/server

# 创建虚拟环境
python3 -m venv venv
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt
```

### 3. 启动服务

```bash
# 基础启动（默认端口8765）
python main.py

# 指定端口
python main.py --port 8888

# 指定日志级别
python main.py --log-level debug
```

启动后会在终端显示服务器地址，例如：
```
============================================================
🎙️  Voice Bridge Server
============================================================
📡  服务地址: http://192.168.1.100:8765
🔗  WebSocket: ws://192.168.1.100:8765/ws
💓  健康检查: http://192.168.1.100:8765/health
============================================================
```

### 4. 测试服务

```bash
# 健康检查
curl http://localhost:8765/health

# HTTP API测试
curl -X POST http://localhost:8765/api/command \
  -H "Content-Type: application/json" \
  -d '{"command": "列出当前目录文件"}'
```

---

## Android端部署

### 1. 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高
- Android SDK 34
- Kotlin 1.9.0+

### 2. 下载Vosk模型

1. 访问 [Vosk Models](https://alphacephei.com/vosk/models)
2. 下载 `vosk-model-small-cn-0.22`（中文小模型，约40MB）
3. 解压到 `android/app/src/main/assets/vosk-model-small-cn-0.22/`

模型文件结构：
```
app/src/main/assets/
└── vosk-model-small-cn-0.22/
    ├── am/
    │   └── final.mdl
    ├── graph/
    ├── ivector/
    └── mfcc.conf
```

### 3. 修改服务器地址

在 `MainActivity.kt` 中修改默认服务器IP：

```kotlin
// 第48行附近
etServerIp.setText("192.168.1.100")  // 修改为你的Mac IP
```

### 4. 构建APK

```bash
cd voice-bridge/android

# 清理构建
./gradlew clean

# 构建Debug APK
./gradlew assembleDebug

# APK输出位置
# app/build/outputs/apk/debug/app-debug.apk
```

### 5. 安装到S20

```bash
# 通过ADB安装
adb install app/build/outputs/apk/debug/app-debug.apk

# 或使用Android Studio的"Run"按钮直接部署
```

---

## 使用指南

### 首次连接

1. **确保设备在同一WiFi** - Mac和S20连接同一局域网
2. **启动Mac服务** - 运行 `python main.py`
3. **获取Mac IP** - 从服务启动信息中获取
4. **配置S20** - 在App中输入Mac的IP地址
5. **点击连接** - 等待状态变为"已连接"

### 语音指令

连接成功后，直接说出指令即可：

| 示例指令 | 功能 |
|---------|------|
| "打开浏览器" | 启动默认浏览器 |
| "打开微信" | 启动微信 |
| "播放音乐" | 启动音乐播放器 |
| "查询天气" | 获取天气信息 |
| "设置提醒" | 创建提醒事项 |

### 状态说明

- 🔴 灰色 - 未连接
- 🟡 黄色 - 连接中
- 🟢 绿色 - 已连接
- 🔴 红色 - 连接错误

---

## 故障排查

### 无法连接到服务器

1. 检查Mac和S20是否在同一WiFi
2. 确认Mac防火墙允许端口8765
   ```bash
   # 临时关闭防火墙（仅测试）
   sudo /usr/libexec/ApplicationFirewall/socketfilterfw --setglobalstate off
   ```
3. 检查服务器IP是否正确
   ```bash
   # Mac上查看IP
   ifconfig | grep "inet "
   ```

### 语音识别不工作

1. 确认麦克风权限已授予
2. 检查Vosk模型是否正确放置
3. 查看logcat日志
   ```bash
   adb logcat -s VoiceBridgeService:D
   ```

### WebSocket断开频繁

1. 检查WiFi信号强度
2. 查看Mac服务日志
3. 增加日志级别排查
   ```bash
   python main.py --log-level debug
   ```

---

## 项目结构

```
voice-bridge/
├── server/                 # Mac服务端
│   ├── main.py            # FastAPI服务主文件
│   ├── requirements.txt   # Python依赖
│   └── .gitignore
│
├── android/               # Android客户端
│   ├── app/
│   │   ├── build.gradle
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/voicebridge/
│   │       │   ├── VoiceBridgeApp.kt
│   │       │   ├── MainActivity.kt
│   │       │   ├── ConnectionState.kt
│   │       │   └── service/
│   │       │       └── VoiceRecognitionService.kt
│   │       └── res/
│   │           ├── layout/activity_main.xml
│   │           ├── values/
│   │           └── drawable/
│   ├── build.gradle
│   └── settings.gradle
│
├── docs/                  # 文档
│   └── DEPLOY.md         # 本文件
│
└── models/               # Vosk模型目录
```

---

## 开发计划

- [x] 基础WebSocket通信
- [x] Vosk语音识别集成
- [x] TTS音频播放
- [x] 自动重连机制
- [ ] 语音唤醒词支持
- [ ] 多语言识别
- [ ] 语音指令历史记录
- [ ] 自定义快捷指令

---

## 技术栈

| 组件 | 技术 |
|-----|------|
| 服务器 | Python + FastAPI + WebSocket |
| Android | Kotlin + Coroutines + Jetpack |
| 语音识别 | Vosk Offline ASR |
| 语音合成 | macOS say / pyttsx3 |

---

## License

MIT License

## 致谢

- [Vosk](https://github.com/alphacep/vosk-api) - 离线语音识别引擎
- [FastAPI](https://fastapi.tiangolo.com/) - 现代Python Web框架
- [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) - WebSocket客户端