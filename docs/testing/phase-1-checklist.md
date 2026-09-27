# Phase 1 manual test checklist

Install the **debug** build (it has the red escape-hatch section in Settings). *Since 0.2.0 the tablet runs the githubRelease build; use `app-github-debug.apk` for the debug-only steps 14 and 19.*
`adb install -r app\build\outputs\apk\github\debug\app-github-debug.apk` (installs as a separate app, `…smarthome_dashboard.debug`, next to the release build)

| # | Step | Expected result |
|---|---|---|
| 1 | Launch Kiosara. | Full screen, landscape, no status or navigation bar. HA login page or dashboard. |
| 2 | Log in to HA with **Keep me logged in** ticked. | The dashboard appears. |
| 3 | `adb shell am force-stop io.github.andy_walker_idfa.smarthome_dashboard`, then open the app again. | The dashboard appears **without** a login. |
| 4 | Reboot the tablet and swipe the lock screen. | The dashboard appears on its own (as the Home app), still logged in. |
| 5 | Swipe from the top or bottom edge. | The bars appear briefly, then hide again. |
| 6 | Press Back on the dashboard. | Navigates back inside HA, or does nothing. The app never closes. |
| 7 | Hold a finger in the **top-left corner** for 3 s. | Settings opens. |
| 8 | In Settings, set text zoom to 120 and Save. Close. | The dashboard text is larger. Set it back to 100. |
| 9 | Settings → Additional dashboards: add `Test` = `http://<your HA host>:1/` (port 1 is always refused), tap **Show**, close. | The "Can't reach Home Assistant" screen with a countdown. Retries at 2 s, 4 s, 8 s… |
| 10 | While on the error screen, tap anywhere except the button. | Nothing happens underneath (touches are swallowed). |
| 11 | Settings → **Go to start URL**. Remove `Test`. | Back on the dashboard. |
| 12 | Turn Wi-Fi off for ~1 minute, then on. | The dashboard reloads once by itself within a few seconds of Wi-Fi returning. |
| 13 | Turn Wi-Fi off for ~5 s, then on. | No reload; HA reconnects by itself. |
| 14 | Settings → debug section → **Simulate renderer crash**. | The dashboard reloads within a few seconds. The app does not close. |
| 15 | Settings → **Clear web cache**. | The dashboard reloads and you stay logged in. |
| 16 | Settings → **Reload every N minutes** = 1, Save, wait 2 minutes. Then set it back to 0. | The page reloads about once a minute. |
| 17 | Settings → **Return to start URL after N idle minutes** = 1. Open a different HA view, wait 1 minute without touching. | Back on the start URL. Set it back to 0. |
| 18 | Switch the tablet language to Czech (Android settings). | Settings are in Czech; the error screen stays in English. |
| 19 | Debug section → **Choose Home app**. | The system Home picker opens (the escape hatch works). |

Automated tests:
- `.\gradlew.bat testGithubDebugUnitTest testStoreDebugUnitTest`
- `.\gradlew.bat connectedGithubDebugAndroidTest` (8 on-device tests)
