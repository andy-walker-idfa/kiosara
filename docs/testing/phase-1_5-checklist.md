# Phase 1.5 manual test checklist (0.2.0, versionCode 2)

The tablet runs the **githubRelease** build, signed with the release key.

| # | Step | Expected result |
|---|---|---|
| 1 | App drawer / Home picker. | The app is called **Wall Panel** with a green "panel with tiles" icon. No "HA" in the name. |
| 2 | First start after the fresh install. | **"Not set up yet"** screen with an **Open settings** button. No page loads. |
| 3 | Tap **Open settings**, enter your HA URL, tap **Save**, close Settings. | The HA login page appears full screen. |
| 4 | Log in with **Keep me logged in**. Force-stop the app and reopen it. | The dashboard appears without a login. |
| 5 | Settings → **Device** section. | Name "Wall panel" (editable) and a 32-character device ID. |
| 6 | Change the device name, Save, close and reopen Settings. | The new name is kept. |
| 7 | Note the device ID. Force-stop, then open Settings again. | The same ID. |
| 8 | Settings → bottom line. | `Version 0.2.0 · github`. |
| 9 | Reboot the tablet. | The dashboard comes up directly, with no lock screen, still logged in. |
| 10 | Settings → clear the Start URL field, Save. | Save is refused (the Start URL field is highlighted). |
| 11 | Switch the tablet language to Czech. | "Zatím nenastaveno" / Czech settings; error screens stay English. |

Automated:
- `.\gradlew.bat ktlintCheck testGithubDebugUnitTest testStoreDebugUnitTest lintGithubDebug lintStoreDebug`
- `.\gradlew.bat connectedGithubDebugAndroidTest` (runs against the separate `.debug` app; the installed release build and its HA login are untouched)
- GitHub Actions **CI** workflow green on `main`.
