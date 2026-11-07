package com.focushome.ui

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focushome.DnsFilter

/**
 * Stands in front of the home screen whenever the web filter can't run: Android needs the VPN OK'd again,
 * or Private DNS is set to a provider that would go around it. Nothing else on the home screen works until it's fixed.
 */
@Composable
fun WebFilterGate(needsConsent: Boolean, privateDns: Boolean, onChanged: () -> Unit) {
    val context = LocalContext.current
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        DnsFilter.start(context)
        onChanged()
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onChanged() }

    Page {
        Spacer(Modifier.height(72.dp))
        Heading("Web filter is off")
        Spacer(Modifier.height(12.dp))
        if (needsConsent) {
            Body(
                "Focus Home's web filter blocks Reddit and X in every browser. Android needs you to OK it " +
                    "as a VPN. It only handles website name lookups. Your other traffic never goes through it.",
            )
            Spacer(Modifier.height(10.dp))
            Body("Browsers stay blocked until it's back on.", color = Faint)
            Spacer(Modifier.weight(1f))
            Action("Turn on web filter", primary = true) {
                val intent = VpnService.prepare(context)
                if (intent == null) {
                    DnsFilter.start(context)
                    onChanged()
                } else {
                    consent.launch(intent)
                }
            }
        } else if (privateDns) {
            Body(
                "Private DNS is set to a named provider, which sends lookups around the filter. " +
                    "Set Private DNS to Automatic or Off.",
            )
            Spacer(Modifier.height(10.dp))
            Body("Samsung: Settings › Connections › More connection settings › Private DNS.", color = Faint)
            Spacer(Modifier.weight(1f))
            Action("Open Settings", primary = true) { settings.launch(privateDnsIntent(context)) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private fun privateDnsIntent(context: Context): Intent {
    val direct = Intent("android.settings.PRIVATE_DNS_SETTINGS")
    return if (direct.resolveActivity(context.packageManager) != null) direct else Intent(Settings.ACTION_WIRELESS_SETTINGS)
}

/** The Settings section: status, plus how to make Android restart the filter by itself. */
@Composable
fun WebFilterSection(running: Boolean, deviceOwner: Boolean) {
    val context = LocalContext.current
    Body(
        if (running) "✓  On. Reddit and X can't load in any browser or app." else "Starting…",
        color = if (running) Ink else Dim,
    )
    Spacer(Modifier.height(8.dp))
    if (deviceOwner) {
        Body("Locked: Focus Home is device owner, so the filter is Android's always-on VPN and its settings are locked.", color = Faint)
        return
    }
    Body(
        "There's no switch for it here. If it's disconnected, Focus Home starts it again, and browsers are blocked while it's down. " +
            "To have Android keep it on too, turn on Always-on VPN for Focus Home. Leave \"Block connections without VPN\" off, " +
            "or nothing but DNS gets through.",
        color = Faint,
    )
    Spacer(Modifier.height(12.dp))
    Action("Open VPN settings") { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
}
