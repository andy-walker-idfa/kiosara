---
name: device-provisioning
description: Step-by-step device owner provisioning and removal for the Kiosara app via adb (optional kiosk lock), including prerequisites, the Home launcher, WebView updates, the ordered ways back, and recovery if something goes wrong.
---

# Device provisioning (TB310FU)

The kiosk lock is **optional** (CLAUDE.md "Scope decisions", Phase 4b, rules 1–9). The app is fully usable in
three states: (a) not device owner, (b) device owner with the lock off (a normal tablet), (c) device owner with the
lock on. Being device owner locks nothing by itself; the lock is a separate switch in Settings that needs a PIN.

`$PKG` = `io.github.andy_walker_idfa.smarthome_dashboard`

## Prerequisites (check and report before changing anything)
```powershell
adb shell dumpsys account | Select-String "Accounts:"   # must be "Accounts: 0"
adb shell pm list users                                 # must list only "UserInfo{0:Owner..."
adb shell dpm list-owners                               # must be "no owners"
```
- **Accounts:** any account whose authenticator doesn't allow device owners (Google and most OEM accounts) blocks
  provisioning. Remove it in Settings → Passwords & accounts (a Google Meet account may appear alongside a Google
  account). No factory reset is needed when all accounts and extra users can be removed.
- **Users/profiles:** a guest user, work profile, clone profile or Lenovo Kids Space also block it; remove them.
- Install the build that stays on the device (normally the signed **release**). A device owner can't be
  uninstalled until device owner is removed, and a different signature needs an uninstall.
- TB310FU (checked 2026-09-26): 0 accounts, only user 0, no owners. Apps that offer a sign-in (e.g. YouTube)
  showed prompts but added no account. **Provision before anyone signs in.**

## Provision (state a → b)
```powershell
adb shell dpm set-device-owner $PKG/.kiosk.KioskAdminReceiver
adb shell dpm list-owners        # → Device owner (User 0): $PKG/.kiosk.KioskAdminReceiver
```
Nothing is locked yet. Settings → Kiosk lock shows "This app is device owner. Kiosk lock is off".
Android adds its own defaults for any device owner (no work profile, no clone profile); the app sets **no**
restrictions: never DISALLOW_FACTORY_RESET, DISALLOW_SAFE_BOOT, DISALLOW_DEBUGGING_FEATURES, USB or account limits.

### Re-adding a Google account after provisioning (WebView updates) — recommended step
The user re-adds their account after provisioning (decision 2026-09-26), via "Unlock for 15 minutes".
Allowed once the app is device owner (the app doesn't restrict accounts). Add the account in Android settings (with
the kiosk lock off, or during "Unlock for 15 minutes"), update "Android System WebView" in the Play Store, then
remove the account again if you want. **While a Google account is on the tablet, a factory reset asks for its
password afterwards (Factory Reset Protection).** Provisioning again later needs all accounts removed.

## Lock on (state b → c)
Only in the app: Settings → Settings protection → set a PIN; Settings → Kiosk lock → switch on. The dashboard locks
when it is shown (status bar, notifications, Home, Recents blocked; power-button menu available). Home Assistant has
no command to switch it off (only a "Kiosk lock" state sensor).

## Wireless debugging while locked (decision: no in-app button)
Android switches wireless debugging off at every reboot and Wi-Fi change, and the lock hides Quick Settings.
1. On the tablet: 5 taps top-right → PIN → Settings → Kiosk lock → **Unlock for 15 minutes**.
2. Swipe down (twice) → Quick Settings tile **Wireless debugging** (tile added once under Developer options →
   Quick settings developer tiles).
3. PC: `.\scripts\adb-wifi.ps1` (mDNS finds the new port). Dashboard re-locks after 15 min or with **Lock now**.
4. Fallback: USB cable (USB debugging on and the PC authorized beforehand).
Device owner can't switch it on (`setGlobalSetting` allows only `ADB_ENABLED` = USB); `WRITE_SECURE_SETTINGS`
would, but was rejected as too broad for a developer convenience (CLAUDE.md "Scope decisions").

## Ways back, in this order
1. **PIN → Settings → an escape hatch**: "Unlock for 15 minutes" (locks again by itself), "Open Android settings"
   (unlocks for 15 minutes), switch the lock off (persists), or "Remove device owner permanently".
2. **Forgotten PIN, adb over Wi-Fi:** `adb shell am broadcast -a $PKG.action.RESET_PIN -n $PKG/.security.PinResetReceiver`
   (removing the PIN also switches the lock off).
3. **adb over USB cable:** works even after a reboot with wireless debugging off (debugging is never restricted),
   **if USB debugging was switched on and the PC authorized before the lock was enabled** (checklist step), and
   the authorization timeout is disabled (`settings put global adb_allowed_connection_time 0`, or Developer options
   → Disable adb authorization timeout); by default Android forgets a PC after 7 days without a USB connection.
   TB310FU: done 2026-09-26 (USB authorized, timeout disabled).
   Take the tablet off the wall, connect USB, then the same PIN reset command.
4. **Safe mode:** the app is disabled there, so the PIN reset can't run in safe mode itself. In safe mode choose
   the stock launcher as Home app (Settings → Apps → Default apps → Home app; optionally switch on USB debugging),
   restart normally: the dashboard doesn't start, Quick Settings works → wireless debugging → step 2 → choose the
   app as Home again. TB310FU: long-press **Power off** in the power menu → "Reboot to safe mode".
   (`adb shell am task lock stop` is not a way back: the dashboard locks again as soon as it is shown.)
5. **Last resort: factory reset from recovery mode.** TB310FU key combination **not verified** (the user skipped
   the check; an attempt landed in fastboot mode, harmless: Power + Volume Down until black, then Power). Refer to
   Lenovo's support instructions. Wipes everything, including the HA login; WebView rolls back to 126; with
   a Google account on the tablet, its password is needed afterwards (FRP).

Note: with device owner, Android protects the app: "Clear storage", `adb uninstall`, `am force-stop` and
`am crash` are refused (verified on the Android 13 emulator for force-stop and crash). Remove device owner first.

## Remove device owner (state b/c → a)
In the app: Settings → Kiosk lock → **Remove device owner permanently** (stops the lock first, then
`clearDeviceOwnerApp`). `adb shell dpm remove-active-admin` does **not** work for a non-test app. Verify with
`adb shell dpm list-owners` → `no owners`.

## Home launcher (no device owner needed)
```powershell
adb shell cmd role add-role-holder android.app.role.HOME $PKG     # make it the default Home app
adb shell cmd role get-role-holders android.app.role.HOME         # verify
```
To undo: `adb shell cmd role add-role-holder android.app.role.HOME com.tblenovo.launcher` (TB310FU stock launcher).

## Debug builds
The `.debug` package can be installed next to a device-owner release build but can never become device owner or
lock. Instrumentation tests can't start activities while the release build is locked: unlock first.

## WebView updates
There's no Google account, so WebView does not auto-update (153.0.8010.36, updated 2026-09-24).
- **Option A:** temporarily add a Google account (see above), update "Android System WebView" in Play, remove the
  account again.
- **Option B:** sideload a Google-signed WebView APK (`adb install -r <apk>`); Android accepts it only if the
  signature matches.

Check with `adb shell dumpsys webviewupdate | Select-String "Current WebView"`.
