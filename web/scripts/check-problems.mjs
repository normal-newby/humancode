#!/usr/bin/env node
/**
 * Structurally validates every problem.
 *
 * There is nothing to execute anymore (CLAUDE.md §6) — problems are small
 * apps, not pure functions with test cases, and verification is the
 * interviewer reading the diff, not a runner. What this still catches:
 * missing fields, a file whose starter is identical to its reference (the
 * candidate would be handed a passing solution to stare at), and a problem
 * with no curveballs (the mechanism has nothing to spring).
 *
 * Usage:
 *   npm run check:problems   the curated bank in resources/problems
 *   npm run check:pool       the warm pool in data/problem-pool.json
 *
 * The pool matters more: those problems were written by a model minutes ago and
 * nobody has ever looked at them. Run it before a demo.
 */
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const problemsDir = resolve(here, '../../src/main/resources/problems')
const poolFile = resolve(here, '../../data/problem-pool.json')
const checkingPool = process.argv.includes('--pool')

/** Both sources end up as a plain list of problems. */
function load() {
  if (!checkingPool) {
    return readdirSync(problemsDir)
      .filter((f) => f.endsWith('.json'))
      .map((f) => JSON.parse(readFileSync(join(problemsDir, f), 'utf8')))
  }
  if (!existsSync(poolFile)) {
    console.log(`No pool cache at ${poolFile} — nothing warm to check.`)
    process.exit(0)
  }
  return JSON.parse(readFileSync(poolFile, 'utf8'))
}

const MIN_RUBRIC = 3
const MIN_CURVEBALLS = 1

/**
 * Python problems are logic puzzles, never apps: there is no preview pane for
 * them, so anything that opens a window or waits on stdin is a window nobody
 * can see. ProblemGenerator rejects these at generation time; this catches one
 * that got into the pool before that check existed, or into the bank by hand.
 */
const PYTHON_INTERFACE = /\b(tkinter|Tkinter|curses|pygame|PyQt\d?|PySide\d?|kivy)\b|\binput\s*\(/

let failures = 0

const problems = load()

for (const problem of problems) {
  const { id, statement, files, rubric, curveballs, difficulty, type = 'BUILD' } = problem
  const problemFailures = []

  if (!statement || !statement.trim()) {
    problemFailures.push('has no statement')
  }
  if (!Array.isArray(files) || files.length === 0) {
    problemFailures.push('has no files')
  }
  if (!Array.isArray(rubric) || rubric.length < MIN_RUBRIC) {
    problemFailures.push(`has fewer than ${MIN_RUBRIC} rubric items`)
  }
  if (!Array.isArray(curveballs) || curveballs.length < MIN_CURVEBALLS) {
    problemFailures.push('has no curveballs')
  }
  if (!['BUILD', 'BUG_FIX'].includes(type)) {
    problemFailures.push(`has an unknown type '${type}'`)
  }

  let anyFileHasWork = false
  for (const f of files ?? []) {
    if (!f.name) problemFailures.push('has a file with no name')
    if (!f.language) problemFailures.push(`file '${f.name}' has no language`)
    if (f.starterContent == null) problemFailures.push(`file '${f.name}' has no starterContent`)
    if (!f.referenceContent || !f.referenceContent.trim()) {
      problemFailures.push(`file '${f.name}' has no referenceContent`)
    }
    if (f.starterContent !== f.referenceContent) {
      anyFileHasWork = true
    }
    if (f.language === 'python' || f.name?.endsWith('.py')) {
      const hit = `${f.starterContent ?? ''}\n${f.referenceContent ?? ''}`.match(PYTHON_INTERFACE)
      if (hit) {
        problemFailures.push(`file '${f.name}' builds an interface ('${hit[0].trim()}') instead of a logic puzzle`)
      }
    }
  }
  if (files?.length && !anyFileHasWork) {
    problemFailures.push('has no gap between any file\'s starter and reference content')
  }

  if (problemFailures.length === 0) {
    const count = `${files.length} file${files.length === 1 ? '' : 's'}`
    console.log(`✓ ${id.padEnd(26)} ${String(difficulty).padEnd(7)} ${count}`)
  } else {
    for (const reason of problemFailures) {
      console.error(`✗ ${id}: ${reason}`)
    }
    failures += problemFailures.length
  }
}

// The difficulty census is the point of the bank run: the picker offers three
// levels and ProblemBank falls back loudly when one is missing, so a bank with
// no hard problem should be visible here rather than at a demo.
const census = ['very-easy', 'easy', 'medium', 'hard']
  .map((level) => `${level} ${problems.filter((p) => p.difficulty === level).length}`)
  .join(', ')

console.log(
  `\n${problems.length} ${checkingPool ? 'pooled' : 'bank'} problem(s) (${census}),` +
    ` ${failures} issue${failures === 1 ? '' : 's'} found`,
)
process.exit(failures === 0 ? 0 : 1)
