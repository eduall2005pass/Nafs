package com.nafsshield.service

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nafsshield.ui.MainActivity
import com.nafsshield.util.Constants
import com.nafsshield.util.PinManager
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class NafsVpnService : VpnService() {

    companion object {
        const val TAG = "NafsVpnService"
        @Volatile var isRunning = false
        val blockedCount = AtomicInteger(0)

        enum class DnsState { PRIMARY, SECONDARY, FALLBACK }
        @Volatile var currentDnsState = DnsState.PRIMARY
        @Volatile var currentDns = Constants.DEFAULT_DNS_PRIMARY
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var pinManager: PinManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Constants.ACTION_STOP_VPN) {
            stopVpn()
            return START_NOT_STICKY
        }
        pinManager = PinManager(this)
        if (!isRunning) startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        // Foreground notification আগে — crash এড়াতে
        ensureNotificationChannel()
        startForeground(Constants.NOTIF_ID_VPN, buildVpnNotification())

        val builder = Builder()
            .addAddress(Constants.VPN_ADDRESS, 32)
            .addRoute(Constants.VPN_ROUTE, 0)
            .addDnsServer(pinManager.primaryDns)
            .setSession("NafsShield VPN")
            .setBlocking(true)

        // সব app এর traffic route করো (কোনো app exclude নেই)
        try {
            vpnInterface = builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "VPN establish failed: ${e.message}")
            stopSelf()
            return
        }

        if (vpnInterface == null) {
            Log.e(TAG, "VPN establish returned null")
            stopSelf()
            return
        }

        isRunning = true
        running.set(true)
        currentDns = pinManager.primaryDns
        currentDnsState = DnsState.PRIMARY

        scope.launch { runPacketLoop() }
        scope.launch { runDnsHealthCheck() }

        Log.i(TAG, "VPN started — DNS: ${pinManager.primaryDns}")
    }

    private suspend fun runPacketLoop() {
        withContext(Dispatchers.IO) {
        val fd     = vpnInterface?.fileDescriptor ?: return@withContext
        val input  = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val buffer = ByteBuffer.allocate(32767)

        while (running.get()) {
            try {
                buffer.clear()
                val length = input.read(buffer.array())
                if (length <= 0) { delay(1); continue }

                val packet = buffer.array().copyOf(length)

                // IPv4 only
                if (length < 20 || ((packet[0].toInt() shr 4) and 0xF) != 4) {
                    output.write(packet); continue
                }

                val protocol   = packet[9].toInt() and 0xFF
                // UDP only (DNS is UDP port 53)
                if (protocol != 17) { output.write(packet); continue }

                val ipHeaderLen = (packet[0].toInt() and 0xF) * 4
                if (length < ipHeaderLen + 8) { output.write(packet); continue }

                val destPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                               (packet[ipHeaderLen + 3].toInt() and 0xFF)

                if (destPort == Constants.DNS_PORT) {
                    val dnsStart   = ipHeaderLen + 8
                    val dnsPayload = packet.copyOfRange(dnsStart, length)
                    val domain     = parseDnsDomain(dnsPayload)

                    if (domain != null && isDomainBlocked(domain)) {
                        blockedCount.incrementAndGet()
                        val response = buildNxDomainResponse(dnsPayload)
                        val udpResp  = buildUdpResponse(packet, response, ipHeaderLen)
                        output.write(udpResp)
                        Log.d(TAG, "DNS blocked: $domain")
                        continue
                    }
                }
                output.write(packet)

            } catch (e: Exception) {
                if (running.get()) Log.e(TAG, "Packet loop error: ${e.message}")
            }
        }
    }
    }

    private suspend fun runDnsHealthCheck() = withContext(Dispatchers.IO) {
        while (running.get()) {
            delay(30_000)
            val primaryOk   = isDnsAlive(pinManager.primaryDns)
            val secondaryOk = if (!primaryOk) isDnsAlive(pinManager.secondaryDns) else false

            val newState = when {
                primaryOk   -> DnsState.PRIMARY
                secondaryOk -> DnsState.SECONDARY
                else        -> DnsState.FALLBACK
            }
            if (newState != currentDnsState) {
                currentDnsState = newState
                currentDns = when (newState) {
                    DnsState.PRIMARY   -> pinManager.primaryDns
                    DnsState.SECONDARY -> pinManager.secondaryDns
                    DnsState.FALLBACK  -> Constants.FALLBACK_DNS
                }
                Log.i(TAG, "DNS switched to $newState: $currentDns")
            }
        }
    }

    private fun isDnsAlive(dnsServer: String): Boolean = try {
        val socket = java.net.DatagramSocket()
        socket.soTimeout = 3000
        val query  = buildSimpleDnsQuery("google.com")
        val addr   = java.net.InetAddress.getByName(dnsServer)
        socket.send(java.net.DatagramPacket(query, query.size, addr, Constants.DNS_PORT))
        val buf    = ByteArray(512)
        socket.receive(java.net.DatagramPacket(buf, buf.size))
        socket.close()
        true
    } catch (e: Exception) { false }

    private fun isDomainBlocked(domain: String): Boolean {
        val lower = domain.lowercase().trimEnd('.')
        return Constants.BLOCKED_DOMAINS.any { blocked ->
            lower == blocked || lower.endsWith(".$blocked")
        }
    }

    private fun parseDnsDomain(dns: ByteArray): String? {
        return try {
            if (dns.size < 13) {
                null
            } else {
                val sb = StringBuilder()
                var i = 12
                while (i < dns.size) {
                    val len = dns[i].toInt() and 0xFF
                    if (len == 0) break
                    if (sb.isNotEmpty()) sb.append('.')
                    i++
                    if (i + len > dns.size) return null
                    sb.append(String(dns, i, len))
                    i += len
                }
                sb.toString()
            }
        } catch (e: Exception) { null }

    private fun buildNxDomainResponse(query: ByteArray): ByteArray {
        val r = query.copyOf()
        r[2] = (r[2].toInt() or 0x80).toByte()
        r[3] = (r[3].toInt() or 0x03).toByte()
        return r
    }

    private fun buildSimpleDnsQuery(domain: String): ByteArray {
        val parts = domain.split(".")
        val size  = 12 + parts.sumOf { it.length + 1 } + 1 + 4
        val buf   = ByteBuffer.allocate(size)
        buf.putShort(1); buf.putShort(0x0100.toShort())
        buf.putShort(1); buf.putShort(0); buf.putShort(0); buf.putShort(0)
        parts.forEach { p -> buf.put(p.length.toByte()); buf.put(p.toByteArray()) }
        buf.put(0); buf.putShort(1); buf.putShort(1)
        return buf.array()
    }

    private fun buildUdpResponse(orig: ByteArray, payload: ByteArray, ipLen: Int): ByteArray {
        val total  = ipLen + 8 + payload.size
        val result = ByteArray(total)
        System.arraycopy(orig, 0, result, 0, ipLen)
        System.arraycopy(orig, 12, result, 16, 4)
        System.arraycopy(orig, 16, result, 12, 4)
        result[2] = (total shr 8).toByte(); result[3] = (total and 0xFF).toByte()
        result[ipLen]     = orig[ipLen + 2]; result[ipLen + 1] = orig[ipLen + 3]
        result[ipLen + 2] = orig[ipLen];     result[ipLen + 3] = orig[ipLen + 1]
        val udpLen = 8 + payload.size
        result[ipLen + 4] = (udpLen shr 8).toByte(); result[ipLen + 5] = (udpLen and 0xFF).toByte()
        result[ipLen + 6] = 0; result[ipLen + 7] = 0
        System.arraycopy(payload, 0, result, ipLen + 8, payload.size)
        recalcIpChecksum(result, ipLen)
        return result
    }

    private fun recalcIpChecksum(packet: ByteArray, headerLen: Int) {
        packet[10] = 0; packet[11] = 0
        var sum = 0
        for (i in 0 until headerLen step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val cs = sum.inv() and 0xFFFF
        packet[10] = (cs shr 8).toByte(); packet[11] = (cs and 0xFF).toByte()
    }

    private fun buildVpnNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.CHANNEL_ID_GUARD)
            .setContentTitle("NafsShield VPN 🔒")
            .setContentText("DNS ফিল্টারিং সক্রিয়")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(Constants.CHANNEL_ID_GUARD) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        Constants.CHANNEL_ID_GUARD,
                        "NafsShield Service",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    private fun stopVpn() {
        running.set(false)
        isRunning = false
        scope.cancel()
        try { vpnInterface?.close() } catch (e: Exception) { /* ignore */ }
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    // Samsung A35: task remove এ service restart
    override fun onRevoke() {
        Log.w(TAG, "VPN revoked by system")
        stopVpn()
        super.onRevoke()
    }
}
