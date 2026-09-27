# Privacy policy

*Applies to the app published as "Kiosara" (package `io.github.andy_walker_idfa.smarthome_dashboard`),
in all distribution channels. Last updated: 2026-09-27 (version 0.9.0).*

## Summary
- The app **collects no data**. Nothing is sent to the developer or to any third party.
- It contains **no analytics, no advertising, no crash reporting and no third-party services or SDKs**
  (no Google Play Services, no Firebase).
- It communicates **only with servers you configure yourself**: your Home Assistant instance and
  your own MQTT broker (if you enable it). The one exception is Google Safe Browsing, built into Android's
  web view (see below).

## What the app stores on the device
- Your settings: Home Assistant URL, text size, MQTT broker and user name, brightness and night mode, and a device
  identifier.
  The identifier is a one-way hash of Android's app-specific ID, used only to name this device in
  your own Home Assistant.
- Credentials you enter (e.g. an MQTT password, and a hash of the optional settings PIN) are encrypted with a key
  held in the Android Keystore.
- A short record of the last automatic recovery (time, kind, error type) and recent restart times.
- Log files of what the app did (connections, reloads, restarts, kiosk lock), at most about 2 MB. Passwords,
  tokens and login codes are never written (sensitive-looking parts are masked, error messages are left out).
  They stay on the tablet, can be viewed only in the PIN-protected Settings, and are not backed up or sent anywhere.
- The web view keeps cookies, cache and local storage for your Home Assistant pages, so you stay logged in.
- None of this is included in Android cloud backups or device-to-device transfers.

## What is sent to your MQTT broker (only if you enable MQTT)
Once you enable the MQTT connection, the app reports its status to **your** broker (normally your own
Home Assistant) so it can be shown and automated there. It sends:
- the battery level and battery temperature;
- whether night mode is on, and the screen brightness.

(Debug builds, which are meant for development, send additional diagnostic values.)

The broker can send commands back: switch night mode, set the screen brightness, and reload
the page. Nothing is sent anywhere else.

## Camera, microphone and other sensitive access
The app does **not** use the camera or the microphone, takes no screenshots and opens no network port. Web pages
you show in the app are denied camera and microphone access.

## Android System WebView and Google Safe Browsing
The dashboard is shown by Android System WebView, a component of Android made by Google. The app switches off
WebView's usage statistics, so no WebView metrics are sent to Google.

WebView's **Safe Browsing** is left on on purpose: when a page on the panel (for example, one reached through a
link) is about to load, WebView checks its address against Google's list of known dangerous sites and warns
instead of opening it. For this, WebView downloads that list and may send Google a short, anonymised hash prefix of
an address it can't decide locally; your Home Assistant pages on your own network are not affected in any
meaningful way. This is Google's standard Android protection, controlled by Google's privacy policy, not by this
app; the app itself sends nothing to Google.

## Contact
Questions: open an issue in the project's GitHub repository.
