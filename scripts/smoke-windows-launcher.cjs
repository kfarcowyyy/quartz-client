const assert = require('node:assert/strict');
const { mkdtemp } = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { _electron } = require('playwright-core');

const executablePath = path.resolve('dist', 'win-unpacked', 'Quartz Client.exe');

async function boot(profile) {
  const app = await _electron.launch({
    executablePath,
    args: ['--user-data-dir=' + profile],
    timeout: 30000,
  });
  try {
    const window = await app.firstWindow();
    window.on('pageerror', error => console.error(error));
    await window.locator('#view button').first().waitFor({ timeout: 15000 });
    await window.evaluate(() => openNewInstance());
    await window.waitForFunction(() => {
      const select = document.querySelector('#n-ver');
      return select?.options.length > 10 && !select.disabled && Boolean(select.value);
    }, undefined, { timeout: 15000 });
    const selected = await window.locator('#n-ver').inputValue();
    return selected;
  } finally {
    await app.close();
  }
}

(async () => {
  const profile = await mkdtemp(path.join(os.tmpdir(), 'quartz-launcher-smoke-'));
  const first = await boot(profile);
  const next = await boot(profile);
  assert.ok(first && next);
  console.log('Packaged launcher opened twice. Version picker selected ' + first + ', ' + next);
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
