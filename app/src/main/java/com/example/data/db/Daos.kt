package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CloneDao {
    @Query("SELECT * FROM clone_instances ORDER BY isRunning DESC, lastLaunchedAt DESC, createdAt DESC")
    fun observeAllClones(): Flow<List<CloneInstanceEntity>>

    @Query("SELECT * FROM clone_instances WHERE id = :id LIMIT 1")
    fun observeCloneById(id: String): Flow<CloneInstanceEntity?>

    @Query("SELECT * FROM clone_instances WHERE id = :id LIMIT 1")
    suspend fun getCloneById(id: String): CloneInstanceEntity?

    @Query("SELECT * FROM clone_instances WHERE sourcePackageName = :pkg ORDER BY cloneIndex ASC")
    suspend fun getClonesForPackage(pkg: String): List<CloneInstanceEntity>

    @Query("SELECT * FROM clone_instances")
    suspend fun getAllClonesSnapshot(): List<CloneInstanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClone(clone: CloneInstanceEntity)

    @Update
    suspend fun updateClone(clone: CloneInstanceEntity)

    @Query("DELETE FROM clone_instances WHERE id = :id")
    suspend fun deleteCloneById(id: String)

    @Query("UPDATE clone_instances SET isRunning = :running, activePid = :pid WHERE id = :id")
    suspend fun updateRunningState(id: String, running: Boolean, pid: Int)

    @Query("UPDATE clone_instances SET isRunning = 0, activePid = 0")
    suspend fun resetAllRunningStates()
}

@Dao
interface ProxyNodeDao {
    @Query("SELECT * FROM proxy_nodes ORDER BY isActive DESC, latencyMs ASC")
    fun observeAllNodes(): Flow<List<ProxyNodeEntity>>

    @Query("SELECT * FROM proxy_nodes WHERE isActive = 1")
    suspend fun getActiveNodes(): List<ProxyNodeEntity>

    @Query("SELECT * FROM proxy_nodes WHERE id = :id LIMIT 1")
    suspend fun getNodeById(id: String): ProxyNodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNode(node: ProxyNodeEntity)

    @Query("DELETE FROM proxy_nodes WHERE id = :id")
    suspend fun deleteNodeById(id: String)

    @Query("UPDATE proxy_nodes SET latencyMs = :latencyMs, lastCheckedAt = :checkedAt, successCount = successCount + :incSuccess, failureCount = failureCount + :incFail WHERE id = :id")
    suspend fun updateProbeResult(id: String, latencyMs: Int, checkedAt: Long, incSuccess: Int, incFail: Int)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM kernel_audit_logs ORDER BY timestamp DESC LIMIT 120")
    fun observeRecentLogs(): Flow<List<KernelAuditLogEntity>>

    @Query("SELECT * FROM kernel_audit_logs WHERE cloneId = :cloneId ORDER BY timestamp DESC LIMIT 80")
    fun observeLogsForClone(cloneId: String): Flow<List<KernelAuditLogEntity>>

    @Insert
    suspend fun insertLog(log: KernelAuditLogEntity)

    @Query("DELETE FROM kernel_audit_logs WHERE id NOT IN (SELECT id FROM kernel_audit_logs ORDER BY timestamp DESC LIMIT 200)")
    suspend fun trimOldLogs()

    @Query("DELETE FROM kernel_audit_logs")
    suspend fun clearAllLogs()
}
