# Charikot Device Owner mode

Charikot v0.1.2 supports two self-protection levels.

## 1. Shared-phone mode (recommended for a parent's own phone)

Open **Charikot > Settings > Charikot uninstall protection** and enable Android Device Admin. This adds an explicit deactivation step before uninstall on supported Android versions. It is useful against casual removal, but the phone owner can still deactivate Device Admin in Android Settings.

## 2. Device Owner mode (strongest, dedicated child device)

Android only allows a Device Owner during device provisioning. Exact provisioning behavior varies by Android/OEM version and commonly requires a freshly reset device with no existing accounts or secondary users. For development/testing, supported builds may allow provisioning through ADB before normal setup is completed:

```bash
adb shell dpm set-device-owner com.charikot.parentlock/.CharikotDeviceAdminReceiver
```

After Charikot becomes Device Owner, it calls:

```text
DevicePolicyManager.setUninstallBlocked(..., com.charikot.parentlock, true)
```

This is the Android-supported way to enforce uninstall blocking. If the ADB command is rejected, the device is not in a state that permits Device Owner provisioning; do not try to bypass Android's provisioning checks.
