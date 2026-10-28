package com.ghhccghk.yadeahook.lyrics

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricWord
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLineText
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.SyncedLineText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 投屏导航顶部的歌词条。
 *
 * 外层由 ComposeView 渲染成 Bitmap，再合到导航并发送给仪表。
 */
@Composable
fun LyricOverlay(data: SuperLyricData?, source: MusicSource?) {
    val karaokeLine = remember(data) { data?.toKaraokeLine() }
    val syncedLine = remember(data) { data?.toSyncedLine() }

    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(
                    RoundedCornerShape(
                        bottomStart = 12.dp,
                        bottomEnd = 12.dp
                    )
                )
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(start = 300.dp, top = 48.dp, end = 24.dp, bottom = 5.dp)
        ) {
            if (source != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = source.icon
                    if (icon != null) {
                        Image(
                            bitmap = icon,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = source.appName,
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp)
                    )
                }
            }

            if (karaokeLine != null) {
                val playbackPosition = rememberPlaybackPosition(data, karaokeLine)
                CompositionLocalProvider(LocalTextStyle provides TranslationTextStyle) {
                    KaraokeLineText(
                        line = karaokeLine,
                        currentTimeProvider = { playbackPosition },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 1.dp),
                        normalLineTextStyle = LyricTextStyle,
                        accompanimentLineTextStyle = LyricTextStyle,
                        phoneticTextStyle = TranslationTextStyle,
                        activeColor = Color.White,
                        showTranslation = true,
                        showPhonetic = false
                    )
                }
            } else if (syncedLine != null) {
                // 没有逐字时间时使用库的普通同步行布局。
                CompositionLocalProvider(LocalTextStyle provides TranslationTextStyle) {
                    SyncedLineText(
                        line = syncedLine,
                        isLineRtl = false,
                        textStyle = LyricTextStyle,
                        textColor = Color.White,
                        modifier = Modifier.padding(top = 1.dp),
                        showTranslation = true
                    )
                }
            } else {
                Text(
                    text = "暂无歌词",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1
                )
            }
        }
    }
}

private val LyricTextStyle = TextStyle(
    color = Color.White,
    fontSize = 14.sp,
    lineHeight = 17.sp,
    fontWeight = FontWeight.Bold
)

private val TranslationTextStyle = TextStyle(
    color = Color.White.copy(alpha = 0.68f),
    fontSize = 9.sp,
    lineHeight = 11.sp
)

private data class PlaybackAnchor(
    val positionMs: Int,
    val elapsedRealtimeMs: Long,
    val endMs: Int
)

/**
 * SuperLyric 只在切换歌词行时下发增量包，行内进度由服务端时钟推进。
 * ComposeView 位于 Hook 进程，因此用本地位图绘制时钟按真实时间插值。
 */
@Composable
private fun rememberPlaybackPosition(
    data: SuperLyricData?,
    line: KaraokeLine
): Int {
    val anchor = remember(data, line) {
        val rawPosition = if (data != null && data.hasPosition()) {
            data.position
        } else {
            line.start.toLong()
        }
        PlaybackAnchor(
            positionMs = rawPosition.coerceAtLeast(0L).toInt(),
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            endMs = line.end
        )
    }

    val position by produceState(initialValue = anchor.positionMs, anchor) {
        if (anchor.positionMs >= anchor.endMs) {
            value = anchor.endMs
            return@produceState
        }

        while (isActive) {
            val elapsed = SystemClock.elapsedRealtime() - anchor.elapsedRealtimeMs
            val next = anchor.positionMs + elapsed.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            value = next.coerceAtMost(anchor.endMs)
            if (next >= anchor.endMs) break
            delay(33L)
        }
    }
    return position
}

/**
 * 将 SuperLyricApi 的逐字时间转换为 Accompanist Lyrics 的 KaraokeLine。
 * 返回 null 时由调用方回退到 SyncedLineText。
 */
private fun SuperLyricData.toKaraokeLine(): KaraokeLine? {
    val current = lyric ?: return null
    val words = current.words?.takeIf { it.isNotEmpty() } ?: return null
    val syllables = words.toKaraokeSyllables()
    if (syllables.isEmpty()) return null

    val lineStart = current.startTime.coerceAtLeast(0L).toInt()
    val lastSyllableEnd = syllables.maxOf { it.end }
    val lineEnd = current.endTime
        .takeIf { it >= lineStart }
        ?.toInt()
        ?.coerceAtLeast(lastSyllableEnd)
        ?: lastSyllableEnd

    return KaraokeLine.MainKaraokeLine(
        syllables = syllables,
        translation = current.translation?.takeIf { it.isNotBlank() }
            ?: translation?.text?.takeIf { it.isNotBlank() },
        alignment = KaraokeAlignment.Unspecified,
        start = lineStart,
        end = lineEnd.coerceAtLeast(lineStart),
        phonetic = current.secondary?.takeIf { it.isNotBlank() }
    )
}

private fun Array<SuperLyricWord>.toKaraokeSyllables(): List<KaraokeSyllable> {
    var previousEnd = 0
    return mapNotNull { word ->
        val content = word.word
        if (content.isEmpty()) return@mapNotNull null

        val start = word.startTime
            .coerceAtLeast(0L)
            .toInt()
            .coerceAtLeast(previousEnd)
        val end = word.endTime
            .coerceAtLeast(start.toLong())
            .toInt()
            .let { if (it > start) it else start + 1 }
        previousEnd = end

        KaraokeSyllable(
            content = content,
            start = start,
            end = end
        )
    }
}

/**
 * SuperLyricApi 3.x 的当前行转换为 Accompanist Lyrics 的 SyncedLine。
 */
private fun SuperLyricData.toSyncedLine(): SyncedLine? {
    val current = lyric ?: return null
    val text = current.text.takeIf { it.isNotBlank() } ?: return null
    val currentTranslation = current.translation?.takeIf { it.isNotBlank() }
    val dataTranslation = translation?.text?.takeIf { it.isNotBlank() }

    return SyncedLine(
        content = text,
        translation = currentTranslation ?: dataTranslation,
        start = current.startTime.toInt(),
        end = current.endTime.toInt()
    )
}
