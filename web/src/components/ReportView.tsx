import { useState } from 'react'
import type { ReportCard } from '../api/types'
import { MetaLine, type TurnStamp } from './MetaLine'
import { Block, Result } from './Transcript'
import { TypedText } from './TypedText'
import { BOOT_COMMAND } from './BootSequence'
import { WindowTab } from './WindowTab'

interface Props {
  report: ReportCard
  onRestart: () => void
}

/**
 * The report card (CLAUDE.md: "verdict, insults, begrudging compliments,
 * similar problems"). It stays inside the log's own grammar rather than
 * becoming a results page — the verdict is one more `▌` line from the
 * interviewer, typed out the same way every prompt is, with the rest hanging
 * under it as `└` asides. No pass/fail banner, no score, no colour-coded
 * verdict — see UI-DESIGN.md §4.7 on why a verdict is read, not displayed.
 *
 * <p>Two blocks, in the order codex prints them: the run's own summary first —
 * what the process did, in numbers, the way a CLI signs off with its token
 * usage — and then the human's answer to it. Keeping the numbers out of the
 * verdict block is what stops them reading as a score: they belong to the
 * session, not to the judgment, and the judgment is words.
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

  const submitLine = [
    `${stats.submitCount} submission${stats.submitCount === 1 ? '' : 's'}`,
    `impatience ended at ${stats.finalImpatience} percent`,
  ].join(', ')

  return (
    <main className="flex min-h-screen flex-col bg-canvas">
      <WindowTab status="session ended" />

      <div className="mx-auto w-full max-w-[84ch] px-6 pt-12 pb-16">
        {/* What the process did. Codex signs off with its own usage; this is
            that line, and it is the only place the numbers appear. */}
        <Block marker="•" tone="text-faint">
          <p className="text-[15px] leading-relaxed text-sub">codex exited</p>
          <MetaLine stamp={stamp} live />
          <Result>{submitLine}</Result>
        </Block>

        {/* What they made of it. */}
        <div className="mt-6">
          <Block marker="▌" tone="text-accent">
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
        </div>

        {verdictDone && (
          <button
            type="button"
            onClick={onRestart}
            className="mt-10 text-sm lowercase text-accent underline-offset-4 transition-opacity hover:underline"
          >
            <span aria-hidden className="text-faint">$ </span>
            {BOOT_COMMAND}
          </button>
        )}
      </div>
    </main>
  )
}
