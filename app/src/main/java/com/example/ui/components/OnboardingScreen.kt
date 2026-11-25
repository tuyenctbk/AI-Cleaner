package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private data class OnboardingPageData(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val tagResIds: List<Int>
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onCompleteOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val errorColor = MaterialTheme.colorScheme.error

    val pages = remember(primaryColor, primaryContainer, errorColor) {
        listOf(
            OnboardingPageData(
                titleRes = R.string.onboarding_title_1,
                descRes = R.string.onboarding_desc_1,
                icon = Icons.Default.Bolt,
                gradientColors = listOf(EmeraldGreen, ElectricBlue),
                tagResIds = listOf(
                    R.string.onboarding_tag_quick_clean,
                    R.string.onboarding_tag_large_files,
                    R.string.onboarding_tag_cache_purge
                )
            ),
            OnboardingPageData(
                titleRes = R.string.onboarding_title_analyzer,
                descRes = R.string.onboarding_desc_analyzer,
                icon = Icons.Default.PieChart,
                gradientColors = listOf(ElectricBlue, VividViolet),
                tagResIds = listOf(
                    R.string.onboarding_tag_pie_charts,
                    R.string.analyzer_scope_file_types,
                    R.string.onboarding_tag_glance_widget
                )
            ),
            OnboardingPageData(
                titleRes = R.string.onboarding_title_2,
                descRes = R.string.onboarding_desc_2,
                icon = Icons.Default.BurstMode,
                gradientColors = listOf(errorColor, primaryColor),
                tagResIds = listOf(
                    R.string.onboarding_tag_dhash_scan,
                    R.string.onboarding_tag_offline_ocr,
                    R.string.onboarding_tag_gesture_swipe
                )
            ),
            OnboardingPageData(
                titleRes = R.string.onboarding_title_3,
                descRes = R.string.onboarding_desc_3,
                icon = Icons.Default.Lock,
                gradientColors = listOf(MintGreen, primaryColor),
                tagResIds = listOf(
                    R.string.onboarding_tag_safe_vault,
                    R.string.onboarding_tag_smart_expiry,
                    R.string.onboarding_tag_on_device
                )
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Step pill & Skip button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = stringResource(
                            R.string.onboarding_step_fmt,
                            pagerState.currentPage + 1,
                            pages.size
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                if (pagerState.currentPage < pages.size - 1) {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCompleteOnboarding()
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("onboarding_skip_button"),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }

            // Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("onboarding_pager")
            ) { pageIdx ->
                val page = pages[pageIdx]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Hero Icon Graphic Badge with soft outer aura
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(164.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(164.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            page.gradientColors.first().copy(alpha = 0.22f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(132.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = page.gradientColors.map { it.copy(alpha = 0.92f) }
                                    )
                                )
                                .border(
                                    width = 3.dp,
                                    brush = Brush.linearGradient(
                                        listOf(Color.White.copy(alpha = 0.65f), Color.Transparent)
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = stringResource(page.titleRes),
                                tint = Color.White,
                                modifier = Modifier.size(62.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Title
                    Text(
                        text = stringResource(page.titleRes),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 25.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Description
                    Text(
                        text = stringResource(page.descRes),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 22.sp,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Adaptive Wrapping Feature Tags Row
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        page.tagResIds.forEach { tagRes ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                            ) {
                                Text(
                                    text = stringResource(tagRes),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Controls: Page Indicator & Action Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Animated Page Dots Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 8.dp,
                            animationSpec = tween(durationMillis = 220),
                            label = "OnboardingDot_$index"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(dotWidth)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    }
                                )
                        )
                    }
                }

                // Next / Get Started Button
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (pagerState.currentPage < pages.size - 1) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onCompleteOnboarding()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("onboarding_next_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (pagerState.currentPage == pages.size - 1) {
                                stringResource(R.string.onboarding_get_started)
                            } else {
                                stringResource(R.string.onboarding_next)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (pagerState.currentPage == pages.size - 1) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.AutoMirrored.Filled.ArrowForward
                            },
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
