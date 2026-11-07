package com.focushome

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A VPN that routes a single made-up address, [DNS_ADDRESS], and tells Android to use it as the DNS server.
 * So only DNS lookups reach this service; the rest of the phone's traffic never touches it.
 * Lookups for [DnsFilter.BLOCKED_DOMAINS] are answered "no such domain", the rest are forwarded unchanged.
 */
class DnsFilterService : VpnService() {
    @Volatile private var tun: ParcelFileDescriptor? = null
    private var output: FileOutputStream? = null
    private var pool: ExecutorService? = null
    private var upstreams: List<InetAddress> = emptyList()
    private var upstreamsAt = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (tun == null) establish()
        return START_STICKY
    }

    private fun establish() {
        val pfd = try {
            Builder()
                .setSession("Focus Home web filter")
                .addAddress(TUN_ADDRESS, 32)
                .addRoute(DNS_ADDRESS, 32)
                .addDnsServer(DNS_ADDRESS)
                .setBlocking(true)
                .setMetered(false)
                .establish()
        } catch (_: Exception) {
            null
        }
        if (pfd == null) {
            // No consent (another VPN took over, or it was disconnected). Focus Home asks again.
            stopSelf()
            return
        }
        tun = pfd
        output = FileOutputStream(pfd.fileDescriptor)
        pool = Executors.newFixedThreadPool(4)
        DnsFilter.setRunning(true)
        Thread({ readLoop(pfd) }, "focus-dns").start()
    }

    private fun readLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val buf = ByteArray(32_767)
        while (tun === pfd) {
            val n = try {
                input.read(buf)
            } catch (_: IOException) {
                break
            }
            if (n > 0) {
                try {
                    handle(buf.copyOf(n))
                } catch (_: Exception) {
                    // A malformed packet; drop it.
                }
            }
        }
    }

    private fun handle(packet: ByteArray) {
        val q = Packets.parseDnsQuery(packet) ?: return
        val name = Dns.questionName(q.dns) ?: return
        if (DnsFilter.isBlocked(name.first)) {
            write(Packets.reply(q, Dns.nxdomain(q.dns, name.second)))
        } else {
            pool?.execute { forward(q) }
        }
    }

    private fun forward(q: Packets.Query) {
        for (server in currentUpstreams().take(3)) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = 4_000
                    socket.send(DatagramPacket(q.dns, q.dns.size, server, 53))
                    val buf = ByteArray(4_096)
                    val answer = DatagramPacket(buf, buf.size)
                    socket.receive(answer)
                    write(Packets.reply(q, buf.copyOf(answer.length)))
                }
                return
            } catch (_: Exception) {
                // Timed out or unreachable; try the next server.
            }
        }
    }

    private fun currentUpstreams(): List<InetAddress> = synchronized(this) {
        val now = SystemClock.elapsedRealtime()
        if (upstreams.isEmpty() || now - upstreamsAt > 15_000) {
            upstreams = DnsFilter.upstreams(this)
            upstreamsAt = now
        }
        upstreams
    }

    private fun write(packet: ByteArray) {
        val out = output ?: return
        synchronized(out) {
            try {
                out.write(packet)
            } catch (_: IOException) {
            }
        }
    }

    private fun shutdown() {
        val pfd = tun
        tun = null
        output = null
        pool?.shutdownNow()
        pool = null
        try {
            pfd?.close()
        } catch (_: IOException) {
        }
        DnsFilter.setRunning(false)
    }

    override fun onRevoke() {
        shutdown()
        stopSelf()
        // If Android still lets us run (the user tapped Disconnect but consent remains), come straight back.
        DnsFilter.start(applicationContext)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    companion object {
        const val TUN_ADDRESS = "10.111.222.1"
        const val DNS_ADDRESS = "10.111.222.2"
    }
}

/** Just enough IPv4/UDP to read DNS queries off the tunnel and write answers back. */
internal object Packets {
    class Query(val header: ByteArray, val ihl: Int, val dns: ByteArray)

    fun parseDnsQuery(p: ByteArray): Query? {
        if (p.size < 28 || (p[0].toInt() shr 4) != 4) return null
        val ihl = (p[0].toInt() and 0x0F) * 4
        if (ihl < 20 || p.size < ihl + 8 || p[9].toInt() != 17) return null
        if (u16(p, ihl + 2) != 53) return null
        val udpLen = u16(p, ihl + 4)
        val end = minOf(p.size, ihl + udpLen)
        if (end <= ihl + 8) return null
        return Query(p.copyOf(ihl + 8), ihl, p.copyOfRange(ihl + 8, end))
    }

    /** An IPv4/UDP packet from the DNS address back to whoever asked. UDP checksum 0 means "none", valid on IPv4. */
    fun reply(q: Query, dns: ByteArray): ByteArray {
        val total = 28 + dns.size
        val out = ByteArray(total)
        out[0] = 0x45
        put16(out, 2, total)
        put16(out, 6, 0x4000) // don't fragment
        out[8] = 64 // TTL
        out[9] = 17 // UDP
        System.arraycopy(q.header, 16, out, 12, 4) // source = the DNS address they sent to
        System.arraycopy(q.header, 12, out, 16, 4) // destination = the asker
        put16(out, 10, checksum(out, 20))
        put16(out, 20, u16(q.header, q.ihl + 2))
        put16(out, 22, u16(q.header, q.ihl))
        put16(out, 24, 8 + dns.size)
        System.arraycopy(dns, 0, out, 28, dns.size)
        return out
    }

    private fun checksum(b: ByteArray, len: Int): Int {
        var sum = 0
        var i = 0
        while (i < len) {
            sum += u16(b, i)
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    fun put16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v shr 8).toByte()
        b[i + 1] = v.toByte()
    }
}

internal object Dns {
    /** The first question's name and the offset just past that question, or null if this isn't a plain query. */
    fun questionName(m: ByteArray): Pair<String, Int>? {
        if (m.size < 12) return null
        if (m[2].toInt() and 0x80 != 0) return null // a response, not a query
        if (Packets.u16(m, 4) < 1) return null
        val name = StringBuilder()
        var i = 12
        while (true) {
            if (i >= m.size) return null
            val len = m[i].toInt() and 0xFF
            if (len == 0) {
                i++
                break
            }
            if (len and 0xC0 != 0 || i + 1 + len > m.size) return null
            if (name.isNotEmpty()) name.append('.')
            name.append(String(m, i + 1, len, Charsets.US_ASCII))
            i += 1 + len
        }
        val end = i + 4 // type and class
        if (end > m.size) return null
        return name.toString().lowercase() to end
    }

    /** "No such domain" for the query, echoing its ID and question. */
    fun nxdomain(query: ByteArray, questionEnd: Int): ByteArray {
        val r = query.copyOf(questionEnd)
        r[2] = (0x80 or (query[2].toInt() and 0x79)).toByte() // response; keep opcode and "recursion desired"
        r[3] = (0x80 or 3).toByte() // recursion available, NXDOMAIN
        Packets.put16(r, 4, 1)
        Packets.put16(r, 6, 0)
        Packets.put16(r, 8, 0)
        Packets.put16(r, 10, 0)
        return r
    }
}
