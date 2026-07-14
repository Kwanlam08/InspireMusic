package com.inspiremusic.service

import android.app.PendingIntent
import android.content.Intent
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.inspiremusic.MainActivity
import com.inspiremusic.data.DiagnosticLogger
import com.inspiremusic.settings.AppSettingsKeys
import kotlin.math.pow

class MusicPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val settingsPrefs by lazy { getSharedPreferences(AppSettingsKeys.PREFS_NAME, MODE_PRIVATE) }
    private var crossfadeRunning = false
    private var activeFade: ValueAnimator? = null
    private var replayGainFactor = 1f
    private val playbackMonitor = object : Runnable {
        override fun run() {
            val player = mediaSession?.player
            var nextCheckMs = 1_000L
            if (player != null) {
                val seconds = settingsPrefs.getInt(AppSettingsKeys.CROSSFADE_SECONDS, 0).coerceIn(0, 12)
                val remaining = player.duration - player.currentPosition
                if (!crossfadeRunning && seconds > 0 && player.isPlaying && player.repeatMode != Player.REPEAT_MODE_ONE &&
                    player.hasNextMediaItem() && player.duration > 0L && remaining in 1..seconds * 1000L
                ) startCrossfade(player, seconds)
                nextCheckMs = when {
                    crossfadeRunning -> 500L
                    seconds <= 0 || !player.isPlaying || player.duration <= 0L -> 1_500L
                    remaining <= (seconds + 2L) * 1_000L -> 100L
                    remaining <= (seconds + 10L) * 1_000L -> 500L
                    else -> 2_500L
                }
            }
            handler.postDelayed(this, nextCheckMs)
        }
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        DiagnosticLogger.initialize(applicationContext)
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
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                DiagnosticLogger.log("playback", "player error code=${error.errorCode}", error)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                replayGainFactor = 1f
                DiagnosticLogger.log("playback", "transition id=${mediaItem?.mediaId} reason=$reason")
                if (!crossfadeRunning) applyBaseVolume(player)
            }

            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                val gainText = (0 until metadata.length()).asSequence().mapNotNull { index ->
                    val entry = metadata[index]
                    val key = reflectedString(entry, "key") ?: reflectedString(entry, "description")
                    val value = reflectedString(entry, "value")
                    if (key?.contains("REPLAYGAIN_TRACK_GAIN", ignoreCase = true) == true) value else null
                }.firstOrNull() ?: return
                val db = Regex("[-+]?\\d+(?:\\.\\d+)?").find(gainText)?.value?.toFloatOrNull() ?: return
                replayGainFactor = 10.0.pow(db / 20.0).toFloat().coerceIn(0.25f, 1.5f)
                if (!crossfadeRunning) applyBaseVolume(player)
                DiagnosticLogger.log("playback", "ReplayGain ${db}dB applied")
            }
        })
        applyBaseVolume(player)

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(buildSessionActivityPendingIntent())
            .build()
        handler.post(playbackMonitor)
    }

    private fun baseVolume(): Float {
        val enabled = settingsPrefs.getBoolean(AppSettingsKeys.REPLAY_GAIN_ENABLED, false)
        return if (enabled) (0.92f * replayGainFactor).coerceIn(0.20f, 1f) else 1f
    }

    private fun reflectedString(value: Any, fieldName: String): String? = runCatching {
        value.javaClass.getField(fieldName).get(value)?.toString()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun applyBaseVolume(player: Player) {
        player.volume = baseVolume()
    }

    private fun startCrossfade(player: Player, seconds: Int) {
        crossfadeRunning = true
        val duration = seconds * 1000L
        activeFade?.cancel()
        activeFade = ValueAnimator.ofFloat(player.volume, 0f).apply {
            this.duration = duration
            addUpdateListener { player.volume = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                    crossfadeRunning = false
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (cancelled) return
                    if (!player.hasNextMediaItem()) {
                        crossfadeRunning = false
                        applyBaseVolume(player)
                        return
                    }
                    player.seekToNextMediaItem()
                    val base = baseVolume()
                    player.volume = 0f
                    activeFade = ValueAnimator.ofFloat(0f, base).apply {
                        this.duration = duration
                        addUpdateListener { player.volume = it.animatedValue as Float }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationCancel(animation: android.animation.Animator) {
                                crossfadeRunning = false
                            }
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                player.volume = base
                                crossfadeRunning = false
                            }
                        })
                        start()
                    }
                }
            })
            start()
        }
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
        handler.removeCallbacks(playbackMonitor)
        activeFade?.cancel()
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
