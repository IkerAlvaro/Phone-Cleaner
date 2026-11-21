package com.ikeralvaro.phonecleaner.feature.compress

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.net.toUri
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EncoderUtil
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.ikeralvaro.phonecleaner.feature.media.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.media3.common.MediaItem as Media3Item

data class VideoInfo(val width: Int, val height: Int, val fps: Float, val bitrate: Int, val durationMs: Long)

/** Transcodifica vídeos con Media3 Transformer a HEVC (o H.264 si el móvil no tiene codificador HEVC). */
@OptIn(UnstableApi::class)
class VideoCompressor(private val context: Context, private val saver: MediaSaver) {

    val supportsHevc: Boolean by lazy {
        try {
            EncoderUtil.getSupportedEncoders(MimeTypes.VIDEO_H265).isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    fun readInfo(item: MediaItem): VideoInfo? = try {
        MediaMetadataRetriever().run {
            try {
                setDataSource(context, item.uri.toUri())
                fun int(key: Int) = extractMetadata(key)?.toIntOrNull()
                val duration = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: item.durationMs
                val frames = if (Build.VERSION.SDK_INT >= 28) int(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT) else null
                val fps = extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
                    ?: if (frames != null && duration > 0) frames * 1000f / duration else 30f
                VideoInfo(
                    width = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: item.width,
                    height = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: item.height,
                    fps = fps,
                    bitrate = int(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?: CompressionPlanner.bitrateFromSize(item.sizeBytes, duration),
                    durationMs = duration,
                )
            } finally {
                release()
            }
        }
    } catch (_: Exception) {
        null
    }

    suspend fun compress(item: MediaItem, mode: CompressionMode, onProgress: (Float) -> Unit): CompressionOutcome {
        val info = withContext(Dispatchers.IO) { readInfo(item) } ?: return CompressionOutcome.Failed("metadata")
        val hevc = supportsHevc
        val bitrate = CompressionPlanner.videoTargetBitrate(info.width, info.height, info.fps, info.bitrate, hevc, mode)
            ?: return CompressionOutcome.NotWorthIt
        val temp = File(context.cacheDir, "compress/${item.id}.mp4").apply { parentFile?.mkdirs(); delete() }
        try {
            val error = transcode(item.uri.toUri(), temp, bitrate, hevc, onProgress)
            if (error != null) return CompressionOutcome.Failed(error)
            if (!CompressionPlanner.isWorthIt(item.sizeBytes, temp.length(), CompressionPlanner.VIDEO_MIN_SAVINGS)) {
                return CompressionOutcome.NotWorthIt
            }
            val newUri = withContext(Dispatchers.IO) { saver.save(temp, item, "video/mp4", isVideo = true) }
                ?: return CompressionOutcome.Failed("save")
            return CompressionOutcome.Saved(item.sizeBytes - temp.length(), newUri)
        } finally {
            temp.delete()
        }
    }

    /** Devuelve null si ha ido bien o un código de error. Transformer debe usarse desde el hilo principal. */
    private suspend fun transcode(input: android.net.Uri, output: File, bitrate: Int, hevc: Boolean, onProgress: (Float) -> Unit): String? =
        withContext(Dispatchers.Main) {
            val done = CompletableDeferred<String?>()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(if (hevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                        .setEnableFallback(true)
                        .build(),
                )
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        done.complete(null)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        done.complete(exportException.errorCodeName)
                    }
                })
                .build()
            transformer.start(EditedMediaItem.Builder(Media3Item.fromUri(input)).build(), output.path)
            try {
                coroutineScope {
                    val poller = launch {
                        val holder = ProgressHolder()
                        while (isActive) {
                            delay(500)
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress(holder.progress / 100f)
                            }
                        }
                    }
                    val result = done.await()
                    poller.cancel()
                    result
                }
            } catch (e: CancellationException) {
                transformer.cancel()
                throw e
            }
        }
}
