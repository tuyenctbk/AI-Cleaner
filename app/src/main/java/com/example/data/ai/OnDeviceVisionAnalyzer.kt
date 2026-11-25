package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.model.ScreenshotCategory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

/**
 * 100% Free, Offline On-Device Vision AI powered by Google ML Kit:
 * 1) Face Quality Scoring (eyes open probability + smile + head alignment) for Smart Best Shot.
 * 2) OCR Text Recognition for Smart Screenshot Classification (OTP/Codes, Receipts/Docs, Chats/Memes).
 *
 * Always downscales bitmaps <= 768px for fast, memory-safe, battery-friendly inference.
 */
class OnDeviceVisionAnalyzer(private val context: Context) {

    data class FaceQualityResult(
        val faceCount: Int,
        val qualityScore: Float, // 0..100
        val hasBlinkingFace: Boolean
    )

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.12f)
            .build()
        FaceDetection.getClient(options)
    }

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Evaluates portraits for Smart Best Shot ranking using ML Kit Face Detection.
     * Returns null gracefully if the bitmap cannot be decoded or ML Kit is unavailable.
     */
    suspend fun analyzeFaceQuality(uri: Uri): FaceQualityResult? = withContext(Dispatchers.Default) {
        val bitmap = decodeDownscaledBitmap(uri, AiModelConfig.MAX_UPLOAD_BITMAP_DIM_PX) ?: return@withContext null
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = faceDetector.process(image).await()
            if (faces.isEmpty()) {
                return@withContext FaceQualityResult(faceCount = 0, qualityScore = 0f, hasBlinkingFace = false)
            }

            var totalScore = 0f
            var anyBlink = false
            for (face in faces) {
                val leftEye = face.leftEyeOpenProbability ?: 0.75f
                val rightEye = face.rightEyeOpenProbability ?: 0.75f
                val eyesAvg = ((leftEye + rightEye) / 2f).coerceIn(0f, 1f)
                if (leftEye < BLINK_EYE_THRESHOLD || rightEye < BLINK_EYE_THRESHOLD) {
                    anyBlink = true
                }
                val smile = (face.smilingProbability ?: 0.5f).coerceIn(0f, 1f)
                // Head pose neutrality: smaller yaw/roll deviation yields a cleaner portrait
                val yawPenalty = (abs(face.headEulerAngleY) / 45f).coerceIn(0f, 1f)
                val rollPenalty = (abs(face.headEulerAngleZ) / 35f).coerceIn(0f, 1f)
                val alignmentScore = (1f - (yawPenalty * 0.6f + rollPenalty * 0.4f)).coerceIn(0f, 1f)

                val faceScore = (eyesAvg * 60f) + (smile * 25f) + (alignmentScore * 15f)
                totalScore += faceScore
            }

            val avgScore = (totalScore / faces.size).coerceIn(0f, 100f)
            FaceQualityResult(
                faceCount = faces.size,
                qualityScore = avgScore,
                hasBlinkingFace = anyBlink
            )
        } catch (_: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Classifies a screenshot on-device using ML Kit OCR text recognition.
     */
    suspend fun classifyScreenshot(uri: Uri, fileName: String = ""): ScreenshotCategory = withContext(Dispatchers.Default) {
        val bitmap = decodeDownscaledBitmap(uri, AiModelConfig.MAX_UPLOAD_BITMAP_DIM_PX)
            ?: return@withContext classifyScreenshotText("", fileName)
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = textRecognizer.process(image).await()
            classifyScreenshotText(visionText.text, fileName)
        } catch (_: Exception) {
            classifyScreenshotText("", fileName)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeDownscaledBitmap(uri: Uri, maxDim: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val longest = max(bounds.outWidth, bounds.outHeight)
            var inSampleSize = 1
            while (longest / (inSampleSize * 2) >= maxDim) {
                inSampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun close() {
        try {
            faceDetector.close()
        } catch (_: Exception) {
        }
        try {
            textRecognizer.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val BLINK_EYE_THRESHOLD = 0.35f

        private val OTP_KEYWORDS = listOf(
            "verification code", "verify your", "one-time", "one time password",
            "otp", "2fa", "two-factor", "security code", "passcode", "login code",
            "sign-in code", "authentication code", "confirm code", "boarding pass",
            "qr code", "expires in", "do not share this code", "mã xác nhận",
            "código de verificación", "code de vérification", "bestätigungscode"
        )

        private val RECEIPT_DOC_KEYWORDS = listOf(
            "receipt", "invoice", "subtotal", "total due", "amount paid", "tax",
            "vat", "order #", "order number", "transaction id", "payment successful",
            "bank transfer", "wire transfer", "account number", "iban", "swift",
            "passport", "driver license", "national id", "tax return", "contract",
            "agreement", "billing", "statement", "paid", "mastercard", "visa ending"
        )

        private val CHAT_MEME_KEYWORDS = listOf(
            "type a message", "send a message", "delivered", "read at", "online",
            "last seen", "typing...", "reply", "retweet", "repost", "likes",
            "comments", "subscribe", "follow", "whatsapp", "telegram", "messenger",
            "instagram", "tiktok", "reddit", "discord", "meme", "lol", "lmao", "pov:"
        )

        private val OTP_CODE_REGEX = Regex("""\b\d{4,8}\b""")
        private val CURRENCY_AMOUNT_REGEX = Regex("""(\$|€|£|¥|₫|₹|USD|EUR)\s?\d+([.,]\d{2})?""", RegexOption.IGNORE_CASE)
        private val TIME_STAMP_REGEX = Regex("""\b\d{1,2}:\d{2}\s?(AM|PM|am|pm)?\b""")

        /**
         * Pure, deterministic, JVM-testable classifier for OCR-extracted screenshot text.
         */
        fun classifyScreenshotText(ocrText: String, fileName: String = ""): ScreenshotCategory {
            val text = ocrText.lowercase()
            val name = fileName.lowercase()

            // 1. Protect important financial/legal receipts & documents FIRST so we never accidentally
            // mark a bank transfer receipt as safe-to-delete OTP clutter!
            val receiptKeywordHits = RECEIPT_DOC_KEYWORDS.count { text.contains(it) || name.contains(it) }
            val hasCurrency = CURRENCY_AMOUNT_REGEX.containsMatchIn(ocrText)
            if (receiptKeywordHits >= 2 || (receiptKeywordHits >= 1 && hasCurrency)) {
                return ScreenshotCategory.RECEIPT_OR_DOC
            }

            // 2. OTP / Verification Codes / Temporary Tickets (safest to bulk delete)
            val hasOtpKeyword = OTP_KEYWORDS.any { text.contains(it) || name.contains(it) }
            val hasNumericCode = OTP_CODE_REGEX.containsMatchIn(ocrText)
            if (hasOtpKeyword && (hasNumericCode || text.contains("qr") || text.contains("boarding"))) {
                return ScreenshotCategory.OTP_OR_CODE
            }
            if (hasOtpKeyword) {
                return ScreenshotCategory.OTP_OR_CODE
            }

            // 3. Chat screenshots & social memes (bulk cleanable)
            val chatHits = CHAT_MEME_KEYWORDS.count { text.contains(it) || name.contains(it) }
            val timestampCount = TIME_STAMP_REGEX.findAll(ocrText).count()
            if (chatHits >= 1 || timestampCount >= 3) {
                return ScreenshotCategory.CHAT_OR_MEME
            }

            return ScreenshotCategory.GENERAL
        }
    }
}
