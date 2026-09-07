package com.ghhccghk.yadeahook.ui

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ghhccghk.yadeahook.bluetooth.CoyoteProtocol
import com.ghhccghk.yadeahook.bluetooth.SpeedStrengthMapper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("speed_alert_prefs", Context.MODE_PRIVATE) }
    val speedMapper = remember { SpeedStrengthMapper(context) }
    
    // 速度警报设置
    var selectedAudioPath by remember { mutableStateOf(prefs.getString("audio_path", "") ?: "") }
    var speedThreshold by remember { mutableStateOf(prefs.getFloat("speed_threshold", 25f).toString()) }
    var cooldownSeconds by remember { mutableStateOf((prefs.getLong("cooldown_ms", 10000L) / 1000).toString()) }
    
    // 设备控制设置
    var mappingConfig by remember { mutableStateOf(speedMapper.getConfig()) }
    var showCurveEditor by remember { mutableStateOf(false) }
    
    // 文件选择器
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { audioUri ->
            val copiedPath = copyAudioToPrivateStorage(context, audioUri)
            if (copiedPath != null) {
                selectedAudioPath = copiedPath
                prefs.edit().putString("audio_path", copiedPath).apply()
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // ============ 速度警报设置 ============
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "速度警报",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 速度阈值
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = speedThreshold,
                        onValueChange = { newValue ->
                            if (newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                speedThreshold = newValue
                            }
                        },
                        label = { Text("警报阈值") },
                        suffix = { Text("km/h") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    TextButton(onClick = { speedThreshold = "25" }) {
                        Text("重置")
                    }
                }
                
                // 快捷按钮
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("15", "20", "25", "30", "35").forEach { value ->
                        Button(
                            onClick = { speedThreshold = value },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(value)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 触发频率
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = cooldownSeconds,
                        onValueChange = { newValue ->
                            if (newValue.matches(Regex("^\\d*$"))) {
                                cooldownSeconds = newValue
                            }
                        },
                        label = { Text("触发间隔") },
                        suffix = { Text("秒") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    TextButton(onClick = { cooldownSeconds = "10" }) {
                        Text("重置")
                    }
                }
                
                // 快捷按钮
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("5", "10", "30", "60").forEach { value ->
                        Button(
                            onClick = { cooldownSeconds = value },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("${value}秒")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 音频选择
                Text(
                    text = "当前音频: ${if (selectedAudioPath.isNotEmpty()) File(selectedAudioPath).name else "默认蜂鸣声"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { launcher.launch(arrayOf("audio/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("选择音频")
                    }
                    
                    Button(
                        onClick = {
                            selectedAudioPath = ""
                            prefs.edit().remove("audio_path").apply()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("使用默认")
                    }
                    
                    Button(
                        onClick = {
                            if (selectedAudioPath.isNotEmpty()) {
                                testPlayAudio(context, selectedAudioPath)
                            } else {
                                testPlayDefaultAudio(context)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("测试")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ============ 设备控制设置 ============
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "设备控制",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 启用开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用设备控制")
                    Switch(
                        checked = mappingConfig.enabled,
                        onCheckedChange = { enabled ->
                            mappingConfig = mappingConfig.copy(enabled = enabled)
                            speedMapper.saveConfig(mappingConfig)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 通道选择
                Text(
                    text = "控制通道",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CoyoteProtocol.Channel.entries.forEach { channel ->
                        Button(
                            onClick = {
                                mappingConfig = mappingConfig.copy(channel = channel)
                                speedMapper.saveConfig(mappingConfig)
                            },
                            colors = if (mappingConfig.channel == channel) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(when (channel) {
                                CoyoteProtocol.Channel.A -> "A"
                                CoyoteProtocol.Channel.B -> "B"
                                CoyoteProtocol.Channel.BOTH -> "A+B"
                            })
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 最大强度
                Text(
                    text = "最大强度: ${mappingConfig.maxStrength}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Slider(
                    value = mappingConfig.maxStrength.toFloat(),
                    onValueChange = { value ->
                        mappingConfig = mappingConfig.copy(maxStrength = value.toInt())
                    },
                    onValueChangeFinished = {
                        speedMapper.saveConfig(mappingConfig)
                    },
                    valueRange = 0f..200f,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 曲线编辑按钮
                Button(
                    onClick = { showCurveEditor = !showCurveEditor },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showCurveEditor) "隐藏曲线编辑器" else "编辑车速-强度曲线")
                }
                
                // 曲线编辑器
                if (showCurveEditor) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    CurveEditor(
                        curvePoints = mappingConfig.curvePoints,
                        onCurvePointsChanged = { newPoints ->
                            mappingConfig = mappingConfig.copy(curvePoints = newPoints)
                            speedMapper.saveConfig(mappingConfig)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 重置曲线按钮
                    TextButton(
                        onClick = {
                            speedMapper.resetToDefault()
                            mappingConfig = speedMapper.getConfig()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("重置为默认曲线")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 保存按钮
        Button(
            onClick = {
                val threshold = speedThreshold.toFloatOrNull() ?: 25f
                val cooldown = (cooldownSeconds.toLongOrNull() ?: 10L) * 1000L
                
                prefs.edit().apply {
                    putFloat("speed_threshold", threshold)
                    putLong("cooldown_ms", cooldown)
                    apply()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存所有设置")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        TextButton(
            onClick = {
                speedThreshold = "25"
                cooldownSeconds = "10"
                selectedAudioPath = ""
                prefs.edit().clear().apply()
                speedMapper.resetToDefault()
                mappingConfig = speedMapper.getConfig()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("恢复所有默认设置")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun copyAudioToPrivateStorage(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val fileName = "speed_alert_${System.currentTimeMillis()}.mp3"
        val outputFile = File(context.filesDir, fileName)
        
        inputStream.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        outputFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun testPlayAudio(context: Context, path: String) {
    try {
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            prepare()
            start()
            setOnCompletionListener { mp ->
                mp.release()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        testPlayDefaultAudio(context)
    }
}

private fun testPlayDefaultAudio(context: Context) {
    try {
        val mediaPlayer = MediaPlayer.create(context, com.ghhccghk.yadeahook.R.raw.beep)
        mediaPlayer?.setOnCompletionListener { mp ->
            mp.release()
        }
        mediaPlayer?.start()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}