# Raw Photo Converter

A local-first Android RAW photo converter for Sony ARW and Canon CR2/CR3 files.

## Current public version

**Android 0.0.9-alpha**

This repository is a curated public source snapshot. Ongoing development stays in the private development repository, and this snapshot changes only for an explicitly published version.

The reviewed source snapshot is pinned to private commit 685032b03ca37fc746d549214bc2b39e7158e532. The Android implementation and signed APK are from private commit b1c1194f69419514f0edfff973c16bd0a076202f.

## What it does

- develops Sony ARW and Canon CR2/CR3 files locally on Android
- offers the largest supported embedded camera JPEG as Camera look
- preserves source aspect ratio and never crops implicitly
- supports Original resolution or a 4000 px long-edge limit
- targets JPEG size while respecting a Q80 quality floor
- preserves supported camera, lens, exposure, GPS, and copyright metadata
- normalizes output orientation
- supports single files and sequential folder batches
- retries individual or all failed files using the original folder and conversion recipe
- uses Android Storage Access Framework without broad storage permission
- keeps processing on-device; originals are not modified

## Alpha status

Android 0.0.9-alpha fixes camera-preview loading from SAF providers such as Downloads by staging the stream in a bounded temporary cache file, then deleting it after decoding. Local validation passed 13 unit tests and 20 emulator instrumentation tests. Three Canon CR3 previews loaded, including from the Downloads picker; Camera look conversion saved a 4000 x 2667 JPEG and left the original unchanged. Physical-device validation remains pending.

Download the signed APK and checksum bundle from the public Android 0.0.9-alpha prerelease: https://github.com/shahgul/raw-photo-converter-releases/releases/tag/android-v0.0.9-alpha. APK binaries are distributed through Releases and are not committed to the source tree.

## Repository policy

Public versions are intentionally published snapshots pinned to reviewed private source commits. Do not assume main represents unreleased development work.

## Third-party components

The Android app uses LibRaw, Geist, and Lucide. Their attribution and license notices are included with the app source under apps/android/app/src/main/assets/licenses.

## Project license and support

Project-owned source is licensed under the Mozilla Public License 2.0 (MPL-2.0); see LICENSE. Third-party components retain their own licenses and notices. The converter core remains free to use. Optional voluntary Buy Me a Coffee support is planned and will not unlock features.