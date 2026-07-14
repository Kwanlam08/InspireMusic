package com.inspiremusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.inspiremusic.MainActivity
import com.inspiremusic.R
import com.inspiremusic.model.AudioItem
import com.inspiremusic.service.MusicPlaybackService
import com.google.common.util.concurrent.MoreExecutors
import kotlin.math.max

class NowPlayingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            manager.updateAppWidget(id, NowPlayingWidgetUpdater.buildRemoteViews(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NowPlayingWidgetUpdater.ACTION_PLAY_PAUSE,
            NowPlayingWidgetUpdater.ACTION_NEXT -> {
                val pendingResult = goAsync()
                NowPlayingWidgetUpdater.dispatchMediaAction(context, intent.action.orEmpty()) {
                    pendingResult.finish()
                }
            }
            else -> super.onReceive(context, intent)
        }
    }
}

object NowPlayingWidgetUpdater {
    const val ACTION_PLAY_PAUSE = "com.inspiremusic.widget.PLAY_PAUSE"
    const val ACTION_NEXT = "com.inspiremusic.widget.NEXT"

    private const val PREFS = "now_playing_widget"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_ALBUM_ART = "album_art"
    private const val KEY_IS_PLAYING = "is_playing"
    private const val KEY_POSITION = "position"
    private const val KEY_DURATION = "duration"

