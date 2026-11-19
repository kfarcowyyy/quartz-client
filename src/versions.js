const fs = require('node:fs/promises');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const paths = require('./paths');
const bundled = require('./minecraft-version-index.json').map(([id, type, date]) => ({ id, type, date }));
const { getJson } = require('./net');

const HOSTS = ['piston-meta.mojang.com', 'launchermeta.mojang.com'];
const REFRESH_AFTER = 6 * 60 * 60 * 1000;
const cacheFile = () => path.join(paths.base(), 'minecraft-versions.json');
let initial;
let current;
let pending;

function fromManifest(manifest) {
  if (!Array.isArray(manifest?.versions)) throw new Error('Invalid Minecraft version manifest');
  const versions = manifest.versions.map(({ id, type, releaseTime, url }) => ({ id, type, date: releaseTime, url }));
  if (!valid(versions)) throw new Error('Incomplete Minecraft version manifest');
  return versions;
}

function valid(versions) {
  return Array.isArray(versions) && versions.some(v => v?.type === 'release') && versions.every(v => {
    if (typeof v?.id !== 'string' || typeof v.type !== 'string' || typeof v.date !== 'string') return false;
    try {
      const url = new URL(v.url);
      return url.protocol === 'https:' && HOSTS.includes(url.hostname) && !url.port &&
        !url.username && !url.password;
    } catch { return false; }
  });
}

function snapshot() {
  if (current) return Promise.resolve(current);
  initial ??= (async () => {
    try {
      const saved = JSON.parse(await fs.readFile(cacheFile(), 'utf8'));
      if (valid(saved.versions) && Number.isFinite(saved.checkedAt)) current = saved;
    } catch { /* A fresh installation can use the bundled index. */ }
    return current;
  })();
  return initial;
}

async function fetchManifest() {
  return Promise.any(HOSTS.map(host =>
    getJson('https://' + host + '/mc/game/version_manifest_v2.json', {}, AbortSignal.timeout(5500)).then(fromManifest)
  ));
}

async function update(force) {
  const cached = await snapshot();
  if (!force && cached && Date.now() - cached.checkedAt < REFRESH_AFTER) return cached;
  pending ??= (async () => {
    const versions = await fetchManifest();
    const checkedAt = Date.now();
    current = { versions, checkedAt };
    const file = cacheFile(), temporary = file + '.' + randomUUID() + '.tmp';
    try {
      await fs.mkdir(path.dirname(file), { recursive: true });
      await fs.writeFile(temporary, JSON.stringify(current));
      await fs.rename(temporary, file);
    } catch {
      await fs.rm(temporary, { force: true }).catch(() => {});
    }
    return current;
  })().finally(() => { pending = null; });
  return pending;
}

function allowedFamily(id) {
  if (typeof id !== 'string') return null;
  const match = /^(1\.(?:1[5-9]|2[01])|26)(?:\.(\d+))?(?:\.(\d+))?$/.exec(id);
  if (!match || (match[1] === '26' && !match[2]) || (match[1] !== '26' && match[3])) return null;
  return match[1];
}

function isCreatable(version) {
  return version.type === 'release' && allowedFamily(version.id) !== null;
}

async function assertCreatable(id) {
  if (!allowedFamily(id)) throw new Error('Wybierz opublikowaną wersję Minecrafta od 1.15 do 1.21.x lub 26.x.');
  const includes = (items) => items.some(item => item.id === id && isCreatable(item));
  if (includes(bundled) || includes((await snapshot())?.versions ?? [])) return;
  const latest = await update(false).catch(() => null);
  if (!latest || !includes(latest.versions)) {
    throw new Error('Wybierz opublikowaną wersję Minecrafta od 1.15 do 1.21.x lub 26.x.');
  }
}

async function list(opts = {}) {
  let cached = await snapshot();
  let refreshFailed = false;
  if (opts?.refresh === true) {
    try { cached = await update(opts.force === true); }
    catch { refreshFailed = true; }
  }
  const source = cached?.versions ?? bundled;
  const items = (opts.creationOnly ? source.filter(isCreatable) : source)
    .map(({ id, type, date }) => ({ id, type, date, family: allowedFamily(id) }));
  return { items, source: cached ? 'cache' : 'bundled', refreshFailed };
}

async function javaMajor(id) {
  const cached = await snapshot();
  let v = cached?.versions.find(x => x.id === id);
  if (!v) {
    const updated = await update(true);
    v = updated.versions.find(x => x.id === id);
  }
  if (!v) throw new Error('Brak metadanych wersji Minecrafta: ' + id);
  const detail = await getJson(v.url, {}, AbortSignal.timeout(10000));
  return (detail.javaVersion && detail.javaVersion.majorVersion) || 8;
}

module.exports = { list, javaMajor, assertCreatable };
