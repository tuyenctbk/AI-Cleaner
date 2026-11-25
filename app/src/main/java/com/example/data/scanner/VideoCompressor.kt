package com.example.data.scanner

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.data.model.CompressionQuality
import com.example.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Hardware-accelerated video transcoder backed by AndroidX Media3 Transformer.
 * Re-encodes heavy videos to HEVC (H.265) with automatic H.264 fallback and real
 * progress callbacks, saving the compressed copy into MediaStore (Movies/AI Cleaner).
 */
class VideoCompressor(private val context: Context) {

    data class VideoCompressionResult(
        val savedBytes: Long,
        val outputSizeBytes: Long,
        val succeeded: Boolean
    )

    @OptIn(UnstableApi::class)
    suspend fun compressVideo(
        mediaItem: MediaItem,
        quality: CompressionQuality,
        onProgress: (Float) -> Unit = {}
    ): VideoCompressionResult {
        if (mediaItem.uriString.isBlank()) {
            return VideoCompressionResult(0L, mediaItem.sizeBytes, false)
        }

        val inputUri = Uri.parse(mediaItem.uriString)
        val targetHeight = when (quality) {
            CompressionQuality.HIGH -> 1080
            CompressionQuality.MEDIUM -> 720
            CompressionQuality.MAX_SAVINGS -> 480
        }

        val tempDir = File(context.cacheDir, "compressed_videos").apply { mkdirs() }
        val baseName = mediaItem.title.substringBeforeLast('.').ifBlank { "video_${mediaItem.id}" }
        val outputFile = File(tempDir, "${baseName}_hevc_${System.currentTimeMillis()}.mp4")

        return try {
            // Try hardware H.265/HEVC first; fall back to H.264 if device lacks an HEVC encoder
            val hevcSuccess = runMedia3Transform(
                inputUri = inputUri,
                outputFile = outputFile,
                targetHeight = targetHeight,
                videoMimeType = MimeTypes.VIDEO_H265,
                onProgress = onProgress
            )

            val transformOk = if (hevcSuccess) {
                true
            } else {
                if (outputFile.exists()) outputFile.delete()
                runMedia3Transform(
                    inputUri = inputUri,
                    outputFile = outputFile,
                    targetHeight = targetHeight,
                    videoMimeType = MimeTypes.VIDEO_H264,
                    onProgress = onProgress
                )
            }

            if (transformOk && outputFile.exists() && outputFile.length() > 0L) {
                val compressedBytes = outputFile.length()
                val savedBytes = (mediaItem.sizeBytes - compressedBytes).coerceAtLeast(0L)

                // Publish compressed video to MediaStore when it genuinely saves space
                if (savedBytes > 0L) {
                    withContext(Dispatchers.IO) {
                        saveCompressedVideoToMediaStore(outputFile, "${baseName}_compressed.mp4")
                    }
                }
                VideoCompressionResult(
                    savedBytes = savedBytes,
                    outputSizeBytes = compressedBytes,
                    succeeded = true
                )
            } else {
                VideoCompressionResult(0L, mediaItem.sizeBytes, false)
            }
        } catch (_: Exception) {
            VideoCompressionResult(0L, mediaItem.sizeBytes, false)
        } finally {
            withContext(Dispatchers.IO) {
                try {
                    if (outputFile.exists()) outputFile.delete()
                } catch (_: Exception) {}
            }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun runMedia3Transform(
        inputUri: Uri,
        outputFile: File,
        targetHeight: Int,
        videoMimeType: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var finished = false
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(videoMimeType)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (!finished) {
                            finished = true
                            onProgress(1f)
                            if (continuation.isActive) continuation.resume(true)
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (!finished) {
                            finished = true
                            if (continuation.isActive) continuation.resume(false)
                        }
                    }
                })
                .build()

            val videoEffects = listOf(Presentation.createForHeight(targetHeight))
            val editedMediaItem = EditedMediaItem.Builder(Media3Item.fromUri(inputUri))
                .setEffects(Effects(/* audioProcessors = */ emptyList(), videoEffects))
                .build()

            val progressJob = launch(Dispatchers.Main) {
                val holder = ProgressHolder()
                while (isActive && !finished) {
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress((holder.progress / 100f).coerceIn(0f, 0.99f))
                    }
                    delay(150L)
                }
            }

            continuation.invokeOnCancellation {
                finished = true
                progressJob.cancel()
                try {
                    transformer.cancel()
                } catch (_: Exception) {}
            }

            try {
                transformer.start(editedMediaItem, outputFile.absolutePath)
            } catch (_: Exception) {
                finished = true
                progressJob.cancel()
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    private fun saveCompressedVideoToMediaStore(sourceFile: File, displayName: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/AI Cleaner"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (_: Exception) {
            // Non-fatal if device storage is read-only or restricted
        }
    }
}
