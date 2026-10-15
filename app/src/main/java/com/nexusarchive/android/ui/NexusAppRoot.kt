package com.nexusarchive.android.ui

import com.nexusarchive.android.ui.components.LocalBarsCollapsed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import com.nexusarchive.android.ui.components.buildLocationOptions
import com.nexusarchive.android.NexusApplication
import androidx.compose.foundation.layout.navigationBars
import com.nexusarchive.android.ui.components.LocalFloatingBarInset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexusarchive.android.data.AppSettings
import com.nexusarchive.android.ui.archive.ArchiveScreen
import com.nexusarchive.android.ui.browse.BrowseScreen
import com.nexusarchive.android.ui.browse.BrowserViewModel
import com.nexusarchive.android.ui.browse.FileActions
import com.nexusarchive.android.ui.components.rememberStoragePermission
import com.nexusarchive.android.ui.home.HomeScreen
import com.nexusarchive.android.ui.home.HomeViewModel
import com.nexusarchive.android.ui.operations.OperationsScreen
import com.nexusarchive.android.ui.operations.OperationsViewModel
import com.nexusarchive.android.ui.settings.SettingsScreen
import com.nexusarchive.android.ui.components.FloatingNavBar
import com.nexusarchive.android.ui.components.NavItem
import com.nexusarchive.android.ui.search.SearchScreen
import com.nexusarchive.android.ui.storage.SafBrowseScreen
import com.nexusarchive.android.ui.storage.StorageScreen
import com.nexusarchive.android.ui.settings.SettingsViewModel
import com.nexusarchive.android.ui.settings.systemAnimationsDisabled
import com.nexusarchive.android.ui.theme.LocalReduceMotion
import com.nexusarchive.android.ui.theme.NexusTheme
import java.io.File

private const val ROUTE_ARCHIVE = "archive"
private const val ROUTE_STORAGE = "storage"
private const val ROUTE_SAF = "saf"
private const val ROUTE_SEARCH = "search"

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Rounded.Home),
    Browse("browse", "Browse", Icons.Rounded.Folder),
    Operations("operations", "Operations", Icons.Rounded.SwapVert),
    Settings("settings", "Settings", Icons.Rounded.Settings),
}

@Composable
fun NexusAppRoot() {
    val settingsVm: SettingsViewModel = viewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val current = settings
    val context = LocalContext.current
    val systemAnimationsOff = remember { systemAnimationsDisabled(context) }

    if (current == null) {
        // First DataStore read is still in flight (a few milliseconds); draw nothing rather than flash defaults.
        Box(Modifier.fillMaxSize())
    } else {
        CompositionLocalProvider(LocalReduceMotion provides (current.reduceMotion || systemAnimationsOff)) {
            NexusTheme(
                themeMode = current.themeMode,
                themeStyle = current.themeStyle,
                presetId = current.themePreset,
                liquidGlass = current.liquidGlass,
            ) {
                AppScaffold(current, settingsVm, systemAnimationsOff)
            }
        }
    }
}

