const fs = require('fs');
const os = require('os');
const path = require('path');
const AdmZip = require('adm-zip');
const { download, safeJoin } = require('./net');
const instances = require('./instances');
const modrinth = require('./modrinth');
const extras = require('./extras');
const paths = require('./paths');

// Import pliku .mrpack (format modpacków Modrinth) jako nowej instancji
async function importMrpack(file, onStatus, opts = {}) {
  const zip = new AdmZip(file);
  const entry = zip.getEntry('modrinth.index.json');
  if (!entry) throw new Error('To nie jest poprawny plik .mrpack');
  const index = JSON.parse(zip.readAsText(entry));
  const deps = index.dependencies || {};

  let loader = 'vanilla';
  if (deps['fabric-loader']) loader = 'fabric';
  else if (deps['quilt-loader']) throw new Error('Quilt nie jest już obsługiwany. Wybierz modpack dla Fabric.');
  else if (deps.forge || deps.neoforge) {
    throw new Error('Ten modpack wymaga Forge/NeoForge - wsparcie dojdzie w etapie 2');
  }

  if ((opts.mcVersion && deps.minecraft !== opts.mcVersion) || (opts.loader && loader !== opts.loader)) {
    throw new Error('Pobrany modpack nie pasuje do wybranych filtrów.');
  }
  const inst = await instances.create({ name: index.name || 'Modpack', mcVersion: deps.minecraft, loader });
  const dir = paths.instDir(inst.id);

  const files = (index.files || []).filter((f) => !f.env || f.env.client !== 'unsupported');
  let i = 0;
  for (const f of files) {
    i++;
    onStatus && onStatus(`Modpack: plik ${i}/${files.length}`);
    await download(f.downloads[0], safeJoin(dir, f.path));
  }

  for (const e of zip.getEntries()) {
    if (e.isDirectory) continue;
    const m = e.entryName.match(/^(?:client-)?overrides\/(.+)$/);
    if (!m) continue;
    const dest = safeJoin(dir, m[1]);
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.writeFileSync(dest, e.getData());
  }

  // Simple Voice Chat w każdym modpacku, także tym zaimportowanym
  if (opts.voicechat !== false) {
    onStatus && onStatus('Dodaję Simple Voice Chat...');
    await extras.ensureVoiceChat(inst);
  }
  return inst;
}

async function installFromModrinth(projectId, onStatus, opts) {
  onStatus && onStatus('Pobieram modpack...');
  const f = await modrinth.latestModpackFile(projectId, opts);
  const folder = fs.mkdtempSync(path.join(os.tmpdir(), 'quartz-pack-'));
  const tmp = path.join(folder, 'download.mrpack');
  try {
    await download(f.url, tmp);
    return await importMrpack(tmp, onStatus, opts);
  } finally {
    fs.rmSync(folder, { recursive: true, force: true });
  }
}

module.exports = { importMrpack, installFromModrinth };
