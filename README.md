# Volcanic Android APK

This folder is an isolated Android implementation for Volcanic. It does not change the Electron desktop app under the repository root.

## MVP scope

- Import local audio through Android document picker.
- Scan local media library when the user grants audio read permission.
- Copy imported files into app-private input storage before decoding to avoid stale URI permission failures.
- Decode `.ncm`, `.kgm`/`.vpr`, and `.qmc` private containers locally.
- Detect decoded payloads as MP3, FLAC, or WAV.
- Persist the playlist to app-private storage.
- Play, pause, previous, next, remove, and clear tracks.
- Keep UI dark, compact, and readable.

## Decode behavior

- MP3 payloads are written to `VolcanicOutput` as `.mp3` and used for playback.
- FLAC/WAV payloads are written as decoded playable files. Android playback can use them, but MP3 encoding is not bundled in this APK yet.
- Unsupported or unrecognized decoded payloads are shown as explicit failures; the app does not report fake conversions.

## Build

Requirements:

- JDK 17
- Android SDK with platform 36 and build-tools installed
- Gradle available on PATH, `GRADLE_HOME` configured, or `F:\Gradle\gradle-9.6.0` present

Commands:

```powershell
cd F:\Codex-projects\Volcanic-musicplayer\android-apk
.\scripts\build-apk.ps1
```

Output:

```text
android-apk\app\build\outputs\apk\debug\app-debug.apk
```

This workspace uses an isolated SDK view at `android-apk\.android-sdk` so APK work does not modify the Electron app folders. If Android SDK is not configured globally, copy `local.properties.example` to `local.properties` and set `sdk.dir`.
