# Raw Photo Converter — Android

Native Android companion for Raw Photo Converter.

This is not a Tauri port. It uses native Android storage, lifecycle, Compose UI, and a LibRaw/NDK RAW engine.

See the [Android screenshot gallery](../../README.md#android-screenshots) for real app screens. Screenshots must be reviewed and refreshed whenever Android changes affect the depicted UI or workflow; see [AGENTS.md](../../AGENTS.md#android-screenshot-maintenance).

Gallery screens 01–08 were captured on a Motorola Edge 50 Pro from signed 0.0.21-alpha (Add photos, single preview, Compare Develop, photo details, multi-photo queue, batch settings, processing, results with thumbnails).

The 0.0.21-alpha release adds 100% loupe, Camera JPEG vs Develop RAW compare split, filename templates, honest batch ETA, real Results thumbnails, and matching Step 1/Step 2 cards on top of 0.0.12–0.0.20 input/output, format/P3/histogram, and progress polish.

## Baseline

- AGP 9.4.0
- built-in Kotlin + Compose compiler plugin 2.4.10
- compileSdk / targetSdk 37
- minSdk 26
- Compose BOM 2026.09.00
- Activity Compose 1.13.0
- ExifInterface 1.4.2
- DocumentFile 1.1.0

The app supports selecting one or multiple ARW, CR2, or CR3 photos, or a folder, through Storage Access Framework. Input and output folders are independent: choosing **Save to** does not replace the selected RAW photos or source folder, and both sides survive activity recreation. Add photos appends to the selected-photo queue and ignores repeated URIs. Queue rows can be removed before conversion; one remaining photo returns to the single-photo preview. Unsupported or unreadable selections are reported without discarding valid picks. Folder batches include supported RAW files directly inside the selected folder, not nested subfolders, and show an aggregate count and size instead of per-photo previews. Selecting one photo copies its SAF stream into a bounded temporary cache file, loads the embedded camera JPEG preview, then removes the temporary file. This supports providers such as Downloads that expose proxy file descriptors. The preview is orientation-corrected and fitted without cropping. It is a camera rendering and can differ from **Develop RAW**. If the RAW has no supported embedded JPEG, the app explains that and keeps **Develop RAW** available; **Camera look** conversion requires that embedded preview. AndroidX ExifInterface reads ARW and CR2 metadata directly; CR3 metadata is read from its embedded camera JPEG preview during conversion.

Conversion develops Sony ARW and Canon CR2/CR3 locally into upright sRGB JPEGs, preserves supported metadata, and saves to a selected SAF folder. Folder batches run sequentially with a foreground notification and cancellation. Failed files can be retried individually or as a batch using the original source URI, output folder, and recipe; successful and skipped results are retained. Existing JPEGs are skipped; the Q80 floor takes priority over the 3 MiB target. Three supplied Canon EOS R CR3 samples converted end to end on an API 35 x86_64 emulator, and a failed test item recovered through Retry Failed Files. Physical Sony ARW folder-batch validation passed on a Motorola Edge 50 Pro (Android 17, arm64-v8a): six ARW files → six JPEGs (Develop RAW / Standard 4000 px), originals unchanged; Canon CR2/CR3 physical-device coverage remains pending.

The Android UI follows Windows V2 / Quiet Darkroom: graphite and warm paper, safelight accents, bundled Geist fonts, and native interpretations of Shahgul UI V2 processing/results. The workflow is vertical on phones and splits source/output at expanded widths. Optional photo/output details stay out of the primary flow; conversion and cancellation remain in a stable bottom action area. Geist (OFL) and Lucide (ISC) notices are bundled under assets/licenses.

## License and support

Project-owned source code is licensed under MPL-2.0; see the repository [LICENSE](../../LICENSE) and [licensing policy](../../docs/LICENSING.md). LibRaw, Geist, Lucide, and other third-party components keep their own licenses and notices. The converter core is free to use; optional Buy Me a Coffee support is planned and does not unlock features. No support-page link is available yet.

## Open

Open `apps/android` in current Android Studio and sync.

Install Android SDK Platform 37.0, NDK 30.0.16248370, and CMake 3.22.1. The build downloads checksum-pinned LibRaw 0.22.2 source; conversion itself needs no network. APKs include arm64-v8a and x86_64, not 32-bit ARM.

### Local Windows build

The local SDK is installed at `C:\Android\Sdk` (set `sdk.dir` and `ANDROID_HOME` to match your machine). Java 21 is installed; the app targets Java 17 bytecode. Keep machine-specific `local.properties` ignored by Git:

```properties
sdk.dir=C\:/Android/Sdk
```

With Android CLI 1.0.16500706, install build packages using slash-separated package names:

```powershell
& "$env:USERPROFILE/.android/bin/android-cli.exe" --sdk=C:/Android/Sdk sdk install 'platforms/android-37.0' 'build-tools/36.0.0' 'ndk/30.0.16248370' 'cmake/3.22.1'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=2
```

On low-RAM hosts, prefer a phone over the emulator and build only the device ABI:

```powershell
.\gradlew.bat :app:installDebug --max-workers=1 --no-daemon -PabiFilters=arm64-v8a "-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8"
```

Run these commands from `apps/android`. With a booted emulator or connected phone, run `gradlew.bat :app:connectedDebugAndroidTest --max-workers=2`. Local checks do not require GitHub Actions. The first build downloads Gradle/dependencies and LibRaw; cached builds and conversion run locally. Signed alphas are published through GitHub Releases on this repository; day-to-day builds and signing happen in the maintainer's development repository.

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

The development build accepts single files and batches from Android **Share → Raw Photo Converter**. Readable shared document folders expand into their directly contained RAW files; subfolders are excluded. File managers that cannot grant access to folder contents require the in-app **Folder** picker. ARW, CR2, and CR3 files enter the queue; unsupported or unreadable items are reported without discarding valid photos. Sharing never starts conversion automatically.

Saved JPEGs can be shared individually from their result rows or together with **Share N JPEGs**. Only successful, readable outputs are included; failed and skipped conversions are excluded. JPEGs remain saved in the selected output folder, and the receiving app gets temporary read access.

The expandable **Export options** section applies to both share actions. **Remove EXIF metadata** defaults off and removes EXIF/XMP/IPTC/comments from temporary shared copies without changing saved JPEGs. Normally compressed image data and ICC profiles remain intact. Photos relying on EXIF orientation are rotated/mirrored into their pixels and re-encoded at JPEG quality 95 in sRGB before removing metadata. Temporary copies expire on a later metadata-free export after 24 hours; preparation failures are cleaned immediately. Large exports have explicit memory/input limits and fail without sharing the original as a fallback.

Starting conversion opens a dedicated processing screen (Morphe-inspired structure in Quiet Darkroom language): source title + phase, count chips, progress, real metric cards (app PSS, elapsed, saved/skip/fail), and Converter logs / Recipe with an info grid plus the latest 200 timestamped events. Batch progress uses completed file counts; a running single file uses an indeterminate indicator. Results are a separate screen with per-file outcomes, JPEG Open/Share/Retry, Copy report, and Convert more. Copy on processing copies the same local report.

Back returns to setup without cancelling; View processing and the foreground notification reopen processing while work is active, or Results when finished. Screen recreation retains navigation and active service state. Cancel remains available during processing; completion settles onto Results. Process death still reports interruption instead of silently restarting, and in-memory event history is not persisted across process death.

## Public GitHub prereleases

Signed alphas are published on this repository's Releases page. Do not treat debug-signed APKs as the normal install channel.

Latest public signed alpha: [Android 0.0.21-alpha](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.21-alpha), `versionCode=1019`. It adds 100% loupe, Camera JPEG vs Develop RAW compare split, filename templates (`{name}` / `{edge}` / `{mode}`), honest batch ETA, real Results thumbnails, and matching Step 1/Step 2 Quiet Darkroom cards. Edge 50 Pro smoke covered Sony folder batch and Compare Develop.

Previous public alphas include [0.0.20-alpha](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.20-alpha) and [0.0.11-alpha](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.11-alpha). APK SHA-256 for 0.0.21-alpha: `8f97981f4acfb74eb14efa91fd1192ec4d972a9b6258e09cda4c2948f702de71`.
