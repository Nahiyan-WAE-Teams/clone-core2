package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.CloneInstanceEntity
import com.example.engine.network.RelayStatus
import com.example.engine.process.SlotProcessTelemetry
import com.example.ui.MainViewModel
import com.example.ui.components.AppIconBadge
import com.example.ui.components.PinVerifyDialog
import com.example.ui.components.SlotStatusStrip
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldMatrix
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun VaultScreen(
    viewModel: MainViewModel,
    clones: List<CloneInstanceEntity>,
    slots: Map<Int, SlotProcessTelemetry>,
    relayStatus: RelayStatus,
    onNavigateToApps: () -> Unit,
    onNavigateToKernel: () -> Unit,
    onOpenCloneDetail: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var pendingPinClone by remember { mutableStateOf<CloneInstanceEntity?>(null) }
    val runningCount = clones.count { it.isRunning }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("vault_screen_list"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Cloned apps", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Keep separate app spaces on this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Button(
                    onClick = onNavigateToApps,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("hero_deploy_clone_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add app")
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ObsidianSurface,
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    Column {
                        Text("${clones.size}", style = MaterialTheme.typography.titleLarge)
                        Text("Cloned", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Column {
                        Text("$runningCount", style = MaterialTheme.typography.titleLarge)
                        Text("Running", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Column {
                        Text("${(clones.size - runningCount).coerceAtLeast(0)}", style = MaterialTheme.typography.titleLarge)
                        Text("Stopped", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }

        if (clones.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ObsidianSurface,
                    shape = MaterialTheme.shapes.large,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("No cloned apps yet", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Choose an installed app to create its first separate space.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Button(
                            onClick = onNavigateToApps,
                            modifier = Modifier.testTag("empty_vault_browse_apps_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Choose an app")
                        }
                    }
                }
            }
        } else {
            item {
                Text("YOUR APPS", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            items(clones, key = { it.id }) { clone ->
                CloneInstanceCard(
                    clone = clone,
                    icon = remember(clone.sourcePackageName) { viewModel.findAppIcon(clone.sourcePackageName) },
                    onLaunch = {
                        if (clone.pinHash != null) pendingPinClone = clone
                        else viewModel.launchCloneSession(clone)
                    },
                    onStop = { viewModel.stopCloneSession(clone) },
                    onRotateProxy = { viewModel.rotateProxyForClone(clone.id) },
                    onCopyDeepLink = {
                        clipboard.setText(AnnotatedString(clone.deepLinkUri))
                        viewModel.showMessage("Copied link")
                    },
                    onPinShortcut = { viewModel.pinCloneShortcut(clone) },
                    onOpenDetail = { onOpenCloneDetail(clone.id) }
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    pendingPinClone?.let { lockedClone ->
        PinVerifyDialog(
            cloneLabel = lockedClone.cloneLabel,
            onDismiss = { pendingPinClone = null },
            onVerify = { pin ->
                val ok = viewModel.verifyPin(lockedClone, pin)
                if (ok) {
                    pendingPinClone = null
                    viewModel.launchCloneSession(lockedClone)
                }
                ok
            }
        )
    }
}

@Composable
private fun CloneInstanceCard(
    clone: CloneInstanceEntity,
    icon: android.graphics.drawable.Drawable?,
    onLaunch: () -> Unit,
    onStop: () -> Unit,
    onRotateProxy: () -> Unit,
    onCopyDeepLink: () -> Unit,
    onPinShortcut: () -> Unit,
    onOpenDetail: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenDetail)
            .testTag("clone_card_${clone.id}"),
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (clone.isRunning) EmeraldMatrix.copy(alpha = 0.6f) else ObsidianOutline
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconBadge(icon = icon, cloneIndex = clone.cloneIndex, size = 50.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = clone.cloneLabel,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (clone.pinHash != null) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "PIN protected",
                                tint = AmberWarn,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = clone.sourcePackageName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            color = TextSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = if (clone.isRunning) {
                                "P${clone.slotIndex} · PID ${clone.activePid}"
                            } else {
                                "SLOT P${clone.slotIndex} · COLD"
                            },
                            accentColor = if (clone.isRunning) EmeraldMatrix else TextMuted
                        )
                        if (clone.gmsIsolationEnabled) {
                            StatusPill(text = "GMS SAFE", accentColor = CyanPulse)
                        }
                    }
                }
                IconButton(
                    onClick = onOpenDetail,
                    modifier = Modifier.testTag("clone_inspect_btn_${clone.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Inspect container",
                        tint = TextSecondary
                    )
                }
            }

            // Network & Deep-link Metadata Strip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianSurfaceHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = if (clone.proxyEnabled) CyanPulse else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${clone.currentProxyLabel} (${clone.proxyRotationMode})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (clone.proxyEnabled) MaterialTheme.colorScheme.onSurface else TextSecondary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = EmeraldMatrix,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "ID:${clone.virtualAndroidId.take(8)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                        }
                    }
                }
            }

            // Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (clone.isRunning) {
                    FilledTonalButton(
                        onClick = onStop,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CoralAlert.copy(alpha = 0.16f),
                            contentColor = CoralAlert
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clone_stop_btn_${clone.id}")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Terminate")
                    }
                } else {
                    Button(
                        onClick = onLaunch,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clone_launch_btn_${clone.id}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Launch")
                    }
                }

                OutlinedButton(
                    onClick = onRotateProxy,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.testTag("clone_rotate_btn_${clone.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rotate proxy",
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Proxy")
                }

                IconButton(
                    onClick = onCopyDeepLink,
                    modifier = Modifier.testTag("clone_copy_link_btn_${clone.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy deep link",
                        tint = CyanPulse
                    )
                }

                IconButton(
                    onClick = onPinShortcut,
                    modifier = Modifier.testTag("clone_pin_btn_${clone.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pin shortcut",
                        tint = if (clone.isPinnedShortcut) EmeraldMatrix else TextSecondary
                    )
                }
            }
        }
    }
}
