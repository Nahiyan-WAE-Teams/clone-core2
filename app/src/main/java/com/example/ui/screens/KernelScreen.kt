package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.db.KernelAuditLogEntity
import com.example.engine.binder.BinderHookMetric
import com.example.engine.kernel.KernelSnapshot
import com.example.engine.process.SlotProcessTelemetry
import com.example.ui.MainViewModel
import com.example.ui.components.SlotStatusStrip
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetrySparklineChart
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldMatrix
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KernelScreen(
    viewModel: MainViewModel,
    snapshot: KernelSnapshot?,
    cpuHistory: List<Float>,
    memHistory: List<Float>,
    slots: Map<Int, SlotProcessTelemetry>,
    binderHooks: List<BinderHookMetric>,
    auditLogs: List<KernelAuditLogEntity>,
    isMonitorActive: Boolean
) {
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.togglePersistentMonitorService(true)
        } else {
            viewModel.showMessage("Notification permission required for persistent background monitor.")
        }
    }

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("kernel_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Kernel & Process Telemetry",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Zero-root /proc & ActivityManager live telemetry",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
                StatusPill(
                    text = snapshot?.thermalStatusLabel ?: "Nominal",
                    accentColor = EmeraldMatrix
                )
            }
        }

        // Real-time Sparklines Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetrySparklineChart(
                    samples = cpuHistory,
                    lineColor = CyanPulse,
                    label = "CPU LOAD",
                    currentValueText = "${"%.1f".format(snapshot?.cpuUsagePercent ?: 0f)}%",
                    modifier = Modifier.weight(1f)
                )
                TelemetrySparklineChart(
                    samples = memHistory,
                    lineColor = EmeraldMatrix,
                    label = "RAM USED",
                    currentValueText = "${"%.1f".format(snapshot?.memUsedPercent ?: 0f)}%",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Kernel /proc/meminfo & /proc/loadavg Card
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = CyanPulse)
                            Text("Kernel & Memory Subsystem", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "Linux ${snapshot?.kernelVersion ?: "..."}",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                    }

                    if (snapshot != null) {
                        val usedMb = (snapshot.memTotalMb - snapshot.memAvailableMb).coerceAtLeast(0L)
                        val progress = if (snapshot.memTotalMb > 0) {
                            usedMb.toFloat() / snapshot.memTotalMb.toFloat()
                        } else 0f

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Physical RAM: $usedMb / ${snapshot.memTotalMb} MB",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "Avail ${snapshot.memAvailableMb} MB",
                                    style = MaterialTheme.typography.labelMedium.copy(color = EmeraldMatrix)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                color = CyanPulse,
                                trackColor = ObsidianSurfaceHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            KernelStatCell("LOADAVG", "${snapshot.loadAvg1m} / ${snapshot.loadAvg5m}")
                            KernelStatCell("CPU0 FREQ", snapshot.cpuFreqMhz)
                            KernelStatCell("SECCOMP", snapshot.seccompStatus)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            KernelStatCell("MAIN PID", "${snapshot.selfPid} (${snapshot.selfThreads} thr)")
                            KernelStatCell("VM RSS", "${"%.1f".format(snapshot.selfVmRssMb)} MB")
                            KernelStatCell("UID NET", "${snapshot.uidRxKb}K↓ ${snapshot.uidTxKb}K↑")
                        }
                    }
                }
            }
        }

        // Per-Core CPU Load Strip
        if (snapshot != null && snapshot.perCoreUsage.isNotEmpty()) {
            item {
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
                        Text(
                            text = "CPU CORES (${snapshot.cpuCoresCount} LOGICAL)",
                            style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                        )
                        snapshot.perCoreUsage.chunked(2).forEachIndexed { rowIdx, pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pair.forEachIndexed { colIdx, pct ->
                                    val coreId = rowIdx * 2 + colIdx
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "CPU$coreId",
                                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                            )
                                            Text(
                                                text = "${pct.toInt()}%",
                                                style = MaterialTheme.typography.labelSmall.copy(color = CyanPulse)
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { (pct / 100f).coerceIn(0f, 1f) },
                                            color = if (pct > 80f) AmberWarn else CyanPulse,
                                            trackColor = ObsidianSurfaceHigh,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                        )
                                    }
                                }
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Sandbox Multi-Process Slots & Resource Governor
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ISOLATED SANDBOX PROCESS SLOTS",
                        style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                    )
                    SlotStatusStrip(slots = slots, onSlotClick = {})

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Persistent Engine Monitor", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Keep proxy relay & slot watchdog active",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                        Switch(
                            checked = isMonitorActive,
                            onCheckedChange = { enable ->
                                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.togglePersistentMonitorService(true)
                                    } else {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    viewModel.togglePersistentMonitorService(enable)
                                }
                            },
                            modifier = Modifier.testTag("monitor_service_switch")
                        )
                    }

                    Button(
                        onClick = { viewModel.triggerKernelTrim() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("kernel_memory_trim_btn")
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Trigger Kernel Heap Trim & Slot GC")
                    }
                }
            }
        }

        // Active Running OS Processes (ActivityManager)
        if (snapshot != null && snapshot.activeSlotProcesses.isNotEmpty()) {
            item {
                Text(
                    text = "OS PROCESS MEMORY TABLE (${snapshot.activeSlotProcesses.size})",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                )
            }
            items(snapshot.activeSlotProcesses, key = { it.pid }) { proc ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = proc.processName,
                                style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                            )
                            Text(
                                text = "PID ${proc.pid} · ${proc.importanceLabel}",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                        }
                        StatusPill(
                            text = "${"%.1f".format(proc.pssMb)} MB PSS",
                            accentColor = CyanPulse
                        )
                    }
                }
            }
        }

        // Local compatibility helper diagnostics (not framework Binder hooks)
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
                        Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldMatrix)
                        Text("Compatibility Helpers (No System Hooks)", style = MaterialTheme.typography.titleMedium)
                    }
                    binderHooks.forEach { hook ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianSurfaceHigh
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hook.hookName,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = hook.targetInterface,
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                    )
                                }
                                StatusPill(
                                    text = "${hook.status} (${hook.intercepts})",
                                    accentColor = EmeraldMatrix
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Kernel & Sandbox Audit Logs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SANDBOX & KERNEL AUDIT STREAM",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                )
                IconButton(onClick = { viewModel.clearLogs() }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear audit logs",
                        tint = TextSecondary
                    )
                }
            }
        }

        items(auditLogs.take(30), key = { it.id }) { log ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ObsidianSurfaceElevated
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    StatusPill(
                        text = log.category,
                        accentColor = when (log.category) {
                            "PROXY" -> AmberWarn
                            "PROCESS" -> EmeraldMatrix
                            "BINDER" -> CyanPulse
                            else -> TextSecondary
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = log.message,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFamily
                            )
                        )
                        Text(
                            text = timeFormat.format(Date(log.timestamp)),
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun KernelStatCell(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}
