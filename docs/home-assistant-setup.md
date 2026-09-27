# Home Assistant setup

This guide covers everything you need to prepare in Home Assistant (HA) before connecting Kiosara. It assumes a working Home Assistant installation on your local network.

> This project is not affiliated with or endorsed by Home Assistant or the Open Home Foundation.

> **Status:** this guide describes app version 0.9.0, a beta (in active testing) (MQTT integration, battery charge management, brightness, night mode, recovery, settings PIN, the optional kiosk lock and log files).

## Overview

The app needs three things from Home Assistant:

1. **A dashboard to display**, and a user account to log in with.
2. **An MQTT broker**, which the app uses to report its status (battery, network, system, dashboard) and receive commands. Home Assistant creates the app's device and entities automatically via MQTT discovery; no YAML is needed.
3. **A switchable smart plug for the charger** (recommended). Home Assistant uses it to keep the tablet's battery between safe charge levels instead of at 100% all the time, which greatly extends battery life and reduces the risk of battery swelling.

## 1. Network preparation

Give both the Home Assistant server and the tablet a **fixed IP address**, preferably through a DHCP reservation in your router. The app connects to both HA and the MQTT broker by address, and a changing IP would break the connection.

Everything described here works on your local network only. **Do not expose the MQTT broker to the internet.**

## 2. MQTT broker

### Home Assistant OS or Supervised

1. Go to **Settings → Add-ons → Add-on Store** and install **Mosquitto broker**.
2. Start it and enable **Start on boot** and **Watchdog**.
3. Go to **Settings → Devices & Services**. Home Assistant usually discovers the broker automatically and offers to set up the **MQTT** integration; confirm it. If it doesn't appear, add the MQTT integration manually and point it at the broker.
4. In the MQTT integration's options, make sure **discovery is enabled** (the default).

### Home Assistant Container or Core

These installation types have no add-ons. Run Mosquitto (or any other MQTT broker) separately, for example as a Docker container, then add the **MQTT** integration in Home Assistant and point it at your broker. Create a broker user for the app as described in your broker's documentation.

## 3. User accounts

Create two separate users under **Settings → People → Users** (enable *Advanced mode* in your user profile if the Users tab isn't visible). Keeping them separate means that if the tablet's dashboard login is ever exposed, it can't be used to change your MQTT setup, and vice versa.

### Dashboard user (for logging in on the tablet)

- A name such as `wall-panel`.
- **Administrator: off.** The panel only needs to view the dashboard and switch devices.
- **Local access only: on.** The account can then only log in from your home network.

You'll log in with this account on the tablet once. Tick **Keep me logged in**; the app preserves the login across restarts and reboots.

### MQTT user (for the app's MQTT connection)

- A name such as `wallpanel_mqtt` and a strong password.
- Administrator off, local access only on.
- With the Mosquitto add-on, any Home Assistant user can log in to the broker, so no further configuration is needed. The usernames `homeassistant` and `addons` are reserved and can't be used.
- **Optional hardening (ACL):** by default every broker user may read and write every topic, including Home Assistant's discovery topics. The Mosquitto add-on supports an access control list (see the add-on's documentation, `customize` → `acl_file`). A rule set for the tablet's user limits it to its own topics:

  ```
  user <tablet MQTT user>
  topic readwrite shdash/#
  topic write homeassistant/device/+/config
  topic read homeassistant/status
  ```

  The app always uses Home Assistant's default discovery prefix (`homeassistant`) and the base topic `shdash/<id>`. Home Assistant's own user needs full access.

You'll enter these credentials in the app's MQTT settings. They are stored encrypted on the tablet and never leave your network.

## 4. The dashboard

**Example:** [`homeassistant/examples/dashboard.yaml`](../homeassistant/examples/dashboard.yaml) is a complete
landscape panel (clock, weather, solar and battery power flow, open doors) with the dark theme
[`homeassistant/themes/kiosara.yaml`](../homeassistant/themes/kiosara.yaml). The comments at the top list the
placeholders to replace and the HACS cards it needs (button-card, power-flow-card-plus, kiosk-mode).

Any Home Assistant dashboard works. For the best result on a wall-mounted tablet:

