import { useState } from 'react'
import type { ReportCard, UserProfile } from '../api/types'
import { useCountUp } from '../hooks/useCountUp'
import { MetaLine, type TurnStamp } from './MetaLine'
import { Block, Result } from './Transcript'
import { TypedText } from './TypedText'
import { PixelFace, moodForImpatience } from './PixelFace'
import { BOOT_COMMAND } from './BootSequence'
import { WindowTab } from './WindowTab'

interface Props {
  report: ReportCard
  /** The saved rating, already folded in — passed straight through to the tab. */
  rating: number
  handle?: string
  /**
   * Their standing after this session, straight from the server — the delta has
   * already been applied to it (`UserService.recordSession`). Null when nobody
   * was signed in, and then the rating line is all there is to say.
   */
  profile: UserProfile | null
  onRestart: () => void
  onLeaderboard: () => void
}

/**
 * The report card (CLAUDE.md: "verdict, insults, begrudging compliments,
 * similar problems"). It stays inside the log's own grammar rather than
 * becoming a results page — the verdict is one more `▌` line from the human,
 * typed out the same way every prompt is, with the rest hanging under it as `└`
 * asides. No pass/fail banner, no score, no colour-coded verdict — see
 * UI-DESIGN.md §4.7 on why a verdict is read, not displayed.
 *
 * <p>Two blocks, in the order codex prints them: the run's own summary first —
 * what the process did, in numbers, the way a CLI signs off with its token
 * usage — and then the human's answer to it. Keeping the numbers out of the
 * verdict block is what stops them reading as a score: they belong to the
 * session, not to the judgment, and the judgment is words.
 *
 * <p>That ordering does more work now than it used to. The verdict is no longer
 * a summary of the session, it is the human opening the app you handed them and
 * saying what they think of it — flat and unmoved when it works, annoyed and
 * possessive when it does not. So the process signing off first is the beat
 * before they look, and nothing here should be tempted into rendering the
 * outcome that decided the tone. The server never sends it.
 */
export function ReportView({
  report,
  rating,
  handle,
  profile,
  onRestart,
  onLeaderboard,
}: Props) {
  const [verdictDone, setVerdictDone] = useState(false)
  const { stats } = report
  // Off the meter, never off the outcome — see PixelFace.moodForImpatience.
  const mood = moodForImpatience(stats.finalImpatience)

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

  // Counts from 0 toward the delta once the verdict has finished revealing —
  // the same beat the insults and compliments wait for, so the rating lands
  // as part of what they said, not ahead of it.
  const ratingValue = useCountUp(report.ratingDelta, verdictDone)
  const ratingPositive = report.ratingDelta > 0
  const ratingSign = ratingPositive ? '+' : ''

  return (
    <main className="flex min-h-screen flex-col bg-canvas">
      <WindowTab status="session ended" rating={rating} handle={handle} />

      <div className="mx-auto w-full max-w-[84ch] px-6 pt-12 pb-16">
        {/* What the process did. Codex signs off with its own usage; this is
            that line, and it is the only place the numbers appear. */}
        <Block marker="•" tone="text-faint">
          <p className="text-[15px] leading-relaxed text-sub">gpdetox exited</p>
          <MetaLine stamp={stamp} live />
          <Result>{submitLine}</Result>
        </Block>

        {/* What they made of it. The face lands before the sentence does,
            which is the same order it happens in a room. */}
        <div className="mt-8 animate-turn-in">
          <PixelFace mood={mood} className="h-16 w-16" animate />
        </div>

        <div className="mt-4">
          <Block
            marker={<PixelFace mood={mood} className="mt-[0.2rem] h-4 w-4" />}
            tone="text-accent"
          >
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
                {/* The saved rating moving, counted rather than dropped in whole —
                    it is the one number in this whole screen, so it gets a beat
                    of its own instead of arriving silently like the others. */}
                {report.ratingDelta !== 0 && (
                  <Result tone={ratingPositive ? 'text-calm' : 'text-hot'}>
                    rating {ratingSign}
                    {ratingValue}
                  </Result>
                )}
                {/* What that leaves them on, and where it puts them. Only with
                    a handle behind it: a signed-out total is this browser's
                    private tally and has no rank to report, so claiming one
                    would be an empty number. */}
                {profile && (
                  <Result tone="text-faint">
                    {profile.handle} now on {profile.rating > 0 ? '+' : ''}
                    {profile.rating}
                    {profile.rank > 0 && ` · rank ${profile.rank}`}
                    <span aria-hidden className="mx-2">
                      ·
                    </span>
                    <button
                      type="button"
                      onClick={onLeaderboard}
                      className="lowercase text-accent underline-offset-4 transition-opacity hover:underline"
                    >
                      the board
                    </button>
                  </Result>
                )}
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
          <div className="mt-10 flex items-baseline gap-6">
            <button
              type="button"
              onClick={onRestart}
              className="text-sm lowercase text-accent underline-offset-4 transition-opacity hover:underline"
            >
              <span aria-hidden className="text-faint">$ </span>
              {BOOT_COMMAND}
            </button>
            {/* Shown whether or not they have a handle: signed out it is how
                they find out what the board even is, signed in it is the same
                command they ran to get here. */}
            <button
              type="button"
              onClick={onLeaderboard}
              className="text-sm lowercase text-sub underline-offset-4 transition-colors hover:text-ink hover:underline"
            >
              <span aria-hidden className="text-faint">$ </span>
              {BOOT_COMMAND} --leaderboard
            </button>
          </div>
        )}
      </div>
    </main>
  )
}
