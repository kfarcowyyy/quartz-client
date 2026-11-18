const { app, BrowserWindow, ipcMain, shell, Menu } = require('electron');
const path = require('path');

const store = require('./store');
const paths = require('./paths');
const versions = require('./versions');
const instances = require('./instances');
const auth = require('./auth');
const modrinth = require('./modrinth');
const curseforge = require('./curseforge');
const mrpack = require('./mrpack');
const extras = require('./extras');
const loaders = require('./loaders');
const java = require('./java');
const launcher = require('./launcher');

let win;
const send = (ch, data) => win && !win.isDestroyed() && win.webContents.send(ch, data);

function handle(name, fn) {
  ipcMain.handle(name, async (_e, ...args) => {
    try {
      return { ok: true, data: await fn(...args) };
    } catch (e) {
      return { ok: false, error: (e && e.message) || String(e) };
    }
  });
}

function createWindow() {
  Menu.setApplicationMenu(null);
  win = new BrowserWindow({
    width: 1180,
    height: 740,
    minWidth: 980,
    minHeight: 620,
    backgroundColor: '#f6f3f9',
    title: 'Quartz Client',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  win.loadFile(path.join(__dirname, '..', 'renderer', 'index.html'));
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://')) shell.openExternal(url);
    return { action: 'deny' };
  });
}

// ---- IPC ----
handle('versions:list', () => versions.list());
handle('settings:get', () => store.get());
handle('settings:set', (patch) => store.set(patch));

handle('instances:list', () => instances.list());
handle('instances:delete', (id) => instances.remove(id));
handle('instances:open', (id) => shell.openPath(paths.instDir(id)));
handle('instances:create', async (o) => {
  const inst = instances.create(o);
  send('status', 'Instaluję dodatki...');
  await extras.applyDefaults(inst, { optimize: !!o.optimize, voicechat: !!o.voicechat }, (m) =>
    send('log', m)
  );
  send('status', '');
  return inst;
});

handle('auth:status', () => auth.status());
handle('auth:microsoft', () => auth.microsoft());
handle('auth:offline', (name) => auth.offline(name));
handle('auth:logout', () => auth.logout());

handle('browse:search', (p) => {
  if (p.provider === 'curseforge') return curseforge.search(p, store.get().curseforgeKey);
  return modrinth.search(p);
});

handle('browse:install', async ({ provider, type, project, instanceId }) => {
  if (type === 'modpack') {
    if (provider !== 'modrinth') throw new Error('Modpacki na razie tylko z Modrinth');
    return mrpack.installFromModrinth(project.id, (s) => send('status', s), {
      voicechat: store.get().voicechat,
    });
  }
  const inst = instances.get(instanceId);
  if (!inst) throw new Error('Wybierz instancję');
  if (provider === 'curseforge') {
    return curseforge.installFile(project.id, inst, type, store.get().curseforgeKey);
  }
  return modrinth.installFile(project.id, inst, type);
});

handle('game:play', async (id) => {
  const inst = instances.get(id);
  if (!inst) throw new Error('Nie znaleziono instancji');
  const settings = store.get();

  send('status', 'Logowanie...');
  const authorization = await auth.authorization();

  const javaPath = await java.ensureJava(inst.mcVersion, (s) => send('status', s));

  send('status', 'Przygotowuję loader...');
  const customId = await loaders.prepare(inst);

  if (settings.voicechat) {
    send('status', 'Sprawdzam Simple Voice Chat...');
    await extras.ensureVoiceChat(inst, (m) => send('log', m));
  }

  send('status', 'Pobieram i uruchamiam grę...');
  await launcher.launch({
    inst,
    authorization,
    javaPath,
    customId,
    ram: settings.ram,
    onLog: (m) => send('log', m),
    onProgress: (p) => send('progress', p),
    onClose: (code) => {
      send('status', '');
      send('closed', code);
    },
  });
  send('status', 'Gra uruchomiona');
});

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
