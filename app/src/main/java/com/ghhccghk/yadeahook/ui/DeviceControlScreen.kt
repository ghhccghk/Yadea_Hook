package com.ghhccghk.yadeahook.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ghhccghk.yadeahook.bluetooth.BleManager
import com.ghhccghk.yadeahook.bluetooth.CoyoteProtocol
import com.ghhccghk.yadeahook.bluetooth.SpeedStrengthMapper

@SuppressLint("MissingPermission")
@Composable
fun DeviceControlScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val bleManager = remember { BleManager(context) }
    val speedMapper = remember { SpeedStrengthMapper(context) }
    
    var isScanning by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectedDeviceName by remember { mutableStateOf<String?>(null) }
    var batteryLevel by remember { mutableIntStateOf(-1) }
    var manualStrengthA by remember { mutableStateOf(0f) }
    var manualStrengthB by remember { mutableStateOf(0f) }
    var isAutoMode by remember { mutableStateOf(true) }
    var deviceVersion by remember { mutableStateOf(CoyoteProtocol.Version.V3) }
    var scannedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var hasBluetoothPermission by remember { mutableStateOf(checkBluetoothPermission(context)) }
    
    DisposableEffect(Unit) {
        bleManager.connectionStateCallback = object : BleManager.ConnectionStateCallback {
            override fun onConnecting(device: BluetoothDevice) {
                isConnecting = true
                connectedDeviceName = device.name ?: device.address
            }
            
            override fun onConnected(device: BluetoothDevice) {
                isConnected = true
                isConnecting = false
                connectedDeviceName = device.name ?: device.address
            }
            
            override fun onDisconnected(device: BluetoothDevice, status: Int) {
                isConnected = false
                isConnecting = false
                connectedDeviceName = null
                batteryLevel = -1
            }
            
            override fun onConnectionFailed(device: BluetoothDevice, status: Int) {
                isConnecting = false
            }
        }
        
        bleManager.dataCallback = object : BleManager.DataCallback {
            override fun onStrengthUpdate(sequenceNumber: Int, strength: Int) {}
            
            override fun onBatteryLevelUpdate(level: Int) {
                batteryLevel = level
            }
            
            override fun onDataReceived(data: ByteArray) {}
        }
        
        bleManager.scanCallback = object : BleManager.DeviceScanCallback {
            override fun onDeviceFound(device: BluetoothDevice, rssi: Int, name: String?) {
                if (!scannedDevices.contains(device)) {
                    scannedDevices = scannedDevices + device
                }
            }
            
            override fun onScanComplete() {
                isScanning = false
            }
            
            override fun onScanFailed(errorCode: Int) {
                isScanning = false
            }
        }
        
        onDispose {
            bleManager.release()
        }
    }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    bleManager.disconnect()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "设备控制",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 权限提示
        if (!hasBluetoothPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "需要蓝牙权限",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "请在系统设置中授予蓝牙权限",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // 连接状态卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = when {
                        isConnected -> Icons.Default.BluetoothConnected
                        isConnecting -> Icons.Default.BluetoothSearching
                        else -> Icons.Default.BluetoothDisabled
                    },
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = when {
                        isConnected -> "已连接: $connectedDeviceName"
                        isConnecting -> "正在连接..."
                        else -> "未连接"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (isConnected && batteryLevel >= 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "电池: $batteryLevel%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = { batteryLevel / 100f },
                            modifier = Modifier.width(100.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 版本选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { deviceVersion = CoyoteProtocol.Version.V2 },
                        colors = if (deviceVersion == CoyoteProtocol.Version.V2) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text("V2")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { deviceVersion = CoyoteProtocol.Version.V3 },
                        colors = if (deviceVersion == CoyoteProtocol.Version.V3) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text("V3")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (!isConnected && !isConnecting) {
                        Button(
                            onClick = {
                                if (!hasBluetoothPermission) {
                                    hasBluetoothPermission = checkBluetoothPermission(context)
                                    return@Button
                                }
                                if (isScanning) {
                                    bleManager.stopScan()
                                } else {
                                    scannedDevices = emptyList()
                                    bleManager.startScan()
                                    isScanning = true
                                }
                            },
                            enabled = hasBluetoothPermission && bleManager.isBluetoothAvailable()
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isScanning) "停止扫描" else "扫描设备")
                        }
                    } else if (isConnected) {
                        Button(
                            onClick = { bleManager.zeroStrength() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("紧急停止")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { bleManager.disconnect() }) {
                            Text("断开连接")
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 扫描结果
        if (scannedDevices.isNotEmpty() && !isConnected) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "发现设备",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    scannedDevices.forEach { device ->
                        Button(
                            onClick = { bleManager.connect(device, deviceVersion) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(device.name ?: device.address)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // 强度控制
        if (isConnected) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "强度控制",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { isAutoMode = true },
                            colors = if (isAutoMode) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text("自动模式")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { isAutoMode = false },
                            colors = if (!isAutoMode) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text("手动模式")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isAutoMode) {
                        val config = speedMapper.getConfig()
                        Text(
                            text = "自动模式：强度根据车速自动调节",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("最大强度: ${config.maxStrength}")
                        Text("控制通道: ${config.channel.name}")
                    } else {
                        Text("A 通道强度: ${manualStrengthA.toInt()}")
                        Slider(
                            value = manualStrengthA,
                            onValueChange = {
                                manualStrengthA = it
                                bleManager.setStrength(CoyoteProtocol.Channel.A, it.toInt())
                            },
                            valueRange = 0f..200f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("B 通道强度: ${manualStrengthB.toInt()}")
                        Slider(
                            value = manualStrengthB,
                            onValueChange = {
                                manualStrengthB = it
                                bleManager.setStrength(CoyoteProtocol.Channel.B, it.toInt())
                            },
                            valueRange = 0f..200f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                manualStrengthA = 0f
                                manualStrengthB = 0f
                                bleManager.zeroStrength()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("清零所有通道")
                        }
                    }
                }
            }
        }
    }
}

private fun checkBluetoothPermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}