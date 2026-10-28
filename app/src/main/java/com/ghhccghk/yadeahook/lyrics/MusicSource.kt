package com.ghhccghk.yadeahook.lyrics

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 当前歌词发布者（音乐软件）的展示信息。
 */
data class MusicSource(
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap?
)
