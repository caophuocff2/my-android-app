# Clone Windows

Small companion app for the existing eleven clone packages. It uses Android's
task manager on a rooted Android 9 device. It does not alter Roblox DEX, native
libraries, hardware identifiers or detection routines.

Enable overlay permission and root, then tap **Bật quản lý cửa sổ**. Open a clone
from its normal icon or the companion. The service restores each clone's last
rectangle when a new task appears or a clone returns in fullscreen. Drag the
cyan strips just outside the four edges to resize that edge independently.
The companion restarts after boot while enabled. Use **Tắt quản lý cửa sổ**
to remove the strips and stop automatic resizing.

Only the frontmost visible clone receives strips, so background windows do not
cover the active window's input. Sizes are saved per package in the companion's
private preferences. Uninstalling the companion clears saved sizes. Root must
already be available; the app does not root a device.

Build with `./gradlew :windows:assembleDebug`, or use the **Build Clone Windows**
GitHub Actions workflow. Android versions with a different activity-manager
command format require separate validation. UGPhone has not been tested.

The APK supplied by the workflow is signed with the same persistent certificate
as the clone family, so later companion updates retain stored window sizes.
`scripts/test_windows.py` performs real ADB drags on each edge and then stops
and reopens a clone through its normal launcher to check rectangle restoration.

## Play Store test

At the user's request, the local test device's Play Store was disabled using
`pm disable-user --user 0 com.android.vending`. Google Play Services were not
disabled. Undo with `pm enable com.android.vending`.

This is a local configuration test. No UGPhone reset cause or detection bypass
has been established. An APK cannot establish how a cloud provider reacts to
installed applications without testing on that provider's device.

## Runtime validation, 2026-10-25

Workflow run `40190070483` compiled and signed the installed APK successfully.
On the existing LDPlayer Android 9 instance, `test_windows.py` passed independent
left, right, top and bottom edge drags. Each test moved only the selected edge
by 24 pixels. The final rectangle was `[216,111][744,429]`.

Force-stopping Clone 02 and opening its normal launcher icon initially created
a fullscreen task. The controller moved that task to freeform and restored
the saved rectangle. The device crash log contained no entries at the end of
the test. Per-device reboot startup and UGPhone compatibility were not tested.
Results: release asset `window-runtime-tests.json`.
