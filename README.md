# Raw Photo Converter

A local-first Android RAW photo converter focused first on Sony ARW files.

## Current public version

**Android 0.0.7-alpha**

This repository is a curated public source snapshot of the tested alpha. It is **not** the development repository and does not automatically track ongoing private development.

The `0.0.7-alpha` source is pinned to private development commit:

`24d7cc18f6551fdf32eda4b621d6057532d69788`

## What it does

- develops Sony ARW files locally on Android
- offers the camera embedded-JPEG path as an alternative
- keeps the original aspect ratio and never crops implicitly
- supports original-resolution or 4000 px long-edge output
- targets JPEG size while respecting a Q80 quality floor
- preserves supported camera/lens/exposure/GPS/copyright metadata
- normalizes output orientation
- supports single files and sequential folder batches
- uses Android Storage Access Framework rather than broad storage permission
- keeps processing on-device; originals are not modified

## Alpha status

This is an early tester build. The core conversion path, native builds, unit tests, and emulator instrumentation are working, but broader real-device validation is still in progress. Feedback from different Sony cameras, lenses, Android phones, storage providers, portrait/landscape files, and larger batches is especially useful.

For installable builds, use this repository's [public GitHub prerelease](https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.7-alpha). APK binaries should not be committed directly to the source tree.

## Repository policy

Public versions are intentionally published snapshots. Ongoing feature development happens privately. This repository changes only when the maintainer explicitly publishes a reviewed version.

Please do not assume `main` represents unreleased development work.

## Third-party components

The Android app uses third-party components including LibRaw, Geist, and Lucide. Their attribution/license material is included with the app source where applicable.

## Project license

A project-wide license has not yet been selected. Until one is added, the source is published for transparency and review but no additional reuse rights are granted beyond rights provided by applicable law and the separately licensed third-party components.
