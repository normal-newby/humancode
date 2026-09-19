#!/usr/bin/env node
/**
 * Runs every bank problem's reference solution against its own test cases.
 *
 * A wrong hand-written expectation is invisible until a candidate writes a
 * correct answer and the interviewer calls them wrong — the worst possible
 * place to find it. This catches it in one second.
 *
 * Usage: npm run check:problems   (from web/)
 */
import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const problemsDir = resolve(here, '../../src/main/resources/problems')

function deepEqual(a, b) {
  if (a === b) return true
  if (typeof a !== typeof b) return false
  if (a === null || b === null) return false
  if (Array.isArray(a) && Array.isArray(b)) {
    return a.length === b.length && a.every((x, i) => deepEqual(x, b[i]))
  }
  if (Array.isArray(a) !== Array.isArray(b)) return false
  if (typeof a === 'object') {
    const ka = Object.keys(a)
    const kb = Object.keys(b)
    return ka.length === kb.length && ka.every((k) => deepEqual(a[k], b[k]))
  }
  return Number.isNaN(a) && Number.isNaN(b)
}

function unorderedEqual(a, b) {
  if (!Array.isArray(a) || !Array.isArray(b)) return deepEqual(a, b)
  if (a.length !== b.length) return false
  const rest = [...b]
  for (const item of a) {
    const i = rest.findIndex((c) => deepEqual(item, c))
    if (i === -1) return false
    rest.splice(i, 1)
  }
  return true
}

const show = (v) => {
  try {
    return JSON.stringify(v) ?? String(v)
  } catch {
    return String(v)
  }
}

let failures = 0
let totalCases = 0

for (const file of readdirSync(problemsDir).filter((f) => f.endsWith('.json'))) {
  const problem = JSON.parse(readFileSync(join(problemsDir, file), 'utf8'))
  const { id, entryPoint, referenceSolution, tests, match } = problem
  const equal = match === 'unordered' ? unorderedEqual : deepEqual

  let fn
  try {
    fn = new Function(`${referenceSolution}\n; return ${entryPoint};`)()
  } catch (e) {
    console.error(`✗ ${id}: reference solution failed to load — ${e.message}`)
    failures++
    continue
  }

  if (typeof fn !== 'function') {
    console.error(`✗ ${id}: reference solution does not define ${entryPoint}()`)
    failures++
    continue
  }

  let passed = 0
  for (const [i, test] of tests.entries()) {
    totalCases++
    let actual
    try {
      actual = fn(...structuredClone(test.args))
    } catch (e) {
      console.error(`✗ ${id} case ${i}: threw ${e.message} on ${show(test.args)}`)
      failures++
      continue
    }
    if (equal(actual, test.expected)) {
      passed++
    } else {
      console.error(
        `✗ ${id} case ${i}: ${show(test.args)} → expected ${show(test.expected)}, got ${show(actual)}`,
      )
      failures++
    }
  }

  const mark = passed === tests.length ? '✓' : '✗'
  console.log(`${mark} ${id.padEnd(20)} ${passed}/${tests.length} (${match})`)
}

console.log(`\n${totalCases} cases checked, ${failures} failed`)
process.exit(failures === 0 ? 0 : 1)
