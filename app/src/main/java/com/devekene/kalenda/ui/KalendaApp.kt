package com.devekene.kalenda.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.devekene.kalenda.R
import com.devekene.kalenda.domain.model.CalendarEvent
import com.devekene.kalenda.security.BiometricCapability
import com.devekene.kalenda.security.BiometricHelper
import com.devekene.kalenda.ui.components.EventDetailSheet
import com.devekene.kalenda.ui.components.EventEditorSheet
import com.devekene.kalenda.ui.components.LockScreenOverlay
import com.devekene.kalenda.ui.components.PinVerifyDialog
import com.devekene.kalenda.ui.components.TopSlideMenu
import com.devekene.kalenda.ui.screens.EventsVaultScreen
import com.devekene.kalenda.ui.screens.ScheduleScreen
import com.devekene.kalenda.ui.screens.SearchScreen
import com.devekene.kalenda.ui.screens.SettingsScreen
import com.devekene.kalenda.ui.theme.KalendaTheme
import kotlinx.coroutines.launch

object KalendaRoutes {
    const val ROUTE_SCHEDULE = "schedule"
    const val ROUTE_SEARCH = "search"
    const val ROUTE_EVENTS = "events"
    const val ROUTE_SETTINGS = "settings"
}

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val navItems = listOf(
    NavDestinationItem(
        route = KalendaRoutes.ROUTE_SCHEDULE,
        labelRes = R.string.nav_schedule,
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        testTag = "nav_schedule"
    ),
    NavDestinationItem(
        route = KalendaRoutes.ROUTE_SEARCH,
        labelRes = R.string.nav_search,
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
        testTag = "nav_search"
    ),
    NavDestinationItem(
        route = KalendaRoutes.ROUTE_EVENTS,
        labelRes = R.string.nav_events,
        selectedIcon = Icons.Filled.EventNote,
        unselectedIcon = Icons.Outlined.EventNote,
        testTag = "nav_events"
    )
)

