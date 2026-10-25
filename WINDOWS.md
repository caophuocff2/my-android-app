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
