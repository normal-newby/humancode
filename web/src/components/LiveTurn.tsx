import type { TelemetryItem } from '../api/types'
import { EditorPane } from './EditorPane'
import { MetaLine, type TurnStamp } from './MetaLine'

interface Props {
  file: string
  language: string
  initialCode: string
  /** Deltas within the turn in progress, ticking. */
  stamp: TurnStamp
  onTelemetry: (item: TelemetryItem) => void
  onCodeChange: (code: string) => void
  onSubmit: () => void
}

/**
 * Your turn, in progress (UI-DESIGN.md §4.3).
 *
 * <p>The editor is not a composer — it is the block you are currently emitting,
 * headed by the tool call it amounts to and stamped underneath with what it has
 * cost so far. It is pinned to the bottom because that is where a terminal
 * keeps the output still being written; everything finished scrolls above it.
 *
 * <p>No border, no fill. A box would make it an input again, and the whole
 * point is that you are the one producing output here.
 */
export function LiveTurn({
  file,
  language,
  initialCode,
  stamp,
  onTelemetry,
  onCodeChange,
  onSubmit,
}: Props) {
  return (
    <div className="mx-auto w-full max-w-[84ch] px-6">
      <div className="grid grid-cols-[1.25rem_1fr] gap-x-2">
        <span aria-hidden className="text-accent">
          ⏺
        </span>
        <div className="min-w-0">
          <p className="text-[15px] leading-relaxed text-ink">Write({file})</p>

          <div className="mt-1 h-[38vh]">
            <EditorPane
              language={language}
              initialCode={initialCode}
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
