# Raw Photo Converter for Android

Native Android companion built with Kotlin, Jetpack Compose, Storage Access Framework, and a LibRaw/NDK engine.

## Android 0.0.10-alpha

The signed alpha selects one RAW photo at a time or batches supported files directly inside a selected folder. Single-photo previews use the embedded camera JPEG. The filename and RAW size are centered at the bottom of the preview; a small, subdued index sits at the lower-left. Changing the selected photo during its preview transition is supported.

The app develops Sony ARW and Canon CR2/CR3 locally, preserves supported metadata, saves through SAF, and supports sequential folder batches, cancellation, and retry for failed files. Originals remain unchanged. Folder batches do not include nested subfolders or show per-photo previews.

Download the signed APK and checksums from the [0.0.10-alpha release](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.10-alpha). Physical-device validation is pending.

## Build

Requirements: Android Studio, Android SDK Platform 37, NDK 30.0.16248370, and CMake 3.22.1. Open this directory in Android Studio and sync, or use `gradlew.bat` on Windows / `./gradlew` on macOS or Linux. The Gradle build downloads checksum-pinned LibRaw 0.22.2 source; conversion itself is offline.

## License

Project-owned source uses MPL-2.0; see the repository [LICENSE](../../LICENSE). LibRaw, Geist, Lucide, and other third-party components retain their own terms. Their included notices are under `app/src/main/assets/licenses`.
