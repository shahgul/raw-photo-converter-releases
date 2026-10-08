# Raw Photo Converter — Android

Native Android app for local Sony ARW and Canon CR2/CR3 conversion. It uses Kotlin, Jetpack Compose, Android Storage Access Framework, AndroidX ExifInterface, and LibRaw/NDK.

## Build baseline

- Android Gradle Plugin 9.4.0 with built-in Kotlin and Compose compiler plugin 2.4.10
- Compile and target SDK 37; minimum SDK 26
- Compose BOM 2026.09.00, Activity Compose 1.13.0, ExifInterface 1.4.2, DocumentFile 1.1.0
- APK ABIs: arm64-v8a and x86_64; 32-bit ARM is not included

Open apps/android in Android Studio and sync. Install Android SDK Platform 37, NDK 30.0.16248370, and CMake 3.22.1. From this directory, run ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug.

With an emulator or device connected, add :app:connectedDebugAndroidTest. The build downloads checksum-pinned LibRaw 0.22.2 source; conversion itself runs locally.

## Conversion

Develop RAW locally or choose Camera look to extract the largest supported embedded camera JPEG. Sony ARW, Canon CR2, and Canon CR3 are accepted. A single-photo preview is staged from its SAF stream into a bounded temporary cache file, supporting providers such as Downloads that expose proxy file descriptors; the temporary file is removed after decoding. CR3 metadata is read from its embedded camera JPEG preview. Missing or unsupported previews report an error; they do not silently switch to RAW development.

Standard uses a 4000 px long-edge limit. Original keeps the available pixel dimensions. Neither mode upscales or crops. JPEG output targets 3 MiB while respecting the Q80 quality floor. Supported metadata is restored only when enabled. Existing JPEGs are skipped, and originals are not modified.

Source and output folders are selected with Android's Storage Access Framework; the app does not request broad all-files access. Folder batches run sequentially with foreground progress and cancellation. Failed files can be retried individually or as a batch with the original source, output folder, and recipe. Successful and skipped results are retained.

## Validation

Local verification for Android 0.0.9-alpha passed 13 unit tests, 20 emulator instrumentation tests, debug and release lint with zero errors, and signed release assembly. Three supplied Canon EOS R CR3 previews loaded, including through the Downloads picker. Camera look conversion saved a 4000 x 2667 JPEG at 2.7 MiB and left the source CR3 SHA-256 unchanged. Physical-device validation remains pending.

## Third-party notices

Project-owned source code is licensed under MPL-2.0; see the root LICENSE file. Geist (OFL), LibRaw (CDDL/LGPL), and Lucide (ISC) retain their own license and source notices under app/src/main/assets/licenses.

The converter core remains free to use. Optional voluntary Buy Me a Coffee support is planned and does not unlock features; the support link will be added after the creator page is set up.

## Signed release

Download Android 0.0.9-alpha, versionCode=9: https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.9-alpha. APK SHA-256: c893a19fa5d9425f2aee67093dc75457ceba7eef740acc2e538a18cca14b3517. The SHA256SUMS.txt asset (SHA-256 d928abaf353dd9cbd7e7957def16b6ece0d1329f0ca53a56e8ad59bfec13cd1b) covers the signed APK and exact LibRaw source archive. The APK is signed with the project's stable RSA-4096 certificate and APK Signature Scheme v2.