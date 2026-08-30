#!/usr/bin/env node
// Enforces AD-7's naming contract ("every code has a matching i18n entry,
// added together") the way Story 3.2b's own AC requires but never actually
// checked: frontend/src/i18n/errorMessages.test.ts hardcoded the backend's
// codes with a comment saying they must be "enumerated from the Java
// sources" and kept in sync by hand - nothing enforced that. This script
// derives the codes from the Java sources directly instead of trusting a
// hand-maintained duplicate, and fails if the frontend catalogs and the
// backend disagree in either direction (epic-3 action item, error-code
// contract check).
//
// A backend error code is always a quoted string literal shaped like
// MODULE_REASON (all caps, underscore-separated, 2+ segments) - e.g.
// "AUTH_UNAUTHENTICATED" in SecurityConfig.java or "QUOTE_NOT_FOUND" in
// QuoteNotFoundException.java. No other string literal in backend/src/main
// matches that shape (verified by hand when this script was written); a
// javadoc mention like {@code AUTH_UNAUTHENTICATED} has no surrounding
// quotes, so it is not picked up as a second, spurious source.

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const CODE_LITERAL = /"([A-Z]{2,}(?:_[A-Z]{2,})+)"/g;

function findJavaFiles(dir) {
  const files = [];
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) {
      files.push(...findJavaFiles(path));
    } else if (entry.endsWith('.java')) {
      files.push(path);
    }
  }
  return files;
}

function backendErrorCodes() {
  const codes = new Set();
  for (const file of findJavaFiles(join(repoRoot, 'backend/src/main/java'))) {
    const contents = readFileSync(file, 'utf8');
    for (const match of contents.matchAll(CODE_LITERAL)) {
      codes.add(match[1]);
    }
  }
  return codes;
}

function catalogCodes(relativePath) {
  const catalog = JSON.parse(readFileSync(join(repoRoot, relativePath), 'utf8'));
  return new Set(Object.keys(catalog.errors.codes));
}

function diff(a, b) {
  return [...a].filter((x) => !b.has(x)).sort();
}

const backendCodes = backendErrorCodes();
const bgCodes = catalogCodes('frontend/src/i18n/bg.json');
const enCodes = catalogCodes('frontend/src/i18n/en.json');

const problems = [];
for (const [label, catalogCodeSet] of [
  ['bg.json', bgCodes],
  ['en.json', enCodes],
]) {
  const missing = diff(backendCodes, catalogCodeSet);
  const stale = diff(catalogCodeSet, backendCodes);
  if (missing.length > 0) {
    problems.push(`${label} is missing i18n entries for: ${missing.join(', ')}`);
  }
  if (stale.length > 0) {
    problems.push(`${label} has entries for codes the backend no longer emits: ${stale.join(', ')}`);
  }
}

if (problems.length > 0) {
  console.error('Error-code contract check failed (AD-7):\n');
  for (const problem of problems) {
    console.error(`  - ${problem}`);
  }
  console.error(
    '\nEvery backend error code (a quoted MODULE_REASON string literal under backend/src/main/java) ' +
      'must have a matching entry in both frontend/src/i18n/bg.json and en.json, and vice versa.',
  );
  process.exit(1);
}

console.log(`Error-code contract OK: ${backendCodes.size} backend codes, all present in bg.json and en.json.`);
