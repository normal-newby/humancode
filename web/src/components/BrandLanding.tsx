import { useEffect, useRef, useState } from 'react'
import { BOOT_COMMAND } from './BootSequence'
import { DifficultyPicker } from './DifficultyPicker'
import { HandleLine } from './HandleLine'
import { LanguagePicker, type SessionLanguage } from './LanguagePicker'
import { ProblemTypePicker } from './ProblemTypePicker'
import type { Difficulty, ProblemType, UserProfile } from '../api/types'
import type { IdentityStatus } from '../hooks/useIdentity'

interface Props {
  difficulty: Difficulty
  language: SessionLanguage
  problemType: ProblemType
  starting: boolean
  error: string | null
  identityStatus: IdentityStatus
  profile: UserProfile | null
  identityError: string | null
  onDifficultyChange: (value: Difficulty) => void
  onLanguageChange: (value: SessionLanguage) => void
  onProblemTypeChange: (value: ProblemType) => void
  onClaim: (handle: string) => void
  onSignOut: () => void
  onLeaderboard: () => void
  onBegin: () => void
}

const scrollTo = (id: string) => {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/** Reveals the real product UI as it enters the reader's view. */
function ProductShowcase() {
  const element = useRef<HTMLElement>(null)
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const node = element.current
    if (!node) return

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setVisible(true)
      return
    }

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return
        setVisible(true)
        observer.disconnect()
      },
      { threshold: 0.18 },
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  return (
    <figure
      ref={element}
      data-visible={visible}
      className="product-showcase mx-auto mt-12 max-w-[1120px]"
    >
      <div className="bg-[#0b0c0e] p-2 shadow-[0_20px_45px_rgba(11,12,14,0.16)] sm:p-3">
        <img
          src="/brand/coding-workspace.png"
          alt="The gpdetox coding workspace with interviewer feedback and a multi-file editor"
          width={2560}
          height={1440}
          className="block h-auto w-full"
        />
      </div>
      <figcaption className="mt-4 flex flex-wrap items-baseline justify-between gap-3 font-mono text-[10px] uppercase tracking-[0.13em] text-[#5d636e]">
        <span>the actual workspace</span>
        <span>scripted demo</span>
      </figcaption>
    </figure>
  )
}

/**
 * The public-facing front door. The actual interview remains the terminal
 * experience; this page gives the product a proper, usable introduction.
 */
