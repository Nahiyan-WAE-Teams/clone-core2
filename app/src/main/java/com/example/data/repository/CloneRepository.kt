package com.example.data.repository

import com.example.data.db.AuditLogDao
import com.example.data.db.CloneDao
import com.example.data.db.CloneInstanceEntity
import com.example.data.db.KernelAuditLogEntity
import com.example.data.db.ProxyNodeDao
import com.example.data.db.ProxyNodeEntity
import kotlinx.coroutines.flow.Flow

class CloneRepository(
    private val cloneDao: CloneDao,
    private val proxyNodeDao: ProxyNodeDao,
    private val auditLogDao: AuditLogDao
) {
    val allClones: Flow<List<CloneInstanceEntity>> = cloneDao.observeAllClones()
    val allProxyNodes: Flow<List<ProxyNodeEntity>> = proxyNodeDao.observeAllNodes()
    val recentLogs: Flow<List<KernelAuditLogEntity>> = auditLogDao.observeRecentLogs()

    fun observeClone(id: String): Flow<CloneInstanceEntity?> = cloneDao.observeCloneById(id)

    fun observeLogsForClone(cloneId: String): Flow<List<KernelAuditLogEntity>> =
        auditLogDao.observeLogsForClone(cloneId)

    suspend fun getClone(id: String): CloneInstanceEntity? = cloneDao.getCloneById(id)

    suspend fun getAllClonesSnapshot(): List<CloneInstanceEntity> = cloneDao.getAllClonesSnapshot()

    suspend fun getClonesForPackage(pkg: String): List<CloneInstanceEntity> =
        cloneDao.getClonesForPackage(pkg)

    suspend fun upsertClone(clone: CloneInstanceEntity) = cloneDao.upsertClone(clone)

    suspend fun updateClone(clone: CloneInstanceEntity) = cloneDao.updateClone(clone)

    suspend fun deleteClone(id: String) = cloneDao.deleteCloneById(id)

    suspend fun setRunningState(id: String, running: Boolean, pid: Int) =
        cloneDao.updateRunningState(id, running, pid)

    suspend fun resetAllRunningStates() = cloneDao.resetAllRunningStates()

    suspend fun getActiveProxyNodes(): List<ProxyNodeEntity> = proxyNodeDao.getActiveNodes()

    suspend fun getProxyNode(id: String): ProxyNodeEntity? = proxyNodeDao.getNodeById(id)

    suspend fun upsertProxyNode(node: ProxyNodeEntity) = proxyNodeDao.upsertNode(node)

    suspend fun deleteProxyNode(id: String) = proxyNodeDao.deleteNodeById(id)

    suspend fun recordProxyProbe(id: String, latencyMs: Int, success: Boolean) {
        proxyNodeDao.updateProbeResult(
            id = id,
            latencyMs = latencyMs,
            checkedAt = System.currentTimeMillis(),
            incSuccess = if (success) 1 else 0,
            incFail = if (success) 0 else 1
        )
    }

    suspend fun logEvent(cloneId: String?, category: String, message: String) {
        auditLogDao.insertLog(
            KernelAuditLogEntity(
                cloneId = cloneId,
                category = category,
                message = message,
                timestamp = System.currentTimeMillis()
            )
        )
        auditLogDao.trimOldLogs()
    }

    suspend fun clearLogs() = auditLogDao.clearAllLogs()
}
