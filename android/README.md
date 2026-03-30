# 📱 Android 端部署指南

## ⚠️ 重要：先下载语音模型

由于模型文件较大（42MB），网络下载容易中断，请**手动下载**：

### 步骤1：下载 Vosk 中文模型

**下载地址**：
```
https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip
```

**推荐方式**：
- 使用浏览器直接下载
- 或使用迅雷/IDM等下载工具（支持断点续传）

### 步骤2：解压模型

下载完成后，解压并将文件夹重命名为 `vosk-model-small-cn-0.22`，然后复制到：

```
voice-bridge/android/app/src/main/assets/
```

最终结构应该是：
```
android/app/src/main/assets/
└── vosk-model-small-cn-0.22/
    ├── am/
    ├── graph/
    ├── ivector/
    ├── mfcc.conf
    └── README
```

---

## 🛠️ 构建 APK

### 前提条件

1. **安装 Android Studio**
   - 下载地址：https://developer.android.com/studio
   - 安装时选择标准配置即可

2. **准备三星 S20**
   - 开启开发者选项：设置 → 关于手机 → 软件信息 → 连续点击"编译编号"7次
   - 开启 USB 调试：设置 → 开发者选项 → USB 调试

### 构建步骤

**步骤1：打开项目**
```bash
# 在 Android Studio 中打开
File → Open → 选择 voice-bridge/android 文件夹
```

**步骤2：等待 Gradle 同步**
- 首次打开会自动下载依赖（需要网络）
- 等待 Gradle sync 完成（底部状态栏显示 "Sync finished"）

**步骤3：连接手机**
- 用 USB 线连接 S20 到电脑
- 在手机上允许 USB 调试
- Android Studio 会显示设备名称

**步骤4：运行/构建**
- 点击 ▶️ Run 按钮直接安装到手机
- 或选择 Build → Build APK(s) 生成安装包

---

## 📋 首次使用

### 1. 确保 Mac 端服务已启动

在 Mac 终端运行：
```bash
cd voice-bridge/server
python3 main.py
```

确认显示：
```
🎙️  Voice Bridge Server
📡  服务地址: http://192.168.1.xxx:8765
```

### 2. 在 S20 上配置

1. 打开 "VoiceBridge" App
2. 输入 Mac 的 IP 地址（如 `192.168.1.23`）
3. 端口保持默认 `8765`
4. 点击"连接"
5. 看到绿色指示灯即连接成功

### 3. 开始使用

- 按住麦克风按钮说话，或开启自动监听模式
- 说出指令，如："你好"、"查天气"
- Mac 会执行并通过语音播报结果

---

## 🔧 常见问题

### Q1: 连接失败
- 确认 Mac 和 S20 在同一 Wi-Fi 网络
- 检查 Mac 防火墙是否允许端口 8765
- 尝试在 S20 浏览器访问 `http://{mac-ip}:8765/health`

### Q2: 语音识别不工作
- 确认模型文件已正确放入 assets 目录
- 检查 App 是否有录音权限
- 重启 App 重试

### Q3: 构建失败
- 确保 Android Studio 是最新版本
- 尝试 File → Invalidate Caches → Invalidate and Restart
- 检查 Gradle 同步是否完成

---

## 📦 项目文件说明

```
android/
├── app/src/main/
│   ├── java/com/voicebridge/
│   │   ├── MainActivity.kt          # 主界面
│   │   ├── VoiceBridgeApp.kt        # 应用入口
│   │   ├── ConnectionState.kt       # 连接状态
│   │   └── service/
│   │       └── VoiceRecognitionService.kt  # 语音识别服务
│   ├── res/                         # UI 资源
│   └── assets/                      # 语音模型（需手动添加）
├── build.gradle                     # 项目构建配置
└── settings.gradle                  # 项目设置
```

---

**下一步**：下载模型并构建 APK，有问题随时问我！