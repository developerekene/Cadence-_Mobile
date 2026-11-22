package com.devekene.kalenda.ui.screens

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.devekene.kalenda.R
import com.devekene.kalenda.domain.model.ReminderPresets
import com.devekene.kalenda.domain.model.ThemeMode
import com.devekene.kalenda.domain.util.DateTimeUtils
import com.devekene.kalenda.security.BiometricCapability
import com.devekene.kalenda.ui.CalendarUiState
import com.devekene.kalenda.ui.components.PinSetupDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: CalendarUiState,
    biometricCapability: BiometricCapability,
    onNavigateBack: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSaveGoogleEmail: (String) -> Unit,
    onSyncGoogleCalendar: (String) -> Unit,
    onExportIcsToGoogle: () -> Unit,
    onActivateBiometricPin: (String) -> Unit,
    onUpdateSecurityPin: (String) -> Unit,
    onRequestDisableBiometricLock: () -> Unit,
    onLockAppNow: () -> Unit,
    onSelectDefaultReminder: (Int) -> Unit,
    onSetNotificationPhases: (Boolean, Boolean, Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onPushLifecycleNotificationsNow: () -> Unit,
    onExportBackup: (String) -> Unit,
    onClearExportedPayload: () -> Unit,
    onImportBackup: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var emailInput by remember(uiState.preferences.googleSyncEmail) {
        mutableStateOf(uiState.preferences.googleSyncEmail)
    }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Google Calendar Sync by Google Email
            item {
                SettingsSectionCard(
                    title = "Google Calendar Sync",
                    icon = Icons.Outlined.CloudSync
                ) {
                    Text(
                        text = "Add your Google email address to sync Kalenda events with your Google Calendar account on this device or export an .ics file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Google email address") },
                        placeholder = { Text("you@gmail.com") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.Email, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_sync_email_input")
                    )

                    if (uiState.preferences.lastGoogleSyncMillis > 0L) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Last synced: ${DateTimeUtils.formatTime(uiState.preferences.lastGoogleSyncMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!uiState.googleSyncStatusMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = uiState.googleSyncStatusMessage,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onSaveGoogleEmail(emailInput)
                                onSyncGoogleCalendar(emailInput)
                            },
                            enabled = !uiState.isSyncingGoogle && emailInput.contains("@"),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sync_google_calendar_button")
                        ) {
                            if (uiState.isSyncingGoogle) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Save & Sync")
                        }
                        OutlinedButton(
                            onClick = onExportIcsToGoogle,
                            modifier = Modifier.testTag("export_ics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share .ics")
                        }
                    }
                }
            }

            // 2. Biometrics & 6-Digit PIN Access Control
            item {
                SettingsSectionCard(
                    title = "Biometric & 6-Digit PIN Lock",
                    icon = Icons.Outlined.Fingerprint
                ) {
                    Text(
                        text = when (biometricCapability) {
                            BiometricCapability.AVAILABLE ->
                                "Device biometrics ready. Enable launch lock to require biometrics or your 6-digit PIN when opening Kalenda."
                            BiometricCapability.NONE_ENROLLED ->
                                "No biometrics enrolled in Android Settings yet. Your 6-digit PIN still protects app launch and locked events."
                            BiometricCapability.UNAVAILABLE ->
                                "Biometric sensor unavailable on this device. Your 6-digit PIN will be used to unlock."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Require Biometrics / PIN on Launch",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (uiState.preferences.biometricAppLockEnabled) {
                                    "Active • 6-digit PIN configured"
                                } else {
                                    "Off • Tap to set a 6-digit PIN & enable"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.preferences.biometricAppLockEnabled,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable) {
                                    showPinSetupDialog = true
                                } else {
                                    onRequestDisableBiometricLock()
                                }
                            },
                            modifier = Modifier.testTag("biometric_launch_lock_switch")
                        )
                    }

                    if (uiState.preferences.biometricAppLockEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { showChangePinDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("change_pin_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Pin,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Change 6-Digit PIN")
                            }
                            OutlinedButton(
                                onClick = onLockAppNow,
                                modifier = Modifier.testTag("test_lock_screen_button")
                            ) {
                                Text("Lock Now")
                            }
                        }
                    }
                }
            }

            // 3. Push Notifications (Upcoming, Current, Past)
            item {
                SettingsSectionCard(
                    title = "Push Notifications (Upcoming, Current & Past)",
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
                                "Grant notification permission to receive schedule alerts"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    PhaseSwitchRow(
                        label = "Upcoming events",
                        subtitle = "Alert before an event starts based on reminder lead time",
                        checked = uiState.preferences.notifyUpcoming,
                        onCheckedChange = { checked ->
                            onSetNotificationPhases(
                                checked,
                                uiState.preferences.notifyCurrent,
                                uiState.preferences.notifyPast
                            )
                        },
                        testTag = "notify_upcoming_switch"
                    )
                    PhaseSwitchRow(
                        label = "Current events",
                        subtitle = "Alert right when a scheduled event goes live",
                        checked = uiState.preferences.notifyCurrent,
                        onCheckedChange = { checked ->
                            onSetNotificationPhases(
                                uiState.preferences.notifyUpcoming,
                                checked,
                                uiState.preferences.notifyPast
                            )
                        },
                        testTag = "notify_current_switch"
                    )
                    PhaseSwitchRow(
                        label = "Past events",
                        subtitle = "Alert when an event concludes and moves to past history",
                        checked = uiState.preferences.notifyPast,
                        onCheckedChange = { checked ->
                            onSetNotificationPhases(
                                uiState.preferences.notifyUpcoming,
                                uiState.preferences.notifyCurrent,
                                checked
                            )
                        },
                        testTag = "notify_past_switch"
                    )

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
                            onClick = onPushLifecycleNotificationsNow,
                            modifier = Modifier.testTag("push_lifecycle_notifications_button")
                        ) {
                            Text("Push Status Alerts Now")
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

            // 4. Theme Appearance
            item {
                SettingsSectionCard(
                    title = "Appearance",
                    icon = Icons.Outlined.Palette
                ) {
                    Text(
                        text = "Choose Dark Purple, Light Lilac, or follow your system theme.",
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

            // 5. End-to-End Encrypted Storage & Portable Backups
            item {
                SettingsSectionCard(
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
                    Spacer(modifier = Modifier.height(10.dp))
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

    if (showPinSetupDialog) {
        PinSetupDialog(
            title = "Activate Biometrics & 6-Digit PIN",
            subtitle = "Create a 6-digit PIN backup. You will unlock Kalenda with device biometrics or this PIN on launch.",
            onDismiss = { showPinSetupDialog = false },
            onConfirmPin = { pin ->
                showPinSetupDialog = false
                onActivateBiometricPin(pin)
            }
        )
    }

    if (showChangePinDialog) {
        PinSetupDialog(
            title = "Change 6-Digit PIN",
            subtitle = "Enter and confirm your new 6-digit backup PIN.",
            onDismiss = { showChangePinDialog = false },
            onConfirmPin = { pin ->
                showChangePinDialog = false
                onUpdateSecurityPin(pin)
            }
        )
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
                        label = { Text("Encrypted KALENDA_E2EE_V1 payload") },
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
private fun PhaseSwitchRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SettingsSectionCard(
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
