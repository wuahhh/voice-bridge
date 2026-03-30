package com.voicebridge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.voicebridge.databinding.ActivityMainBinding
import com.voicebridge.service.VoiceRecognitionService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    
    // UI 元素
    private lateinit var etServerIp: EditText
    private lateinit var etServerPort: EditText
    private lateinit var etWakeWord: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnTest: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvWakeWord: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = getSharedPreferences("voice_bridge_prefs", Context.MODE_PRIVATE)
        
        initUI()
        loadSettings()
        checkPermissions()
        observeConnectionState()
    }

    private fun initUI() {
        // 绑定视图
        etServerIp = findViewById(R.id.etServerIp)
        etServerPort = findViewById(R.id.etServerPort)
        etWakeWord = findViewById(R.id.etWakeWord)
        btnConnect = findViewById(R.id.btnConnect)
        btnTest = findViewById(R.id.btnTest)
        tvStatus = findViewById(R.id.tvStatus)
        tvWakeWord = findViewById(R.id.tvWakeWord)
        
        // 确保输入框可编辑
        etServerIp.isEnabled = true
        etServerIp.isFocusable = true
        etServerIp.isFocusableInTouchMode = true
        
        etServerPort.isEnabled = true
        etServerPort.isFocusable = true
        etServerPort.isFocusableInTouchMode = true
        
        etWakeWord.isEnabled = true
        etWakeWord.isFocusable = true
        etWakeWord.isFocusableInTouchMode = true
        
        // 连接按钮
        btnConnect.setOnClickListener {
            saveSettings()
            if (VoiceRecognitionService.isRunning) {
                stopVoiceService()
            } else {
                startVoiceService()
            }
        }
        
        // 测试按钮 - 修复点击无反应问题
        btnTest.setOnClickListener {
            saveSettings()
            sendTestCommand()
        }
        
        // 保存唤醒词按钮（如果有）
        findViewById<Button>(R.id.btnSaveWakeWord)?.setOnClickListener {
            saveWakeWord()
        }
    }

    private fun loadSettings() {
        // 加载保存的设置
        val savedIp = prefs.getString("server_ip", "192.168.1.12")
        val savedPort = prefs.getString("server_port", "8765")
        val savedWakeWord = prefs.getString("wake_word", "虾宝")
        
        etServerIp.setText(savedIp)
        etServerPort.setText(savedPort)
        etWakeWord.setText(savedWakeWord)
        tvWakeWord.text = "当前唤醒词: $savedWakeWord"
    }

    private fun saveSettings() {
        // 保存设置到 SharedPreferences
        prefs.edit().apply {
            putString("server_ip", etServerIp.text.toString().trim())
            putString("server_port", etServerPort.text.toString().trim())
            apply()
        }
    }
    
    private fun saveWakeWord() {
        val wakeWord = etWakeWord.text.toString().trim()
        if (wakeWord.isNotEmpty()) {
            prefs.edit().putString("wake_word", wakeWord).apply()
            tvWakeWord.text = "当前唤醒词: $wakeWord"
            Toast.makeText(this, "唤醒词已保存: $wakeWord", Toast.LENGTH_SHORT).show()
            
            // 如果服务正在运行，重启以应用新唤醒词
            if (VoiceRecognitionService.isRunning) {
                stopVoiceService()
                startVoiceService()
            }
        } else {
            Toast.makeText(this, "请输入唤醒词", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            checkBatteryOptimization()
        }
    }

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("需要忽略电池优化")
                .setMessage("为了保证语音服务在后台持续运行，需要关闭电池优化。")
                .setPositiveButton("设置") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun startVoiceService() {
        val ip = etServerIp.text.toString().trim()
        val port = etServerPort.text.toString().toIntOrNull() ?: 8765
        val wakeWord = prefs.getString("wake_word", "虾宝") ?: "虾宝"
        
        if (ip.isEmpty()) {
            Toast.makeText(this, "请输入服务器IP", Toast.LENGTH_SHORT).show()
            return
        }
        
        val serviceIntent = Intent(this, VoiceRecognitionService::class.java).apply {
            putExtra("server_ip", ip)
            putExtra("server_port", port)
            putExtra("wake_word", wakeWord)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        updateUIState(true)
        Toast.makeText(this, "语音服务已启动，唤醒词: $wakeWord", Toast.LENGTH_SHORT).show()
    }

    private fun stopVoiceService() {
        stopService(Intent(this, VoiceRecognitionService::class.java))
        updateUIState(false)
        Toast.makeText(this, "语音服务已停止", Toast.LENGTH_SHORT).show()
    }

    private fun sendTestCommand() {
        if (!VoiceRecognitionService.isRunning) {
            Toast.makeText(this, "请先启动语音服务", Toast.LENGTH_SHORT).show()
            return
        }
        
        val testCommand = "你好，这是一个测试"
        lifecycleScope.launch {
            try {
                VoiceRecognitionService.sendTestCommand(testCommand)
                Toast.makeText(this@MainActivity, "测试指令已发送", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeConnectionState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                VoiceRecognitionService.connectionState.collect { state ->
                    updateStatusUI(state)
                }
            }
        }
    }

    private fun updateStatusUI(state: ConnectionState) {
        when (state) {
            is ConnectionState.Disconnected -> {
                tvStatus.text = "未连接"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
            is ConnectionState.Connecting -> {
                tvStatus.text = "连接中..."
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            }
            is ConnectionState.Connected -> {
                tvStatus.text = "已连接"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
            is ConnectionState.Error -> {
                tvStatus.text = "错误: ${state.message}"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
        }
    }

    private fun updateUIState(running: Boolean) {
        btnConnect.text = if (running) "停止服务" else "启动服务"
        btnConnect.isEnabled = true
    }

    override fun onResume() {
        super.onResume()
        updateUIState(VoiceRecognitionService.isRunning)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                checkBatteryOptimization()
            } else {
                Toast.makeText(this, "需要权限才能使用语音识别", Toast.LENGTH_LONG).show()
            }
        }
    }
}
