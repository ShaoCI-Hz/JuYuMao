package com.hezi.juyumao.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.hezi.juyumao.MainActivity
import com.hezi.juyumao.player.PlaybackController
import com.hezi.juyumao.player.PlaybackStateHolder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Hilt EntryPoint：GlanceAppWidget 无法 @Inject，通过它访问播放状态 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackEntryPoint {
    fun holder(): PlaybackStateHolder
    fun controller(): PlaybackController
}

class JuYuMaoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent(context)
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        val backgroundColor = ColorProvider(Color(0xFF121212))
        val primaryColor = ColorProvider(Color(0xFF4A80F7))
        val textColor = ColorProvider(Color.White)
        val subtextColor = ColorProvider(Color(0xFFB3B3B3))

        // 读取当前播放状态（P1-13）
        var title = "局域猫播放器"
        var subtitle = "点击打开"
        var isPlaying = false
        try {
            val entry = EntryPointAccessors.fromApplication(context, PlaybackEntryPoint::class.java)
            val song = entry.holder().currentSong.value
            if (song != null) {
                title = song.title
                subtitle = song.artist
                val line = entry.holder().lyricsLine.value
                if (!line.isNullOrBlank()) subtitle = "$subtitle · $line"
            }
            isPlaying = entry.holder().isPlaying.value
        } catch (_: Exception) {}

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 专辑封面占位
                Box(
                    modifier = GlanceModifier
                        .size(48.dp)
                        .background(primaryColor)
                        .cornerRadius(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "♪",
                        style = TextStyle(
                            color = ColorProvider(Color.Black),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                Spacer(modifier = GlanceModifier.width(12.dp))

                // 歌曲信息 + 歌词
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                ) {
                    Text(
                        text = title,
                        style = TextStyle(
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            color = subtextColor,
                            fontSize = 12.sp,
                        ),
                        maxLines = 1,
                    )
                }

                // 播放/暂停按钮（P1-13）
                Box(
                    modifier = GlanceModifier
                        .size(40.dp)
                        .background(primaryColor)
                        .cornerRadius(20.dp)
                        .clickable(actionRunCallback<PlayPauseAction>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isPlaying) "⏸" else "▶",
                        style = TextStyle(
                            color = ColorProvider(Color.Black),
                            fontSize = 18.sp,
                        ),
                    )
                }
            }
        }
    }
}

/** 播放/暂停动作（P1-13） */
class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        try {
            val entry = EntryPointAccessors.fromApplication(context, PlaybackEntryPoint::class.java)
            entry.controller().togglePlay()
        } catch (_: Exception) {}
        // 刷新 widget 状态
        try { JuYuMaoWidget().updateAll(context) } catch (_: Exception) {}
    }
}

class JuYuMaoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = JuYuMaoWidget()
}
