package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.CloneInstanceEntity
import com.example.data.db.ProxyNodeEntity
import com.example.engine.apps.InstalledAppInfo
import com.example.ui.DeployCloneConfig
import com.example.ui.MainViewModel
import com.example.ui.components.AppIconBadge
import com.example.ui.components.StatusPill
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldMatrix
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    viewModel: MainViewModel,
    installedApps: List<InstalledAppInfo>,
    isScanning: Boolean,
    existingClones: List<CloneInstanceEntity>,
    proxyNodes: List<ProxyNodeEntity>,
    onDeployedNavigateToVault: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("USER") } // ALL, USER, CLONED
    var selectedAppForClone by remember { mutableStateOf<InstalledAppInfo?>(null) }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectExternalApk(uri) { inspected ->
                if (inspected != null) {
                    selectedAppForClone = inspected
                }
            }
        }
    }

    val cloneCountsByPkg = remember(existingClones) {
        existingClones.groupingBy { it.sourcePackageName }.eachCount()
    }

    val filteredApps = remember(installedApps, searchQuery, filterMode, cloneCountsByPkg) {
        installedApps.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filterMode) {
                "USER" -> !app.isSystemApp
                "CLONED" -> (cloneCountsByPkg[app.packageName] ?: 0) > 0
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("apps_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Choose an app",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Select an installed app to create a separate space",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = {
                        apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("inspect_apk_saf_btn")
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("APK")
                }
                IconButton(
                    onClick = { viewModel.refreshInstalledApps() },
                    modifier = Modifier.testTag("refresh_apps_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan apps", tint = CyanPulse)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search installed apps") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apps_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filterMode == "USER",
                onClick = { filterMode = "USER" },
                label = { Text("Installed") },
                modifier = Modifier.testTag("filter_user_apps")
            )
            FilterChip(
                selected = filterMode == "ALL",
                onClick = { filterMode = "ALL" },
                label = { Text("All (${installedApps.size})") },
                modifier = Modifier.testTag("filter_all_apps")
            )
            FilterChip(
                selected = filterMode == "CLONED",
                onClick = { filterMode = "CLONED" },
                label = { Text("Cloned (${cloneCountsByPkg.size})") },
                modifier = Modifier.testTag("filter_cloned_apps")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isScanning && installedApps.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = CyanPulse)
                Spacer(modifier = Modifier.height(10.dp))
                Text("Looking for installed apps…")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { appInfo ->
                    val count = cloneCountsByPkg[appInfo.packageName] ?: 0
                    InstalledAppRowCard(
                        appInfo = appInfo,
                        existingCloneCount = count,
                        onCloneClick = { selectedAppForClone = appInfo }
                    )
                }
            }
        }
    }

    selectedAppForClone?.let { targetApp ->
        DeployCloneBottomSheet(
            app = targetApp,
            proxyNodes = proxyNodes,
            onDismiss = { selectedAppForClone = null },
            onDeploy = { config ->
                selectedAppForClone = null
                viewModel.deployClones(config) {
                    onDeployedNavigateToVault()
                }
            }
        )
    }
}

