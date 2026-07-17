package com.inspiremusic.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.inspiremusic.model.AudioItem
import com.inspiremusic.model.LrcLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object LyricCardExporter {
    suspend fun share(context: Context, song: AudioItem, line: LrcLine): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val artwork = loadArtwork(context, song.albumArtUri)
            val card = renderCard(song, line, artwork)
            val dir = File(context.cacheDir, "shared_lyrics").apply { mkdirs() }
            val target = File(dir, "lyric_${song.id}_${System.currentTimeMillis()}.png")
            FileOutputStream(target).use { card.compress(Bitmap.CompressFormat.PNG, 95, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "${line.text}\n— ${song.title} · ${song.artist}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, "分享歌词卡片").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private suspend fun loadArtwork(context: Context, uri: Uri?): Bitmap? {
        if (uri == null) return null
        val result = context.imageLoader.execute(ImageRequest.Builder(context).data(uri).size(720).allowHardware(false).build()) as? SuccessResult
            ?: return null
        return (result.drawable as? BitmapDrawable)?.bitmap ?: result.drawable.toBitmap()
    }

    private fun renderCard(song: AudioItem, line: LrcLine, artwork: Bitmap?): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), Color.rgb(34, 34, 40), Color.rgb(8, 8, 10), Shader.TileMode.CLAMP)
        })
        artwork?.let {
            canvas.drawBitmap(it, centerCropRect(it.width, it.height), Rect(110, 95, 970, 955), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.drawRoundRect(110f, 95f, 970f, 955f, 54f, 54f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(90, 255, 255, 255)
            })
        }
        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 62f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val lyricLayout = StaticLayout.Builder.obtain(line.text, 0, line.text.length, lyricPaint, 860)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(8f, 1f).setMaxLines(4).build()
        canvas.save(); canvas.translate(110f, 990f); lyricLayout.draw(canvas); canvas.restore()
        canvas.drawText("${song.title} · ${song.artist}", width / 2f, 1275f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255); textSize = 29f; textAlign = Paint.Align.CENTER
        })
        canvas.drawText("INSPIRE MUSIC", 970f, 1316f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 255, 255, 255); textSize = 22f; textAlign = Paint.Align.RIGHT; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        return bitmap
    }

    private fun centerCropRect(width: Int, height: Int): Rect {
        val side = minOf(width, height)
        val left = (width - side) / 2
        val top = (height - side) / 2
        return Rect(left, top, left + side, top + side)
    }
}
