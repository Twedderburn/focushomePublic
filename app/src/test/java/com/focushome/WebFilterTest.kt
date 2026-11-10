package com.focushome

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebFilterTest {
    // ---- Blocklist matching ----

    @Test fun blocksRedditAndXAndSubdomains() {
        listOf(
            "reddit.com", "www.reddit.com", "old.reddit.com", "i.redd.it", "preview.redd.it",
            "www.redditstatic.com", "x.com", "mobile.x.com", "api.x.com", "twitter.com",
            "abs.twimg.com", "pbs.twimg.com", "t.co", "REDDIT.COM", "reddit.com.",
        ).forEach { assertTrue(it, DnsFilter.isBlocked(it)) }
    }

    @Test fun blocksEncryptedDnsAndFirefoxCanary() {
        listOf("cloudflare-dns.com", "mozilla.cloudflare-dns.com", "dns.google", "use-application-dns.net")
            .forEach { assertTrue(it, DnsFilter.isBlocked(it)) }
    }

    @Test fun allowsLookalikesAndNormalSites() {
        listOf(
            "google.com", "example.com", "notreddit.com", "reddit.co", "myx.com", "box.com",
            "xx.com", "t.com", "co", "com", "", "x.co", "twitter.community",
        ).forEach { assertFalse(it, DnsFilter.isBlocked(it)) }
    }

    // ---- Adult sites ----

    @Test fun blocksBuiltInAdultSitesAndTlds() {
        DnsFilter.setAdultListForTest(emptySet()) // built-ins work without the bundled list
        listOf(
            "pornhub.com", "www.pornhub.com", "xvideos.com", "onlyfans.com", "chaturbate.com",
            "anything.xxx", "example.porn", "site.sex", "www.example.adult", "rule34.xxx",
        ).forEach { assertTrue(it, DnsFilter.isBlocked(it)) }
    }

    /** The real bundled file, read from the source tree. */
    private fun bundledAdultList(): Set<String> {
        val file = listOf("src/main/assets", "app/src/main/assets")
            .map { java.io.File(it, DnsFilter.ADULT_LIST_ASSET) }
            .first { it.exists() }
        return file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith('#') }.toHashSet()
    }

    @Test fun bundledAdultListIsSaneAndBlocks() {
        val list = bundledAdultList()
        assertTrue("list size ${list.size}", list.size > 20_000)
        list.forEach { assertTrue("bad entry '$it'", it.contains('.') && it == it.lowercase() && !it.contains(' ')) }
        DnsFilter.setAdultListForTest(list)
        // Sites only in the bundled list, not the built-ins.
        listOf("www.youporngay.com", "sex.com", "www.adultfriendfinder.com", "www.xnxx.tv")
            .filter { name -> generateSequence(name) { it.substringAfter('.', "").ifEmpty { null } }.any { it in list } }
            .also { assertTrue("expected some bundled-only hits", it.isNotEmpty()) }
            .forEach { assertTrue(it, DnsFilter.isBlocked(it)) }
    }

    @Test fun bundledAdultListLeavesEverydaySitesAlone() {
        DnsFilter.setAdultListForTest(bundledAdultList())
        listOf(
            "google.com", "www.google.com", "youtube.com", "gmail.com", "wikipedia.org", "en.wikipedia.org",
            "amazon.com", "apple.com", "microsoft.com", "github.com", "githubusercontent.com", "github.io",
            "blogspot.com", "tumblr.com", "wordpress.com", "medium.com", "imgur.com", "discord.com", "patreon.com",
            "cloudfront.net", "amazonaws.com", "akamaihd.net", "fastly.net", "cloudflare.com", "googleapis.com",
            "gstatic.com", "whatsapp.com", "instagram.com", "facebook.com", "netflix.com", "spotify.com",
            "dropbox.com", "claude.ai", "anthropic.com", "bbc.co.uk", "nytimes.com", "samsung.com", "sussex.ac.uk",
            "essex.gov.uk", "middlesex.edu", "adultlearning.org.uk",
        ).forEach { assertFalse(it, DnsFilter.isBlocked(it)) }
    }

    // ---- DNS / packet handling ----

    private fun dnsQuery(name: String, id: Int = 0x1234): ByteArray {
        val q = java.io.ByteArrayOutputStream()
        q.write(id shr 8); q.write(id and 0xFF)
        q.write(0x01); q.write(0x00) // RD
        q.write(0); q.write(1) // QDCOUNT
        repeat(6) { q.write(0) }
        name.split('.').forEach { label -> q.write(label.length); q.write(label.toByteArray()) }
        q.write(0)
        q.write(0); q.write(1) // A
        q.write(0); q.write(1) // IN
        return q.toByteArray()
    }

    private fun ipv4Udp(dns: ByteArray, srcPort: Int = 40000, dstPort: Int = 53): ByteArray {
        val p = ByteArray(28 + dns.size)
        p[0] = 0x45
        Packets.put16(p, 2, p.size)
        p[8] = 64; p[9] = 17
        byteArrayOf(10, 111, 222.toByte(), 1).copyInto(p, 12) // src = tun
        byteArrayOf(10, 111, 222.toByte(), 2).copyInto(p, 16) // dst = dns
        Packets.put16(p, 20, srcPort)
        Packets.put16(p, 22, dstPort)
        Packets.put16(p, 24, 8 + dns.size)
        dns.copyInto(p, 28)
        return p
    }

    @Test fun parsesQuestionName() {
        val (name, end) = Dns.questionName(dnsQuery("www.Reddit.com"))!!
        assertEquals("www.reddit.com", name)
        assertEquals(dnsQuery("www.reddit.com").size, end)
    }

    @Test fun ignoresResponsesAndJunk() {
        val resp = dnsQuery("x.com").also { it[2] = (it[2].toInt() or 0x80).toByte() }
        assertNull(Dns.questionName(resp))
        assertNull(Dns.questionName(ByteArray(5)))
        assertNull(Dns.questionName(dnsQuery("x.com").copyOf(16))) // truncated
    }

    @Test fun nxdomainEchoesIdAndQuestion() {
        val q = dnsQuery("reddit.com", id = 0xBEEF)
        val (_, end) = Dns.questionName(q)!!
        val r = Dns.nxdomain(q, end)
        assertEquals(0xBEEF, Packets.u16(r, 0))
        assertTrue("QR bit", r[2].toInt() and 0x80 != 0)
        assertTrue("RD kept", r[2].toInt() and 0x01 != 0)
        assertEquals("RCODE NXDOMAIN", 3, r[3].toInt() and 0x0F)
        assertEquals(1, Packets.u16(r, 4))
        assertEquals(0, Packets.u16(r, 6))
        assertArrayEquals(q.copyOfRange(12, end), r.copyOfRange(12, end))
    }

    @Test fun parsesUdpQueryAndBuildsReply() {
        val dns = dnsQuery("x.com")
        val q = Packets.parseDnsQuery(ipv4Udp(dns))
        assertNotNull(q)
        assertArrayEquals(dns, q!!.dns)

        val reply = Packets.reply(q, byteArrayOf(1, 2, 3, 4))
        assertEquals(0x45, reply[0].toInt())
        assertEquals(32, Packets.u16(reply, 2))
        assertEquals(17, reply[9].toInt())
        assertArrayEquals(byteArrayOf(10, 111, 222.toByte(), 2), reply.copyOfRange(12, 16)) // from dns
        assertArrayEquals(byteArrayOf(10, 111, 222.toByte(), 1), reply.copyOfRange(16, 20)) // to asker
        assertEquals(53, Packets.u16(reply, 20))
        assertEquals(40000, Packets.u16(reply, 22))
        assertEquals(12, Packets.u16(reply, 24))
        // IPv4 header checksum must verify to 0xFFFF when summed with itself included.
        var sum = 0
        for (i in 0 until 20 step 2) sum += Packets.u16(reply, i)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        assertEquals(0xFFFF, sum)
    }

    @Test fun rejectsNonDnsPackets() {
        val dns = dnsQuery("x.com")
        assertNull(Packets.parseDnsQuery(ipv4Udp(dns, dstPort = 443)))
        assertNull(Packets.parseDnsQuery(ipv4Udp(dns).also { it[9] = 6 })) // TCP
        assertNull(Packets.parseDnsQuery(ipv4Udp(dns).also { it[0] = 0x60 })) // IPv6
        assertNull(Packets.parseDnsQuery(ByteArray(10)))
    }

    // ---- Always-blocked apps ----

    @Test fun alwaysBlockedApps() {
        assertTrue(Apps.isAlwaysBlocked("com.reddit.frontpage"))
        assertTrue(Apps.isAlwaysBlocked("com.twitter.android"))
        assertTrue(Apps.isAlwaysBlocked("com.twitter.android.lite"))
        assertFalse(Apps.isAlwaysBlocked("com.android.chrome"))
    }
}
