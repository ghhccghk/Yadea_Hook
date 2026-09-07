package com.ghhccghk.yadeahook

import android.Manifest
import android.annotation.SuppressLint
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import com.ghhccghk.yadeahook.ui.DeviceControlScreen
import com.ghhccghk.yadeahook.ui.LogScreen
import com.ghhccghk.yadeahook.ui.SettingsScreen
import com.ghhccghk.yadeahook.ui.theme.YadeaHookTheme

class MainActivity : ComponentActivity() {
    private var speedAlertReceiver: SpeedAlertReceiver? = null
    
    // 蓝牙权限申请
    private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "蓝牙权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "需要蓝牙权限才能连接设备", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 注册速度警报广播接收器
        speedAlertReceiver = SpeedAlertReceiver()
        val filter = IntentFilter(SpeedAlertReceiver.ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(speedAlertReceiver, filter,RECEIVER_EXPORTED)
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            @Suppress("DEPRECATION")
            applicationContext.registerReceiver(
                speedAlertReceiver,
                filter
            )
        }
        requestBluetoothPermissions()


        setContent {
            YadeaHookTheme {
                YadeaHookApp()
            }
        }
    }
    
    override fun onDestroy() {
        speedAlertReceiver?.let {
            unregisterReceiver(it)
            speedAlertReceiver = null
        }
        super.onDestroy()
    }
    
    private fun requestBluetoothPermissions() {
        val permissionsToRequest = bluetoothPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }
}

@Composable
fun YadeaHookApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> LogScreen(modifier = Modifier.padding(innerPadding))
                AppDestinations.DEVICE -> DeviceControlScreen(modifier = Modifier.padding(innerPadding))
                AppDestinations.SETTINGS -> SettingsScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
fun PlaceholderScreen(name: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = name)
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    DEVICE("Device", R.drawable.ic_bluetooth),
    SETTINGS("Settings", R.drawable.ic_settings),
}