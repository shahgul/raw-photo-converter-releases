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

## Current snapshot

- Product: Raw Photo Converter for Android
- Public version: 0.0.9-alpha
- Private source commit: 685032b03ca37fc746d549214bc2b39e7158e532
- Android implementation commit: b1c1194f69419514f0edfff973c16bd0a076202f
- Project-owned code license: MPL-2.0; keep third-party notices separate
- Publication model: explicit/manual only