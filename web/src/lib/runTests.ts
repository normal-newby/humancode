import type { Problem, RunResult } from '../api/types'
import type { CaseResult, WorkerResponse } from '../workers/testRunner'

/** Hard ceiling for the whole suite. An interview candidate will write an infinite loop. */
const TIMEOUT_MS = 3000

export interface LocalRunResult extends RunResult {
  cases: CaseResult[]
  fatal?: string
  timedOut: boolean
}

function summarise(value: unknown): string {
  if (value === undefined) return 'undefined'
  try {
    const text = JSON.stringify(value)
    return text === undefined ? String(value) : text
  } catch {
    return String(value)
  }
}

function firstFailureMessage(cases: CaseResult[]): string | null {
  const failure = cases.find((c) => !c.passed)
  if (!failure) return null
  if (failure.error) {
    return `${failure.error} on input ${summarise(failure.args)}`
  }
  return `${summarise(failure.args)} → expected ${summarise(failure.expected)}, got ${summarise(failure.actual)}`
}

/**
 * Runs the candidate's code against the problem's tests in a throwaway worker.
 *
 * <p>The worker cannot interrupt itself, so the timeout lives out here: if it
 * has not answered in time we terminate it and report a timeout. That is the
 * only thing standing between a `while (true)` and a dead tab.
 */
export function runTests(problem: Problem, code: string): Promise<LocalRunResult> {
  const started = performance.now()

  return new Promise((resolve) => {
    const worker = new Worker(new URL('../workers/testRunner.ts', import.meta.url), {
      type: 'module',
    })

    let settled = false
    const finish = (result: LocalRunResult) => {
      if (settled) return
      settled = true
      worker.terminate()
      resolve(result)
    }

    const timer = window.setTimeout(() => {
      finish({
        passed: false,
        passedCount: 0,
        failedCount: problem.tests.length,
        firstFailure: `Timed out after ${TIMEOUT_MS}ms — likely an infinite loop.`,
        durationMs: Math.round(performance.now() - started),
        cases: [],
        timedOut: true,
      })
    }, TIMEOUT_MS)

    worker.onmessage = (event: MessageEvent<WorkerResponse>) => {
      window.clearTimeout(timer)
      const { ok, fatal, results } = event.data
      const passedCount = results.filter((r) => r.passed).length

      finish({
        passed: ok && results.length > 0,
        passedCount,
        failedCount: results.length - passedCount,
        firstFailure: fatal ?? firstFailureMessage(results),
        durationMs: Math.round(performance.now() - started),
        cases: results,
        fatal,
        timedOut: false,
      })
    }

    worker.onerror = (event) => {
      window.clearTimeout(timer)
      finish({
        passed: false,
        passedCount: 0,
        failedCount: problem.tests.length,
        firstFailure: event.message || 'The test runner crashed.',
        durationMs: Math.round(performance.now() - started),
        cases: [],
        timedOut: false,
      })
    }

    worker.postMessage({
      code,
      entryPoint: problem.entryPoint,
      tests: problem.tests,
      match: problem.match === 'unordered' ? 'unordered' : 'exact',
    })
  })
}
