import type { Metrics, Problem } from '../api/types'
import type { LocalRunResult } from '../lib/runTests'
import { ImpatienceMeter } from './ImpatienceMeter'

interface Props {
  impatience: number
  elapsedSeconds: number
  metrics: Metrics | null
  problem: Problem
  running: boolean
  lastRun: LocalRunResult | null
  onRun: () => void
  onEnd: () => void
}

function clock(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

/**
 * The instruments (UI-DESIGN.md §4.3). Ordered by how much each one should
 * worry you. No headings, no boxes — groups are separated by space alone.
 */
export function LeftRail({
  impatience,
  elapsedSeconds,
  metrics,
  problem,
  running,
  lastRun,
  onRun,
  onEnd,
}: Props) {
  return (
    <aside className="dimmable flex min-h-0 flex-col">
      {/* Only the instruments scroll. The actions stay put — a `run` button
          that can scroll out of reach is a bug you find during a demo. */}
      <div className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto pr-2">
        <ImpatienceMeter impatience={impatience} />

        <div className="text-base tabular-nums text-ink">{clock(elapsedSeconds)}</div>

        <dl className="space-y-1.5">
          <Stat label="written" value={metrics?.charsInserted} />
          <Stat label="deleted" value={metrics?.charsDeleted} />
          <Stat label="pastes" value={metrics?.pasteCount} />
        </dl>

        <div className="space-y-3">
          <div>
            <div className="text-xs lowercase text-ink">{problem.title.toLowerCase()}</div>
            <div className="text-[11px] lowercase text-sub">{problem.difficulty}</div>
          </div>

          {problem.examples.slice(0, 2).map((example, index) => (
            <div key={index} className="space-y-0.5 text-[11px] leading-relaxed break-words">
              <div className="text-sub">
                <span className="text-faint">in&nbsp;&nbsp;</span>
                {example.input}
              </div>
              <div className="text-sub">
                <span className="text-faint">out&nbsp;</span>
                {example.output}
              </div>
            </div>
          ))}
        </div>
      </div>

      {lastRun && (
        <div className="shrink-0 space-y-1 pt-6">
          <div className="flex justify-between text-[11px]">
            <span className="lowercase tracking-wide text-sub">tests</span>
            <span
              className={`tabular-nums ${lastRun.passed ? 'text-calm' : 'text-hot'}`}
            >
              {lastRun.passedCount}/{lastRun.passedCount + lastRun.failedCount}
            </span>
          </div>
          {!lastRun.passed && lastRun.firstFailure && (
            <p className="text-[10px] leading-relaxed break-words text-faint">
              {lastRun.firstFailure}
            </p>
          )}
        </div>
      )}

      <div className="flex shrink-0 gap-4 pt-6 text-xs lowercase">
        <button
          type="button"
          onClick={onRun}
          disabled={running}
          className="text-sub underline-offset-4 transition-colors hover:text-ink hover:underline disabled:opacity-40"
        >
          {running ? 'running…' : 'run'}
        </button>
        <button
          type="button"
          onClick={onEnd}
          className="text-sub underline-offset-4 transition-colors hover:text-ink hover:underline"
        >
          end
        </button>
      </div>
    </aside>
  )
}

function Stat({ label, value }: { label: string; value: number | undefined }) {
  return (
    <div className="flex justify-between text-[11px]">
      <dt className="lowercase tracking-wide text-sub">{label}</dt>
      <dd className="tabular-nums text-ink">{value ?? '—'}</dd>
    </div>
  )
}
