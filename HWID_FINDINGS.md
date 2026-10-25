# Identifier inspection and device test

Inspected source APK SHA-256:
`3a50f089d842457eca29f3531aba68b97b3028de521971c5c9d7ac62331d6814`.

JADX 1.5.6 decoded `classes4.dex`. The client-specific Java bridge contains:

```java
// com.axjava.JNIFunctions
public static String generate(Context context) {
    return Settings.Secure.getString(context.getContentResolver(), "android_id");
}
```

This method returns Android ID directly. It does not concatenate the package
name, generate a random UUID or read an installation preference.
Every clone's DEX and native libraries have been checked byte-for-byte against
the source APK. The method therefore remains identical in all eleven clones.

On stock Android 8+, Android ID is scoped to the signing key, Android user and
device. All eleven clones use one persistent signing key, so this identifier
should be equal when installed under the same Android user on the same device.
It need not equal the source APK's identifier because the source signer differs.
Separate Android users or separate emulators are separate scopes.
Reference: https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID

Native boundary: `com.axjava.Main.Start` loads `libzstd-jni-1.5.7-6.so` and calls
native method `lIIlIIlIIlII(Activity)`. The library exports `JNI_OnLoad` at ELF
virtual address `0x5ef37c` (13,168 bytes). Java references and exported native
names do not establish the complete path from `generate` to the executor's
final HWID. **Equality of the final native/Luau HWID remains unverified.** No
hardware identifier or licensing routine has been patched.

### Follow-up: actual executor HWID comparison (2026-10-25)

The later runtime test obtained successful `gethwid()` results from Clone 01
and Clone 02 in two separate Roblox game sessions, using Arceus X 2.3.7.
Both returned the same nonempty value. The full samples and comparison are
stored locally under `output/clone-runtime/hwid-clone01.json`,
`hwid-clone02.json`, and `hwid-comparison.json`.

This establishes equal executor HWID for these two packages on this LDPlayer.
The earlier static-analysis limitation above is superseded for that tested
pair. Clone 03–11 and UGPhone/cloud-device behavior remain untested. Hardware
identifier generation was not modified.

## Device validation

GitHub Actions run: https://github.com/caophuocff2/my-android-app/actions/runs/40182907937

All eleven packages passed signature, package, label, ZIP alignment and
unchanged DEX/native payload validation. The solid RAR also passed `rar t`.

On the existing LDPlayer Android 9 instance (about 1.5 GiB guest RAM):

- Clone 01 and Clone 02 installed side by side successfully.
- Both could launch into Android freeform tasks; Clone 02 reached the login screen.
- Clone 01 reached `ActivityNativeMain` and rendered the client background.
- Two client processes were not sustained together. When Clone 01 restarted,
  Clone 02 and several other Android processes exited. Guest memory pressure
  is a likely explanation, but no definitive low-memory-killer reason was captured.
- No entry was captured in the Android crash log during these tests.
- Login, gameplay, final executor HWID and eleven simultaneous sessions were not tested.
- The emulator did not expose `settings_ssaid.xml`, so per-app Android IDs were
  not directly compared. Absence of that file is not proof of equality or inequality.

Both test packages and their downloaded APKs have been removed. The emulator
reported FITRIM unsupported: uninstalling frees guest space but does not establish
that its virtual disk has shrunk on the Windows host.

Downloads: https://github.com/caophuocff2/my-android-app/releases/tag/clones-3-1
`ArceusQT-Clones.rar`: 117,286,334 bytes, containing all eleven independent APKs.
