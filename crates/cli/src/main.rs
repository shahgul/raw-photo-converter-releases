use anyhow::{anyhow, Result};
use clap::{Parser, Subcommand};
use raw_photo_converter_core::{
    convert_folder_to, convert_paths_with_cancel, default_output_dir, dependency_status,
    resolve_raw_sources, CancelFlag, ColorGamut, ConversionMode, ConverterSettings, OutputFormat,
};
use std::{
    path::PathBuf,
    sync::{
        atomic::Ordering,
        Arc,
    },
};

#[derive(Parser)]
#[command(name = "rpc", version, about = "Local-first RAW photo converter")]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Check darktable, ImageMagick and ExifTool.
    Doctor,

    /// Convert Sony ARW and Canon CR2/CR3 files in a folder.
    Convert {
        folder: PathBuf,

        /// Convert only these RAW filenames (useful for retrying failed files).
        #[arg(long = "file", value_name = "NAME")]
        files: Vec<String>,

        /// Output folder (default: <folder>/JPG).
        #[arg(long)]
        output: Option<PathBuf>,

        #[arg(long, default_value_t = 3.0)]
        target_size_mb: f64,

        /// Long-edge cap in px. Use 0 for original resolution.
        #[arg(long, default_value_t = 4000)]
        long_edge: u32,

        #[arg(long, default_value_t = 80)]
        min_quality: u8,

        #[arg(long, default_value_t = 96)]
        max_quality: u8,

        #[arg(long, default_value_t = true)]
        preserve_metadata: bool,

        /// develop-raw | camera-look
        #[arg(long, default_value = "develop-raw")]
        mode: String,

        /// jpeg | webp | avif
        #[arg(long, default_value = "jpeg")]
        format: String,

        /// srgb | display-p3
        #[arg(long, default_value = "srgb")]
        gamut: String,

        /// Output stem template. Tokens: {name} {edge} {mode}
        #[arg(long, default_value = "{name}")]
        name_template: String,

        /// Bounded tool workers (1 sequential, 2 max). Default 1.
        #[arg(long, default_value_t = 1)]
        jobs: u8,
    },
}

fn main() -> Result<()> {
    let cli = Cli::parse();

    match cli.command {
        Commands::Doctor => {
            let statuses = dependency_status();
            for tool in &statuses {
                println!(
                    "{:<8} {:<14} {}",
                    if tool.available { "OK" } else { "MISSING" },
                    tool.name,
                    tool.command
                );
            }
            if statuses.iter().any(|tool| !tool.available) {
                std::process::exit(2);
            }
        }
        Commands::Convert {
            folder,
            files,
            output,
            target_size_mb,
            long_edge,
            min_quality,
            max_quality,
            preserve_metadata,
            mode,
            format,
            gamut,
            name_template,
            jobs,
        } => {
            let settings = ConverterSettings {
                target_size_mb,
                max_long_edge: long_edge,
                min_quality,
                max_quality,
                preserve_metadata,
                mode: parse_mode(&mode)?,
                format: parse_format(&format)?,
                color_gamut: parse_gamut(&gamut)?,
                name_template,
                max_parallel: jobs,
                ..ConverterSettings::default()
            };

            let output_dir = output.unwrap_or_else(|| default_output_dir(&folder));
            let cancel = Arc::new(CancelFlag::new(false));
            let cancel_handler = Arc::clone(&cancel);
            ctrlc::set_handler(move || {
                cancel_handler.store(true, Ordering::SeqCst);
                eprintln!("\nCancelling… completed outputs are kept.");
            })
            .ok();

            let summary = if files.is_empty() {
                convert_folder_to(&folder, &output_dir, &settings, print_progress, &cancel)?
            } else {
                let sources = resolve_raw_sources(&folder, &files)?;
                if sources.is_empty() {
                    return Err(anyhow!("no matching RAW files to convert"));
                }
                convert_paths_with_cancel(
                    &sources,
                    &output_dir,
                    &settings,
                    print_progress,
                    &cancel,
                )?
            };

            println!(
                "\nConverted: {} · Skipped: {} · Failed: {}{}",
                summary.converted,
                summary.skipped,
                summary.failed,
                if summary.cancelled { " · Cancelled" } else { "" }
            );
            if summary.recovered_temp_files > 0 {
                println!(
                    "Recovered {} leftover temp file{}",
                    summary.recovered_temp_files,
                    if summary.recovered_temp_files == 1 {
                        ""
                    } else {
                        "s"
                    }
                );
            }
            if let Some(ms) = summary.elapsed_ms {
                println!("Elapsed: {:.1}s", ms as f64 / 1000.0);
            }
            if let Some(timings) = summary.phase_timings.as_ref() {
                println!(
                    "Phases: develop {:.1}s · optimize {:.1}s · metadata {:.1}s · save {:.1}s",
                    timings.developing_ms as f64 / 1000.0,
                    timings.optimizing_ms as f64 / 1000.0,
                    timings.metadata_ms as f64 / 1000.0,
                    timings.saving_ms as f64 / 1000.0
                );
            }

            if summary.failed > 0 {
                std::process::exit(1);
            }
            if summary.cancelled {
                std::process::exit(130);
            }
        }
    }

    Ok(())
}

