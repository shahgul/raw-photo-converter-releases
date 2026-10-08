# Raw Photo Converter

A local-first Android RAW photo converter for Sony ARW and Canon CR2/CR3 files.

## Current public version

**Android 0.0.8-alpha**

This repository is a curated public source snapshot of the tested alpha. It is **not** the development repository and does not automatically track ongoing private development.

The `0.0.8-alpha` source is pinned to private development commit:

`fea7ba94bfe2c48c225e48e3647c93f312d602a8`

The signed APK was built from Android implementation commit `3d421ae48ac516a0cf3d85b944aa40bec86cd559`; the pinned source commit adds the selected license and publication documentation without changing the app implementation.

## What it does

- develops Sony ARW and Canon CR2/CR3 files locally on Android
- offers the camera embedded-JPEG path as an alternative
- keeps the original aspect ratio and never crops implicitly
- supports original-resolution or 4000 px long-edge output
- targets JPEG size while respecting a Q80 quality floor
- preserves supported camera/lens/exposure/GPS/copyright metadata
- normalizes output orientation
- supports single files and sequential folder batches
- retries individual or all failed files using the original folder and conversion recipe
- shows output behavior in an information dialog without shifting the bottom controls
- uses the same square launcher icon as the Windows app
- uses Android Storage Access Framework rather than broad storage permission
- keeps processing on-device; originals are not modified

## Alpha status

This is an early tester build. The core conversion path, native builds, unit tests, and emulator instrumentation are working. Three Canon CR3 samples and failed-file recovery were verified end to end on an API 35 x86_64 emulator; physical-device validation remains pending. Feedback from different cameras, lenses, Android phones, storage providers, portrait/landscape files, and larger batches is especially useful.

For installable builds, use this repository's [public GitHub prerelease](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.8-alpha). APK binaries should not be committed directly to the source tree.

## Repository policy

Public versions are intentionally published snapshots. Ongoing feature development happens privately. This repository changes only when the maintainer explicitly publishes a reviewed version.

Please do not assume `main` represents unreleased development work.

## Third-party components

The Android app uses third-party components including LibRaw, Geist, and Lucide. Their attribution/license material is included with the app source where applicable.

## Project license

Project-owned source code is licensed under the [Mozilla Public License 2.0 (MPL-2.0)](https://www.mozilla.org/MPL/2.0/); see [LICENSE](LICENSE). Third-party components retain their own licenses and notices. The converter core remains free to use; optional voluntary Buy Me a Coffee support is planned and will not unlock features. A support link will be added after the creator page is set up.
