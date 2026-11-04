package com.tbm.iptv.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.tbm.iptv.data.Http
import com.tbm.iptv.data.Prefs
import java.io.File

class SubTrack(val file: File, val mime: String, val lang: String, val label: String)

object PlayerFactory {
    fun create(ctx: Context): ExoPlayer {
        // לקוח לזרימה: זמן קריאה קצר – אחרי מעבר Wi-Fi⇄סלולר חיבור מת מתגלה תוך שניות ולא 90 שניות
        val streamClient = Http.client.newBuilder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val http = OkHttpDataSource.Factory(streamClient).setUserAgent(Prefs.userAgent)
        // DefaultDataSource: קבצים מקומיים (הקלטות, כתוביות) + http דרך OkHttp
        val ds = DefaultDataSource.Factory(ctx, http)
        val renderers = DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
        // באפר לפי ההגדרה: (מינימום, מקסימום, להתחלת ניגון, אחרי עצירה) בשניות + באפר אחורה
        val (minB, maxB, startB, reB) = when (Prefs.str("buffer_size", "normal")) {
            "small" -> listOf(15, 60, 2, 4)
            "large" -> listOf(60, 240, 3, 8)
            "max" -> listOf(120, 600, 5, 12)
            else -> listOf(40, 150, 2, 6)
        }
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minB * 1000, maxB * 1000, startB * 1000, reB * 1000)
            .setBackBuffer(30_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        return ExoPlayer.Builder(ctx, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ds))
            .setLoadControl(load)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    true,
                )
                // כתוביות מובנות: עברית קודם, אחריה אנגלית
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredTextLanguages("he", "iw", "en").build()
                val lang = Prefs.audioLang
                if (lang.isNotBlank()) {
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setPreferredAudioLanguage(lang).build()
                }
            }
    }

    fun mimeFor(url: String): String? {
        val u = url.lowercase().substringBefore('?')
        return when {
            u.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            u.endsWith(".mp4") || u.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
            u.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            u.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
            u.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
            else -> null
        }
    }

    fun mediaItem(url: String, title: String, sub: SubTrack? = null): MediaItem {
        val b = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        if (url.contains(".m3u8", true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        if (sub != null) {
            b.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(sub.file))
                        .setMimeType(sub.mime)
                        .setLanguage(sub.lang)
                        .setLabel(sub.label)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                        .build()
                )
            )
        }
        return b.build()
    }

    /** פריט ל-Chromecast – מחייב סוג MIME. ערוץ Xtream חי עובר ל-HLS כי Chromecast לא מנגן TS */
    fun castItem(url: String, title: String): MediaItem {
        var u = url
        if (u.contains("/live/") && u.endsWith(".ts")) u = u.removeSuffix(".ts") + ".m3u8"
        return MediaItem.Builder()
            .setUri(u)
            .setMimeType(mimeFor(u) ?: MimeTypes.VIDEO_MP4)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
    }
}

/** נגן משותף – ממשיך לנגן בחלון צף בתוך האפליקציה (In-App PiP) אחרי יציאה מהנגן */
object PlayerHolder {
    private var player: ExoPlayer? = null

    var miniActive by mutableStateOf(false)
    var miniTitle by mutableStateOf("")

    fun get(ctx: Context): ExoPlayer =
        player ?: PlayerFactory.create(ctx.applicationContext).also { player = it }

    fun current(): ExoPlayer? = player

    fun release() {
        miniActive = false
        player?.release()
        player = null
    }
}
