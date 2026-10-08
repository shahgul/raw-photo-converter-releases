# Raw Photo Converter — Android

Native Android app for local Sony ARW and Canon CR2/CR3 conversion. It uses Kotlin, Jetpack Compose, Android Storage Access Framework, AndroidX ExifInterface, and LibRaw/NDK.

## Build baseline

- Android Gradle Plugin 9.4.0 with built-in Kotlin and Compose compiler plugin 2.4.10
- Compile and target SDK 37; minimum SDK 26
- Compose BOM 2026.09.00, Activity Compose 1.13.0, ExifInterface 1.4.2, DocumentFile 1.1.0
- APK ABIs: arm64-v8a and x86_64; 32-bit ARM is not included

Open `apps/android` in Android Studio and sync. Install Android SDK Platform 37, NDK 30.0.16248370, and CMake 3.22.1. From this directory, run:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

With an emulator or device connected, add `:app:connectedDebugAndroidTest`. The build downloads checksum-pinned LibRaw 0.22.2 source; conversion itself runs locally.

## Conversion

Develop RAW locally or choose Camera look to extract the largest supported embedded camera JPEG. Sony ARW, Canon CR2, and Canon CR3 are accepted. CR3 metadata is read from its embedded camera JPEG preview. Missing or unsupported previews report an error; they do not silently switch to RAW development.

Standard uses a 4000 px long-edge limit. Original keeps the available pixel dimensions. Neither mode upscales or crops. JPEG output targets 3 MiB while respecting the Q80 quality floor. Supported metadata is restored only when enabled. Existing JPEGs are skipped, and originals are not modified.

Source and output folders are selected with Android's Storage Access Framework; the app does not request broad all-files access. Folder batches run sequentially with foreground progress and cancellation. Failed files can be retried individually or as a batch with the original source, output folder, and recipe. Successful and skipped results are retained.

## Validation

Three supplied Canon EOS R CR3 samples converted end to end on an API 35 x86_64 emulator. An intentional failed file was replaced with a valid CR3 and recovered through Retry Failed Files. Physical-device validation remains pending.

## Third-party notices

Project-owned source code is licensed under MPL-2.0; see the root `LICENSE` file. Geist (OFL), LibRaw (CDDL/LGPL), and Lucide (ISC) retain their own license and source notices under `app/src/main/assets/licenses`.

The converter core remains free to use. Optional voluntary Buy Me a Coffee support is planned and does not unlock features; the support link will be added after the creator page is set up.

## Signed release

Download [Android 0.0.8-alpha](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.8-alpha), `versionCode=8`. APK SHA-256: `e110d6e1fa6a131d03a0113434777d8ed06154e8dc85cf0c7d2e862e922eb03e`. `SHA256SUMS.txt` covers the signed APK and the exact LibRaw source archive. Debug-signed builds are for development and are not the normal update channel.
