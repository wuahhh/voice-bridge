# 🚀 快速开始检查清单

## Mac 端 ✅ 已完成

- [x] Python 依赖已安装
- [x] 服务已启动在 http://192.168.1.23:8765
- [x] WebSocket 端点 ws://192.168.1.23:8765/ws 可用

**保持运行**：不要关闭终端窗口，服务需要持续运行

---

## Android 端 ⏳ 待完成

### 第一步：下载语音模型（约42MB）
```bash
# 手动下载
https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip

# 解压后放到
voice-bridge/android/app/src/main/assets/vosk-model-small-cn-0.22/
```

### 第二步：安装 Android Studio
https://developer.android.com/studio

### 第三步：打开项目并构建
```
1. Android Studio → Open → 选择 voice-bridge/android
2. 等待 Gradle 同步完成
3. 连接 S20 手机（开启USB调试）
4. 点击 Run 按钮安装
```

### 第四步：连接使用
```
1. 输入 Mac IP: 192.168.1.23
2. 端口: 8765
3. 点击"连接"
4. 开始语音控制！
```

---

## 📞 测试命令示例

对着 S20 说出：
- "你好" - 测试基本连接
- "查天气" - 查询天气（如果OpenClaw配置了天气技能）
- "帮助" - 查看可用命令

---

**预计完成时间**：下载模型（5-10分钟）+ 构建APK（10-15分钟）= **约20-30分钟**