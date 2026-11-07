use anyhow::{anyhow, Context, Result};
use serde::{Deserialize, Serialize};
use std::{
    ffi::OsStr,
    fs,
    path::{Path, PathBuf},
    process::{Command, Stdio},
    sync::{
        atomic::{AtomicBool, Ordering},
        Mutex,
    },
    thread,
    time::{Duration, Instant},
};

const TEMP_DIR_NAME: &str = ".raw-photo-converter-temp";

/// Cooperative cancellation token shared across a batch and its child tools.
pub type CancelFlag = AtomicBool;

#[derive(Debug, Clone, Copy)]
pub struct Cancelled;

impl std::fmt::Display for Cancelled {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "Conversion cancelled")
    }
}

impl std::error::Error for Cancelled {}

pub fn is_cancelled(error: &anyhow::Error) -> bool {
    error.downcast_ref::<Cancelled>().is_some()
}

fn check_cancelled(cancel: &CancelFlag) -> Result<()> {
    if cancel.load(Ordering::Relaxed) {
        Err(Cancelled.into())
    } else {
        Ok(())
    }
}

/// Matches Android `ConversionMode`: full RAW develop vs embedded camera JPEG.
#[derive(Debug, Clone, Copy, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum ConversionMode {
    #[default]
    DevelopRaw,
    CameraLook,
}

/// Matches Android `OutputFormat`.
#[derive(Debug, Clone, Copy, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum OutputFormat {
    #[default]
    Jpeg,
    Webp,
    Avif,
}

impl OutputFormat {
    pub fn extension(self) -> &'static str {
        match self {
            Self::Jpeg => "jpg",
            Self::Webp => "webp",
            Self::Avif => "avif",
        }
    }

    pub fn label(self) -> &'static str {
        match self {
            Self::Jpeg => "JPEG",
            Self::Webp => "WebP",
            Self::Avif => "AVIF",
        }
    }
}

/// Matches Android `ColorGamut`.
#[derive(Debug, Clone, Copy, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum ColorGamut {
    #[default]
    Srgb,
    DisplayP3,
}

impl ColorGamut {
    pub fn label(self) -> &'static str {
        match self {
            Self::Srgb => "sRGB",
            Self::DisplayP3 => "Display P3",
        }
    }

    fn magick_colorspace(self) -> &'static str {
        match self {
            Self::Srgb => "sRGB",
            Self::DisplayP3 => "DisplayP3",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ConverterSettings {
    pub target_size_mb: f64,
    /// Long-edge cap in px. `0` keeps the available resolution (Android Original).
    pub max_long_edge: u32,
    pub min_quality: u8,
    pub max_quality: u8,
    pub preserve_metadata: bool,
    pub skip_existing: bool,
    #[serde(default)]
    pub mode: ConversionMode,
    #[serde(default)]
    pub format: OutputFormat,
    #[serde(default)]
    pub color_gamut: ColorGamut,
    /// Stem template before the format extension. Tokens: `{name}`, `{edge}`, `{mode}`.
    #[serde(default = "default_name_template")]
    pub name_template: String,
    /// Bounded tool workers. `1` (default) is sequential; `2` is the measured ceiling.
    #[serde(default = "default_max_parallel")]
    pub max_parallel: u8,
    /// Extra clockwise rotation applied at encode time after the engine uprights the frame.
    /// Allowed: 0, 90, 180, 270. Preview and Develop share this value.
    #[serde(default)]
    pub rotate_degrees: u16,
}

fn default_name_template() -> String {
    "{name}".into()
}

fn default_max_parallel() -> u8 {
    1
}

const SUPPORTED_RAW_EXTENSIONS: [&str; 3] = ["arw", "cr2", "cr3"];

impl Default for ConverterSettings {
    fn default() -> Self {
        Self {
            target_size_mb: 3.0,
            max_long_edge: 4000,
            min_quality: 80,
            max_quality: 96,
            preserve_metadata: true,
            skip_existing: true,
            mode: ConversionMode::DevelopRaw,
            format: OutputFormat::Jpeg,
            color_gamut: ColorGamut::Srgb,
            name_template: default_name_template(),
            max_parallel: default_max_parallel(),
            rotate_degrees: 0,
        }
    }
}

/// Render the output stem from a RAW filename, matching Android `ConversionPolicy.outputStem`.
pub fn output_stem(
    source_name: &str,
    template: &str,
    max_long_edge: u32,
    mode: ConversionMode,
) -> Result<String> {
    let ext = Path::new(source_name)
        .extension()
        .and_then(OsStr::to_str)
        .unwrap_or_default();
    if !SUPPORTED_RAW_EXTENSIONS
        .iter()
        .any(|supported| ext.eq_ignore_ascii_case(supported))
    {
        return Err(anyhow!(
            "Select a supported RAW file (.ARW, .CR2 or .CR3)."
        ));
    }

    let name = Path::new(source_name)
        .file_stem()
        .and_then(OsStr::to_str)
        .ok_or_else(|| anyhow!("Invalid RAW filename."))?;
    if name.is_empty() || name.contains('/') || name.contains('\\') {
        return Err(anyhow!("Invalid RAW filename."));
    }

    let edge = if max_long_edge == 0 {
        "original".to_owned()
    } else {
        max_long_edge.to_string()
    };
    let mode_token = match mode {
        ConversionMode::DevelopRaw => "develop",
        ConversionMode::CameraLook => "camera",
    };

    let rendered = template
        .replace("{name}", name)
        .replace("{edge}", &edge)
        .replace("{mode}", mode_token);
    let rendered = rendered
        .chars()
        .map(|ch| match ch {
            '\\' | '/' | ':' | '*' | '?' | '"' | '<' | '>' | '|' => '_',
            other => other,
        })
        .collect::<String>();
    let rendered = rendered.trim().trim_matches('.').to_owned();
    if rendered.is_empty() {
        return Err(anyhow!("Filename template produced an empty name."));
    }
    if rendered.contains('/') || rendered.contains('\\') {
        return Err(anyhow!(
            "Filename template cannot include path separators."
        ));
    }
    Ok(rendered)
}

pub fn output_name(source_name: &str, settings: &ConverterSettings) -> Result<String> {
    Ok(format!(
        "{}.{}",
        output_stem(
            source_name,
            &settings.name_template,
            settings.max_long_edge,
            settings.mode
        )?,
        settings.format.extension()
    ))
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ToolStatus {
    pub name: &'static str,
    pub command: &'static str,
    pub available: bool,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RawFileInfo {
    pub path: PathBuf,
    pub name: String,
    pub size_bytes: u64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ConversionResult {
    pub source: PathBuf,
    pub output: PathBuf,
    pub skipped: bool,
    pub quality: Option<u8>,
    pub size_bytes: Option<u64>,
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub target_met: Option<bool>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ProgressEvent {
    pub index: usize,
    pub total: usize,
    pub file: String,
    pub state: String,
    pub phase: Option<String>,
    /// Measured wall time for `phase` when that phase just finished — never invented.
    pub phase_elapsed_ms: Option<u64>,
    pub quality: Option<u8>,
    pub size_bytes: Option<u64>,
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub target_met: Option<bool>,
    pub error: Option<String>,
    /// Absolute output path when a file finishes (done / skipped).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub output: Option<PathBuf>,
}

fn progress_event(
    index: usize,
    total: usize,
    file: String,
    state: &str,
) -> ProgressEvent {
    ProgressEvent {
        index,
        total,
        file,
        state: state.into(),
        phase: None,
        phase_elapsed_ms: None,
        quality: None,
        size_bytes: None,
        width: None,
        height: None,
        target_met: None,
        error: None,
        output: None,
    }
}

#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TempRecovery {
    pub folders_cleared: usize,
    pub files_removed: usize,
}

/// Remove leftover `.raw-photo-converter-temp` work files under unique source folders.
///
/// Safe after crash/kill: never touches RAW originals or finalized outputs. Called
/// automatically before each batch; also available for explicit recovery.
pub fn recover_stale_temps(sources: &[PathBuf]) -> TempRecovery {
    let mut parents = Vec::new();
    for source in sources {
        if let Some(parent) = source.parent() {
            let parent = parent.to_path_buf();
            if !parents.iter().any(|existing: &PathBuf| existing == &parent) {
                parents.push(parent);
            }
        }
    }

    let mut recovery = TempRecovery::default();
    for parent in parents {
        let cleared = clear_temp_dir(&parent.join(TEMP_DIR_NAME));
        if cleared > 0 {
            recovery.folders_cleared += 1;
            recovery.files_removed += cleared;
        }
    }
    recovery
}

fn clear_temp_dir(temp_dir: &Path) -> usize {
    if !temp_dir.is_dir() {
        return 0;
    }

    let mut removed = 0usize;
    if let Ok(entries) = fs::read_dir(temp_dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            let ok = if path.is_dir() {
                fs::remove_dir_all(&path).is_ok()
            } else {
                fs::remove_file(&path).is_ok()
            };
            if ok {
                removed += 1;
            }
        }
    }
    remove_dir_if_empty(temp_dir);
    removed
}

/// Measured phase wall times for a batch — only real Instant samples, never smoothed.
#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PhaseTimingTotals {
    pub developing_ms: u64,
    pub optimizing_ms: u64,
    pub metadata_ms: u64,
    pub saving_ms: u64,
}

impl PhaseTimingTotals {
    fn add(&mut self, phase: &str, ms: u64) {
        match phase {
            "developing" => self.developing_ms = self.developing_ms.saturating_add(ms),
            "optimizing" => self.optimizing_ms = self.optimizing_ms.saturating_add(ms),
            "metadata" => self.metadata_ms = self.metadata_ms.saturating_add(ms),
            "saving" => self.saving_ms = self.saving_ms.saturating_add(ms),
            _ => {}
        }
    }

    fn merge(&mut self, other: &PhaseTimingTotals) {
        self.developing_ms = self.developing_ms.saturating_add(other.developing_ms);
        self.optimizing_ms = self.optimizing_ms.saturating_add(other.optimizing_ms);
        self.metadata_ms = self.metadata_ms.saturating_add(other.metadata_ms);
        self.saving_ms = self.saving_ms.saturating_add(other.saving_ms);
    }

    fn has_samples(&self) -> bool {
        self.developing_ms > 0
            || self.optimizing_ms > 0
            || self.metadata_ms > 0
            || self.saving_ms > 0
    }
}

#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BatchSummary {
    pub converted: usize,
    pub skipped: usize,
    pub failed: usize,
    /// True when the batch stopped early because the user cancelled.
    #[serde(default)]
    pub cancelled: bool,
    /// Stale temp work files removed before this batch (crash/interrupt recovery).
    #[serde(default)]
    pub recovered_temp_files: usize,
    /// Wall-clock batch duration from first file to last event.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub elapsed_ms: Option<u64>,
    /// Sum of measured phase durations (may overlap when max_parallel > 1).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub phase_timings: Option<PhaseTimingTotals>,
}

/// Default Windows/CLI export folder beside the shoot: `{folder}/JPG`.
pub fn default_output_dir(folder: &Path) -> PathBuf {
    folder.join("JPG")
}

/// Camera/lens/exposure metadata for the Inspect photo-details panel.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RawMetadata {
    pub camera: Option<String>,
    pub lens: Option<String>,
    pub iso: Option<String>,
    pub exposure: Option<String>,
    pub aperture: Option<String>,
    pub focal_length: Option<String>,
    pub date_time_original: Option<String>,
    pub orientation: Option<i32>,
    pub make: Option<String>,
}

