# Charikot Privacy Notes

Charikot is designed as a local-only parental-control utility.

## Data handled locally

The app may store on the device:

- selected blocked-app package names
- schedule settings
- language preference
- temporary-unlock expiry times
- blocked-attempt history (app/package and timestamp)
- PIN verification material

## PIN security

The PIN is not stored as plaintext. Charikot creates an HMAC-SHA256 key in Android Keystore and stores only a salted verification value in private app preferences.

## Special Android access

Charikot uses Usage Access to identify the foreground app and Appear on top to show its blocking screen. It does not use AccessibilityService.

## Network

The Android manifest does not request `android.permission.INTERNET`. Core functionality therefore does not upload activity to a server.

## Device limitations

A user with sufficient control of Android Settings can disable special access, force-stop, or uninstall a normal third-party app. Charikot does not claim to provide enterprise Device Owner lockdown.
