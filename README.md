# Raw Photo Converter

A local-first RAW converter for Sony ARW and Canon CR2/CR3 files — Windows desktop and Android.

## Current public versions

**Windows 0.1.0-alpha** — [NSIS x64 installer and checksums](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/windows-v0.1.0-alpha) (**unsigned**; SmartScreen may warn). Pinned to private commit `764a4fbe8c5f01a1334c0305d298fe969a8bd382`.

**Android 0.0.22-alpha** — [signed APK, LibRaw source, and checksums](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.22-alpha). Pinned to private commit `444ece548a1a52b83cf6ef9be79b6ee8fc6b34b3` (`versionCode=1020`).

This repository is a curated public source snapshot. Development remains in the maintainer's separate development repository. Private Git history is not mirrored. Binaries are distributed through GitHub Releases, not committed to the source tree.

**Supported:** Sony ARW and Canon CR2/CR3. Other camera formats (Nikon, Fujifilm, etc.) are not supported yet. This is alpha software: expect rough edges and please report them (see [CONTRIBUTING.md](CONTRIBUTING.md)).

### Windows install

Download the NSIS x64 setup from the Windows release. The installer is **not Authenticode-signed** in this alpha — Windows SmartScreen / Defender may warn; verify the SHA-256 in `SHA256SUMS.txt` before installing. Conversion still needs local [darktable-cli, ImageMagick, and ExifTool](docs/WINDOWS-SETUP.md); the installer ships the app shell only.

### Android install

Download the APK from the Android release, open it on your phone, and allow installs from your browser or file manager when asked. The APK is signed with the project's stable key; SHA-256 is listed in the release `SHA256SUMS.txt`. Installing over an earlier signed alpha updates it in place.

## Windows desktop

Quiet Darkroom B2 shell (Tauri 2 + React): folder pick, Develop dock with live progress, Inspect with EXIF strip, hold-to-remove, rotate/compare, first-run dependency help, shared crop-frame icon. Processing stays local — no accounts, telemetry, or cloud upload.

Installer SHA-256: `0d248aa85daf299b10df24221b06894ac615275dabb6a26265662f556cf9cea7`.

## Android app screenshots

Authentic captures from a Motorola Edge 50 Pro (Android 17). In-app gallery screens were captured from signed **0.0.21-alpha**; **0.0.22-alpha** keeps that UI and ships the shared Quiet Darkroom crop-frame launcher icon.

| 01 · Add photos | 02 · Single-photo preview | 03 · Compare Develop |
|:---:|:---:|:---:|
| <img src="docs/screenshots/android-add-photos.png" width="230" alt="Empty Setup with Step 1 Add photos and Step 2 Recipe" /> | <img src="docs/screenshots/android-single-photo-preview.png" width="230" alt="Single Sony ARW with embedded camera JPEG preview" /> | <img src="docs/screenshots/android-compare-develop.png" width="230" alt="Camera JPEG versus Develop RAW split slider" /> |
| **04 · Photo details** | **05 · Selected-photo queue** | **06 · Batch settings** |
| <img src="docs/screenshots/android-photo-details.png" width="230" alt="Expanded EXIF photo details under compare preview" /> | <img src="docs/screenshots/android-multi-photo-queue.png" width="230" alt="Two selected Sony RAW photos with remove actions" /> | <img src="docs/screenshots/android-batch-settings.png" width="230" alt="Recipe with format, gamut, templates, and metadata toggles" /> |
| **07 · Processing** | **08 · Results** | |
| <img src="docs/screenshots/android-processing.png" width="230" alt="Processing screen developing two Sony ARWs with ETA" /> | <img src="docs/screenshots/android-results.png" width="230" alt="Results with saved-frame thumbnails and per-file Open Share" /> | |

Screenshot maintenance: Android UI/workflow changes must trigger a review of this gallery. See [AGENTS.md](AGENTS.md#android-screenshot-maintenance).

## What Android does

- Select one or multiple RAW photos through Storage Access Framework, or process supported files directly inside a selected folder (nested subfolders are excluded). Photos can also be shared into the app.
- Add more photos to the queue, ignore duplicate URIs, remove individual items, and retain the queue across activity recreation. One remaining photo shows the embedded camera preview, with histogram/EXIF details, 100% loupe, and an opt-in Camera JPEG vs Develop RAW compare split.
- Filename templates (`{name}`, `{edge}`, `{mode}`), honest batch ETA, and real Results JPEG thumbnails.
- Develop Sony ARW and Canon CR2/CR3 locally, or extract the largest supported embedded camera JPEG with Camera look.
- Output JPEG, WebP, or AVIF (AVIF on Android 14+), in sRGB or Display P3 wide gamut. Preserve aspect ratio without implicit cropping; choose Original resolution or a 4000 px long-edge limit. Size optimization respects the Q80 quality floor.
- Preserve supported metadata when enabled, normalize orientation, and save through SAF without broad storage permissions.
- Run sequential foreground batches with cancellation, a completion cue (with an in-app toggle), and a results screen; retry failed files with their original source and recipe. Existing JPEGs are skipped and originals stay untouched.

Unsupported or unreadable picks are reported without discarding valid selections. Conversion and preview processing happen on-device without accounts, telemetry, or cloud upload.

Android APK SHA-256: `6fc42fe7dbf0861ee450cb3cf9c6175eb9cbf19209664c57813b3a53756b48ac`.

## Build

**Windows:** Rust stable + Node. From `apps/desktop`, `npm install` then `npm run tauri -- build`. NSIS output lands under `src-tauri/target/release/bundle/nsis/`. See [apps/desktop/PACKAGING.md](apps/desktop/PACKAGING.md) and [docs/WINDOWS-SETUP.md](docs/WINDOWS-SETUP.md).

**Android:** Open `apps/android` in Android Studio. Install Android SDK Platform 37.0, NDK 30.0.16248370, and CMake 3.22.1. From that directory, run the Gradle wrapper for unit tests, lint, and APK assembly. The build downloads checksum-pinned LibRaw source; conversion itself is offline. APKs include arm64-v8a and x86_64; minSdk is 26.

## License and contributions

Project-owned source is MPL-2.0; see [LICENSE](LICENSE). LibRaw (CDDL), Geist (OFL), and Lucide (ISC) retain their bundled notices under `apps/android/app/src/main/assets/licenses`. See [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), and [docs/LICENSING.md](docs/LICENSING.md). Optional voluntary support is planned and does not unlock converter features.
