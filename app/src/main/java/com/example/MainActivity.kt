package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.MainViewModel
import com.example.ui.screens.AppsScreen
import com.example.ui.screens.CloneDetailScreen
import com.example.ui.screens.KernelScreen
import com.example.ui.screens.NetworkAndLinksScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.CloneCoreTheme
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextSecondary

object Routes {
    const val VAULT = "vault"
    const val APPS = "apps"
    const val KERNEL = "kernel"
    const val NETWORK = "network"
    const val CLONE_DETAIL = "clone_detail/{cloneId}"

    fun cloneDetail(cloneId: String): String = "clone_detail/$cloneId"
}

private data class TopNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TOP_NAV_ITEMS = listOf(
    TopNavItem(Routes.VAULT, "Clones", Icons.Filled.Layers, Icons.Outlined.Layers, "nav_vault"),
    TopNavItem(Routes.APPS, "Apps", Icons.Filled.Apps, Icons.Outlined.Apps, "nav_apps"),
    TopNavItem(Routes.KERNEL, "Activity", Icons.Filled.Speed, Icons.Outlined.Speed, "nav_kernel"),
    TopNavItem(Routes.NETWORK, "Network", Icons.Filled.Hub, Icons.Outlined.Hub, "nav_network")
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        viewModel.handleIncomingDeepLink(intent)

        setContent {
            CloneCoreTheme {
                CloneCoreRootApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIncomingDeepLink(intent)
    }
}

@Composable
fun CloneCoreRootApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val clones by viewModel.clones.collectAsStateWithLifecycle()
    val proxyNodes by viewModel.proxyNodes.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isScanningApps by viewModel.isScanningApps.collectAsStateWithLifecycle()
    val slotStates by viewModel.slotStates.collectAsStateWithLifecycle()
    val relayStatus by viewModel.relayStatus.collectAsStateWithLifecycle()
    val kernelSnapshot by viewModel.kernelSnapshot.collectAsStateWithLifecycle()
    val cpuHistory by viewModel.cpuHistory.collectAsStateWithLifecycle()
    val memHistory by viewModel.memHistory.collectAsStateWithLifecycle()
    val binderHooks by viewModel.binderHooks.collectAsStateWithLifecycle()
    val isMonitorActive by viewModel.isMonitorServiceActive.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val pendingDeepLink by viewModel.pendingDeepLinkCloneId.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        val msg = snackbarMsg ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    LaunchedEffect(pendingDeepLink) {
        val targetId = pendingDeepLink ?: return@LaunchedEffect
        viewModel.consumePendingDeepLink()
        navController.navigate(Routes.cloneDetail(targetId)) {
            launchSingleTop = true
        }
    }

    val isTopLevelRoute = TOP_NAV_ITEMS.any { it.route == currentRoute }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianCanvas,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                if (isTopLevelRoute && !useNavigationRail) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        TOP_NAV_ITEMS.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyanPulse,
                                    selectedTextColor = CyanPulse,
                                    indicatorColor = CyanContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
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
                if (isTopLevelRoute && useNavigationRail) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        TOP_NAV_ITEMS.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CyanPulse,
                                    selectedTextColor = CyanPulse,
                                    indicatorColor = CyanContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.VAULT,
                        modifier = Modifier
                            .widthIn(max = 780.dp)
                            .fillMaxSize()
                    ) {
                        composable(Routes.VAULT) {
                            VaultScreen(
                                viewModel = viewModel,
                                clones = clones,
                                slots = slotStates,
                                relayStatus = relayStatus,
                                onNavigateToApps = { navigateTopLevel(Routes.APPS) },
                                onNavigateToKernel = { navigateTopLevel(Routes.KERNEL) },
                                onOpenCloneDetail = { cloneId ->
                                    navController.navigate(Routes.cloneDetail(cloneId))
                                }
                            )
                        }

                        composable(Routes.APPS) {
                            AppsScreen(
                                viewModel = viewModel,
                                installedApps = installedApps,
                                isScanning = isScanningApps,
                                existingClones = clones,
                                proxyNodes = proxyNodes,
                                onDeployedNavigateToVault = { navigateTopLevel(Routes.VAULT) }
                            )
                        }

                        composable(Routes.KERNEL) {
                            KernelScreen(
                                viewModel = viewModel,
                                snapshot = kernelSnapshot,
                                cpuHistory = cpuHistory,
                                memHistory = memHistory,
                                slots = slotStates,
                                binderHooks = binderHooks,
                                auditLogs = auditLogs,
                                isMonitorActive = isMonitorActive
                            )
                        }

                        composable(Routes.NETWORK) {
                            NetworkAndLinksScreen(
                                viewModel = viewModel,
                                proxyNodes = proxyNodes,
                                clones = clones,
                                relayStatus = relayStatus,
                                onOpenCloneDetail = { cloneId ->
                                    navController.navigate(Routes.cloneDetail(cloneId))
                                }
                            )
                        }

                        composable(
                            route = Routes.CLONE_DETAIL,
                            arguments = listOf(navArgument("cloneId") { type = NavType.StringType })
                        ) { entry ->
                            val cloneId = entry.arguments?.getString("cloneId") ?: return@composable
                            CloneDetailScreen(
                                cloneId = cloneId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
