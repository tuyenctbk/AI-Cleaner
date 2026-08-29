package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiInsightRoutine
import com.example.ui.theme.*
import com.example.ui.viewmodel.CleanerUiState

import com.example.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInsightsScreen(
    uiState: CleanerUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onGenerateInsights: (String) -> Unit,
    onExecuteRoutine: (AiInsightRoutine) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var queryInput by remember { mutableStateOf(uiState.aiQueryText) }
    var selectedRoutineForExecution by remember { mutableStateOf<AiInsightRoutine?>(null) }

    selectedRoutineForExecution?.let { routine ->
        com.example.ui.components.MassDeletionSummaryDialog(
            title = "Execute AI Routine?",
            subtitle = routine.title,
            totalFilesCount = routine.itemCount,
            projectedSpaceBytes = routine.potentialSavingsBytes,
            itemsPreviewList = listOf(routine.summary, routine.explanation),
            onConfirmDelete = {
                onExecuteRoutine(routine)
                selectedRoutineForExecution = null
            },
            onDismiss = { selectedRoutineForExecution = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.title_ai_insights),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                            color = PolishTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = VividViolet.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Gemini",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = VividViolet,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back), tint = PolishTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PolishBackground)
            )
        },
        containerColor = PolishBackground,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Natural Language Query Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = PolishSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(VividViolet, ElectricBlue))
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = VividViolet)
                            Text(
                                text = "Ask Gemini AI Cleaner",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = PolishTextPrimary
                            )
                        }

                        OutlinedTextField(
                            value = queryInput,
                            onValueChange = {
                                queryInput = it
                                onQueryChange(it)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_query_input"),
                            placeholder = {
                                Text("e.g. Batch delete old screenshots or clean WhatsApp media...", color = PolishTextSecondary)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = PolishSurfaceVariant,
                                unfocusedContainerColor = PolishSurfaceVariant,
                                focusedBorderColor = VividViolet,
                                unfocusedBorderColor = PolishOutline.copy(alpha = 0.4f)
                            )
                        )

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onGenerateInsights(queryInput)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VividViolet)
                        ) {
                            if (uiState.isGeneratingAiInsights) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing File Usage Patterns...")
                            } else {
                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate Custom AI Routine", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // AI Recommendations Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Suggested Custom Routines",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PolishTextPrimary
                    )

                    Text(
                        text = "${uiState.aiRoutines.size} Available",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = VividViolet
                    )
                }
            }

            // List of AI Routine Cards
            items(uiState.aiRoutines, key = { it.id }) { routine ->
                AiRoutineCardItem(
                    routine = routine,
                    onExecute = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedRoutineForExecution = routine
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AiRoutineCardItem(
    routine: AiInsightRoutine,
    onExecute: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PolishSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                if (routine.isExecuted) listOf(EmeraldGreen, EmeraldGreen) else listOf(VividViolet.copy(alpha = 0.6f), ElectricBlue.copy(alpha = 0.6f))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VividViolet.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = routine.categoryTag,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = VividViolet,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${routine.confidenceScore}% Confidence",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldGreen
                    )
                }
            }

            Text(
                text = routine.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PolishTextPrimary
            )

            Text(
                text = routine.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = PolishTextSecondary
            )

            Divider(color = PolishOutline.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Potential Savings", style = MaterialTheme.typography.labelSmall, color = PolishTextSecondary)
                    Text(
                        text = routine.formattedSavings,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldGreen
                    )
                }

                if (routine.isExecuted) {
                    Button(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(disabledContainerColor = EmeraldGreen.copy(alpha = 0.2f))
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = EmeraldGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Routine Executed", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onExecute,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VividViolet),
                        modifier = Modifier.testTag("execute_ai_routine_button_${routine.id}")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Execute Routine", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