@Composable
private fun AppScaffold(settings: AppSettings, settingsVm: SettingsViewModel, systemAnimationsOff: Boolean) {
    val context = LocalContext.current
    val reduce = LocalReduceMotion.current
    val navController = rememberNavController()
    val homeVm: HomeViewModel = viewModel()
    val browserVm: BrowserViewModel = viewModel()
    val operationsVm: OperationsViewModel = viewModel()
    val permission = rememberStoragePermission()
    // Sub-screens get their target from saved state instead of the route, so file paths never go through URL encoding.
    var archivePath by rememberSaveable { mutableStateOf("") }
    var safStorageId by rememberSaveable { mutableStateOf("") }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val operations by operationsVm.operations.collectAsStateWithLifecycle()
    val activeCount = operations.count { it.isActive }

    // Ask for notification permission once, in context: the first time an operation actually starts.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(activeCount) {
        if (activeCount > 0 && !askedForNotifications && settings.notifications &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotifications = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val tabs = remember { listOf(Destination.Home, Destination.Browse, Destination.Operations) }
    val currentTab = tabs.indexOfFirst { tab -> currentDestination?.hierarchy?.any { it.route == tab.route } == true }
    var lastTab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(currentTab) { if (currentTab >= 0) lastTab = currentTab }
    val selectedIndex = if (currentTab >= 0) currentTab else lastTab
    val onSearchRoute = currentDestination?.route == ROUTE_SEARCH

    fun navigate(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openSearch() {
        browserVm.ensureStarted()
        navController.navigate(ROUTE_SEARCH) { launchSingleTop = true }
    }

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduce) EnterTransition.None else fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.96f)
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduce) ExitTransition.None else fadeOut(tween(120))
    }

    val navHost: @Composable () -> Unit = {
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            enterTransition = enter,
            exitTransition = exit,
            popEnterTransition = enter,
            popExitTransition = exit,
        ) {
            composable(Destination.Home.route) {
                HomeScreen(
                    vm = homeVm,
                    permission = permission,
                    onOpenFolder = { path ->
                        browserVm.navigateTo(path)
                        navigate(Destination.Browse)
                    },
                    onOpenFile = { path ->
                        val error = FileActions.open(context, File(path), chooser = false)
                        if (error == null) browserVm.recordRecentFile(path)
                        error
                    },
                    onSearch = ::openSearch,
                    onOpenOperations = { navigate(Destination.Operations) },
                    onOpenStorage = { navController.navigate(ROUTE_STORAGE) },
                    onOpenSafStorage = { id ->
                        safStorageId = id
                        navController.navigate(ROUTE_SAF)
                    },
                    onOpenSettings = { navigate(Destination.Settings) },
                )
            }
            composable(Destination.Browse.route) {
                BrowseScreen(
                    vm = browserVm,
                    settings = settings,
                    settingsVm = settingsVm,
                    permission = permission,
                    onOpenArchive = { path ->
                        archivePath = path
                        navController.navigate(ROUTE_ARCHIVE)
                    },
                    onOpenStorage = { navController.navigate(ROUTE_STORAGE) },
                    onOpenSafStorage = { id ->
                        safStorageId = id
                        navController.navigate(ROUTE_SAF)
                    },
                )
            }
            composable(ROUTE_SEARCH) {
                val browseUi by browserVm.ui.collectAsStateWithLifecycle()
                SearchScreen(
                    currentFolder = browseUi.currentPath,
                    volumes = browseUi.volumes,
                    quickAccess = settings.bookmarks,
                    onBack = { navController.popBackStack() },
                    onOpenFolder = { path ->
                        browserVm.navigateTo(path)
                        navigate(Destination.Browse)
                    },
                    onOpenArchive = { path ->
                        archivePath = path
                        navController.navigate(ROUTE_ARCHIVE)
                    },
                    onRecentFile = { browserVm.recordRecentFile(it) },
                )
            }
            composable(ROUTE_ARCHIVE) {
                val browseUi by browserVm.ui.collectAsStateWithLifecycle()
                val safBookmarks by remember { (context.applicationContext as NexusApplication).safStorage.bookmarks }
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val locations = remember(browseUi.volumes, settings.bookmarks, safBookmarks) {
                    buildLocationOptions(browseUi.volumes, settings.bookmarks, safBookmarks)
                }
                ArchiveScreen(
                    path = archivePath,
                    locations = locations,
                    onPickLocation = { option ->
                        navController.popBackStack()
                        if (option.path != null) {
                            browserVm.navigateTo(option.path)
                            navigate(Destination.Browse)
                        } else if (option.safId != null) {
                            safStorageId = option.safId
                            navController.navigate(ROUTE_SAF)
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onOpenOperations = { navigate(Destination.Operations) },
                    onCopyToClipboard = { paths -> browserVm.copyPaths(paths) },
                )
            }
            composable(ROUTE_STORAGE) {
                StorageScreen(
                    onBack = { navController.popBackStack() },
                    onOpenStorage = { id ->
                        safStorageId = id
                        navController.navigate(ROUTE_SAF)
                    },
                )
            }
            composable(ROUTE_SAF) {
                SafBrowseScreen(
                    storageId = safStorageId,
                    onBack = { navController.popBackStack() },
                    onManageStorage = { navController.navigate(ROUTE_STORAGE) },
                )
            }
            composable(Destination.Operations.route) {
                OperationsScreen(vm = operationsVm)
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    settings = settings,
                    vm = settingsVm,
                    permission = permission,
                    systemAnimationsOff = systemAnimationsOff,
                    onOpenStorage = { navController.navigate(ROUTE_STORAGE) },
                )
            }
        }
    }

    // One floating navigation bar for every screen size, drawn over the content so lists scroll behind it.
    // Screens add LocalFloatingBarInset as bottom padding so their last items stay reachable. The bar is hidden
    // on the full-screen search page, where the keyboard would otherwise push it up over the results.
    var barHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val systemNavPx = WindowInsets.navigationBars.getBottom(density)
    val barInset = if (onSearchRoute || barHeightPx == 0) 0.dp else with(density) { (barHeightPx - systemNavPx).coerceAtLeast(0).toDp() }

    // Moving further down a list tucks the floating buttons away and shrinks the pill; moving back up restores them.
    var barsCollapsed by remember { mutableStateOf(false) }
    LaunchedEffect(currentDestination?.route) { barsCollapsed = false }
    val scrollWatcher = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f) barsCollapsed = true else if (available.y > 6f) barsCollapsed = false
                return Offset.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(scrollWatcher),
    ) {
        CompositionLocalProvider(
            LocalFloatingBarInset provides barInset,
            LocalBarsCollapsed provides barsCollapsed,
        ) { navHost() }
        if (!onSearchRoute) {
            CompositionLocalProvider(LocalBarsCollapsed provides barsCollapsed) {
            FloatingNavBar(
                items = tabs.map { NavItem(it.label, it.icon, if (it == Destination.Operations) activeCount else 0) },
                selectedIndex = selectedIndex,
                onSelect = { navigate(tabs[it]) },
                onSearch = ::openSearch,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { barHeightPx = it.height },
            )
            }
        }
    }
}
