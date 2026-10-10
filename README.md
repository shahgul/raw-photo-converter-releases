# Raw Photo Converter

A local-first Android RAW converter for Sony ARW and Canon CR2/CR3 files.

## Current public version

**Android 0.0.10-alpha**

This repository is a curated public source snapshot. Ongoing development stays in the private development repository, and this snapshot changes only for an explicitly published version.

The reviewed source snapshot is pinned to private commit `a55410c536bb57a750b93607a882059b77187880`. The Android implementation and signed APK are from private commit `57b7a6138a3b4598a9f2cf42e42f3c82b85aedc5`.

## Android app screenshots

Actual Android alpha screenshots, shown in workflow order. These are documentation images, not a new app release.

| Add photos | RAW preview and metadata | Batch setup |
|:---:|:---:|:---:|
| <img src="docs/screenshots/Screenshot_20261010_192056_Raw%20Photo%20Converter.jpg" width="240" alt="Choose Sony ARW or Canon CR2/CR3 files" /> | <img src="docs/screenshots/Screenshot_20261010_192051_Raw%20Photo%20Converter.jpg" width="240" alt="Embedded JPEG preview and camera metadata" /> | <img src="docs/screenshots/Screenshot_20261010_192037_Raw%20Photo%20Converter.jpg" width="240" alt="Batch selection and conversion recipe" /> |
| **Processing** | **Results** | **App identity** |
| <img src="docs/screenshots/Screenshot_20261010_192007_Raw%20Photo%20Converter.jpg" width="240" alt="Live processing metrics and converter logs" /> | <img src="docs/screenshots/Screenshot_20261010_191959_Raw%20Photo%20Converter.jpg" width="240" alt="Saved JPEG and conversion results" /> | <img src="docs/screenshots/Screenshot_20261010_191925_Raw%20Photo%20Converter.jpg" width="240" alt="Raw Photo Converter Android app identity" /> |

## What it does

- develops Sony ARW and Canon CR2/CR3 files locally on Android
- offers the largest supported embedded camera JPEG as Camera look
- preserves source aspect ratio and never crops implicitly
- supports Original resolution or a 4000 px long-edge limit
- targets JPEG size while respecting a Q80 quality floor
- preserves supported camera, lens, exposure, GPS, and copyright metadata
- normalizes output orientation
- selects one photo at a time or processes supported files directly inside a selected folder
- retries individual or all failed files using the original source and conversion recipe
- uses Android Storage Access Framework without broad storage permission
- keeps processing on-device; originals are not modified

A single-photo preview shows its filename and RAW size in a centered bottom caption, with a small index at the lower-left. Folder batches show an aggregate count and size; nested subfolders are not included.

## Alpha status

The 0.0.10-alpha release workflow passed 13 unit tests, release lint, signed assembly, and APK Signature Scheme v2 verification. On an API 35 x86_64 emulator, both supplied Sony ARW previews loaded; `DSC05537.ARW` developed to a 4000 x 2672 JPEG at 2.3 MiB while its source remained unchanged. Canon CR3 conversion and failed-file retry were verified on the same emulator. Physical-device validation remains pending.

Download the stable-key signed APK and checksum bundle from the [Android 0.0.10-alpha prerelease](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.10-alpha). APK binaries are distributed through Releases, not committed to the source tree.

Screenshot maintenance: UI/workflow changes require refreshed real screenshots and gallery captions before the corresponding public release. See [AGENTS.md](AGENTS.md#android-screenshot-maintenance).

## Build

Open `apps/android` in Android Studio and sync. Install Android SDK Platform 37, NDK 30.0.16248370, and CMake 3.22.1. From `apps/android`, run the Gradle wrapper for unit tests, lint, or debug APK assembly. The build downloads checksum-pinned LibRaw 0.22.2 source; conversion itself needs no network.

## Repository policy

Public versions are intentionally published snapshots pinned to reviewed private source commits. Do not assume `main` represents unreleased development work.

## Third-party components

The Android app uses LibRaw, Geist, and Lucide. Their attribution and license notices are included with the app source under `apps/android/app/src/main/assets/licenses`.

## Project license and support

Project-owned source is licensed under the Mozilla Public License 2.0 (MPL-2.0); see [LICENSE](LICENSE). Third-party components retain their own licenses and notices. The converter core remains free to use. Optional voluntary Buy Me a Coffee support is planned and will not unlock features.
