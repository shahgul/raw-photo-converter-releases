# Contributing

Raw Photo Converter's public repository contains reviewed Android source snapshots and signed releases. Development takes place in the maintainer's separate development repository. Public snapshots change only for an explicitly published version; coordinate code contributions with the maintainer before opening a source-change pull request against a snapshot.

For an Android bug report, include the app version, phone model, Android version, camera and RAW format, storage provider, conversion mode/resolution, reproduction steps, and the app's copied result report. Describe expected and actual output, including orientation and metadata behavior. Remove personal paths, GPS data, and other sensitive information from reports. Share sample photographs only when you have permission to do so.

Project-owned contributions use MPL-2.0. Preserve existing license notices and include attribution for third-party code or assets. Conversion must remain local, preserve aspect ratio without implicit cropping, leave originals untouched, and use Storage Access Framework for normal storage access.

For Android changes, run the existing unit tests, lint, and relevant device tests. Update authentic screenshots when UI or workflow changes affect the gallery. A successful build alone does not establish physical-device correctness or performance.
