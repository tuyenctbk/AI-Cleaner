package com.example.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import com.example.R
import androidx.compose.ui.res.stringResource

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
            }
        }
        return
    }

    val topItem = items.first()
    val nextItem = items.getOrNull(1)

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Card Stack Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Next item in background
            if (nextItem != null) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp, start = 12.dp, end = 12.dp),
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
                                if (offsetX.value > 180f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    coroutineScope.launch {
                                        offsetX.animateTo(800f, tween(180))
                                        onSwipeRightKeep(topItem)
                                        offsetX.snapTo(0f)
                                        offsetY.snapTo(0f)
                                    }
                                } else if (offsetX.value < -180f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    coroutineScope.launch {
                                        offsetX.animateTo(-800f, tween(180))
                                        onSwipeLeftDelete(topItem)
                                        offsetX.snapTo(0f)
                                        offsetY.snapTo(0f)
                                    }
                                } else {
                                    coroutineScope.launch {
                                        offsetX.animateTo(0f, tween(150))
                                        offsetY.animateTo(0f, tween(150))
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
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
                        Text(
                            text = "${topItem.formattedSize} • ${if (topItem.width > 0) "${topItem.width}x${topItem.height}" else "High Res"}",
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
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    coroutineScope.launch {
                        offsetX.animateTo(-800f, tween(200))
                        onSwipeLeftDelete(topItem)
                        offsetX.snapTo(0f)
                        offsetY.snapTo(0f)
                    }
                },
                modifier = Modifier
                    .height(52.dp)
                    .weight(1f)
                    .testTag("swipe_delete_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoralRed.copy(alpha = 0.12f),
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
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUndo()
                },
                enabled = canUndo,
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
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    coroutineScope.launch {
                        offsetX.animateTo(800f, tween(200))
                        onSwipeRightKeep(topItem)
                        offsetX.snapTo(0f)
                        offsetY.snapTo(0f)
                    }
                },
                modifier = Modifier
                    .height(52.dp)
                    .weight(1f)
                    .testTag("swipe_keep_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen.copy(alpha = 0.15f),
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
