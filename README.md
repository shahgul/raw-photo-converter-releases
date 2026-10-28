# Raw Photo Converter

A local-first Android RAW converter for Sony ARW and Canon CR2/CR3 files.

## Current public version

**Android 0.0.20-alpha** — [signed APK, LibRaw source, and checksums](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.20-alpha).

This repository is a curated public source snapshot. Development remains in the maintainer's separate development repository. This reviewed snapshot and the Android implementation are pinned to commit `0dc9345853c5a15b40436c3161b71f5bf670c5aa`. Private Git history is not mirrored. APKs are distributed through GitHub Releases, not committed to the source tree.

**Supported:** Sony ARW and Canon CR2/CR3 RAW files on Android 8.0+ (arm64). Other camera formats (Nikon, Fujifilm, etc.) are not supported yet. This is an alpha: expect rough edges and please report them (see [CONTRIBUTING.md](CONTRIBUTING.md)).

**Installing:** download the APK from the release, open it on your phone, and allow installs from your browser or file manager when asked. Android or Play Protect may warn about an app from outside the Play Store; the APK is signed with the project's stable key and its SHA-256 is listed below and in `SHA256SUMS.txt`. Installing over an earlier signed alpha updates it in place.

## Android app screenshots

Authentic Android app captures in workflow order, taken on 0.0.11-alpha. Screens 01–05 are from an API 35 emulator with supplied Sony RAW files; processing and results are from an earlier phone build. **The 0.0.12–0.0.20 changes (output formats, histogram, loupe, new results screen, Step 1/Step 2 labels) are not shown here yet; refreshed captures are pending.**

| 01 · Add photos | 02 · Single-photo preview | 03 · Photo details |
|:---:|:---:|:---:|
| <img src="docs/screenshots/android-add-photos.png" width="230" alt="Add photos" /> | <img src="docs/screenshots/android-single-photo-preview.png" width="230" alt="Single-photo preview" /> | <img src="docs/screenshots/android-photo-details.png" width="230" alt="Photo details" /> |
| **04 · Selected-photo queue** | **05 · Batch settings** | **06 · Processing** |
| <img src="docs/screenshots/android-multi-photo-queue.png" width="230" alt="Two selected Sony RAW photos with individual remove actions and Add photos" /> | <img src="docs/screenshots/android-batch-settings.png" width="230" alt="Current conversion and metadata settings" /> | <img src="docs/screenshots/Screenshot_20261010_192007_Raw Photo Converter.jpg" width="230" alt="Processing" /> |
| **07 · Results** | | |
| <img src="docs/screenshots/Screenshot_20261010_191959_Raw Photo Converter.jpg" width="230" alt="Results" /> | | |


Screenshot maintenance: Android UI/workflow changes must trigger a review of this gallery, with updated captures before public release when needed. See [AGENTS.md](AGENTS.md#android-screenshot-maintenance).

## What it does

- Select one or multiple RAW photos through Storage Access Framework, or process supported files directly inside a selected folder (nested subfolders are excluded). Photos can also be shared into the app.
- Add more photos to the queue, ignore duplicate URIs, remove individual items, and retain the queue across activity recreation. One remaining photo shows the embedded camera preview, with Histogram and EXIF chips and a tap-to-open 100% loupe (pinch, pan, double-tap).
- Develop Sony ARW and Canon CR2/CR3 locally, or extract the largest supported embedded camera JPEG with Camera look.
- Output JPEG, WebP, or AVIF (AVIF on Android 14+), in sRGB or Display P3 wide gamut. Preserve aspect ratio without implicit cropping; choose Original resolution or a 4000 px long-edge limit. Size optimization respects the Q80 quality floor.
- Preserve supported metadata when enabled, normalize orientation, and save through SAF without broad storage permissions.
- Run sequential foreground batches with cancellation, a completion cue (with an in-app toggle), and a results screen listing each photo's outcome, dimensions and size; retry failed files with their original source and recipe. Existing JPEGs are skipped and originals stay untouched.

Unsupported or unreadable picks are reported without discarding valid selections. Conversion and preview processing happen on-device without accounts, telemetry, or cloud upload.

## Alpha validation

The 0.0.20-alpha release was built, linted, unit-tested and signed by the maintainer's release workflow, and was run by the maintainer on a physical Android phone, where it behaved as expected. This is a smoke test, not a formal multi-device validation: performance, memory and thermal behavior across devices and real-camera batches are not yet measured. Earlier alphas passed emulator instrumentation tests with Sony ARW and Canon CR3 samples.

The APK SHA-256 is `ddc757aab9b84d3513bbdc9954e7d25c3387b65ed3403e28ce41ddc102285373`. SHA256SUMS.txt covers both the APK and the exact LibRaw 0.22.2 source archive.

## Build

Open `apps/android` in Android Studio. Install Android SDK Platform 37.0, NDK 30.0.16248370, and CMake 3.22.1. From that directory, run the Gradle wrapper for unit tests, lint, and APK assembly. The build downloads checksum-pinned LibRaw source; conversion itself is offline. APKs include arm64-v8a and x86_64; minSdk is 26.

## License and contributions

Project-owned source is MPL-2.0; see [LICENSE](LICENSE). LibRaw (CDDL), Geist (OFL), and Lucide (ISC) retain their bundled notices under `apps/android/app/src/main/assets/licenses`. No third-party UI code was added for the queue. See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md). Optional voluntary support is planned and does not unlock converter features.
