import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from "react";
import { RotateCcw, RotateCw, Search, SplitSquareHorizontal, X } from "lucide-react";

export type HistogramData = {
  red: number[];
  green: number[];
  blue: number[];
  luminance: number[];
  maxCount: number;
  hasShadowClipping: boolean;
  hasHighlightClipping: boolean;
  shadowClipPercent: number;
  highlightClipPercent: number;
};

export type HistogramMode = "rgb" | "luma";

const HISTOGRAM_MAX_EDGE = 720;

function loadImagePixels(dataUrl: string): Promise<{ data: Uint8ClampedArray; width: number; height: number } | null> {
  return new Promise((resolve) => {
    const image = new Image();
    image.onload = () => {
      const naturalW = image.naturalWidth;
      const naturalH = image.naturalHeight;
      if (!naturalW || !naturalH) {
        resolve(null);
        return;
      }
      // Downscale before getImageData so inspect never allocates a full 1280–1600 canvas.
      const scale = Math.min(1, HISTOGRAM_MAX_EDGE / Math.max(naturalW, naturalH));
      const width = Math.max(1, Math.round(naturalW * scale));
      const height = Math.max(1, Math.round(naturalH * scale));
      const canvas = document.createElement("canvas");
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext("2d", { willReadFrequently: true });
      if (!ctx) {
        resolve(null);
        return;
      }
      ctx.drawImage(image, 0, 0, width, height);
      try {
        const pixels = ctx.getImageData(0, 0, width, height);
        resolve({ data: pixels.data, width, height });
      } catch {
        resolve(null);
      }
    };
    image.onerror = () => resolve(null);
    image.src = dataUrl;
  });
}

/** Sampled Rec.709 histogram; mirrors Android HistogramAnalyzer. */
export async function analyzeHistogram(dataUrl: string): Promise<HistogramData | null> {
  const pixels = await loadImagePixels(dataUrl);
  if (!pixels) return null;

  const { data, width, height } = pixels;
  const total = width * height;
  const stride = Math.max(1, Math.floor(Math.sqrt(total / 250_000)));
  const red = new Array(256).fill(0);
  const green = new Array(256).fill(0);
  const blue = new Array(256).fill(0);
  const luminance = new Array(256).fill(0);
  let sampled = 0;

  for (let y = 0; y < height; y += stride) {
    for (let x = 0; x < width; x += stride) {
      const i = (y * width + x) * 4;
      const r = data[i];
      const g = data[i + 1];
      const b = data[i + 2];
      const lum = Math.min(255, Math.max(0, Math.round(0.2126 * r + 0.7152 * g + 0.0722 * b)));
      red[r] += 1;
      green[g] += 1;
      blue[b] += 1;
      luminance[lum] += 1;
      sampled += 1;
    }
  }

  let maxCount = 1;
  for (let i = 1; i < 255; i += 1) {
    maxCount = Math.max(maxCount, red[i], green[i], blue[i], luminance[i]);
  }

  const shadowClipPercent = sampled ? (luminance[0] / sampled) * 100 : 0;
  const highlightClipPercent = sampled ? (luminance[255] / sampled) * 100 : 0;

  return {
    red,
    green,
    blue,
    luminance,
    maxCount,
    hasShadowClipping: shadowClipPercent >= 1,
    hasHighlightClipping: highlightClipPercent >= 1,
    shadowClipPercent,
    highlightClipPercent,
  };
}

