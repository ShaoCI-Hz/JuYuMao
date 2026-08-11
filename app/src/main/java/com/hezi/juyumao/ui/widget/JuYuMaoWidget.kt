package com.hezi.juyumao.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.hezi.juyumao.MainActivity

class JuYuMaoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }

    @Composable
    private fun WidgetContent() {
        val backgroundColor = ColorProvider(Color(0xFF121212))
        val primaryColor = ColorProvider(Color(0xFF1ED760))
        val textColor = ColorProvider(Color.White)
        val subtextColor = ColorProvider(Color(0xFFB3B3B3))

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
                // Album art placeholder
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

                // Song info
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                ) {
                    Text(
                        text = "局域猫播放器",
                        style = TextStyle(
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = "点击打开",
                        style = TextStyle(
                            color = subtextColor,
                            fontSize = 12.sp,
                        ),
                        maxLines = 1,
                    )
                }

                // Play button：当前为静态占位（未接入播放状态源），点击打开 App；
                // 播放/暂停控制需接入播放服务状态后实现（updatePeriodMillis=0 不自动刷新）
                Box(
                    modifier = GlanceModifier
                        .size(40.dp)
                        .background(primaryColor)
                        .cornerRadius(20.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "▶",
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

class JuYuMaoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = JuYuMaoWidget()
}
