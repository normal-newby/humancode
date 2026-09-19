#!/usr/bin/env node
/**
 * Structurally validates every bank problem.
 *
 * There is nothing to execute anymore (CLAUDE.md §6) — problems are small
 * apps, not pure functions with test cases, and verification is the
 * interviewer reading the diff, not a runner. What this still catches:
 * missing fields, a file whose starter is identical to its reference (the
 * candidate would be handed a passing solution to stare at), and a problem
 * with no curveballs (the mechanism has nothing to spring).
 *
 * Usage: npm run check:problems   (from web/)
 */
import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const problemsDir = resolve(here, '../../src/main/resources/problems')

const MIN_RUBRIC = 3
const MIN_CURVEBALLS = 1

let failures = 0

for (const file of readdirSync(problemsDir).filter((f) => f.endsWith('.json'))) {
  const problem = JSON.parse(readFileSync(join(problemsDir, file), 'utf8'))
  const { id, statement, files, rubric, curveballs } = problem
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
  }
  if (files?.length && !anyFileHasWork) {
    problemFailures.push('has no gap between any file\'s starter and reference content')
  }

  if (problemFailures.length === 0) {
    console.log(`✓ ${id.padEnd(20)} ${files.length} file${files.length === 1 ? '' : 's'}`)
  } else {
    for (const reason of problemFailures) {
      console.error(`✗ ${id}: ${reason}`)
    }
    failures += problemFailures.length
  }
}

console.log(`\n${failures} issue${failures === 1 ? '' : 's'} found`)
process.exit(failures === 0 ? 0 : 1)