/** Dominant colours from the camera preview; single-frame only, omit in bulk. */
export async function extractPalette(dataUrl: string, count = 5): Promise<string[]> {
  const pixels = await loadImagePixels(dataUrl);
  if (!pixels) return [];

  const { data, width, height } = pixels;
  const total = width * height;
  const stride = Math.max(1, Math.floor(Math.sqrt(total / 40_000)));
  const buckets = new Map<number, { r: number; g: number; b: number; n: number }>();

  for (let y = 0; y < height; y += stride) {
    for (let x = 0; x < width; x += stride) {
      const i = (y * width + x) * 4;
      const r = data[i];
      const g = data[i + 1];
      const b = data[i + 2];
      // Skip near-black / near-white extremes so the strip reads as colour, not chrome.
      const lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
      if (lum < 18 || lum > 240) continue;
      const key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
      const bucket = buckets.get(key);
      if (bucket) {
        bucket.r += r;
        bucket.g += g;
        bucket.b += b;
        bucket.n += 1;
      } else {
        buckets.set(key, { r, g, b, n: 1 });
      }
    }
  }

  return [...buckets.values()]
    .sort((a, b) => b.n - a.n)
    .slice(0, count)
    .map((bucket) => {
      const r = Math.round(bucket.r / bucket.n);
      const g = Math.round(bucket.g / bucket.n);
      const b = Math.round(bucket.b / bucket.n);
      return `#${[r, g, b].map((channel) => channel.toString(16).padStart(2, "0")).join("")}`;
    })
    .sort((a, b) => {
      const parse = (hex: string) => {
        const value = Number.parseInt(hex.slice(1), 16);
        const r = (value >> 16) & 255;
        const g = (value >> 8) & 255;
        const bl = value & 255;
        return 0.2126 * r + 0.7152 * g + 0.0722 * bl;
      };
      return parse(a) - parse(b);
    });
}

/** Dominant colours sampled from the camera JPEG — a quiet reference strip, not a hero card. */
export function PaletteStrip({ colours }: { colours: string[] }) {
  if (!colours.length) return null;
  return (
    <div className="palette-strip" aria-label="Colours from this preview">
      <span className="palette-strip-label">Palette</span>
      <div className="palette-swatches">
        {colours.map((colour) => (
          <button
            key={colour}
            type="button"
            className="palette-swatch"
            style={{ background: colour }}
            title={`${colour} · click to copy`}
            aria-label={`Copy ${colour}`}
            onClick={() => void navigator.clipboard?.writeText(colour)}
          />
        ))}
      </div>
    </div>
  );
}

