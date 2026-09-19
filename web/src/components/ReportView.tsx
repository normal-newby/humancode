import { useState } from 'react'
import type { ReportCard } from '../api/types'
import { MetaLine, type TurnStamp } from './MetaLine'
import { Block, Result } from './Transcript'
import { TypedText } from './TypedText'

interface Props {
  report: ReportCard
  onRestart: () => void
}

/**
 * The report card (CLAUDE.md: "verdict, insults, begrudging compliments,
 * similar problems"). It stays inside the log's own grammar rather than
 * becoming a results page — the verdict is one more `>` line from the
 * interviewer, typed out the same way every prompt is, with the rest hanging
 * under it as `⎿` asides. No pass/fail banner, no score, no colour-coded
 * verdict — see UI-DESIGN.md §4.7 on why a verdict is read, not displayed.
 */
export function ReportView({ report, onRestart }: Props) {
  const [verdictDone, setVerdictDone] = useState(false)
  const { stats } = report

  const stamp: TurnStamp = {
    elapsedSeconds: stats.elapsedSeconds,
    written: stats.charsWritten,
    deleted: stats.charsDeleted,
    pastes: stats.pasteCount,
    idleSeconds: null,
  }

  const runLine = [
    `${stats.runCount} run${stats.runCount === 1 ? '' : 's'}`,
    stats.failedRunCount > 0 ? `${stats.failedRunCount} failed` : null,
    `impatience ended at ${stats.finalImpatience} percent`,
  ]
    .filter(Boolean)
    .join(', ')

  return (
    <main className="flex min-h-screen justify-center bg-canvas px-6 py-16">
      <div className="w-full max-w-[84ch]">
        <Block marker=">" tone="text-accent">
          <TypedText
            text={report.verdict}
            animate
            onDone={() => setVerdictDone(true)}
            className="text-[15px] leading-relaxed text-ink"
          />

          {verdictDone && (
            <div className="animate-meta-in">
              {report.insults.map((line, index) => (
                <Result key={`insult-${index}`}>{line}</Result>
              ))}
              {report.compliments.map((line, index) => (
                <Result key={`compliment-${index}`} tone="text-ink">
                  {line}
                </Result>
              ))}

              <MetaLine stamp={stamp} live />
              <Result>{runLine}</Result>
              {report.similarProblems.length > 0 && (
                <Result>similar problems: {report.similarProblems.join(', ')}</Result>
              )}

              {report.canned && (
                <span
                  className="mt-1 block text-[9px] text-faint"
                  title="fallback report, not the model"
                >
                  canned
                </span>
              )}
            </div>
          )}
        </Block>

        {verdictDone && (
          <button
            type="button"
            onClick={onRestart}
            className="mt-10 text-sm lowercase text-accent underline-offset-4 transition-opacity hover:underline"
          >
            begin another
          </button>
        )}
      </div>
    </main>
  )
}