/// Read useful EXIF fields from a RAW via ExifTool (matches Android RawMetadata).
pub fn inspect_raw_metadata(source: &Path) -> Result<RawMetadata> {
    let source = source
        .canonicalize()
        .with_context(|| format!("source does not exist: {}", source.display()))?;

    let output = Command::new("exiftool")
        .arg("-json")
        .arg("-n")
        .arg("-Model")
        .arg("-Make")
        .arg("-LensModel")
        .arg("-ISO")
        .arg("-ExposureTime")
        .arg("-FNumber")
        .arg("-FocalLength")
        .arg("-DateTimeOriginal")
        .arg("-Orientation")
        .arg(&source)
        .output()
        .with_context(|| format!("failed to inspect {}", source.display()))?;

    if !output.status.success() {
        return Err(anyhow!(
            "ExifTool failed to inspect {}",
            source
                .file_name()
                .and_then(OsStr::to_str)
                .unwrap_or("RAW")
        ));
    }

    let rows: Vec<serde_json::Value> = serde_json::from_slice(&output.stdout)
        .with_context(|| "ExifTool returned invalid JSON metadata")?;
    let row = rows
        .into_iter()
        .next()
        .ok_or_else(|| anyhow!("ExifTool returned no metadata rows"))?;

    fn text(value: &serde_json::Value, key: &str) -> Option<String> {
        match value.get(key)? {
            serde_json::Value::Null => None,
            serde_json::Value::String(text) => {
                let trimmed = text.trim();
                if trimmed.is_empty() {
                    None
                } else {
                    Some(trimmed.to_owned())
                }
            }
            other => Some(other.to_string()),
        }
    }

    Ok(RawMetadata {
        camera: text(&row, "Model"),
        make: text(&row, "Make"),
        lens: text(&row, "LensModel"),
        iso: text(&row, "ISO"),
        exposure: text(&row, "ExposureTime"),
        aperture: text(&row, "FNumber"),
        focal_length: text(&row, "FocalLength"),
        date_time_original: text(&row, "DateTimeOriginal"),
        orientation: row.get("Orientation").and_then(|value| value.as_i64()).map(|v| v as i32),
    })
}

/// Bounded long edge for opt-in Compare Develop previews (matches Android).
pub const DEVELOP_PREVIEW_MAX_EDGE: u32 = 1200;

/// Extract and lightly downscale the embedded camera JPEG for UI inspect.
/// Temp folder for UI-only previews. Uses the system temp directory so selecting a
/// RAW on a read-only / sync / camera volume cannot fail (or hang) while creating
/// `.raw-photo-converter-temp` beside the original.
fn ui_preview_temp_dir(stem: &str) -> Result<PathBuf> {
    let dir = std::env::temp_dir().join("raw-photo-converter-ui").join(stem);
    fs::create_dir_all(&dir)?;
    Ok(dir)
}

