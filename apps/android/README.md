# Raw Photo Converter — Android

Native Android app for local Sony ARW conversion, built with Kotlin, Jetpack Compose, Android Storage Access Framework, and LibRaw/NDK.

## Build baseline

- Android Gradle Plugin 9.4.0 with built-in Kotlin and Compose compiler plugin 2.4.10
- Compile and target SDK 37; minimum SDK 26
- Compose BOM 2026.09.00, Activity Compose 1.13.0, ExifInterface 1.4.2, DocumentFile 1.1.0

Open `apps/android` in Android Studio and sync. Install Android SDK Platform 37, NDK 30.0.16248370, and CMake 3.22.1. From this directory, run:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

With an emulator or device connected, add `:app:connectedDebugAndroidTest`. The build downloads checksum-pinned LibRaw 0.22.2 source; conversion itself runs locally. APKs include arm64-v8a and x86_64, not 32-bit ARM.

## Conversion

Choose **Develop RAW** to render sensor data or **Camera look** to extract the largest supported embedded camera JPEG. Missing or unsupported previews report an error; they do not silently switch to RAW development. Supported metadata is restored only when enabled.

**Standard (4000 px)** sets the default long-edge limit. **Original** keeps the available pixel dimensions. Neither mode upscales or crops. JPEG output targets 3 MiB while respecting the Q80 quality floor. Existing JPEGs are skipped. Folder batches run sequentially with a foreground notification and cancellation. Originals are not modified.

## Processing and results

The processing screen shows phase, file counts, elapsed time, memory, and local conversion events. Results include per-file outcomes, JPEG open/share/retry, report copying, and Convert more. A single-file conversion uses an indeterminate progress indicator; batch progress uses completed file counts.

## Third-party notices

Geist (OFL), LibRaw (CDDL/LGPL), and Lucide (ISC) license and source notices are bundled under `app/src/main/assets/licenses`.

## Signed release

Download Android 0.0.7-alpha from the [public GitHub prerelease](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.7-alpha). `SHA256SUMS.txt` covers the signed APK and the LibRaw source archive. Debug-signed builds are for development and are not the normal update channel.
