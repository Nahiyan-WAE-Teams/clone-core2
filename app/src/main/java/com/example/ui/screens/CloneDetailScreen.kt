package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.vfs.SandboxFileNode
import com.example.engine.vfs.SandboxStorageStats
import com.example.ui.MainViewModel
import com.example.ui.components.AppIconBadge
import com.example.ui.components.StatusPill
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldMatrix
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneDetailScreen(
    cloneId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val clone by viewModel.observeClone(cloneId).collectAsStateWithLifecycle()
    val logs by viewModel.observeLogsForClone(cloneId).collectAsStateWithLifecycle()
    val slots by viewModel.slotStates.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var vfsFiles by remember { mutableStateOf<List<SandboxFileNode>>(emptyList()) }
    var storageStats by remember { mutableStateOf<SandboxStorageStats?>(null) }
    var showWriteFileDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun reloadVfs() {
        viewModel.loadSandboxFiles(cloneId) { files, stats ->
            vfsFiles = files
            storageStats = stats
        }
    }

    LaunchedEffect(cloneId) {
        reloadVfs()
    }

    val currentClone = clone
    if (currentClone == null) {
        Scaffold(
            containerColor = ObsidianCanvas,
            topBar = {
                TopAppBar(
                    title = { Text("Container Inspector") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianCanvas)
                )
            }
        ) { pad ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Container not found or deleted.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Return to Vault") }
            }
        }
        return
    }

    var labelEdit by remember(currentClone.id) { mutableStateOf(currentClone.cloneLabel) }
    var androidIdEdit by remember(currentClone.id) { mutableStateOf(currentClone.virtualAndroidId) }
    var deviceModelEdit by remember(currentClone.id) { mutableStateOf(currentClone.virtualDeviceModel) }
    var gmsIsoEdit by remember(currentClone.id) { mutableStateOf(currentClone.gmsIsolationEnabled) }
    var clipIsoEdit by remember(currentClone.id) { mutableStateOf(currentClone.clipboardIsolationEnabled) }
    var prioEdit by remember(currentClone.id) { mutableIntStateOf(currentClone.threadPriorityBoost) }

    val slotTelemetry = slots[currentClone.slotIndex]

    Scaffold(
        containerColor = ObsidianCanvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentClone.cloneLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("detail_delete_clone_btn")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Delete clone", tint = CoralAlert)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianCanvas)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("clone_detail_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Session Control Header
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (currentClone.isRunning) EmeraldMatrix.copy(alpha = 0.6f) else ObsidianOutline
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIconBadge(
                                icon = viewModel.findAppIcon(currentClone.sourcePackageName),
                                cloneIndex = currentClone.cloneIndex,
                                size = 54.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(currentClone.appLabel, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = currentClone.virtualPackageName,
                                    style = MaterialTheme.typography.labelSmall.copy(color = CyanPulse),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusPill(
                                        text = if (currentClone.isRunning) {
                                            "PID ${currentClone.activePid} · :sandbox_p${currentClone.slotIndex}"
                                        } else {
                                            "SLOT :sandbox_p${currentClone.slotIndex} IDLE"
                                        },
                                        accentColor = if (currentClone.isRunning) EmeraldMatrix else TextSecondary
                                    )
                                    StatusPill(text = currentClone.abiArchitecture, accentColor = CyanPulse)
                                }
                            }
                        }

                        if (slotTelemetry?.isBound == true && slotTelemetry.cloneId == currentClone.id) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianSurfaceHigh
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Heap: ${"%.1f".format(slotTelemetry.heapUsedMb)} MB",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        text = "Native: ${"%.1f".format(slotTelemetry.nativeUsedMb)} MB",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        text = "Threads: ${slotTelemetry.threadCount}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (currentClone.isRunning) {
                                Button(
                                    onClick = { viewModel.stopCloneSession(currentClone) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CoralAlert,
                                        contentColor = ObsidianCanvas
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("detail_stop_btn")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Terminate")
                                }
                            } else {
                                Button(
                                    onClick = {
                                        viewModel.launchCloneSession(currentClone)
                                        reloadVfs()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("detail_launch_btn")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Start Sandbox")
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.launchTargetAppIntent(currentClone.sourcePackageName) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Target")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.runBinderSelfTest(currentClone) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("detail_verify_hooks_btn")
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verify Hooks")
                            }
                            OutlinedButton(
                                onClick = { viewModel.rotateProxyForClone(currentClone.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rotate Proxy")
                            }
                        }
                    }
                }
            }

            // Virtual Identity & Isolation Config
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Virtual Identity & Kernel Isolation", style = MaterialTheme.typography.titleMedium)

                        OutlinedTextField(
                            value = labelEdit,
                            onValueChange = { labelEdit = it },
                            label = { Text("Clone Label") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = androidIdEdit,
                                onValueChange = { androidIdEdit = it },
                                label = { Text("Virtual ANDROID_ID") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { androidIdEdit = viewModel.generateRandomAndroidId() },
                                modifier = Modifier.testTag("randomize_android_id_btn")
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = "Randomize Android ID", tint = CyanPulse)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("GMS Sandbox Namespace", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = gmsIsoEdit, onCheckedChange = { gmsIsoEdit = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Clipboard Isolation", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = clipIsoEdit, onCheckedChange = { clipIsoEdit = it })
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Normal", -4 to "Fast (-4)", -8 to "Urgent (-8)").forEach { (prio, title) ->
                                FilterChip(
                                    selected = prioEdit == prio,
                                    onClick = { prioEdit = prio },
                                    label = { Text(title) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.updateCloneIdentity(
                                    clone = currentClone,
                                    newLabel = labelEdit,
                                    newAndroidId = androidIdEdit,
                                    newDeviceModel = deviceModelEdit,
                                    gmsIsolation = gmsIsoEdit,
                                    clipboardIsolation = clipIsoEdit,
                                    threadPriority = prioEdit
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_identity_btn")
                        ) {
                            Text("Apply Virtual Identity")
                        }
                    }
                }
            }

            // Virtual File System (VFS) Explorer
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Isolated Virtual File System", style = MaterialTheme.typography.titleMedium)
                            StatusPill(
                                text = "${(storageStats?.totalBytes ?: 0L) / 1024} KB",
                                accentColor = CyanPulse
                            )
                        }
                        Text(
                            text = currentClone.sandboxRootPath,
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showWriteFileDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("vfs_write_file_btn")
                            ) {
                                Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Write File")
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.wipeCloneCache(currentClone.id) { reloadVfs() }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wipe Cache")
                            }
                        }

                        vfsFiles.take(16).forEach { node ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (node.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = if (node.isDirectory) CyanPulse else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = node.relativePath,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFamily
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!node.isDirectory) {
                                    Text(
                                        text = "${node.sizeBytes} B",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Deep Link Quick Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Session Deep-Link Route", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = currentClone.deepLinkUri,
                                style = MaterialTheme.typography.labelMedium.copy(color = EmeraldMatrix)
                            )
                        }
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(currentClone.deepLinkUri))
                            viewModel.showMessage("Copied ${currentClone.deepLinkUri}")
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy deep link", tint = CyanPulse)
                        }
                    }
                }
            }

            // Per-Clone Audit Logs
            if (logs.isNotEmpty()) {
                item {
                    Text(
                        text = "CONTAINER AUDIT LOGS",
                        style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                    )
                }
                items(logs.take(15), key = { it.id }) { log ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianSurfaceElevated
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusPill(text = log.category, accentColor = CyanPulse)
                            Text(
                                text = log.message,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showWriteFileDialog) {
        var fileName by remember { mutableStateOf("session_token.cfg") }
        var fileContent by remember { mutableStateOf("account_tier=isolated\nproxy_lock=true") }
        AlertDialog(
            onDismissRequest = { showWriteFileDialog = false },
            title = { Text("Write Isolated VFS File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        label = { Text("Content") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showWriteFileDialog = false
                    viewModel.writeSandboxTestFile(currentClone.id, fileName, fileContent) {
                        reloadVfs()
                    }
                }) {
                    Text("Write to Sandbox")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWriteFileDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Destroy Sandbox Container?") },
            text = {
                Text("This permanently wipes ${currentClone.cloneLabel}'s isolated VFS directory and session state.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteClone(currentClone)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralAlert)
                ) {
                    Text("Destroy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
