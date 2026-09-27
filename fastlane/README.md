# Store metadata (Fastlane layout)

Read by F-Droid, IzzyOnDroid and (optionally) Google Play tooling.

- `metadata/android/<locale>/title.txt`: the app's display name. **Change it here and in
  `app/src/main/res/values*/strings.xml` (`app_name`) when the final brand is chosen.**
- `short_description.txt` (max 80 characters) and `full_description.txt` (max 4000 characters; simple HTML).
- `changelogs/<versionCode>.txt` (max 500 characters). One file per release, kept in sync with `CHANGELOG.md`.
- Screenshots: to be added later in `metadata/android/<locale>/images/phoneScreenshots/` (and
  `tenInchScreenshots/` for tablets) as PNG/JPG, plus `images/icon.png` (512×512).

Locales: `en-US`, `cs-CZ`.