@Composable
private fun InstalledAppRowCard(
    appInfo: InstalledAppInfo,
    existingCloneCount: Int,
    onCloneClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("installed_app_${appInfo.packageName}"),
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconBadge(
                icon = appInfo.icon,
                cloneIndex = if (existingCloneCount > 0) existingCloneCount else null,
                size = 48.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appInfo.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        color = TextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(
                        text = "v${appInfo.versionName} · SDK ${appInfo.targetSdk}",
                        accentColor = CyanPulse
                    )
                    StatusPill(
                        text = "${appInfo.abiList} · ${"%.1f".format(appInfo.apkSizeMb)}MB",
                        accentColor = if (existingCloneCount > 0) EmeraldMatrix else TextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCloneClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("clone_app_btn_${appInfo.packageName}")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clone")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeployCloneBottomSheet(
    app: InstalledAppInfo,
    proxyNodes: List<ProxyNodeEntity>,
    onDismiss: () -> Unit,
    onDeploy: (DeployCloneConfig) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var baseLabel by remember { mutableStateOf(app.label) }
    var batchCount by remember { mutableFloatStateOf(1f) }
    var preferredSlot by remember { mutableStateOf<Int?>(null) }
    var proxyEnabled by remember { mutableStateOf(proxyNodes.isNotEmpty()) }
    var rotationMode by remember { mutableStateOf("ON_LAUNCH") }
    var gmsIsolation by remember { mutableStateOf(true) }
    var clipboardIsolation by remember { mutableStateOf(true) }
    var virtualModel by remember { mutableStateOf("Pixel 9 Pro") }
    var pinCode by remember { mutableStateOf("") }
    var threadBoost by remember { mutableIntStateOf(-4) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconBadge(icon = app.icon, size = 48.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Create a clone",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${app.packageName} (${app.abiList})",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            color = TextSecondary
                        )
                    )
                }
            }

            OutlinedTextField(
                value = baseLabel,
                onValueChange = { baseLabel = it },
                label = { Text("Clone name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deploy_label_input")
            )

            // Batch Multi-Clone Count
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Number of copies", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${batchCount.roundToInt()} ${if (batchCount.roundToInt() == 1) "copy" else "copies"}",
                        style = MaterialTheme.typography.labelMedium.copy(color = CyanPulse)
                    )
                }
                Slider(
                    value = batchCount,
                    onValueChange = { batchCount = it },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.testTag("deploy_batch_slider")
                )
            }

            // Process Slot Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Process preference", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = preferredSlot == null,
                        onClick = { preferredSlot = null },
                        label = { Text("Auto") }
                    )
                    (0..3).forEach { idx ->
                        FilterChip(
                            selected = preferredSlot == idx,
                            onClick = { preferredSlot = idx },
                            label = { Text("P$idx") }
                        )
                    }
                }
            }

            // Proxy Rotation Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use a proxy", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (proxyNodes.isEmpty()) "Add a proxy in Network settings" else "${proxyNodes.size} saved proxy(ies)",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
                Switch(
                    checked = proxyEnabled,
                    onCheckedChange = { proxyEnabled = it }
                )
            }

            if (proxyEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ON_LAUNCH" to "On Launch", "INTERVAL_5M" to "5m Auto", "INTERVAL_15M" to "15m Auto").forEach { (key, title) ->
                        FilterChip(
                            selected = rotationMode == key,
                            onClick = { rotationMode = key },
                            label = { Text(title) }
                        )
                    }
                }
            }

            // Isolation Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Separate account data", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Keep supported account data separate where available",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
                Switch(checked = gmsIsolation, onCheckedChange = { gmsIsolation = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Separate clipboard access", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Reduce clipboard sharing between clones",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
                Switch(checked = clipboardIsolation, onCheckedChange = { clipboardIsolation = it })
            }

            // Virtual Device Profile
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Device profile", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Pixel 9 Pro", "Galaxy S25 Ultra", "Stock").forEach { model ->
                        FilterChip(
                            selected = virtualModel == model,
                            onClick = { virtualModel = model },
                            label = { Text(model) }
                        )
                    }
                }
            }

            // Thread Scheduling Priority
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Performance preference", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Standard (0)", -4 to "Fast (-4)", -8 to "Urgent (-8)").forEach { (prio, title) ->
                        FilterChip(
                            selected = threadBoost == prio,
                            onClick = { threadBoost = prio },
                            label = { Text(title) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = pinCode,
                onValueChange = { if (it.length <= 8) pinCode = it },
                label = { Text("Optional clone PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onDeploy(
                        DeployCloneConfig(
                            app = app,
                            baseLabel = baseLabel,
                            count = batchCount.roundToInt(),
                            preferredSlotIndex = preferredSlot,
                            proxyEnabled = proxyEnabled,
                            proxyPoolId = proxyNodes.firstOrNull { it.isActive }?.id,
                            proxyRotationMode = rotationMode,
                            gmsIsolation = gmsIsolation,
                            clipboardIsolation = clipboardIsolation,
                            virtualDeviceModel = virtualModel,
                            pinCode = pinCode,
                            threadPriorityBoost = threadBoost
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_deploy_clone_btn")
            ) {
                Text("Create ${batchCount.roundToInt()} clone(s)")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
