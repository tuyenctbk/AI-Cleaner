package com.example.data.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max

/**
 * PerceptualHasher computes 64-bit (8x8) grayscale Difference Hashes (dHash)
 * for images and measures visual similarity via bit-level Hamming distance.
 *
 * Algorithm:
 * 1. Downscale the image to 9x8 pixels (9 columns x 8 rows).
 * 2. Convert each pixel to BT.601 grayscale luminance.
 * 3. Compare adjacent horizontal neighbors: bit(y * 8 + x) = 1 if gray[y, x] > gray[y, x + 1], else 0.
 * 4. Compare two 64-bit hashes using [hammingDistance]; hashes within [DEFAULT_SIMILARITY_THRESHOLD]
 *    (Hamming distance <= 6) represent visually similar photos despite recompression or minor edits.
 */
object PerceptualHasher {

    const val HASH_WIDTH = 9
    const val HASH_HEIGHT = 8
    const val HASH_BIT_COUNT = 64
    const val DEFAULT_SIMILARITY_THRESHOLD = 6
    private const val DECODE_TARGET_SIZE = 128

    /**
     * Calculates the Hamming distance (number of differing bits, 0..64) between two 64-bit dHashes.
     */
    fun hammingDistance(hashA: Long, hashB: Long): Int = (hashA xor hashB).countOneBits()

    /**
     * Returns true when [hashA] and [hashB] are within [maxDistance] differing bits.
     */
    fun areSimilar(
        hashA: Long,
        hashB: Long,
        maxDistance: Int = DEFAULT_SIMILARITY_THRESHOLD
    ): Boolean = hammingDistance(hashA, hashB) <= maxDistance

    /**
     * Converts the Hamming distance between [hashA] and [hashB] into a 0..100 similarity percentage.
     */
    fun similarityScore(hashA: Long, hashB: Long): Float {
        val distance = hammingDistance(hashA, hashB)
        return ((HASH_BIT_COUNT - distance).toFloat() / HASH_BIT_COUNT.toFloat()) * 100f
    }

    /**
     * Decodes a memory-safe downscaled [Bitmap] from [uri] and computes its 8x8 grayscale dHash.
     * Returns null if the URI cannot be opened or decoded.
     */
    fun computeDHash(context: Context, uri: Uri): Long? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val longest = max(bounds.outWidth, bounds.outHeight)
            var inSampleSize = 1
            while (longest / (inSampleSize * 2) >= DECODE_TARGET_SIZE) {
                inSampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            try {
                computeDHash(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Computes a 64-bit 8x8 grayscale dHash directly from an Android [Bitmap].
     */
    fun computeDHash(bitmap: Bitmap): Long {
        val scaled9x8 = if (bitmap.width == HASH_WIDTH && bitmap.height == HASH_HEIGHT) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, HASH_WIDTH, HASH_HEIGHT, true)
        }
        return try {
            val pixels9x8 = IntArray(HASH_WIDTH * HASH_HEIGHT)
            scaled9x8.getPixels(pixels9x8, 0, HASH_WIDTH, 0, 0, HASH_WIDTH, HASH_HEIGHT)
            computeDHashFromArgb9x8(pixels9x8)
        } finally {
            if (scaled9x8 !== bitmap) {
                scaled9x8.recycle()
            }
        }
    }

    /**
     * Computes an 8x8 grayscale dHash from a 9x8 ARGB pixel array (length 72, row-major order).
     */
    fun computeDHashFromArgb9x8(argb9x8: IntArray): Long {
        require(argb9x8.size == HASH_WIDTH * HASH_HEIGHT) {
            "Expected 9x8 (72) ARGB samples, got ${argb9x8.size}"
        }
        return computeDHashFromGray9x8(argbToGrayscale(argb9x8))
    }

    /**
     * Computes an 8x8 grayscale dHash from any row-major grayscale image buffer by area-averaging
     * down to a 9x8 luminance grid. Useful in both Android and pure JVM contexts.
     */
    fun computeDHashFromGrayscaleImage(grayPixels: IntArray, width: Int, height: Int): Long {
        require(width >= HASH_WIDTH && height >= HASH_HEIGHT) {
            "Image dimensions must be at least ${HASH_WIDTH}x${HASH_HEIGHT}, got ${width}x${height}"
        }
        require(grayPixels.size >= width * height) {
            "Expected at least ${width * height} grayscale pixels, got ${grayPixels.size}"
        }

        if (width == HASH_WIDTH && height == HASH_HEIGHT && grayPixels.size == HASH_WIDTH * HASH_HEIGHT) {
            return computeDHashFromGray9x8(grayPixels)
        }

        val grid9x8 = IntArray(HASH_WIDTH * HASH_HEIGHT)
        for (ty in 0 until HASH_HEIGHT) {
            val yStart = (ty * height) / HASH_HEIGHT
            val yEnd = (((ty + 1) * height) / HASH_HEIGHT).coerceAtLeast(yStart + 1).coerceAtMost(height)
            for (tx in 0 until HASH_WIDTH) {
                val xStart = (tx * width) / HASH_WIDTH
                val xEnd = (((tx + 1) * width) / HASH_WIDTH).coerceAtLeast(xStart + 1).coerceAtMost(width)

                var sum = 0L
                var count = 0
                for (y in yStart until yEnd) {
                    val rowOffset = y * width
                    for (x in xStart until xEnd) {
                        sum += grayPixels[rowOffset + x]
                        count++
                    }
                }
                grid9x8[ty * HASH_WIDTH + tx] = if (count > 0) (sum / count).toInt() else 0
            }
        }
        return computeDHashFromGray9x8(grid9x8)
    }

    /**
     * Computes a 64-bit 8x8 grayscale dHash from a 9x8 grayscale luminance array (length 72).
     * Each bit represents `leftPixel > rightPixel` across the 8 rows x 8 horizontal pairs.
     */
    fun computeDHashFromGray9x8(gray9x8: IntArray): Long {
        require(gray9x8.size == HASH_WIDTH * HASH_HEIGHT) {
            "Expected 9x8 (72) grayscale samples, got ${gray9x8.size}"
        }
        var hash = 0L
        var bit = 0
        for (y in 0 until HASH_HEIGHT) {
            val rowOffset = y * HASH_WIDTH
            for (x in 0 until HASH_HEIGHT) {
                if (gray9x8[rowOffset + x] > gray9x8[rowOffset + x + 1]) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        return hash
    }

    /**
     * Converts packed ARGB_8888 pixels to 0..255 BT.601 grayscale luminance.
     */
    fun argbToGrayscale(argbPixels: IntArray): IntArray {
        return IntArray(argbPixels.size) { idx ->
            val c = argbPixels[idx]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            (77 * r + 150 * g + 29 * b) shr 8
        }
    }
}
