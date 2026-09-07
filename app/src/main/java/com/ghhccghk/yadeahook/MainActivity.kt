package com.ghhccghk.yadeahook

import android.annotation.SuppressLint
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.ghhccghk.yadeahook.ui.LogScreen
import com.ghhccghk.yadeahook.ui.SettingsScreen
import com.ghhccghk.yadeahook.ui.theme.YadeaHookTheme

class MainActivity : ComponentActivity() {
    private var speedAlertReceiver: SpeedAlertReceiver? = null

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

        setContent {
            YadeaHookTheme {
                YadeaHookApp()
            }
        }
    }
    
    override fun onDestroy() {
        // 注销广播接收器
        speedAlertReceiver?.let {
            unregisterReceiver(it)
            speedAlertReceiver = null
        }
        super.onDestroy()
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
                AppDestinations.FAVORITES -> PlaceholderScreen("Favorites", Modifier.padding(innerPadding))
                AppDestinations.PROFILE -> PlaceholderScreen("Profile", Modifier.padding(innerPadding))
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
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
    SETTINGS("Settings", R.drawable.ic_settings),
}
