package com.focushome

import android.app.Application
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class FocusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FocusStore.init(this)
        Notifications.createChannel(this)
        QrKey.clearShared(this)
        DnsFilter.lockIn(this)
        DnsFilter.start(this)
        // Sideloaded apps don't get Play services modules at install time, so fetch the scanner now, not on first scan.
        ModuleInstall.getClient(this).installModules(
            ModuleInstallRequest.newBuilder().addApi(GmsBarcodeScanning.getClient(this)).build(),
        )
    }
}
