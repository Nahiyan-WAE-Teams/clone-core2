package com.example

import android.app.Application
import android.os.Build
import com.example.data.db.CloneCoreDatabase
import com.example.data.repository.CloneRepository
import com.example.engine.network.ProxyRotationEngine
import com.example.engine.process.SandboxProcessManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class CloneCoreApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: CloneCoreDatabase
        private set

    lateinit var repository: CloneRepository
        private set

    lateinit var processManager: SandboxProcessManager
        private set

    lateinit var proxyEngine: ProxyRotationEngine
        private set

    val isMainProcess: Boolean by lazy {
        val currentProc = resolveCurrentProcessName()
        currentProc == packageName || !currentProc.contains(":")
    }

    override fun onCreate() {
        super.onCreate()
        if (!isMainProcess) {
            // Remote :sandbox_p0..p3 processes only run their bound SandboxSlotService
            return
        }

        database = CloneCoreDatabase.getInstance(this)
        repository = CloneRepository(
            cloneDao = database.cloneDao(),
            proxyNodeDao = database.proxyNodeDao(),
            auditLogDao = database.auditLogDao()
        )
        proxyEngine = ProxyRotationEngine().apply {
            startLocalRelay()
        }
        processManager = SandboxProcessManager(this) { cloneId, category, message ->
            appScope.launch {
                repository.logEvent(cloneId, category, message)
            }
        }

        appScope.launch {
            repository.resetAllRunningStates()
            repository.logEvent(
                cloneId = null,
                category = "KERNEL",
                message = "CloneCore Sandbox Engine initialized (main PID=${android.os.Process.myPid()})"
            )
        }
    }

    private fun resolveCurrentProcessName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName()
        }
        return try {
            File("/proc/self/cmdline").readText().trim('\u0000', ' ', '\n')
        } catch (_: Exception) {
            packageName
        }
    }
}