pub fn camera_preview_jpeg(source: &Path, cancel: &CancelFlag) -> Result<Vec<u8>> {
    check_cancelled(cancel)?;
    let source = source
        .canonicalize()
        .with_context(|| format!("source does not exist: {}", source.display()))?;
    let stem = source
        .file_stem()
        .and_then(OsStr::to_str)
        .ok_or_else(|| anyhow!("source filename is not valid UTF-8"))?;

    let temp_dir = ui_preview_temp_dir(stem)?;
    let extracted = temp_dir.join("ui_camera.jpg");
    let sized = temp_dir.join("ui_camera_fit.jpg");

    let outcome = (|| {
        extract_embedded_jpeg(&source, &extracted, cancel)?;
        check_cancelled(cancel)?;
        // Bake EXIF orientation into pixels so the inspect preview matches encode
        // (browser EXIF display + CSS rotate would otherwise disagree with -rotate).
        run_checked(
            Command::new("magick")
                .arg(&extracted)
                .arg("-auto-orient")
                .arg("-resize")
                .arg("1280x1280>")
                .arg("-quality")
                .arg("82")
                .arg(&sized),
            "ImageMagick preview",
            cancel,
        )?;
        let bytes = fs::read(&sized).with_context(|| format!("failed to read {}", sized.display()))?;
        // Guard the WebView IPC path: oversized previews have blanked/crashed the shell.
        if bytes.len() > 2_500_000 {
            return Err(anyhow!(
                "camera preview is unexpectedly large ({} bytes)",
                bytes.len()
            ));
        }
        Ok(bytes)
    })();

    let _ = fs::remove_file(&extracted);
    let _ = fs::remove_file(&sized);
    remove_dir_if_empty(&temp_dir);
    outcome
}

/// Develop a bounded RAW preview JPEG for Camera/Develop compare.
pub fn develop_preview_jpeg(
    source: &Path,
    max_long_edge: u32,
    cancel: &CancelFlag,
) -> Result<Vec<u8>> {
    check_cancelled(cancel)?;
    let edge = if max_long_edge == 0 {
        DEVELOP_PREVIEW_MAX_EDGE
    } else {
        max_long_edge.min(DEVELOP_PREVIEW_MAX_EDGE)
    };

    let source = source
        .canonicalize()
        .with_context(|| format!("source does not exist: {}", source.display()))?;
    let source_dir = source
        .parent()
        .ok_or_else(|| anyhow!("source has no parent directory"))?;
    let stem = source
        .file_stem()
        .and_then(OsStr::to_str)
        .ok_or_else(|| anyhow!("source filename is not valid UTF-8"))?;

    // darktable still needs a work dir near the source for relative paths; keep that.
    // Final UI JPEG lands in system temp so the IPC payload never depends on source writes.
    let work_dir = source_dir.join(TEMP_DIR_NAME);
    fs::create_dir_all(&work_dir)?;
    let preview_dir = ui_preview_temp_dir(&format!("{stem}-develop"))?;

    let settings = ConverterSettings {
        max_long_edge: edge,
        mode: ConversionMode::DevelopRaw,
        ..ConverterSettings::default()
    };

    let outcome = (|| {
        let mut noop = |_: &str| {};
        let tiff = develop_raw_tiff(
            &source,
            source_dir,
            stem,
            &work_dir,
            &settings,
            &mut noop,
            cancel,
        )?;
        let preview = preview_dir.join("ui_develop.jpg");
        encode_output(
            &tiff,
            &preview,
            82,
            &ConverterSettings {
                format: OutputFormat::Jpeg,
                color_gamut: ColorGamut::Srgb,
                ..ConverterSettings::default()
            },
            cancel,
        )?;
        let bytes = fs::read(&preview).with_context(|| format!("failed to read {}", preview.display()))?;
        if bytes.len() > 2_500_000 {
            return Err(anyhow!(
                "develop preview is unexpectedly large ({} bytes)",
                bytes.len()
            ));
        }
        Ok(bytes)
    })();

    let _ = cleanup_stem_files(&work_dir, stem);
    remove_dir_if_empty(&work_dir);
    let _ = fs::remove_file(preview_dir.join("ui_develop.jpg"));
    remove_dir_if_empty(&preview_dir);
    outcome
}

/// Build a small JPEG data payload for a saved result thumbnail.
pub fn result_thumbnail_jpeg(path: &Path, max_edge: u32, cancel: &CancelFlag) -> Result<Vec<u8>> {
    check_cancelled(cancel)?;
    let path = path
        .canonicalize()
        .with_context(|| format!("image does not exist: {}", path.display()))?;
    let edge = max_edge.max(64).min(512);
    let temp = std::env::temp_dir().join(format!(
        "rpc-thumb-{}-{}.jpg",
        std::process::id(),
        path.file_stem()
            .and_then(OsStr::to_str)
            .unwrap_or("img")
    ));
    let geometry = format!("{edge}x{edge}>");
    let outcome = (|| {
        run_checked(
            Command::new("magick")
                .arg(&path)
                .arg("-resize")
                .arg(&geometry)
                .arg("-quality")
                .arg("78")
                .arg(&temp),
            "ImageMagick thumbnail",
            cancel,
        )?;
        fs::read(&temp).with_context(|| format!("failed to read {}", temp.display()))
    })();
    let _ = fs::remove_file(&temp);
    outcome
}

pub fn dependency_status() -> Vec<ToolStatus> {
    [
        ("darktable", "darktable-cli", "--version"),
        ("ImageMagick", "magick", "-version"),
        ("ExifTool", "exiftool", "-ver"),
    ]
    .into_iter()
    .map(|(name, command, version_arg)| ToolStatus {
        name,
        command,
        available: command_available(command, version_arg),
    })
    .collect()
}

pub fn ensure_dependencies() -> Result<()> {
    let missing = dependency_status()
        .into_iter()
        .filter(|tool| !tool.available)
        .map(|tool| tool.command)
        .collect::<Vec<_>>();

    if missing.is_empty() {
        Ok(())
    } else {
        Err(anyhow!("missing required tools: {}", missing.join(", ")))
    }
}

fn is_supported_raw_path(path: &Path) -> bool {
    path.extension()
        .and_then(OsStr::to_str)
        .map(|ext| {
            SUPPORTED_RAW_EXTENSIONS
                .iter()
                .any(|supported| ext.eq_ignore_ascii_case(supported))
        })
        .unwrap_or(false)
}

pub fn scan_raw_files(folder: &Path) -> Result<Vec<RawFileInfo>> {
    // Choosing/dropping a single RAW file path should not call read_dir on the file.
    if folder.is_file() {
        return raw_file_infos(&[folder.to_path_buf()]);
    }

    let mut files = fs::read_dir(folder)
        .with_context(|| format!("failed to read {}", folder.display()))?
        .filter_map(|entry| entry.ok().map(|entry| entry.path()))
        .filter(|path| is_supported_raw_path(path))
        .filter_map(|path| {
            let metadata = fs::metadata(&path).ok()?;
            let name = path.file_name()?.to_string_lossy().into_owned();
            Some(RawFileInfo {
                path,
                name,
                size_bytes: metadata.len(),
            })
        })
        .collect::<Vec<_>>();

    files.sort_by(|a, b| a.name.cmp(&b.name));
    Ok(files)
}

