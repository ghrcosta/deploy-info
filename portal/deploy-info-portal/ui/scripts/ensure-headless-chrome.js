#!/usr/bin/env node
// Self-healing `pretest` hook for the frontend unit tests.
//
// Resolves a usable headless Chrome binary with the same logic as
// `karma.conf.js` (CHROME_BIN override -> chrome-headless-shell in the
// Puppeteer cache -> full Chrome for Testing in the same cache). If nothing
// is found — e.g. after `~/.cache` was purged by cleanup tools — it downloads
// a pinned `chrome-headless-shell` build via `npx @puppeteer/browsers install`
// and verifies the binary afterwards.
//
// The version is pinned (not @stable) so the browser cannot silently change
// between runs; bump it deliberately when a new build is wanted.

'use strict';

const { spawnSync } = require('child_process');
const { existsSync, readdirSync, statSync } = require('fs');
const { homedir } = require('os');
const { join } = require('path');

// Keep in sync with the fallback hint in karma.conf.js.
const PINNED_SHELL_VERSION = '154.0.8037.92';
const INSTALL_CMD = ['@puppeteer/browsers', 'install', `chrome-headless-shell@${PINNED_SHELL_VERSION}`];

const PUPPETEER_CACHE_ROOT = join(homedir(), '.cache', 'puppeteer');

function binaryNameFor(product) {
  // chrome-headless-shell-win64\chrome-headless-shell.exe, chrome-win64\chrome.exe, ...
  return process.platform === 'win32' ? `${product}.exe` : product;
}

function findBinaryInVersionDir(versionDir, binaryName) {
  if (!existsSync(versionDir) || !statSync(versionDir).isDirectory()) {
    return null;
  }
  for (const dir of readdirSync(versionDir)) {
    const subdir = join(versionDir, dir);
    if (!statSync(subdir).isDirectory()) {
      continue;
    }
    const candidate = join(subdir, binaryName);
    if (existsSync(candidate)) {
      return candidate;
    }
    // macOS .app bundles (full Chrome for Testing) nest the binary deeper:
    // <product>-mac-arm64/<name>.app/Contents/MacOS/<name>
    if (dir.endsWith('.app')) {
      const macCandidate = join(subdir, 'Contents', 'MacOS', binaryName.replace(/\.exe$/, ''));
      if (existsSync(macCandidate)) {
        return macCandidate;
      }
    }
  }
  return null;
}

function findInPuppeteerCache(product) {
  const productRoot = join(PUPPETEER_CACHE_ROOT, product);
  if (!existsSync(productRoot)) {
    return null;
  }
  // Cache layout: <product>/<platform>-<version>/<product>-<platform>[.app]/...
  // Covered platforms: linux64, win64 (+ .exe), mac-arm64, mac-x64.
  // Newest cached version first.
  for (const entry of readdirSync(productRoot).sort().reverse()) {
    const found = findBinaryInVersionDir(join(productRoot, entry), binaryNameFor(product));
    if (found) {
      return found;
    }
  }
  return null;
}

function resolveHeadlessChrome() {
  // 1. Explicit override — honored as-is (karma.conf.js uses the same value;
  //    a bogus path fails fast there, which is the documented failure mode).
  if (process.env.CHROME_BIN) {
    return process.env.CHROME_BIN;
  }
  // 2. chrome-headless-shell in the Puppeteer cache (lightest option).
  const shell = findInPuppeteerCache('chrome-headless-shell');
  if (shell) {
    return shell;
  }
  // 3. Full Chrome for Testing in the same cache (fallback).
  return findInPuppeteerCache('chrome');
}

function main() {
  if (resolveHeadlessChrome()) {
    // Silent no-op: browser already present (or CHROME_BIN override set).
    return 0;
  }

  console.error(
    `No headless Chrome found under ${PUPPETEER_CACHE_ROOT}; downloading chrome-headless-shell@${PINNED_SHELL_VERSION} (~260 MB, needs internet)...`
  );
  // `npx` is invoked via a shell on Windows (npx.cmd); stdio is inherited so
  // the @puppeteer/browsers progress output stays visible.
  const result = spawnSync('npx', INSTALL_CMD, {
    stdio: 'inherit',
    shell: process.platform === 'win32'
  });
  if (result.status !== 0) {
    console.error(
      `Download failed (exit code ${result.status ?? result.signal}).\n` +
        'Install manually with:  npx @puppeteer/browsers install ' +
        `chrome-headless-shell@${PINNED_SHELL_VERSION}\n` +
        'or set CHROME_BIN to an existing Chrome/Chromium binary.'
    );
    return result.status ?? 1;
  }

  const resolved = resolveHeadlessChrome();
  if (!resolved) {
    console.error(
      `Download reported success but no chrome-headless-shell@${PINNED_SHELL_VERSION} ` +
        `binary was found under ${PUPPETEER_CACHE_ROOT}. Install manually with:\n` +
        `  npx @puppeteer/browsers install chrome-headless-shell@${PINNED_SHELL_VERSION}`
    );
    return 1;
  }
  return 0;
}

process.exit(main());
