# Charikot 0.1.2

This release hardens child-device protection based on real-device testing.

## Changes

- Removed all 5-minute and 15-minute temporary unlock flows.
- Removed permanent unlock from the blocked-app overlay.
- A blocked app can now be unlocked only from the PIN-protected Charikot management screen.
- Added Device Admin support for stronger uninstall resistance on shared phones.
- Added Device Owner support using `DevicePolicyManager.setUninstallBlocked()` for OS-enforced uninstall blocking on properly provisioned managed/dedicated devices.
- Added English and Nepali self-protection UI and setup guidance.
- Protection is re-applied after boot when applicable.

## Important Android limitation

Standard Device Admin is uninstall resistance, not absolute uninstall prevention. A sufficiently authorized device user can deactivate a normal Device Admin and then uninstall the app. Full uninstall blocking requires Charikot to be provisioned as the Device Owner / DPC before the device is put into normal use.

See `docs/DEVICE-OWNER-SETUP.md` for the supported strong-protection setup path.
