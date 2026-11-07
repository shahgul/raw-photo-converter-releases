use base64::{engine::general_purpose::STANDARD, Engine};
use raw_photo_converter_core::{
    camera_preview_jpeg, convert_paths_with_cancel, default_output_dir, dependency_status,
    develop_preview_jpeg, inspect_raw_metadata, raw_file_infos, result_thumbnail_jpeg,
    scan_raw_files, BatchSummary, CancelFlag, ConverterSettings, DEVELOP_PREVIEW_MAX_EDGE,
    RawFileInfo, RawMetadata, ToolStatus,
};
use serde::Serialize;
use std::panic::AssertUnwindSafe;
use std::path::PathBuf;
use std::sync::atomic::Ordering;
use std::sync::Arc;
#[cfg(target_os = "windows")]
use std::process::Command;
use tauri::{AppHandle, Emitter, State};

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ScanResult {
    files: Vec<RawFileInfo>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ImagePayload {
    data_url: String,
    bytes: usize,
}

struct ConversionControl {
    cancel: Arc<CancelFlag>,
}

impl Default for ConversionControl {
    fn default() -> Self {
        Self {
            cancel: Arc::new(CancelFlag::new(false)),
        }
    }
}

fn resolve_output_dir(folder: &str, output_folder: Option<String>) -> PathBuf {
    match output_folder {
        Some(path) if !path.trim().is_empty() => PathBuf::from(path),
        _ => default_output_dir(&PathBuf::from(folder)),
    }
}

fn jpeg_data_url(bytes: Vec<u8>) -> ImagePayload {
    let size = bytes.len();
    ImagePayload {
        data_url: format!("data:image/jpeg;base64,{}", STANDARD.encode(bytes)),
        bytes: size,
    }
}

#[tauri::command]
fn doctor() -> Vec<ToolStatus> {
    dependency_status()
}

#[tauri::command]
fn scan_folder(folder: String) -> Result<ScanResult, String> {
    scan_raw_files(&PathBuf::from(folder))
        .map(|files| ScanResult { files })
        .map_err(|error| format!("{error:#}"))
}

#[tauri::command]
fn inspect_paths(paths: Vec<String>) -> Result<ScanResult, String> {
    let paths = paths.into_iter().map(PathBuf::from).collect::<Vec<_>>();
    raw_file_infos(&paths)
        .map(|files| ScanResult { files })
        .map_err(|error| format!("{error:#}"))
}

#[tauri::command]
fn reveal_output_folder(folder: String, output_folder: Option<String>) -> Result<(), String> {
    let output_dir = resolve_output_dir(&folder, output_folder);
    if !output_dir.is_dir() {
        return Err(format!(
            "Output folder is unavailable: {}",
            output_dir.display()
        ));
    }

    #[cfg(target_os = "windows")]
    {
        Command::new("explorer.exe")
            .arg(&output_dir)
            .spawn()
            .map(|_| ())
            .map_err(|error| {
                format!(
                    "failed to reveal {} in File Explorer: {error}",
                    output_dir.display()
                )
            })
    }

    #[cfg(not(target_os = "windows"))]
    {
        Err("Revealing the output folder is supported on Windows only.".into())
    }
}

#[tauri::command]
fn cancel_conversion(control: State<'_, ConversionControl>) -> Result<(), String> {
    control.cancel.store(true, Ordering::SeqCst);
    Ok(())
}

#[tauri::command]
fn open_url(url: String) -> Result<(), String> {
    if !(url.starts_with("https://") || url.starts_with("http://")) {
        return Err("Only http(s) links can be opened.".into());
    }

    #[cfg(target_os = "windows")]
    {
        Command::new("cmd")
            .args(["/C", "start", "", &url])
            .spawn()
            .map(|_| ())
            .map_err(|error| format!("failed to open URL: {error}"))
    }

    #[cfg(not(target_os = "windows"))]
    {
        Err("Opening URLs is supported on Windows only.".into())
    }
}

#[tauri::command]
fn open_path(path: String) -> Result<(), String> {
    let path = PathBuf::from(&path);
    if !path.is_file() {
        return Err(format!("File is unavailable: {}", path.display()));
    }

    #[cfg(target_os = "windows")]
    {
        Command::new("cmd")
            .args(["/C", "start", "", &path.to_string_lossy()])
            .spawn()
            .map(|_| ())
            .map_err(|error| format!("failed to open {}: {error}", path.display()))
    }

    #[cfg(not(target_os = "windows"))]
    {
        Err("Opening files is supported on Windows only.".into())
    }
}

#[tauri::command]
fn reveal_path(path: String) -> Result<(), String> {
    let path = PathBuf::from(&path);
    if !path.exists() {
        return Err(format!("Path is unavailable: {}", path.display()));
    }

    #[cfg(target_os = "windows")]
    {
        Command::new("explorer")
            .arg(format!("/select,{}", path.display()))
            .spawn()
            .map(|_| ())
            .map_err(|error| format!("failed to reveal {}: {error}", path.display()))
    }

    #[cfg(not(target_os = "windows"))]
    {
        Err("Revealing files is supported on Windows only.".into())
    }
}

#[tauri::command]
async fn inspect_raw(path: String) -> Result<RawMetadata, String> {
    tauri::async_runtime::spawn_blocking(move || {
        inspect_raw_metadata(&PathBuf::from(path)).map_err(|error| format!("{error:#}"))
    })
    .await
    .map_err(|error| format!("inspect task failed: {error}"))?
}

#[tauri::command]
async fn load_camera_preview(path: String) -> Result<ImagePayload, String> {
    // Keep preview off the UI/IPC thread. Sync extraction + magick previously froze
    // (and sometimes blanked) the WebView as soon as a photo was queued.
    tauri::async_runtime::spawn_blocking(move || {
        let cancel = CancelFlag::new(false);
        match std::panic::catch_unwind(AssertUnwindSafe(|| {
            camera_preview_jpeg(&PathBuf::from(&path), &cancel)
        })) {
            Ok(Ok(bytes)) => Ok(jpeg_data_url(bytes)),
            Ok(Err(error)) => Err(format!("{error:#}")),
            Err(_) => Err("camera preview failed unexpectedly".into()),
        }
    })
    .await
    .map_err(|error| format!("camera preview task failed: {error}"))?
}

#[tauri::command]
async fn load_develop_preview(path: String) -> Result<ImagePayload, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let cancel = CancelFlag::new(false);
        match std::panic::catch_unwind(AssertUnwindSafe(|| {
            develop_preview_jpeg(&PathBuf::from(&path), DEVELOP_PREVIEW_MAX_EDGE, &cancel)
        })) {
            Ok(Ok(bytes)) => Ok(jpeg_data_url(bytes)),
            Ok(Err(error)) => Err(format!("{error:#}")),
            Err(_) => Err("develop preview failed unexpectedly".into()),
        }
    })
    .await
    .map_err(|error| format!("develop preview task failed: {error}"))?
}

