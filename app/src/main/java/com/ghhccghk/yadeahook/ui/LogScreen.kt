package com.ghhccghk.yadeahook.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghhccghk.yadeahook.VehicleControlReceiver
import com.ghhccghk.yadeahook.provider.HookLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logs = LogReceiver.logs
    val listState = rememberLazyListState()
    var ttpExpanded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val receiver = LogReceiver()
        context.registerReceiver(receiver, IntentFilter(HookLogger.ACTION), Context.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    val ttpEntry = logs.lastOrNull { it.tag == "TtpInfo" }
    val ttpFields = remember(ttpEntry) {
        ttpEntry?.message?.split(", ")?.mapNotNull { field ->
            val idx = field.indexOf('=')
            if (idx > 0) field.substring(0, idx) to field.substring(idx + 1)
            else null
        } ?: emptyList()
    }

    fun sendControl(command: String, param: Int = 0) {
        context.sendBroadcast(Intent(VehicleControlReceiver.ACTION).apply {
            putExtra("command", command)
            putExtra("param", param)
        })
        Toast.makeText(context, "已发送: $command", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Hook 日志", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = { logs.clear() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("清除", fontSize = 13.sp)
            }
        }

        // 控制按钮
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("车辆控制", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(onClick = { sendControl("START") }) { Text("启动", fontSize = 11.sp) }
                    OutlinedButton(onClick = { sendControl("STOP") }) { Text("关闭", fontSize = 11.sp) }
                    OutlinedButton(onClick = { sendControl("SCOOTER_LOCK") }) { Text("锁车", fontSize = 11.sp) }
                    OutlinedButton(onClick = { sendControl("SCOOTER_UNLOCK") }) { Text("解锁", fontSize = 11.sp) }
                    OutlinedButton(onClick = { sendControl("SCOOTER_GEAR_1") }) { Text("1档", fontSize = 11.sp) }
                    OutlinedButton(onClick = { sendControl("SCOOTER_GEAR_2") }) { Text("2档", fontSize = 11.sp) }
                    OutlinedButton(onClick = { sendControl("SCOOTER_GEAR_3") }) { Text("3档", fontSize = 11.sp) }
                    OutlinedButton(onClick = { sendControl("disconnect") }) { Text("断开", fontSize = 11.sp) }
                    OutlinedButton(onClick = { sendControl("cancel_scan") }) { Text("取消扫描", fontSize = 11.sp) }
                }
            }
        }

        // TtpInfo 全量数据面板
        if (ttpEntry != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TtpInfo 数据 ${if (ttpExpanded) "▲" else "▼"}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { ttpExpanded = !ttpExpanded }
                        )
                        if (ttpExpanded) {
                            TextButton(onClick = {
                                val text = ttpFields.joinToString("\n") { "${it.first} = ${it.second}" }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("TtpInfo", text))
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("复制全部", fontSize = 12.sp)
                            }
                        }
                    }
                    AnimatedVisibility(visible = ttpExpanded) {
                        SelectionContainer {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .weight(1f, fill = false)
                            ) {
                                items(ttpFields) { (name, value) ->
                                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                                        Text(
                                            text = name,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(200.dp)
                                        )
                                        Text(
                                            text = value,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 日志列表
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "等待 hook 事件...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                items(logs.filter { it.tag != "TtpInfo" }) { entry ->
                    Text(
                        text = "[${timeFormat.format(Date(entry.timestamp))}] ${entry.tag} | ${entry.message}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
