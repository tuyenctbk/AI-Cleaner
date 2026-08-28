package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DuplicateGroup
import com.example.data.model.MediaItem
import com.example.data.model.formatFileSize
import com.example.ui.theme.*

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.example.ui.components.dpadFocusable

@Composable
fun SimilarPhotosGroupCard(
    group: DuplicateGroup,
    onToggleItem: (Long) -> Unit,
    onToggleAllInGroup: (Boolean) -> Unit,
    onCompareGroup: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val allDuplicatesSelected = group.items.filter { !it.isBestShot }.all { it.isSelected }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(2.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.03f)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Group Header with select group checkbox and Compare button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${group.items.size} Photos • ${group.selectedCount} Selected • ${formatFileSize(group.totalSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onCompareGroup != null) {
                        OutlinedButton(
                            onClick = onCompareGroup,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("compare_group_button_${group.id}")
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Compare,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Compare", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Group Checkbox
                    IconButton(
                        onClick = { onToggleAllInGroup(!allDuplicatesSelected) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (allDuplicatesSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.5.dp, if (allDuplicatesSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (allDuplicatesSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Select Group",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Photo Grid for this group: Large Best Photo on left, Duplicates on right
            val bestItem = group.items.firstOrNull { it.isBestShot } ?: group.items.first()
            val duplicateItems = group.items.filter { it.id != bestItem.id }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Best Photo (Large card)
                val bestInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = bestInteraction,
                            indication = androidx.compose.foundation.LocalIndication.current,
                            onClick = { onToggleItem(bestItem.id) }
                        )
                        .dpadFocusable(bestInteraction, RoundedCornerShape(16.dp))
                ) {
                    CommonMediaThumbnail(
                        mediaItem = bestItem,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Best Shot Golden Badge
                    Surface(
                        shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 10.dp),
                        color = Color(0xFFE65100).copy(alpha = 0.95f),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Best Shot",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Best Photo keep indicator / checkbox
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bestItem.isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Duplicates 2x2 or column thumbnails
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    duplicateItems.take(2).forEach { dupItem ->
                        val itemInteraction = remember(dupItem.id) { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(
                                    interactionSource = itemInteraction,
                                    indication = androidx.compose.foundation.LocalIndication.current,
                                    onClick = { onToggleItem(dupItem.id) }
                                )
                                .dpadFocusable(itemInteraction, RoundedCornerShape(14.dp))
                        ) {
                            CommonMediaThumbnail(
                                mediaItem = dupItem,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Checkbox badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (dupItem.isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f))
                                    .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(5.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (dupItem.isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // Size Tag
                            Text(
                                text = dupItem.formattedSize,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

