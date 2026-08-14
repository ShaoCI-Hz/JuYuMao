package com.hezi.juyumao.ui.lyrics

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import androidx.core.content.FileProvider
import com.hezi.juyumao.player.audio.LyricsData
import com.hezi.juyumao.player.audio.LrcParser
import java.io.File
import java.io.FileOutputStream

/**
 * 歌词海报生成与分享（P0-2）：
 * 深色渐变背景 + 封面 + 当前歌词几行 + 歌名/歌手，生成 PNG 后走系统分享。
 */
object LyricPosterShare {

    private const val W = 1080
    private const val H = 1920

    /**
     * 生成歌词海报文件（cacheDir/posters/）。
     * @param accent 主题强调色（ARGB）
     */
    fun createPoster(
        context: Context,
        title: String,
        artist: String,
        albumArtPath: String?,
        lyricsData: LyricsData?,
        positionMs: Long,
        accent: Int = 0xFF4A80F7.toInt(),
    ): File? = try {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 深色渐变背景
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                darken(accent, 0.15f), 0xFF101014.toInt(),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bgPaint)

        // 封面（圆角裁剪用简单缩放）
        var artTop = 260
        val artPaint = Paint().apply { isAntiAlias = true }
        var artBitmap: Bitmap? = null
        if (!albumArtPath.isNullOrEmpty()) {
            artBitmap = try {
                BitmapFactory.decodeFile(albumArtPath)?.let {
                    Bitmap.createScaledBitmap(it, 520, 520, true)
                }
            } catch (_: Exception) { null }
        }
        if (artBitmap != null) {
            canvas.drawBitmap(artBitmap, (W - 520) / 2f, artTop.toFloat(), artPaint)
            artTop += 620
        } else {
            // 无封面：画音符占位（圆形 + ♪）
            val circlePaint = Paint().apply {
                isAntiAlias = true
                color = accent
                alpha = 40
            }
            canvas.drawCircle(W / 2f, (artTop + 260).toFloat(), 260f, circlePaint)
            val notePaint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
                textSize = 200f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("♪", W / 2f, artTop + 340f, notePaint)
            artTop += 620
        }

        // 歌词（当前行上下各 2 行）
        val lyricPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 58f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val subPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            alpha = 160
            textSize = 48f
            textAlign = Paint.Align.CENTER
        }
        val lines = lyricsData?.lines.orEmpty()
        val idx = if (lines.isEmpty()) -1 else LrcParser.findCurrentLineIndex(lines, positionMs)
        val start = (idx - 2).coerceAtLeast(0)
        val end = (idx + 3).coerceAtMost(lines.size - 1)
        if (lines.isNotEmpty() && idx >= 0) {
            var y = artTop + 200f
            for (i in start..end) {
                val isCurrent = i == idx
                canvas.drawText(
                    lines[i].text,
                    W / 2f,
                    y,
                    if (isCurrent) lyricPaint else subPaint,
                )
                y += 110f
            }
        }

        // 歌名 / 歌手
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 76f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val artistPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            alpha = 190
            textSize = 46f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title, W / 2f, H - 260f, titlePaint)
        canvas.drawText(artist, W / 2f, H - 180f, artistPaint)

        val dir = File(context.cacheDir, "posters").apply { mkdirs() }
        val file = File(dir, "lyric_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        file
    } catch (_: Exception) {
        null
    }

    /** 通过系统分享面板分享海报 */
    fun share(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享歌词海报"))
        } catch (_: Exception) {
        }
    }

    private fun darken(color: Int, factor: Float): Int {
        val r = (Color.red(color) * (1 - factor)).toInt()
        val g = (Color.green(color) * (1 - factor)).toInt()
        val b = (Color.blue(color) * (1 - factor)).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}
