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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import java.io.File

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("speed_alert_prefs", Context.MODE_PRIVATE) }
    
    // 音频文件路径
    var selectedAudioPath by remember { mutableStateOf(prefs.getString("audio_path", "") ?: "") }
    
    // 速度阈值 (默认 25 km/h)
    var speedThreshold by remember { mutableStateOf(prefs.getFloat("speed_threshold", 25f).toString()) }
    
    // 触发频率/冷却时间 (默认 10 秒)
    var cooldownSeconds by remember { mutableStateOf((prefs.getLong("cooldown_ms", 10000L) / 1000).toString()) }
    
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
            text = "速度警报设置",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // ============ 速度阈值设置 ============
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "速度阈值",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "当车速超过此值时触发警报",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = speedThreshold,
                        onValueChange = { newValue ->
                            // 只允许数字和小数点
                            if (newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                speedThreshold = newValue
                            }
                        },
                        label = { Text("速度") },
                        suffix = { Text("km/h") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    TextButton(
                        onClick = {
                            speedThreshold = "25"
                        }
                    ) {
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
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ============ 触发频率设置 ============
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "触发频率",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "两次警报之间的最短间隔时间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = cooldownSeconds,
                        onValueChange = { newValue ->
                            // 只允许数字
                            if (newValue.matches(Regex("^\\d*$"))) {
                                cooldownSeconds = newValue
                            }
                        },
                        label = { Text("间隔时间") },
                        suffix = { Text("秒") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    TextButton(
                        onClick = {
                            cooldownSeconds = "10"
                        }
                    ) {
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
                            Text("秒")
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ============ 音频文件设置 ============
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "警报音频",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "选择自定义音频文件或使用默认蜂鸣声",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "当前音频：",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    text = if (selectedAudioPath.isNotEmpty()) {
                        File(selectedAudioPath).name
                    } else {
                        "默认蜂鸣声"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            launcher.launch(arrayOf("audio/*"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("选择文件")
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
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        if (selectedAudioPath.isNotEmpty()) {
                            testPlayAudio(context, selectedAudioPath)
                        } else {
                            testPlayDefaultAudio(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("测试播放")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // ============ 保存按钮 ============
        Button(
            onClick = {
                // 保存所有设置
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
            Text("保存设置")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // ============ 恢复默认设置 ============
        TextButton(
            onClick = {
                speedThreshold = "25"
                cooldownSeconds = "10"
                selectedAudioPath = ""
                prefs.edit().clear().apply()
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
        val fileName = "speed_alert_.mp3"
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
