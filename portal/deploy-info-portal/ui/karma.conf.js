// Karma configuration for the frontend unit tests.
//
// The suite always runs headless: the browser binary is resolved automatically
// from the Puppeteer cache (or CHROME_BIN), so tests can run on machines
// without a desktop browser (CI, containers, headless workstations).
// `npm test` self-heals a purged cache via the `pretest` hook in package.json.

const { existsSync, readdirSync, statSync } = require('fs');
const { homedir } = require('os');
const { join } = require('path');

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
  // 1. Explicit override — honored as-is (a bogus value must fail fast with
  //    the standard launcher error, not silently fall back to the cache).
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

const resolvedChromeBin = resolveHeadlessChrome();
if (resolvedChromeBin) {
  process.env.CHROME_BIN = resolvedChromeBin;
} else if (!process.env.CHROME_BIN) {
  console.error(
    'No headless Chrome found for the frontend unit tests.\n' +
      'Run "npm test" (auto-installs chrome-headless-shell via the pretest hook) or:\n' +
      '  npx @puppeteer/browsers install chrome-headless-shell@154.0.8037.92\n' +
      `Expected under ${PUPPETEER_CACHE_ROOT}, or set CHROME_BIN to an existing binary.`
  );
}

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage')
    ],
    client: {
      jasmine: {},
      clearContext: false // leave Jasmine Spec Runner output visible in browser
    },
    jasmineHtmlReporter: {
      suppressAll: true // hides results in the console when running in CI
    },
    coverageReporter: {
      dir: join(__dirname, './coverage/ui'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'text-summary' }]
    },
    reporters: ['progress', 'kjhtml'],
    browsers: ['ChromeHeadlessCI'],
    customLaunchers: {
      // Container/CI-safe: never touches a desktop browser.
      ChromeHeadlessCI: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage', '--disable-extensions']
      }
    },
    restartOnFileChange: true
  });
};
