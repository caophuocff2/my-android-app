# APK clone builds

Run **Build 11 APK clones** under GitHub Actions. The build downloads `source.apk`
from release `apk-source-v2.742`, checks its SHA-256, repackages 11 different
application IDs, then signs every APK using the same persistent certificate.
The public release contains individual APKs, a real solid RAR, and a verification report.

Signing material is held in GitHub Actions secrets `CLONE_KEYSTORE_BASE64` and
`CLONE_KEYSTORE_PASSWORD`; neither belongs in this public repository.
Keep the original local key for future updates.

Executable DEX and native libraries are verified unchanged. Package names,
provider authorities, app permission namespaces and launcher labels change.
Activities opt into resizing so an Android system with freeform window support
can open the clones in separate windows.

Android 8+ scopes Android ID to the signing key, Android user and device:
https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID
One certificate can therefore give these packages the same Android ID on the
same Android user/device. This does **not** establish that the client's own HWID
uses Android ID. No HWID or licensing logic is patched.

Device installation, launch, authentication, gameplay and multiple floating
windows require runtime validation. Independent package names alone do not
add Android freeform-window support.
