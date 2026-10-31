package com.ghhccghk.yadeahook.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ghhccghk.yadeahook.settings.FloatSettingSpec
import com.ghhccghk.yadeahook.settings.OverlaySettingSpecs
import com.ghhccghk.yadeahook.settings.YadeaSettingsKeys
import io.github.lingqiqi5211.meowui.component.MeowPreferenceSection
import io.github.lingqiqi5211.meowui.core.MeowUiStyle
import io.github.lingqiqi5211.meowui.core.preference.PreferenceConnectionState
import io.github.lingqiqi5211.meowui.core.preference.PreferenceWriteResult
import io.github.lingqiqi5211.meowui.preference.rememberMeowPreferenceConnectionState
import io.github.lingqiqi5211.meowui.theme.MeowTheme
import io.github.lingqiqi5211.meowui.xposed.MeowXposedPreferenceHost

private sealed interface OverlayWriteStatus {
    data object Idle : OverlayWriteStatus
    data object Saved : OverlayWriteStatus
    data class Failed(val message: String) : OverlayWriteStatus
}

/**
 * First MeowUI-backed settings batch for the projected lyric overlay.
 *
 * Non-overlay settings on this page keep their existing storage. This section reports the
 * host's actual remote write result instead of treating a local UI change as saved.
 */
@Composable
fun LyricOverlaySettingsSection(modifier: Modifier = Modifier) {
    var writeStatus by remember { mutableStateOf<OverlayWriteStatus>(OverlayWriteStatus.Idle) }

    MeowXposedPreferenceHost(
        preferenceName = YadeaSettingsKeys.PREFERENCE_NAME,
        onWriteResult = { result ->
            writeStatus = when (result) {
                PreferenceWriteResult.Success -> OverlayWriteStatus.Saved
                is PreferenceWriteResult.NotConnected ->
                    OverlayWriteStatus.Failed("未连接远程设置，修改未保存")
                is PreferenceWriteResult.Failure ->
                    OverlayWriteStatus.Failed("保存失败，请检查 Xposed 服务连接")
            }
        },
    ) {
        val connectionState by rememberMeowPreferenceConnectionState()

        MeowTheme(
            style = MeowUiStyle.MaterialExpressive,
            darkTheme = isSystemInDarkTheme(),
            dynamicColor = true,
        ) {
            Column(modifier = modifier.fillMaxWidth()) {
                OverlayConnectionStatus(
                    connectionState = connectionState,
                    writeStatus = writeStatus,
                )
                Spacer(modifier = Modifier.height(12.dp))

                MeowPreferenceSection(
                    modifier = Modifier.fillMaxWidth(),
                    title = "投屏歌词显示",
                ) {
                    MeowSwitchPreference(
                        key = YadeaSettingsKeys.overlayEnabled,
                        title = "显示歌词叠加",
                        summary = "在投屏画面顶部显示实时歌词",
                    )
                    MeowSwitchPreference(
                        key = YadeaSettingsKeys.overlayShowSource,
                        title = "显示音乐来源",
                        summary = "显示当前音乐应用的图标和名称",
                    )
                    MeowSwitchPreference(
                        key = YadeaSettingsKeys.overlayShowTranslation,
                        title = "显示歌词翻译",
                        summary = "歌曲提供翻译时显示在歌词下方",
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                MeowPreferenceSection(
                    modifier = Modifier.fillMaxWidth(),
                    title = "投屏歌词布局与样式",
                ) {
                    MeowSliderPreference(
                        key = YadeaSettingsKeys.overlayDensity,
                        title = "画布密度",
                        summary = "同时影响 dp 和 sp 的基础缩放",
                        valueRange = OverlaySettingSpecs.density.range,
                        valueText = { value -> formatMultiplier(value) },
                    )
                    MeowSliderPreference(
                        key = YadeaSettingsKeys.overlayFontScale,
                        title = "字体缩放",
                        summary = "在画布密度基础上额外缩放文字",
                        valueRange = OverlaySettingSpecs.fontScale.range,
                        valueText = { value -> formatMultiplier(value) },
                    )
                    MeowSliderPreference(
                        key = YadeaSettingsKeys.overlayTop,
                        title = "顶部偏移",
                        summary = "歌词内容距画布顶部的距离",
                        valueRange = OverlaySettingSpecs.top.range,
                        valueText = ::formatDp,
                    )
                    MeowSliderPreference(
                        key = YadeaSettingsKeys.overlayStart,
                        title = "左侧偏移",
                        summary = "歌词内容距画布左侧的距离",
                        valueRange = OverlaySettingSpecs.start.range,
                        valueText = ::formatDp,
                    )
                    MeowSliderPreference(
                        key = YadeaSettingsKeys.overlayEnd,
                        title = "右侧偏移",
                        summary = "歌词内容距画布右侧的距离",
                        valueRange = OverlaySettingSpecs.end.range,
                        valueText = ::formatDp,
                    )
                    MeowSliderPreference(
                        key = YadeaSettingsKeys.overlayWidthRatio,
                        title = "内容宽度",
                        summary = "应用左右偏移后的可用宽度比例",
                        valueRange = OverlaySettingSpecs.widthRatio.range,
                        valueText = ::formatPercent,
                    )
                    MeowSliderPreference(
                        key = YadeaSettingsKeys.overlayBackgroundAlpha,
                        title = "背景透明度",
                        summary = "歌词内容区的背景透明度，0% 为透明",
                        valueRange = OverlaySettingSpecs.backgroundAlpha.range,
                        valueText = ::formatPercent,
                    )
                    MeowSliderPreference(
                        key = YadeaSettingsKeys.overlayFrameRate,
                        title = "歌词刷新率",
                        summary = "逐字歌词行内进度的更新上限，默认 30 FPS",
                        valueRange = OverlaySettingSpecs.frameRate.range,
                        valueText = ::formatFrameRate,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayConnectionStatus(
    connectionState: PreferenceConnectionState,
    writeStatus: OverlayWriteStatus,
) {
    val stateText = when (connectionState) {
        PreferenceConnectionState.Connecting -> "正在连接远程设置"
        PreferenceConnectionState.Connected -> "已连接，修改会自动保存"
        is PreferenceConnectionState.Disconnected -> "未连接，当前无法保存设置"
    }
    val stateColor = if (connectionState is PreferenceConnectionState.Disconnected) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val writeText = when (writeStatus) {
        OverlayWriteStatus.Idle -> null
        OverlayWriteStatus.Saved -> "最近一次修改已保存"
        is OverlayWriteStatus.Failed -> writeStatus.message
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "投屏歌词设置",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stateText,
            style = MaterialTheme.typography.bodySmall,
            color = stateColor,
        )
        writeText?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (writeStatus is OverlayWriteStatus.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

private val FloatSettingSpec.range
    get() = minimum..maximum

private fun formatMultiplier(value: Float): String = String.format("%.2f×", value)

private fun formatDp(value: Float): String = "${value.toInt()} dp"

private fun formatPercent(value: Float): String = "${(value * 100).toInt()}%"

private fun formatFrameRate(value: Float): String = "${value.toInt()} FPS"
