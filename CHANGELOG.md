# Changelog

## 1.6 (2026-11-07)

### Changed
- **A redesigned home screen** in warm neutral browns instead of pure black and gray. One **accent colour** (Clay, Ochre, Sage, Teal, Slate, Rose or Plum) is picked in Settings › Accent colour and used for the clock, buttons and highlights.
  - A new clock: the day and date in small accent capitals, then a large light time with an accent colon and am/pm. Your status sits below it in a small pill.
  - Then a **Claude** card, then your **Favorites**.
  - Favorites (the essentials list, renamed) show as a four-across grid of full-colour, rounded-square icons with labels. The All apps drawer uses the same icons.
  - Lock and Settings are now round icon buttons: a padlock in the accent colour and a neutral gear.
  - The Claude card is off by default; turn it on in Settings › Claude on the home screen. It lets you type a message and hand it straight to the Claude app with the send button or the keyboard's Send key. If Claude can't take shared text, the message is copied and Claude opens so you can paste it. In Focus mode the card only works if Claude is one of your favorites.
- **All apps live in a swipe-up drawer** that also opens from the **All apps** handle at the bottom. It's searchable and stars your favorites. It only opens while unlocked; in Focus mode the handle reads "Locked" and a swipe just reminds you to scan your key.

## 1.5 (2026-11-07)

### Added
- **Adult sites are blocked by the web filter**, in every browser and app and in every mode, with no setting.
  - About 48,000 domains from the porn-only list in StevenBlack/hosts (MIT licence), bundled with the app. `scripts/update-adult-blocklist.sh` refreshes it.
  - A built-in list of the biggest sites, plus the whole `.xxx`, `.porn`, `.sex` and `.adult` top-level domains.
- The "Turn on web filter" screen and Settings now say that adult sites are blocked too.

## 1.4 (2026-11-07)

### Added
- **Web filter.** A built-in DNS filter blocks Reddit and X in every browser and app, not only their own apps.
  - It runs as a local VPN that only handles DNS. Lookups for reddit.com, redd.it, x.com, twitter.com, twimg.com, t.co and their subdomains get "no such domain". Everything else goes to your network's normal DNS server, and no other traffic goes through it.
  - It also blocks the common encrypted-DNS providers, so a browser can't use one to get around it.
- **It's built in, with no off switch.**
  - The filter starts when the app starts, after a restart and after updates, and the app-blocking service restarts it within seconds if it stops.
  - If Android stops letting it run (it was disconnected in Settings, or another VPN took over), the home screen shows only a "Turn on web filter" screen until it's back on, and every browser is blocked in both modes.
  - The same happens if Private DNS is set to a named provider, which would go around the filter.
- **Optional hard lock: device owner.** If you make Focus Home device owner with adb (see the README), the filter becomes Android's always-on VPN and the VPN and Private DNS settings are locked. This can only be undone with a factory reset.

## 1.3 (2026-11-07)

### Added
- **Reddit and X are always blocked.** With app blocking on, they're sent home the moment they open, in Focus mode *and* while unlocked, even if they're on your essentials list. The "Not now" screen says the app is always blocked.
- Reddit and X (including X Lite) no longer appear in the unlocked app list or the essentials picker.
- The list is fixed in the app (`Apps.ALWAYS_BLOCKED`), so there's no setting to turn it off.

## 1.2 (2026-10-23)

### Changed
- **The Lock button replaces "Scan to focus".** On the unlocked screen, one tap turns Focus mode back on. The QR key is now only for unlocking.
- Setup wording and the caption printed on new key cards now say "Scan in Focus Home to unlock". Keys you've already printed still work.

## 1.1 (2026-10-23)

These changes close the easiest ways around Focus mode.

### Changed
- **Auto-relock ignores clock changes.** The unlock window now counts time since the phone started, so changing the clock no longer stretches it. Restarting the phone while unlocked turns Focus mode straight back on.
- **Key codes only work as a live scan in Focus Home.**
  - New codes are plain text, not links, so Gallery, Camera and other apps can't act on them. A photo or screenshot of the key can't unlock the phone it's on.
  - Scanning with the regular Camera app no longer switches Focus mode.
  - New keys are handed off with **Print** or **Send to another device**. Nothing is saved to Photos.
  - Screenshots of the key screen are blocked.
  - Settings has the same Print and Send buttons, for printing the key again while unlocked.
  - Keys printed with 1.0 still work in the app. Key images that 1.0 saved to Photos are deleted the next time you unlock.
- **The emergency unlock gets longer with use.** The wait is 15 minutes for the first use of the day, then 30, then 60, and it goes back to 15 the next day. The screen shows today's wait.

## 1.0 (2026-10-23)

First release.

### Added
- **Home screen launcher** with two modes:
  - **Focus mode:** a black, text-only screen with a big clock, the date, today's stats ("Focused 2h 14m today · 3 unlocks") and your essential apps listed by name.
  - **Unlocked:** every app, in a searchable text-only list, with when Focus mode will come back.
- **QR key.** You can make a printable code or pair any QR code you already have. Only a hash of the code is stored.
- **Essential apps.** The defaults are Phone, Messages, Camera, Gallery, Clock, Calendar and Claude. You can only change them while unlocked.
- **Auto-relock** brings Focus mode back after 30 minutes by default. You can choose Off, 15 min, 30 min, 1 h or 2 h. It pulls the home screen in front of whatever app is open and posts a notification.
- **Emergency unlock** for a lost key: a countdown you have to sit through on that screen. Leaving the screen starts it over.
- **App blocking**, an accessibility service you can turn on:
  - In Focus mode, any app that isn't essential closes as soon as it opens, even from Recents, a notification or a link.
  - A "Not now" screen names the blocked app.
  - Calls, alarms, Contacts, the keyboard, system pickers and Android Settings are never blocked.
  - Links that open as a tab inside an essential app are allowed.
- **Setup flow** that covers the key code, the essential apps, making Focus Home the home screen, app blocking and relock notifications.
