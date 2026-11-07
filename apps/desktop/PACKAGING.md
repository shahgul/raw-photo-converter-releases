# Windows packaging

NSIS installer bundling is enabled in `src-tauri/tauri.conf.json` (`bundle.targets: ["nsis"]`).

The desktop window uses `decorations: false` with Quiet Darkroom caption controls in the React chrome. Bundle icons come from `src-tauri/icons/` (source SVG `app-icon.svg`; option explorations under `icons/options/`). That SVG is the product-wide mark (Windows + Android). After regenerating, copy `icons/android/mipmap-*` into `apps/android/app/src/main/res/` (see `apps/android/branding/`). Regenerate the full icon set with:

```bash
cd apps/desktop
magick -background none src-tauri/icons/app-icon.svg -resize 1024x1024 src-tauri/icons/app-icon-1024.png
npx @tauri-apps/cli icon src-tauri/icons/app-icon-1024.png -o src-tauri/icons --ios-color "#0C0C0D"
```

## Local unsigned installer

```bash
cd apps/desktop
npm run tauri -- build
```

Output lands under `apps/desktop/src-tauri/target/release/bundle/nsis/`.

This builds an **unsigned** installer suitable for private smoke tests. It does not establish release readiness.

## Signing (required before public release)

- Use a real Authenticode certificate; do not commit keys or passwords.
- Configure signing via Tauri / OS env (`TAURI_SIGNING_*` or Windows signtool) outside the repo.
- Keep signing material out of Git (same discipline as Android release keys).

## Dependency baseline (private)

Desktop stack is kept on current stable Tauri **2.x** (not 3 alpha), Vite **8.3.6+**, React 19.3, Motion 14. Rust lockfile is refreshed with `cargo update`; `cargo audit` and `npm audit` should stay clean of known vulnerabilities. TypeScript stays on 6.x until a deliberate 7 migration.

## Native tools

Native tools (darktable-cli, ImageMagick, ExifTool) are still expected on PATH for conversion. The installer ships the app shell only.

User-facing first-run help:

- In-app **First-time setup** panel when doctor reports missing tools
- Written guide: [docs/WINDOWS-SETUP.md](../../docs/WINDOWS-SETUP.md)

Bundling `darktable-cli` (and peers) as sidecars is a future option only after a redistribution/license audit: darktable is GPL-3, binaries are large, and update/security maintenance must be planned. Do not silently vendor those tools into the NSIS payload without that audit.
