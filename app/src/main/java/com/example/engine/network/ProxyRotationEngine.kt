package com.example.engine.network

import com.example.data.db.ProxyNodeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

data class RelayStatus(
    val isRunning: Boolean = false,
    val localHost: String = "127.0.0.1",
    val localPort: Int = 0,
    val activeUpstreamLabel: String = "No upstream configured",
    val totalRelayedConnections: Int = 0,
    val lastRotationAt: Long = 0L
)

/**
 * Local raw TCP relay with a selectable upstream socket endpoint.
 * The relay forwards bytes unchanged; it does not capture guest-app traffic,
 * negotiate SOCKS5/HTTP CONNECT on behalf of clients, or implement proxy
 * authentication. It is therefore not yet a per-clone traffic-routing engine.
 */
class ProxyRotationEngine {

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val activeUpstream = AtomicReference<ProxyNodeEntity?>(null)
    private var connectionCount = 0

    private val _relayStatus = MutableStateFlow(RelayStatus())
    val relayStatus: StateFlow<RelayStatus> = _relayStatus.asStateFlow()

    fun startLocalRelay() {
        if (running.get()) return
        thread(name = "CloneCore-LocalRelay", isDaemon = true) {
            try {
                val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
                serverSocket = ss
                running.set(true)
                val port = ss.localPort
                _relayStatus.value = _relayStatus.value.copy(
                    isRunning = true,
                    localPort = port
                )

                while (running.get() && !ss.isClosed) {
                    val client = try {
                        ss.accept()
                    } catch (_: Exception) {
                        break
                    }
                    connectionCount++
                    _relayStatus.value = _relayStatus.value.copy(
                        totalRelayedConnections = connectionCount
                    )
                    thread(isDaemon = true) {
                        handleRelayClient(client)
                    }
                }
            } catch (_: Exception) {
                running.set(false)
                _relayStatus.value = _relayStatus.value.copy(isRunning = false, localPort = 0)
            }
        }
    }

    fun setUpstreamNode(node: ProxyNodeEntity?) {
        activeUpstream.set(node)
        val label = if (node == null) {
            "No upstream configured (incoming relay clients are closed)"
        } else {
            "${node.protocol}://${node.host}:${node.port} (${node.regionTag})"
        }
        _relayStatus.value = _relayStatus.value.copy(
            activeUpstreamLabel = label,
            lastRotationAt = System.currentTimeMillis()
        )
    }

    fun stopLocalRelay() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        _relayStatus.value = _relayStatus.value.copy(isRunning = false, localPort = 0)
    }

    private fun handleRelayClient(client: Socket) {
        val upstream = activeUpstream.get()
        if (upstream == null) {
            try {
                client.close()
            } catch (_: Exception) {
            }
            return
        }

        var remote: Socket? = null
        try {
            remote = Socket()
            remote.connect(InetSocketAddress(upstream.host, upstream.port), 5000)
            val cIn = client.getInputStream()
            val cOut = client.getOutputStream()
            val rIn = remote.getInputStream()
            val rOut = remote.getOutputStream()

            val t1 = thread(isDaemon = true) { pipeStreams(cIn, rOut) }
            pipeStreams(rIn, cOut)
            t1.join(2000)
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
            try { remote?.close() } catch (_: Exception) {}
        }
    }

    private fun pipeStreams(input: InputStream, output: OutputStream) {
        val buf = ByteArray(8192)
        try {
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                output.write(buf, 0, read)
                output.flush()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Performs a TCP reachability check and, for SOCKS5, a protocol greeting.
     * HTTP proxy checks currently measure TCP reachability only.
     */
    suspend fun probeProxyLatency(node: ProxyNodeEntity): Int = withContext(Dispatchers.IO) {
        val startNs = System.nanoTime()
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.soTimeout = 3500
            socket.connect(InetSocketAddress(node.host, node.port), 3500)

            if (node.protocol.equals("SOCKS5", ignoreCase = true)) {
                val out = socket.getOutputStream()
                val inp = socket.getInputStream()
                // Standard SOCKS5 greeting (VER=5, NMETHODS=1, NO_AUTH=0)
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                val resp = ByteArray(2)
                val n = inp.read(resp)
                if (n < 2 || resp[0] != 0x05.toByte()) {
                    return@withContext -1
                }
            }
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            elapsedMs
        } catch (_: Exception) {
            -1
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    fun selectNextRotationNode(
        pool: List<ProxyNodeEntity>,
        currentLabel: String
    ): ProxyNodeEntity? {
        val active = pool.filter { it.isActive }
        if (active.isEmpty()) return null
        val sorted = active.sortedWith(
            compareBy<ProxyNodeEntity> { if (it.latencyMs < 0) Int.MAX_VALUE else it.latencyMs }
        )
        val currentIdx = sorted.indexOfFirst {
            "${it.protocol}://${it.host}:${it.port}" == currentLabel
        }
        return if (currentIdx < 0 || currentIdx + 1 >= sorted.size) {
            sorted.first()
        } else {
            sorted[currentIdx + 1]
        }
    }
}
