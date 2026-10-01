package dev.astriavr.player

import android.content.Context
import android.media.MediaCrypto
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

/** Observe compressed input to the existing platform video decoder, without a second decoder. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class BitrateRenderersFactory(context: Context, private val meter: VideoBitrateMeter,
    private val forceSdr: Boolean = false, private val onSdrUnavailable: () -> Unit = {}) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(context: Context, extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector, enableDecoderFallback: Boolean,
        eventHandler: Handler, eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long, out: ArrayList<Renderer>) {
        // This build ships only platform codecs (no extension renderers).
        val builder = MediaCodecVideoRenderer.Builder(context)
            .setCodecAdapterFactory(getCodecAdapterFactory())
            .setMediaCodecSelector(mediaCodecSelector)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
            .setEventHandler(eventHandler).setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
        out.add(object : MediaCodecVideoRenderer(builder) {
            private var startUs = Long.MIN_VALUE
            private var requestedSdr = false
            private var reportedUnsupported = false
            override fun getMediaCodecConfiguration(codecInfo: MediaCodecInfo, format: Format,
                crypto: MediaCrypto?, codecOperatingRate: Float): MediaCodecAdapter.Configuration {
                val configuration = super.getMediaCodecConfiguration(codecInfo, format, crypto, codecOperatingRate)
                val transfer = format.colorInfo?.colorTransfer
                requestedSdr = forceSdr && Build.VERSION.SDK_INT >= 31 &&
                    (transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG || format.sampleMimeType == "video/dolby-vision")
                reportedUnsupported = false
                if (requestedSdr && Build.VERSION.SDK_INT >= 31)
                    configuration.mediaFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                return configuration
            }
            override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
                super.onOutputFormatChanged(format, mediaFormat)
                if (requestedSdr && !reportedUnsupported && mediaFormat != null) {
                    val transfer = if (mediaFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER))
                        mediaFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else 0
                    // A decoder can silently ignore the request. Never leave a false "HDR off" state.
                    if (transfer != MediaFormat.COLOR_TRANSFER_SDR_VIDEO) {
                        reportedUnsupported = true
                        eventHandler.post(onSdrUnavailable)
                    }
                }
            }
            override fun onQueueInputBuffer(buffer: DecoderInputBuffer) {
                // Media3 has flipped the buffer before this callback; limit is the queued byte size.
                if (!buffer.isEndOfStream && buffer.timeUs >= startUs)
                    meter.addSample(buffer.timeUs, buffer.data?.limit() ?: 0)
                super.onQueueInputBuffer(buffer)
            }
            override fun onPositionReset(positionUs: Long, joining: Boolean, sampleStreamIsResetToKeyFrame: Boolean) {
                meter.reset(); startUs = positionUs
                super.onPositionReset(positionUs, joining, sampleStreamIsResetToKeyFrame)
            }
        })
    }
}
