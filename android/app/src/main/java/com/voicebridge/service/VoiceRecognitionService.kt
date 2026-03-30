package com.voicebridge.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voicebridge.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import java.io.*
import java.net.URI
import java.net.URISyntaxException
import java.util.*

/**
 * 语音识别服务 - 核心服务
 * 管理Vosk语音识别、WebSocket连接和音频播放
 */
class VoiceRecognitionService : Service() {
    
    companion object {
        private const val TAG = "VoiceBridgeService"
        private const val NOTIFICATION_ID = 1001
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val COMMAND_COOLDOWN_MS = 2000L
        
        @Volatile
        var isRunning: Boolean = false
            private set
        
        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val connectionState: StateFlow<ConnectionState> = _connectionState
        
        private val _recognizedText = MutableStateFlow("")
        val recognizedText: StateFlow<String> = _recognizedText
        
        private val _responseText = MutableStateFlow("")
        val responseText: StateFlow<String> = _responseText
        
        private var lastCommand: String = ""
        private var lastCommandTime: Long = 0
        
        private var activeInstance: VoiceRecognitionService? = null
        
        fun getStatusInfo(): String {
            return """
                服务状态: ${if (isRunning) "运行中" else "已停止"}
                连接状态: ${_connectionState.value}
                最后命令: $lastCommand
                最后响应: ${_responseText.value}
            """.trimIndent()
        }
        
        suspend fun sendTestCommand(command: String) {
            activeInstance?.sendCommandToServer(command)
        }
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var webSocketClient: WebSocketClient? = null
    private var serverUri: URI? = null
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    
    private var speechService: SpeechService? = null
    private var voskModel: Model? = null
    
    private var mediaPlayer: MediaPlayer? = null
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "服务创建")
        activeInstance = this
        acquireWakeLock()
        initializeVosk()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ip = intent?.getStringExtra("server_ip") ?: "192.168.1.100"
        val port = intent?.getIntExtra("server_port", 8765) ?: 8765
        
        try {
            serverUri = URI("ws://$ip:$port/ws")
            Log.i(TAG, "服务器地址: $serverUri")
        } catch (e: URISyntaxException) {
            Log.e(TAG, "无效的服务器地址", e)
            stopSelf()
            return START_NOT_STICKY
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        isRunning = true
        _connectionState.value = ConnectionState.Connecting
        
        connectWebSocket()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "服务销毁")
        isRunning = false
        activeInstance = null
        
        reconnectJob?.cancel()
        disconnectWebSocket()
        stopRecognition()
        releaseWakeLock()
        
