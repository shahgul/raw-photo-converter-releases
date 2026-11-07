import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import { open } from "@tauri-apps/plugin-dialog";
import {
  isPermissionGranted,
  requestPermission,
  sendNotification,
} from "@tauri-apps/plugin-notification";
import {
  ArrowRight,
  Bell,
  BellOff,
  Check,
  Copy,
  FileImage,
  FolderOpen,
  Images,
  RefreshCw,
  Settings,
  SlidersHorizontal,
  X,
} from "lucide-react";
import { AnimatePresence, motion, useMotionValue, useReducedMotion, useTransform } from "motion/react";
import { useEffect, useMemo, useRef, useState } from "react";
import { HoldRemoveButton } from "./HoldRemoveButton";
import {
  analyzeHistogram,
  extractPalette,
  InspectFrame,
  LoupePortal,
  ResultThumbStrip,
  type HistogramData,
  type HistogramMode,
  type RawMetadata,
} from "./inspect";
import { isTauriShell, toggleWindowMaximize, WindowControls } from "./windowChrome";

type ToolStatus = { name: string; command: string; available: boolean };
type RawFileInfo = { path: string; name: string; sizeBytes: number };
type ScanResult = { files: RawFileInfo[] };
type ProgressEvent = {
  index: number;
  total: number;
  file: string;
  state: "processing" | "done" | "skipped" | "failed" | "cancelled";
  phase?: string | null;
  phaseElapsedMs?: number | null;
  quality?: number | null;
  sizeBytes?: number | null;
  width?: number | null;
  height?: number | null;
  targetMet?: boolean | null;
  error?: string | null;
  output?: string | null;
};
type ImagePayload = { dataUrl: string; bytes: number };
type PhaseTimingTotals = {
  developingMs: number;
  optimizingMs: number;
  metadataMs: number;
  savingMs: number;
};
type BatchSummary = {
  converted: number;
  skipped: number;
  failed: number;
  cancelled?: boolean;
  recoveredTempFiles?: number;
  elapsedMs?: number | null;
  phaseTimings?: PhaseTimingTotals | null;
};
type ConversionMode = "developRaw" | "cameraLook";
type OutputFormat = "jpeg" | "webp" | "avif";
type ColorGamut = "srgb" | "displayP3";
/** UI pace preference. Engine still clamps workers to 1..=2 (darktable is heavy). */
type ParallelMode = "auto" | "steady" | "faster";
type Settings = {
  targetSizeMb: number;
  maxLongEdge: number;
  minQuality: number;
  maxQuality: number;
  preserveMetadata: boolean;
  skipExisting: boolean;
  mode: ConversionMode;
  format: OutputFormat;
  colorGamut: ColorGamut;
  nameTemplate: string;
  parallelMode: ParallelMode;
  maxParallel: 1 | 2;
  /** Extra CW rotation for Develop output (and inspect preview). */
  rotateDegrees: 0 | 90 | 180 | 270;
};
type Scheme = "light" | "dark";
type ThemePreference = "system" | "light" | "dark";

type CompletionToastState = {
  title: string;
  body: string;
  tone: "ok" | "warn" | "cancel";
};

const presets: Record<string, Pick<Settings, "targetSizeMb" | "maxLongEdge">> = {
  Social: { targetSizeMb: 2, maxLongEdge: 2160 },
  Archive: { targetSizeMb: 3, maxLongEdge: 4000 },
  Large: { targetSizeMb: 8, maxLongEdge: 6000 },
  Original: { targetSizeMb: 3, maxLongEdge: 0 },
};

const formatLabels: Record<OutputFormat, string> = {
  jpeg: "JPEG",
  webp: "WebP",
  avif: "AVIF",
};

const templatePresets = ["{name}", "{name}_{edge}", "{name}_{mode}"] as const;

function deviceCoreCount() {
  return typeof navigator !== "undefined" && navigator.hardwareConcurrency
    ? navigator.hardwareConcurrency
    : 4;
}

/** Recommend 1 or 2 workers from logical cores. Cap stays 2 until darktable is re-measured. */
function recommendWorkers(cores = deviceCoreCount()): 1 | 2 {
  return cores >= 8 ? 2 : 1;
}

function resolveWorkers(mode: ParallelMode, cores = deviceCoreCount()): 1 | 2 {
  if (mode === "steady") return 1;
  if (mode === "faster") return 2;
  return recommendWorkers(cores);
}

function parallelModeLabel(mode: ParallelMode) {
  if (mode === "auto") return "Auto";
  if (mode === "steady") return "Steady";
  return "Faster";
}

function parallelModeHint(mode: ParallelMode, workers: 1 | 2, cores: number) {
  if (mode === "auto") {
    return workers === 2
      ? `Auto · ${cores} cores → two files at once`
      : `Auto · ${cores} cores → one file at a time`;
  }
  if (mode === "steady") return "One file at a time · coolest and safest";
  return "Two files at once · more CPU, RAM, and heat";
}

function phaseLabel(phase?: string | null, format: OutputFormat = "jpeg") {
  switch (phase) {
    case "developing":
      return "Developing";
    case "optimizing":
      return `Optimizing ${formatLabels[format]}`;
    case "metadata":
      return "Restoring metadata";
    case "saving":
      return "Saving";
    default:
      return "Processing";
  }
}

/** Honest remaining ETA from completed files only; needs ≥2 measured samples before showing. */
const ETA_MIN_SAMPLES = 2;
function estimateRemainingMs(elapsedMs: number, completed: number, total: number) {
  if (elapsedMs <= 0 || completed < ETA_MIN_SAMPLES || total <= completed) return null;
  return Math.max(0, Math.round((elapsedMs / completed) * (total - completed)));
}

const FRAME_ROW_HEIGHT = 112;
const SHEET_VIEWPORT_ROWS = 6;
const FRAME_OVERSCAN_ROWS = 2;

function frameColumnCount() {
  if (typeof window === "undefined") return 5;
  if (window.matchMedia("(max-width: 680px)").matches) return 2;
  if (window.matchMedia("(max-width: 980px)").matches) return 3;
  return 5;
}

function formatEta(ms: number) {
  const totalSec = Math.max(1, Math.round(ms / 1000));
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return min > 0 ? `${min}m ${String(sec).padStart(2, "0")}s` : `${sec}s`;
}

function formatMb(bytes: number) {
  const mb = bytes / 1024 / 1024;
  return `${mb.toFixed(mb >= 10 ? 1 : 2)} MB`;
}

