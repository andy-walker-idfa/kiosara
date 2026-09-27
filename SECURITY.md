# Security policy

## Supported versions
Only the latest release receives fixes. This is a home project with best-effort support; there is no bug
bounty.

## Reporting a vulnerability
Please **don't open a public issue** for security problems. Use GitHub's private reporting instead:
**Security → Report a vulnerability** in this repository. Include the app version (Settings shows it), the
Android version and tablet model, and the steps to reproduce.

You can expect a first answer within about two weeks. Fixed issues are credited in the release notes unless you
prefer otherwise.

## Scope
In scope: the Android app and the Home Assistant blueprint in this repository.

Known and documented, not vulnerabilities in this sense (see the "Security notes" in
[docs/home-assistant-setup.md](docs/home-assistant-setup.md)):
- plain `http://` to Home Assistant and plain MQTT on the home network;
- links on the dashboard can lead to other websites, even with the kiosk lock on;
- the settings PIN is a deterrent, designed never to lock the owner out (adb can reset it).
