/**
 * Hold-to-confirm remove control adapted from React Bits Hold Button
 * (https://reactbits.dev/micro/hold-button). Interaction mechanics retained;
 * visual language restyled for Quiet Darkroom (no glow, no trash glyph).
 */
import {
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
  type PointerEvent,
  type ReactNode,
} from "react";

type Phase = "idle" | "holding" | "done";
type Input = "pointer" | "key" | null;

const HIT_PAD = 10;
const LINEAR = (t: number) => t;
const EASE_OUT = (t: number) => 1 - Math.pow(1 - t, 3);

export type HoldRemoveButtonProps = {
  children?: ReactNode;
  doneLabel?: ReactNode;
  holdTime?: number;
  releaseTime?: number;
  disabled?: boolean;
  onHold?: () => void;
  className?: string;
  "aria-label"?: string;
  title?: string;
};

export function HoldRemoveButton({
  children = "Remove",
  doneLabel = "Removed",
  holdTime = 900,
  releaseTime = 180,
  disabled = false,
  onHold,
  className = "",
  "aria-label": ariaLabel,
  title,
}: HoldRemoveButtonProps) {
  const [phase, setPhase] = useState<Phase>("idle");
  const [input, setInput] = useState<Input>(null);
  const phaseRef = useRef<Phase>("idle");
  const inputRef = useRef<Input>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const gesture = useRef<{ pointerId: number | null; start: number; rect: DOMRect | null }>({
    pointerId: null,
    start: 0,
    rect: null,
  });
  const timers = useRef({ complete: 0, reset: 0 });
  const hintId = useId();

  const go = (next: Phase, kind: Input = null) => {
    phaseRef.current = next;
    inputRef.current = kind;
    setPhase(next);
    setInput(kind);
  };

  const clearTimers = () => {
    window.clearTimeout(timers.current.complete);
    window.clearTimeout(timers.current.reset);
  };

  const motion = useRef({ raf: 0, p: 0, from: 0, to: 0, start: 0 });

  const complete = () => {
    if (phaseRef.current !== "holding") return;
    if (performance.now() - gesture.current.start < holdTime - 50) return;
    clearTimers();
    go("done", inputRef.current);
    onHold?.();
  };

  const drive = (to: number, duration: number, ease: (t: number) => number) => {
    const m = motion.current;
    cancelAnimationFrame(m.raf);
    m.from = m.p;
    m.to = to;
    m.start = performance.now();
    const step = (now: number) => {
      const t = duration > 0 ? Math.min(1, (now - m.start) / duration) : 1;
      m.p = m.from + (m.to - m.from) * ease(t);
      buttonRef.current?.style.setProperty("--hb-p", m.p.toFixed(4));
      if (t < 1) {
        m.raf = requestAnimationFrame(step);
        return;
      }
      m.raf = 0;
      if (m.to === 1) complete();
    };
    m.raf = requestAnimationFrame(step);
  };

  const begin = (kind: Input) => {
    if (disabled || phaseRef.current !== "idle") return false;
    const button = buttonRef.current;
    if (!button) return false;
    gesture.current.start = performance.now();
    gesture.current.rect = button.getBoundingClientRect();
    go("holding", kind);
    drive(1, holdTime, LINEAR);
    timers.current.complete = window.setTimeout(complete, holdTime + 100);
    return true;
  };

  const release = (_options: { drifted?: boolean } = {}) => {
    if (phaseRef.current !== "holding") return;
    clearTimers();
    go("idle");
    drive(0, releaseTime, EASE_OUT);
  };
  const releaseRef = useRef(release);
  releaseRef.current = release;

  const handlePointerDown = (e: PointerEvent<HTMLButtonElement>) => {
    if (e.button !== 0 || !e.isPrimary || gesture.current.pointerId !== null) return;
    if (!begin("pointer")) return;
    gesture.current.pointerId = e.pointerId;
    try {
      e.currentTarget.setPointerCapture(e.pointerId);
    } catch {
      /* capture optional */
    }
  };

  const endPointer = (e: PointerEvent<HTMLButtonElement>, options?: { drifted?: boolean }) => {
    if (e.pointerId !== gesture.current.pointerId) return;
    gesture.current.pointerId = null;
    try {
      if (e.currentTarget.hasPointerCapture(e.pointerId)) {
        e.currentTarget.releasePointerCapture(e.pointerId);
      }
    } catch {
      /* ignore */
    }
    release(options);
  };

  const handlePointerMove = (e: PointerEvent<HTMLButtonElement>) => {
    if (e.pointerId !== gesture.current.pointerId) return;
    const r = gesture.current.rect;
    if (!r) return;
    const out =
      e.clientX < r.left - HIT_PAD ||
      e.clientX > r.right + HIT_PAD ||
      e.clientY < r.top - HIT_PAD ||
      e.clientY > r.bottom + HIT_PAD;
    if (out) endPointer(e, { drifted: true });
  };

  const handlePointerLeave = (e: PointerEvent<HTMLButtonElement>) => {
    if (e.pointerType !== "touch") endPointer(e, { drifted: true });
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLButtonElement>) => {
    if (e.key === "Escape") {
      if (inputRef.current === "key") release({ drifted: true });
      return;
    }
    if (e.key === " " || e.key === "Enter") {
      e.preventDefault();
      if (!e.repeat) begin("key");
    }
  };

  const handleKeyUp = (e: KeyboardEvent<HTMLButtonElement>) => {
    if (e.key === " " || e.key === "Enter") {
      e.preventDefault();
      if (inputRef.current === "key") release();
    }
  };

  useLayoutEffect(() => {
    const button = buttonRef.current;
    if (!button) return undefined;
    const measure = () => {
      button.style.setProperty("--hb-w", `${button.offsetWidth}px`);
      button.style.setProperty("--hb-h", `${button.offsetHeight}px`);
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(button);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    if (phase !== "holding") return undefined;
    const cancel = () => releaseRef.current({ drifted: true });
    const onVisibility = () => {
      if (document.hidden) cancel();
    };
    window.addEventListener("blur", cancel);
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      window.removeEventListener("blur", cancel);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [phase]);

  useEffect(() => {
    const t = timers.current;
    const m = motion.current;
    return () => {
      window.clearTimeout(t.complete);
      window.clearTimeout(t.reset);
      cancelAnimationFrame(m.raf);
    };
  }, []);

  const labels = (
    <>
      <span className="hold-remove__idle">{children}</span>
      <span className="hold-remove__done" aria-hidden="true">
        {doneLabel}
      </span>
    </>
  );

  const cssVars = {
    "--hb-hold": `${holdTime}ms`,
    "--hb-release": `${releaseTime}ms`,
    "--hb-cycles": holdTime / 1100,
  } as CSSProperties;

  return (
    <button
      ref={buttonRef}
      type="button"
      disabled={disabled}
      className={`hold-remove${className ? ` ${className}` : ""}`}
      data-phase={phase}
      data-input={input ?? undefined}
      aria-label={ariaLabel}
      aria-describedby={hintId}
      title={title}
      style={cssVars}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={(e) => endPointer(e)}
      onPointerCancel={(e) => endPointer(e, { drifted: true })}
      onLostPointerCapture={(e) => endPointer(e, { drifted: true })}
      onPointerLeave={handlePointerLeave}
      onKeyDown={handleKeyDown}
      onKeyUp={handleKeyUp}
      onContextMenu={(e) => e.preventDefault()}
    >
      <span className="hold-remove__label">{labels}</span>
      <span className="hold-remove__clip" aria-hidden="true">
        <span className="hold-remove__fill">
          <span className="hold-remove__label hold-remove__label--fill">{labels}</span>
        </span>
        <span className="hold-remove__crest">
          <span className="hold-remove__label hold-remove__label--fill">{labels}</span>
        </span>
      </span>
      <span className="hold-remove__sr" id={hintId}>
        Press and hold for {Math.round(holdTime / 100) / 10} seconds to remove from queue
      </span>
    </button>
  );
}
