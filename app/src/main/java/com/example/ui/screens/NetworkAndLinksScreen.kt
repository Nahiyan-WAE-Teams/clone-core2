package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.CloneInstanceEntity
import com.example.data.db.ProxyNodeEntity
import com.example.engine.deeplink.DeepLinkAndBridgeManager
import com.example.engine.network.RelayStatus
import com.example.ui.MainViewModel
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldMatrix
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.TextSecondary

@Composable
fun NetworkAndLinksScreen(
    viewModel: MainViewModel,
    proxyNodes: List<ProxyNodeEntity>,
    clones: List<CloneInstanceEntity>,
    relayStatus: RelayStatus,
    onOpenCloneDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Proxy Pool, 1 = Deep Links & Bridge
    var showAddProxyDialog by remember { mutableStateOf(false) }
    var deepLinkTesterUri by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("network_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Proxy Engine & Deep Links",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Automated per-session proxy rotation & cross-platform routing",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
        Spacer(modifier = Modifier.height(10.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.testTag("network_tab_proxy")
            ) {
                Text("Proxy Rotation")
            }
            SegmentedButton(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.testTag("network_tab_deeplinks")
            ) {
                Text("Deep Links & Bridge")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Local Loopback Relay Status Card
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Router, contentDescription = null, tint = CyanPulse)
                                    Text("Local Loopback Relay", style = MaterialTheme.typography.titleMedium)
                                }
                                StatusPill(
                                    text = if (relayStatus.isRunning) "LISTEN :${relayStatus.localPort}" else "STOPPED",
                                    accentColor = if (relayStatus.isRunning) EmeraldMatrix else CoralAlert
                                )
                            }
                            Text(
                                text = "Endpoint: ${relayStatus.localHost}:${relayStatus.localPort}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                            )
                            Text(
                                text = "Active Upstream: ${relayStatus.activeUpstreamLabel}",
                                style = MaterialTheme.typography.bodySmall.copy(color = CyanPulse)
                            )
                            Text(
                                text = "Relayed Sockets: ${relayStatus.totalRelayedConnections}",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showAddProxyDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("add_proxy_node_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Node")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.probeAllProxyNodes() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("probe_all_proxies_btn")
                                ) {
                                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Test All")
                                }
                            }
                        }
                    }
                }

                if (proxyNodes.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ObsidianSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("No Proxy Nodes Configured", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Add HTTP, HTTPS, or SOCKS5 endpoints to enable automated per-clone proxy rotation and real TCP RTT probing.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }
                    }
                } else {
                    items(proxyNodes, key = { it.id }) { node ->
                        ProxyNodeCard(
                            node = node,
                            onToggleActive = { viewModel.toggleProxyNodeActive(node) },
                            onProbe = { viewModel.probeProxyNode(node) },
                            onDelete = { viewModel.deleteProxyNode(node.id) }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Interactive Deep-Link URI Tester
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = CyanPulse)
                                Text("Deep-Link & URI Router", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = "Dispatch any clonecore://session/<id> URI directly to spin up its sandbox container.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                            OutlinedTextField(
                                value = deepLinkTesterUri,
                                onValueChange = { deepLinkTesterUri = it },
                                placeholder = { Text("clonecore://session/c_xxxxxxxx") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("deeplink_tester_input")
                            )
                            Button(
                                onClick = {
                                    val targetId = deepLinkTesterUri
                                        .removePrefix("clonecore://session/")
                                        .removePrefix("https://clonecore.app/s/")
                                        .trim()
                                    if (targetId.isNotEmpty() && clones.any { it.id == targetId }) {
                                        onOpenCloneDetail(targetId)
                                    } else {
                                        viewModel.showMessage("No matching clone session found for URI.")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("deeplink_dispatch_btn")
                            ) {
                                Text("Dispatch Deep-Link Session")
                            }
                        }
                    }
                }

                items(clones, key = { it.id }) { clone ->
                    val bridgeJson = remember(clone.id, relayStatus.localPort) {
                        DeepLinkAndBridgeManager.buildCrossPlatformBridgePayload(
                            clone = clone,
                            relayPort = relayStatus.localPort
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
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
                                Text(clone.cloneLabel, style = MaterialTheme.typography.titleMedium)
                                StatusPill(text = ":sandbox_p${clone.slotIndex}", accentColor = CyanPulse)
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianSurfaceHigh
                            ) {
                                Text(
                                    text = clone.deepLinkUri,
                                    style = MaterialTheme.typography.labelMedium.copy(color = EmeraldMatrix),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(clone.deepLinkUri))
                                        viewModel.showMessage("Copied ${clone.deepLinkUri}")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("URI")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.pinCloneShortcut(clone) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pin")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "CloneCore Bridge: ${clone.cloneLabel}")
                                            putExtra(Intent.EXTRA_TEXT, bridgeJson)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Export Cross-Platform Bridge")
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bridge")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddProxyDialog) {
        AddProxyNodeDialog(
            onDismiss = { showAddProxyDialog = false },
            onAdd = { name, proto, host, port, region, user, pass ->
                showAddProxyDialog = false
                viewModel.addOrUpdateProxyNode(name, proto, host, port, region, user, pass)
            }
        )
    }
}

@Composable
private fun ProxyNodeCard(
    node: ProxyNodeEntity,
    onToggleActive: () -> Unit,
    onProbe: () -> Unit,
    onDelete: () -> Unit
) {
    val latencyText = when {
        node.lastCheckedAt == 0L -> "UNTESTED"
        node.latencyMs > 0 -> "${node.latencyMs}ms"
        else -> "UNREACHABLE"
    }
    val latencyColor = when {
        node.lastCheckedAt == 0L -> AmberWarn
        node.latencyMs in 1..250 -> EmeraldMatrix
        node.latencyMs > 250 -> AmberWarn
        else -> CoralAlert
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${node.protocol}://${node.host}:${node.port}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            color = TextSecondary
                        )
                    )
                }
                Switch(checked = node.isActive, onCheckedChange = { onToggleActive() })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(text = node.regionTag, accentColor = CyanPulse)
                    StatusPill(text = latencyText, accentColor = latencyColor)
                }
                Row {
                    IconButton(onClick = onProbe) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = "Test proxy latency", tint = CyanPulse)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete proxy node", tint = CoralAlert)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddProxyNodeDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, Int, String, String?, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf("SOCKS5") }
    var host by remember { mutableStateOf("") }
    var portStr by remember { mutableStateOf("1080") }
    var region by remember { mutableStateOf("US-EAST") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Proxy Endpoint") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SOCKS5", "HTTP", "HTTPS").forEach { proto ->
                        FilterChip(
                            selected = protocol == proto,
                            onClick = { protocol = proto },
                            label = { Text(proto) }
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Node Alias") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host / IP Address") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_host_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = portStr,
                        onValueChange = { portStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("proxy_port_input")
                    )
                    OutlinedTextField(
                        value = region,
                        onValueChange = { region = it },
                        label = { Text("Region Tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = host.isNotBlank() && (portStr.toIntOrNull() ?: 0) in 1..65535,
                onClick = {
                    onAdd(
                        name,
                        protocol,
                        host,
                        portStr.toIntOrNull() ?: 1080,
                        region,
                        username,
                        password
                    )
                },
                modifier = Modifier.testTag("save_proxy_node_btn")
            ) {
                Text("Save & Probe")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
