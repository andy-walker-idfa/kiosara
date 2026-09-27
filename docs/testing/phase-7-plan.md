# Phase 7 plan: soak test, security review, v1.0

**Feature freeze since 0.8.0 (user, 2026-09-27).** The path to v1.0 is: 0.8.0 → soak test (2–4 weeks of normal
use) → bug fixes only → final security review → README/user guide → v1.0. No new features, settings or entities
unless the user explicitly asks. Performance tuning only where the soak test shows a need. The self-updater comes
after v1.0.

## 1. Soak test (2–4 weeks of normal use)

Principle (user, 2026-09-27): a home appliance, not business-critical software. **No alerting and no monitoring
features.** You use the panel normally and notice problems yourself; I check the log file at the end.

### Setup
- The tablet as it is normally used: release build, device owner, kiosk lock on, MQTT on, charging blueprint,
  night mode as you like it. No adb connection needed during the test.
- Note the start date and app version (the log's start marker records version, Android version and time zone).

### During the test (you)
- Use the panel normally. Write down anything odd with date and time (a short list is enough): a frozen or blank
  dashboard, the screen not dark at night or not waking in the morning, a touch that didn't wake it, the lock not
  back after an unlock, the battery outside its range, anything that needed your intervention.
- Normal events that happen anyway count as disturbances: HA restarts or updates, Wi-Fi hiccups, reboots, app
  updates. Nothing needs to be provoked.

### At the end (me)
- Read the log files in the in-app viewer (Settings → Logs, behind the PIN; decided 2026-09-27, no adb log access)
  and check:
  - app memory: the "Memory: app … MB" lines every 30 minutes. After the first 48 hours, growth below 20 %
    over the rest of the test; otherwise the conditional daily WebView restart is added;
  - restarts and self-repairs (watchdog, crash/freeze restart, renderer rebuilds): each one explained, no crash loop;
  - MQTT: disconnects recover by themselves after HA restarts and Wi-Fi outages;
  - kiosk lock: locked again after every unlock, restart and update;
  - night mode: switched on and off on schedule.
- Compare with your list of odd moments.

### Pass
- Nothing on your list that needed your intervention (or each case understood and fixed).
- The log shows no crash loop, no unexplained restarts, and no steady memory growth.
- The battery stayed within the charging blueprint's range (the charging blueprint handles overheating).

Duration: at least 14 days. A fix restarts the clock only for what it touched.

## 2. Final security review (security-reviewer, before v1.0)
Everything shipped since the phase reviews, as one review:
- manifest: permissions, exported components, backup rules, network security config;
- secrets: SecretStore, PIN hashing and attempts, the adb-only PIN reset;
- WebView: TLS/self-signed pinning, blocked schemes, denied web permissions, no JavaScript interface;
- MQTT: command validation, retained commands ignored, no remote unlock;
- kiosk lock and device owner: no restrictions beyond the allowlist, and the ways back;
- log files: no secrets, payloads or URLs with tokens (Phase 6);
- dependencies: versions and licences (licensee), THIRD_PARTY_NOTICES.md;
- the reproducible build and CI (no secrets).

Findings are fixed or explicitly accepted by you before v1.0.

## 3. README and user guide for v1.0
- **README:**
  - what the app is, with a screenshot of the dashboard and the settings;
  - features and "deliberately not included";
  - requirements, install (GitHub release APK), first start;
  - pointer to the user guide;
  - build from source, flavors, privacy, licence, "not affiliated with Home Assistant".
- **User guide:** `docs/home-assistant-setup.md` grows into it (kept short, per the principle). Its current parts:
  - MQTT and HA setup;
  - entities;
  - charging blueprint;
  - screen, night mode and blueprints;
  - recovery and PIN;
  - kiosk lock with the ways back.

  Missing so far: a short "daily use" section (gesture, PIN, unlock) and a troubleshooting section covering the
  soak-test findings.
- Fastlane metadata and screenshots, CHANGELOG 1.0.0, versionCode.
- The v1.0 GitHub release itself only when you say so (the repo is still private; see the release roadmap).
