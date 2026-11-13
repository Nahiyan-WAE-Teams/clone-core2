package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clone_instances")
data class CloneInstanceEntity(
    @PrimaryKey val id: String,
    val sourcePackageName: String,
    val appLabel: String,
    val cloneLabel: String,
    val cloneIndex: Int,
    val slotIndex: Int,
    val virtualPackageName: String,
    val virtualAuthorityPrefix: String,
    val virtualAndroidId: String,
    val virtualDeviceModel: String,
    val abiArchitecture: String,
    val sandboxRootPath: String,
    val proxyEnabled: Boolean,
    val proxyPoolId: String?,
    val proxyRotationMode: String,
    val currentProxyLabel: String,
    val deepLinkUri: String,
    val pinHash: String?,
    val gmsIsolationEnabled: Boolean,
    val clipboardIsolationEnabled: Boolean,
    val threadPriorityBoost: Int,
    val memoryLimitMb: Int,
    val isPinnedShortcut: Boolean,
    val isRunning: Boolean,
    val activePid: Int,
    val lastLaunchedAt: Long,
    val createdAt: Long,
    val totalUptimeMs: Long,
    val launchCount: Int
)

@Entity(tableName = "proxy_nodes")
data class ProxyNodeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val protocol: String, // HTTP, HTTPS, SOCKS5
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
    val regionTag: String,
    val latencyMs: Int, // -1 = untested/unreachable
    val lastCheckedAt: Long,
    val isActive: Boolean,
    val successCount: Int,
    val failureCount: Int
)

@Entity(tableName = "kernel_audit_logs")
data class KernelAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cloneId: String?,
    val category: String, // VFS, BINDER, PROXY, PROCESS, DEEPLINK, KERNEL
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
