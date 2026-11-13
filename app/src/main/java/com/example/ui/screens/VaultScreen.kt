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

    val activeSlotCount = slots.values.count { it.isBound }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("vault_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Architecture Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(186.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, ObsidianOutline, RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_sandbox_hero),
                    contentDescription = "Sandbox engine architecture hero banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    ObsidianCanvas.copy(alpha = 0.25f),
                                    ObsidianCanvas.copy(alpha = 0.88f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = if (relayStatus.isRunning) {
                                "RELAY 127.0.0.1:${relayStatus.localPort}"
                            } else {
                                "ENGINE READY"
                            },
                            accentColor = if (relayStatus.isRunning) EmeraldMatrix else CyanPulse
                        )
                        StatusPill(
                            text = "NO-ROOT VFS",
                            accentColor = CyanPulse
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "CloneCore Engine",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${clones.size} Containers · $activeSlotCount/4 Parallel Slots",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                            )
                            Button(
                                onClick = onNavigateToApps,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("hero_deploy_clone_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Deploy clone",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_new_clone))
                            }
                        }
                    }
                }
            }
        }

        // Parallel Multi-Process Slots Strip
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PARALLEL SANDBOX SLOTS",
                        style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                    )
                    Text(
                        text = "Inspect Kernel ->",
                        style = MaterialTheme.typography.labelMedium.copy(color = CyanPulse),
                        modifier = Modifier
                            .clickable(onClick = onNavigateToKernel)
                            .padding(vertical = 4.dp)
                    )
                }
                SlotStatusStrip(
                    slots = slots,
                    onSlotClick = { idx ->
                        val boundCloneId = slots[idx]?.cloneId
                        if (boundCloneId != null) {
                            onOpenCloneDetail(boundCloneId)
                        } else {
                            onNavigateToKernel()
                        }
                    }
                )
            }
        }

        // Cloned Instances List or Empty State
        if (clones.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = ObsidianSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_sandbox),
                            contentDescription = "Empty sandbox container",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Text(
                            text = stringResource(R.string.empty_vault_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.empty_vault_desc),
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                        Button(
                            onClick = onNavigateToApps,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("empty_vault_browse_apps_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Installed Apps & Clone")
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "ISOLATED CONTAINERS (${clones.size})",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                )
            }
            items(clones, key = { it.id }) { clone ->
                CloneInstanceCard(
                    clone = clone,
                    icon = remember(clone.sourcePackageName) {
                        viewModel.findAppIcon(clone.sourcePackageName)
                    },
                    onLaunch = {
                        if (clone.pinHash != null) {
                            pendingPinClone = clone
                        } else {
                            viewModel.launchCloneSession(clone)
                        }
                    },
                    onStop = { viewModel.stopCloneSession(clone) },
                    onRotateProxy = { viewModel.rotateProxyForClone(clone.id) },
                    onCopyDeepLink = {
                        clipboard.setText(AnnotatedString(clone.deepLinkUri))
                        viewModel.showMessage("Copied ${clone.deepLinkUri}")
                    },
                    onPinShortcut = { viewModel.pinCloneShortcut(clone) },
                    onOpenDetail = { onOpenCloneDetail(clone.id) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
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
