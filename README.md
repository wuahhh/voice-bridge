# Voice Bridge

使用语音控制Mac上的OpenClaw。三星S20作为语音遥控器，通过本地WiFi控制Mac执行指令。

## 快速开始

### Mac端
```bash
cd voice-bridge/server
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python main.py
```

### Android端
1. 下载Vosk中文模型并放入 `android/app/src/main/assets/`
2. 用Android Studio打开 `voice-bridge/android` 文件夹
3. 构建并安装APK到S20

详细部署文档：[docs/DEPLOY.md](docs/DEPLOY.md)

## 功能特性

- 🎙️ **本地语音识别** - 完全离线，隐私安全
- 🔄 **自动重连** - 断网自动恢复
- ⚡ **低延迟** - WebSocket实时通信
- 🔊 **语音反馈** - TTS播放执行结果

## 技术栈

- **服务器**: Python + FastAPI
- **客户端**: Kotlin + Android
- **语音识别**: Vosk
- **通信**: WebSocket