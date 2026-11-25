package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ai.ApiKeyValidationStatus
import com.example.ui.theme.*
import com.example.ui.viewmodel.CleanerUiState

@Composable
fun SettingsAiEngineCard(
    uiState: CleanerUiState,
    onSetEcoLiteMode: (Boolean) -> Unit,
    onTestAndSaveByokKey: (String) -> Unit,
    onClearByokKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var apiKeyInput by remember { mutableStateOf("") }
    var showKeyPlaintext by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_ai_engine_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(VividViolet.copy(alpha = 0.6f), ElectricBlue.copy(alpha = 0.6f)))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Active engine header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = VividViolet,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_ai_section_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                ActiveAiEnginePill(
                    quota = uiState.aiQuota,
                    fromCache = uiState.aiLastResultFromCache
                )
            }

            // Eco Lite Mode Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_ai_eco_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_ai_eco_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.aiQuota.ecoLiteMode,
                    onCheckedChange = { enabled ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSetEcoLiteMode(enabled)
                    },
                    modifier = Modifier.testTag("settings_eco_lite_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // BYOK Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_byok_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.settings_byok_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (uiState.byokMaskedKey.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldGreen.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.settings_byok_saved_fmt, uiState.byokMaskedKey),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldGreen
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_byok_input"),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.settings_byok_placeholder),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    visualTransformation = if (showKeyPlaintext) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKeyPlaintext = !showKeyPlaintext }) {
                            Icon(
                                imageVector = if (showKeyPlaintext) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.settings_byok_toggle_visibility)
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                )

                // Validation status feedback
                when (uiState.byokValidationStatus) {
                    ApiKeyValidationStatus.TESTING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = stringResource(R.string.settings_byok_status_testing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    ApiKeyValidationStatus.VALID -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource(R.string.settings_byok_status_valid),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldGreen
                            )
                        }
                    }
                    ApiKeyValidationStatus.INVALID -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = CoralRed, modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource(R.string.settings_byok_status_invalid),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = CoralRed
                            )
                        }
                    }
                    ApiKeyValidationStatus.IDLE -> Unit
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTestAndSaveByokKey(apiKeyInput)
                            apiKeyInput = ""
                        },
                        enabled = apiKeyInput.isNotBlank() && uiState.byokValidationStatus != ApiKeyValidationStatus.TESTING,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VividViolet),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_byok_save_button")
                    ) {
                        Text(
                            text = stringResource(R.string.settings_byok_btn_test_save),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (uiState.byokMaskedKey.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onClearByokKey()
                                apiKeyInput = ""
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed),
                            modifier = Modifier.testTag("settings_byok_clear_button")
                        ) {
                            Text(
                                text = stringResource(R.string.settings_byok_btn_clear),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_byok_get_key_button")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.settings_byok_btn_get_key),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
