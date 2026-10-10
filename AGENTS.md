# AGENTS.md

## Public snapshot repository — do not develop here

This repository is the curated public source and release repository for Raw Photo Converter.

It is not the development source of truth.

Rules for every coding agent:

- Do not perform normal feature development, bug fixing, refactoring, UI iteration, dependency upgrades, or experiments in this repository.
- Ongoing development belongs in the maintainer's separate development repository.
- Do not sync from the private repository automatically.
- Modify this public repository only when Shahgul explicitly asks to publish, sync, or prepare a public version.
- Every public version must be pinned to an exact reviewed private commit/tag.
- Never copy private Git history wholesale. Publish a curated source snapshot.
- Never commit APK binaries into the source tree. Distribute signed APKs through GitHub Releases with checksums.
- Before publishing a newer snapshot, audit secrets, internal/private references, third-party licensing/attribution, and release notes.
- If asked to continue, fix, implement, or start the next task while operating in this repository, stop and move the work to the development repository unless Shahgul explicitly says the public snapshot itself must be changed.

## Android screenshot maintenance

- The canonical Android screenshot gallery lives in `docs/screenshots/`, with its ordered presentation in the root `README.md`. The Android README links to that gallery.
- When an Android feature, screen, navigation path, visual design, action, or result changes, review whether existing screenshots are now misleading or whether a new screen/state needs coverage. Treat this as part of the Android change's documentation and release checklist, not optional polish.
- If a screenshot is affected, capture an authentic image from the current Android app, replace outdated images or add appropriately named ones, update gallery order/captions/alt text, and verify all referenced paths render. Never use fabricated screenshots or claim screenshots show behavior not present in the build.
- If updated screenshots cannot be captured during development, explicitly record which screenshots are stale or missing in the change summary/release checklist and request fresh captures before public publication; do not silently keep inaccurate images.
- Public screenshots and README updates require an explicit user-requested publication or documentation sync. The public repository is not automatically mirrored from private development. During an authorized public update, ensure the screenshots accurately represent the public APK/source version and keep both READMEs and screenshot paths consistent.

## Current snapshot

- Product: Raw Photo Converter for Android
- Public version: 0.0.10-alpha
- Private source commit: a55410c536bb57a750b93607a882059b77187880
- Android implementation and signed APK commit: 57b7a6138a3b4598a9f2cf42e42f3c82b85aedc5
- Project-owned code license: MPL-2.0; keep third-party notices separate
- Publication model: explicit/manual only
