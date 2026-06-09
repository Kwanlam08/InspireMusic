package com.applemusic.clone.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.applemusic.clone.MainActivity

class MusicPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // ── 抗卡顿 LoadControl ──
        // 默认 50KB 缓冲对网络流没问题，但本地文件 + UI 频繁触发
        // (封面加载 / Coil decode) 时偶发消费主线程 → 音频 underrun。
        // 这里把缓冲提到 2 秒预读 + 30 秒最大缓冲，podcast 长时间不卡。
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs    */ 2_000,   // 至少先攒 2s 再开始播（消除前 0.5s 的卡顿感）
                /* maxBufferMs    */ 30_000,  // 最多攒 30s，避免长时间占内存
                /* bufferForPlaybackMs */ 1_000,
                /* bufferForPlaybackAfterRebufferMs */ 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(buildSessionActivityPendingIntent())
            .build()
    }

    /**
     * 通知栏 / 蓝牙耳机控件点击后回到 MainActivity。
     * 用 FLAG_IMMUTABLE 是 Android 12+ 的硬性要求。
     */
    private fun buildSessionActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * 用户从最近任务划掉 / 停止服务时：
     *  - 正在播放：不停止播放，不杀服务（保持后台音频 + 通知栏常驻）
     *  - 没在播放：杀服务
     *
     * 这样就实现了「底栏上方 MiniPlayer 删后台后永久常驻」 + 通知栏控件始终显示。
     */
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (mediaSession?.player?.playWhenReady == true) {
            // 正在播放：不调用 super.onTaskRemoved()，service 继续保活
            // 这样通知栏不会被清理、MiniPlayer 也永久常驻
        } else {
            stopSelf()
        }
    }

    /**
     * 当所有 controller 解绑时（用户从通知栏划掉通知 / 蓝牙断开）：
     *  - 正在播放：不停止服务，MiniPlayer 永久常驻
     *  - 没在播放：调用 super 让 Media3 走正常清理流程
     */
    override fun onDestroy() {
        // 播放中禁止清理 session，保持后台运行
        if (mediaSession?.player?.playWhenReady == true) {
            // 保留 mediaSession 不释放（用户后续想停止时显式调用 stop）
        } else {
            mediaSession?.run {
                player.release()
                release()
                mediaSession = null
            }
        }
        super.onDestroy()
    }
}
