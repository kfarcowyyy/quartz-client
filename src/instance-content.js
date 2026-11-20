const fs = require('fs');
const path = require('path');
const AdmZip = require('adm-zip');
const { randomUUID } = require('node:crypto');
const paths = require('./paths');
const { safeJoin } = require('./net');

// typ w interfejsie -> folder w instancji
const KINDS = { mods: 'mods', resourcepacks: 'resourcepacks', shaders: 'shaderpacks' };

function folder(id, kind) {
  const sub = KINDS[kind];
  if (!sub) throw new Error('Nieznany typ: ' + kind);
  const dir = path.join(paths.instDir(id), sub);
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

const plainName = (name) => name.replace(/\.disabled$/i, '');
const jarDisplayCache = new Map();
const MAX_MOD_BYTES = 64 * 1024 * 1024;
const MAX_DETAILS_BYTES = 128 * 1024;
const MAX_ICON_BYTES = 96 * 1024;

function fileIndex(id) {
  return path.join(paths.instDir(id), 'downloaded-content.json');
}

function readIndex(id) {
  try {
    const data = JSON.parse(fs.readFileSync(fileIndex(id), 'utf8'));
    return new Map(Array.isArray(data) ? data : []);
  } catch { return new Map(); }
}

function writeIndex(id, index) {
  const file = fileIndex(id), temporary = file + '.' + randomUUID() + '.tmp';
  try {
    fs.writeFileSync(temporary, JSON.stringify([...index]), { flag: 'wx' });
    fs.renameSync(temporary, file);
  } finally {
    fs.rmSync(temporary, { force: true });
  }
}

function externalIcon(value) {
  if (typeof value !== 'string' || value.length > 2048) return null;
  try {
    const url = new URL(value);
    if (url.protocol === 'https:' && !url.username && !url.password) return url.href;
  } catch { /* An embedded icon can still be used. */ }
  return null;
}

function cleanTitle(title) {
  return typeof title === 'string' ? title.replace(/[\u0000-\u001f\u007f]/g, '').trim().slice(0, 120) : '';
}

function rememberDownload(id, type, name, project) {
  if (type !== 'mod' || !name || path.basename(name) !== name) return;
  const title = cleanTitle(project?.title), icon = externalIcon(project?.icon);
  if (!title && !icon) return;
  const index = readIndex(id);
  index.set(plainName(name), { title, icon });
  writeIndex(id, index);
}

function entryBytes(archive, name, limit) {
  if (typeof name !== 'string') return null;
  const entry = archive.getEntry(name);
  if (!entry || entry.isDirectory || entry.header.size < 1 || entry.header.size > limit) return null;
  const bytes = entry.getData();
  return bytes.length <= limit ? bytes : null;
}

function jsonMetadata(archive, name) {
  const bytes = entryBytes(archive, name, MAX_DETAILS_BYTES);
  try { return bytes ? JSON.parse(bytes.toString('utf8')) : null; }
  catch { return null; }
}

function iconPath(value) {
  if (typeof value === 'string') return value.replace(/^\/+/, '');
  if (!value || typeof value !== 'object') return null;
  const sizes = Object.keys(value).filter(key => /^\d+$/.test(key) && typeof value[key] === 'string')
    .sort((a, b) => Math.abs(Number(a) - 64) - Math.abs(Number(b) - 64));
  return sizes.length ? iconPath(value[sizes[0]]) : null;
}

function readIcon(archive, value) {
  const name = iconPath(value);
  if (!name || !/\.(png|jpe?g|webp)$/i.test(name)) return null;
  const bytes = entryBytes(archive, name, MAX_ICON_BYTES);
  if (!bytes) return null;
  const png = bytes.subarray(0, 8).equals(Buffer.from('89504e470d0a1a0a', 'hex'));
  const jpeg = bytes.subarray(0, 3).equals(Buffer.from('ffd8ff', 'hex'));
  const webp = bytes.toString('ascii', 0, 4) === 'RIFF' && bytes.toString('ascii', 8, 12) === 'WEBP';
  const mime = png ? 'image/png' : jpeg ? 'image/jpeg' : webp ? 'image/webp' : null;
  return mime ? 'data:' + mime + ';base64,' + bytes.toString('base64') : null;
}

function jarDetails(file, stat) {
  if (stat.size > MAX_MOD_BYTES || !/\.jar(?:\.disabled)?$/i.test(file)) return {};
  const key = file + '|' + stat.size + '|' + stat.mtimeMs;
  if (jarDisplayCache.has(key)) return jarDisplayCache.get(key);
  let data = {};
  try {
    const archive = new AdmZip(fs.readFileSync(file));
    const fabric = jsonMetadata(archive, 'fabric.mod.json');
    const quilt = jsonMetadata(archive, 'quilt.mod.json')?.quilt_loader?.metadata;
    const descriptor = [fabric, quilt].find(value => cleanTitle(value?.name));
    const title = cleanTitle(descriptor?.name), icon = readIcon(archive, descriptor?.icon);
    data = { title, icon };
  } catch { /* A damaged or unrecognized JAR can still be managed by filename. */ }
  if (jarDisplayCache.size >= 384) jarDisplayCache.delete(jarDisplayCache.keys().next().value);
  jarDisplayCache.set(key, data);
  return data;
}

function list(id) {
  const out = {}, saved = readIndex(id);
  for (const kind of Object.keys(KINDS)) {
    const dir = folder(id, kind);
    out[kind] = fs
      .readdirSync(dir, { withFileTypes: true })
      .filter((e) => !e.name.startsWith('.'))
      .map((e) => {
        const isDir = e.isDirectory();
        const file = path.join(dir, e.name);
        const stat = isDir ? null : fs.statSync(file);
        const original = plainName(e.name);
        const found = kind === 'mods' && !isDir ? saved.get(original) : null;
        const embedded = kind === 'mods' && stat && (!found?.title || !found?.icon) ? jarDetails(file, stat) : {};
        return {
          name: e.name,
          title: cleanTitle(found?.title) || embedded.title || original,
          icon: externalIcon(found?.icon) || embedded.icon || null,
          enabled: !e.name.endsWith('.disabled'),
          size: stat?.size ?? null,
        };
      })
      .sort((a, b) => a.title.localeCompare(b.title, 'pl', { sensitivity: 'base' }));
  }
  return out;
}

function target(id, kind, name) {
  if (path.basename(name) !== name) throw new Error('Niepoprawna nazwa pliku');
  return safeJoin(folder(id, kind), name);
}

// Wyłączenie = dopisanie .disabled (Minecraft takich plików nie ładuje)
function toggle(id, kind, name) {
  const from = target(id, kind, name);
  const to = name.endsWith('.disabled') ? from.slice(0, -'.disabled'.length) : from + '.disabled';
  fs.renameSync(from, to);
}

function remove(id, kind, name) {
  fs.rmSync(target(id, kind, name), { recursive: true, force: true });
  if (kind === 'mods') forgetDownload(id, name);
}

function forgetDownload(id, name) {
  const index = readIndex(id);
  if (index.delete(plainName(name))) writeIndex(id, index);
}

function importFiles(id, kind, files) {
  const dir = folder(id, kind);
  let n = 0;
  for (const f of files) {
    const name = path.basename(f);
    fs.copyFileSync(f, path.join(dir, name));
    if (kind === 'mods') forgetDownload(id, name);
    n++;
  }
  return n;
}

module.exports = { folder, list, toggle, remove, importFiles, rememberDownload };
