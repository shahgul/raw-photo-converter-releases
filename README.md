# Raw Photo Converter

A local-first Android RAW converter for Sony ARW and Canon CR2/CR3 files.

## Current public version

**Android 0.0.11-alpha** — [signed APK, LibRaw source, and checksums](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.11-alpha).

This repository is a curated public source snapshot. Development remains in the maintainer's separate development repository. This reviewed snapshot and the Android implementation are pinned to commit `1555279cdf2f4fcf1d74c3e43aecd6e9a31cc59d`. Private Git history is not mirrored. APKs are distributed through GitHub Releases, not committed to the source tree.

## Android app screenshots

Authentic Android app captures in workflow order. Setup screens 01–05 show the 0.0.11-alpha implementation on an API 35 emulator with supplied Sony RAW files. Processing and results retain the unchanged phone captures from the earlier alpha.

| 01 · Add photos | 02 · Single-photo preview | 03 · Photo details |
|:---:|:---:|:---:|
| <img src="docs/screenshots/android-add-photos.png" width="230" alt="Add photos" /> | <img src="docs/screenshots/android-single-photo-preview.png" width="230" alt="Single-photo preview" /> | <img src="docs/screenshots/android-photo-details.png" width="230" alt="Photo details" /> |
| **04 · Selected-photo queue** | **05 · Batch settings** | **06 · Processing** |
| <img src="docs/screenshots/android-multi-photo-queue.png" width="230" alt="Two selected Sony RAW photos with individual remove actions and Add photos" /> | <img src="docs/screenshots/android-batch-settings.png" width="230" alt="Current conversion and metadata settings" /> | <img src="docs/screenshots/Screenshot_20261010_192007_Raw Photo Converter.jpg" width="230" alt="Processing" /> |
| **07 · Results** | | |
| <img src="docs/screenshots/Screenshot_20261010_191959_Raw Photo Converter.jpg" width="230" alt="Results" /> | | |


Screenshot maintenance: Android UI/workflow changes must trigger a review of this gallery, with updated captures before public release when needed. See [AGENTS.md](AGENTS.md#android-screenshot-maintenance).

## What it does

- Select one or multiple RAW photos through Storage Access Framework, or process supported files directly inside a selected folder.
- Add more photos to the queue, ignore duplicate URIs, remove individual items, and retain the queue across activity recreation. One remaining photo returns to the embedded camera preview.
- Develop Sony ARW and Canon CR2/CR3 locally, or extract the largest supported embedded camera JPEG with Camera look.
- Preserve aspect ratio without implicit cropping; choose Original resolution or a 4000 px long-edge limit. JPEG size optimization respects the Q80 quality floor.
- Preserve supported metadata when enabled, normalize orientation, and save through SAF without broad storage permissions.
- Run sequential foreground batches with cancellation and per-file outcomes; retry failed files with their original source and recipe. Existing JPEGs are skipped and originals stay untouched.

Folder batches cover supported files directly in that folder; nested subfolders are excluded. Invalid or unreadable photo picks are reported without discarding valid selections. Conversion and preview processing happen on-device without accounts, telemetry, or cloud upload.

## Alpha validation

The 0.0.11-alpha checks passed 13 unit tests, 14 focused API 35 emulator instrumentation tests, release lint with zero errors, and signed release assembly. Device tests cover queue actions, picker append/deduplication/restoration, selected-only batches with partial failures, retry eligibility, cancellation, original-file safety, cleanup, and existing-JPEG skipping. The APK uses the existing stable RSA-4096 certificate and APK Signature Scheme v2. Physical-device performance and real-camera batch validation remain pending.

The APK SHA-256 is `b699ee432e57c016c033dde10d661297abb22f186f4df83cabf54f8e4f28a8df`. SHA256SUMS.txt covers both the APK and exact LibRaw 0.22.2 source archive.

## Build

Open `apps/android` in Android Studio. Install Android SDK Platform 37.0, NDK 30.0.16248370, and CMake 3.22.1. From that directory, run the Gradle wrapper for unit tests, lint, and APK assembly. The build downloads checksum-pinned LibRaw source; conversion itself is offline. APKs include arm64-v8a and x86_64; minSdk is 26.

## License and contributions

Project-owned source is MPL-2.0; see [LICENSE](LICENSE). LibRaw (CDDL), Geist (OFL), and Lucide (ISC) retain their bundled notices under `apps/android/app/src/main/assets/licenses`. No third-party UI code was added for the queue. See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md). Optional voluntary support is planned and does not unlock converter features.