/// Build a deduped RAW queue from explicit file paths (multi-select / drag-drop).
pub fn raw_file_infos(paths: &[PathBuf]) -> Result<Vec<RawFileInfo>> {
    if paths.is_empty() {
        return Ok(Vec::new());
    }

    let mut files = Vec::new();
    let mut seen = std::collections::BTreeSet::new();

    for path in paths {
        if !is_supported_raw_path(path) {
            continue;
        }
        let canonical = path
            .canonicalize()
            .with_context(|| format!("RAW file does not exist: {}", path.display()))?;
        let key = canonical.to_string_lossy().to_ascii_lowercase();
        if !seen.insert(key) {
            continue;
        }
        let metadata = fs::metadata(&canonical)
            .with_context(|| format!("failed to read {}", canonical.display()))?;
        let name = canonical
            .file_name()
            .ok_or_else(|| anyhow!("RAW path has no filename"))?
            .to_string_lossy()
            .into_owned();
        files.push(RawFileInfo {
            path: canonical,
            name,
            size_bytes: metadata.len(),
        });
    }

    if files.is_empty() {
        return Err(anyhow!(
            "No supported RAW files were selected (.ARW, .CR2, .CR3)."
        ));
    }

    files.sort_by(|a, b| a.name.cmp(&b.name));
    Ok(files)
}

pub fn convert_folder<F>(
    folder: &Path,
    settings: &ConverterSettings,
    progress: F,
) -> Result<BatchSummary>
where
    F: FnMut(ProgressEvent) + Send,
{
    let output = default_output_dir(folder);
    convert_folder_to(folder, &output, settings, progress, &CancelFlag::new(false))
}

/// Convert every RAW in `folder` into `output_dir`, honouring `cancel`.
pub fn convert_folder_to<F>(
    folder: &Path,
    output_dir: &Path,
    settings: &ConverterSettings,
    progress: F,
    cancel: &CancelFlag,
) -> Result<BatchSummary>
where
    F: FnMut(ProgressEvent) + Send,
{
    let files = scan_raw_files(folder)?;
    let sources = files.into_iter().map(|file| file.path).collect::<Vec<_>>();
    // An explicit empty retry list returns before the tool check. A full-folder
    // run still reports missing darktable, ImageMagick, or ExifTool.
    if sources.is_empty() {
        validate_settings(settings)?;
        ensure_dependencies()?;
        return Ok(BatchSummary::default());
    }
    convert_paths_with_cancel(&sources, output_dir, settings, progress, cancel)
}

/// Convert an explicit ordered list of RAW paths into `output_dir`.
///
/// Progress `index`/`total` cover this list only, matching Android retry
/// attempts that report against the failed subset rather than the full shoot.
pub fn convert_paths<F>(
    sources: &[PathBuf],
    output_dir: &Path,
    settings: &ConverterSettings,
    progress: F,
) -> Result<BatchSummary>
where
    F: FnMut(ProgressEvent) + Send,
{
    convert_paths_with_cancel(
        sources,
        output_dir,
        settings,
        progress,
        &CancelFlag::new(false),
    )
}

/// Like [`convert_paths`], but stops early when `cancel` is set.
///
/// Completed outputs are kept. The unfinished file is not saved — matching
/// Android's cancel contract. `max_parallel` is clamped to 1..=2; default 1.
pub fn convert_paths_with_cancel<F>(
    sources: &[PathBuf],
    output_dir: &Path,
    settings: &ConverterSettings,
    progress: F,
    cancel: &CancelFlag,
) -> Result<BatchSummary>
where
    F: FnMut(ProgressEvent) + Send,
{
    validate_settings(settings)?;
    if sources.is_empty() {
        return Ok(BatchSummary::default());
    }
    if cancel.load(Ordering::Relaxed) {
        return Ok(BatchSummary {
            cancelled: true,
            ..BatchSummary::default()
        });
    }

    ensure_dependencies()?;

    let recovery = recover_stale_temps(sources);
    let workers = settings.max_parallel.clamp(1, 2) as usize;
    let batch_started = Instant::now();
    let mut summary = if workers == 1 {
        convert_paths_sequential(sources, output_dir, settings, progress, cancel)?
    } else {
        convert_paths_parallel(sources, output_dir, settings, progress, cancel, workers)?
    };
    summary.recovered_temp_files = recovery.files_removed;
    summary.elapsed_ms = Some(batch_started.elapsed().as_millis() as u64);
    Ok(summary)
}

fn convert_paths_sequential<F>(
    sources: &[PathBuf],
    output_dir: &Path,
    settings: &ConverterSettings,
    mut progress: F,
    cancel: &CancelFlag,
) -> Result<BatchSummary>
where
    F: FnMut(ProgressEvent),
{
    let total = sources.len();
    let mut summary = BatchSummary::default();
    let mut timings = PhaseTimingTotals::default();

    for (offset, source) in sources.iter().enumerate() {
        if check_cancelled(cancel).is_err() {
            summary.cancelled = true;
            break;
        }

        let index = offset + 1;
        let outcome = convert_indexed_file(
            source,
            index,
            total,
            output_dir,
            settings,
            cancel,
            &mut progress,
            &mut timings,
        );

        apply_file_outcome(&mut summary, &mut progress, outcome);
        if summary.cancelled {
            break;
        }
    }

    if timings.has_samples() {
        summary.phase_timings = Some(timings);
    }
    Ok(summary)
}

fn convert_paths_parallel<F>(
    sources: &[PathBuf],
    output_dir: &Path,
    settings: &ConverterSettings,
    progress: F,
    cancel: &CancelFlag,
    workers: usize,
) -> Result<BatchSummary>
where
    F: FnMut(ProgressEvent) + Send,
{
    let total = sources.len();
    let progress = Mutex::new(progress);
    let summary = Mutex::new(BatchSummary::default());
    let timings = Mutex::new(PhaseTimingTotals::default());
    let next_index = Mutex::new(0usize);

    thread::scope(|scope| {
        for _ in 0..workers {
            scope.spawn(|| {
                loop {
                    if cancel.load(Ordering::Relaxed) {
                        break;
                    }

                    let offset = {
                        let mut guard = next_index.lock().unwrap();
                        if *guard >= sources.len() {
                            break;
                        }
                        let value = *guard;
                        *guard += 1;
                        value
                    };

                    let source = &sources[offset];
                    let index = offset + 1;
                    let mut local_timings = PhaseTimingTotals::default();
                    let outcome = {
                        let mut emit = |event: ProgressEvent| {
                            (progress.lock().unwrap())(event);
                        };
                        convert_indexed_file(
                            source,
                            index,
                            total,
                            output_dir,
                            settings,
                            cancel,
                            &mut emit,
                            &mut local_timings,
                        )
                    };

                    {
                        let mut timing_guard = timings.lock().unwrap();
                        timing_guard.merge(&local_timings);
                    }

                    let mut summary_guard = summary.lock().unwrap();
                    let mut emit = |event: ProgressEvent| {
                        (progress.lock().unwrap())(event);
                    };
                    apply_file_outcome(&mut summary_guard, &mut emit, outcome);
                    if summary_guard.cancelled {
                        cancel.store(true, Ordering::Relaxed);
                        break;
                    }
                }
            });
        }
    });

    let mut summary = summary.into_inner().unwrap();
    let timings = timings.into_inner().unwrap();
    if cancel.load(Ordering::Relaxed) && !summary.cancelled {
        // Workers observed cancel between files; mark the batch cancelled.
        summary.cancelled = true;
    }
    if timings.has_samples() {
        summary.phase_timings = Some(timings);
    }
    Ok(summary)
}

