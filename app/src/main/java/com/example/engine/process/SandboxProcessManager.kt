package com.example.engine.process

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.example.data.db.CloneInstanceEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SlotProcessTelemetry(
    val slotIndex: Int,
    val processSuffix: String,
    val isBound: Boolean = false,
    val pid: Int = 0,
    val cloneId: String? = null,
    val heapUsedMb: Float = 0f,
    val nativeUsedMb: Float = 0f,
    val threadCount: Int = 0,
    val uptimeMs: Long = 0L,
    val lastStatus: String = "IDLE"
)

class SandboxProcessManager(
    private val context: Context,
    private val onSlotEvent: (cloneId: String?, category: String, message: String) -> Unit
) {
    private val slotClasses = listOf(
        SandboxSlot0Service::class.java,
        SandboxSlot1Service::class.java,
        SandboxSlot2Service::class.java,
        SandboxSlot3Service::class.java
    )

    private val initialSlots = (0..3).associateWith { idx ->
        SlotProcessTelemetry(slotIndex = idx, processSuffix = ":sandbox_p$idx")
    }

    private val _slotStates = MutableStateFlow(initialSlots)
    val slotStates: StateFlow<Map<Int, SlotProcessTelemetry>> = _slotStates.asStateFlow()

    private val messengers = mutableMapOf<Int, Messenger>()
    private val connections = mutableMapOf<Int, ServiceConnection>()
    private val pendingInits = mutableMapOf<Int, CloneInstanceEntity>()

    private val replyHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == BaseSandboxSlotService.MSG_TELEMETRY_REPLY) {
                val b = msg.data
                val slotIdx = b.getInt(BaseSandboxSlotService.KEY_SLOT_INDEX, -1)
                if (slotIdx in 0..3) {
                    val pid = b.getInt(BaseSandboxSlotService.KEY_PID, 0)
                    val cloneId = b.getString(BaseSandboxSlotService.KEY_CLONE_ID)?.takeIf { it.isNotBlank() }
                    val heap = b.getFloat(BaseSandboxSlotService.KEY_HEAP_MB, 0f)
                    val nativeMb = b.getFloat(BaseSandboxSlotService.KEY_NATIVE_MB, 0f)
                    val threads = b.getInt(BaseSandboxSlotService.KEY_THREAD_COUNT, 0)
                    val uptime = b.getLong(BaseSandboxSlotService.KEY_UPTIME_MS, 0L)
                    val note = b.getString(BaseSandboxSlotService.KEY_STATUS_NOTE) ?: "ACTIVE"

                    _slotStates.update { map ->
                        val prev = map[slotIdx] ?: SlotProcessTelemetry(slotIdx, ":sandbox_p$slotIdx")
                        map + (slotIdx to prev.copy(
                            isBound = true,
                            pid = pid,
                            cloneId = cloneId,
                            heapUsedMb = heap,
                            nativeUsedMb = nativeMb,
                            threadCount = threads,
                            uptimeMs = uptime,
                            lastStatus = note
                        ))
                    }
                }
            }
        }
    }

    private val replyMessenger = Messenger(replyHandler)

    fun startCloneInSlot(clone: CloneInstanceEntity) {
        val slotIdx = clone.slotIndex.coerceIn(0, 3)
        val existingMessenger = messengers[slotIdx]
        if (existingMessenger != null) {
            sendInitMessage(existingMessenger, clone)
            return
        }

        pendingInits[slotIdx] = clone
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val remote = Messenger(service)
                messengers[slotIdx] = remote
                val pending = pendingInits.remove(slotIdx) ?: clone
                sendInitMessage(remote, pending)
                onSlotEvent(
                    pending.id,
                    "PROCESS",
                    "Bound isolated OS process :sandbox_p$slotIdx for ${pending.cloneLabel}"
                )
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                messengers.remove(slotIdx)
                connections.remove(slotIdx)
                _slotStates.update { map ->
                    map + (slotIdx to SlotProcessTelemetry(slotIdx, ":sandbox_p$slotIdx", lastStatus = "DISCONNECTED"))
                }
            }
        }
        connections[slotIdx] = conn
        val intent = Intent(context, slotClasses[slotIdx])
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
    }

    private fun sendInitMessage(remote: Messenger, clone: CloneInstanceEntity) {
        val msg = Message.obtain(null, BaseSandboxSlotService.MSG_INIT_SANDBOX).apply {
            replyTo = replyMessenger
            data = Bundle().apply {
                putString(BaseSandboxSlotService.KEY_CLONE_ID, clone.id)
                putString(BaseSandboxSlotService.KEY_SOURCE_PKG, clone.sourcePackageName)
                putString(BaseSandboxSlotService.KEY_VIRTUAL_PKG, clone.virtualPackageName)
                putString(BaseSandboxSlotService.KEY_ANDROID_ID, clone.virtualAndroidId)
                putInt(BaseSandboxSlotService.KEY_THREAD_PRIORITY, clone.threadPriorityBoost)
            }
        }
        try {
            remote.send(msg)
        } catch (_: Exception) {
        }
    }

    fun pingAllActiveSlots() {
        messengers.forEach { (_, remote) ->
            val msg = Message.obtain(null, BaseSandboxSlotService.MSG_PING_TELEMETRY).apply {
                replyTo = replyMessenger
            }
            try {
                remote.send(msg)
            } catch (_: Exception) {
            }
        }
    }

    fun trimSlotMemory(slotIndex: Int) {
        val remote = messengers[slotIndex] ?: return
        val msg = Message.obtain(null, BaseSandboxSlotService.MSG_TRIM_MEMORY).apply {
            replyTo = replyMessenger
        }
        try {
            remote.send(msg)
            onSlotEvent(null, "KERNEL", "Dispatched memory trim signal to :sandbox_p$slotIndex")
        } catch (_: Exception) {
        }
    }

    fun stopSlot(slotIndex: Int) {
        val conn = connections.remove(slotIndex)
        messengers.remove(slotIndex)
        pendingInits.remove(slotIndex)
        if (conn != null) {
            try {
                context.unbindService(conn)
            } catch (_: Exception) {
            }
        }
        val stoppedClone = _slotStates.value[slotIndex]?.cloneId
        _slotStates.update { map ->
            map + (slotIndex to SlotProcessTelemetry(slotIndex, ":sandbox_p$slotIndex", lastStatus = "TERMINATED"))
        }
        onSlotEvent(stoppedClone, "PROCESS", "Terminated sandbox process slot :sandbox_p$slotIndex")
    }

    fun pickAvailableSlotIndex(preferred: Int? = null): Int {
        if (preferred != null && preferred in 0..3) return preferred
        val current = _slotStates.value
        return (0..3).firstOrNull { current[it]?.isBound != true } ?: 0
    }
}
