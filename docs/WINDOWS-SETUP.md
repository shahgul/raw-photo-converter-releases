# Windows first-time setup

Raw Photo Converter develops RAWs with local tools. Until those tools are bundled in the installer, install them once on the PC, then open the app.

## What you need

| Tool | Command on PATH | Used for |
| --- | --- | --- |
| [darktable](https://www.darktable.org/install/) | `darktable-cli` | Develop RAW → TIFF |
| [ImageMagick 7+](https://imagemagick.org/script/download.php#windows) | `magick` | Size / quality optimize |
| [ExifTool](https://exiftool.org/) | `exiftool` | Restore metadata |

The desktop app never uploads photos. Originals stay untouched.

## Install (about 10 minutes)

1. **darktable**  
   Install from the official Windows build. Confirm `darktable-cli` is on PATH (open a **new** PowerShell after install).

2. **ImageMagick 7**  
   During setup, enable **Add application directory to your system path**. Prefer the `magick` CLI (ImageMagick 7).

3. **ExifTool**  
   Download the Windows package. Rename `exiftool(-k).exe` to `exiftool.exe` and put that folder on PATH.

4. **Restart** Raw Photo Converter (fully quit, then open again) so it re-reads PATH.

## Verify in PowerShell

```powershell
darktable-cli --version
magick -version
exiftool -ver
```

Each command should print a version. If one fails with “not recognized,” fix PATH or reopen the terminal, then restart the app.

## In the app

When a tool is missing, the Quiet Darkroom UI shows a **First-time setup** panel with Get it links and a **Recheck tools** button. Chrome status will say **See setup below** until all three report ready.

When the footer shows darktable, ImageMagick, and ExifTool as ready, pick a folder of RAW files and Develop.

## Still stuck?

- Use a new PowerShell window after changing PATH.
- Prefer user or system PATH that includes the install folders.
- ExifTool must be named `exiftool.exe`, not `exiftool(-k).exe`.
- ImageMagick 6-only installs may expose `convert` instead of `magick`; install ImageMagick 7.

Bundling these tools inside the NSIS installer is tracked separately and needs a license/size audit first. See [apps/desktop/PACKAGING.md](../apps/desktop/PACKAGING.md).