@Composable
fun KalendaApp(
    viewModel: CalendarViewModel,
    initialEventId: String? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val coroutineScope = rememberCoroutineScope()

    val biometricCapability = remember(context) {
        BiometricHelper.checkCapability(context)
    }

    var isTopMenuOpen by remember { mutableStateOf(false) }
    var pendingEmailSync by remember { mutableStateOf<String?>(null) }
    var pendingPinVerifyTitle by remember { mutableStateOf<String?>(null) }
    var pendingPinVerifyAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.refreshPermissionState() }
    )

    val calendarPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grants ->
            viewModel.refreshPermissionState()
            val readGranted = grants[Manifest.permission.READ_CALENDAR] == true
            val writeGranted = grants[Manifest.permission.WRITE_CALENDAR] == true
            val email = pendingEmailSync
            pendingEmailSync = null
            if (readGranted && writeGranted && email != null) {
                viewModel.syncWithGoogleCalendar(email)
            } else if (!readGranted || !writeGranted) {
                viewModel.postSnackbar("Calendar permissions are needed for direct Google Calendar sync (or use Share .ics)")
            }
        }
    )

    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.refreshPermissionState()
        }
    }

    val runWithDeviceVerification: (String, String, () -> Unit) -> Unit = { title, subtitle, onVerified ->
        val hasPin = uiState.preferences.pinHash.isNotBlank() && uiState.preferences.pinSalt.isNotBlank()
        if (activity != null && biometricCapability == BiometricCapability.AVAILABLE) {
            BiometricHelper.authenticate(
                activity = activity,
                title = title,
                subtitle = subtitle,
                negativeButtonText = if (hasPin) "Use 6-digit PIN" else "Cancel",
                onSuccess = onVerified,
                onFallbackOrError = { err ->
                    if (hasPin) {
                        pendingPinVerifyTitle = title
                        pendingPinVerifyAction = onVerified
                    } else {
                        viewModel.postSnackbar(err)
                    }
                }
            )
        } else if (hasPin) {
            pendingPinVerifyTitle = title
            pendingPinVerifyAction = onVerified
        } else {
            viewModel.postSnackbar("Enroll device biometrics or activate a 6-digit PIN in Settings to lock/unlock events")
        }
    }

    val handleEventClick: (CalendarEvent) -> Unit = { event ->
        if (event.isBiometricLocked && event.id !in uiState.unlockedEventIds) {
            runWithDeviceVerification(
                "Unlock Event",
                "Verify with biometrics to view \"${event.category.displayName}\" event"
            ) {
                viewModel.markEventUnlockedAndOpen(event)
            }
        } else {
            viewModel.openEventDetail(event)
        }
    }

    LaunchedEffect(initialEventId) {
        if (!initialEventId.isNullOrBlank()) {
            viewModel.openEventById(initialEventId)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    if (isTopMenuOpen) {
        BackHandler { isTopMenuOpen = false }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isSettingsRoute = currentDestination?.route == KalendaRoutes.ROUTE_SETTINGS

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    KalendaTheme(themeMode = uiState.preferences.themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                floatingActionButton = {
                    val isSearch = currentDestination?.route == KalendaRoutes.ROUTE_SEARCH
                    if (!isSearch && !isSettingsRoute && !uiState.requiresLaunchUnlock) {
                        FloatingActionButton(
                            onClick = { viewModel.openNewEventEditor() },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.testTag("create_event_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.cd_create_event)
                            )
                        }
                    }
                },
                bottomBar = {
                    if (!useRail && !isSettingsRoute && !uiState.requiresLaunchUnlock) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(26.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 4.dp,
                                shadowElevation = 10.dp,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                                )
                            ) {
                                NavigationBar(
                                    modifier = Modifier.height(68.dp),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    windowInsets = WindowInsets(0, 0, 0, 0)
                                ) {
                                    navItems.forEach { item ->
                                        val selected = currentDestination?.hierarchy?.any {
                                            it.route == item.route
                                        } == true
                                        val label = stringResource(item.labelRes)
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigateTo(item.route) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = label
                                                )
                                            },
                                            label = { Text(label) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.testTag(item.testTag)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail && !isSettingsRoute && !uiState.requiresLaunchUnlock) {
                        NavigationRail(
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding(),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            navItems.forEach { item ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.route == item.route
                                } == true
                                val label = stringResource(item.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = KalendaRoutes.ROUTE_SCHEDULE
                        ) {
                            composable(KalendaRoutes.ROUTE_SCHEDULE) {
                                ScheduleScreen(
                                    uiState = uiState,
                                    onSelectDay = viewModel::selectDay,
                                    onShiftMonth = viewModel::shiftMonth,
                                    onJumpToday = viewModel::jumpToToday,
                                    onSelectCategory = viewModel::selectCategoryFilter,
                                    onSetViewMode = viewModel::setViewMode,
                                    onOpenTopMenu = { isTopMenuOpen = true },
                                    onCreateEvent = { hour ->
                                        viewModel.openNewEventEditor(
                                            day = uiState.selectedDay,
                                            defaultHour = hour
                                        )
                                    },
                                    onEventClick = handleEventClick,
                                    onRescheduleByDrag = viewModel::rescheduleEventByDrag
                                )
                            }
                            composable(KalendaRoutes.ROUTE_SEARCH) {
                                SearchScreen(
                                    uiState = uiState,
                                    onQueryChange = viewModel::updateSearchQuery,
                                    onTogglePastOnly = viewModel::toggleSearchPastOnly,
                                    onEventClick = handleEventClick
                                )
                            }
                            composable(KalendaRoutes.ROUTE_EVENTS) {
                                EventsVaultScreen(
                                    uiState = uiState,
                                    onEventClick = handleEventClick,
                                    onCreateEvent = { viewModel.openNewEventEditor() },
                                    onOpenSettings = { navController.navigate(KalendaRoutes.ROUTE_SETTINGS) }
                                )
                            }
                            composable(KalendaRoutes.ROUTE_SETTINGS) {
                                BackHandler { navController.popBackStack() }
                                SettingsScreen(
                                    uiState = uiState,
                                    biometricCapability = biometricCapability,
                                    onNavigateBack = { navController.popBackStack() },
                                    onSelectThemeMode = viewModel::setThemeMode,
                                    onSaveGoogleEmail = viewModel::setGoogleSyncEmail,
                                    onSyncGoogleCalendar = { email ->
                                        if (uiState.hasCalendarPermissions) {
                                            viewModel.syncWithGoogleCalendar(email)
                                        } else {
                                            pendingEmailSync = email
                                            calendarPermissionsLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.READ_CALENDAR,
                                                    Manifest.permission.WRITE_CALENDAR
                                                )
                                            )
                                        }
                                    },
                                    onExportIcsToGoogle = {
                                        coroutineScope.launch {
                                            val ics = viewModel.buildIcsExportPayload()
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/calendar"
                                                putExtra(Intent.EXTRA_SUBJECT, "Kalenda Schedule (.ics)")
                                                putExtra(Intent.EXTRA_TEXT, ics)
                                                if (uiState.preferences.googleSyncEmail.isNotBlank()) {
                                                    putExtra(Intent.EXTRA_EMAIL, arrayOf(uiState.preferences.googleSyncEmail))
                                                }
                                            }
                                            runCatching {
                                                context.startActivity(Intent.createChooser(shareIntent, "Export to Google Calendar (.ics)"))
                                            }
                                        }
                                    },
                                    onActivateBiometricPin = viewModel::activateBiometricPinSecurity,
                                    onUpdateSecurityPin = viewModel::updateSecurityPin,
                                    onRequestDisableBiometricLock = {
                                        runWithDeviceVerification(
                                            "Disable Launch Lock",
                                            "Confirm identity to turn off launch lock"
                                        ) {
                                            viewModel.disableBiometricPinSecurity()
                                        }
                                    },
                                    onLockAppNow = viewModel::lockAppNow,
                                    onSelectDefaultReminder = viewModel::setDefaultReminderMinutes,
                                    onSetNotificationPhases = viewModel::setNotificationPhases,
                                    onRequestNotificationPermission = requestNotificationPermission,
                                    onPushLifecycleNotificationsNow = viewModel::triggerLifecycleStatusNotifications,
                                    onExportBackup = viewModel::exportEncryptedVault,
                                    onClearExportedPayload = viewModel::clearExportedPayload,
                                    onImportBackup = viewModel::importEncryptedVault
                                )
                            }
                        }
                    }
                }
            }

            TopSlideMenu(
                visible = isTopMenuOpen && !uiState.requiresLaunchUnlock,
                themeMode = uiState.preferences.themeMode,
                googleSyncEmail = uiState.preferences.googleSyncEmail,
                biometricAppLockEnabled = uiState.preferences.biometricAppLockEnabled,
                onDismiss = { isTopMenuOpen = false },
                onToggleTheme = viewModel::cycleThemeMode,
                onOpenSettings = { navController.navigate(KalendaRoutes.ROUTE_SETTINGS) },
                onLockAppNow = viewModel::lockAppNow
            )

            uiState.editingDraft?.let { draft ->
                EventEditorSheet(
                    draft = draft,
                    onDismiss = viewModel::closeEventEditor,
                    onSave = { event ->
                        if (event.reminderMinutesBefore >= 0 && !uiState.hasNotificationPermission) {
                            requestNotificationPermission()
                        }
                        viewModel.saveEvent(event)
                    },
                    onDelete = viewModel::deleteEvent
                )
            }

            uiState.viewingEvent?.let { event ->
                EventDetailSheet(
                    event = event,
                    onDismiss = viewModel::closeEventDetail,
                    onEdit = viewModel::openEditEventEditor,
                    onDelete = viewModel::deleteEvent,
                    onShiftOneDay = viewModel::shiftEventOneDay,
                    onToggleBiometricLock = { targetEvent ->
                        val nextLocked = !targetEvent.isBiometricLocked
                        runWithDeviceVerification(
                            if (nextLocked) "Lock Event with Biometrics" else "Remove Event Biometric Lock",
                            "Authenticate to update lock protection for \"${targetEvent.title}\""
                        ) {
                            viewModel.setEventBiometricLocked(targetEvent, nextLocked)
                        }
                    },
                    onAddToGoogleCalendar = { targetEvent ->
                        val insertIntent = viewModel.buildGoogleCalendarInsertIntent(targetEvent)
                        runCatching {
                            context.startActivity(insertIntent)
                        }.onFailure {
                            viewModel.postSnackbar("No external calendar app installed; sync via Settings > Google Calendar")
                        }
                    }
                )
            }

            pendingPinVerifyTitle?.let { dialogTitle ->
                PinVerifyDialog(
                    title = dialogTitle,
                    subtitle = "Enter your 6-digit PIN to continue",
                    onDismiss = {
                        pendingPinVerifyTitle = null
                        pendingPinVerifyAction = null
                    },
                    onVerifyPin = viewModel::verifyConfiguredPin,
                    onSuccess = {
                        val action = pendingPinVerifyAction
                        pendingPinVerifyTitle = null
                        pendingPinVerifyAction = null
                        action?.invoke()
                    }
                )
            }

            if (uiState.requiresLaunchUnlock) {
                LockScreenOverlay(
                    hasPinConfigured = uiState.preferences.pinHash.isNotBlank(),
                    biometricAvailable = biometricCapability == BiometricCapability.AVAILABLE,
                    onRequestBiometrics = {
                        if (activity != null) {
                            BiometricHelper.authenticate(
                                activity = activity,
                                title = "Unlock Kalenda",
                                subtitle = "Verify with device biometrics to view your schedules",
                                negativeButtonText = "Use 6-digit PIN",
                                onSuccess = { viewModel.unlockAppWithBiometrics() },
                                onFallbackOrError = { msg ->
                                    if (uiState.preferences.pinHash.isBlank()) {
                                        viewModel.postSnackbar(msg)
                                    }
                                }
                            )
                        }
                    },
                    onVerifyPin = viewModel::verifyLaunchPin
                )
            }
        }
    }
}
