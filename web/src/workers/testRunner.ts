/// <reference lib="webworker" />

/**
 * Runs the candidate's code against the problem's test cases.
 *
 * <p>This is a Web Worker on purpose (CLAUDE.md §6): the candidate's code never
 * touches the page, cannot reach the DOM or our session cookies, and — most
 * importantly — an infinite loop freezes only this worker, which the caller
 * then terminates. A main-thread `eval` would hang the entire tab, and an
 * infinite loop is a *likely* outcome in a timed interview, not an edge case.
 */

export interface WorkerTest {
  args: unknown[]
  expected: unknown
}

export interface WorkerRequest {
  code: string
  entryPoint: string
  tests: WorkerTest[]
  match: 'exact' | 'unordered'
}

export interface CaseResult {
  index: number
  passed: boolean
  args: unknown[]
  expected: unknown
  actual?: unknown
  error?: string
}

export interface WorkerResponse {
  ok: boolean
  /** Set when the code could not even be loaded (syntax error, no function). */
  fatal?: string
  results: CaseResult[]
}

function describe(value: unknown): string {
  if (value === undefined) return 'undefined'
  try {
    return JSON.stringify(value) ?? String(value)
  } catch {
    return String(value)
  }
}

/** Structural equality over JSON-ish values. */
function deepEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true
  if (typeof a !== typeof b) return false
  if (a === null || b === null) return false

  if (Array.isArray(a) && Array.isArray(b)) {
    if (a.length !== b.length) return false
    return a.every((item, i) => deepEqual(item, b[i]))
  }
  if (Array.isArray(a) !== Array.isArray(b)) return false

  if (typeof a === 'object') {
    const ka = Object.keys(a as object)
    const kb = Object.keys(b as object)
    if (ka.length !== kb.length) return false
    return ka.every((k) =>
      deepEqual((a as Record<string, unknown>)[k], (b as Record<string, unknown>)[k]),
    )
  }

  // NaN never equals itself, but two NaN results should count as a match.
  return Number.isNaN(a) && Number.isNaN(b)
}

/**
 * Order-insensitive comparison for problems like Two Sum, where "return the
 * indices in any order" means [1,0] is as correct as [0,1].
 */
function unorderedEqual(a: unknown, b: unknown): boolean {
  if (!Array.isArray(a) || !Array.isArray(b)) return deepEqual(a, b)
  if (a.length !== b.length) return false

  const remaining = [...b]
  for (const item of a) {
    const i = remaining.findIndex((candidate) => deepEqual(item, candidate))
    if (i === -1) return false
    remaining.splice(i, 1)
  }
  return true
}

self.onmessage = (event: MessageEvent<WorkerRequest>) => {
  const { code, entryPoint, tests, match } = event.data
  const equal = match === 'unordered' ? unorderedEqual : deepEqual

  let fn: unknown
  try {
    // Evaluate the candidate's source, then hand back the entry point. Indirect
    // construction keeps this out of the worker's own scope.
    // eslint-disable-next-line no-new-func
    fn = new Function(`${code}\n; return typeof ${entryPoint} === 'function' ? ${entryPoint} : undefined;`)()
  } catch (e) {
    const response: WorkerResponse = {
      ok: false,
      fatal: e instanceof Error ? `${e.name}: ${e.message}` : String(e),
      results: [],
    }
    self.postMessage(response)
    return
  }

  if (typeof fn !== 'function') {
    self.postMessage({
      ok: false,
      fatal: `No function named ${entryPoint} was defined.`,
      results: [],
    } satisfies WorkerResponse)
    return
  }

  const results: CaseResult[] = tests.map((test, index) => {
    try {
      // Fresh copy of the args each run: a candidate who mutates the input
      // should not corrupt the next test case.
      const args = structuredClone(test.args)
      const actual = (fn as (...a: unknown[]) => unknown)(...args)
      return {
        index,
        passed: equal(actual, test.expected),
        args: test.args,
        expected: test.expected,
        actual,
      }
    } catch (e) {
      return {
        index,
        passed: false,
        args: test.args,
        expected: test.expected,
        error: e instanceof Error ? `${e.name}: ${e.message}` : String(e),
      }
    }
  })

  self.postMessage({ ok: results.every((r) => r.passed), results } satisfies WorkerResponse)
}

export { describe }
