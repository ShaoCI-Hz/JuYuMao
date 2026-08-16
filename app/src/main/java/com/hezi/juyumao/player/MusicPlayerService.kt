package com.hezi.juyumao.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import com.hezi.juyumao.MainActivity
import com.hezi.juyumao.data.local.db.dao.SongDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlayerService : MediaSessionService() {

    @Inject
    lateinit var exoPlayer: ExoPlayer

    @Inject
    lateinit var playbackStateHolder: PlaybackStateHolder

    @Inject
    lateinit var songDao: SongDao

    @Inject
    lateinit var playbackController: PlaybackController

    private var mediaSession: MediaSession? = null
    private var notificationManager: PlayerNotificationManager? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "juyumao_media_playback"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_FAVORITE = "com.hezi.juyumao.FAVORITE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 通知栏收藏快捷（P1-12）：Media3 1.5.1 的 CustomActionReceiver API 与当前解析版本不兼容，暂不做通知栏收藏按钮
        // 小米媒体卡片/妙播接入：SessionActivity（点击卡片跳 App）；媒体按键由 Manifest 注册的 MediaButtonReceiver 自动路由
        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivity)
            .setCallback(object : MediaSession.Callback {
                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int,
                ): Int {
                    // 单曲队列（mediaItemCount<=1）：通知栏/媒体键的上一首/下一首
                    // 转发到 PlaybackController，与 App 内按钮行为一致（否则 ExoPlayer 无下一首无响应）
                    if (playerCommand == Player.COMMAND_SEEK_TO_NEXT && exoPlayer.mediaItemCount <= 1) {
                        playbackController.next()
                        return androidx.media3.session.SessionResult.RESULT_SUCCESS
                    }
                    if (playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS && exoPlayer.mediaItemCount <= 1) {
                        playbackController.previous()
                        return androidx.media3.session.SessionResult.RESULT_SUCCESS
                    }
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            })
            .build()

        // 使用 AlwaysActionsNotificationManager：始终显示 上一首/播放暂停/下一首 三个按钮
        // （Media3 默认单曲播放时隐藏下一首，见 getActions 覆写）
        notificationManager = AlwaysActionsNotificationManager(
            this,
            CHANNEL_ID,
            NOTIFICATION_ID,
            mediaDescriptionAdapter = object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence {
                    return player.mediaMetadata.title ?: "局域猫播放器"
                }

                override fun createCurrentContentIntent(player: Player): PendingIntent? {
                    val intent = Intent(this@MusicPlayerService, MainActivity::class.java)
                    return PendingIntent.getActivity(
                        this@MusicPlayerService, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                }

                override fun getCurrentContentText(player: Player): CharSequence? {
                    // 艺术家 + 当前歌词行（P1-12）
                    val artist = player.mediaMetadata.artist ?: "未知艺术家"
                    val line = playbackStateHolder.lyricsLine.value
                    return if (line != null) "$artist · $line" else artist
                }

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback,
                ): android.graphics.Bitmap? {
                    // 从播放状态读取封面路径（PlaybackStateHolder.artworkUri）
                    val artPath = playbackStateHolder.artworkUri.value ?: return null
                    return try {
                        // 采样解码：限制到 ~512px，避免全尺寸大图 OOM
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(artPath, bounds)
                        var sample = 1
                        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) {
                            sample *= 2
                        }
                        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                        android.graphics.BitmapFactory.decodeFile(artPath, opts)
                    } catch (_: Exception) {
                        null
                    }
                }
            },
            notificationListener = object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
                    if (ongoing) {
                        startForeground(notificationId, notification)
                    }
                }
                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    stopSelf()
                }
            },
        )

        notificationManager?.apply {
            setPlayer(exoPlayer)
            setSmallIcon(com.hezi.juyumao.R.mipmap.ic_launcher)
            setUseFastForwardAction(false)
            setUseRewindAction(false)
            setUseNextAction(true)
            setUsePreviousAction(true)
            // 紧凑视图（通知栏收起态）显示 上一首/播放暂停/下一首 三个按钮
            setUseNextActionInCompactView(true)
            setUsePreviousActionInCompactView(true)
            // 关键：把 MediaSession token 关联到通知（MediaStyle.setMediaSession），
            // 小米/系统才会把通知渲染为"音乐卡片"（大封面/进度/切歌/妙播）样式，
            // 否则只是普通媒体通知外观（真机复现：通知在但非音乐卡片）
            setMediaSessionToken(mediaSession!!.platformToken)
        }

        // 定时刷新通知（仅歌词非空时，P1-12）
        scope.launch {
            while (true) {
                delay(5000)
                if (playbackStateHolder.lyricsLine.value != null) {
                    try { notificationManager?.invalidate() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "音乐播放",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "局域猫播放器音乐播放控制"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // 仅在未播放时停止；播放中保留前台服务继续播放
        if (player == null || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        notificationManager?.setPlayer(null)
        // 不释放 ExoPlayer（由 Hilt 单例管理生命周期），只释放 mediaSession
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