enum FileOutcome {
    Skipped {
        file_name: String,
        index: usize,
        total: usize,
        result: ConversionResult,
    },
    Done {
        file_name: String,
        index: usize,
        total: usize,
        result: ConversionResult,
    },
    Cancelled {
        file_name: String,
        index: usize,
        total: usize,
    },
    Failed {
        file_name: String,
        index: usize,
        total: usize,
        error: String,
    },
}

fn convert_indexed_file<F>(
    source: &Path,
    index: usize,
    total: usize,
    output_dir: &Path,
    settings: &ConverterSettings,
    cancel: &CancelFlag,
    progress: &mut F,
    timings: &mut PhaseTimingTotals,
) -> FileOutcome
where
    F: FnMut(ProgressEvent),
{
    let file_name = source
        .file_name()
        .and_then(OsStr::to_str)
        .unwrap_or("unknown")
        .to_owned();

    progress(progress_event(
        index,
        total,
        file_name.clone(),
        "processing",
    ));

    let mut open_phase: Option<(String, Instant)> = None;
    let result = {
        let mut emit_phase = |phase: &str| {
            if let Some((previous, started)) = open_phase.take() {
                let ms = started.elapsed().as_millis() as u64;
                timings.add(&previous, ms);
                let mut done = progress_event(index, total, file_name.clone(), "processing");
                done.phase = Some(previous);
                done.phase_elapsed_ms = Some(ms);
                progress(done);
            }
            let mut event = progress_event(index, total, file_name.clone(), "processing");
            event.phase = Some(phase.into());
            progress(event);
            open_phase = Some((phase.into(), Instant::now()));
        };
        let outcome =
            convert_one_with_progress(source, output_dir, settings, &mut emit_phase, cancel);
        if let Some((previous, started)) = open_phase.take() {
            let ms = started.elapsed().as_millis() as u64;
            timings.add(&previous, ms);
            let mut done = progress_event(index, total, file_name.clone(), "processing");
            done.phase = Some(previous);
            done.phase_elapsed_ms = Some(ms);
            progress(done);
        }
        outcome
    };

    match result {
        Ok(result) if result.skipped => FileOutcome::Skipped {
            file_name,
            index,
            total,
            result,
        },
        Ok(result) => FileOutcome::Done {
            file_name,
            index,
            total,
            result,
        },
        Err(error) if is_cancelled(&error) => FileOutcome::Cancelled {
            file_name,
            index,
            total,
        },
        Err(error) => FileOutcome::Failed {
            file_name,
            index,
            total,
            error: format!("{error:#}"),
        },
    }
}

fn apply_file_outcome<F>(summary: &mut BatchSummary, progress: &mut F, outcome: FileOutcome)
where
    F: FnMut(ProgressEvent),
{
    match outcome {
        FileOutcome::Skipped {
            file_name,
            index,
            total,
            result,
        } => {
            summary.skipped += 1;
            let mut event = progress_event(index, total, file_name, "skipped");
            event.size_bytes = result.size_bytes;
            event.width = result.width;
            event.height = result.height;
            event.target_met = result.target_met;
            event.output = Some(result.output);
            progress(event);
        }
        FileOutcome::Done {
            file_name,
            index,
            total,
            result,
        } => {
            summary.converted += 1;
            let mut event = progress_event(index, total, file_name, "done");
            event.quality = result.quality;
            event.size_bytes = result.size_bytes;
            event.width = result.width;
            event.height = result.height;
            event.target_met = result.target_met;
            event.output = Some(result.output);
            progress(event);
        }
        FileOutcome::Cancelled {
            file_name,
            index,
            total,
        } => {
            summary.cancelled = true;
            let mut event = progress_event(index, total, file_name, "cancelled");
            event.phase = Some("cancelled".into());
            event.error = Some(
                "Completed outputs are kept. The unfinished file was not saved.".into(),
            );
            progress(event);
        }
        FileOutcome::Failed {
            file_name,
            index,
            total,
            error,
        } => {
            summary.failed += 1;
            let mut event = progress_event(index, total, file_name, "failed");
            event.error = Some(error);
            progress(event);
        }
    }
}

/// Resolve RAW filenames against a shoot folder for subset conversion / retry.
pub fn resolve_raw_sources(folder: &Path, names: &[String]) -> Result<Vec<PathBuf>> {
    if names.is_empty() {
        return Err(anyhow!("no RAW filenames were provided"));
    }

    let files = scan_raw_files(folder)?;
    let mut sources = Vec::with_capacity(names.len());

    for name in names {
        let matched = files
            .iter()
            .find(|file| file.name.eq_ignore_ascii_case(name))
            .ok_or_else(|| anyhow!("RAW file not found in folder: {name}"))?;
        sources.push(matched.path.clone());
    }

    Ok(sources)
}

pub fn convert_one(
    source: &Path,
    output_dir: &Path,
    settings: &ConverterSettings,
) -> Result<ConversionResult> {
    let mut noop = |_: &str| {};
    convert_one_with_progress(
        source,
        output_dir,
        settings,
        &mut noop,
        &CancelFlag::new(false),
    )
}

fn convert_one_with_progress<F>(
    source: &Path,
    output_dir: &Path,
    settings: &ConverterSettings,
    progress: &mut F,
    cancel: &CancelFlag,
) -> Result<ConversionResult>
where
    F: FnMut(&str),
{
    validate_settings(settings)?;
    check_cancelled(cancel)?;

    let source = source
        .canonicalize()
        .with_context(|| format!("source does not exist: {}", source.display()))?;

    let source_dir = source
        .parent()
        .ok_or_else(|| anyhow!("source has no parent directory"))?;

    let source_name = source
        .file_name()
        .and_then(OsStr::to_str)
        .ok_or_else(|| anyhow!("source filename is not valid UTF-8"))?;

    let stem = source
        .file_stem()
        .and_then(OsStr::to_str)
        .ok_or_else(|| anyhow!("source filename is not valid UTF-8"))?;

    let output_file_name = output_name(source_name, settings)?;

    fs::create_dir_all(output_dir)
        .with_context(|| format!("failed to create {}", output_dir.display()))?;

    let final_output = output_dir.join(&output_file_name);

    if settings.skip_existing && final_output.exists() {
        let (width, height) = image_dimensions(&final_output, cancel).unwrap_or((None, None));
        return Ok(ConversionResult {
            source,
            output: final_output.clone(),
            skipped: true,
            quality: None,
            size_bytes: fs::metadata(&final_output).ok().map(|m| m.len()),
            width,
            height,
            target_met: None,
        });
    }

    let temp_dir = source_dir.join(TEMP_DIR_NAME);
    fs::create_dir_all(&temp_dir)
        .with_context(|| format!("failed to create {}", temp_dir.display()))?;

    cleanup_stem_files(&temp_dir, stem)?;

    let outcome = convert_one_inner(
        &source,
        source_dir,
        stem,
        &temp_dir,
        &final_output,
        settings,
        progress,
        cancel,
    );

    let _ = cleanup_stem_files(&temp_dir, stem);
    remove_dir_if_empty(&temp_dir);

    outcome
}

