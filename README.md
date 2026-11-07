# Focus Home

A minimal Android home-screen launcher that cuts screen time. Built for a Galaxy S25. Focus mode shows only a short list of essential apps. To switch between Focus mode and the full app list, you scan a QR code that you keep somewhere inconvenient.

## How it works

- **Focus mode**: a black, text-only screen with a large clock, the date, today's stats ("Focused 2h 14m today · 3 unlocks") and your essential apps listed by name. There are no icons, no wallpaper and no colour.
- **Unlocked**: every installed app in a searchable, text-only list. The header shows when Focus mode will come back.
- **QR key**: only unlocking needs the key. It takes a live scan in the app with **Scan to unlock**. This uses Google's code scanner, so the app needs no camera permission, and you can't type a code in by hand. To go back into Focus mode you just tap **Lock** on the unlocked screen, or wait for auto-relock.
- **Key options**: you can make a new code or pair any QR code you already have. Only a SHA-256 hash of the code's contents is stored.
  - Generated codes are plain text (`FOCUSHOME-KEY:<random token>`), not links. No other app can act on them, so a photo or screenshot of the code is useless on the phone itself.
  - A new code is handed off with **Print** (the system print dialog) or **Send to another device** (a share from private cache, which is cleared on the next launch). Nothing goes to Photos, and screenshots of the key screen are blocked (`FLAG_SECURE`).
  - The generated code's contents are kept, so you can print it again from Settings while unlocked.
  - Upgrading from 1.0: links printed by 1.0 still work with the in-app scanner. The key images 1.0 saved to `Pictures/Focus Home` are deleted the next time you unlock.
- **Auto-relock**: after you unlock, Focus mode turns itself back on after 30 minutes by default. You can choose Off, 15 min, 30 min, 1 h or 2 h in Settings.
  - The deadline runs on time since boot (`elapsedRealtime`, with an `ELAPSED_REALTIME_WAKEUP` exact alarm), so changing the clock does nothing.
  - Rebooting during an unlock counts as the time running out.
  - The alarm pulls the home screen in front of whatever app you're in and posts a notification. The home screen also re-checks the deadline every time it's shown.
- **Emergency unlock** ("Lost your key?"): a countdown you have to sit through on that screen. It's 15 minutes for the first use of the day, then 30, then 60 for every use after that, and it resets the next day. The screen stays on, and leaving it or turning the display off starts the wait over.
- **App blocking** (`FocusGuardService`, an accessibility service you turn on in setup or Settings):
  - In Focus mode, any app with a launcher icon that isn't essential is sent home the moment it opens, whether from Recents, a notification, a link or a widget. A "Not now" screen names the blocked app for 2.5 seconds.
  - It only reads the package and activity name of each new window, never window contents.
  - Always allowed: Android Settings, calls (dialer and in-call screen), alarms (Clock), Contacts, emergency, keyboards, and anything without a launcher icon (pickers, the share sheet, permission prompts, Play services).
  - In-app browser tabs (Custom Tabs) opened from an essential app are allowed.
  - Overlays and bubbles from background apps are ignored, because only real activities count.
  - **Always blocked:** Reddit (`com.reddit.frontpage`) and X (`com.twitter.android`, `com.twitter.android.lite`) are sent home in every mode, unlocked included, and whatever the essentials list says. They're also hidden from every app list in Focus Home. The list is `Apps.ALWAYS_BLOCKED` and has no setting. The websites in a browser aren't affected.
