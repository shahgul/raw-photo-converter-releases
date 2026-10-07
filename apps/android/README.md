# Raw Photo Converter — Android

Native Android companion for Raw Photo Converter.

This is not a Tauri port. It uses native Android storage, lifecycle, Compose UI, and a LibRaw/NDK RAW engine.

## Baseline

- AGP 9.4.0
- built-in Kotlin + Compose compiler plugin 2.4.10
- compileSdk / targetSdk 37
- minSdk 26
- Compose BOM 2026.09.00
- Activity Compose 1.13.0
- ExifInterface 1.4.2
- DocumentFile 1.1.0

The app supports opening one RAW or a folder through Storage Access Framework, counting immediate ARWs in a folder, and reading real ARW metadata.

Conversion now develops Sony ARW locally into upright sRGB JPEGs, preserves supported metadata, and saves to a selected SAF folder. Folder batches run sequentially with a foreground notification and cancellation. Existing JPEGs are skipped; the Q80 floor takes priority over the 3 MiB target.

The Android UI follows Windows V2 / Quiet Darkroom: graphite and warm paper, safelight accents, bundled Geist fonts, and native interpretations of Shahgul UI V2 processing/results. The workflow is vertical on phones and splits source/output at expanded widths. Optional photo/output details stay out of the primary flow; conversion and cancellation remain in a stable bottom action area. Geist (OFL) and Lucide (ISC) notices are bundled under assets/licenses.

## Open

Open `apps/android` in current Android Studio and sync.

Install Android SDK Platform 37.0, NDK 30.0.16248370, and CMake 3.22.1. The build downloads checksum-pinned LibRaw 0.22.2 source; conversion itself needs no network. APKs include arm64-v8a and x86_64, not 32-bit ARM.

### Local Windows build

The local SDK is installed at `D:\Android\Sdk`; `ANDROID_HOME` and the user PATH point to it. Java 21 is installed; the app targets Java 17 bytecode. Keep machine-specific `local.properties` ignored by Git:

```properties
sdk.dir=D\:/Android/Sdk
```

With Android CLI 1.0.16500706, install build packages using slash-separated package names:

```powershell
& D:/Android/Sdk/cmdline-tools/latest/bin/android.exe --sdk=D:/Android/Sdk sdk install 'platforms/android-37.0' 'build-tools/36.0.0' 'ndk/30.0.16248370' 'cmake/3.22.1'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=2
```

Run these commands from `apps/android`. With a booted emulator or connected phone, run `gradlew.bat :app:connectedDebugAndroidTest --max-workers=2`. Local checks do not require GitHub Actions. The first build downloads Gradle/dependencies and LibRaw; cached builds and conversion run locally.

The isolated local emulator is `RawConverter_API35`, stored under the ignored `app/build/android-avd` directory. To reuse it from PowerShell in `apps/android`:

```powershell
$env:ANDROID_AVD_HOME = Join-Path (Get-Location) 'app/build/android-avd'
& D:/Android/Sdk/emulator/emulator.exe -avd RawConverter_API35 -gpu swiftshader_indirect -no-snapshot
```

Wait for `adb shell getprop sys.boot_completed` to return `1` before running instrumentation tests. A successful Gradle task alone does not prove tests ran: check the XML test counts under `app/build/outputs/androidTest-results/connected`.

See `../../docs/ANDROID-PLAN.md`.


## Conversion and resolution options

Choose **Develop RAW** to render sensor data, or **Camera look** to extract the largest supported embedded camera JPEG. Missing/unsupported previews report an error; they never silently switch to RAW development. Camera look preserves compressed pixels when the preview fits the selected resolution and 3 MiB target; resizing or exceeding that target requires JPEG re-encoding with the same Q80 floor. Supported metadata is restored only when enabled; private preview EXIF/XMP/IPTC/comments are removed first, while ICC profiles remain.

**Standard (4000 px)** is the default long-edge limit. **Original** keeps the available pixel dimensions: sensor-derived dimensions for Develop RAW, embedded-preview dimensions for Camera look. Neither mode upscales or crops. Original resolution can cost more memory/time; memory/input safeguards still apply. Results show the actual output dimensions and mode, with quality shown only for re-encoded JPEGs.

## Processing screen

Starting conversion opens a dedicated processing screen (Morphe-inspired structure in Quiet Darkroom language): source title + phase, count chips, progress, real metric cards (app PSS, elapsed, saved/skip/fail), and Converter logs / Recipe with an info grid plus the latest 200 timestamped events. Batch progress uses completed file counts; a running single file uses an indeterminate indicator. Results are a separate screen with per-file outcomes, JPEG Open/Share/Retry, Copy report, and Convert more. Copy on processing copies the same local report.

Back returns to setup without cancelling; View processing and the foreground notification reopen processing while work is active, or Results when finished. Screen recreation retains navigation and active service state. Cancel remains available during processing; completion settles onto Results. Process death still reports interruption instead of silently restarting, and in-memory event history is not persisted across process death.

## Private GitHub prereleases

Use a stable release signing key; do not publish CI-generated debug signatures as the normal alpha channel.

See `../../docs/ANDROID-RELEASE.md` for the one-time signing-secret setup and the **Android Release** workflow.

Latest signed prerelease: [Android 0.0.6-alpha](https://github.com/shahgul/raw-photo-converter/releases/tag/android-v0.0.6-alpha), `versionCode=6`. Sleek mobile UI with source-land continuity, Open/Share/Retry on results, and System/Light/Dark theme. Installs over 0.0.5-alpha using the same signing certificate. Local validation passed unit tests and release lint for the signed assembly. Physical-phone performance and the separate RAW renderer's colour correction remain pending. Share the downloaded signed APK directly with testers who lack repository access. Existing JPEGs are skipped; compare modes in separate output folders.
