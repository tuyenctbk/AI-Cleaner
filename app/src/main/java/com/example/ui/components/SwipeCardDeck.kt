package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.model.MediaItem
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeCardDeck(
    items: List<MediaItem>,
    onSwipeLeftDelete: (MediaItem) -> Unit,
    onSwipeRightKeep: (MediaItem) -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    if (items.isEmpty()) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(420.dp)
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(EmeraldGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.swipe_empty_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.swipe_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canUndo) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onUndo()
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_restore),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        return
    }

    val topItem = items.first()
    val nextItem = items.getOrNull(1)
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val deckHeight = (screenHeightDp * 0.44f).dp.coerceIn(250.dp, 405.dp)

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var isAnimatingOut by remember { mutableStateOf(false) }

    LaunchedEffect(topItem.id) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
        isAnimatingOut = false
    }

    fun triggerSwipeOut(keep: Boolean) {
        if (isAnimatingOut) return
        isAnimatingOut = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        coroutineScope.launch {
            try {
                offsetX.animateTo(if (keep) 850f else -850f, tween(185))
                if (keep) onSwipeRightKeep(topItem) else onSwipeLeftDelete(topItem)
                offsetX.snapTo(0f)
                offsetY.snapTo(0f)
            } finally {
                isAnimatingOut = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Card Stack Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(deckHeight)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Next item in background rises dynamically as top card is dragged
            if (nextItem != null) {
                val dragProgress = (abs(offsetX.value) / 220f).coerceIn(0f, 1f)
                val nextScale = 0.94f + (0.06f * dragProgress)
                val nextAlpha = 0.78f + (0.22f * dragProgress)
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = (14 * (1f - dragProgress)).dp)
                        .scale(nextScale)
                        .alpha(nextAlpha),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    CommonMediaThumbnail(
                        mediaItem = nextItem,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Top draggable card
            val rotation = (offsetX.value / 400f) * 18f
            val dragAlphaDelete = (-offsetX.value / 180f).coerceIn(0f, 1f)
            val dragAlphaKeep = (offsetX.value / 180f).coerceIn(0f, 1f)

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                    .rotate(rotation)
                    .shadow(16.dp, RoundedCornerShape(24.dp))
                    .pointerInput(topItem.id) {
                        detectDragGestures(
                            onDragEnd = {
                                if (isAnimatingOut) return@detectDragGestures
                                if (offsetX.value > 180f) {
                                    triggerSwipeOut(keep = true)
                                } else if (offsetX.value < -180f) {
                                    triggerSwipeOut(keep = false)
                                } else {
                                    coroutineScope.launch {
                                        launch {
                                            offsetX.animateTo(
                                                0f,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                        launch {
                                            offsetY.animateTo(
                                                0f,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (isAnimatingOut) return@detectDragGestures
                                change.consume()
                                coroutineScope.launch {
                                    offsetX.snapTo(offsetX.value + dragAmount.x)
                                    offsetY.snapTo(offsetY.value + dragAmount.y * 0.4f)
                                }
                            }
                        )
                    },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CommonMediaThumbnail(
                        mediaItem = topItem,
                        modifier = Modifier.fillMaxSize()
                    )

                    // On-Device ML Kit Screenshot Category Pill
                    if (topItem.isScreenshot && topItem.screenshotCategory != com.example.data.model.ScreenshotCategory.NONE) {
                        val (catLabelRes, catColor) = when (topItem.screenshotCategory) {
                            com.example.data.model.ScreenshotCategory.OTP_OR_CODE ->
                                R.string.swipe_badge_otp_safe to CoralRed
                            com.example.data.model.ScreenshotCategory.RECEIPT_OR_DOC ->
                                R.string.swipe_badge_receipt_keep to EmeraldGreen
                            com.example.data.model.ScreenshotCategory.CHAT_OR_MEME ->
                                R.string.swipe_badge_chat_meme to AmberGolden
                            else ->
                                R.string.swipe_badge_screenshot to ElectricBlue
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = catColor.copy(alpha = 0.92f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .testTag("swipe_screenshot_category_badge")
                        ) {
                            Text(
                                text = stringResource(catLabelRes),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Gradient Scrim at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                )
                            )
                    )

                    // File name and size at bottom
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = topItem.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val resolutionText = if (topItem.width > 0 && topItem.height > 0) {
                            stringResource(R.string.format_resolution, topItem.width, topItem.height)
                        } else {
                            stringResource(R.string.label_high_res)
                        }
                        Text(
                            text = stringResource(R.string.storage_media_subtitle_fmt, topItem.formattedSize, resolutionText),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }

                    // Keep Stamp overlay (Right Swipe)
                    if (dragAlphaKeep > 0.05f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(EmeraldGreen.copy(alpha = dragAlphaKeep * 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = stringResource(R.string.swipe_stamp_keep),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Delete Stamp overlay (Left Swipe)
                    if (dragAlphaDelete > 0.05f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(CoralRed.copy(alpha = dragAlphaDelete * 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = stringResource(R.string.swipe_stamp_delete),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons Row: [Delete / Move to Trash] [Undo] [Keep Image]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Button: Delete
            Button(
                onClick = { triggerSwipeOut(keep = false) },
                enabled = !isAnimatingOut,
                modifier = Modifier
                    .height(52.dp)
                    .weight(1f)
                    .testTag("swipe_delete_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoralRed.copy(alpha = 0.14f),
                    contentColor = CoralRed
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.btn_delete),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.btn_delete),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Center Button: Undo
            IconButton(
                onClick = {
                    if (!isAnimatingOut) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onUndo()
                    }
                },
                enabled = canUndo && !isAnimatingOut,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = stringResource(R.string.btn_restore),
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right Button: Keep
            Button(
                onClick = { triggerSwipeOut(keep = true) },
                enabled = !isAnimatingOut,
                modifier = Modifier
                    .height(52.dp)
                    .weight(1f)
                    .testTag("swipe_keep_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen.copy(alpha = 0.16f),
                    contentColor = EmeraldGreen
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.btn_keep),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.btn_keep),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
