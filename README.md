# Charikot

**Charikot** is a lightweight, local-only Android parental-control app for a shared family phone. A parent can lock or unlock selected installed apps with a single switch, protect management with a 4- or 6-digit PIN, set schedules, temporarily allow an app for 5 or 15 minutes, and review local blocked-attempt history.

The app supports **English and नेपाली (Nepali)**.

## Why this project exists

The main goal is simple: when a parent hands their phone to a child, the parent should be able to choose which apps are unavailable and later unlock them just as easily.

## Current version

- App name: **Charikot**
- Package: `com.charikot.parentlock`
- Version: `0.1.2` (`versionCode 3`)
- Minimum Android: API 26 (Android 8.0)
- Target Android: API 36
- Architecture: local-only, no server and no account
- UI: Android platform Views written in Kotlin

## Main features

- PIN-protected parent area
- One-switch lock/unlock for launchable installed apps
- English / Nepali language mode
- Always-block or scheduled blocking
- Day-of-week and start/end-time schedule
- No temporary bypass from the blocking screen
- Locked apps can only be unlocked from the PIN-protected Charikot parent area
- Optional Device Admin anti-uninstall protection
- Full uninstall blocking when provisioned as Android Device Owner
- Local blocked-attempt history
- Protection restarts after device boot
- No AccessibilityService
- No internet permission

## How protection works

Charikot v0.1.1 intentionally does **not** use Android Accessibility. Instead it uses:

1. **Usage Access** (`PACKAGE_USAGE_STATS`) to determine which app is currently in the foreground.
2. **Appear on top** (`SYSTEM_ALERT_WINDOW`) to display the local PIN blocking screen over an app selected by the parent.
3. A foreground service while protection is active.

This design avoids reading messages, passwords, typed text, or accessibility event content. App choices, schedules, PIN verification material and reports stay on the device.

## Important limitation

Charikot is a normal third-party Android app, not a Device Owner / enterprise DPC. It cannot guarantee prevention of uninstall, force-stop, Safe Mode, factory reset, or a user manually disabling Usage Access / overlay permission in Android Settings. For a dedicated child-owned managed device, Android Device Owner / DPC architecture is the stronger option.

## Source layout

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/charikot/parentlock/
│   ├── AppLanguage.kt
│   ├── AppPrefs.kt
│   ├── BootReceiver.kt
│   ├── CharikotApp.kt
│   ├── InstalledApps.kt
│   ├── MainActivity.kt
│   ├── PinManager.kt
│   ├── ProtectionAccess.kt
│   ├── ProtectionService.kt
│   └── ScheduleChecker.kt
└── res/
    ├── drawable/ic_charikot.xml
    ├── values/
    └── xml/
```

## Build with Android Studio / Gradle

Open the repository in Android Studio and build the `app` module. The project uses only Android framework APIs plus Kotlin standard library; there are no AndroidX or Compose dependencies in v0.1.1.

Typical command when a compatible Gradle installation is available:

```bash
gradle :app:assembleDebug
```

## Offline build

`build_offline.sh` reproduces the lightweight SDK + `kotlinc` pipeline used for the signed test build. It requires:

- Android SDK platform 36
- Android Build Tools 35.0.0 (override with `BUILD_TOOLS_VERSION`)
- Kotlin compiler (`kotlinc`)
- `ANDROID_SDK_ROOT` or `ANDROID_HOME`

Build an unsigned aligned APK:

```bash
./build_offline.sh
```

For your own signed build, never commit a keystore. Supply signing data through environment variables:

```bash
export CHARIKOT_KEYSTORE=/secure/path/upload.jks
export CHARIKOT_KEY_ALIAS=myalias
export CHARIKOT_KEYSTORE_PASSWORD='...'
export CHARIKOT_KEY_PASSWORD='...'
./build_offline.sh
```

## Privacy

See [PRIVACY.md](PRIVACY.md). Core functionality does not require an internet connection and the manifest contains no `INTERNET` permission.

## Contributing

Issues and pull requests are welcome. Please keep the central UX principle intact: **locking and unlocking an app should remain obvious and fast for a parent.**

## License

Apache License 2.0. See [LICENSE](LICENSE).

## Uninstall protection

Charikot v0.1.2 adds layered self-protection:

- **Standard shared-phone mode:** the parent can activate Android Device Admin. This adds an explicit deactivation step before uninstall on supported Android builds, reducing casual removal by a child.
- **Device Owner mode:** on a dedicated child device, Charikot can use Android `DevicePolicyManager.setUninstallBlocked()` to enforce a true uninstall block for its own package. Device Owner provisioning is an Android system setup step and normally requires a freshly provisioned device.

Android does not let an ordinary third-party app silently make itself permanently non-uninstallable. This project does not attempt to bypass that OS security boundary.
