package com.example

import com.example.data.scanner.PerceptualHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualHasherTest {

    @Test
    fun computeDHashFromGray9x8_matchesExpectedHorizontalGradientBits() {
        val decreasing = IntArray(72) { idx -> 255 - (idx % 9) * 20 }
        val increasing = IntArray(72) { idx -> (idx % 9) * 20 }

        val hashDec = PerceptualHasher.computeDHashFromGray9x8(decreasing)
        val hashInc = PerceptualHasher.computeDHashFromGray9x8(increasing)

        assertEquals(-1L, hashDec)
        assertEquals(0L, hashInc)
        assertEquals(64, PerceptualHasher.hammingDistance(hashDec, hashInc))
        assertEquals(0, PerceptualHasher.hammingDistance(hashDec, hashDec))
    }

    @Test
    fun computeDHashFromGrayscaleImage_isResilientToGlobalBrightnessShift() {
        val width = 90
        val height = 80
        val original = IntArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            ((x * 2 + y) % 180) + 10
        }
        val brightened = IntArray(width * height) { idx ->
            (original[idx] + 35).coerceAtMost(255)
        }

        val hashOriginal = PerceptualHasher.computeDHashFromGrayscaleImage(original, width, height)
        val hashBrightened = PerceptualHasher.computeDHashFromGrayscaleImage(brightened, width, height)

        val distance = PerceptualHasher.hammingDistance(hashOriginal, hashBrightened)
        assertTrue("Expected brightness-shifted photo distance <= 6, got $distance", distance <= 6)
        assertTrue(PerceptualHasher.areSimilar(hashOriginal, hashBrightened))
        assertTrue(PerceptualHasher.similarityScore(hashOriginal, hashBrightened) >= 90f)
    }

    @Test
    fun visuallyDistinctImages_haveLargeHammingDistanceAndAreNotSimilar() {
        val width = 72
        val height = 64
        // Left-bright to right-dark gradient
        val gradientLeftToRight = IntArray(width * height) { idx ->
            val x = idx % width
            255 - (x * 255 / width)
        }
        // Right-bright to left-dark gradient
        val gradientRightToLeft = IntArray(width * height) { idx ->
            val x = idx % width
            (x * 255 / width)
        }

        val hashA = PerceptualHasher.computeDHashFromGrayscaleImage(gradientLeftToRight, width, height)
        val hashB = PerceptualHasher.computeDHashFromGrayscaleImage(gradientRightToLeft, width, height)

        val distance = PerceptualHasher.hammingDistance(hashA, hashB)
        assertTrue("Expected opposite gradients distance > 6, got $distance", distance > 6)
        assertFalse(PerceptualHasher.areSimilar(hashA, hashB))
    }

    @Test
    fun argbConversionAnd9x8ArgbDHash_produceExpectedGrayscaleHash() {
        val pixels9x8 = IntArray(72) { idx ->
            val x = idx % 9
            val luminance = 240 - x * 25
            (0xFF shl 24) or (luminance shl 16) or (luminance shl 8) or luminance
        }

        val hash = PerceptualHasher.computeDHashFromArgb9x8(pixels9x8)
        assertEquals(-1L, hash)
        assertEquals(100f, PerceptualHasher.similarityScore(hash, hash), 0.01f)
    }
}