fn convert_one_inner<F>(
    source: &Path,
    source_dir: &Path,
    stem: &str,
    temp_dir: &Path,
    final_output: &Path,
    settings: &ConverterSettings,
    progress: &mut F,
    cancel: &CancelFlag,
) -> Result<ConversionResult>
where
    F: FnMut(&str),
{
    let work_image = match settings.mode {
        ConversionMode::DevelopRaw => {
            develop_raw_tiff(source, source_dir, stem, temp_dir, settings, progress, cancel)?
        }
        ConversionMode::CameraLook => {
            prepare_camera_look(source, stem, temp_dir, settings, progress, cancel)?
        }
    };

    let ext = settings.format.extension();
    let candidate = temp_dir.join(format!("{stem}_candidate.{ext}"));
    let best = temp_dir.join(format!("{stem}_best.{ext}"));
    let target_bytes = (settings.target_size_mb * 1024.0 * 1024.0) as u64;

    progress("optimizing");
    let mut low = settings.min_quality as i16;
    let mut high = settings.max_quality as i16;
    let mut best_quality: Option<u8> = None;

    while low <= high {
        check_cancelled(cancel)?;
        let quality = ((low + high) / 2) as u8;
        encode_output(&work_image, &candidate, quality, settings, cancel)?;

        let bytes = fs::metadata(&candidate)?.len();

        if bytes <= target_bytes {
            fs::copy(&candidate, &best)?;
            best_quality = Some(quality);
            low = quality as i16 + 1;
        } else {
            high = quality as i16 - 1;
        }
    }

    let mut quality = best_quality.unwrap_or(settings.min_quality);

    if best_quality.is_none() {
        encode_output(&work_image, &best, quality, settings, cancel)?;
    }

    if settings.preserve_metadata {
        progress("metadata");
        restore_metadata(source, &best, settings.format, cancel)?;
    }

    while fs::metadata(&best)?.len() > target_bytes && quality > settings.min_quality {
        check_cancelled(cancel)?;
        progress("optimizing");
        quality -= 1;
        encode_output(&work_image, &best, quality, settings, cancel)?;
        if settings.preserve_metadata {
            progress("metadata");
            restore_metadata(source, &best, settings.format, cancel)?;
        }
    }

    check_cancelled(cancel)?;
    progress("saving");
    if final_output.exists() {
        fs::remove_file(final_output)?;
    }

    fs::rename(&best, final_output)
        .or_else(|_| {
            fs::copy(&best, final_output)?;
            fs::remove_file(&best)?;
            Ok::<_, std::io::Error>(())
        })
        .with_context(|| format!("failed to finalize {}", final_output.display()))?;

    let final_size = fs::metadata(final_output)?.len();
    let _ = fs::remove_file(&candidate);
    let (width, height) = image_dimensions(final_output, cancel).unwrap_or((None, None));

    Ok(ConversionResult {
        source: source.to_path_buf(),
        output: final_output.to_path_buf(),
        skipped: false,
        quality: Some(quality),
        size_bytes: Some(final_size),
        width,
        height,
        target_met: Some(final_size <= target_bytes),
    })
}

/// Read pixel size of a saved output via ImageMagick identify (`WxH`).
fn image_dimensions(path: &Path, cancel: &CancelFlag) -> Result<(Option<u32>, Option<u32>)> {
    let stdout = run_captured(
        Command::new("magick")
            .arg("identify")
            .arg("-format")
            .arg("%wx%h")
            .arg(path),
        "ImageMagick identify",
        cancel,
    )?;
    let text = String::from_utf8_lossy(&stdout);
    let text = text.trim();
    let (w, h) = text
        .split_once('x')
        .ok_or_else(|| anyhow!("unexpected identify output: {text}"))?;
    Ok((w.parse().ok(), h.parse().ok()))
}

fn develop_raw_tiff<F>(
    source: &Path,
    source_dir: &Path,
    stem: &str,
    temp_dir: &Path,
    settings: &ConverterSettings,
    progress: &mut F,
    cancel: &CancelFlag,
) -> Result<PathBuf>
where
    F: FnMut(&str),
{
    let input_name = source
        .file_name()
        .and_then(OsStr::to_str)
        .ok_or_else(|| anyhow!("source filename is not valid UTF-8"))?;

    let darktable_input = format!("./{input_name}");
    let darktable_output = format!("./{TEMP_DIR_NAME}");

    progress("developing");
    let mut command = Command::new("darktable-cli");
    command
        .current_dir(source_dir)
        .arg(&darktable_input)
        .arg(darktable_output)
        .arg("--out-ext")
        .arg("tif");

    // Android Original keeps available resolution; omit darktable size caps.
    if settings.max_long_edge > 0 {
        command
            .arg("--width")
            .arg(settings.max_long_edge.to_string())
            .arg("--height")
            .arg(settings.max_long_edge.to_string());
    }

    run_checked(&mut command, "darktable", cancel)?;

    newest_matching_tiff(temp_dir, stem)?
        .ok_or_else(|| anyhow!("darktable reported success but no TIFF was produced"))
}

fn prepare_camera_look<F>(
    source: &Path,
    stem: &str,
    temp_dir: &Path,
    settings: &ConverterSettings,
    progress: &mut F,
    cancel: &CancelFlag,
) -> Result<PathBuf>
where
    F: FnMut(&str),
{
    progress("developing");
    let embedded = temp_dir.join(format!("{stem}_camera.jpg"));
    extract_embedded_jpeg(source, &embedded, cancel)?;

    check_cancelled(cancel)?;
    // Upright storage pixels before resize/encode so user rotate matches inspect.
    let upright = temp_dir.join(format!("{stem}_camera_upright.tif"));
    let mut orient = Command::new("magick");
    orient.arg(&embedded).arg("-auto-orient");
    if settings.max_long_edge != 0 {
        let geometry = format!("{}x{}>", settings.max_long_edge, settings.max_long_edge);
        orient.arg("-resize").arg(geometry);
    }
    orient.arg(&upright);
    run_checked(&mut orient, "ImageMagick camera look", cancel)?;
    let _ = fs::remove_file(&embedded);
    Ok(upright)
}

