package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.model.JunkCategory
import com.example.data.model.JunkType
import com.example.data.model.formatFileSize
import com.example.ui.components.AiScanningAnimation
import com.example.ui.theme.*
import com.example.ui.viewmodel.CleanerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCleanScreen(
    uiState: CleanerUiState,
    onBack: () -> Unit,
    onToggleCategory: (JunkType) -> Unit,
    onExecuteClean: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCategories = uiState.junkCategories.filter { it.isSelected }
    val totalSelectedBytes = selectedCategories.sumOf { it.sizeBytes }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_smart_clean),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (uiState.junkCategories.isNotEmpty() && !uiState.isScanning) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onExecuteClean,
                            enabled = totalSelectedBytes > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("execute_smart_clean_button"),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Text(
                                    text = stringResource(R.string.btn_clean_selected, formatFileSize(totalSelectedBytes)),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("smart_clean_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Illustration & Status
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.03f)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_cleaning_trash_1787907390869),
                                contentDescription = "Clean Radar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (uiState.isScanning) stringResource(R.string.smart_clean_scanning) else stringResource(R.string.smart_clean_ready),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (uiState.isScanning) uiState.scanStepText else stringResource(R.string.smart_clean_found_desc, formatFileSize(uiState.junkCategories.sumOf { it.sizeBytes })),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Scanning Animation with Compose Graphics
            if (uiState.isScanning) {
                item {
                    AiScanningAnimation(
                        progress = uiState.scanProgress,
                        stepText = uiState.scanStepText
                    )
                }
            }

            // Categories List
            if (!uiState.isScanning) {
                items(uiState.junkCategories) { category ->
                    JunkCategoryRow(
                        category = category,
                        onToggle = { onToggleCategory(category.type) }
                    )
                }
            }
        }
    }
}

@Composable
private fun JunkCategoryRow(
    category: JunkCategory,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .shadow(1.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.02f)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(getJunkIconColor(category.type).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getJunkIcon(category.type),
                        contentDescription = null,
                        tint = getJunkIconColor(category.type),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${category.itemCount} items • ${category.description}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = category.formattedSize,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Checkbox(
                    checked = category.isSelected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

private fun getJunkIcon(type: JunkType): ImageVector {
    return when (type) {
        JunkType.APP_CACHE -> Icons.Default.Apps
        JunkType.TEMP_FILES -> Icons.Default.Description
        JunkType.EMPTY_FOLDERS -> Icons.Default.FolderOpen
        JunkType.THUMBNAIL_CACHE -> Icons.Default.PhotoLibrary
        JunkType.RESIDUAL_FILES -> Icons.Default.DeleteOutline
        JunkType.OLD_LOGS -> Icons.Default.BugReport
        JunkType.APK_PACKAGES -> Icons.Default.Android
    }
}

private fun getJunkIconColor(type: JunkType): Color {
    return when (type) {
        JunkType.APP_CACHE -> Color(0xFF6750A4)
        JunkType.TEMP_FILES -> Color(0xFFF97316)
        JunkType.EMPTY_FOLDERS -> Color(0xFF10B981)
        JunkType.THUMBNAIL_CACHE -> Color(0xFF8B5CF6)
        JunkType.RESIDUAL_FILES -> CoralRed
        JunkType.OLD_LOGS -> Color(0xFF64748B)
        JunkType.APK_PACKAGES -> EmeraldGreen
    }
}

