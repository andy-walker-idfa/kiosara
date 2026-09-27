# Phase 4b checklist (0.6.0 / versionCode 9): optional kiosk lock

The kiosk lock is optional. Test all three states and the transitions:
**(a)** not device owner, **(b)** device owner with the lock off (normal tablet), **(c)** device owner with the lock on.
`$PKG` = `io.github.andy_walker_idfa.smarthome_dashboard`. Keep adb over Wi-Fi connected throughout.

## State (a): not device owner (after installing 0.6.0)
| # | Step | Expected result |
|---|---|---|
| 1 | Settings → Kiosk lock. | Explains that the lock needs device owner; no switch. Everything else works as in 0.5.0. |
| 2 | HA device page. | "Device owner" off; "Kiosk lock" unlocked. |

## Prerequisites and provisioning (a → b)
| # | Step | Expected result |
|---|---|---|
| 3 | `adb shell dumpsys account`, `adb shell pm list users`, `adb shell dpm list-owners` | `Accounts: 0`; only user 0; `no owners`. Report before continuing. |
| 4 | `adb shell dpm set-device-owner $PKG/.kiosk.KioskAdminReceiver` | "Success: Device owner set". Settings → Kiosk lock: "This app is device owner. Kiosk lock is off". HA "Device owner" on. |
| 5 | `adb shell dumpsys user` (Effective restrictions) | Only `no_add_managed_profile` (and possibly `no_add_clone_profile`): Android's defaults, nothing from the app. |

## State (b): device owner, lock off = normal tablet
| # | Step | Expected result |
|---|---|---|
| 6 | Press Home, open another app, pull down notifications and Quick Settings, use Recents. | All work as on a normal tablet. |
| 7 | **Escape hatches before any lock:** Settings → Open Android settings; Settings → Remove device owner permanently → Remove. | Android settings open. After removal: back to state (a), `adb shell dpm list-owners` → `no owners`. Provision again (step 4). |

## Before the first lock: confirm the way back
| # | Step | Expected result |
|---|---|---|
| 7a | Developer options → USB debugging on; connect a USB cable once, allow the PC ("Always allow"; no prompt if already allowed); `adb devices`. Developer options → Disable adb authorization timeout (or `adb shell settings put global adb_allowed_connection_time 0`). | The tablet is listed over USB; the authorization doesn't expire. Way back no. 3 is ready. (TB310FU: done 2026-09-26.) |
| 8 | *Skipped by the user (2026-09-26); the attempt landed in harmless fastboot mode.* **Recovery mode** (your hands on the tablet): power off; hold the key combination for recovery (try Power + Volume Up; release at the Lenovo logo). In recovery, choose **Reboot system now**. **Do not wipe.** | The tablet reboots normally. Tell me the combination that worked; I'll document it for the TB310FU. |
| 9 | *Skipped by the user (2026-09-26).* **Safe mode:** hold Power → press and hold "Power off" → "Reboot to safe mode". In safe mode, check Settings → Apps → Default apps → Home app is reachable (don't change it). Restart normally. | Confirms how safe mode is entered on this tablet and that the Home app can be changed there. |
| 10 | Re-enable wireless debugging after these reboots and reconnect adb. | adb works again. |

## State (c): lock on
Results on the TB310FU (2026-09-27, user): steps 11–20 passed. Step 22 (update while locked): back and locked;
the screen stayed off after a timeout, fixed in 0.7.1 and verified. Step 21 (adb PIN reset while locked) passed on the
Android 13 emulator; not repeated on the tablet.

| # | Step | Expected result |
|---|---|---|
| 11 | Settings: set a PIN (if not set). Settings → Kiosk lock → switch on. Close Settings. | Dashboard locked: no status bar, notifications, Home, Recents or other apps. HA "Kiosk lock" locked. |
| 12 | Try the Lenovo taskbar, split-screen and floating-window gestures. | Blocked. |
| 13 | Long-press the power button. | The power menu appears (restart possible). |
| 14 | 5 taps in the top-right corner → PIN. | Settings open while locked. |
| 15 | Settings → Unlock for 15 minutes; open another app; wait 15 minutes. | Everything works for 15 minutes; then the dashboard comes back by itself and is locked again. |
| 16 | Settings → Open Android settings. | Android settings open (unlocked for 15 minutes). |
| 17 | HA: press "Restart app" while locked. | The dashboard comes back and is locked again. |
| 18 | Reboot the tablet. | The dashboard starts locked. (Wireless debugging is off after the reboot: use Unlock for 15 minutes → Quick Settings tile.) |
| 19 | Settings → Kiosk lock → off. Restart the app ("Restart app" in HA) and reboot. | Stays off: state (b) after both. |
| 20 | Switch the lock on again, then Settings protection → Remove PIN → confirm. | The confirmation says the lock goes off too; state (b). |
| 21 | Set a PIN, lock on, then `adb shell am broadcast -a $PKG.action.RESET_PIN -n $PKG/.security.PinResetReceiver` | PIN removed and the lock off (state b). |
| 22 | `adb install -r` the same APK while locked. | Update succeeds; the dashboard comes back (as Home) and locks again. |

## Back to (a)
| # | Step | Expected result |
|---|---|---|
| 23 | Settings → Remove device owner permanently. | State (a): `dpm list-owners` → `no owners`; the app keeps working. Provision again if you want to keep the lock available. |

Automated:
- `.\gradlew.bat ktlintCheck testGithubDebugUnitTest testStoreDebugUnitTest lintGithubDebug lintStoreDebug` (161 tests per flavor)
- Emulator (Tablet_API33, Android 13): full cycle, see CLAUDE.md "Kiosk lock".
