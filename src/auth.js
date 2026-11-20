const crypto = require('crypto');
const store = require('./store');
let signedIn;
let pending;

function offlineUuid(name) {
  const h = crypto.createHash('md5').update('OfflinePlayer:' + name).digest();
  h[6] = (h[6] & 0x0f) | 0x30;
  h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.toString('hex');
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`;
}

const pub = () => {
  const a = store.get().account;
  return a ? { type: a.type, name: a.name, uuid: a.uuid } : null;
};

async function microsoft() {
  const { Auth } = require('msmc');
  const manager = new Auth('select_account');
  const xbox = await manager.launch('electron');
  const mc = await xbox.getMinecraft();
  signedIn = mc;
  store.set({
    account: { type: 'microsoft', name: mc.profile.name, uuid: mc.profile.id, saved: xbox.save() },
  });
  return pub();
}

function offline(name) {
  if (!/^[A-Za-z0-9_]{3,16}$/.test(name || '')) {
    throw new Error('Nick: 3-16 znaków, litery, cyfry i _');
  }
  signedIn = null;
  store.set({ account: { type: 'offline', name, uuid: offlineUuid(name) } });
  return pub();
}

function logout() {
  signedIn = null;
  store.set({ account: null });
  return null;
}

// Zwraca obiekt "authorization" dla minecraft-launcher-core
async function authorization() {
  const a = store.get().account;
  if (!a) throw new Error('Najpierw zaloguj się w zakładce Konto');

  if (a.type === 'offline') {
    return {
      access_token: '0',
      client_token: '0',
      uuid: a.uuid,
      name: a.name,
      user_properties: '{}',
      meta: { type: 'mojang', demo: false },
    };
  }

  return (await minecraftSession(a)).mclc();
}

async function minecraftSession(account) {
  if (signedIn?.profile.id === account.uuid && signedIn.validate()) return signedIn;
  if (pending?.uuid === account.uuid) return pending.session;
  const refresh = { uuid: account.uuid };
  pending = refresh;
  refresh.session = (async () => {
    const { Auth } = require('msmc');
    const xbox = await new Auth('select_account').refresh(account.saved);
    const mc = await xbox.getMinecraft();
    if (store.get().account?.uuid !== account.uuid || mc.profile.id !== account.uuid) {
      throw new Error('The Minecraft account has changed.');
    }
    store.set({ account: { ...account, saved: xbox.save() } });
    signedIn = mc;
    return mc;
  })();
  try { return await refresh.session; }
  finally { if (pending === refresh) pending = null; }
}

async function minecraftProfile() {
  const account = store.get().account;
  if (account?.type !== 'microsoft') throw new Error('Sign in to Microsoft to view your capes.');
  return (await minecraftSession(account)).profile;
}

async function equipCape(id) {
  const account = store.get().account;
  if (account?.type !== 'microsoft') throw new Error('Sign in to Microsoft to equip a cape.');
  const session = await minecraftSession(account);
  if (!Array.isArray(session.profile.capes) || !session.profile.capes.some(cape => cape.id === id)) {
    throw new Error('This cape is not on the signed-in Minecraft account.');
  }
  const token = session.mclc().access_token;
  if (typeof token !== 'string' || !token) throw new Error('Sign in to Microsoft again.');
  const response = await fetch('https://api.minecraftservices.com/minecraft/profile/capes/active', {
    method: 'PUT',
    headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
    body: JSON.stringify({ capeId: id }),
    redirect: 'error',
    signal: AbortSignal.timeout(10000),
  });
  if (!response.ok) throw new Error('Minecraft could not equip the cape (HTTP ' + response.status + ').');
  const updated = await response.json();
  if (updated?.id !== account.uuid || store.get().account?.uuid !== account.uuid) {
    throw new Error('The signed in Minecraft account changed.');
  }
  Object.assign(session.profile, updated);
  return updated;
}

module.exports = { microsoft, offline, logout, authorization, minecraftProfile, equipCape, status: pub };
