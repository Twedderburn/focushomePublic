package com.focushome

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.SystemClock
import android.os.UserManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The web filter: a local VPN ([DnsFilterService]) that carries only DNS. Lookups for blocked domains get
 * "no such domain"; everything else is passed to the network's normal DNS server. No other traffic goes through it.
 */
object DnsFilter {
    /** Each name also blocks every subdomain (www.reddit.com, old.reddit.com, mobile.x.com, ...). */
    val BLOCKED_DOMAINS = setOf(
        // Reddit
        "reddit.com", "redd.it", "redditmedia.com", "redditstatic.com", "reddituploads.com", "redditinc.com",
        "reddit.app.link", "redditspace.com",
        // X / Twitter
        "x.com", "twitter.com", "twimg.com", "t.co", "twttr.com", "twitpic.com", "pscp.tv", "periscope.tv",
        // Encrypted DNS providers. Browsers set to use them would skip the filter; blocked, their lookups just fail.
        "cloudflare-dns.com", "one.one.one.one", "dns.google", "dns.quad9.net", "doh.opendns.com", "dns.nextdns.io",
        "doh.cleanbrowsing.org", "dns.adguard.com", "dns.adguard-dns.com", "doh.dns.sb", "dns.mullvad.net",
        // Firefox's canary: "no such domain" here tells it not to switch on its own encrypted DNS.
        "use-application-dns.net",
    )

    fun isBlocked(name: String): Boolean {
        var n = name.trimEnd('.').lowercase()
        while (n.isNotEmpty()) {
            if (n in BLOCKED_DOMAINS) return true
            n = n.substringAfter('.', "")
        }
        return false
    }

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    internal fun setRunning(on: Boolean) {
        _running.value = on
    }

    /** False when Android needs the user to OK the VPN again (first run, or after it was disconnected in Settings). */
    fun hasConsent(context: Context): Boolean = try {
        VpnService.prepare(context) == null
    } catch (_: Exception) {
        false
    }

    /** Starts the filter if Android allows it. Safe to call often. */
    fun start(context: Context) {
        if (_running.value || !hasConsent(context)) return
        try {
            context.startService(Intent(context, DnsFilterService::class.java))
        } catch (_: Exception) {
            // Android refused a start from the background; the app-blocking service or the home screen will retry.
        }
    }

    /**
     * Private DNS set to a named provider ("strict" mode) sends lookups straight to that provider over TLS,
     * around the filter, so it counts as the filter being off.
     */
    fun privateDnsBypass(context: Context): Boolean = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        cm.allNetworks.any { cm.getLinkProperties(it)?.privateDnsServerName != null }
    } catch (_: Exception) {
        false
    }

    private var checkedAt = 0L
    private var bypassCached = false

    /** Whether lookups really are being filtered right now. Re-checks Private DNS at most every 5 seconds. */
    fun isProtecting(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - checkedAt > 5_000 || checkedAt == 0L) {
            checkedAt = now
            bypassCached = privateDnsBypass(context)
        }
        return _running.value && !bypassCached
    }

    fun isDeviceOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)?.isDeviceOwnerApp(context.packageName) == true

    /**
     * Only when Focus Home has been made device owner (an adb step, see the README): pins the filter as Android's
     * always-on VPN and takes VPN and Private DNS settings away from the user, so the filter can't be switched off.
     * "Block connections without VPN" stays off: this VPN carries only DNS, so lockdown would cut all other traffic.
     */
    fun lockIn(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, FocusAdminReceiver::class.java)
        runCatching { dpm.setAlwaysOnVpnPackage(admin, context.packageName, false) }
        runCatching { dpm.setGlobalPrivateDnsModeOpportunistic(admin) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS) }
    }

    /** DNS servers of the real (non-VPN) networks, then public fallbacks. */
    internal fun upstreams(context: Context): List<java.net.InetAddress> {
        val found = try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            cm.allNetworks
                .filter { n ->
                    cm.getNetworkCapabilities(n)?.let {
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    } == true
                }
                .flatMap { cm.getLinkProperties(it)?.dnsServers.orEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
        return (found + FALLBACK).distinct()
    }

    private val FALLBACK = listOf("1.1.1.1", "8.8.8.8").map { java.net.InetAddress.getByName(it) }
}

/** Starts the filter after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        DnsFilter.lockIn(context)
        DnsFilter.start(context)
    }
}

/** Does nothing unless Focus Home is made device owner with adb; then it locks the filter in. */
class FocusAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        DnsFilter.lockIn(context)
        DnsFilter.start(context)
    }
}
