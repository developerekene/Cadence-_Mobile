package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.components.EventDetailSheet
import com.example.ui.components.EventEditorSheet
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.VaultSettingsScreen
import com.example.ui.theme.CadenceTheme

object CadenceRoutes {
    const val ROUTE_SCHEDULE = "schedule"
    const val ROUTE_SEARCH = "search"
    const val ROUTE_VAULT = "vault"
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
        route = CadenceRoutes.ROUTE_SCHEDULE,
        labelRes = R.string.nav_schedule,
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        testTag = "nav_schedule"
    ),
    NavDestinationItem(
        route = CadenceRoutes.ROUTE_SEARCH,
        labelRes = R.string.nav_search,
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
        testTag = "nav_search"
    ),
    NavDestinationItem(
        route = CadenceRoutes.ROUTE_VAULT,
        labelRes = R.string.nav_vault,
        selectedIcon = Icons.Filled.Lock,
        unselectedIcon = Icons.Outlined.Lock,
        testTag = "nav_vault"
    )
)

@Composable
fun CadenceApp(
    viewModel: CalendarViewModel,
    initialEventId: String? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.refreshPermissionState() }
    )

    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.refreshPermissionState()
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

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CadenceTheme(themeMode = uiState.preferences.themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                floatingActionButton = {
                    val isVault = currentDestination?.route == CadenceRoutes.ROUTE_VAULT
                    if (!isVault) {
                        FloatingActionButton(
                            onClick = { viewModel.openNewEventEditor() },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
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
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
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
                                    modifier = Modifier.testTag(item.testTag)
                                )
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
                    if (useRail) {
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
                            startDestination = CadenceRoutes.ROUTE_SCHEDULE
                        ) {
                            composable(CadenceRoutes.ROUTE_SCHEDULE) {
                                ScheduleScreen(
                                    uiState = uiState,
                                    onSelectDay = viewModel::selectDay,
                                    onShiftMonth = viewModel::shiftMonth,
                                    onJumpToday = viewModel::jumpToToday,
                                    onSelectCategory = viewModel::selectCategoryFilter,
                                    onSetViewMode = viewModel::setViewMode,
                                    onToggleTheme = viewModel::cycleThemeMode,
                                    onCreateEvent = { hour ->
                                        viewModel.openNewEventEditor(
                                            day = uiState.selectedDay,
                                            defaultHour = hour
                                        )
                                    },
                                    onEventClick = viewModel::openEventDetail,
                                    onRescheduleByDrag = viewModel::rescheduleEventByDrag
                                )
                            }
                            composable(CadenceRoutes.ROUTE_SEARCH) {
                                SearchScreen(
                                    uiState = uiState,
                                    onQueryChange = viewModel::updateSearchQuery,
                                    onTogglePastOnly = viewModel::toggleSearchPastOnly,
                                    onEventClick = viewModel::openEventDetail
                                )
                            }
                            composable(CadenceRoutes.ROUTE_VAULT) {
                                VaultSettingsScreen(
                                    uiState = uiState,
                                    onSelectThemeMode = viewModel::setThemeMode,
                                    onSelectDefaultReminder = viewModel::setDefaultReminderMinutes,
                                    onRequestNotificationPermission = requestNotificationPermission,
                                    onSendTestNotification = viewModel::sendTestNotification,
                                    onExportBackup = viewModel::exportEncryptedVault,
                                    onClearExportedPayload = viewModel::clearExportedPayload,
                                    onImportBackup = viewModel::importEncryptedVault
                                )
                            }
                        }
                    }
                }
            }

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
                    onShiftOneDay = viewModel::shiftEventOneDay
                )
            }
        }
    }
}
