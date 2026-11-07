import { getCurrentWindow } from "@tauri-apps/api/window";
import { Copy, Minus, Square, X } from "lucide-react";
import { useEffect, useState } from "react";

export function isTauriShell() {
  return typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;
}

async function withWindow(run: (win: ReturnType<typeof getCurrentWindow>) => Promise<void>) {
  if (!isTauriShell()) return;
  try {
    await run(getCurrentWindow());
  } catch {
    /* Browser / missing capability: ignore. */
  }
}

export function WindowControls() {
  const [maximized, setMaximized] = useState(false);
  const [available, setAvailable] = useState(false);

  useEffect(() => {
    if (!isTauriShell()) return;
    let cancelled = false;
    const win = getCurrentWindow();
    setAvailable(true);
    void win.isMaximized().then((value) => {
      if (!cancelled) setMaximized(value);
    });
    const unlisten = win.onResized(() => {
      void win.isMaximized().then((value) => {
        if (!cancelled) setMaximized(value);
      });
    });
    return () => {
      cancelled = true;
      void unlisten.then((stop) => stop());
    };
  }, []);

  if (!available) return null;

  return (
    <div className="window-controls" role="group" aria-label="Window">
      <button
        type="button"
        className="window-control"
        aria-label="Minimize"
        onClick={() => void withWindow((win) => win.minimize())}
      >
        <Minus size={14} strokeWidth={1.75} />
      </button>
      <button
        type="button"
        className="window-control"
        aria-label={maximized ? "Restore" : "Maximize"}
        onClick={() =>
          void withWindow(async (win) => {
            await win.toggleMaximize();
            setMaximized(await win.isMaximized());
          })
        }
      >
        {maximized ? <Copy size={12} strokeWidth={1.75} /> : <Square size={12} strokeWidth={1.75} />}
      </button>
      <button
        type="button"
        className="window-control window-control-close"
        aria-label="Close"
        onClick={() => void withWindow((win) => win.close())}
      >
        <X size={14} strokeWidth={1.75} />
      </button>
    </div>
  );
}

export function toggleWindowMaximize() {
  return withWindow(async (win) => {
    await win.toggleMaximize();
  });
}
