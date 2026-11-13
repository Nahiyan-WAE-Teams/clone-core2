package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.CloneCoreApp
import com.example.data.db.CloneInstanceEntity
import com.example.data.db.KernelAuditLogEntity
import com.example.data.db.ProxyNodeEntity
import com.example.engine.apps.InstalledAppInfo
import com.example.engine.apps.InstalledAppsScanner
import com.example.engine.binder.BinderHookMetric
import com.example.engine.binder.BinderProxyEngine
import com.example.engine.deeplink.DeepLinkAndBridgeManager
import com.example.engine.kernel.KernelSnapshot
import com.example.engine.kernel.KernelTelemetryReader
import com.example.engine.network.RelayStatus
import com.example.engine.process.SandboxMonitorService
import com.example.engine.process.SlotProcessTelemetry
import com.example.engine.vfs.SandboxFileNode
import com.example.engine.vfs.SandboxFileSystem
import com.example.engine.vfs.SandboxStorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

data class DeployCloneConfig(
    val app: InstalledAppInfo,
    val baseLabel: String,
    val count: Int,
    val preferredSlotIndex: Int?,
    val proxyEnabled: Boolean,
    val proxyPoolId: String?,
    val proxyRotationMode: String,
    val gmsIsolation: Boolean,
    val clipboardIsolation: Boolean,
    val virtualDeviceModel: String,
    val pinCode: String,
    val threadPriorityBoost: Int
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as CloneCoreApp
    private val repository = app.repository
    private val processManager = app.processManager
    private val proxyEngine = app.proxyEngine

    val clones: StateFlow<List<CloneInstanceEntity>> = repository.allClones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val proxyNodes: StateFlow<List<ProxyNodeEntity>> = repository.allProxyNodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<KernelAuditLogEntity>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val slotStates: StateFlow<Map<Int, SlotProcessTelemetry>> = processManager.slotStates

    val relayStatus: StateFlow<RelayStatus> = proxyEngine.relayStatus

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isScanningApps = MutableStateFlow(false)
    val isScanningApps: StateFlow<Boolean> = _isScanningApps.asStateFlow()

    private val _kernelSnapshot = MutableStateFlow<KernelSnapshot?>(null)
    val kernelSnapshot: StateFlow<KernelSnapshot?> = _kernelSnapshot.asStateFlow()

    private val _cpuHistory = MutableStateFlow<List<Float>>(emptyList())
    val cpuHistory: StateFlow<List<Float>> = _cpuHistory.asStateFlow()

    private val _memHistory = MutableStateFlow<List<Float>>(emptyList())
    val memHistory: StateFlow<List<Float>> = _memHistory.asStateFlow()

    private val _binderHooks = MutableStateFlow(BinderProxyEngine.getActiveHookMetrics())
    val binderHooks: StateFlow<List<BinderHookMetric>> = _binderHooks.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _pendingDeepLinkCloneId = MutableStateFlow<String?>(null)
    val pendingDeepLinkCloneId: StateFlow<String?> = _pendingDeepLinkCloneId.asStateFlow()

    private val _isMonitorServiceActive = MutableStateFlow(false)
    val isMonitorServiceActive: StateFlow<Boolean> = _isMonitorServiceActive.asStateFlow()

    private val rotationJobs = mutableMapOf<String, Job>()

    init {
        refreshInstalledApps()
        startKernelTelemetryLoop()
        syncSlotStatesToClones()
    }

    private fun startKernelTelemetryLoop() {
        viewModelScope.launch {
            while (isActive) {
                val snap = withContext(Dispatchers.IO) {
                    KernelTelemetryReader.captureSnapshot(app)
                }
                _kernelSnapshot.value = snap
                _cpuHistory.update { (it + snap.cpuUsagePercent).takeLast(32) }
                _memHistory.update { (it + snap.memUsedPercent).takeLast(32) }
                _binderHooks.value = BinderProxyEngine.getActiveHookMetrics()
                processManager.pingAllActiveSlots()
                delay(1500L)
            }
        }
    }

    private fun syncSlotStatesToClones() {
        viewModelScope.launch {
            slotStates.collect { slots ->
                val all = repository.getAllClonesSnapshot()
                for (clone in all) {
                    val slot = slots[clone.slotIndex]
                    val shouldRun = slot?.isBound == true && slot.cloneId == clone.id
                    val newPid = if (shouldRun) slot?.pid ?: 0 else 0
                    if (clone.isRunning != shouldRun || clone.activePid != newPid) {
                        repository.setRunningState(clone.id, shouldRun, newPid)
                    }
                }
            }
        }
    }

    fun refreshInstalledApps() {
        if (_isScanningApps.value) return
        viewModelScope.launch {
            _isScanningApps.value = true
            val scanned = InstalledAppsScanner.scanLaunchableApps(app)
            _installedApps.value = scanned
            _isScanningApps.value = false
        }
    }

    fun inspectExternalApk(uri: Uri, onResult: (InstalledAppInfo?) -> Unit) {
        viewModelScope.launch {
            val info = InstalledAppsScanner.inspectExternalApkUri(app, uri)
            if (info == null) {
                showMessage("Could not parse APK archive metadata.")
            } else {
                repository.logEvent(
                    null,
                    "VFS",
                    "Inspected external APK ${info.packageName} (${info.abiList})"
                )
            }
            onResult(info)
        }
    }

    fun findAppIcon(packageName: String): Drawable? {
        return _installedApps.value.firstOrNull { it.packageName == packageName }?.icon
            ?: runCatching { app.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    fun deployClones(config: DeployCloneConfig, onCompleted: (List<CloneInstanceEntity>) -> Unit = {}) {
        viewModelScope.launch {
            val existing = repository.getClonesForPackage(config.app.packageName)
            var nextIndex = (existing.maxOfOrNull { it.cloneIndex } ?: 0) + 1
            val created = mutableListOf<CloneInstanceEntity>()
            val activeNodes = repository.getActiveProxyNodes()

            repeat(config.count.coerceIn(1, 5)) { idxOffset ->
                val cloneId = "c_${UUID.randomUUID().toString().take(8)}"
                val cloneNum = nextIndex++
                val slotIdx = if (config.preferredSlotIndex != null) {
                    (config.preferredSlotIndex + idxOffset) % 4
                } else {
                    (existing.size + idxOffset) % 4
                }
                val virtualPkg = "${app.packageName}.vapp.$cloneId"
                val androidId = generateRandomAndroidId()
                val label = if (config.count > 1) {
                    "${config.baseLabel.trim().ifEmpty { config.app.label }} #$cloneNum"
                } else {
                    config.baseLabel.trim().ifEmpty { "${config.app.label} #$cloneNum" }
                }

                val sandboxRoot = withContext(Dispatchers.IO) {
                    SandboxFileSystem.initializeSandboxManifest(
                        context = app,
                        cloneId = cloneId,
                        sourcePackage = config.app.packageName,
                        virtualPackage = virtualPkg,
                        virtualAndroidId = androidId,
                        abi = config.app.abiList
                    )
                }

                val selectedNode = when {
                    !config.proxyEnabled -> null
                    config.proxyPoolId != null -> activeNodes.firstOrNull { it.id == config.proxyPoolId }
                    else -> activeNodes.firstOrNull()
                }
                val proxyLabel = if (!config.proxyEnabled) {
                    "Direct"
                } else if (selectedNode != null) {
                    "${selectedNode.protocol}://${selectedNode.host}:${selectedNode.port}"
                } else {
                    "Auto Pool (No Nodes)"
                }

                val entity = CloneInstanceEntity(
                    id = cloneId,
                    sourcePackageName = config.app.packageName,
                    appLabel = config.app.label,
                    cloneLabel = label,
                    cloneIndex = cloneNum,
                    slotIndex = slotIdx,
                    virtualPackageName = virtualPkg,
                    virtualAuthorityPrefix = "${app.packageName}.vproc.$cloneId.provider",
                    virtualAndroidId = androidId,
                    virtualDeviceModel = config.virtualDeviceModel,
                    abiArchitecture = config.app.abiList,
                    sandboxRootPath = sandboxRoot.absolutePath,
                    proxyEnabled = config.proxyEnabled,
                    proxyPoolId = config.proxyPoolId,
                    proxyRotationMode = config.proxyRotationMode,
                    currentProxyLabel = proxyLabel,
                    deepLinkUri = DeepLinkAndBridgeManager.buildSessionDeepLink(cloneId),
                    pinHash = config.pinCode.takeIf { it.isNotBlank() }?.let { sha256(it) },
                    gmsIsolationEnabled = config.gmsIsolation,
                    clipboardIsolationEnabled = config.clipboardIsolation,
                    threadPriorityBoost = config.threadPriorityBoost,
                    memoryLimitMb = 512,
                    isPinnedShortcut = false,
                    isRunning = false,
                    activePid = 0,
                    lastLaunchedAt = 0L,
                    createdAt = System.currentTimeMillis(),
                    totalUptimeMs = 0L,
                    launchCount = 0
                )
                repository.upsertClone(entity)
                repository.logEvent(
                    cloneId,
                    "VFS",
                    "Provisioned isolated sandbox $cloneId for ${config.app.packageName} on :sandbox_p$slotIdx"
                )
                created.add(entity)
            }

            showMessage("Deployed ${created.size} isolated clone(s) for ${config.app.label}")
            onCompleted(created)
        }
    }

    fun launchCloneSession(clone: CloneInstanceEntity) {
        viewModelScope.launch {
            val assignedSlot = processManager.findSlotForClone(clone.id)
                ?: processManager.pickAvailableSlotIndex(clone.slotIndex.takeIf { it in 0..3 })
            if (assignedSlot !in 0..3) {
                showMessage("All four sandbox process slots are in use. Stop a running clone first.")
                return@launch
            }

            val sessionClone = clone.copy(slotIndex = assignedSlot)
            if (!processManager.startCloneInSlot(sessionClone)) {
                showMessage("Could not reserve :sandbox_p$assignedSlot. Try again.")
                return@launch
            }

            if (sessionClone.proxyEnabled) {
                val activeNodes = repository.getActiveProxyNodes()
                val chosen = if (sessionClone.proxyRotationMode == "ON_LAUNCH") {
                    proxyEngine.selectNextRotationNode(activeNodes, sessionClone.currentProxyLabel)
                } else {
                    activeNodes.firstOrNull { it.id == sessionClone.proxyPoolId } ?: activeNodes.firstOrNull()
                }
                if (chosen != null) {
                    proxyEngine.setUpstreamNode(chosen)
                    val label = "${chosen.protocol}://${chosen.host}:${chosen.port}"
                    repository.updateClone(
                        sessionClone.copy(
                            currentProxyLabel = label,
                            lastLaunchedAt = System.currentTimeMillis(),
                            launchCount = sessionClone.launchCount + 1
                        )
                    )
                    repository.logEvent(sessionClone.id, "PROXY", "Selected upstream proxy $label")
                } else {
                    proxyEngine.setUpstreamNode(null)
                    repository.updateClone(
                        sessionClone.copy(
                            lastLaunchedAt = System.currentTimeMillis(),
                            launchCount = sessionClone.launchCount + 1
                        )
                    )
                }
                scheduleAutoRotationIfNeeded(sessionClone)
            } else {
                proxyEngine.setUpstreamNode(null)
                repository.updateClone(
                    sessionClone.copy(
                        lastLaunchedAt = System.currentTimeMillis(),
                        launchCount = sessionClone.launchCount + 1
                    )
                )
            }

            if (sessionClone.slotIndex != clone.slotIndex) {
                repository.logEvent(
                    sessionClone.id,
                    "PROCESS",
                    "Assigned free process slot :sandbox_p$assignedSlot"
                )
            }
            showMessage("Starting ${sessionClone.cloneLabel} in :sandbox_p$assignedSlot")
        }
    }

    fun stopCloneSession(clone: CloneInstanceEntity) {
        viewModelScope.launch {
            rotationJobs.remove(clone.id)?.cancel()
            processManager.stopSlot(clone.slotIndex)
            val elapsed = if (clone.lastLaunchedAt > 0L) {
                (System.currentTimeMillis() - clone.lastLaunchedAt).coerceAtLeast(0L)
            } else 0L
            repository.updateClone(
                clone.copy(
                    isRunning = false,
                    activePid = 0,
                    totalUptimeMs = clone.totalUptimeMs + elapsed
                )
            )
            showMessage("Terminated :sandbox_p${clone.slotIndex} (${clone.cloneLabel})")
        }
    }

    fun launchTargetAppIntent(packageName: String): Boolean {
        val launchIntent = app.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(launchIntent)
            viewModelScope.launch {
                repository.logEvent(null, "BINDER", "Dispatched AMS launch intent for $packageName")
            }
            true
        } else {
            showMessage("Target package $packageName is not installed on host OS")
            false
        }
    }

    private fun scheduleAutoRotationIfNeeded(clone: CloneInstanceEntity) {
        rotationJobs.remove(clone.id)?.cancel()
        val intervalMs = when (clone.proxyRotationMode) {
            "INTERVAL_5M" -> 5 * 60_000L
            "INTERVAL_15M" -> 15 * 60_000L
            else -> return
        }
        rotationJobs[clone.id] = viewModelScope.launch {
            while (isActive) {
                delay(intervalMs)
                rotateProxyForClone(clone.id)
            }
        }
    }

    fun rotateProxyForClone(cloneId: String) {
        viewModelScope.launch {
            val clone = repository.getClone(cloneId) ?: return@launch
            val nodes = repository.getActiveProxyNodes()
            val next = proxyEngine.selectNextRotationNode(nodes, clone.currentProxyLabel)
            if (next == null) {
                showMessage("No active proxy nodes in pool. Add a proxy node first.")
                return@launch
            }
            proxyEngine.setUpstreamNode(next)
            val label = "${next.protocol}://${next.host}:${next.port}"
            repository.updateClone(clone.copy(proxyEnabled = true, currentProxyLabel = label))
            repository.logEvent(clone.id, "PROXY", "Rotated proxy -> $label (${next.regionTag})")
            showMessage("Rotated proxy to ${next.name} ($label)")
        }
    }

    fun addOrUpdateProxyNode(
        name: String,
        protocol: String,
        host: String,
        port: Int,
        regionTag: String,
        username: String?,
        password: String?
    ) {
        viewModelScope.launch {
            val node = ProxyNodeEntity(
                id = "px_${UUID.randomUUID().toString().take(8)}",
                name = name.trim().ifEmpty { "$regionTag $protocol" },
                protocol = protocol,
                host = host.trim(),
                port = port,
                username = username?.takeIf { it.isNotBlank() },
                password = password?.takeIf { it.isNotBlank() },
                regionTag = regionTag.trim().ifEmpty { "GLOBAL" },
                latencyMs = -1,
                lastCheckedAt = 0L,
                isActive = true,
                successCount = 0,
                failureCount = 0
            )
            repository.upsertProxyNode(node)
            repository.logEvent(null, "PROXY", "Added proxy node ${node.protocol}://${node.host}:${node.port}")
            probeProxyNode(node)
        }
    }

    fun toggleProxyNodeActive(node: ProxyNodeEntity) {
        viewModelScope.launch {
            repository.upsertProxyNode(node.copy(isActive = !node.isActive))
        }
    }

    fun deleteProxyNode(id: String) {
        viewModelScope.launch {
            repository.deleteProxyNode(id)
        }
    }

    fun probeProxyNode(node: ProxyNodeEntity) {
        viewModelScope.launch {
            val rtt = proxyEngine.probeProxyLatency(node)
            repository.recordProxyProbe(node.id, rtt, rtt > 0)
            val msg = if (rtt > 0) {
                "${node.name}: ${rtt}ms RTT"
            } else {
                "${node.name}: Unreachable / Timeout"
            }
            repository.logEvent(null, "PROXY", "Health probe $msg")
            showMessage(msg)
        }
    }

    fun probeAllProxyNodes() {
        viewModelScope.launch {
            val nodes = proxyNodes.value
            if (nodes.isEmpty()) {
                showMessage("No proxy nodes configured.")
                return@launch
            }
            var healthy = 0
            for (n in nodes) {
                val rtt = proxyEngine.probeProxyLatency(n)
                repository.recordProxyProbe(n.id, rtt, rtt > 0)
                if (rtt > 0) healthy++
            }
            showMessage("Probed ${nodes.size} node(s): $healthy reachable")
        }
    }

    fun runBinderSelfTest(clone: CloneInstanceEntity) {
        viewModelScope.launch {
            val lines = BinderProxyEngine.runHookVerificationPass(
                context = app,
                cloneId = clone.id,
                sourcePackage = clone.sourcePackageName,
                virtualPackage = clone.virtualPackageName
            )
            _binderHooks.value = BinderProxyEngine.getActiveHookMetrics()
            lines.forEach { repository.logEvent(clone.id, "BINDER", it) }
            showMessage("Local compatibility checks completed; Android Binder hooks are not installed")
        }
    }

    fun loadSandboxFiles(cloneId: String, onLoaded: (List<SandboxFileNode>, SandboxStorageStats) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val files = SandboxFileSystem.listSandboxFiles(app, cloneId)
                val stats = SandboxFileSystem.computeStorageStats(app, cloneId)
                files to stats
            }
            onLoaded(result.first, result.second)
        }
    }

    fun writeSandboxTestFile(cloneId: String, fileName: String, content: String, onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                SandboxFileSystem.writeCustomSandboxFile(app, cloneId, fileName, content)
            }
            repository.logEvent(cloneId, "VFS", "Wrote isolated VFS file: data/files/$fileName")
            showMessage("Saved $fileName inside isolated sandbox VFS")
            onDone()
        }
    }

    fun wipeCloneCache(cloneId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) {
                SandboxFileSystem.wipeSandboxCache(app, cloneId)
            }
            repository.logEvent(cloneId, "VFS", "Purged sandbox cache (${freed / 1024} KB freed)")
            showMessage("Purged ${freed / 1024} KB sandbox cache")
            onDone()
        }
    }

    fun updateCloneIdentity(
        clone: CloneInstanceEntity,
        newLabel: String,
        newAndroidId: String,
        newDeviceModel: String,
        gmsIsolation: Boolean,
        clipboardIsolation: Boolean,
        threadPriority: Int
    ) {
        viewModelScope.launch {
            val updated = clone.copy(
                cloneLabel = newLabel.trim().ifEmpty { clone.cloneLabel },
                virtualAndroidId = newAndroidId.trim().ifEmpty { clone.virtualAndroidId },
                virtualDeviceModel = newDeviceModel,
                gmsIsolationEnabled = gmsIsolation,
                clipboardIsolationEnabled = clipboardIsolation,
                threadPriorityBoost = threadPriority
            )
            repository.updateClone(updated)
            BinderProxyEngine.recordAndroidIdSpoof()
            repository.logEvent(clone.id, "BINDER", "Updated virtual identity & priority ($threadPriority)")
            showMessage("Updated sandbox container configuration")
        }
    }

    fun deleteClone(clone: CloneInstanceEntity) {
        viewModelScope.launch {
            rotationJobs.remove(clone.id)?.cancel()
            processManager.findSlotForClone(clone.id)?.let { activeSlot ->
                processManager.stopSlot(activeSlot)
            }
            withContext(Dispatchers.IO) {
                SandboxFileSystem.deleteSandbox(app, clone.id)
            }
            repository.deleteClone(clone.id)
            repository.logEvent(null, "VFS", "Destroyed sandbox container ${clone.cloneLabel} (${clone.id})")
            showMessage("Deleted ${clone.cloneLabel}")
        }
    }

    fun pinCloneShortcut(clone: CloneInstanceEntity) {
        viewModelScope.launch {
            val icon = findAppIcon(clone.sourcePackageName)
            val ok = DeepLinkAndBridgeManager.requestPinCloneShortcut(app, clone, icon)
            if (ok) {
                repository.updateClone(clone.copy(isPinnedShortcut = true))
                repository.logEvent(clone.id, "DEEPLINK", "Requested pinned launcher shortcut for ${clone.deepLinkUri}")
                showMessage("Shortcut pin requested for ${clone.cloneLabel}")
            } else {
                showMessage("Launcher does not support pinned shortcuts on this device")
            }
        }
    }

    fun triggerKernelTrim() {
        viewModelScope.launch {
            val freedKb = withContext(Dispatchers.IO) {
                KernelTelemetryReader.triggerKernelMemoryTrim()
            }
            (0..3).forEach { idx ->
                if (slotStates.value[idx]?.isBound == true) {
                    processManager.trimSlotMemory(idx)
                }
            }
            repository.logEvent(null, "KERNEL", "Global memory trim & GC completed (${freedKb} KB reclaimed)")
            showMessage("Kernel memory trim complete (${freedKb} KB reclaimed)")
        }
    }

    fun togglePersistentMonitorService(enable: Boolean) {
        val intent = Intent(app, SandboxMonitorService::class.java).apply {
            action = if (enable) SandboxMonitorService.ACTION_START else SandboxMonitorService.ACTION_STOP
            putExtra(SandboxMonitorService.EXTRA_ACTIVE_COUNT, slotStates.value.values.count { it.isBound })
            putExtra(SandboxMonitorService.EXTRA_RELAY_PORT, relayStatus.value.localPort)
        }
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } else {
            app.startService(intent)
        }
        _isMonitorServiceActive.value = enable
    }

    fun observeClone(cloneId: String): StateFlow<CloneInstanceEntity?> {
        return repository.observeClone(cloneId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    fun observeLogsForClone(cloneId: String): StateFlow<List<KernelAuditLogEntity>> {
        return repository.observeLogsForClone(cloneId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun handleIncomingDeepLink(intent: Intent?) {
        val cloneId = DeepLinkAndBridgeManager.parseCloneIdFromIntent(intent) ?: return
        _pendingDeepLinkCloneId.value = cloneId
        viewModelScope.launch {
            repository.logEvent(cloneId, "DEEPLINK", "Routed incoming deep-link session -> $cloneId")
        }
    }

    fun consumePendingDeepLink() {
        _pendingDeepLinkCloneId.value = null
    }

    fun verifyPin(clone: CloneInstanceEntity, enteredPin: String): Boolean {
        val hash = clone.pinHash ?: return true
        return sha256(enteredPin) == hash
    }

    fun clearLogs() {
        viewModelScope.launch { repository.clearLogs() }
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun clearMessage() {
        _snackbarMessage.value = null
    }

    fun generateRandomAndroidId(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
