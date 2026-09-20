import type { ProblemFile, TelemetryItem } from '../api/types'
import { EditorPane } from './EditorPane'
import { MetaLine, type TurnStamp } from './MetaLine'

interface Props {
  files: ProblemFile[]
  /** Deltas within the turn in progress, ticking. */
  stamp: TurnStamp
  onTelemetry: (item: TelemetryItem) => void
  onCodeChange: (file: string, code: string) => void
  onSubmit: () => void
}

/**
 * Your turn, in progress (UI-DESIGN.md §4.3).
 *
 * <p>The editor is not a composer — it is the block you are currently emitting,
 * headed by the tool call it amounts to and stamped underneath with what it has
 * cost so far. It now has a column of its own rather than a fixed-height strip
 * pinned under the scrolled-back log, so it fills whatever height that column
 * has to give it.
 *
 * <p>No border, no fill. A box would make it an input again, and the whole
 * point is that you are the one producing output here.
 */
export function LiveTurn({ files, stamp, onTelemetry, onCodeChange, onSubmit }: Props) {
  // The accurate, per-file record lives in the closed turn's meta line (built
  // from what was actually touched); this header is a live label and settles
  // for "how many files" once there is more than one, rather than trying to
  // track which one is active a second layer up.
  const label = files.length === 1 ? files[0].name : `${files.length} files`

  return (
    <div className="flex min-h-0 w-full flex-1 flex-col px-6 py-4">
      <div className="grid min-h-0 flex-1 grid-cols-[1.25rem_1fr] gap-x-2">
        <span aria-hidden className="text-accent">
          •
        </span>
        <div className="flex min-h-0 min-w-0 flex-col">
          <p className="text-[15px] leading-relaxed text-ink">Editing {label}</p>

          <div className="mt-1 min-h-0 flex-1">
            <EditorPane
              files={files}
              onTelemetry={onTelemetry}
              onCodeChange={onCodeChange}
              onRun={onSubmit}
            />
          </div>

          <MetaLine stamp={stamp} live />
        </div>
      </div>
    </div>
  )
}