fn extract_embedded_jpeg(source: &Path, dest: &Path, cancel: &CancelFlag) -> Result<()> {
    const TAGS: &[&str] = &["-PreviewImage", "-JpgFromRaw", "-OtherImage"];
    let _ = fs::remove_file(dest);

    for tag in TAGS {
        check_cancelled(cancel)?;
        let mut command = Command::new("exiftool");
        command.arg("-b").arg(tag).arg(source);
        let output = run_captured(&mut command, &format!("ExifTool {tag}"), cancel)?;

        if output.len() < 50_000 || output.get(0..2) != Some(&[0xFF, 0xD8]) {
            continue;
        }

        fs::write(dest, &output).with_context(|| format!("failed to write {}", dest.display()))?;
        return Ok(());
    }

    Err(anyhow!(
        "no usable embedded camera JPEG was found in {}",
        source
            .file_name()
            .and_then(OsStr::to_str)
            .unwrap_or("RAW")
    ))
}

pub fn validate_settings(settings: &ConverterSettings) -> Result<()> {
    if !(0.1..=100.0).contains(&settings.target_size_mb) {
        return Err(anyhow!("target size must be between 0.1 and 100 MB"));
    }
    if settings.max_long_edge != 0 && !(256..=8000).contains(&settings.max_long_edge) {
        return Err(anyhow!(
            "max long edge must be 0 (original) or between 256 and 8000 px"
        ));
    }
    if settings.min_quality < 1
        || settings.max_quality > 100
        || settings.min_quality > settings.max_quality
    {
        return Err(anyhow!("invalid quality range"));
    }
    if settings.name_template.trim().is_empty() {
        return Err(anyhow!("filename template cannot be blank"));
    }
    if !(1..=2).contains(&settings.max_parallel) {
        return Err(anyhow!("max parallel must be 1 or 2"));
    }
    if !matches!(settings.rotate_degrees, 0 | 90 | 180 | 270) {
        return Err(anyhow!("rotate degrees must be 0, 90, 180, or 270"));
    }
    Ok(())
}

fn command_available(command: &str, version_arg: &str) -> bool {
    Command::new(command)
        .arg(version_arg)
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .map(|status| status.success())
        .unwrap_or(false)
}

fn encode_output(
    input: &Path,
    output: &Path,
    quality: u8,
    settings: &ConverterSettings,
    cancel: &CancelFlag,
) -> Result<()> {
    let _ = fs::remove_file(output);

    // Prefer ImageMagick colorspace conversion over stripping profiles so
    // sRGB / Display P3 outputs carry a coherent ICC when the encoder supports it.
    let mut command = Command::new("magick");
    command
        .arg(input)
        // Match inspect: start from upright pixels, then apply the user's CW turn.
        .arg("-auto-orient")
        .arg("-colorspace")
        .arg(settings.color_gamut.magick_colorspace());

    // User-chosen preview orientation (0/90/180/270 CW), after the engine uprights pixels.
    if settings.rotate_degrees > 0 {
        command.arg("-rotate").arg(settings.rotate_degrees.to_string());
    }

    if settings.format == OutputFormat::Jpeg {
        command
            .arg("-sampling-factor")
            .arg("4:2:0")
            .arg("-interlace")
            .arg("Plane");
    }

    command
        .arg("-quality")
        .arg(quality.to_string())
        .arg(output);

    run_checked(&mut command, "ImageMagick", cancel)
}

fn restore_metadata(
    source: &Path,
    output: &Path,
    format: OutputFormat,
    cancel: &CancelFlag,
) -> Result<()> {
    run_checked(
        Command::new("exiftool")
            .arg("-overwrite_original")
            .arg("-TagsFromFile")
            .arg(source)
            .arg("-EXIF:All")
            .arg("-XMP:All")
            .arg("-IPTC:All")
            .arg(output),
        "ExifTool",
        cancel,
    )?;

    // Developed / resized pixels are already upright. Copying RAW Orientation
    // can rotate portrait JPEGs a second time in orientation-aware viewers.
    // WebP/AVIF orientation handling is uneven across tools, so normalize JPEG only.
    if format == OutputFormat::Jpeg {
        run_checked(
            Command::new("exiftool")
                .arg("-overwrite_original")
                .arg("-Orientation#=1")
                .arg(output),
            "ExifTool orientation",
            cancel,
        )?;
    }

    Ok(())
}

fn run_checked(command: &mut Command, label: &str, cancel: &CancelFlag) -> Result<()> {
    check_cancelled(cancel)?;
    command.stdout(Stdio::null()).stderr(Stdio::null());
    let mut child = command
        .spawn()
        .with_context(|| format!("failed to start {label}"))?;

    loop {
        if cancel.load(Ordering::Relaxed) {
            let _ = child.kill();
            let _ = child.wait();
            return Err(Cancelled.into());
        }

        match child
            .try_wait()
            .with_context(|| format!("failed while waiting for {label}"))?
        {
            Some(status) if status.success() => return Ok(()),
            Some(status) => {
                return Err(anyhow!("{label} failed with exit code {:?}", status.code()));
            }
            None => thread::sleep(Duration::from_millis(40)),
        }
    }
}

fn run_captured(command: &mut Command, label: &str, cancel: &CancelFlag) -> Result<Vec<u8>> {
    use std::io::Read;

    check_cancelled(cancel)?;
    command.stdout(Stdio::piped()).stderr(Stdio::null());
    let mut child = command
        .spawn()
        .with_context(|| format!("failed to start {label}"))?;

    let mut stdout_pipe = child
        .stdout
        .take()
        .ok_or_else(|| anyhow!("{label} stdout pipe missing"))?;
    let reader = thread::spawn(move || {
        let mut buffer = Vec::new();
        let _ = stdout_pipe.read_to_end(&mut buffer);
        buffer
    });

    let status = loop {
        if cancel.load(Ordering::Relaxed) {
            let _ = child.kill();
            let _ = child.wait();
            let _ = reader.join();
            return Err(Cancelled.into());
        }

        match child
            .try_wait()
            .with_context(|| format!("failed while waiting for {label}"))?
        {
            Some(status) => break status,
            None => thread::sleep(Duration::from_millis(40)),
        }
    };

    let stdout = reader
        .join()
        .map_err(|_| anyhow!("{label} stdout reader failed"))?;

    if status.success() {
        Ok(stdout)
    } else {
        Err(anyhow!("{label} failed with exit code {:?}", status.code()))
    }
}

fn newest_matching_tiff(folder: &Path, stem: &str) -> Result<Option<PathBuf>> {
    let mut matches = fs::read_dir(folder)?
        .filter_map(|entry| entry.ok())
        .map(|entry| entry.path())
        .filter(|path| {
            let name_matches = path
                .file_stem()
                .and_then(OsStr::to_str)
                .map(|name| name == stem || name.starts_with(&format!("{stem}_")))
                .unwrap_or(false);

            let ext_matches = path
                .extension()
                .and_then(OsStr::to_str)
                .map(|ext| ext.eq_ignore_ascii_case("tif") || ext.eq_ignore_ascii_case("tiff"))
                .unwrap_or(false);

            name_matches && ext_matches
        })
        .collect::<Vec<_>>();

    matches.sort_by_key(|path| {
        fs::metadata(path)
            .and_then(|metadata| metadata.modified())
            .ok()
    });

    Ok(matches.pop())
}

fn cleanup_stem_files(folder: &Path, stem: &str) -> Result<()> {
    if !folder.exists() {
        return Ok(());
    }

    for entry in fs::read_dir(folder)? {
        let path = entry?.path();
        let name = path.file_name().and_then(OsStr::to_str).unwrap_or_default();
        if name.starts_with(stem) {
            let _ = fs::remove_file(path);
        }
    }

    Ok(())
}