        serviceScope.cancel()
        voskModel?.close()
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VoiceBridge::WakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L) // 30分钟
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    private fun initializeVosk() {
        serviceScope.launch {
            try {
                // 检查模型是否存在
                val modelPath = File(filesDir, "vosk-model-small-cn-0.22")
                if (!modelPath.exists()) {
                    Log.w(TAG, "Vosk模型不存在，需要从assets解压")
                    unpackModelFromAssets()
                }
                
                withContext(Dispatchers.IO) {
                    voskModel = Model(modelPath.absolutePath)
                    Log.i(TAG, "Vosk模型加载成功")
                }
                
                withContext(Dispatchers.Main) {
                    startRecognition()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Vosk初始化失败", e)
                _connectionState.value = ConnectionState.Error("语音识别初始化失败: ${e.message}")
            }
        }
    }
    
    private suspend fun unpackModelFromAssets() = withContext(Dispatchers.IO) {
        val assetManager = assets
        val modelDir = File(filesDir, "vosk-model-small-cn-0.22")
        modelDir.mkdirs()
        
        // 从assets复制模型文件
        val modelFiles = arrayOf(
            "am/final.mdl",
            "graph/phones/word_boundary.int",
            "graph/Gr.fst",
            "ivector/final.dubm",
            "ivector/final.mat",
            "ivector/final.ie",
            "ivector/global_cmvn.stats",
            "ivector/online_cmvn.conf",
            "ivector/splice.conf",
            "mfcc.conf"
        )
        
        for (file in modelFiles) {
            val destFile = File(modelDir, file)
            destFile.parentFile?.mkdirs()
            
            try {
                assetManager.open("vosk-model-small-cn-0.22/$file").use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "无法复制文件: $file")
            }
        }
    }
    
    private fun startRecognition() {
        if (voskModel == null) {
            Log.w(TAG, "模型未加载，无法启动识别")
            return
        }
        
        try {
            val recognizer = Recognizer(voskModel, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            
            speechService?.startListening(object : org.vosk.android.RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    hypothesis?.let {
                        try {
                            val json = JSONObject(it)
                            val partial = json.optString("partial", "")
                            if (partial.isNotEmpty()) {
                                _recognizedText.value = partial
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析partial结果失败", e)
                        }
                    }
                }
                
                override fun onResult(hypothesis: String?) {
                    hypothesis?.let {
                        try {
                            val json = JSONObject(it)
                            val text = json.optString("text", "")
                            if (text.isNotEmpty()) {
                                _recognizedText.value = text
                                processVoiceCommand(text)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析结果失败", e)
                        }
                    }
                }
                
                override fun onFinalResult(hypothesis: String?) {
                }
                
                override fun onError(exception: Exception?) {
                    Log.e(TAG, "语音识别错误", exception)
                    _connectionState.value = ConnectionState.Error("语音识别错误: ${exception?.message}")
                }
                
                override fun onTimeout() {
                    Log.w(TAG, "语音识别超时")
                    restartRecognition()
                }
            })
            
            Log.i(TAG, "语音识别已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动语音识别失败", e)
        }
    }
    
    private fun stopRecognition() {
        speechService?.stop()
        speechService = null
    }
    
    private fun restartRecognition() {
        stopRecognition()
        mainHandler.postDelayed({
            startRecognition()
        }, 500)
    }
    
    private fun processVoiceCommand(command: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCommandTime < COMMAND_COOLDOWN_MS) {
            Log.d(TAG, "命令冷却中，忽略")
            return
        }
        
        if (command == lastCommand && currentTime - lastCommandTime < 5000) {
            Log.d(TAG, "重复命令，忽略")
            return
        }
        
        lastCommand = command
        lastCommandTime = currentTime
        
        sendCommandToServer(command)
    }
    
    private fun connectWebSocket() {
        if (serverUri == null) return
        
        webSocketClient = object : WebSocketClient(serverUri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.i(TAG, "WebSocket连接成功")
                reconnectAttempts = 0
                _connectionState.value = ConnectionState.Connected
                
                sendJson(mapOf(
                    "type" to "connect",
                    "device" to Build.MODEL,
                    "timestamp" to System.currentTimeMillis()
                ))
            }
            
            override fun onMessage(message: String?) {
                message?.let { handleServerMessage(it) }
            }
            
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.w(TAG, "WebSocket关闭: code=$code, reason=$reason, remote=$remote")
                _connectionState.value = ConnectionState.Disconnected
                
                if (isRunning && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    scheduleReconnect()
                }
            }
            
            override fun onError(ex: Exception?) {
                Log.e(TAG, "WebSocket错误", ex)
                _connectionState.value = ConnectionState.Error("连接错误: ${ex?.message}")
            }
        }
        
        try {
            webSocketClient?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "连接WebSocket失败", e)
            scheduleReconnect()
        }
    }
    
    private fun disconnectWebSocket() {
        reconnectJob?.cancel()
        try {
            webSocketClient?.close()
        } catch (e: Exception) {
            Log.e(TAG, "关闭WebSocket时出错", e)
        }
        webSocketClient = null
        _connectionState.value = ConnectionState.Disconnected
    }
    
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        
        reconnectAttempts++
        _connectionState.value = ConnectionState.Connecting
        
        Log.i(TAG, "计划重连... 尝试次数: $reconnectAttempts")
        
        reconnectJob = serviceScope.launch {
            delay(RECONNECT_DELAY_MS * reconnectAttempts.coerceAtMost(5))
            if (isRunning && webSocketClient?.isOpen != true) {
                connectWebSocket()
            }
        }
    }
    
    fun sendCommandToServer(command: String) {
        val json = JSONObject().apply {
            put("type", "command")
            put("command", command)
            put("request_id", UUID.randomUUID().toString())
            put("timestamp", System.currentTimeMillis())
            put("tts", true)
        }
        
        webSocketClient?.send(json.toString())
        Log.i(TAG, "发送命令: $command")
    }
    
    private fun sendJson(data: Map<String, Any?>) {
        val json = JSONObject(data).toString()
        webSocketClient?.send(json)
    }
    
    private fun handleServerMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type")
            
            when (type) {
                "command_result" -> {
                    val result = json.optString("result", "")
                    _responseText.value = result
                    
                    if (json.has("audio")) {
                        val audioBase64 = json.getString("audio")
                        playAudioFromBase64(audioBase64)
                    }
                }
                "error" -> {
                    val error = json.optString("error", "未知错误")
                    _responseText.value = "错误: $error"
                }
                else -> {
                    Log.d(TAG, "收到消息: $message")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理消息失败", e)
        }
    }
    
    private fun playAudioFromBase64(base64Audio: String) {
        try {
            val audioData = Base64.decode(base64Audio, Base64.DEFAULT)
            val tempFile = File.createTempFile("tts_", ".aiff", cacheDir)
            FileOutputStream(tempFile).use { it.write(audioData) }
            
            mainHandler.post {
                playAudioFile(tempFile.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放音频失败", e)
        }
    }
    
    private fun playAudioFile(filePath: String) {
        mediaPlayer?.release()
        
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(filePath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                prepare()
                start()
                
                setOnCompletionListener {
                    it.release()
                    File(filePath).delete()
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer错误: what=$what, extra=$extra")
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "播放音频失败", e)
                release()
            }
        }
    }
    
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, VoiceBridgeApp.CHANNEL_ID)
            .setContentTitle("语音助手")
            .setContentText("语音控制服务运行中...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}