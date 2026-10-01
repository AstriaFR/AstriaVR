package dev.astriavr.player

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

/** Metadata probing stays on background workers, never while binding a UI row. */
object VideoMetadata {
    data class Info(val bytes: Long, val mime: String, val durationMs: Long = 0,
        val width: Int = 0, val height: Int = 0, val frameRate: Float = 0f, val bitrate: Int = 0,
        val audioMime: String = "", val channels: Int = 0, val sampleRate: Int = 0,
        val rotation: Int = 0, val audioTracks: Int = 0)
    fun read(context: Context, uri: Uri): Info {
        var size = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
        if (size < 0) size = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                it.length.takeIf { length -> length >= 0 } ?: it.parcelFileDescriptor.statSize
            } ?: -1L
        }.getOrDefault(-1L)
        return runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                var video: MediaFormat? = null
                var audio: MediaFormat? = null
                var audioTracks = 0
                var durationUs = 0L
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    durationUs = maxOf(durationUs, runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L))
                    if (mime.startsWith("video/") && video == null) video = format
                    if (mime.startsWith("audio/")) { audioTracks++; if (audio == null) audio = format }
                }
                fun integer(format: MediaFormat?, key: String) = runCatching { format?.getInteger(key) ?: 0 }.getOrDefault(0)
                val fps = runCatching { video?.getFloat(MediaFormat.KEY_FRAME_RATE) ?: 0f }
                    .getOrElse { integer(video, MediaFormat.KEY_FRAME_RATE).toFloat() }
                Info(size, video?.getString(MediaFormat.KEY_MIME).orEmpty(), durationUs / 1000,
                    integer(video, MediaFormat.KEY_WIDTH), integer(video, MediaFormat.KEY_HEIGHT), fps,
                    integer(video, MediaFormat.KEY_BIT_RATE), audio?.getString(MediaFormat.KEY_MIME).orEmpty(),
                    integer(audio, MediaFormat.KEY_CHANNEL_COUNT), integer(audio, MediaFormat.KEY_SAMPLE_RATE),
                    integer(video, MediaFormat.KEY_ROTATION), audioTracks)
            } finally { extractor.release() }
        }.getOrElse { Info(size, "") }
    }

    fun describe(entry: PlaylistStore.Entry, info: Info? = null): String {
        val bytes = info?.bytes?.takeIf { it >= 0 } ?: entry.fileSize
        val duration = info?.durationMs?.takeIf { it > 0 } ?: entry.duration
        return buildString {
            append(AppText.FILE_SIZE_N.text(size(bytes)))
            append(AppText.DURATION_N.text(if (duration > 0) UiStyle.duration(duration) else AppText.UNKNOWN.text()))
            append(AppText.PLAYBACK_POSITION_N.text(UiStyle.duration(entry.position)))
            append(AppText.VIDEO_CODEC_N.text(codec(info?.mime?.takeIf { it.isNotEmpty() } ?: entry.videoMime)))
            if (info == null) append(AppText.LOADING_RESOLUTION_FRAME_RATE_AND_AUDIO.text())
            else {
                append(AppText.RESOLUTION_N.text(if (info.width > 0 && info.height > 0) "${info.width} × ${info.height}" else AppText.UNKNOWN.text()))
                append(AppText.FRAME_RATE_N.text(if (info.frameRate.isFinite() && info.frameRate > 0) String.format(Locale.ROOT, "%.2f fps", info.frameRate) else AppText.NOT_PROVIDED.text()))
                if (info.bitrate > 0) append(String.format(Locale.ROOT, AppText.NOMINAL_VIDEO_BITRATE_F_MBPS_N.text(), info.bitrate / 1_000_000.0))
                else if (bytes > 0 && duration > 0) append(String.format(Locale.ROOT, AppText.AVERAGE_FILE_BITRATE_INCLUDING_AUDIO_F.text(), bytes * .008 / duration))
                if (info.rotation != 0) append(AppText.ROTATION_N.text(info.rotation))
                append(AppText.AUDIO_N.text(when (info.audioMime) { "audio/mp4a-latm" -> "AAC"; "" -> AppText.NO_AUDIO_TRACK_DETECTED.text(); else -> info.audioMime.substringAfter('/').uppercase(Locale.ROOT) }))
                if (info.audioTracks > 0) append(AppText.AUDIO_TRACKS_N.text(info.audioTracks))
                if (info.channels > 0) append(AppText.CHANNELS_N.text(info.channels))
                if (info.sampleRate > 0) append(AppText.SAMPLE_RATE_HZ_N.text(info.sampleRate))
            }
            append(AppText.FILE_LOCATION.text(entry.uri))
        }
    }
    fun codec(mime: String) = when (mime.lowercase(Locale.ROOT)) {
        "video/avc" -> "H.264 / AVC"
        "video/hevc" -> "H.265 / HEVC"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/x-vnd.on2.vp8" -> "VP8"
        "video/mp4v-es" -> "MPEG-4"
        "video/3gpp" -> "H.263"
        "video/dolby-vision" -> "Dolby Vision"
        "" -> AppText.UNKNOWN_CODEC.text()
        else -> mime.substringAfter('/').uppercase(Locale.ROOT)
    }
    fun size(bytes: Long): String {
        if (bytes < 0) return AppText.UNKNOWN_SIZE.text()
        if (bytes < 1024) return "${bytes} B"
        val units = arrayOf("KiB", "MiB", "GiB", "TiB")
        var value = bytes / 1024.0; var unit = 0
        while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
        return String.format(Locale.ROOT, if (value >= 100) "%.0f %s" else "%.2f %s", value, units[unit])
    }
}