- **Web filter** (`DnsFilter.kt`, `DnsFilterService.kt`): blocks Reddit and X in every browser and app.
  - It's a local VPN that routes one made-up address (`10.111.222.2`) and makes it the phone's DNS server, so only DNS lookups go through it.
  - Lookups for `DnsFilter.BLOCKED_DOMAINS` and their subdomains get NXDOMAIN ("no such domain"). That covers reddit.com, redd.it, redditmedia.com, redditstatic.com, x.com, twitter.com, twimg.com, t.co and others, plus the common DNS-over-HTTPS providers (so a browser's "secure DNS" fails rather than going around the filter) and Firefox's `use-application-dns.net` canary.
  - All other lookups are forwarded to the real network's DNS servers, falling back to 1.1.1.1 and 8.8.8.8. The rest of the phone's traffic never touches the VPN.
  - **No off switch.**
    - It starts with the app, on boot (`BootReceiver`) and after updates.
    - `FocusGuardService` restarts it within 5 seconds if it stops.
    - If Android needs the VPN OK'd again (it was disconnected in Settings, or another VPN took over), the home screen shows only a "Turn on web filter" screen. Until the filter is back, every browser is blocked in both modes.
    - The same happens if Private DNS is set to a named provider ("strict" mode), which sends lookups around the filter.
  - **Always-on VPN** (Settings › Connections › More connection settings › VPN › ⚙ Focus Home) makes Android restart it too. Leave "Block connections without VPN" off. This VPN carries only DNS, so lockdown can cut off everything else.
  - **Device owner (optional, hard lock).**
    - Make Focus Home device owner and it pins the filter as Android's always-on VPN, sets Private DNS to Automatic, and turns on `DISALLOW_CONFIG_VPN` and `DISALLOW_CONFIG_PRIVATE_DNS`. The VPN can't be disconnected, and Focus Home can't be uninstalled or force-stopped.
    - **Android only allows this on a phone with no accounts signed in.** Remove every account in Settings › Accounts and backup (Samsung, Google, and so on), then run `adb shell dpm set-device-owner com.focushome/.FocusAdminReceiver`, then sign back in.
    - **The only way to undo it is a factory reset.**
- **Settings and the essential-apps picker** can only be reached while unlocked. If a relock happens mid-edit, you land back on the Focus screen.
- **Default essentials** are matched to whatever is installed: Phone, Messages, Camera, Gallery, Clock, Calendar and Claude.

## Building

Requires JDK 17–21 and the Android SDK (platform 35). On this machine, see the Gradle temp-dir workaround in the Swiftread/Family Planner notes.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # minified (~2.4 MB), signed with the debug key for sideloading
```

Install with `adb -d install -r app/build/outputs/apk/release/app-release.apk`, then open Focus Home and follow the setup. `FocusHome-1.2.apk` in this folder is a copy of the release build for sideloading.

## Layout

- `FocusStore.kt` holds all state (SharedPreferences plus a StateFlow): mode, key hash, essentials, relock timer, daily stats and setup progress. It also schedules the relock alarm.
- `QrKey.kt` generates tokens, renders the printable key card (ZXing), prints and shares it, wraps the scanner, and hashes codes.
- `Apps.kt` lists launchable apps, launches them, and picks the default essentials.
- `RelockReceiver.kt` contains the alarm receiver and the relock notification.
- `DnsFilter.kt` holds the web filter's blocklist, start and status helpers, the boot receiver and the device-owner lock. `DnsFilterService.kt` is the VPN, with the minimal IPv4/UDP/DNS handling.
- `FocusGuardService.kt` is the app-blocking accessibility service. It also has helpers for checking whether the service is on and for opening its Settings page.
- `ui/` holds the Compose screens: Focus/unlocked home, setup, settings, emergency unlock, and the theme.

## Limits

With app blocking on, the routes through Recents, notifications and links are closed. Android Settings is deliberately never blocked, so it remains the way out. From there you can:

- turn off the accessibility service;
- pick another home app;
- clear Focus Home's data or uninstall it;
- force-stop it, which also switches the accessibility service off.

Disconnecting the web filter from Android's VPN settings is possible without device owner. It just brings up the "Turn on web filter" screen and blocks all browsers until you turn it back on. To use a browser without the filter, you'd also have to turn off app blocking.

The web filter doesn't stop a browser with its own built-in VPN or proxy (Opera, Edge's Secure Network, Tor), sites reached by IP address, or another VPN app (which replaces the filter, so the browser block takes over).

Safe mode and adb are also outside an app's reach.

Android may mark the accessibility setting as restricted for sideloaded apps. If it does, go to App info › ⋮ › Allow restricted settings. Installs done with adb weren't restricted on the emulator.

Cold-starting a blocked app can show its splash screen for a moment before it's sent away.

During testing on the emulator, the Google scanner module couldn't download because the emulator has no Play Store. On a real phone it downloads the first time the app starts.
