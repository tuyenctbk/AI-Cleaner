package com.example

import com.example.data.ai.AiExecutionTier
import com.example.data.ai.AiKeySource
import com.example.data.ai.AiQuotaManager
import com.example.data.ai.AiQuotaSnapshot
import com.example.data.ai.OnDeviceVisionAnalyzer
import com.example.data.model.MediaItem
import com.example.data.model.ScreenshotCategory
import com.example.data.scanner.MediaStoreDuplicateScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AiFeaturesUnitTest {

    @Test
    fun selectBestShot_prefersEyesOpenSmilingPortraitOverBlinkingPortrait() {
        val blinkingShot = MediaItem(
            id = 1L,
            title = "IMG_001.jpg",
            path = "/DCIM/Camera/IMG_001.jpg",
            uriString = "content://media/1",
            sizeBytes = 3_200_000L,
            dateModified = 1000L,
            mimeType = "image/jpeg",
            width = 4000,
            height = 3000,
            sharpnessScore = 88f,
            faceCount = 1,
            faceQualityScore = 32f,
            hasBlinkingFace = true
        )
        val smilingEyesOpenShot = MediaItem(
            id = 2L,
            title = "IMG_002.jpg",
            path = "/DCIM/Camera/IMG_002.jpg",
            uriString = "content://media/2",
            sizeBytes = 3_150_000L,
            dateModified = 1002L,
            mimeType = "image/jpeg",
            width = 4000,
            height = 3000,
            sharpnessScore = 88f,
            faceCount = 1,
            faceQualityScore = 94f,
            hasBlinkingFace = false
        )

        val best = MediaStoreDuplicateScanner.selectBestShot(listOf(blinkingShot, smilingEyesOpenShot))
        assertEquals(2L, best.id)
    }

    @Test
    fun classifyScreenshotText_detectsOtpCodes() {
        val category = OnDeviceVisionAnalyzer.classifyScreenshotText(
            ocrText = "Your verification code is 482910. Valid for 5 minutes. Do not share this OTP.",
            fileName = "Screenshot_20260412.png"
        )
        assertEquals(ScreenshotCategory.OTP_OR_CODE, category)
    }

    @Test
    fun classifyScreenshotText_protectsReceiptEvenWithNumericId() {
        val category = OnDeviceVisionAnalyzer.classifyScreenshotText(
            ocrText = "Payment Receipt #892104\nSubtotal: $42.50\nTax: $3.40\nTotal Paid: $45.90\nOrder confirmation",
            fileName = "Screenshot_bank.png"
        )
        assertEquals(ScreenshotCategory.RECEIPT_OR_DOC, category)
    }

    @Test
    fun classifyScreenshotText_detectsChatOrMeme() {
        val category = OnDeviceVisionAnalyzer.classifyScreenshotText(
            ocrText = "WhatsApp • Typing...\nDelivered 10:42 PM\nHAHAHA meme of the day",
            fileName = "Screenshot_chat.png"
        )
        assertEquals(ScreenshotCategory.CHAT_OR_MEME, category)
    }

    @Test
    fun aiQuotaSnapshot_softNudgeAtEightyPercentAndRecoveryAtExhaustion() {
        val softNudgeSnapshot = AiQuotaSnapshot(
            dailyUsed = 8,
            baseDailyLimit = 10,
            bonusGraceCredits = 0,
            ecoLiteMode = false,
            hasUserByokKey = false,
            keySource = AiKeySource.BUILD_CONFIG,
            activeTier = AiExecutionTier.PRIMARY_CLOUD
        )
        assertTrue(softNudgeSnapshot.shouldShowSoftNudge)
        assertFalse(softNudgeSnapshot.shouldShowQuotaRecovery)
        assertEquals(2, softNudgeSnapshot.remainingCredits)
        assertEquals(80, softNudgeSnapshot.usagePercent)

        val exhaustedSnapshot = softNudgeSnapshot.copy(dailyUsed = 10)
        assertFalse(exhaustedSnapshot.shouldShowSoftNudge)
        assertTrue(exhaustedSnapshot.shouldShowQuotaRecovery)
        assertEquals(0, exhaustedSnapshot.remainingCredits)

        // BYOK users never see free-tier soft nudge or limit recovery
        val byokSnapshot = exhaustedSnapshot.copy(
            hasUserByokKey = true,
            keySource = AiKeySource.USER_BYOK
        )
        assertFalse(byokSnapshot.shouldShowSoftNudge)
        assertFalse(byokSnapshot.shouldShowQuotaRecovery)
    }

    @Test
    fun computeTimeUntilMidnight_returnsValidHoursAndMinutes() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MAY)
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val (hours, minutes) = AiQuotaManager.computeTimeUntilMidnight(cal.timeInMillis)
        assertEquals(2, hours)
        assertEquals(45, minutes)
        assertEquals("2026-05-10", AiQuotaManager.formatIsoDate(cal.timeInMillis))
    }
}