function SlidingChipGroup<T extends string>({
  layoutId,
  value,
  options,
  onChange,
  ariaLabel,
}: {
  layoutId: string;
  value: T;
  options: Array<{ value: T; label: string }>;
  onChange: (value: T) => void;
  ariaLabel: string;
}) {
  const reduceMotion = useReducedMotion();
  return (
    <div className="recipe-chip-set histogram-modes" role="group" aria-label={ariaLabel}>
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={option.value}
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

function pathPoints(bins: number[], maxCount: number, width: number, height: number) {
  if (!bins.length || maxCount <= 0) return "";
  const step = width / (bins.length - 1);
  let d = `M 0 ${height}`;
  bins.forEach((count, index) => {
    const x = index * step;
    const y = height - (count / maxCount) * height;
    d += ` L ${x.toFixed(2)} ${y.toFixed(2)}`;
  });
  d += ` L ${width} ${height} Z`;
  return d;
}

/** Compact exposure plot meant to sit over the preview corner. */
export function HistogramOverlay({
  data,
  mode,
  onMode,
}: {
  data: HistogramData | null;
  mode: HistogramMode;
  onMode: (mode: HistogramMode) => void;
}) {
  const width = 160;
  const height = 36;

  return (
    <div className="histogram-overlay" aria-label="Histogram">
      <div className="histogram-overlay-top">
        <SlidingChipGroup
          layoutId="inspect-histogram-mode"
          value={mode}
          onChange={onMode}
          ariaLabel="Histogram mode"
          options={[
            { value: "rgb" as const, label: "RGB" },
            { value: "luma" as const, label: "Luma" },
          ]}
        />
      </div>
      {!data ? (
        <p className="histogram-empty">…</p>
      ) : (
        <svg
          className="histogram-plot"
          viewBox={`0 0 ${width} ${height}`}
          role="img"
          aria-label="Exposure histogram"
        >
          {mode === "rgb" ? (
            <>
              <path d={pathPoints(data.red, data.maxCount, width, height)} className="hist-r" />
              <path d={pathPoints(data.green, data.maxCount, width, height)} className="hist-g" />
              <path d={pathPoints(data.blue, data.maxCount, width, height)} className="hist-b" />
            </>
          ) : (
            <path d={pathPoints(data.luminance, data.maxCount, width, height)} className="hist-l" />
          )}
        </svg>
      )}
    </div>
  );
}

export function BeforeAfterSplit({
  beforeUrl,
  afterUrl,
}: {
  beforeUrl: string;
  afterUrl: string;
}) {
  const [fraction, setFraction] = useState(0.5);
  const frameRef = useRef<HTMLDivElement>(null);

  function onPointerDown(event: ReactPointerEvent<HTMLDivElement>) {
    event.currentTarget.setPointerCapture(event.pointerId);
    move(event.clientX);
  }

  function onPointerMove(event: ReactPointerEvent<HTMLDivElement>) {
    if (!event.currentTarget.hasPointerCapture(event.pointerId)) return;
    move(event.clientX);
  }

  function move(clientX: number) {
    const rect = frameRef.current?.getBoundingClientRect();
    if (!rect?.width) return;
    setFraction(Math.min(0.92, Math.max(0.08, (clientX - rect.left) / rect.width)));
  }

  return (
    <div
      className="compare-split"
      ref={frameRef}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      role="img"
      aria-label="Drag to compare the camera JPEG with a Develop RAW preview."
    >
      <img
        className="compare-layer"
        src={afterUrl}
        alt="Develop RAW preview"
        draggable={false}
      />
      <div
        className="compare-before"
        style={{ clipPath: `inset(0 ${(1 - fraction) * 100}% 0 0)` }}
      >
        <img
          className="compare-layer"
          src={beforeUrl}
          alt="Camera JPEG preview"
          draggable={false}
        />
      </div>
      <div className="compare-handle" style={{ left: `${fraction * 100}%` }} aria-hidden="true">
        <span />
      </div>
      <span className="compare-label left">Camera</span>
      <span className="compare-label right">Develop</span>
    </div>
  );
}

export function PreviewLoupe({
  src,
  label,
  onClose,
}: {
  src: string;
  label: string;
  onClose: () => void;
}) {
  const reduceMotion = useReducedMotion();
  const [scale, setScale] = useState(1);
  const [fitScale, setFitScale] = useState(1);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [natural, setNatural] = useState({ w: 0, h: 0 });
  const stageRef = useRef<HTMLDivElement>(null);
  const dragging = useRef<{ x: number; y: number; ox: number; oy: number } | null>(null);

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
      if (event.key === "1") {
        setScale(1);
        setOffset({ x: 0, y: 0 });
      }
      if (event.key === "2") {
        setScale(2);
        setOffset({ x: 0, y: 0 });
      }
      if (event.key.toLowerCase() === "f") {
        setScale(fitScale);
        setOffset({ x: 0, y: 0 });
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose, fitScale]);

  useEffect(() => {
    const stage = stageRef.current;
    if (!stage || !natural.w || !natural.h) return;

    const computeFit = () => {
      const rect = stage.getBoundingClientRect();
      return Math.min(rect.width / natural.w, rect.height / natural.h, 1);
    };

    const initial = computeFit();
    setFitScale(initial);
    setScale(initial);
    setOffset({ x: 0, y: 0 });

    const observer = new ResizeObserver(() => {
      setFitScale(computeFit());
    });
    observer.observe(stage);
    return () => observer.disconnect();
  }, [natural.w, natural.h, src]);

  return (
    <motion.div
      className="loupe-layer"
      initial={reduceMotion ? false : { opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      role="dialog"
      aria-modal="true"
      aria-label={`Inspect ${label}`}
    >
      <div className="loupe-chrome">
        <strong>{label}</strong>
        <div className="loupe-actions">
          <button
            type="button"
            className="recipe-chip"
            onClick={() => {
              setScale(fitScale);
              setOffset({ x: 0, y: 0 });
            }}
          >
            Fit
          </button>
          <button
            type="button"
            className="recipe-chip"
            onClick={() => {
              setScale(1);
              setOffset({ x: 0, y: 0 });
            }}
          >
            100%
          </button>
          <button
            type="button"
            className="recipe-chip"
            onClick={() => {
              setScale(2);
              setOffset({ x: 0, y: 0 });
            }}
          >
            200%
          </button>
          <button type="button" className="theme-toggle" aria-label="Close loupe" onClick={onClose}>
            <X size={15} />
          </button>
        </div>
      </div>
      <div
        className="loupe-stage"
        ref={stageRef}
        onWheel={(event) => {
          event.preventDefault();
          setScale((current) =>
            Math.min(8, Math.max(fitScale * 0.85, current * (event.deltaY < 0 ? 1.08 : 0.92))),
          );
        }}
        onPointerDown={(event) => {
          dragging.current = { x: event.clientX, y: event.clientY, ox: offset.x, oy: offset.y };
          event.currentTarget.setPointerCapture(event.pointerId);
        }}
        onPointerMove={(event) => {
          if (!dragging.current) return;
          setOffset({
            x: dragging.current.ox + (event.clientX - dragging.current.x),
            y: dragging.current.oy + (event.clientY - dragging.current.y),
          });
        }}
        onPointerUp={() => {
          dragging.current = null;
        }}
      >
        <img
          className="loupe-image"
          src={src}
          alt={label}
          draggable={false}
          onLoad={(event) => {
            setNatural({
              w: event.currentTarget.naturalWidth,
              h: event.currentTarget.naturalHeight,
            });
          }}
          style={{
            transform: `translate(${offset.x}px, ${offset.y}px) scale(${scale})`,
            transition: reduceMotion ? "none" : "transform 120ms var(--ease)",
          }}
        />
      </div>
      <p className="loupe-hint mono">Scroll to zoom · drag to pan · F fit · 1=100% · 2=200% · Esc</p>
    </motion.div>
  );
}

export type RawMetadata = {
  camera?: string | null;
  make?: string | null;
  lens?: string | null;
  iso?: string | null;
  exposure?: string | null;
  aperture?: string | null;
  focalLength?: string | null;
  dateTimeOriginal?: string | null;
  orientation?: number | null;
};

function cameraLabel(metadata: RawMetadata): string {
  return [metadata.make, metadata.camera].filter(Boolean).join(" ");
}

/** Always-on exposure line for the selected frame (ISO · f/ · shutter · focal · camera). */
function exposureStripParts(metadata: RawMetadata | null): string[] {
  if (!metadata) return [];
  const parts: string[] = [];
  if (metadata.iso) parts.push(`ISO ${metadata.iso}`);
  if (metadata.aperture) parts.push(`f/${metadata.aperture}`);
  if (metadata.exposure) parts.push(`${metadata.exposure}s`);
  if (metadata.focalLength) parts.push(`${metadata.focalLength} mm`);
  const camera = cameraLabel(metadata);
  if (camera) parts.push(camera);
  return parts;
}

/** Fuller fields kept behind disclosure — not duplicated on the exposure strip. */
function moreDetailRows(metadata: RawMetadata | null): Array<[string, string]> {
  if (!metadata) return [];
  const rows: Array<[string, string]> = [];
  if (metadata.lens) rows.push(["Lens", metadata.lens]);
  if (metadata.dateTimeOriginal) rows.push(["Captured", metadata.dateTimeOriginal]);
  if (metadata.orientation != null) rows.push(["Source orientation", String(metadata.orientation)]);
  return rows;
}

export function ExposureStrip({ metadata }: { metadata: RawMetadata | null }) {
  const parts = exposureStripParts(metadata);
  if (!parts.length) return null;

  return (
    <p className="exposure-strip mono" aria-label="Exposure">
      {parts.join(" · ")}
    </p>
  );
}

/** Extra EXIF in an upward popover so opening it never reflows the light-table. */
export function PhotoDetails({ metadata }: { metadata: RawMetadata | null }) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const rows = moreDetailRows(metadata);

  useEffect(() => {
    if (!open) return undefined;
    const onPointer = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onPointer);
    window.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onPointer);
      window.removeEventListener("keydown", onKey);
    };
  }, [open]);

  if (!rows.length) return null;

  return (
    <div className="photo-details" ref={rootRef}>
      <button
        type="button"
        className="photo-details-toggle"
        aria-expanded={open}
        aria-haspopup="dialog"
        onClick={() => setOpen((current) => !current)}
      >
        {open ? "Hide details" : "More details"}
      </button>
      {open && (
        <dl className="photo-details-panel" role="dialog" aria-label="More photo details">
          {rows.map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd className="mono">{value}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  );
}

/** Inspect content that lives inside the light-table frame (01 Source → 03 Inspect). */
export function InspectFrame({
  label,
  frameCount,
  sourceBytes,
  cameraUrl,
  cameraLoading,
  developUrl,
  developLoading,
  developError,
  comparing,
  onCompare,
  onLoupe,
  histogram,
  histogramMode,
  onHistogramMode,
  metadata,
  palette,
  rotateDegrees,
  onRotate,
  toolsReady,
  footer,
}: {
  label: string;
  frameCount: number;
  sourceBytes: number;
  cameraUrl: string | null;
  cameraLoading: boolean;
  developUrl: string | null;
  developLoading: boolean;
  developError: string | null;
  comparing: boolean;
  onCompare: () => void;
  onLoupe: () => void;
  histogram: HistogramData | null;
  histogramMode: HistogramMode;
  onHistogramMode: (mode: HistogramMode) => void;
  metadata: RawMetadata | null;
  palette?: string[];
  rotateDegrees: 0 | 90 | 180 | 270;
  onRotate: (degrees: 0 | 90 | 180 | 270) => void;
  toolsReady: boolean;
  footer: ReactNode;
}) {
  const showCompare = comparing && cameraUrl && developUrl;
  const reduceMotion = useReducedMotion();
  const compareDisabled = developLoading || !toolsReady || (!cameraUrl && !cameraLoading);
  const compareTitle = !toolsReady
    ? "Install darktable (and peers) first · see First-time setup"
    : cameraLoading
      ? "Waiting for camera preview…"
      : !cameraUrl
        ? "Needs an embedded camera JPEG to compare"
        : comparing
          ? "Show camera JPEG only"
          : "Develop a RAW preview and wipe between Camera · Develop";

  // Continuous spin for CSS so 270°→0° animates +90°, not the long way through 180°.
  // Canonical rotateDegrees (0/90/180/270) still drives Develop output.
  const spinRef = useRef(rotateDegrees as number);
  const [spinDegrees, setSpinDegrees] = useState<number>(rotateDegrees);

  useEffect(() => {
    const normalized = ((spinRef.current % 360) + 360) % 360;
    if (normalized === rotateDegrees) return;
    spinRef.current = rotateDegrees;
    setSpinDegrees(rotateDegrees);
  }, [rotateDegrees]);

  function turn(delta: 90 | -90) {
    const next = (((rotateDegrees + delta) % 360) + 360) % 360;
    const nextSpin = spinRef.current + delta;
    spinRef.current = nextSpin;
    setSpinDegrees(nextSpin);
    onRotate(next as 0 | 90 | 180 | 270);
  }

  // Transform-only rotate keeps the frame box fixed; quarter-turns scale so the
  // image still fits inside the same viewport instead of clipping hard.
  const quarterTurn = rotateDegrees % 180 !== 0;
  const mediaTransform = {
    transform: `rotate(${spinDegrees}deg)${quarterTurn ? " scale(0.72)" : ""}`,
  };

  const formatMb =
    sourceBytes > 0
      ? `${(sourceBytes / 1024 / 1024).toFixed(sourceBytes / 1024 / 1024 >= 10 ? 1 : 2)} MB`
      : null;

  return (
    <motion.div
      className="inspect-frame"
      initial={reduceMotion ? false : { opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={reduceMotion ? undefined : { opacity: 0 }}
      transition={{ duration: 0.2, ease: [0.23, 1, 0.32, 1] }}
    >
      <div className="light-table-topline">
        <span>03 / INSPECT</span>
        <span className="inspect-frame-meta mono">
          {label}
          {frameCount > 1 ? ` · ${String(frameCount).padStart(2, "0")} frames` : ""}
          {formatMb ? ` · ${formatMb}` : ""}
        </span>
      </div>

      <div className="inspect-frame-actions">
        <button
          type="button"
          className="recipe-chip"
          disabled={!cameraUrl}
          onClick={onLoupe}
          title="Open 100% loupe"
        >
          <Search size={12} />
          <span className="recipe-chip-label">100%</span>
        </button>
        <button
          type="button"
          className={comparing ? "recipe-chip active" : "recipe-chip"}
          disabled={compareDisabled}
          title={compareTitle}
          aria-pressed={comparing}
          onClick={onCompare}
        >
          {comparing && (
            <motion.span
              className="recipe-chip-pill"
              layoutId="inspect-compare"
              transition={
                reduceMotion
                  ? { duration: 0 }
                  : { type: "spring", stiffness: 440, damping: 34, mass: 0.7 }
              }
            />
          )}
          <SplitSquareHorizontal size={12} />
          <span className="recipe-chip-label">
            {developLoading ? "Developing…" : comparing ? "Camera only" : "Compare Develop"}
          </span>
        </button>
      </div>

      <div className="inspect-stage inspect-stage-frame">
        {!cameraUrl ? (
          <div className="inspect-empty">
            <strong>{cameraLoading ? "Loading camera preview…" : "No camera preview"}</strong>
            <span>
              {cameraLoading
                ? "Extracting the embedded JPEG."
                : developError || "Embedded JPEG unavailable. Develop RAW still works."}
            </span>
          </div>
        ) : (
          <div className="inspect-media" style={mediaTransform}>
            {showCompare ? (
              <BeforeAfterSplit beforeUrl={cameraUrl} afterUrl={developUrl} />
            ) : (
              <img className="inspect-image" src={cameraUrl} alt={`Camera preview of ${label}`} />
            )}
          </div>
        )}

        {cameraUrl && (
          <HistogramOverlay data={histogram} mode={histogramMode} onMode={onHistogramMode} />
        )}

        {comparing && !developError && showCompare && (
          <p className="inspect-compare-hint">Drag to wipe Camera · Develop</p>
        )}

        <div className="inspect-rotate" role="group" aria-label="Output orientation">
          <button
            type="button"
            className="inspect-rotate-btn"
            aria-label="Rotate left"
            title="Rotate left · applies to Develop output"
            onClick={() => turn(-90)}
          >
            <RotateCcw size={13} strokeWidth={1.9} />
          </button>
          <span className="inspect-rotate-deg mono">{rotateDegrees}°</span>
          <button
            type="button"
            className="inspect-rotate-btn"
            aria-label="Rotate right"
            title="Rotate right · applies to Develop output"
            onClick={() => turn(90)}
          >
            <RotateCw size={13} strokeWidth={1.9} />
          </button>
        </div>
      </div>

      {developError && comparing && <p className="inspect-note">{developError}</p>}
      <div className="exposure-row">
        <ExposureStrip metadata={metadata} />
        <PhotoDetails
          key={[
            metadata?.lens,
            metadata?.dateTimeOriginal,
            metadata?.orientation,
          ].join("|")}
          metadata={metadata}
        />
      </div>
      {palette && palette.length > 0 && (
        <div className="inspect-frame-meta-row">
          <PaletteStrip colours={palette} />
        </div>
      )}

      {footer}
    </motion.div>
  );
}

export function ResultThumbStrip({
  items,
}: {
  items: Array<{ name: string; dataUrl: string | null }>;
}) {
  const reduceMotion = useReducedMotion();
  const visible = useMemo(() => items.filter((item) => item.dataUrl), [items]);
  if (!visible.length) return null;

  return (
    <div className="result-thumbs" aria-label="Saved result thumbnails">
      {visible.slice(0, 8).map((item, index) => (
        <motion.figure
          key={item.name}
          initial={reduceMotion ? false : { opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.2, delay: reduceMotion ? 0 : Math.min(index * 0.04, 0.2), ease: [0.23, 1, 0.32, 1] }}
        >
          <img src={item.dataUrl!} alt={item.name} />
          <figcaption className="mono">{item.name.replace(/\.(jpe?g|webp|avif)$/i, "")}</figcaption>
        </motion.figure>
      ))}
      {visible.length > 8 && (
        <div className="result-thumb-more">+{visible.length - 8}</div>
      )}
    </div>
  );
}

export function LoupePortal({
  open,
  src,
  label,
  onClose,
}: {
  open: boolean;
  src: string | null;
  label: string;
  onClose: () => void;
}) {
  return (
    <AnimatePresence>
      {open && src && <PreviewLoupe key="loupe" src={src} label={label} onClose={onClose} />}
    </AnimatePresence>
  );
}
