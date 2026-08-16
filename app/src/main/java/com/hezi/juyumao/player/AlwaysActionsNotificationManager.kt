package com.hezi.juyumao.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.ui.PlayerNotificationManager

/**
 * 始终显示完整媒体控制按钮的 PlayerNotificationManager。
 *
 * Media3 默认按 `player.isCommandAvailable(COMMAND_SEEK_TO_NEXT/PREVIOUS)` 决定是否显示
 * 上一首/下一首按钮——单曲播放（队列仅 1 首）时下一首按钮会被隐藏。
 * 覆写 [getActions] 强制始终返回 上一首/播放暂停/下一首 三个按钮（行为由
 * MediaSession.onPlayerCommandRequest 转发到 PlaybackController，见 MusicPlayerService）。
 */
class AlwaysActionsNotificationManager(
    context: Context,
    channelId: String,
    notificationId: Int,
    mediaDescriptionAdapter: PlayerNotificationManager.MediaDescriptionAdapter,
    notificationListener: PlayerNotificationManager.NotificationListener? = null,
) : PlayerNotificationManager(
    context,
    channelId,
    notificationId,
    mediaDescriptionAdapter,
    notificationListener,
    null,
    androidx.media3.ui.R.drawable.exo_notification_small_icon,
    androidx.media3.ui.R.drawable.exo_notification_play,
    androidx.media3.ui.R.drawable.exo_notification_pause,
    androidx.media3.ui.R.drawable.exo_notification_stop,
    androidx.media3.ui.R.drawable.exo_notification_rewind,
    androidx.media3.ui.R.drawable.exo_notification_fastforward,
    androidx.media3.ui.R.drawable.exo_notification_previous,
    androidx.media3.ui.R.drawable.exo_notification_next,
    null,
) {
    override fun getActions(player: Player): List<String> {
        return listOf(
            PlayerNotificationManager.ACTION_PREVIOUS,
            if (player.isPlaying) PlayerNotificationManager.ACTION_PAUSE
            else PlayerNotificationManager.ACTION_PLAY,
            PlayerNotificationManager.ACTION_NEXT,
        )
    }
}
