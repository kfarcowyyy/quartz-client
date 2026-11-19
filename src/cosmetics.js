const fs = require('node:fs');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const { nativeImage } = require('electron');
const paths = require('./paths');
const catalog = require('./style-catalog.json');

const skinId = /^[0-9a-f]{32}-(wide|slim)$/;
const noSkin = 'account';
const MAX_PNG = 1024 * 1024;

function profileFile(account) {
  const id = String(account?.uuid || '').replace(/-/g, '').toLowerCase();
  return /^[a-f0-9]{32}$/.test(id) ? path.join(paths.base(), 'wardrobe', id + '.json') : null;
}

function sibling(account, suffix) {
  const file = profileFile(account);
  if (!file) throw new Error('Sign in or choose an offline name to use the wardrobe.');
  return file.slice(0, -5) + suffix;
}

function readJson(file, fallback) {
  try { return JSON.parse(fs.readFileSync(file, 'utf8')); }
  catch (error) {
    if (error.code === 'ENOENT' || error instanceof SyntaxError) return fallback;
    throw error;
  }
}

function writeJson(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const pending = file + '.' + randomUUID() + '.tmp';
  try {
    fs.writeFileSync(pending, JSON.stringify(data), { flag: 'wx' });
    fs.renameSync(pending, file);
  } finally { fs.rmSync(pending, { force: true }); }
}

function skinPath(account, id) {
  if (typeof id !== 'string' || !skinId.test(id)) throw new Error('Unknown skin.');
  return path.join(sibling(account, '-skins'), id + '.png');
}

function read(account) {
  const file = profileFile(account);
  const selection = file && readJson(file, null);
  const current = selection?.skin;
  return { skin: skinId.test(current || '') && fs.existsSync(skinPath(account, current)) ? current : noSkin };
}

function listSkins(account) {
  const folder = sibling(account, '-skins');
  if (!fs.existsSync(folder)) return [];
  return fs.readdirSync(folder, { withFileTypes: true }).filter(entry =>
    entry.isFile() && entry.name.endsWith('.png') && skinId.test(entry.name.slice(0, -4))
  ).map(entry => {
    const id = entry.name.slice(0, -4);
    const file = skinPath(account, id);
    const metadata = readJson(file.slice(0, -4) + '.json', {});
    const bytes = fs.readFileSync(file);
    return {
      id,
      name: typeof metadata.name === 'string' ? metadata.name.slice(0, 80) : 'Skin ' + id.slice(0, 6),
      slim: id.endsWith('-slim'),
      preview: bytes.length <= MAX_PNG ? 'data:image/png;base64,' + bytes.toString('base64') : null,
    };
  });
}

function selectSkin(account, id) {
  if (id !== noSkin && !fs.existsSync(skinPath(account, id))) throw new Error('Skin file not found.');
  writeJson(sibling(account, '.json'), { skin: id });
  return read(account);
}

function addSkin(account, file, slim) {
  if (fs.statSync(file).size > MAX_PNG) throw new Error('The skin PNG must be smaller than 1 MB.');
  const bytes = fs.readFileSync(file);
  if (!bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])) ||
      bytes.length < 24 || bytes.readUInt32BE(16) !== 64 || bytes.readUInt32BE(20) !== 64) {
    throw new Error('Choose a 64 × 64 Minecraft skin PNG.');
  }
  const image = nativeImage.createFromBuffer(bytes);
  if (image.isEmpty()) throw new Error('This PNG could not be opened.');
  const id = randomUUID().replace(/-/g, '') + (slim ? '-slim' : '-wide');
  const destination = skinPath(account, id);
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.writeFileSync(destination, image.toPNG(), { flag: 'wx' });
  try {
    writeJson(destination.slice(0, -4) + '.json', { name: path.basename(file, path.extname(file)).slice(0, 80) });
  } catch (error) {
    fs.rmSync(destination, { force: true });
    throw error;
  }
  return id;
}

function removeSkin(account, id) {
  const file = skinPath(account, id);
  if (read(account).skin === id) selectSkin(account, noSkin);
  fs.rmSync(file, { force: true });
  fs.rmSync(file.slice(0, -4) + '.json', { force: true });
}

function cachedCapes(account) {
  const capes = readJson(sibling(account, '-capes.json'), null);
  return Array.isArray(capes) ? capes : null;
}

async function loadCapes(account, authorization) {
  if (account?.type !== 'microsoft') return [];
  if (!authorization?.access_token) throw new Error('Sign in to Minecraft to view account capes.');
  const response = await fetch('https://api.minecraftservices.com/minecraft/profile', {
    headers: { Authorization: 'Bearer ' + authorization.access_token },
    redirect: 'error',
    signal: AbortSignal.timeout(5000),
  });
  if (!response.ok) throw new Error('Could not load your Minecraft capes.');
  const profile = await response.json();
  if (String(profile.id).replace(/-/g, '').toLowerCase() !== String(account.uuid).replace(/-/g, '').toLowerCase()) {
    throw new Error('The signed in Minecraft account changed.');
  }
  const capes = (Array.isArray(profile.capes) ? profile.capes : []).map(cape => ({
    name: typeof cape.alias === 'string' ? cape.alias.slice(0, 80) : 'Minecraft cape',
    active: cape.state === 'ACTIVE',
    url: typeof cape.url === 'string' && /^https?:\/\/textures\.minecraft\.net\/texture\/[0-9a-f]+$/i.test(cape.url)
      ? cape.url.replace(/^http:/, 'https:') : null,
  }));
  writeJson(sibling(account, '-capes.json'), capes);
  return capes;
}

function readStyle(account) {
  const profile = profileFile(account);
  const models = profile && readJson(sibling(account, '-models.json'), {});
  const wheel = profile && readJson(sibling(account, '-emotes.json'), []);
  const equipped = Object.fromEntries(catalog.cosmetics.filter(item => models?.[item.slot] === item.id)
    .map(item => [item.slot, item.id]));
  const slots = Array.from({ length: 8 }, (_, i) =>
    Array.isArray(wheel) && catalog.emotes.some(item => item.id === wheel[i]) ? wheel[i] : null);
  return { ...catalog, equipped, slots };
}

function equipModel(account, id) {
  const item = catalog.cosmetics.find(entry => entry.id === id);
  if (!item) throw new Error('Unknown cosmetic.');
  const { equipped } = readStyle(account);
  if (equipped[item.slot] === id) delete equipped[item.slot];
  else equipped[item.slot] = id;
  writeJson(sibling(account, '-models.json'), equipped);
  return readStyle(account);
}

function setEmote(account, index, id) {
  if (!Number.isInteger(index) || index < 0 || index >= 8 ||
      (id !== null && !catalog.emotes.some(item => item.id === id))) throw new Error('Unknown emote or wheel slot.');
  const { slots } = readStyle(account);
  slots[index] = id;
  writeJson(sibling(account, '-emotes.json'), slots);
  return readStyle(account);
}

module.exports = { readStyle, equipModel, setEmote, profileFile, read, listSkins, selectSkin, addSkin, removeSkin, cachedCapes, loadCapes };
