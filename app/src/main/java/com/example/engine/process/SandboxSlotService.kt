package com.example.engine.process

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.Debug
import com.example.engine.binder.BinderProxyEngine
import com.example.engine.vfs.SandboxFileSystem
import com.example.engine.vfs.VirtualContextWrapper

/**
 * Remote multi-process workspace service.
 * Each subclass runs in a dedicated app process (:sandbox_p0 .. :sandbox_p3)
 * with its own PID and ART heap. These processes share the host app UID and
 * permissions; they are not isolated security principals and do not execute
 * the target APK as a guest application.
 */
abstract class BaseSandboxSlotService : Service() {

    abstract val slotIndex: Int

    private var activeCloneId: String? = null
    private var virtualContext: VirtualContextWrapper? = null
    private var startedAtMs: Long = 0L

    private val incomingHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_INIT_SANDBOX -> {
                    val data = msg.data
                    val cloneId = data.getString(KEY_CLONE_ID) ?: return
                    val sourcePkg = data.getString(KEY_SOURCE_PKG) ?: packageName
                    val virtualPkg = data.getString(KEY_VIRTUAL_PKG) ?: "$packageName.vapp.$cloneId"
                    val androidId = data.getString(KEY_ANDROID_ID) ?: "0000000000000000"
                    val prio = data.getInt(KEY_THREAD_PRIORITY, 0)

                    activeCloneId = cloneId
                    startedAtMs = System.currentTimeMillis()
                    try {
                        Process.setThreadPriority(prio.coerceIn(-19, 19))
                    } catch (_: Exception) {
                    }

                    virtualContext = VirtualContextWrapper(
                        base = applicationContext,
                        cloneId = cloneId,
                        virtualPackageName = virtualPkg,
                        virtualAndroidId = androidId
                    )
                    SandboxFileSystem.writeSessionTokenFile(
                        context = applicationContext,
                        cloneId = cloneId,
                        slotIndex = slotIndex,
                        pid = Process.myPid()
                    )
                    val diagnostics = BinderProxyEngine.runHookVerificationPass(
                        context = applicationContext,
                        cloneId = cloneId,
                        sourcePackage = sourcePkg,
                        virtualPackage = virtualPkg
                    )
                    replyWithTelemetry(msg.replyTo, diagnostics.joinToString(" | "))
                }

                MSG_PING_TELEMETRY -> {
                    replyWithTelemetry(msg.replyTo, "HEARTBEAT_OK")
                }

                MSG_APPLY_PRIORITY -> {
                    val prio = msg.data.getInt(KEY_THREAD_PRIORITY, 0)
                    try {
                        Process.setThreadPriority(prio.coerceIn(-19, 19))
                    } catch (_: Exception) {
                    }
                    replyWithTelemetry(msg.replyTo, "PRIORITY_UPDATED($prio)")
                }

                MSG_TRIM_MEMORY -> {
                    System.gc()
                    replyWithTelemetry(msg.replyTo, "HEAP_TRIMMED")
                }

                else -> super.handleMessage(msg)
            }
        }
    }

    private val messenger = Messenger(incomingHandler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun replyWithTelemetry(replyTo: Messenger?, statusNote: String) {
        if (replyTo == null) return
        val rt = Runtime.getRuntime()
        val heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)
        val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)
        val bundle = Bundle().apply {
            putInt(KEY_SLOT_INDEX, slotIndex)
            putInt(KEY_PID, Process.myPid())
            putInt(KEY_UID, Process.myUid())
            putString(KEY_CLONE_ID, activeCloneId ?: "")
            putFloat(KEY_HEAP_MB, heapUsedMb)
            putFloat(KEY_NATIVE_MB, nativeMb)
            putInt(KEY_THREAD_COUNT, Thread.activeCount())
            putLong(KEY_UPTIME_MS, if (startedAtMs > 0) System.currentTimeMillis() - startedAtMs else 0L)
            putString(KEY_STATUS_NOTE, statusNote)
        }
        val reply = Message.obtain(null, MSG_TELEMETRY_REPLY).apply {
            data = bundle
        }
        try {
            replyTo.send(reply)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val MSG_INIT_SANDBOX = 101
        const val MSG_PING_TELEMETRY = 102
        const val MSG_APPLY_PRIORITY = 103
        const val MSG_TRIM_MEMORY = 104
        const val MSG_TELEMETRY_REPLY = 201

        const val KEY_SLOT_INDEX = "slot_index"
        const val KEY_CLONE_ID = "clone_id"
        const val KEY_SOURCE_PKG = "source_pkg"
        const val KEY_VIRTUAL_PKG = "virtual_pkg"
        const val KEY_ANDROID_ID = "android_id"
        const val KEY_THREAD_PRIORITY = "thread_priority"
        const val KEY_PID = "pid"
        const val KEY_UID = "uid"
        const val KEY_HEAP_MB = "heap_mb"
        const val KEY_NATIVE_MB = "native_mb"
        const val KEY_THREAD_COUNT = "thread_count"
        const val KEY_UPTIME_MS = "uptime_ms"
        const val KEY_STATUS_NOTE = "status_note"
    }
}

class SandboxSlot0Service : BaseSandboxSlotService() {
    override val slotIndex: Int = 0
}

class SandboxSlot1Service : BaseSandboxSlotService() {
    override val slotIndex: Int = 1
}

class SandboxSlot2Service : BaseSandboxSlotService() {
    override val slotIndex: Int = 2
}

class SandboxSlot3Service : BaseSandboxSlotService() {
    override val slotIndex: Int = 3
}
