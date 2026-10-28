# Raw Photo Converter for Android

Native Kotlin + Jetpack Compose, Storage Access Framework, and a LibRaw/NDK engine.

## Android 0.0.20-alpha

Select one or multiple Sony ARW or Canon CR2/CR3 photos, or a folder. Add photos appends to an editable queue, deduplicates URIs, and reports invalid selections without discarding valid ones. Remove items before conversion; a single remaining photo restores its embedded camera preview. Selected URIs survive activity recreation. Folder batches process supported files directly in the selected folder, excluding nested subfolders.

Conversion is local and sequential, with a visible foreground notification, cancellation, per-file outcomes, and retry for failed files. Originals are untouched and existing JPEGs are skipped. Develop RAW and Camera look support Standard/Original resolution, JPEG/WebP/AVIF output (AVIF on Android 14+), sRGB or Display P3, and optional metadata preservation.

See the [current screenshot gallery](../../README.md#android-app-screenshots) and download the [signed 0.0.20-alpha APK and checksums](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.20-alpha). The gallery was captured on 0.0.11-alpha; captures for 0.0.12–0.0.20 are pending.

0.0.20-alpha was signed by the release workflow and smoke-tested by the maintainer on a physical phone. Multi-device performance and real-camera batch validation remain pending.

## Build

Use Android Studio, SDK Platform 37.0, NDK 30.0.16248370, and CMake 3.22.1. Run `gradlew.bat` on Windows or `./gradlew` on macOS/Linux. Local checks: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`; device tests: `:app:connectedDebugAndroidTest`. Supply `-PversionName=<version> -PversionCode=<code>` for a versioned build. Release signing reads ANDROID_KEYSTORE_FILE and ANDROID_SIGNING_PASSWORD from the environment; never commit signing material. The build fetches pinned LibRaw source; conversion is offline.

## License

Project-owned source uses [MPL-2.0](../../LICENSE). LibRaw, Geist, and Lucide notices remain under `app/src/main/assets/licenses`. See [contribution guidance](../../CONTRIBUTING.md) and [security reporting](../../SECURITY.md).