fn remove_dir_if_empty(folder: &Path) {
    let is_empty = fs::read_dir(folder)
        .map(|mut entries| entries.next().is_none())
        .unwrap_or(false);

    if is_empty {
        let _ = fs::remove_dir(folder);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    fn write_stub_raw(folder: &Path, name: &str) -> PathBuf {
        let path = folder.join(name);
        let mut file = fs::File::create(&path).expect("create stub RAW");
        writeln!(file, "stub").expect("write stub RAW");
        path
    }

    #[test]
    fn resolve_raw_sources_matches_names_case_insensitively() {
        let dir = std::env::temp_dir().join(format!(
            "rpc-resolve-{}",
            std::process::id()
        ));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        write_stub_raw(&dir, "Canon-eos-r-raw-00001.cr3");
        write_stub_raw(&dir, "Canon-eos-r-raw-00013.CR3");

        let sources = resolve_raw_sources(
            &dir,
            &["canon-eos-r-raw-00013.cr3".into(), "Canon-eos-r-raw-00001.cr3".into()],
        )
        .unwrap();

        assert_eq!(
            sources
                .iter()
                .map(|path| path.file_name().unwrap().to_string_lossy().into_owned())
                .collect::<Vec<_>>(),
            vec![
                "Canon-eos-r-raw-00013.CR3".to_string(),
                "Canon-eos-r-raw-00001.cr3".to_string()
            ]
        );

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn resolve_raw_sources_rejects_missing_names() {
        let dir = std::env::temp_dir().join(format!(
            "rpc-resolve-missing-{}",
            std::process::id()
        ));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        write_stub_raw(&dir, "keep.cr3");

        let error = resolve_raw_sources(&dir, &["missing.cr3".into()])
            .expect_err("missing name should fail");
        assert!(error.to_string().contains("missing.cr3"));

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn convert_folder_empty_directory_still_checks_dependencies() {
        let dir = std::env::temp_dir().join(format!("rpc-empty-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();

        let result = convert_folder(&dir, &ConverterSettings::default(), |_| {});
        if dependency_status().iter().all(|tool| tool.available) {
            let summary = result.expect("empty folder with tools installed");
            assert_eq!(summary.converted, 0);
            assert_eq!(summary.skipped, 0);
            assert_eq!(summary.failed, 0);
        } else {
            let error = result.expect_err("missing tools should be reported");
            assert!(error.to_string().contains("missing required tools"));
        }

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn convert_paths_returns_empty_summary_for_empty_list() {
        let summary = convert_paths(
            &[],
            Path::new("unused"),
            &ConverterSettings::default(),
            |_| {},
        )
        .unwrap();
        assert_eq!(summary.converted, 0);
        assert_eq!(summary.skipped, 0);
        assert_eq!(summary.failed, 0);
    }

    #[test]
    fn output_stem_matches_android_tokens() {
        assert_eq!(
            output_stem("DSC00135.ARW", "{name}", 4000, ConversionMode::DevelopRaw).unwrap(),
            "DSC00135"
        );
        assert_eq!(
            output_stem(
                "DSC00135.ARW",
                "{name}_{edge}_{mode}",
                0,
                ConversionMode::CameraLook
            )
            .unwrap(),
            "DSC00135_original_camera"
        );
        assert_eq!(
            output_name(
                "frame.cr3",
                &ConverterSettings {
                    format: OutputFormat::Webp,
                    name_template: "{name}_{mode}".into(),
                    ..ConverterSettings::default()
                }
            )
            .unwrap(),
            "frame_develop.webp"
        );
    }

    #[test]
    fn validate_settings_allows_original_resolution() {
        let mut settings = ConverterSettings::default();
        settings.max_long_edge = 0;
        validate_settings(&settings).unwrap();

        settings.max_long_edge = 100;
        assert!(validate_settings(&settings).is_err());
    }

    #[test]
    fn validate_settings_accepts_quarter_turn_rotation() {
        let mut settings = ConverterSettings::default();
        settings.rotate_degrees = 90;
        validate_settings(&settings).unwrap();
        settings.rotate_degrees = 45;
        assert!(validate_settings(&settings).is_err());
    }

    #[test]
    fn camera_preview_returns_error_not_panic_on_stub_raw() {
        let dir = std::env::temp_dir().join(format!("rpc-preview-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        let source = write_stub_raw(&dir, "stub.ARW");
        let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            camera_preview_jpeg(&source, &CancelFlag::new(false))
        }));
        assert!(result.is_ok(), "camera preview panicked");
        assert!(result.unwrap().is_err(), "stub RAW should not yield a JPEG");
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn scan_raw_files_accepts_single_raw_file_path() {
        let dir = std::env::temp_dir().join(format!("rpc-scan-file-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        let source = write_stub_raw(&dir, "solo.CR3");
        let files = scan_raw_files(&source).unwrap();
        assert_eq!(files.len(), 1);
        assert_eq!(files[0].name.to_ascii_lowercase(), "solo.cr3");
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn raw_file_infos_dedupes_and_rejects_non_raw() {
        let dir = std::env::temp_dir().join(format!("rpc-infos-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        let a = write_stub_raw(&dir, "a.ARW");
        let b = write_stub_raw(&dir, "b.cr3");
        write_stub_raw(&dir, "notes.txt");

        let files = raw_file_infos(&[a.clone(), b, a, dir.join("notes.txt")]).unwrap();
        assert_eq!(files.len(), 2);
        assert_eq!(files[0].name.to_ascii_lowercase(), "a.arw");
        assert_eq!(files[1].name.to_ascii_lowercase(), "b.cr3");

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn convert_paths_stops_before_work_when_already_cancelled() {
        let cancel = CancelFlag::new(true);
        let dir = std::env::temp_dir().join(format!("rpc-cancel-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        let source = write_stub_raw(&dir, "frame.arw");

        let summary = convert_paths_with_cancel(
            &[source],
            &dir.join("JPG"),
            &ConverterSettings::default(),
            |_| {},
            &cancel,
        )
        .unwrap();

        assert!(summary.cancelled);
        assert_eq!(summary.converted, 0);
        assert_eq!(summary.failed, 0);
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn recover_stale_temps_clears_work_dir_without_touching_raws() {
        let dir = std::env::temp_dir().join(format!("rpc-recover-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        let raw = write_stub_raw(&dir, "keep.ARW");
        let temp = dir.join(TEMP_DIR_NAME);
        fs::create_dir_all(&temp).unwrap();
        fs::write(temp.join("keep_candidate.jpg"), b"junk").unwrap();
        fs::write(temp.join("keep_best.jpg"), b"junk").unwrap();

        let recovery = recover_stale_temps(&[raw.clone()]);
        assert_eq!(recovery.folders_cleared, 1);
        assert_eq!(recovery.files_removed, 2);
        assert!(raw.is_file());
        assert!(!temp.join("keep_candidate.jpg").exists());
        assert!(!temp.exists() || fs::read_dir(&temp).unwrap().next().is_none());

        let _ = fs::remove_dir_all(&dir);
    }
}