    fun update(
        context: Context,
        song: AudioItem?,
        isPlaying: Boolean,
        positionMs: Long
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TITLE, song?.title.orEmpty())
            .putString(KEY_ARTIST, song?.artist.orEmpty())
            .putString(KEY_ALBUM_ART, song?.albumArtUri?.toString().orEmpty())
            .putBoolean(KEY_IS_PLAYING, isPlaying)
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .putLong(KEY_DURATION, song?.duration?.coerceAtLeast(0L) ?: 0L)
            .apply()
        updateAll(context)
    }

    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = manager.getAppWidgetIds(ComponentName(appContext, NowPlayingWidgetProvider::class.java))
        ids.forEach { id ->
            manager.updateAppWidget(id, buildRemoteViews(appContext))
        }
    }

    fun buildRemoteViews(context: Context): RemoteViews {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null)
            ?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.app_name)
        val artist = prefs.getString(KEY_ARTIST, null)
            ?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.np_not_playing)
        val artUri = prefs.getString(KEY_ALBUM_ART, null).orEmpty()
        val isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)
        val position = prefs.getLong(KEY_POSITION, 0L)
        val duration = prefs.getLong(KEY_DURATION, 0L)
        val progress = if (duration > 0L) {
            ((position.coerceIn(0L, duration).toFloat() / duration.toFloat()) * 1000f).toInt()
        } else {
            0
        }

        val albumBitmap = loadAlbumArt(appContext, artUri)
        return RemoteViews(appContext.packageName, R.layout.widget_now_playing).apply {
            setImageViewBitmap(R.id.widget_background, createBackgroundBitmap(albumBitmap))
            setImageViewBitmap(R.id.widget_album_art, createAlbumBitmap(albumBitmap))
            setTextViewText(R.id.widget_title, title)
            setTextViewText(R.id.widget_artist, artist)
            setProgressBar(R.id.widget_progress, 1000, progress, false)
            setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )
            setOnClickPendingIntent(R.id.widget_content, launchAppIntent(appContext))
            setOnClickPendingIntent(
                R.id.widget_play_pause,
                widgetActionIntent(appContext, ACTION_PLAY_PAUSE, 10)
            )
            setOnClickPendingIntent(
                R.id.widget_next,
                widgetActionIntent(appContext, ACTION_NEXT, 11)
            )
        }
    }

    fun dispatchMediaAction(context: Context, action: String, onFinished: () -> Unit) {
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, MusicPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            runCatching {
                val controller = future.get()
                when (action) {
                    ACTION_PLAY_PAUSE -> {
                        if (controller.isPlaying) controller.pause() else controller.play()
                    }
                    ACTION_NEXT -> controller.seekToNextMediaItem()
                }
                persistFromController(appContext, controller)
                controller.release()
            }
            updateAll(appContext)
            onFinished()
        }, MoreExecutors.directExecutor())
    }

    private fun persistFromController(context: Context, controller: MediaController) {
        val metadata = controller.mediaMetadata
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TITLE, metadata.title?.toString().orEmpty().ifBlank {
                prefs.getString(KEY_TITLE, "").orEmpty()
            })
            .putString(KEY_ARTIST, metadata.artist?.toString().orEmpty().ifBlank {
                prefs.getString(KEY_ARTIST, "").orEmpty()
            })
            .putString(KEY_ALBUM_ART, metadata.artworkUri?.toString().orEmpty().ifBlank {
                prefs.getString(KEY_ALBUM_ART, "").orEmpty()
            })
            .putBoolean(KEY_IS_PLAYING, controller.isPlaying)
            .putLong(KEY_POSITION, controller.currentPosition.coerceAtLeast(0L))
            .putLong(KEY_DURATION, controller.duration.takeIf { it > 0L } ?: prefs.getLong(KEY_DURATION, 0L))
            .apply()
    }

    private fun launchAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            20,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun widgetActionIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, NowPlayingWidgetProvider::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun loadAlbumArt(context: Context, uriString: String): Bitmap? {
        if (uriString.isBlank()) return null
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSize(512, 512)
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
    }

    private fun createAlbumBitmap(source: Bitmap?): Bitmap {
        val size = 160
        val radius = 34f
        return if (source != null) {
            roundedCenterCrop(source, size, size, radius)
        } else {
            createPlaceholderBitmap(size, radius)
        }
    }

    private fun createBackgroundBitmap(source: Bitmap?): Bitmap {
        val size = 520
        val radius = 72f
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val path = Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }
        canvas.clipPath(path)

        if (source != null) {
            // Retain a recognisable cover instead of a low-resolution blurred wallpaper.
            drawCenterCrop(canvas, source, bounds)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    size.toFloat(),
                    size.toFloat(),
                    intArrayOf(Color.rgb(19, 162, 178), Color.rgb(28, 48, 74), Color.rgb(7, 12, 22)),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(bounds, paint)
        }

        Paint(Paint.ANTI_ALIAS_FLAG).also { paint ->
            paint.shader = LinearGradient(
                0f,
                0f,
                0f,
                size.toFloat(),
                intArrayOf(Color.argb(44, 8, 14, 22), Color.argb(132, 7, 14, 22), Color.argb(202, 5, 10, 16)),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(bounds, paint)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).also { paint ->
            paint.color = Color.argb(20, 255, 255, 255)
            canvas.drawOval(RectF(-80f, -90f, 300f, 190f), paint)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).also { paint ->
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.argb(116, 255, 255, 255)
            canvas.drawRoundRect(bounds.insetCopy(2f), radius, radius, paint)
        }
        return bitmap
    }

    private fun createPlaceholderBitmap(size: Int, radius: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val path = Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }
        canvas.clipPath(path)
        Paint(Paint.ANTI_ALIAS_FLAG).also { paint ->
            paint.shader = LinearGradient(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                Color.rgb(25, 205, 220),
                Color.rgb(18, 24, 44),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(bounds, paint)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).also { paint ->
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            paint.textSize = size * 0.28f
            val y = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
            canvas.drawText("IM", size / 2f, y, paint)
        }
        return bitmap
    }

    private fun roundedCenterCrop(source: Bitmap, width: Int, height: Int, radius: Float): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(centerCropMatrix(source, rect))
        paint.shader = shader
        canvas.drawRoundRect(rect, radius, radius, paint)
        return output
    }

    private fun drawCenterCrop(canvas: Canvas, source: Bitmap, bounds: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val matrix = centerCropMatrix(source, bounds)
        canvas.drawBitmap(source, matrix, paint)
    }

    private fun centerCropMatrix(source: Bitmap, bounds: RectF): Matrix {
        val scale = max(bounds.width() / source.width.toFloat(), bounds.height() / source.height.toFloat())
        val dx = bounds.left + (bounds.width() - source.width * scale) / 2f
        val dy = bounds.top + (bounds.height() - source.height * scale) / 2f
        return Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
    }

    private fun RectF.insetCopy(inset: Float): RectF =
        RectF(left + inset, top + inset, right - inset, bottom - inset)
}
