const assert = require('node:assert/strict');
const { mkdtemp, mkdir, writeFile } = require('node:fs/promises');
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
    await window.evaluate(() => {
      globalThis.removalResult = confirmRemoval('Usunąć plik?', '<Quartz test>', 'Plik zostanie usunięty z instancji.');
    });
    const removal = window.locator('dialog.removal-dialog');
    await removal.waitFor({ state: 'visible' });
    assert.equal(await removal.locator('.removal-name').textContent(), '<Quartz test>');
    assert.equal(await removal.locator(':focus').getAttribute('value'), 'cancel');
    await window.keyboard.press('Escape');
    assert.equal(await window.evaluate(() => globalThis.removalResult), false);
    await window.evaluate(() => {
      globalThis.removalResult = confirmRemoval('Usunąć skina?', 'Example skin', 'Skin zostanie usunięty z garderoby.');
    });
    await removal.locator('button[value="remove"]').click();
    assert.equal(await window.evaluate(() => globalThis.removalResult), true);
    await window.locator('#nav [data-page="browse"]').click();
    await window.locator('[data-type="modpack"]').click();
    await window.waitForFunction(() => document.querySelector('#b-version')?.options.length > 10, undefined, {
      timeout: 15000,
    });
    assert.deepEqual(await window.locator('#b-loader option').evaluateAll(options => options.map(option => option.value)),
      ['', 'fabric']);
    assert.equal(await window.locator('#b-inst').count(), 0);
    await window.evaluate(() => { go('play'); openNewInstance(); });
    await window.waitForFunction(() => {
      const select = document.querySelector('#n-ver');
      return select?.options.length > 10 && !select.disabled && Boolean(select.value);
    }, undefined, { timeout: 15000 });
    const listed = await window.locator('#n-ver option').evaluateAll(options => options.map(option => option.value));
    for (const release of ['1.15', '1.15.1', '1.16.5', '1.17.1', '1.18.2', '1.19.4', '1.20.6', '1.21.11', '26.3']) {
      assert.ok(listed.includes(release), 'Missing release ' + release);
    }
    assert.ok(listed.every(value => /^(?:1\.(?:1[5-9]|2[01])|26\.\d+)(?:\.\d+)?$/.test(value)));
    assert.equal(await window.locator('#n-snap').count(), 0);
    assert.equal(await window.locator('#n-quartz-status').count(), 0);
    await window.locator('#n-ver + .quartz-picker-trigger').click();
    await window.locator('#n-ver-choices [role=option]').filter({ hasText: /^1\.15\.1$/ }).click();
    assert.equal(await window.locator('#n-ver').inputValue(), '1.15.1');
    const selected = await window.locator('#n-ver').inputValue();
    await window.locator('#n-cancel').click();
    const account = await window.evaluate(async () => {
      state.account = await api('auth:offline', 'QuartzSmoke');
      renderNav();
      return state.account;
    });
    const skinId = '00000000000000000000000000000000-wide';
    const userData = await app.evaluate(({ app }) => app.getPath('userData'));
    const folder = path.join(userData, 'wardrobe', account.uuid.replaceAll('-', '') + '-skins');
    await mkdir(folder, { recursive: true });
    await writeFile(path.join(folder, skinId + '.png'), Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAmUlEQVR4nO3QQQ0AIBDAMMSgFOOHjJKwx/7N1uwzP7c0QNcADdA1QAN0DdAAXQM0QNcADdA1QAN0DdAAXQM0QNcADdA1QAN0DdAAXQM0QNcADdA1QAN0DdAAXQM0QNcADdA1QAN0DdAAXQM0QNcADdA1QAN0DdAAXQM0QNcADdA1QAN0DdAAXQM0QNcADdA1QAN0DZgHEHLABdvsQndxUgn6AAAAAElFTkSuQmCC', 'base64'));
    await window.locator('#nav [data-page="cosmetics"]').click();
    await window.locator('[data-wardrobe-tab="skins"]').click();
    await window.locator('[data-wardrobe-preview]').first().click();
    const model = window.locator('.interactive-model');
    await model.waitFor();
    await model.focus();
    const beforeRotation = await model.evaluate(canvas => canvas.toDataURL());
    await model.press('ArrowRight');
    assert.notEqual(await model.evaluate(canvas => canvas.toDataURL()), beforeRotation);
    const equip = window.locator('[data-wardrobe-action="equip"]');
    if (await equip.isEnabled()) await equip.click();
    await equip.waitFor();
    await window.waitForFunction(() => document.querySelector('[data-wardrobe-action="equip"]').disabled);
    await window.waitForFunction(() => {
      const head = document.querySelector('#account-head');
      return Boolean(head?.getContext('2d').getImageData(15, 22, 1, 1).data[0] > 200);
    });
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
