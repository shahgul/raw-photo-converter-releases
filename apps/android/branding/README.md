# Product icon (Android)

Canonical mark: Quiet Darkroom crop-frame (option 03) — corner brackets + safelight inner square on `#0C0C0D`.

Canonical SVG and 1024 px PNG for this public snapshot live in this folder. The private development tree also keeps a matching Windows/Tauri copy.

To refresh Android mipmaps from `app-icon.svg`:

```bash
magick -background none app-icon.svg -resize 1024x1024 app-icon-1024.png
# Then generate density mipmaps into app/src/main/res/mipmap-*
# Keep ic_launcher_background at #0C0C0D.
```
