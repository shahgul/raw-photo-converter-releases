# Raw Photo Converter

A local-first Android RAW converter for Sony ARW and Canon CR2/CR3 files.

## Current public version

**Android 0.0.21-alpha** — [signed APK, LibRaw source, and checksums](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.21-alpha).

This repository is a curated public source snapshot. Development remains in the maintainer's separate development repository. The Android implementation and signed APK are pinned to private commit `90222b66a3997daa59b9c5d179d991fe0c49c60b`. This public snapshot (including refreshed screenshots) is pinned to private commit `7df638d6eb2e5a8ceea9b5cab5c2ceb9b9b59378`. Private Git history is not mirrored. APKs are distributed through GitHub Releases, not committed to the source tree.

**Supported:** Sony ARW and Canon CR2/CR3 RAW files on Android 8.0+ (arm64). Other camera formats (Nikon, Fujifilm, etc.) are not supported yet. This is an alpha: expect rough edges and please report them (see [CONTRIBUTING.md](CONTRIBUTING.md)).

**Installing:** download the APK from the release, open it on your phone, and allow installs from your browser or file manager when asked. Android or Play Protect may warn about an app from outside the Play Store; the APK is signed with the project's stable key and its SHA-256 is listed below and in `SHA256SUMS.txt`. Installing over an earlier signed alpha updates it in place.

## Android app screenshots

Authentic captures from a Motorola Edge 50 Pro (Android 17) running signed **0.0.21-alpha**, except processing and results (see note).

| 01 · Add photos | 02 · Single-photo preview | 03 · Compare Develop |
|:---:|:---:|:---:|
| <img src="docs/screenshots/android-add-photos.png" width="230" alt="Empty Setup with Step 1 Add photos and Step 2 Recipe" /> | <img src="docs/screenshots/android-single-photo-preview.png" width="230" alt="Single Sony ARW with embedded camera JPEG preview" /> | <img src="docs/screenshots/android-compare-develop.png" width="230" alt="Camera JPEG versus Develop RAW split slider" /> |
| **04 · Photo details** | **05 · Selected-photo queue** | **06 · Batch settings** |
| <img src="docs/screenshots/android-photo-details.png" width="230" alt="Expanded EXIF photo details under compare preview" /> | <img src="docs/screenshots/android-multi-photo-queue.png" width="230" alt="Two selected Sony RAW photos with remove actions" /> | <img src="docs/screenshots/android-batch-settings.png" width="230" alt="Recipe with format, gamut, templates, and metadata toggles" /> |
| **07 · Processing** | **08 · Results** | |
| <img src="docs/screenshots/Screenshot_20261010_192007_Raw Photo Converter.jpg" width="230" alt="Processing screen from an earlier phone build" /> | <img src="docs/screenshots/Screenshot_20261010_191959_Raw Photo Converter.jpg" width="230" alt="Results screen from an earlier phone build" /> | |

Screens 01–06 were refreshed on 2026-11-04 from the signed 0.0.21-alpha install. **07 Processing and 08 Results are earlier phone captures** and do not show the current Results thumbnails/StatusMarks.

Screenshot maintenance: Android UI/workflow changes must trigger a review of this gallery. See [AGENTS.md](AGENTS.md#android-screenshot-maintenance).

## What it does

- Select one or multiple RAW photos through Storage Access Framework, or process supported files directly inside a selected folder (nested subfolders are excluded). Photos can also be shared into the app.
- Add more photos to the queue, ignore duplicate URIs, remove individual items, and retain the queue across activity recreation. One remaining photo shows the embedded camera preview, with histogram/EXIF details, 100% loupe, and an opt-in Camera JPEG vs Develop RAW compare split.
- Filename templates (`{name}`, `{edge}`, `{mode}`), honest batch ETA, and real Results JPEG thumbnails.
- Develop Sony ARW and Canon CR2/CR3 locally, or extract the largest supported embedded camera JPEG with Camera look.
- Output JPEG, WebP, or AVIF (AVIF on Android 14+), in sRGB or Display P3 wide gamut. Preserve aspect ratio without implicit cropping; choose Original resolution or a 4000 px long-edge limit. Size optimization respects the Q80 quality floor.
- Preserve supported metadata when enabled, normalize orientation, and save through SAF without broad storage permissions.
- Run sequential foreground batches with cancellation, a completion cue (with an in-app toggle), and a results screen; retry failed files with their original source and recipe. Existing JPEGs are skipped and originals stay untouched.

Unsupported or unreadable picks are reported without discarding valid selections. Conversion and preview processing happen on-device without accounts, telemetry, or cloud upload.

## Alpha validation

The 0.0.21-alpha release was built, linted, unit-tested and signed by the maintainer's release workflow. On a Motorola Edge 50 Pro (Android 17): a six-file Sony ARW folder batch completed with originals unchanged (debug tree), and Compare Develop was smoke-tested on `DSC00135.ARW` after the signed install. This is device smoke evidence, not a formal multi-device validation. Canon CR2/CR3 and WebP/AVIF/Display P3 hardware paths remain pending.

The APK SHA-256 is `8f97981f4acfb74eb14efa91fd1192ec4d972a9b6258e09cda4c2948f702de71`. SHA256SUMS.txt covers both the APK and the exact LibRaw 0.22.2 source archive.

## Build

Open `apps/android` in Android Studio. Install Android SDK Platform 37.0, NDK 30.0.16248370, and CMake 3.22.1. From that directory, run the Gradle wrapper for unit tests, lint, and APK assembly. The build downloads checksum-pinned LibRaw source; conversion itself is offline. APKs include arm64-v8a and x86_64; minSdk is 26.

## License and contributions

Project-owned source is MPL-2.0; see [LICENSE](LICENSE). LibRaw (CDDL), Geist (OFL), and Lucide (ISC) retain their bundled notices under `apps/android/app/src/main/assets/licenses`. See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md). Optional voluntary support is planned and does not unlock converter features.