#[tauri::command]
async fn load_result_thumbnail(path: String) -> Result<ImagePayload, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let cancel = CancelFlag::new(false);
        result_thumbnail_jpeg(&PathBuf::from(path), 256, &cancel)
            .map(jpeg_data_url)
            .map_err(|error| format!("{error:#}"))
    })
    .await
    .map_err(|error| format!("thumbnail task failed: {error}"))?
}

#[tauri::command]
async fn start_conversion(
    app: AppHandle,
    control: State<'_, ConversionControl>,
    folder: String,
    settings: ConverterSettings,
    output_folder: Option<String>,
    sources: Vec<String>,
) -> Result<BatchSummary, String> {
    if sources.is_empty() {
        return Err("No RAW files are queued for conversion.".into());
    }

    control.cancel.store(false, Ordering::SeqCst);
    let cancel = Arc::clone(&control.cancel);
    let output_dir = resolve_output_dir(&folder, output_folder);

    tauri::async_runtime::spawn_blocking(move || {
        let paths = sources.into_iter().map(PathBuf::from).collect::<Vec<_>>();
        convert_paths_with_cancel(
            &paths,
            &output_dir,
            &settings,
            |event| {
                let _ = app.emit("conversion-progress", event);
            },
            &cancel,
        )
        .map_err(|error| format!("{error:#}"))
    })
    .await
    .map_err(|error| format!("conversion task failed: {error}"))?
}

/// Retry an explicit list of RAW absolute paths into the chosen output folder.
#[tauri::command]
async fn retry_conversion(
    app: AppHandle,
    control: State<'_, ConversionControl>,
    folder: String,
    settings: ConverterSettings,
    sources: Vec<String>,
    output_folder: Option<String>,
) -> Result<BatchSummary, String> {
    if sources.is_empty() {
        return Err("No failed files were selected for retry.".into());
    }

    control.cancel.store(false, Ordering::SeqCst);
    let cancel = Arc::clone(&control.cancel);
    let output_dir = resolve_output_dir(&folder, output_folder);

    tauri::async_runtime::spawn_blocking(move || {
        let paths = sources.into_iter().map(PathBuf::from).collect::<Vec<_>>();
        convert_paths_with_cancel(
            &paths,
            &output_dir,
            &settings,
            |event| {
                let _ = app.emit("conversion-progress", event);
            },
            &cancel,
        )
        .map_err(|error| format!("{error:#}"))
    })
    .await
    .map_err(|error| format!("retry task failed: {error}"))?
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_notification::init())
        .manage(ConversionControl::default())
        .invoke_handler(tauri::generate_handler![
            doctor,
            scan_folder,
            inspect_paths,
            reveal_output_folder,
            cancel_conversion,
            open_url,
            open_path,
            reveal_path,
            inspect_raw,
            load_camera_preview,
            load_develop_preview,
            load_result_thumbnail,
            start_conversion,
            retry_conversion
        ])
        .run(tauri::generate_context!())
        .expect("error while running Raw Photo Converter");
}