export function BrandLanding({
  difficulty,
  language,
  problemType,
  starting,
  error,
  identityStatus,
  profile,
  identityError,
  onDifficultyChange,
  onLanguageChange,
  onProblemTypeChange,
  onClaim,
  onSignOut,
  onLeaderboard,
  onBegin,
}: Props) {
  return (
    <main className="min-h-screen overflow-x-hidden bg-[#f6f5f1] font-sans text-[#0b0c0e]">
      <header className="mx-auto flex max-w-[1440px] items-center justify-between px-5 py-5 sm:px-8 lg:px-12">
        <button type="button" onClick={() => scrollTo('top')} className="shrink-0" aria-label="Back to top">
          <img src="/brand/gpdetox-logo.svg" alt="gpdetox" className="h-7 w-auto sm:h-8" />
        </button>
        <nav aria-label="Primary navigation" className="hidden items-center gap-7 font-mono text-[11px] uppercase tracking-[0.12em] text-[#5d636e] sm:flex">
          <button type="button" onClick={() => scrollTo('how-it-works')} className="hover:text-[#0b0c0e]">how it works</button>
          <button type="button" onClick={() => scrollTo('challenge')} className="hover:text-[#0b0c0e]">start a session</button>
          <button type="button" onClick={onLeaderboard} className="hover:text-[#0b0c0e]">leaderboard</button>
        </nav>
        <button type="button" onClick={() => scrollTo('challenge')} className="font-mono text-[11px] uppercase tracking-[0.12em] underline underline-offset-4 hover:text-[#246fc2]">
          try it
        </button>
      </header>

      <section id="top" className="mx-auto grid max-w-[1440px] gap-10 px-5 pb-16 pt-10 sm:px-8 sm:pt-16 lg:grid-cols-[1.02fr_0.98fr] lg:items-center lg:px-12 lg:pb-24">
        <div className="min-w-0 max-w-2xl">
          <p className="font-mono text-[11px] uppercase tracking-[0.16em] text-[#5d636e]">the interview, inverted</p>
          <h1 className="mt-5 text-[clamp(3.5rem,8vw,7rem)] font-medium leading-[0.95] tracking-[-0.065em]">
            <span className="block">You are</span>
            <span className="block">the model</span>
            <span className="block">now.</span>
          </h1>
          <p className="mt-8 max-w-md text-lg leading-relaxed text-[#4d535c] sm:text-xl">
            An AI interviewer gives you a real app task. You write the code. It watches the clock and forms opinions.
          </p>
          <div className="mt-9 flex flex-wrap items-center gap-x-6 gap-y-3 font-mono text-xs">
            <button type="button" onClick={() => scrollTo('challenge')} className="bg-[#0b0c0e] px-5 py-3 text-white transition-colors hover:bg-[#292d34]">
              choose a challenge
            </button>
            <button type="button" onClick={() => scrollTo('how-it-works')} className="underline underline-offset-4 hover:text-[#246fc2]">
              see the premise ↓
            </button>
          </div>
        </div>
        <div className="relative min-w-0 bg-[#0b0c0e] p-3 sm:p-5">
          <img src="/brand/app-preview.png" alt="The gpdetox editor and live interviewer commentary" width={2560} height={1440} className="block h-auto w-full" />
          <p className="mt-3 font-mono text-[10px] uppercase tracking-[0.13em] text-[#949aa5]">live product interface · scripted demo</p>
        </div>
      </section>

      <section id="how-it-works" className="border-y border-black/10 bg-[#0b0c0e] text-white">
        <div className="mx-auto grid max-w-[1440px] gap-10 px-5 py-16 sm:px-8 lg:grid-cols-[0.9fr_1.1fr] lg:px-12 lg:py-24">
          <div>
            <img src="/brand/gpdetox-logo-white.svg" alt="gpdetox" className="h-10 w-auto" />
            <p className="mt-7 max-w-sm text-xl leading-relaxed text-[#c7cbd1]">It feels like a coding agent session because the roles are flipped.</p>
          </div>
          <ol className="grid gap-8 sm:grid-cols-3">
            {[
              ['01', 'They prompt.', 'Pick a web or Python task built around a small app, not a throwaway puzzle.'],
              ['02', 'You generate.', 'Edit the files in the browser while the interviewer follows your actual work.'],
              ['03', 'They react.', 'Pauses, changes, and submissions get a response. The clock is always watching.'],
            ].map(([number, title, copy]) => (
              <li key={number} className="border-t border-white/20 pt-4">
                <span className="font-mono text-xs text-[#5aa7ff]">{number}</span>
                <h2 className="mt-8 text-2xl font-medium tracking-tight">{title}</h2>
                <p className="mt-3 text-sm leading-relaxed text-[#a9afb9]">{copy}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="mx-auto max-w-[1440px] px-5 py-16 sm:px-8 lg:px-12 lg:py-24">
        <div className="mx-auto flex max-w-[1120px] items-end justify-between gap-6">
          <div>
            <p className="font-mono text-[11px] uppercase tracking-[0.16em] text-[#5d636e]">not another coding drill</p>
            <h2 className="mt-3 text-4xl font-medium tracking-[-0.05em] sm:text-6xl">Your code. Their patience.</h2>
          </div>
          <p className="hidden max-w-[28ch] text-sm leading-relaxed text-[#5d636e] lg:block">Build a small interface or track down a bug in code that already exists.</p>
        </div>
        <ProductShowcase />
        <p className="mx-auto mt-10 max-w-[42ch] text-center text-lg leading-relaxed text-[#4d535c]">No pass counter. No green banner. Hand it over and read the room.</p>
      </section>

      <section id="challenge" className="bg-[#5aa7ff] px-5 py-16 sm:px-8 lg:px-12 lg:py-24">
        <div className="mx-auto grid max-w-[1040px] gap-10 lg:grid-cols-[0.8fr_1.2fr]">
          <div>
            <p className="font-mono text-[11px] uppercase tracking-[0.16em] text-[#123c68]">start a live session</p>
            <h2 className="mt-4 text-[clamp(2.5rem,4.5vw,3.25rem)] font-medium leading-[1.05] tracking-[-0.055em]">Make it<br />uncomfortable.</h2>
            <p className="mt-6 max-w-sm text-base leading-relaxed text-[#173d66]">Choose the kind of work you want to be judged on. Then the actual session begins.</p>
          </div>
          <div className="landing-session-setup min-w-0 bg-[#0b0c0e] px-6 py-7 font-mono text-white sm:px-9 sm:py-10">
            <p className="font-mono text-xs text-[#8f97a3]">session setup</p>
            <HandleLine
              status={identityStatus}
              profile={profile}
              error={identityError}
              onClaim={onClaim}
              onSignOut={onSignOut}
              onLeaderboard={onLeaderboard}
              disabled={starting}
            />
            <DifficultyPicker value={difficulty} onChange={onDifficultyChange} disabled={starting} />
            <LanguagePicker value={language} onChange={onLanguageChange} disabled={starting} />
            <ProblemTypePicker value={problemType} onChange={onProblemTypeChange} disabled={starting} />
            <button type="button" onClick={onBegin} disabled={starting} className="mt-10 bg-white px-5 py-3 font-mono text-xs lowercase text-[#0b0c0e] transition-colors hover:bg-[#5aa7ff] disabled:cursor-wait disabled:opacity-50">
              <span aria-hidden>$ </span>{starting ? 'finding someone to judge you…' : BOOT_COMMAND}
            </button>
            {error && <p role="alert" className="mt-5 font-mono text-xs text-[#ff9389]">{error}</p>}
          </div>
        </div>
      </section>

      <footer className="mx-auto flex max-w-[1440px] flex-col gap-5 px-5 py-8 font-mono text-[11px] uppercase tracking-[0.12em] text-[#5d636e] sm:flex-row sm:items-center sm:justify-between sm:px-8 lg:px-12">
        <img src="/brand/gpdetox-logo.svg" alt="gpdetox" className="h-5 w-auto" />
        <p>model: you / approval: never asked</p>
      </footer>
    </main>
  )
}