function formatElapsed(ms: number) {
  const totalSec = Math.max(0, Math.floor(ms / 1000));
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${String(min).padStart(2, "0")}:${String(sec).padStart(2, "0")}`;
}

async function copyText(text: string) {
  if (!text) throw new Error("Nothing to copy.");
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const area = document.createElement("textarea");
  area.value = text;
  area.setAttribute("readonly", "");
  area.style.position = "fixed";
  area.style.left = "-9999px";
  document.body.appendChild(area);
  area.select();
  const ok = document.execCommand("copy");
  document.body.removeChild(area);
  if (!ok) throw new Error("Clipboard copy failed.");
}

function resultLine(
  file: RawFileInfo,
  info: ProgressEvent | undefined,
  settings: Settings,
) {
  const format = formatLabels[settings.format];
  if (!info) return `${file.name}: waiting`;
  if (info.state === "failed") return `${file.name}: Failed: ${info.error || "unknown error"}`;
  if (info.state === "skipped") return `${file.name}: Skipped: ${format} already exists`;
  if (info.state === "cancelled") return `${file.name}: Cancelled`;
  if (info.state === "done") {
    const quality = info.quality != null ? `Q${info.quality} / ` : "";
    const size = typeof info.sizeBytes === "number" ? formatMb(info.sizeBytes) : "saved";
    const dims =
      info.width != null && info.height != null ? `${info.width} x ${info.height} / ` : "";
    const mode = settings.mode === "cameraLook" ? "Camera look" : "Developed RAW";
    const gamut = settings.colorGamut === "displayP3" ? "Display P3" : "sRGB";
    const target =
      info.targetMet === false ? " / above size target" : info.targetMet === true ? " / target met" : "";
    return `${file.name}: Saved ${dims}${mode} / ${format} / ${gamut} / ${quality}${size}${target}`;
  }
  return `${file.name}: ${phaseLabel(info.phase, settings.format)}`;
}

function buildConversionReport(args: {
  files: RawFileInfo[];
  fileStates: Record<string, ProgressEvent>;
  settings: Settings;
  outputFolder: string;
  summary: BatchSummary | null;
  phaseLog: Array<{ file: string; phase: string }>;
  elapsedMs: number | null;
}) {
  const phase = args.summary
    ? args.summary.cancelled
      ? "Cancelled"
      : "Complete"
    : "Processing";
  const processed = Object.values(args.fileStates).filter((event) => event.state !== "processing").length;
  const lines = [
    `Raw Photo Converter - ${phase}`,
    `${processed} / ${args.files.length} processed`,
    `${args.settings.mode === "cameraLook" ? "Camera look" : "Develop RAW"} / ${
      args.settings.maxLongEdge === 0 ? "Original resolution" : `Standard (${args.settings.maxLongEdge} px)`
    } / ${formatLabels[args.settings.format]} / ${
      args.settings.colorGamut === "displayP3" ? "Display P3" : "sRGB"
    }`,
    `Destination: ${args.outputFolder || "(default JPG next to source)"}`,
    `Filename: ${args.settings.nameTemplate}`,
    `Metadata: ${args.settings.preserveMetadata ? "enabled (including available GPS)" : "disabled"}`,
  ];
  if (args.elapsedMs != null && args.elapsedMs > 0) {
    lines.push(`Elapsed: ${formatElapsed(args.elapsedMs)}`);
  }
  if (args.summary) {
    lines.push(
      `${args.summary.converted} converted · ${args.summary.skipped} skipped · ${args.summary.failed} failed`,
    );
  }
  for (const file of args.files) {
    lines.push(resultLine(file, args.fileStates[file.name], args.settings));
  }
  if (args.phaseLog.length) {
    lines.push("Recent phases:");
    for (const entry of args.phaseLog) {
      lines.push(`${entry.file} ${phaseLabel(entry.phase, args.settings.format)}`);
    }
  }
  return lines.join("\n");
}

function playCompletionTone() {
  try {
    const AudioContextCtor =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextCtor) return;
    const context = new AudioContextCtor();
    const gain = context.createGain();
    gain.gain.setValueAtTime(0.0001, context.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.055, context.currentTime + 0.012);
    gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + 0.46);
    gain.connect(context.destination);

    [659.25, 783.99].forEach((frequency, index) => {
      const oscillator = context.createOscillator();
      oscillator.type = "sine";
      oscillator.frequency.setValueAtTime(frequency, context.currentTime);
      oscillator.connect(gain);
      oscillator.start(context.currentTime + index * 0.07);
      oscillator.stop(context.currentTime + 0.5);
    });

    window.setTimeout(() => void context.close(), 650);
  } catch {}
}

function readThemePreference(): ThemePreference {
  try {
    const stored = localStorage.getItem("rpc-theme");
    if (stored === "system" || stored === "light" || stored === "dark") return stored;
    // Migrate older light/dark-only preference.
    const legacy = localStorage.getItem("rpc-scheme");
    if (legacy === "light" || legacy === "dark") return legacy;
  } catch {}
  return "system";
}

function resolveScheme(preference: ThemePreference): Scheme {
  if (preference === "light" || preference === "dark") return preference;
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function themePreferenceLabel(preference: ThemePreference) {
  if (preference === "system") return "System";
  if (preference === "light") return "Light";
  return "Dark";
}

function BrandMark({ size = 22 }: { size?: number }) {
  return (
    <svg
      className="brand-mark"
      width={size}
      height={size}
      viewBox="0 0 32 32"
      fill="none"
      aria-hidden="true"
    >
      <path
        className="brand-mark-frame"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="square"
        d="M6 9 V6 H9 M23 6 H26 V9 M26 23 V26 H23 M9 26 H6 V23"
      />
      <rect
        className="brand-mark-core"
        x="10.6"
        y="10.6"
        width="10.8"
        height="10.8"
        stroke="currentColor"
        strokeWidth="1.8"
      />
    </svg>
  );
}

function QuietCheck({
  checked,
  onChange,
}: {
  checked: boolean;
  onChange: (next: boolean) => void;
}) {
  const reduceMotion = useReducedMotion();

  return (
    <span className="quiet-check">
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span className="quiet-check-mark" aria-hidden="true">
        <AnimatePresence initial={false}>
          {checked && (
            <motion.span
              className="quiet-check-tick"
              key="checked"
              initial={reduceMotion ? false : { opacity: 0, scale: 0.72 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={reduceMotion ? undefined : { opacity: 0, scale: 0.72 }}
              transition={{ duration: 0.14, ease: [0.23, 1, 0.32, 1] }}
            >
              <Check size={11} strokeWidth={2.5} />
            </motion.span>
          )}
        </AnimatePresence>
      </span>
    </span>
  );
}

function RecipeChipGroup<T extends string | number>({
  layoutId,
  value,
  options,
  onChange,
  className,
  "aria-label": ariaLabel,
}: {
  layoutId: string;
  value: T;
  options: Array<{ value: T; label: string }>;
  onChange: (value: T) => void;
  className?: string;
  "aria-label"?: string;
}) {
  const reduceMotion = useReducedMotion();

  return (
    <div className={className ? `recipe-chip-set ${className}` : "recipe-chip-set"} role="group" aria-label={ariaLabel}>
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={String(option.value)}
            type="button"
            className={active ? "recipe-chip active" : "recipe-chip"}
            aria-pressed={active}
            onClick={() => onChange(option.value)}
          >
            {active && (
              <motion.span
                className="recipe-chip-pill"
                layoutId={layoutId}
                transition={
                  reduceMotion
                    ? { duration: 0 }
                    : { type: "spring", stiffness: 440, damping: 34, mass: 0.7 }
                }
              />
            )}
            <span className="recipe-chip-label">{option.label}</span>
          </button>
        );
      })}
    </div>
  );
}

function CompletionToast({
  toast,
  onDismiss,
}: {
  toast: CompletionToastState;
  onDismiss: () => void;
}) {
  const reduceMotion = useReducedMotion();
  const x = useMotionValue(0);
  const opacity = useTransform(x, [-180, 0, 180], [0, 1, 0]);

  useEffect(() => {
    const id = window.setTimeout(onDismiss, 5600);
    return () => window.clearTimeout(id);
  }, [onDismiss, toast.title, toast.body]);

  return (
    <motion.div
      className={`swipe-toast tone-${toast.tone}`}
      role="status"
      aria-live="polite"
      style={{ x, opacity }}
      initial={reduceMotion ? false : { y: 18, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      exit={reduceMotion ? undefined : { y: 12, opacity: 0 }}
      drag={reduceMotion ? false : "x"}
      dragConstraints={{ left: 0, right: 0 }}
      dragElastic={0.18}
      onDragEnd={(_, info) => {
        if (Math.abs(info.offset.x) > 88 || Math.abs(info.velocity.x) > 520) onDismiss();
      }}
    >
      <span className="swipe-toast-mark" aria-hidden="true">
        {toast.tone === "ok" ? <Check size={15} strokeWidth={2.2} /> : <X size={15} strokeWidth={2.2} />}
      </span>
      <div className="swipe-toast-copy">
        <strong>{toast.title}</strong>
        <span>{toast.body}</span>
      </div>
      <button type="button" className="swipe-toast-dismiss" aria-label="Dismiss" onClick={onDismiss}>
        <X size={13} />
      </button>
    </motion.div>
  );
}

function readSound() {
  try {
    return localStorage.getItem("rpc-sound") !== "off";
  } catch {
    return true;
  }
}

function readNotify() {
  try {
    return localStorage.getItem("rpc-notify") === "on";
  } catch {
    return false;
  }
}

function readOutputPreference(): { custom: boolean; path: string } {
  try {
    const raw = localStorage.getItem("rpc-output");
    if (!raw) return { custom: false, path: "" };
    const parsed = JSON.parse(raw) as { custom?: boolean; path?: string };
    return {
      custom: Boolean(parsed.custom && parsed.path),
      path: typeof parsed.path === "string" ? parsed.path : "",
    };
  } catch {
    return { custom: false, path: "" };
  }
}

const defaultSettings: Settings = {
  targetSizeMb: 3,
  maxLongEdge: 4000,
  minQuality: 80,
  maxQuality: 96,
  preserveMetadata: true,
  skipExisting: true,
  mode: "developRaw",
  format: "jpeg",
  colorGamut: "srgb",
  nameTemplate: "{name}",
  parallelMode: "auto",
  maxParallel: recommendWorkers(),
  rotateDegrees: 0,
};

function readSettings(): Settings {
  try {
    const raw = localStorage.getItem("rpc-settings");
    if (!raw) return { ...defaultSettings, maxParallel: recommendWorkers() };
    const parsed = JSON.parse(raw) as Partial<Settings> & { parallelMode?: string };
    const parallelMode: ParallelMode =
      parsed.parallelMode === "steady" || parsed.parallelMode === "faster" || parsed.parallelMode === "auto"
        ? parsed.parallelMode
        : parsed.maxParallel === 2
          ? "faster"
          : parsed.maxParallel === 1
            ? "steady"
            : "auto";
    const maxParallel = resolveWorkers(parallelMode);
    const rotateRaw = Number(parsed.rotateDegrees);
    const rotateDegrees: 0 | 90 | 180 | 270 =
      rotateRaw === 90 || rotateRaw === 180 || rotateRaw === 270 ? rotateRaw : 0;
    return { ...defaultSettings, ...parsed, parallelMode, maxParallel, rotateDegrees };
  } catch {
    return { ...defaultSettings, maxParallel: recommendWorkers() };
  }
}

async function notifyCompletion(title: string, body: string) {
  try {
    let granted = await isPermissionGranted();
    if (!granted) {
      const permission = await requestPermission();
      granted = permission === "granted";
    }
    if (!granted) return;
    sendNotification({ title, body });
  } catch {}
}

function pathTail(path: string) {
  const normalized = path.replace(/[\\/]+$/, "");
  const slash = Math.max(normalized.lastIndexOf("\\"), normalized.lastIndexOf("/"));
  return slash >= 0 ? normalized.slice(slash + 1) : normalized;
}

function defaultOutputFor(sourceFolder: string) {
  if (!sourceFolder) return "";
  const trimmed = sourceFolder.replace(/[\\/]+$/, "");
  const sep = trimmed.includes("\\") ? "\\" : "/";
  return `${trimmed}${sep}JPG`;
}

function isRawPath(path: string) {
  return /\.(arw|cr2|cr3)$/i.test(path);
}

function parentFolder(path: string) {
  const normalized = path.replace(/[\\/]+$/, "");
  const slash = Math.max(normalized.lastIndexOf("\\"), normalized.lastIndexOf("/"));
  return slash >= 0 ? normalized.slice(0, slash) : normalized;
}

function folderFromDroppedPath(path: string) {
  if (!isRawPath(path)) return path;
  return parentFolder(path);
}

const toolHints: Record<
  string,
  { step: string; text: string; verify: string; url: string }
> = {
  "darktable-cli": {
    step: "1 · darktable",
    text: "Install darktable. Keep darktable-cli on PATH.",
    verify: "darktable-cli --version",
    url: "https://www.darktable.org/install/",
  },
  magick: {
    step: "2 · ImageMagick",
    text: "Install ImageMagick 7+ and enable Add to PATH so magick works.",
    verify: "magick -version",
    url: "https://imagemagick.org/script/download.php#windows",
  },
  exiftool: {
    step: "3 · ExifTool",
    text: "Install ExifTool, rename to exiftool.exe, and put it on PATH.",
    verify: "exiftool -ver",
    url: "https://exiftool.org/",
  },
};

function summaryFromFileStates(
  files: RawFileInfo[],
  fileStates: Record<string, ProgressEvent>,
): BatchSummary {
  let converted = 0;
  let skipped = 0;
  let failed = 0;
  for (const file of files) {
    switch (fileStates[file.name]?.state) {
      case "done":
        converted += 1;
        break;
      case "skipped":
        skipped += 1;
        break;
      case "failed":
        failed += 1;
        break;
      default:
        break;
    }
  }
  return { converted, skipped, failed };
}

export default function App() {
  const reduceMotion = useReducedMotion();
  const [themePreference, setThemePreference] = useState<ThemePreference>(() => readThemePreference());
  const [scheme, setScheme] = useState<Scheme>(() => resolveScheme(readThemePreference()));
  const [soundOn, setSoundOn] = useState(() => readSound());
  const [notifyOn, setNotifyOn] = useState(() => readNotify());
  const [completionToast, setCompletionToast] = useState<CompletionToastState | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [folder, setFolder] = useState("");
  const [outputFolder, setOutputFolder] = useState(() => readOutputPreference().path);
  const [outputCustom, setOutputCustom] = useState(() => readOutputPreference().custom);
  const [files, setFiles] = useState<RawFileInfo[]>([]);
  const [tools, setTools] = useState<ToolStatus[]>([]);
  const [doctorReady, setDoctorReady] = useState(false);
  const [doctorChecking, setDoctorChecking] = useState(false);
  const [running, setRunning] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [progress, setProgress] = useState<ProgressEvent | null>(null);
  const [fileStates, setFileStates] = useState<Record<string, ProgressEvent>>({});
  const [summary, setSummary] = useState<BatchSummary | null>(null);
  const [error, setError] = useState("");
  const [settings, setSettings] = useState<Settings>(() => readSettings());
  const [batchStartedAt, setBatchStartedAt] = useState<number | null>(null);
  const [lastElapsedMs, setLastElapsedMs] = useState<number | null>(null);
  const [copyFlash, setCopyFlash] = useState<
    { kind: "report" } | { kind: "path"; path: string } | null
  >(null);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [selectedName, setSelectedName] = useState<string | null>(null);
  const [cameraUrl, setCameraUrl] = useState<string | null>(null);
  const [cameraLoading, setCameraLoading] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [metadata, setMetadata] = useState<RawMetadata | null>(null);
  const [developUrl, setDevelopUrl] = useState<string | null>(null);
  const [developLoading, setDevelopLoading] = useState(false);
  const [developError, setDevelopError] = useState<string | null>(null);
  const [comparing, setComparing] = useState(false);
  const [loupeOpen, setLoupeOpen] = useState(false);
  const [histogram, setHistogram] = useState<HistogramData | null>(null);
  const [histogramMode, setHistogramMode] = useState<HistogramMode>("rgb");
  const [palette, setPalette] = useState<string[]>([]);
  const [resultThumbs, setResultThumbs] = useState<Array<{ name: string; dataUrl: string | null }>>([]);
  const [phaseLog, setPhaseLog] = useState<
    Array<{ id: number; file: string; phase: string; elapsedMs?: number }>
  >([]);
  const [sheetScrollTop, setSheetScrollTop] = useState(0);
  const [frameCols, setFrameCols] = useState(() => frameColumnCount());
  const sheetScrollRef = useRef<HTMLDivElement>(null);
  const fileStatesRef = useRef(fileStates);
  fileStatesRef.current = fileStates;
  const filesRef = useRef(files);
  filesRef.current = files;
  const selectedNameRef = useRef(selectedName);
  selectedNameRef.current = selectedName;
  const previewRequest = useRef(0);
  const phaseLogId = useRef(0);
  const effectiveOutput = outputFolder || defaultOutputFor(folder);
  const selectedFile = useMemo(
    () => files.find((file) => file.name === selectedName) ?? files[0] ?? null,
    [files, selectedName],
  );

  useEffect(() => {
    try {
      localStorage.setItem("rpc-theme", themePreference);
    } catch {}
    const apply = () => setScheme(resolveScheme(themePreference));
    apply();
    if (themePreference !== "system") return;
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => apply();
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, [themePreference]);

  useEffect(() => {
    document.documentElement.dataset.scheme = scheme;
  }, [scheme]);

  useEffect(() => {
    try {
      localStorage.setItem("rpc-sound", soundOn ? "on" : "off");
    } catch {}
  }, [soundOn]);

  useEffect(() => {
    try {
      localStorage.setItem("rpc-notify", notifyOn ? "on" : "off");
    } catch {}
  }, [notifyOn]);

  useEffect(() => {
    try {
      localStorage.setItem("rpc-settings", JSON.stringify(settings));
    } catch {}
  }, [settings]);

  useEffect(() => {
    if (!settingsOpen) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setSettingsOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [settingsOpen]);

  useEffect(() => {
    try {
      localStorage.setItem(
        "rpc-output",
        JSON.stringify({ custom: outputCustom, path: outputCustom ? outputFolder : "" }),
      );
    } catch {}
  }, [outputCustom, outputFolder]);

  useEffect(() => {
    if (!running) return;
    const id = window.setInterval(() => setNowMs(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [running]);

  useEffect(() => {
    if (!copyFlash) return;
    const id = window.setTimeout(() => setCopyFlash(null), 1600);
    return () => window.clearTimeout(id);
  }, [copyFlash]);

  useEffect(() => {
    const syncCols = () => setFrameCols(frameColumnCount());
    syncCols();
    window.addEventListener("resize", syncCols);
    return () => window.removeEventListener("resize", syncCols);
  }, []);

  useEffect(() => {
    // Reset sheet scroll only when the shoot folder changes — not on every
    // remove, which was jumping the contact sheet (and the page) on each hold.
    setSheetScrollTop(0);
  }, [folder]);

  useEffect(() => {
    if (!running || !progress?.file) return;
    const index = files.findIndex((file) => file.name === progress.file);
    if (index < 0) return;
    const row = Math.floor(index / frameCols);
    const rowTop = row * FRAME_ROW_HEIGHT;
    const rowBottom = rowTop + FRAME_ROW_HEIGHT;
    const scroller = sheetScrollRef.current;
    if (!scroller) return;
    const viewTop = scroller.scrollTop;
    const viewBottom = viewTop + SHEET_VIEWPORT_ROWS * FRAME_ROW_HEIGHT;
    if (rowTop >= viewTop && rowBottom <= viewBottom) return;
    const nextTop = Math.max(0, rowTop - FRAME_ROW_HEIGHT);
    scroller.scrollTo({
      top: nextTop,
      behavior: reduceMotion ? "auto" : "smooth",
    });
    setSheetScrollTop(nextTop);
  }, [running, progress?.file, progress?.index, files, frameCols, reduceMotion]);

  useEffect(() => {
    if (!selectedFile) {
      setCameraUrl(null);
      setCameraLoading(false);
      setCameraError(null);
      setDevelopUrl(null);
      setDevelopError(null);
      setComparing(false);
      setHistogram(null);
      setMetadata(null);
      return;
    }

    const requestId = ++previewRequest.current;
    setCameraUrl(null);
    setCameraLoading(true);
    setCameraError(null);
    setDevelopUrl(null);
    setDevelopError(null);
    setComparing(false);
    setHistogram(null);
    setPalette([]);
    setMetadata(null);
    setLoupeOpen(false);
    // Keep rotateDegrees across frame selection — it is batch output orientation,
    // shared by inspect preview and Develop. Reset only when the queue is replaced.

    void invoke<ImagePayload>("load_camera_preview", { path: selectedFile.path })
      .then((payload) => {
        if (previewRequest.current !== requestId) return;
        setCameraUrl(payload.dataUrl);
        setCameraLoading(false);
      })
      .catch((cause) => {
        if (previewRequest.current !== requestId) return;
        setCameraError(String(cause));
        setCameraLoading(false);
      });

    void invoke<RawMetadata>("inspect_raw", { path: selectedFile.path })
      .then((payload) => {
        if (previewRequest.current !== requestId) return;
        setMetadata(payload);
      })
      .catch(() => {
        if (previewRequest.current !== requestId) return;
        setMetadata(null);
      });
  }, [selectedFile?.path]);

  useEffect(() => {
    if (!cameraUrl) {
      setHistogram(null);
      setPalette([]);
      return;
    }
    let active = true;
    void analyzeHistogram(cameraUrl).then((data) => {
      if (active) setHistogram(data);
    });
    // Real palette only for a single-frame inspect; omit in bulk mode.
    if (filesRef.current.length === 1) {
      void extractPalette(cameraUrl).then((colours) => {
        if (active) setPalette(colours);
      });
    } else {
      setPalette([]);
    }
    return () => {
      active = false;
    };
  }, [cameraUrl, files.length]);

  useEffect(() => {
    const outputs = Object.values(fileStates)
      .filter((event) => (event.state === "done" || event.state === "skipped") && event.output)
      .map((event) => ({ name: event.file, path: event.output as string }));
    if (!outputs.length) {
      setResultThumbs([]);
      return;
    }

    let active = true;
    void Promise.all(
      outputs.slice(0, 12).map(async (item) => {
        try {
          const payload = await invoke<ImagePayload>("load_result_thumbnail", { path: item.path });
          return { name: item.name, dataUrl: payload.dataUrl };
        } catch {
          return { name: item.name, dataUrl: null };
        }
      }),
    ).then((thumbs) => {
      if (active) setResultThumbs(thumbs);
    });

    return () => {
      active = false;
    };
  }, [fileStates, summary]);

  useEffect(() => {
    let cancelled = false;
    const cleanups: Array<() => void> = [];

    void invoke<ToolStatus[]>("doctor")
      .then((status) => {
        if (!cancelled) {
          setTools(status);
          setDoctorReady(true);
        }
      })
      .catch(() => {
        if (!cancelled) {
          // Browser/dev without the Tauri shell still renders the Quiet Darkroom UI.
          setTools([]);
          setDoctorReady(true);
        }
      });

    void listen<ProgressEvent>("conversion-progress", (event) => {
      setProgress(event.payload);
      setFileStates((current) => ({ ...current, [event.payload.file]: event.payload }));
      if (event.payload.state === "processing" && event.payload.phase) {
        const phase = event.payload.phase;
        const file = event.payload.file;
        const elapsedMs = event.payload.phaseElapsedMs ?? undefined;
        setPhaseLog((current) => {
          if (elapsedMs != null) {
            let updated = false;
            const next = current.map((entry) => {
              if (!updated && entry.file === file && entry.phase === phase && entry.elapsedMs == null) {
                updated = true;
                return { ...entry, elapsedMs };
              }
              return entry;
            });
            return updated ? next : current;
          }
          const last = current[current.length - 1];
          if (last && last.file === file && last.phase === phase && last.elapsedMs == null) {
            return current;
          }
          phaseLogId.current += 1;
          return [...current, { id: phaseLogId.current, file, phase }].slice(-10);
        });
      }
    })
      .then((unlisten) => {
        if (cancelled) unlisten();
        else cleanups.push(unlisten);
      })
      .catch(() => {
        /* Progress events require the Tauri runtime. */
      });

    try {
      const webview = getCurrentWebview();
      void webview
        .onDragDropEvent((event) => {
          if (event.payload.type === "enter") setDragging(true);
          if (event.payload.type === "leave") setDragging(false);
          if (event.payload.type === "drop") {
            setDragging(false);
            void handleDroppedPaths(event.payload.paths);
          }
        })
        .then((unlisten) => {
          if (cancelled) unlisten();
          else cleanups.push(unlisten);
        })
        .catch(() => {
          /* Drag/drop is native-shell only. */
        });
    } catch {
      /* getCurrentWebview throws outside the Tauri webview. */
    }

    return () => {
      cancelled = true;
      for (const cleanup of cleanups) cleanup();
    };
  }, []);

  const startConversionRef = useRef<() => void>(() => {});
  const cancelRunningRef = useRef<() => void>(() => {});
  const copyReportRef = useRef<() => void>(() => {});
  const requestCompareRef = useRef<() => void>(() => {});

  useEffect(() => {
    function keydown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null;
      const typing =
        target &&
        (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable);
      if (typing) return;

      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "o") {
        event.preventDefault();
        if (event.shiftKey) void chooseFiles();
        else void chooseFolder();
        return;
      }

      if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
        event.preventDefault();
        void startConversionRef.current();
        return;
      }

      if ((event.ctrlKey || event.metaKey) && event.shiftKey && event.key.toLowerCase() === "c") {
        event.preventDefault();
        void copyReportRef.current();
        return;
      }

      if (event.key === "Escape") {
        if (loupeOpen) {
          event.preventDefault();
          setLoupeOpen(false);
          return;
        }
        if (running) {
          event.preventDefault();
          void cancelRunningRef.current();
          return;
        }
      }

      const queue = filesRef.current;
      if (!queue.length || loupeOpen) return;

      if (event.key === "ArrowRight" || event.key === "ArrowDown") {
        event.preventDefault();
        const index = Math.max(0, queue.findIndex((file) => file.name === selectedNameRef.current));
        const next = queue[Math.min(queue.length - 1, index + 1)];
        if (next) setSelectedName(next.name);
      }
      if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
        event.preventDefault();
        const index = Math.max(0, queue.findIndex((file) => file.name === selectedNameRef.current));
        const next = queue[Math.max(0, index - 1)];
        if (next) setSelectedName(next.name);
      }
      if ((event.key === "Delete" || event.key === "Backspace") && !running) {
        const selected = selectedNameRef.current;
        if (selected) {
          event.preventDefault();
          removeFromQueue(selected);
        }
      }
      if (event.key === "Enter" && cameraUrl) {
        event.preventDefault();
        setLoupeOpen(true);
      }
      if (event.key.toLowerCase() === "l" && cameraUrl) {
        event.preventDefault();
        setLoupeOpen(true);
      }
      if (event.key.toLowerCase() === "c" && cameraUrl) {
        event.preventDefault();
        void requestCompareRef.current();
      }
    }

    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  }, [loupeOpen, running, cameraUrl]);

  const allToolsReady = tools.length === 3 && tools.every((tool) => tool.available);
  const sourceSize = useMemo(() => files.reduce((sum, file) => sum + file.sizeBytes, 0), [files]);
  const failedFiles = useMemo(
    () => files.filter((file) => fileStates[file.name]?.state === "failed"),
    [files, fileStates],
  );
  const activePreset = Object.entries(presets).find(([, value]) =>
    value.targetSizeMb === settings.targetSizeMb && value.maxLongEdge === settings.maxLongEdge
  )?.[0];

  const completed = progress
    ? Math.max(0, progress.index - (progress.state === "processing" ? 1 : 0))
    : 0;
  const batchTotal = progress?.total || files.length;
  const batchPercent = batchTotal ? Math.round((completed / batchTotal) * 100) : 0;
  const currentName = running
    ? progress?.file || (retrying ? "Preparing retry…" : "Preparing first frame…")
    : null;
  const canRetryFailed = !running && allToolsReady && failedFiles.length > 0;
  const elapsedMs = batchStartedAt && running ? Math.max(0, nowMs - batchStartedAt) : 0;
  const remainingMs = estimateRemainingMs(elapsedMs, completed, batchTotal);
  const currentPhase = running
    ? phaseLabel(progress?.phase, settings.format)
    : null;
  // Fixed one-line ThoughtLine for the active file only — never a growing batch log.
  const dockPhaseTrail = useMemo(() => {
    if (!running) return [];
    const activeFile = progress?.file;
    const relevant = activeFile
      ? phaseLog.filter((entry) => entry.file === activeFile)
      : phaseLog;
    return relevant.slice(-2);
  }, [running, progress?.file, phaseLog]);
  const templateExample = settings.nameTemplate
    .replaceAll("{name}", "DSC00135")
    .replaceAll("{edge}", settings.maxLongEdge === 0 ? "original" : String(settings.maxLongEdge))
    .replaceAll("{mode}", settings.mode === "cameraLook" ? "camera" : "develop");
  const batchSizeSummary = useMemo(() => {
    let sourceBytes = 0;
    let outputBytes = 0;
    let counted = 0;
    for (const file of files) {
      const state = fileStates[file.name];
      if (state?.state === "done" && typeof state.sizeBytes === "number") {
        sourceBytes += file.sizeBytes;
        outputBytes += state.sizeBytes;
        counted += 1;
      }
    }
    if (!counted || sourceBytes <= 0) return null;
    const savedRatio = 1 - outputBytes / sourceBytes;
    return { sourceBytes, outputBytes, counted, savedRatio };
  }, [files, fileStates]);

  async function applyQueue(nextFiles: RawFileInfo[], sourceFolder: string, replace: boolean) {
    setFolder(sourceFolder);
    if (!outputCustom) setOutputFolder(defaultOutputFor(sourceFolder));
    setSummary(null);
    setProgress(null);
    setResultThumbs([]);
    setError("");
    if (replace) {
      setFileStates({});
      setFiles(nextFiles);
      setSelectedName(nextFiles[0]?.name ?? null);
      setSheetScrollTop(0);
      setSettings((current) =>
        current.rotateDegrees === 0 ? current : { ...current, rotateDegrees: 0 },
      );
      return;
    }

    setFiles((current) => {
      const merged = [...current];
      for (const file of nextFiles) {
        if (!merged.some((item) => item.path === file.path || item.name === file.name)) {
          merged.push(file);
        }
      }
      merged.sort((a, b) => a.name.localeCompare(b.name));
      return merged;
    });
    setSelectedName((current) => current ?? nextFiles[0]?.name ?? null);
  }

  async function loadFolder(selected: string) {
    try {
      const scan = await invoke<ScanResult>("scan_folder", { folder: selected });
      await applyQueue(scan.files, selected, true);
    } catch (cause) {
      setFiles([]);
      setError(String(cause));
    }
  }

  async function loadPaths(paths: string[], replace: boolean) {
    try {
      const scan = await invoke<ScanResult>("inspect_paths", { paths });
      const sourceFolder = parentFolder(scan.files[0]?.path || paths[0] || "");
      await applyQueue(scan.files, sourceFolder, replace);
    } catch (cause) {
      setError(String(cause));
    }
  }

  async function handleDroppedPaths(paths: string[]) {
    if (!paths.length) return;
    const raws = paths.filter(isRawPath);
    if (raws.length) {
      // Always append on drop so multi-drop builds the queue; folders still replace.
      await loadPaths(raws, false);
      return;
    }
    const first = paths[0];
    if (first) await loadFolder(folderFromDroppedPath(first));
  }

  function removeFromQueue(name: string) {
    if (running) return;
    setFiles((current) => {
      const index = current.findIndex((file) => file.name === name);
      const next = current.filter((file) => file.name !== name);
      setSelectedName((selected) => {
        if (selected !== name) return selected;
        // Keep selection near the removed frame instead of jumping to frame 01.
        return next[Math.min(Math.max(index, 0), next.length - 1)]?.name ?? null;
      });
      if (!next.length) {
        setFolder("");
        if (!outputCustom) setOutputFolder("");
      }
      return next;
    });
    setFileStates((current) => {
      const { [name]: _removed, ...rest } = current;
      return rest;
    });
    setSummary(null);
  }

  async function requestCompareDevelop() {
    if (!selectedFile || developLoading) return;
    if (comparing) {
      setComparing(false);
      return;
    }
    if (developUrl) {
      setComparing(true);
      return;
    }
    setDevelopLoading(true);
    setDevelopError(null);
    try {
      const payload = await invoke<ImagePayload>("load_develop_preview", { path: selectedFile.path });
      setDevelopUrl(payload.dataUrl);
      setComparing(true);
    } catch (cause) {
      setDevelopError(String(cause));
      setComparing(false);
    } finally {
      setDevelopLoading(false);
    }
  }

  async function recheckTools() {
    setDoctorChecking(true);
    setError("");
    try {
      const status = await invoke<ToolStatus[]>("doctor");
      setTools(status);
      setDoctorReady(true);
    } catch (cause) {
      setTools([]);
      setDoctorReady(true);
      setError(`Engine bridge unavailable: ${String(cause)}`);
    } finally {
      setDoctorChecking(false);
    }
  }

  async function chooseFolder() {
    try {
      const selected = await open({ directory: true, multiple: false });
      if (typeof selected === "string") await loadFolder(selected);
    } catch (cause) {
      setError(`Could not open folder picker: ${String(cause)}`);
    }
  }

  async function chooseFiles() {
    try {
      const selected = await open({
        multiple: true,
        filters: [{ name: "RAW photos", extensions: ["arw", "cr2", "cr3", "ARW", "CR2", "CR3"] }],
      });
      const paths = Array.isArray(selected) ? selected : selected ? [selected] : [];
      if (paths.length) await loadPaths(paths, files.length === 0);
    } catch (cause) {
      setError(`Could not open photo picker: ${String(cause)}`);
    }
  }

  async function chooseOutputFolder() {
    const selected = await open({ directory: true, multiple: false });
    if (typeof selected === "string") {
      setOutputFolder(selected);
      setOutputCustom(true);
    }
  }

  function resetOutputFolder() {
    setOutputCustom(false);
    setOutputFolder(defaultOutputFor(folder));
  }

  function applyPreset(name: string) {
    setSettings((current) => ({ ...current, ...presets[name] }));
  }

  async function startConversion() {
    if (!files.length || !allToolsReady || running) return;
    const conversionSettings = {
      ...settings,
      maxParallel: resolveWorkers(settings.parallelMode),
    };
    const sourceFolder = folder || parentFolder(files[0].path);
    setRunning(true);
    setRetrying(false);
    setSummary(null);
    setProgress(null);
    setFileStates({});
    setPhaseLog([]);
    setError("");
    setCopyFlash(null);
    setCompletionToast(null);
    setLastElapsedMs(null);
    const startedAt = Date.now();
    setBatchStartedAt(startedAt);
    setNowMs(startedAt);
    try {
      const result = await invoke<BatchSummary>("start_conversion", {
        folder: sourceFolder,
        settings: conversionSettings,
        outputFolder: effectiveOutput || null,
        sources: files.map((file) => file.path),
      });
      setSummary(result);
      announceCompletion(result);
    } catch (cause) {
      setError(String(cause));
    } finally {
      setLastElapsedMs(Math.max(0, Date.now() - startedAt));
      setRunning(false);
      setRetrying(false);
      setBatchStartedAt(null);
    }
  }

  function announceCompletion(result: BatchSummary) {
    const title = result.cancelled
      ? "Development cancelled"
      : result.failed > 0
        ? "Development finished with failures"
        : "Development complete";
    const body = `${result.converted} converted · ${result.skipped} skipped · ${result.failed} failed`;
    const tone: CompletionToastState["tone"] = result.cancelled
      ? "cancel"
      : result.failed > 0
        ? "warn"
        : "ok";
    setCompletionToast({ title, body, tone });
    if (!result.cancelled && (result.converted > 0 || result.failed > 0 || result.skipped > 0)) {
      if (soundOn && result.converted > 0) playCompletionTone();
      if (notifyOn && result.converted > 0) void notifyCompletion(title, body);
    }
  }

  async function retryFailed(fileName?: string) {
    if (!allToolsReady || running) return;
    const targets = failedFiles.filter((file) => !fileName || file.name === fileName);
    if (!targets.length) return;
    const sourceFolder = folder || parentFolder(targets[0].path);
    const conversionSettings = {
      ...settings,
      maxParallel: resolveWorkers(settings.parallelMode),
    };

    setRunning(true);
    setRetrying(true);
    setSummary(null);
    setProgress(null);
    setPhaseLog([]);
    setError("");
    setCopyFlash(null);
    setLastElapsedMs(null);
    const startedAt = Date.now();
    setBatchStartedAt(startedAt);
    setNowMs(startedAt);
    try {
      const result = await invoke<BatchSummary>("retry_conversion", {
        folder: sourceFolder,
        settings: conversionSettings,
        sources: targets.map((file) => file.path),
        outputFolder: effectiveOutput || null,
      });
      const merged = summaryFromFileStates(files, fileStatesRef.current);
      setSummary({ ...merged, cancelled: result.cancelled, recoveredTempFiles: result.recoveredTempFiles, elapsedMs: result.elapsedMs, phaseTimings: result.phaseTimings });
      announceCompletion({
        ...result,
        converted: result.converted,
        skipped: result.skipped,
        failed: result.failed,
      });
    } catch (cause) {
      setError(String(cause));
    } finally {
      setLastElapsedMs(Math.max(0, Date.now() - startedAt));
      setRunning(false);
      setRetrying(false);
      setBatchStartedAt(null);
    }
  }

  async function cancelRunning() {
    if (!running) return;
    try {
      await invoke("cancel_conversion");
    } catch (cause) {
      setError(String(cause));
    }
  }

  async function revealOutputFolder() {
    const sourceFolder = folder || (files[0] ? parentFolder(files[0].path) : "");
    if (!sourceFolder && !effectiveOutput) return;
    setError("");
    try {
      await invoke("reveal_output_folder", {
        folder: sourceFolder,
        outputFolder: effectiveOutput || null,
      });
    } catch (cause) {
      setError(String(cause));
    }
  }

  const latestOutput = useMemo(() => {
    for (let index = files.length - 1; index >= 0; index -= 1) {
      const info = fileStates[files[index].name];
      if ((info?.state === "done" || info?.state === "skipped") && info.output) {
        return info.output;
      }
    }
    return null;
  }, [files, fileStates]);

  async function copyPath(path: string) {
    try {
      await copyText(path);
      setCopyFlash({ kind: "path", path });
    } catch (cause) {
      setError(String(cause));
    }
  }

  async function copyReport() {
    const reportElapsed = running
      ? elapsedMs || null
      : lastElapsedMs;
    try {
      await copyText(
        buildConversionReport({
          files,
          fileStates,
          settings,
          outputFolder: effectiveOutput,
          summary,
          phaseLog,
          elapsedMs: reportElapsed,
        }),
      );
      setCopyFlash({ kind: "report" });
    } catch (cause) {
      setError(String(cause));
    }
  }

  async function openLatest() {
    if (!latestOutput) return;
    try {
      await invoke("open_path", { path: latestOutput });
    } catch (cause) {
      setError(String(cause));
    }
  }

  startConversionRef.current = () => {
    void startConversion();
  };
  cancelRunningRef.current = () => {
    void cancelRunning();
  };
  copyReportRef.current = () => {
    void copyReport();
  };
  requestCompareRef.current = () => {
    void requestCompareDevelop();
  };

  const sheetRowCount = Math.ceil(files.length / frameCols) || 0;
  const sheetViewportHeight = SHEET_VIEWPORT_ROWS * FRAME_ROW_HEIGHT;
  const sheetStartRow = Math.max(
    0,
    Math.floor(sheetScrollTop / FRAME_ROW_HEIGHT) - FRAME_OVERSCAN_ROWS,
  );
  const sheetEndRow = Math.min(
    sheetRowCount,
    Math.ceil((sheetScrollTop + sheetViewportHeight) / FRAME_ROW_HEIGHT) + FRAME_OVERSCAN_ROWS,
  );
  const sheetStartIndex = sheetStartRow * frameCols;
  const sheetEndIndex = Math.min(files.length, sheetEndRow * frameCols);
  const visibleFrames = files.slice(sheetStartIndex, sheetEndIndex);
  const sheetTopPad = sheetStartRow * FRAME_ROW_HEIGHT;
  const sheetBottomPad = Math.max(0, (sheetRowCount - sheetEndRow) * FRAME_ROW_HEIGHT);

  return (
    <main className="app-shell">
      <div className="ambient" aria-hidden="true" />

      <AnimatePresence>
        {dragging && (
          <motion.div
            className="drop-curtain"
            initial={reduceMotion ? false : { opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <div className="drop-curtain-inner">
              <Images size={24} />
              <strong>Drop the shoot</strong>
              <span>Folder or RAW files · originals stay untouched</span>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <header
        className={`chrome ${isTauriShell() ? "chrome-framed" : ""}`}
        data-tauri-drag-region={isTauriShell() ? true : undefined}
        onDoubleClick={(event) => {
          if (!isTauriShell()) return;
          const target = event.target as HTMLElement | null;
          if (target?.closest("button, a, input")) return;
          void toggleWindowMaximize();
        }}
      >
        <div className="chrome-left">
          <button
            className="wordmark"
            type="button"
            onClick={chooseFolder}
            title="Choose RAW folder"
          >
            <BrandMark size={20} />
            <span className="wordmark-copy">
              <strong>Raw</strong>
              <small>Photo Converter</small>
            </span>
            <span className="sr-only">Choose RAW folder</span>
          </button>
          <div className="chrome-status" aria-label="Engine status">
            <span className={allToolsReady ? "live-dot ready" : "live-dot"} />
            <span>
              {!doctorReady
                ? "Checking engine…"
                : allToolsReady
                  ? "Engine online"
                  : "See setup below"}
            </span>
          </div>
        </div>

        <div className="chrome-right">
          <button
            className={`chrome-icon ${settingsOpen ? "active" : ""}`}
            type="button"
            aria-haspopup="dialog"
            aria-expanded={settingsOpen}
            aria-label="Open settings"
            title="Settings"
            onClick={() => setSettingsOpen(true)}
          >
            <Settings size={15} />
          </button>
          <WindowControls />
        </div>
      </header>

      <div className="studio">
        <section className="masthead">
          <h1>Develop the file.<br /><em>Keep the frame.</em></h1>
        </section>

        <section className="bench">
          <motion.div
            className={`light-table ${files.length ? "inspecting" : ""}`}
            whileHover={files.length || reduceMotion ? undefined : { scale: 1.002 }}
          >
            <span className="frame-corner tl" aria-hidden="true" />
            <span className="frame-corner tr" aria-hidden="true" />
            <span className="frame-corner bl" aria-hidden="true" />
            <span className="frame-corner br" aria-hidden="true" />

            <AnimatePresence mode="wait" initial={false}>
              {!files.length || !selectedFile ? (
                <motion.div
                  key="source-empty"
                  className="light-table-empty"
                  initial={reduceMotion ? false : { opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={reduceMotion ? undefined : { opacity: 0, y: -6 }}
                  transition={{ duration: 0.2, ease: [0.23, 1, 0.32, 1] }}
                >
                  <div className="light-table-topline">
                    <span>01 / SOURCE</span>
                    <span>DROP OR BROWSE</span>
                  </div>
                  <div className="source-center">
                    <div className="source-center-inner">
                      <FolderOpen className="source-icon" size={28} strokeWidth={1.6} aria-hidden="true" />
                      <strong>Bring in a shoot</strong>
                      <p>Drop a folder or RAW files, or browse below.</p>
                    </div>
                  </div>
                  <div className="source-footer source-footer-actions">
                    <span>ARW · CR2 · CR3</span>
                    <div className="source-action-row">
                      <button type="button" className="source-link-button" onClick={() => void chooseFiles()}>
                        <FileImage size={14} />
                        Choose photos
                      </button>
                      <button type="button" className="source-link-button" onClick={() => void chooseFolder()}>
                        <FolderOpen size={14} />
                        Choose folder
                      </button>
                    </div>
                  </div>
                </motion.div>
              ) : (
                <InspectFrame
                  key="inspect-active"
                  label={selectedFile.name.replace(/\.(ARW|CR2|CR3)$/i, "")}
                  frameCount={files.length}
                  sourceBytes={sourceSize}
                  cameraUrl={cameraUrl}
                  cameraLoading={cameraLoading}
                  developUrl={developUrl}
                  developLoading={developLoading}
                  developError={comparing ? developError : cameraError}
                  comparing={comparing}
                  onCompare={() => void requestCompareDevelop()}
                  onLoupe={() => setLoupeOpen(true)}
                  histogram={histogram}
                  histogramMode={histogramMode}
                  onHistogramMode={setHistogramMode}
                  metadata={metadata}
                  palette={files.length === 1 ? palette : []}
                  rotateDegrees={settings.rotateDegrees}
                  onRotate={(rotateDegrees) =>
                    setSettings((current) => ({ ...current, rotateDegrees }))
                  }
                  toolsReady={allToolsReady}
                  footer={
                    <div className="source-footer source-footer-actions">
                      <span className="mono source-path" title={folder || parentFolder(selectedFile.path)}>
                        {folder || parentFolder(selectedFile.path)}
                      </span>
                      <div className="source-action-row">
                        <button type="button" className="source-link-button" onClick={() => void chooseFiles()}>
                          <FileImage size={14} />
                          Add photos
                        </button>
                        <button type="button" className="source-link-button" onClick={() => void chooseFolder()}>
                          <FolderOpen size={14} />
                          Replace folder
                        </button>
                      </div>
                    </div>
                  }
                />
              )}
            </AnimatePresence>
          </motion.div>

          <aside className="recipe-deck">
            <div className="deck-heading">
              <div>
                <span className="section-index">02 / RECIPE</span>
                <h2>Output character</h2>
              </div>
              <SlidersHorizontal size={16} />
            </div>

            <div className="preset-stack" role="group" aria-label="Conversion presets">
              {Object.entries(presets).map(([name, preset]) => (
                <button
                  type="button"
                  key={name}
                  className={`preset-card ${activePreset === name ? "active" : ""}`}
                  onClick={() => applyPreset(name)}
                  aria-pressed={activePreset === name}
                >
                  <span>
                    <strong>{name}</strong>
                    <small>
                      {preset.maxLongEdge === 0
                        ? `Full resolution · ≤ ${preset.targetSizeMb} MB`
                        : `${preset.maxLongEdge}px · ≤ ${preset.targetSizeMb} MB`}
                    </small>
                  </span>
                  {activePreset === name && (
                    <motion.span className="preset-marker" layoutId="preset-marker" transition={{ type: "spring", stiffness: 420, damping: 36 }} />
                  )}
                </button>
              ))}
            </div>

            <div className="precision-row">
              <label>
                <span>Target</span>
                <span className="precision-control">
                  <input
                    type="number"
                    min="0.1"
                    max="100"
                    step="0.1"
                    aria-label="Target size in megabytes"
                    value={settings.targetSizeMb}
                    onChange={(event) =>
                      setSettings((current) => ({ ...current, targetSizeMb: Number(event.target.value) }))
                    }
                  />
                  <em>MB</em>
                </span>
              </label>
              <label>
                <span>Long edge</span>
                <span className="precision-control">
                  <input
                    type="number"
                    min="0"
                    max="8000"
                    step="100"
                    aria-label="Long edge in pixels, 0 for original"
                    value={settings.maxLongEdge}
                    onChange={(event) =>
                      setSettings((current) => ({ ...current, maxLongEdge: Number(event.target.value) }))
                    }
                  />
                  <em>{settings.maxLongEdge === 0 ? "ORIG" : "PX"}</em>
                </span>
              </label>
            </div>

            <div className="recipe-chip-groups" role="group" aria-label="Output recipe">
              <div className="recipe-chip-row">
                <span>Mode</span>
                <RecipeChipGroup
                  layoutId="recipe-mode"
                  aria-label="Conversion mode"
                  value={settings.mode}
                  onChange={(mode) => setSettings((current) => ({ ...current, mode }))}
                  options={[
                    { value: "developRaw" as const, label: "Develop RAW" },
                    { value: "cameraLook" as const, label: "Camera look" },
                  ]}
                />
              </div>
              <div className="recipe-chip-row">
                <span>Format</span>
                <RecipeChipGroup
                  layoutId="recipe-format"
                  aria-label="Output format"
                  value={settings.format}
                  onChange={(format) => setSettings((current) => ({ ...current, format }))}
                  options={(Object.keys(formatLabels) as OutputFormat[]).map((format) => ({
                    value: format,
                    label: formatLabels[format],
                  }))}
                />
              </div>
              <div className="recipe-chip-row">
                <span>Gamut</span>
                <RecipeChipGroup
                  layoutId="recipe-gamut"
                  aria-label="Color gamut"
                  value={settings.colorGamut}
                  onChange={(colorGamut) => setSettings((current) => ({ ...current, colorGamut }))}
                  options={[
                    { value: "srgb" as const, label: "sRGB" },
                    { value: "displayP3" as const, label: "Display P3" },
                  ]}
                />
              </div>
              <div className="recipe-chip-row">
                <span>Name</span>
                <RecipeChipGroup
                  layoutId="recipe-name"
                  aria-label="Filename template"
                  value={settings.nameTemplate}
                  onChange={(nameTemplate) => setSettings((current) => ({ ...current, nameTemplate }))}
                  options={templatePresets.map((template) => ({ value: template, label: template }))}
                />
              </div>
              <p className="template-example mono">Example · {templateExample}.{settings.format === "jpeg" ? "jpg" : settings.format}</p>
            </div>

            <div className="output-row">
              <div className="output-copy">
                <span className="section-index">OUTPUT</span>
                <strong>{folder ? pathTail(effectiveOutput) || "JPG" : "JPG beside source"}</strong>
                <small className="mono">{folder ? effectiveOutput : "Choose a source first"}</small>
              </div>
              <div className="output-actions">
                <button type="button" className="recipe-chip" disabled={!folder || running} onClick={() => void chooseOutputFolder()}>
                  Choose folder
                </button>
                {outputCustom && (
                  <button type="button" className="recipe-chip" disabled={running} onClick={resetOutputFolder}>
                    Use JPG
                  </button>
                )}
              </div>
            </div>

            <div className="recipe-options">
              <label className="option-row">
                <QuietCheck
                  checked={settings.preserveMetadata}
                  onChange={(preserveMetadata) =>
                    setSettings((current) => ({ ...current, preserveMetadata }))
                  }
                />
                <span>
                  <strong>Keep metadata</strong>
                  <small>EXIF · XMP · IPTC</small>
                </span>
              </label>
              <label className="option-row">
                <QuietCheck
                  checked={settings.skipExisting}
                  onChange={(skipExisting) =>
                    setSettings((current) => ({ ...current, skipExisting }))
                  }
                />
                <span>
                  <strong>Protect existing work</strong>
                  <small>Skip outputs already present</small>
                </span>
              </label>
            </div>
          </aside>
        </section>

        <AnimatePresence initial={false}>
        {files.length > 0 && (
        <motion.section
          className="contact-sheet"
          key="contact-sheet"
          initial={reduceMotion ? false : { opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          exit={reduceMotion ? undefined : { opacity: 0, y: 10 }}
          transition={{ duration: 0.24, ease: [0.23, 1, 0.32, 1] }}
        >
          <div className="sheet-heading">
            <div>
              <span className="section-index">04 / CONTACT SHEET</span>
              <h2>Frames in this pass</h2>
            </div>
            <span className="sheet-meta mono">
              {`${files.length} FRAME${files.length === 1 ? "" : "S"} · ${formatMb(sourceSize)}`}
            </span>
          </div>

            <div
              ref={sheetScrollRef}
              className="frame-strip-scroll"
              style={{ maxHeight: sheetViewportHeight }}
              onScroll={(event) => setSheetScrollTop(event.currentTarget.scrollTop)}
            >
              <div style={{ height: sheetTopPad }} aria-hidden="true" />
              <div
                className="frame-strip"
                style={{ gridTemplateColumns: `repeat(${frameCols}, minmax(0, 1fr))` }}
              >
                {visibleFrames.map((file, localIndex) => {
                  const index = sheetStartIndex + localIndex;
                  const state = fileStates[file.name]?.state ?? "waiting";
                  const info = fileStates[file.name];
                  const saved = info?.sizeBytes;
                  return (
                    <motion.div
                      className={`frame-tile ${state} ${progress?.file === file.name ? "current" : ""} ${
                        selectedFile?.name === file.name ? "selected" : ""
                      }`}
                      key={file.path}
                      style={{ minHeight: FRAME_ROW_HEIGHT }}
                      initial={reduceMotion || index > 40 ? false : { opacity: 0, y: 7 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: reduceMotion ? 0 : Math.min(localIndex * 0.02, 0.12) }}
                    >
                      <button
                        type="button"
                        className="frame-select"
                        onClick={() => setSelectedName(file.name)}
                      >
                        <div className="frame-number mono">{String(index + 1).padStart(2, "0")}</div>
                        <div className={`frame-state-mark state-${state ?? "pending"}`} aria-hidden="true">
                          {state === "processing" ? (
                            <RefreshCw size={12} className="spin" strokeWidth={2.1} />
                          ) : state === "failed" || state === "cancelled" ? (
                            <X size={12} strokeWidth={2.2} />
                          ) : state === "done" || state === "skipped" ? (
                            <Check size={12} strokeWidth={2.2} />
                          ) : null}
                        </div>
                        <div className="frame-copy">
                          <strong>{file.name.replace(/\.(ARW|CR2|CR3)$/i, "")}</strong>
                          <span className="mono">
                            {state === "done"
                              ? [
                                  info?.width != null && info?.height != null
                                    ? `${info.width}×${info.height}`
                                    : null,
                                  `Q${info?.quality ?? "-"}`,
                                  `${formatMb(file.sizeBytes)} → ${saved ? formatMb(saved) : "DONE"}`,
                                  info?.targetMet === false ? "above target" : null,
                                ]
                                  .filter(Boolean)
                                  .join(" · ")
                              : state === "processing"
                                ? phaseLabel(info?.phase ?? progress?.phase, settings.format).toUpperCase()
                                : state === "skipped"
                                  ? info?.width != null && info?.height != null
                                    ? `SKIPPED · ${info.width}×${info.height}`
                                    : "SKIPPED"
                                  : state === "cancelled"
                                    ? "CANCELLED"
                                  : state === "failed"
                                    ? "FAILED"
                                    : formatMb(file.sizeBytes)}
                          </span>
                        </div>
                      </button>
                      <div className="frame-actions">
                        {(state === "done" || state === "skipped") && info?.output && (
                          <>
                            <button
                              className="frame-action"
                              type="button"
                              onClick={() =>
                                void invoke("open_path", { path: info.output }).catch((cause) =>
                                  setError(String(cause)),
                                )
                              }
                            >
                              Open
                            </button>
                            <button
                              className="frame-action"
                              type="button"
                              onClick={() =>
                                void invoke("reveal_path", { path: info.output }).catch((cause) =>
                                  setError(String(cause)),
                                )
                              }
                            >
                              Show
                            </button>
                            <button
                              className={
                                copyFlash?.kind === "path" && copyFlash.path === info.output
                                  ? "frame-action frame-action-copied"
                                  : "frame-action"
                              }
                              type="button"
                              aria-live="polite"
                              onClick={() => void copyPath(info.output!)}
                            >
                              {copyFlash?.kind === "path" && copyFlash.path === info.output
                                ? "Copied"
                                : "Copy"}
                            </button>
                          </>
                        )}
                        {state === "failed" && (
                          <button
                            className="frame-action"
                            type="button"
                            disabled={!canRetryFailed}
                            onClick={() => void retryFailed(file.name)}
                          >
                            Retry
                          </button>
                        )}
                        <HoldRemoveButton
                          disabled={running}
                          aria-label={`Hold to remove ${file.name} from queue`}
                          title="Hold to remove from queue"
                          onHold={() => removeFromQueue(file.name)}
                        >
                          Remove
                        </HoldRemoveButton>
                      </div>
                    </motion.div>
                  );
                })}
              </div>
              <div style={{ height: sheetBottomPad }} aria-hidden="true" />
            </div>
        </motion.section>
        )}
        </AnimatePresence>

        <footer className="studio-footer">
          <div className="toolchain">
            {tools.map((tool) => (
              <span
                key={tool.command}
                className={tool.available ? "tool-ready" : "tool-missing"}
                title={
                  tool.available
                    ? `${tool.command} ready`
                    : toolHints[tool.command]?.text || "Missing tool"
                }
              >
                <i />{tool.name}
              </span>
            ))}
            {!tools.length && (
              <span>{doctorReady ? "Local engine unreachable" : "Checking local engine…"}</span>
            )}
          </div>
          <div className="footer-copy">
            <span className="footer-note">
              Size-aware local development for JPEG, WebP, or AVIF, for photographers who care about what survives the export.
            </span>
            <span className="footer-note">RAW originals are never modified.</span>
          </div>
        </footer>

        {!allToolsReady && tools.length > 0 && (
          <section className="setup-guide" role="status" aria-labelledby="setup-guide-title">
            <div className="setup-guide-heading">
              <div>
                <p className="overline" id="setup-guide-title">First-time setup</p>
                <h2>Install the local engine</h2>
                <p>
                  Conversion needs three tools on PATH. Install any that are missing, open a new
                  terminal to verify, then recheck here (or fully restart the app).
                </p>
              </div>
              <button
                type="button"
                className="setup-recheck"
                disabled={doctorChecking}
                onClick={() => void recheckTools()}
              >
                <RefreshCw size={13} className={doctorChecking ? "spin" : undefined} />
                {doctorChecking ? "Checking…" : "Recheck tools"}
              </button>
            </div>

            <ol className="setup-steps">
              {tools.map((tool) => {
                const hint = toolHints[tool.command];
                return (
                  <li
                    key={tool.command}
                    className={tool.available ? "setup-step ready" : "setup-step missing"}
                  >
                    <div className="setup-step-top">
                      <span className="setup-step-mark" aria-hidden="true">
                        {tool.available ? <Check size={12} strokeWidth={2.4} /> : <span />}
                      </span>
                      <div className="setup-step-copy">
                        <strong>{hint?.step || tool.name}</strong>
                        <small>
                          {tool.available
                            ? `${tool.command} ready`
                            : hint?.text || "Install this tool and keep it on PATH."}
                        </small>
                        {!tool.available && hint?.verify && (
                          <code className="mono setup-verify">{hint.verify}</code>
                        )}
                      </div>
                      {!tool.available && hint?.url && (
                        <button
                          type="button"
                          className="setup-get"
                          onClick={() => void invoke("open_url", { url: hint.url })}
                        >
                          Get it
                        </button>
                      )}
                    </div>
                  </li>
                );
              })}
            </ol>

            <p className="setup-guide-note">
              Full walkthrough with PATH tips: docs/WINDOWS-SETUP.md in the project repo.
              After PATH changes, quit the app completely before Recheck if it still looks stuck.
            </p>
          </section>
        )}

        {error && (
          <div className="error-card" role="alert">
            <strong>Needs attention</strong>
            <span>{error}</span>
          </div>
        )}
      </div>

      <LoupePortal
        open={loupeOpen}
        src={cameraUrl}
        label={selectedFile?.name.replace(/\.(ARW|CR2|CR3)$/i, "") || "Preview"}
        onClose={() => setLoupeOpen(false)}
      />

      <AnimatePresence>
        {settingsOpen && (
          <motion.div
            className="settings-curtain"
            key="settings"
            role="presentation"
            initial={reduceMotion ? false : { opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={reduceMotion ? undefined : { opacity: 0 }}
            transition={{ duration: 0.18, ease: [0.23, 1, 0.32, 1] }}
            onMouseDown={(event) => {
              if (event.target === event.currentTarget) setSettingsOpen(false);
            }}
          >
            <motion.div
              className="settings-panel"
              role="dialog"
              aria-modal="true"
              aria-labelledby="settings-title"
              initial={reduceMotion ? false : { opacity: 0, y: 14, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={reduceMotion ? undefined : { opacity: 0, y: 10, scale: 0.98 }}
              transition={{ duration: 0.22, ease: [0.23, 1, 0.32, 1] }}
            >
              <div className="settings-heading">
                <div>
                  <p className="overline" id="settings-title">Settings</p>
                  <h2>Preferences</h2>
                </div>
                <button
                  type="button"
                  className="theme-toggle"
                  aria-label="Close settings"
                  onClick={() => setSettingsOpen(false)}
                >
                  <X size={15} />
                </button>
              </div>

              <div className="pace-control settings-pace">
                <div className="pace-copy">
                  <strong>Theme</strong>
                  <small>System follows Windows light/dark</small>
                </div>
                <RecipeChipGroup
                  className="pace-segments"
                  layoutId="settings-theme"
                  aria-label="Theme"
                  value={themePreference}
                  onChange={setThemePreference}
                  options={[
                    { value: "system" as const, label: "System" },
                    { value: "light" as const, label: "Light" },
                    { value: "dark" as const, label: "Dark" },
                  ]}
                />
              </div>

              <div className="pace-control settings-pace">
                <div className="pace-copy">
                  <strong>Develop pace</strong>
                  <small>
                    {parallelModeHint(settings.parallelMode, settings.maxParallel, deviceCoreCount())}
                  </small>
                </div>
                <RecipeChipGroup
                  className="pace-segments"
                  layoutId="settings-pace"
                  aria-label="Develop pace"
                  value={settings.parallelMode}
                  onChange={(parallelMode) =>
                    setSettings((current) => ({
                      ...current,
                      parallelMode,
                      maxParallel: resolveWorkers(parallelMode),
                    }))
                  }
                  options={[
                    { value: "auto" as const, label: "Auto" },
                    { value: "steady" as const, label: "Steady" },
                    { value: "faster" as const, label: "Faster" },
                  ]}
                />
                <p className="pace-note">
                  Darktable is heavy, so batches stay at most two develops at once until higher
                  concurrency is re-measured on real machines.
                </p>
              </div>

              <div className="settings-options">
                <label className="option-row">
                  <QuietCheck checked={soundOn} onChange={setSoundOn} />
                  <span>
                    <strong>Completion sound</strong>
                    <small>Short chime in the app when a batch finishes</small>
                  </span>
                </label>
                <label className="option-row">
                  <QuietCheck checked={notifyOn} onChange={setNotifyOn} />
                  <span>
                    <strong>Windows notification</strong>
                    <small>System toast when a batch finishes · needs permission</small>
                  </span>
                </label>
              </div>

              <p className="settings-shortcuts mono">
                Shortcuts · Ctrl+O photos · Shift+O folder · Enter loupe · Ctrl+Enter run
              </p>

              <div className="settings-footer">
                <span className="mono settings-status">
                  {soundOn ? <Bell size={12} /> : <BellOff size={12} />}
                  {themePreferenceLabel(themePreference)}
                  {" · "}
                  {parallelModeLabel(settings.parallelMode)}
                </span>
                <button type="button" className="recipe-chip" onClick={() => setSettingsOpen(false)}>
                  Done
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {completionToast && (
          <CompletionToast
            key={`${completionToast.title}-${completionToast.body}`}
            toast={completionToast}
            onDismiss={() => setCompletionToast(null)}
          />
        )}
      </AnimatePresence>

      <AnimatePresence initial={false}>
      {files.length > 0 && summary && !running && resultThumbs.some((item) => item.dataUrl) && (
        <motion.div
          className="dock-thumbs-rail"
          key="dock-thumbs"
          style={{ x: "-50%" }}
          initial={reduceMotion ? false : { opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          exit={reduceMotion ? undefined : { opacity: 0, y: 8 }}
          transition={{ duration: 0.2, ease: [0.23, 1, 0.32, 1] }}
        >
          <ResultThumbStrip items={resultThumbs} />
        </motion.div>
      )}
      </AnimatePresence>

      <AnimatePresence initial={false}>
      {files.length > 0 && (
      <motion.div
        className={[
          "action-dock",
          running ? "is-running" : "",
          summary && !running ? "is-complete" : "",
        ]
          .filter(Boolean)
          .join(" ")}
        key="action-dock"
        style={{ x: "-50%" }}
        initial={reduceMotion ? false : { opacity: 0, y: 18 }}
        animate={{ opacity: 1, y: 0 }}
        exit={reduceMotion ? undefined : { opacity: 0, y: 14 }}
        transition={{ duration: 0.22, ease: [0.23, 1, 0.32, 1] }}
        role="status"
        aria-live="polite"
      >
        {running ? (
          <>
            <div className="dock-context dock-progress">
              <span className="mono phase-swap">
                <AnimatePresence mode="popLayout" initial={false}>
                  <motion.span
                    key={retrying ? "retrying" : currentPhase || "developing"}
                    initial={reduceMotion ? false : { opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={reduceMotion ? undefined : { opacity: 0, y: -6 }}
                    transition={{ duration: 0.14, ease: [0.23, 1, 0.32, 1] }}
                  >
                    {retrying ? "RETRYING" : currentPhase?.toUpperCase() || "DEVELOPING"}
                    {progress ? ` · ${progress.index}/${progress.total}` : ""}
                  </motion.span>
                </AnimatePresence>
              </span>
              <strong title={currentName ?? undefined}>{currentName}</strong>
              {remainingMs != null && (
                <em className="dock-eta mono">~{formatEta(remainingMs)} remaining</em>
              )}
              <div className="dock-track" aria-hidden="true">
                <motion.span
                  animate={{ width: `${batchPercent}%` }}
                  transition={{ duration: reduceMotion ? 0 : 0.28 }}
                />
              </div>
            </div>
            <motion.button
              className="develop-button cancel"
              type="button"
              onClick={() => void cancelRunning()}
              whileTap={reduceMotion ? undefined : { scale: 0.97 }}
              transition={{ duration: 0.12 }}
            >
              <X size={16} />
              Cancel
            </motion.button>
            {dockPhaseTrail.length > 0 && (
              <p className="dock-thought" aria-label="Current file phases">
                {dockPhaseTrail.map((entry, index) => (
                  <span
                    key={entry.id}
                    className={index === dockPhaseTrail.length - 1 ? "is-active" : undefined}
                  >
                    {index > 0 ? <span className="dock-thought-sep" aria-hidden="true">→</span> : null}
                    <span className="mono">
                      {phaseLabel(entry.phase, settings.format)}
                      {entry.elapsedMs != null ? ` · ${(entry.elapsedMs / 1000).toFixed(1)}s` : ""}
                    </span>
                  </span>
                ))}
              </p>
            )}
          </>
        ) : summary ? (
          <>
            <div className="dock-context">
              <span className="mono">
                {summary.cancelled ? "CANCELLED" : summary.failed > 0 ? "PARTIAL" : "COMPLETE"}
                {summary.elapsedMs != null && summary.elapsedMs > 0
                  ? ` · ${formatEta(summary.elapsedMs)}`
                  : ""}
              </span>
              <strong>
                {summary.converted} converted · {summary.skipped} skipped · {summary.failed} failed
                {summary.cancelled ? " · unfinished not saved" : ""}
              </strong>
              {batchSizeSummary && (
                <em className="dock-eta mono">
                  {formatMb(batchSizeSummary.sourceBytes)} → {formatMb(batchSizeSummary.outputBytes)}
                  {batchSizeSummary.savedRatio > 0
                    ? ` · ${Math.round(batchSizeSummary.savedRatio * 100)}% smaller`
                    : ""}
                </em>
              )}
            </div>
            <div className="dock-complete-actions">
              <div className="dock-tool-cluster" role="group" aria-label="Batch results">
                {canRetryFailed && (
                  <button
                    className="dock-ghost"
                    type="button"
                    title="Retry failed files"
                    onClick={() => void retryFailed()}
                  >
                    <RefreshCw size={13} strokeWidth={1.9} aria-hidden="true" />
                    Retry
                  </button>
                )}
                {latestOutput && (
                  <button
                    className="dock-ghost"
                    type="button"
                    title="Open latest output"
                    onClick={() => void openLatest()}
                  >
                    <FileImage size={13} strokeWidth={1.9} aria-hidden="true" />
                    Open
                  </button>
                )}
                <button
                  className="dock-ghost"
                  type="button"
                  title="Copy conversion report"
                  onClick={() => void copyReport()}
                >
                  <Copy size={13} strokeWidth={1.9} aria-hidden="true" />
                  {copyFlash?.kind === "report" ? "Copied" : "Report"}
                </button>
                <button
                  className="dock-ghost"
                  type="button"
                  title="Reveal output folder"
                  onClick={revealOutputFolder}
                >
                  <FolderOpen size={13} strokeWidth={1.9} aria-hidden="true" />
                  Folder
                </button>
              </div>
              <motion.button
                className="develop-button"
                type="button"
                disabled={!allToolsReady}
                onClick={startConversion}
                whileTap={reduceMotion || !allToolsReady ? undefined : { scale: 0.97 }}
                transition={{ duration: 0.12 }}
              >
                Develop {files.length}
                <ArrowRight size={16} />
              </motion.button>
            </div>
          </>
        ) : (
          <>
            <div className="dock-context">
              <span className="mono">{`${files.length} RAW`}</span>
              <strong>
                {`${settings.mode === "cameraLook" ? "Camera" : "Develop"} · ${formatLabels[settings.format]} · ${
                  settings.colorGamut === "displayP3" ? "P3" : "sRGB"
                } · ${settings.maxLongEdge === 0 ? "Original" : `${settings.maxLongEdge}px`} · ≤${settings.targetSizeMb}MB · ${parallelModeLabel(settings.parallelMode)}${
                  settings.rotateDegrees ? ` · ${settings.rotateDegrees}°` : ""
                }`}
              </strong>
            </div>
            <motion.button
              className="develop-button"
              type="button"
              disabled={!allToolsReady}
              onClick={startConversion}
              whileTap={reduceMotion || !allToolsReady ? undefined : { scale: 0.97 }}
              transition={{ duration: 0.12 }}
            >
              Develop {files.length}
              <ArrowRight size={16} />
            </motion.button>
          </>
        )}
      </motion.div>
      )}
      </AnimatePresence>
    </main>
  );
}
