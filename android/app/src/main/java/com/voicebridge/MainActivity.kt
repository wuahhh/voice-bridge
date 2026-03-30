package com.voicebridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.voicebridge.databinding.ActivityMainBinding
import com.voicebridge.service.VoiceRecognitionService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 主界面 - 语音控制面板
 */
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
    private var serviceIntent: Intent? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkPermissions()
        observeConnectionState()
    }
    
    private fun setupUI() {
        binding.apply {
            // 连接/断开按钮
            btnConnect.setOnClickListener {
                if (VoiceRecognitionService.isRunning) {
                    stopVoiceService()
                } else {
                    startVoiceService()
                }
            }
            
            // 服务器设置
            btnSettings.setOnClickListener {
                showSettingsDialog()
            }
            
            // 测试按钮
            btnTest.setOnClickListener {
                sendTestCommand()
            }
            
            // 状态指示器点击
            statusIndicator.setOnClickListener {
                showStatusDetails()
            }
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
    
    private fun startVoiceService() {
        serviceIntent = Intent(this, VoiceRecognitionService::class.java).apply {
            putExtra("server_ip", binding.etServerIp.text.toString())
            putExtra("server_port", binding.etServerPort.text.toString().toIntOrNull() ?: 8765)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        updateUIState(true)
        Toast.makeText(this, "语音服务已启动", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopVoiceService() {
        serviceIntent?.let {
            stopService(it)
        }
        updateUIState(false)
        Toast.makeText(this, "语音服务已停止", Toast.LENGTH_SHORT).show()
    }
    
    private fun observeConnectionState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                VoiceRecognitionService.connectionState.collectLatest { state ->
                    updateConnectionUI(state)
                }
            }
        }
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                VoiceRecognitionService.recognizedText.collectLatest { text ->
                    binding.tvRecognized.text = text
                }
            }
        }
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                VoiceRecognitionService.responseText.collectLatest { text ->
                    binding.tvResponse.text = text
                }
            }
        }
    }
    
    private fun updateConnectionUI(state: ConnectionState) {
        binding.apply {
            when (state) {
                is ConnectionState.Disconnected -> {
                    statusIndicator.setBackgroundResource(R.drawable.status_disconnected)
                    tvStatus.text = "未连接"
                }
                is ConnectionState.Connecting -> {
                    statusIndicator.setBackgroundResource(R.drawable.status_connecting)
                    tvStatus.text = "连接中..."
                }
                is ConnectionState.Connected -> {
                    statusIndicator.setBackgroundResource(R.drawable.status_connected)
                    tvStatus.text = "已连接"
                }
                is ConnectionState.Error -> {
                    statusIndicator.setBackgroundResource(R.drawable.status_error)
                    tvStatus.text = "连接错误"
                    Snackbar.make(root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun updateUIState(running: Boolean) {
        binding.apply {
            btnConnect.text = if (running) "断开连接" else "开始连接"
            etServerIp.isEnabled = !running
            etServerPort.isEnabled = !running
        }
    }
    
    private fun showSettingsDialog() {
        // 显示服务器设置对话框
        AlertDialog.Builder(this)
            .setTitle("服务器设置")
            .setView(R.layout.dialog_settings)
            .setPositiveButton("保存") { dialog, _ ->
                // 保存设置
                dialog.dismiss()
            }
            .show()
    }
    
    private fun sendTestCommand() {
        // 发送测试命令
        lifecycleScope.launch {
            VoiceRecognitionService.sendTestCommand("你好，这是一个测试")
        }
    }
    
    private fun showStatusDetails() {
        val status = VoiceRecognitionService.getStatusInfo()
        AlertDialog.Builder(this)
            .setTitle("连接状态详情")
            .setMessage(status)
            .setPositiveButton("确定", null)
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        updateUIState(VoiceRecognitionService.isRunning)
    }
}