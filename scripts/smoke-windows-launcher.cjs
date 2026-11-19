const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const { once } = require('node:events');
const { mkdtemp } = require('node:fs/promises');
const net = require('node:net');
const os = require('node:os');
const path = require('node:path');
const { setTimeout: delay } = require('node:timers/promises');

const executable = path.resolve('dist', 'win-unpacked', 'Quartz Client.exe');

async function selectPort() {
  const server = net.createServer().listen(0, '127.0.0.1');
  await once(server, 'listening');
  const port = server.address().port;
  await new Promise((resolve, reject) => {
    server.close(error => error ? reject(error) : resolve());
  });
  return port;
}

async function until(description, read, milliseconds = 20000) {
  const deadline = Date.now() + milliseconds;
  let last;
  while (Date.now() < deadline) {
    try {
      const result = await read();
      if (result) return result;
    } catch (error) {
      last = error;
    }
    await delay(250);
  }
  throw new Error('Timed out waiting for ' + description, { cause: last });
}

async function openPage(url) {
  const ws = new WebSocket(url);
  await once(ws, 'open', { signal: AbortSignal.timeout(5000) });
  let id = 0;
  return {
    async evaluate(expression) {
      const request = ++id;
      const answer = new Promise((resolve, reject) => {
        const signal = AbortSignal.timeout(4000);
        const stop = () => {
          cleanup();
          reject(new Error('Electron renderer did not respond'));
        };
        const onMessage = event => {
          const data = JSON.parse(event.data);
          if (data.id !== request) return;
          cleanup();
          if (data.error || data.result?.exceptionDetails) reject(new Error(JSON.stringify(data)));
          else resolve(data.result.result.value);
        };
        const cleanup = () => {
          ws.removeEventListener('message', onMessage);
          signal.removeEventListener('abort', stop);
        };
        ws.addEventListener('message', onMessage);
        signal.addEventListener('abort', stop, { once: true });
      });
      ws.send(JSON.stringify({ id: request, method: 'Runtime.evaluate', params: { expression, returnByValue: true } }));
      return answer;
    },
    close() { ws.close(); },
  };
}

async function boot(profile) {
  const port = await selectPort();
  const url = 'http://127.0.0.1:' + port;
  const child = spawn(executable, [
    '--user-data-dir=' + profile,
    '--remote-debugging-port=' + port,
  ], { windowsHide: true, stdio: 'ignore' });
  let page;
  try {
    await once(child, 'spawn', { signal: AbortSignal.timeout(5000) });
    const target = await until('the launcher window', async () => {
      if (child.exitCode !== null) throw new Error('Launcher exit ' + child.exitCode);
      const response = await fetch(url + '/json/list', { signal: AbortSignal.timeout(1500) });
      return (await response.json()).find(t => t.type === 'page' && t.url.endsWith('/renderer/index.html'));
    });
    page = await openPage(target.webSocketDebuggerUrl);
    await until('launcher renderer and preload', () => page.evaluate(
      "typeof quartz?.invoke === 'function' && !!document.querySelector('#view button')"
    ));
    await page.evaluate('openNewInstance(); true');
    const selected = await until('Minecraft version list in the new instance window', () => page.evaluate(
      "document.querySelector('#n-ver')?.options.length > 10 && document.querySelector('#n-ver').value"
    ));
    assert.ok(selected);
    const close = await fetch(url + '/json/close/' + target.id, { signal: AbortSignal.timeout(5000) });
    assert.ok(close.ok);
    await until('the launcher to close', () => child.exitCode !== null, 12000);
    assert.equal(child.exitCode, 0);
    return selected;
  } finally {
    page?.close();
    if (child.exitCode === null) child.kill();
  }
}

(async () => {
  const profile = await mkdtemp(path.join(os.tmpdir(), 'quartz-launcher-smoke-'));
  const first = await boot(profile);
  const next = await boot(profile);
  console.log('The packaged launcher opened twice with the same profile. Version picker: ' + first + ', ' + next);
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