fn parse_mode(value: &str) -> Result<ConversionMode> {
    match value.to_ascii_lowercase().as_str() {
        "develop-raw" | "develop" | "raw" => Ok(ConversionMode::DevelopRaw),
        "camera-look" | "camera" => Ok(ConversionMode::CameraLook),
        other => Err(anyhow!("unknown mode '{other}' (develop-raw | camera-look)")),
    }
}

fn parse_format(value: &str) -> Result<OutputFormat> {
    match value.to_ascii_lowercase().as_str() {
        "jpeg" | "jpg" => Ok(OutputFormat::Jpeg),
        "webp" => Ok(OutputFormat::Webp),
        "avif" => Ok(OutputFormat::Avif),
        other => Err(anyhow!("unknown format '{other}' (jpeg | webp | avif)")),
    }
}

fn parse_gamut(value: &str) -> Result<ColorGamut> {
    match value.to_ascii_lowercase().as_str() {
        "srgb" => Ok(ColorGamut::Srgb),
        "display-p3" | "p3" | "displayp3" => Ok(ColorGamut::DisplayP3),
        other => Err(anyhow!("unknown gamut '{other}' (srgb | display-p3)")),
    }
}

fn print_progress(event: raw_photo_converter_core::ProgressEvent) {
    match event.state.as_str() {
        "processing" => {
            let phase = match event.phase.as_deref() {
                Some("developing") => "developing",
                Some("optimizing") => "optimizing",
                Some("metadata") => "restoring metadata",
                Some("saving") => "saving",
                _ => "processing",
            };
            if let Some(ms) = event.phase_elapsed_ms {
                println!(
                    "[{}/{}] {} · {} · {:.1}s",
                    event.index,
                    event.total,
                    event.file,
                    phase,
                    ms as f64 / 1000.0
                );
            } else {
                println!(
                    "[{}/{}] {} · {}",
                    event.index, event.total, event.file, phase
                );
            }
        }
        "done" => {
            let mb = event.size_bytes.unwrap_or_default() as f64 / 1024.0 / 1024.0;
            println!(
                "           done · Q{} · {:.2} MB",
                event.quality.unwrap_or_default(),
                mb
            );
        }
        "skipped" => println!("           skipped"),
        "cancelled" => eprintln!(
            "           cancelled · {}",
            event
                .error
                .unwrap_or_else(|| "Completed outputs are kept.".into())
        ),
        "failed" => eprintln!(
            "           failed · {}",
            event.error.unwrap_or_else(|| "unknown error".into())
        ),
        _ => {}
    }
}