- **Create a dedicated dashboard** (**Settings → Dashboards → Add dashboard**) designed for the tablet's resolution and orientation, with large, easy-to-hit controls.
- **Hide the header and sidebar** with the `kiosk-mode` frontend plugin (installable via HACS). Add `?kiosk` to the dashboard URL you enter in the app, e.g. `http://<HA address>:8123/wall-panel/0?kiosk`.
- **Use a dark theme.** It is easier on the eyes at night and looks better on a wall.
- A typical layout: clock and weather at the top, home status (doors, windows, alarm, temperatures, who's home) in the middle, and a few large toggles for lights or devices at the bottom.

On first start the app shows **"Not set up yet"**: tap **Open settings**, enter the dashboard URL under **Dashboard → Home Assistant URL** and tap **Save** (top right).

## 5. Charger smart plug (recommended)

### Requirements

- The plug must be **controllable locally** by Home Assistant, e.g. via Zigbee (ZHA or Zigbee2MQTT), Z-Wave, Matter, or Wi-Fi plugs with local firmware or a local integration (Shelly, Tasmota, ESPHome). Avoid cloud-only plugs: if the cloud or internet is down, charging control stops.
- It must appear in Home Assistant as a **switch entity** (e.g. `switch.living_room_plug`).

### Set the power-on behavior to "On"

If Home Assistant or the plug's controller is ever unavailable after a power outage, the plug should power on by itself, so the tablet keeps charging rather than running flat. Where to find the setting:

| Plug type | Setting |
|---|---|
| Zigbee2MQTT | Device page → *Exposes* → `power_on_behavior` → **on** |
| ZHA | Device page → *Configuration* → "Start-up on/off" (or similar) → **On** |
| Shelly | Device web interface → Relay settings → "Power on default mode" → **On** |
| Tasmota | Console command `PowerOnState 1` |
| ESPHome | `restore_mode: ALWAYS_ON` in the switch configuration |

If your plug only offers "previous state", choose that and keep the plug switched on whenever charging isn't being managed.

### Test

1. Toggle the plug from Home Assistant and check that it responds immediately.
2. Unplug it from the wall socket and plug it back in: it should come back **on**.

## 6. Connecting the app

1. In the app, open the settings: tap the **top-right corner** of the screen **5 times** within 4 seconds (or tap **Open settings** on the "Not set up yet" screen). From the 2nd tap, small dots in the corner show how many taps counted; a tap that misses the corner is simply ignored. If you set a PIN, it is asked first. The taps also reach your dashboard, so keep that corner free of buttons. Settings are grouped by importance: **Dashboard** (required: the Home Assistant URL, plus the text size), **Home Assistant integration (MQTT)**, **Screen**, **Settings protection** and **Kiosk lock**, followed by **Reload dashboard** and **Logs**. Everything else has fixed, sensible defaults. Fields marked with `*` are required; every field has a short explanation below it.
2. In **Home Assistant integration (MQTT)**:
   - Switch on **Connect to Home Assistant**. Android asks whether the app may show notifications. The app works either way; allowing it shows a small, silent "Connected to your Home Assistant" notification that Android requires for background services.
   - **Broker address \***: your Home Assistant's address (the Mosquitto add-on runs on the HA host), e.g. `192.168.1.10`. If your broker doesn't use port 1883, add the port: `192.168.1.10:1884`.
   - **Username \*** and **Password \***: the MQTT user from step 3 (not the dashboard user). Tick **Show password** to check what you typed. The password is stored encrypted and never shown again; leave the field empty later to keep it.
   - Tap **Test connection**. It tries the values without saving them and reports success or the exact problem (host not found, connection refused, wrong username or password, timeout).
   - Tap **Save** at the top of the screen. The status line under the title changes to **MQTT: connected** within a few seconds. If you close Settings with unsaved changes, the app asks whether to discard them.
3. **Battery optimization:** if the section shows "Optimized", tap **Change** and allow the app to run unrestricted. Otherwise Android may pause the connection while the screen is off. (The notice disappears once the app is allowed.) Some manufacturers add their own, more aggressive battery management on top; [dontkillmyapp.com](https://dontkillmyapp.com) explains how to switch it off for your tablet's brand.
4. A new device named **Wall panel** appears under **Settings → Devices & Services → MQTT**, with entity IDs such as `sensor.wall_panel_battery`. To give it another name (e.g. with several panels), rename the device in Home Assistant; it offers to rename the entity IDs as well.

### Entities

| Entity | Type | Notes |
|---|---|---|
| Battery | sensor (%) | Used by the charging blueprint |
| Battery temperature | sensor (°C) | Used by the charging blueprint (overheat protection) |
| Night mode | switch | On at night. Switching it overrides the app's night schedule until the next start or end time. Ignored while Night mode is off in the app |
| Screen brightness | number (0–100 %) | Brightness of the normal screen. Setting it switches the app to a fixed level (kept after restarts) |
| Reload | button | Reloads the dashboard page |

That's deliberately all: the app is a home appliance, and every entity has to serve showing the dashboard, night mode, or the battery. (Debug builds of the app publish many more diagnostic entities, as a separate device, for development.) Panels updated from an older version remove their other entities from Home Assistant automatically.

When the tablet goes offline (power, Wi-Fi, app stopped), all entities become **unavailable** within about a minute. When Home Assistant restarts, the app re-publishes everything automatically.

## 7. Battery charge management

The repository contains a **blueprint**, `homeassistant/blueprints/automation/shdash/battery_charge_management.yaml`, that keeps the battery between two levels by switching the charger plug.

### What it does

- Switches the plug **on at or below 40 %** and **off at or above 80 %** (both adjustable). It waits at least 2 minutes after the plug last changed, so it never toggles rapidly.
- **Overheat protection:** at or above the maximum temperature (default **40 °C**), the plug is switched off, an **overheat lock** is switched on and you are notified. Charging never happens at or above that temperature. The lock clears by itself once the battery cools to 35 °C (maximum minus 5 °C, adjustable), or when you switch it off yourself.
- **Emergency charging:** if the battery drops to **15 %** (adjustable) while the overheat lock is on and the temperature is below the maximum, it charges anyway, up to 25 %, and notifies you. A long hot period therefore can't run the tablet flat.
- **Fail-safe:** if the tablet is offline for more than 30 minutes (adjustable), the plug is switched on, so the tablet can't run flat, but never while the overheat lock is on.
- **Low-battery warning** below 20 % (adjustable), e.g. if the plug stopped working.
- Everything is re-checked when Home Assistant starts and every 5 minutes, so nothing is missed after a restart.

### Setup

1. **Create the overheat lock helper:** **Settings → Devices & services → Helpers → Create helper → Toggle**, name it e.g. "Wall panel overheat lock". The blueprint needs it to remember an overheat across Home Assistant restarts and while the tablet is offline.
2. **Import the blueprint** with this button (it opens your Home Assistant and pre-fills the address):

   [![Open your Home Assistant instance and show the blueprint import dialog with a specific blueprint pre-filled.](https://my.home-assistant.io/badges/blueprint_import.svg)](https://my.home-assistant.io/redirect/blueprint_import/?blueprint_url=https%3A%2F%2Fgithub.com%2Fandy-walker-idfa%2Fkiosara%2Fblob%2Fmain%2Fhomeassistant%2Fblueprints%2Fautomation%2Fshdash%2Fbattery_charge_management.yaml)

   Or manually: **Settings → Automations & scenes → Blueprints → Import blueprint** and paste
   `https://github.com/andy-walker-idfa/kiosara/blob/main/homeassistant/blueprints/automation/shdash/battery_charge_management.yaml`.
   It appears as **"Kiosara: battery charge guard"**.
3. **Create the automation:** on the blueprint, choose **Create automation** and select:
   - **Battery level sensor** and **Battery temperature sensor**: the tablet's "Battery" and "Battery temperature" entities.
   - **Charger plug**: your plug's switch entity. There is no default; pick the plug that powers the tablet's charger.
   - **Overheat lock**: the helper from step 1.
   - Optionally adjust the thresholds and set a **Notify action** such as `notify.mobile_app_your_phone` for phone notifications. Without it, notifications appear only in Home Assistant.
4. Make sure the plug's **power-on behavior is "On"** (section 5).

### Status card

`homeassistant/lovelace/tablet_status_card.yaml` is an example card showing battery, temperature, charging, the charger plug, page errors and quick actions. Paste it in a dashboard via **Edit dashboard → Add card → Manual**, then replace the placeholder plug and helper entity IDs with yours.

## 8. Screen and night mode

The app's **Screen** settings control the display:

- **Brightness:** *Follow Android* (default; this includes Android's own adaptive brightness if it is switched on in Android's display settings) or *Fixed*. Setting the brightness from Home Assistant switches to *Fixed*.
- **Night mode** (off by default), **from** and **until** (default 22:00–06:30, local time):
  - at night the screen is **black** (lowest backlight) and the dashboard is paused, which saves power;
  - a touch shows the dashboard for **one minute**; that touch is not passed on to the dashboard, so nothing gets switched by accident;
  - when night starts, the dashboard **goes back to its start page**, so in the morning you see the main view whatever was open in the evening;
  - Home Assistant's *Night mode* switch overrides the schedule until the next start or end time (e.g. switch it off for a late party);
  - after a longer night, the app checks that the dashboard reconnected to Home Assistant when the screen wakes, and reloads it if it hasn't within 10 seconds.

By day the screen stays on with the dashboard.

## 9. Unattended operation and settings PIN

**Recovery.** The app watches itself so the panel keeps working without anyone noticing:
- If the dashboard page freezes (no answer for 10 seconds, checked every minute) or doesn't react to touch for 15 seconds, the page is restarted and reloaded.
- If the Home Assistant frontend stays disconnected (two checks 5 minutes apart), the page is reloaded.
- If the app crashes or freezes while the dashboard is on screen, it restarts itself. After 5 restarts within 30 minutes it stops trying and Android's normal handling takes over.

All of this happens silently; the app's log (below) records it.

**Logs.** Settings → **Logs** → **View logs** shows what the app did recently: connections, reloads, watchdog actions, restarts, kiosk lock and screen changes, newest first, with filters for warnings and errors. The log stays on the tablet (two files of up to 1 MB); sensitive parts such as passwords or login codes in addresses are masked, and error messages are never written.

**Settings PIN (optional).** In Settings → **Settings protection**, set a 4–8 digit PIN; **6 or more digits are recommended**. After 5 wrong PINs, entry is blocked for 30 seconds. The PIN is a deterrent for guests and children, not a lock: the app never locks you out. With only 4 digits, a very patient child could try every combination in a few hours; with 6 digits that takes weeks. The PIN also protects **Trust this certificate** on the error screen.
- If the saved PIN can't be read (rare, e.g. after a system change), Settings opens without it and asks you to set a new one.
- While the app is device owner (kiosk lock, section 10), Android doesn't allow clearing the app's storage; use the adb command, or the ways back in section 10.
- **Forgot the PIN?** With adb connected, run:
  ```
  adb shell am broadcast -a io.github.andy_walker_idfa.smarthome_dashboard.action.RESET_PIN -n io.github.andy_walker_idfa.smarthome_dashboard/.security.PinResetReceiver
  ```
  (only adb can send this; other apps on the tablet can't). Without adb: Android **Settings → Apps → the app → Storage → Clear storage**. This also clears the Home Assistant login and all app settings, so you set it up again.

## 10. Kiosk lock (optional)

The kiosk lock keeps the dashboard in front: no status bar, notifications, Home button, recent apps or other apps (for example, so children can't switch to YouTube). The power-button menu keeps working, so the tablet can always be restarted. **It is entirely optional.** Without it everything else works as usual.

**What it does not do:** it keeps the *app* in front, but it does not restrict which web pages the dashboard itself can show. Links on your dashboard (and in Home Assistant, e.g. its About page or documentation links) can lead to other websites, and from there anywhere. Keeping such links off the panel is up to you: use a dedicated dashboard without external links, and log the tablet into Home Assistant as a non-admin user (see "Security notes"). The panel returns to its start page when night starts, and **Reload** in Home Assistant loads the current page again.

It needs two steps, and neither locks anything by itself:

**Step 1: make the app "device owner"** (once, from a PC with adb; no factory reset needed):
1. Remove all accounts from the tablet (Android Settings → Passwords & accounts). Only the owner user may exist (no guest user, work profile or kids space). Check with `adb shell dumpsys account` (must show `Accounts: 0`) and `adb shell pm list users`.
2. Run:
   ```
   adb shell dpm set-device-owner io.github.andy_walker_idfa.smarthome_dashboard/.kiosk.KioskAdminReceiver
   ```
3. The app's Settings → **Kiosk lock** now says "This app is device owner. Kiosk lock is off". The tablet still behaves like a normal tablet. Accounts may be added again now (for example, a Google account to update Android System WebView in the Play Store). A Google account on the tablet means a factory reset later asks for its password (see below).

**Recommended after Step 1: add your Google account again** (so Android System WebView, which shows the dashboard, keeps getting updates from the Play Store; without an account it never updates):
1. With the kiosk lock off, or during **Unlock for 15 minutes**: Android Settings → Passwords & accounts → Add account → Google, and sign in.
2. Open the Play Store → your profile → Manage apps & device → update **Android System WebView** (and let it update automatically from now on).
3. Keep in mind:
   - **Factory Reset Protection:** while a Google account is on the tablet, a factory reset asks for its password afterwards.
   - **Setting up device owner again** later (after "Remove device owner permanently") needs all accounts removed first.
   - Apps on the tablet such as YouTube and the Play Store can use this account when the lock is off or unlocked. With the kiosk lock on, nobody can reach them.
   - Google Play Protect may ask about the sideloaded app; allow it.

**Before Step 2, prepare the way back:** in Android's Developer options switch on **USB debugging** as well, connect the tablet to your PC once with a USB cable and allow the PC ("Always allow"), then check `adb devices`. Also switch on **Disable adb authorization timeout** (Developer options); otherwise Android forgets the PC after 7 days without a USB connection. This makes way back no. 3 below possible even if wireless debugging is off.

**Step 2: switch the lock on** in Settings → Kiosk lock. It needs a PIN (Settings protection), so nobody can get into Settings without it. The lock stays on after restarts, and stays off once you switch it off. Home Assistant can't switch it off.

**Switching on wireless debugging while the lock is on** (Android switches it off at every restart and when the Wi-Fi changes; only needed if you use adb):
1. Tap the top-right corner 5 times, enter the PIN, then Settings → Kiosk lock → **Unlock for 15 minutes**.
2. Swipe down from the top edge (twice) to open Quick Settings and tap the **Wireless debugging** tile. (One-time setup: Developer options → Quick settings developer tiles → Wireless debugging.)
3. On the PC, connect (`adb connect`, or the repository's `scripts\adb-wifi.ps1`). Go back to the dashboard; it locks again after 15 minutes, or at once with **Lock now** in Settings.
4. If that isn't possible: connect a USB cable (USB debugging must have been switched on and the PC allowed beforehand, see "Before Step 2").

**Escape hatches** (in Settings, behind the PIN):
- **Unlock for 15 minutes**: everything works normally for 15 minutes, then the dashboard comes back and locks again by itself (**Lock now** ends it early). A restart of the app also ends the unlock. Use this, for example, to switch on wireless debugging from Quick Settings after a reboot.
- **Open Android settings**: unlocks for 15 minutes and opens Android's settings. Because the app is the Home app, there is no home screen with app icons: to start another app during the unlock (e.g. YouTube), use Android Settings → Apps → the app → **Open**, or Recents if it was used recently. When the 15 minutes are over, that app is closed and the dashboard is locked again. While locked, Android screens such as permissions, battery or Wi-Fi can't open.
- **Remove device owner permanently**: back to a normal tablet (the app stays installed). Setting it up again needs Step 1 again.

While the app is device owner, Android doesn't allow uninstalling it or clearing its storage. Remove device owner first.

**Ways back, if you are stuck** (in this order):
1. **Your PIN** → Settings → an escape hatch above.
2. **Forgot the PIN, adb over Wi-Fi works:**
   ```
   adb shell am broadcast -a io.github.andy_walker_idfa.smarthome_dashboard.action.RESET_PIN -n io.github.andy_walker_idfa.smarthome_dashboard/.security.PinResetReceiver
   ```
   This removes the PIN and switches the kiosk lock off.
3. **adb over a USB cable**: works even when wireless debugging is off after a reboot (the app never restricts debugging), provided USB debugging was switched on and your PC allowed beforehand (see "Before Step 2"). Connect the tablet and run the same command.
4. **Safe mode**: Android starts without downloaded apps, so the lock isn't active, but the app can't be reset there either. Use it to stop the dashboard from starting: in safe mode open Android **Settings → Apps → Default apps → Home app** and choose the Lenovo launcher (you can also switch on USB debugging there). Restart normally: the dashboard no longer starts by itself, so Quick Settings works; switch on wireless debugging and use way back no. 2. Afterwards choose the app as Home app again. On the Lenovo Tab M9, safe mode is entered by pressing and holding the power button, then pressing and holding **Power off** until "Reboot to safe mode" appears.
5. **Last resort: factory reset from recovery mode.** The key combination is not verified on the Lenovo Tab M9 (TB310FU); follow Lenovo's support instructions for your model. **Careful:** Power + Volume Down can start *fastboot mode* instead (a text screen for system updates from a PC). It is harmless: hold Power + Volume Down until the screen goes black, then press Power to start normally. This erases everything, including the Home Assistant login. If a Google account was on the tablet, Android asks for **that account's password** after the reset (Factory Reset Protection); without it the tablet can't be set up again. Also, Android System WebView goes back to the factory version.

## 11. Security notes

The app is built for a home panel on a network you control. Please keep these points in mind:

- **Unencrypted traffic on your LAN.** Home Assistant is usually reached over plain `http://` at home, and the app's MQTT connection is plain MQTT (port 1883; TLS on port 8883 is not supported yet). Your dashboard session and the MQTT password therefore cross your home network unencrypted. Use this only on a network you trust. If you can, reach Home Assistant over `https://`; the app supports it, and a self-signed certificate can be trusted for your Home Assistant host from the error screen (PIN-protected).
- **Log the tablet in as a non-admin Home Assistant user.** Anyone standing at the panel can use whatever the logged-in user can use. A separate non-admin user (section 3) can't change your Home Assistant configuration.
- **Restrict who may command the panel.** The panel accepts commands (night mode, brightness, reload) from anyone who can publish to its command topics on your broker. With the Mosquitto add-on, every Home Assistant user can by default. If you use broker access lists (section 3), give only Home Assistant write access to `shdash/+/set/#`. Commands can't unlock the kiosk lock, change settings or open other websites.
- **Limit message size on the broker.** The app ignores MQTT messages larger than 1 KB, but it still has to receive them. Mosquitto's `max_packet_size` (e.g. 65536) stops oversized messages at the broker.
- **Links on the dashboard** can lead to other websites, even with the kiosk lock on (section 10).
- **Settings PIN:** use 6 or more digits (section 9).
- **Reporting a vulnerability:** see [SECURITY.md](../SECURITY.md).

## Troubleshooting

**The app's device doesn't appear in Home Assistant.**
- In the app's settings, tap **Test connection**: it names the exact problem (host not found, connection refused, wrong username or password, timeout). The status line under the Settings title shows the live connection state.
- Check that the Mosquitto broker is running and the MQTT integration is set up with discovery enabled.
- Check the MQTT credentials in the app; the Mosquitto add-on's log shows failed logins.
- Make sure the tablet can reach the broker's address and port.

**All the app's entities show "Unavailable".**
The app is offline: the broker reports this automatically when the app's connection drops. Check that the tablet is powered on, connected to Wi-Fi, and that the app is running.

**The tablet asks to log in to Home Assistant again.**
Log in again with the dashboard user and tick **Keep me logged in**. The login is removed only by clearing the app's storage (Android **Settings → Apps → Kiosara → Storage → Clear storage**) or by uninstalling the app; the app never removes it by itself.

**The charger plug doesn't switch.**
Check that the plug's switch entity works from the Home Assistant interface, and that the blueprint automation is enabled and uses the correct plug entity. If the overheat lock helper is on, charging is paused on purpose until the battery cools down (or is critically low).

**A dashboard view other than the start page is showing in the morning.**
The dashboard goes back to its start page when night starts, only while **Night mode** is on in the app's settings.