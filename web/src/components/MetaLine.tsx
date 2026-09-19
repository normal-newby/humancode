import { clock, compact } from '../lib/format'

/**
 * What the candidate did during one beat of the interview. Frozen the instant
 * its turn is spoken — except for the live one under the pinned problem
 * statement, which ticks and carries session totals.
 */
export interface TurnStamp {
  /** Elapsed session time when the turn was spoken. */
  elapsedSeconds: number
  /** Characters typed since the previous turn. */
  written: number
  /** Characters deleted since the previous turn. */
  deleted: number
  /** Pastes since the previous turn. */
  pastes: number
  /** Only set when an idle trigger fired — the receipt for the line above. */
  idleSeconds: number | null
}

interface Props {
  stamp: TurnStamp
  /** The live variant skips the entrance animation; it is always on screen. */
  live?: boolean
  /** Fallback line rather than the model. Dev marker, invisible at a demo. */
  canned?: boolean
}

/**
 * The meta line (UI-DESIGN.md §5).
 *
 * <p>`└  04:12 · ↑ 412 · ↓ 180 · ⧉ 1 · idle 22s`
 *
 * <p>Time first, always. The character counts are deliberately formatted like a
 * token-usage readout — the resemblance is the joke and the units are the ones
 * that actually matter to a person typing.
 *
 * <p>Glyphs are decorative and `aria-hidden`; the whole row is announced once
 * from the `sr-only` sentence, so nothing reads as "up arrow 412".
 */
export function MetaLine({ stamp, live = false, canned = false }: Props) {
  const idle = stamp.idleSeconds !== null
  // Zero segments are dropped — except on an idle turn, where "you typed
  // nothing" is the entire point of the line.
  const segments: string[] = [clock(stamp.elapsedSeconds)]
  if (stamp.written > 0 || idle) segments.push(`↑ ${compact(stamp.written)}`)
  if (stamp.deleted > 0 || idle) segments.push(`↓ ${compact(stamp.deleted)}`)
  if (stamp.pastes > 0) segments.push(`⧉ ${stamp.pastes}`)
  if (idle) segments.push(`idle ${Math.round(stamp.idleSeconds ?? 0)}s`)

  const spoken = [
    `${clock(stamp.elapsedSeconds)} elapsed`,
    `${stamp.written} characters written`,
    `${stamp.deleted} deleted`,
    stamp.pastes > 0 ? `${stamp.pastes} paste${stamp.pastes === 1 ? '' : 's'}` : null,
    idle ? `idle ${Math.round(stamp.idleSeconds ?? 0)} seconds` : null,
    canned ? 'canned line' : null,
  ]
    .filter(Boolean)
    .join(', ')

  return (
    <div
      className={`mt-1 flex items-baseline gap-2 text-xs tabular-nums text-sub ${
        live ? '' : 'animate-meta-in'
      }`}
    >
      <span className="sr-only">{spoken}</span>

      <span aria-hidden className="text-faint">
        └
      </span>
      <span aria-hidden className="flex flex-wrap items-baseline gap-x-2">
        {segments.map((segment, index) => (
          <span key={`${index}-${segment}`}>
            {index > 0 && <span className="mr-2 text-faint">·</span>}
            {segment}
          </span>
        ))}
      </span>

      {canned && (
        <span aria-hidden className="ml-auto text-[9px] text-faint" title="fallback line, not the model">
          canned
        </span>
      )}
    </div>
  )
}
