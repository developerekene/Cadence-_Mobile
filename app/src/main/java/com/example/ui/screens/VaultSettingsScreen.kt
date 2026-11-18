package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.ReminderPresets
import com.example.domain.model.ThemeMode
import com.example.ui.CalendarUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultSettingsScreen(
    uiState: CalendarUiState,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectDefaultReminder: (Int) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSendTestNotification: () -> Unit,
    onExportBackup: (String) -> Unit,
    onClearExportedPayload: () -> Unit,
    onImportBackup: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Vault & Preferences",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Offline-first storage, reminder alerts, dark mode, and AES-256 encryption.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Appearance Card
            item {
                VaultSectionCard(
                    title = "Appearance",
                    icon = Icons.Outlined.Palette
                ) {
                    Text(
                        text = "Choose light, dark, or match your device theme.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = uiState.preferences.themeMode == mode,
                                onClick = { onSelectThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = ThemeMode.entries.size
                                ),
                                modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }

            // Notifications & Reminders Card
            item {
                VaultSectionCard(
                    title = "Reminders & Push Notifications",
                    icon = Icons.Outlined.NotificationsActive
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.hasNotificationPermission) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.WarningAmber
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (uiState.hasNotificationPermission) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }
                        )
                        Text(
                            text = if (uiState.hasNotificationPermission) {
                                "Push notifications enabled • Exact alarms ${if (uiState.canScheduleExactAlarms) "active" else "standard"}"
                            } else {
                                "Notification permission needed for upcoming event alerts"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!uiState.hasNotificationPermission) {
                            Button(
                                onClick = onRequestNotificationPermission,
                                modifier = Modifier.testTag("grant_notifications_button")
                            ) {
                                Text("Enable Alerts")
                            }
                        }
                        OutlinedButton(
                            onClick = onSendTestNotification,
                            modifier = Modifier.testTag("test_notification_button")
                        ) {
                            Text("Send Test Alert")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Default reminder for new events",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ReminderPresets.NONE,
                            ReminderPresets.FIVE_MIN,
                            ReminderPresets.FIFTEEN_MIN,
                            ReminderPresets.THIRTY_MIN,
                            ReminderPresets.ONE_HOUR
                        ).forEach { preset ->
                            FilterChip(
                                selected = uiState.preferences.defaultReminderMinutes == preset,
                                onClick = { onSelectDefaultReminder(preset) },
                                label = { Text(ReminderPresets.formatLabel(preset)) }
                            )
                        }
                    }
                }
            }

            // End-to-End Encryption & Offline Sync Status Card
            item {
                VaultSectionCard(
                    title = "End-to-End Encrypted Storage",
                    icon = Icons.Outlined.Security
                ) {
                    InfoRow(
                        label = "Cipher",
                        value = "AES-256-GCM (${if (uiState.encryptionStats.isHardwareBacked) "Android Keystore" else "Local Key"})"
                    )
                    InfoRow(
                        label = "Key Fingerprint",
                        value = uiState.encryptionStats.keyFingerprint
                    )
                    InfoRow(
                        label = "Encrypted Records",
                        value = "${uiState.encryptionStats.totalEncryptedEvents} events protected"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = uiState.encryptionStats.syncProviderLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showExportDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_vault_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export E2EE")
                        }
                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_vault_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import E2EE")
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        var passphrase by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                onClearExportedPayload()
                showExportDialog = false
            },
            icon = { Icon(imageVector = Icons.Outlined.Key, contentDescription = null) },
            title = { Text("Export Encrypted Vault") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (uiState.exportedBackupPayload == null) {
                        Text(
                            text = "Enter a passphrase (min 6 chars). Your events will be encrypted with PBKDF2 + AES-256-GCM.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text("Backup passphrase") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    } else {
                        Text(
                            text = "Encrypted payload ready. Copy and store it safely:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = uiState.exportedBackupPayload,
                            onValueChange = {},
                            readOnly = true,
                            maxLines = 5
                        )
                    }
                }
            },
            confirmButton = {
                if (uiState.exportedBackupPayload == null) {
                    TextButton(
                        enabled = passphrase.length >= 6,
                        onClick = { onExportBackup(passphrase) }
                    ) { Text("Encrypt & Export") }
                } else {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(uiState.exportedBackupPayload))
                        onClearExportedPayload()
                        showExportDialog = false
                    }) { Text("Copy & Close") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onClearExportedPayload()
                    showExportDialog = false
                }) { Text("Close") }
            }
        )
    }

    if (showImportDialog) {
        var payloadInput by remember { mutableStateOf("") }
        var passphrase by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            icon = { Icon(imageVector = Icons.Outlined.Download, contentDescription = null) },
            title = { Text("Import Encrypted Vault") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = payloadInput,
                        onValueChange = { payloadInput = it },
                        label = { Text("Encrypted CADENCE_E2EE_V1 payload") },
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Backup passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = payloadInput.isNotBlank() && passphrase.length >= 6,
                    onClick = {
                        onImportBackup(payloadInput, passphrase)
                        showImportDialog = false
                    }
                ) { Text("Decrypt & Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun VaultSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
