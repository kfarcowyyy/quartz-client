const wardrobeTabs = {
  skins: ['Skins', 'Skiny'], capes: ['Capes', 'Peleryny'], models: ['3D cosmetics', 'Kosmetyki 3D'], emotes: ['Emotes', 'Emotki'],
};
let wardrobeTab = 'models';
const wardrobeCopy = (en, pl) => state.settings.language === 'pl' ? pl : en;

function viewWardrobe() {
  if (!state.account) return `<div class="page"><h1>${wardrobeCopy('Wardrobe', 'Garderoba')}</h1>
    <section class="card"><p>${wardrobeCopy('Sign in or choose an offline name.', 'Zaloguj się lub wybierz nick offline.')}</p>
    <button class="btn primary" id="wardrobe-account">${wardrobeCopy('Account', 'Konto')}</button></section></div>`;
  return `<div class="page wardrobe"><h1>${wardrobeCopy('Wardrobe', 'Garderoba')}</h1>
    <div id="wardrobe-tabs" class="wardrobe-tabs" aria-label="${wardrobeCopy('Wardrobe categories', 'Kategorie garderoby')}"></div>
    <div id="wardrobe-content" class="card wardrobe-content" aria-live="polite"></div></div>`;
}

function bindWardrobe() {
  $('#wardrobe-account')?.addEventListener('click', () => go('account'));
  const panel = $('#wardrobe-content');
  const tabs = $('#wardrobe-tabs');
  if (!panel || !tabs) return;
  const account = state.account;
  const active = () => panel.isConnected && state.account?.uuid === account.uuid;
  let skins, capes, style, busy = false, request = 0;

  function paintTabs() {
    tabs.innerHTML = Object.entries(wardrobeTabs).map(([key, title]) =>
      `<button class="btn" data-wardrobe-tab="${key}" aria-pressed="${key === wardrobeTab}">${wardrobeCopy(...title)}</button>`).join('');
  }

  function paint() {
    if (!active()) return;
    paintTabs();
    if (wardrobeTab === 'models' || wardrobeTab === 'emotes') {
      if (!style) { panel.textContent = wardrobeCopy('Loading wardrobe…', 'Wczytuję garderobę…'); return; }
      if (wardrobeTab === 'models') {
        panel.innerHTML = `<div class="wardrobe-grid">${style.cosmetics.map((item, i) => {
          const equipped = style.equipped[item.slot] === item.id;
          return `<article class="wardrobe-item style-item${equipped ? ' equipped' : ''}">
            <canvas width="160" height="114" data-style-preview="${i}" aria-hidden="true"></canvas>
            <strong>${esc(wardrobeCopy(item.en, item.pl))}</strong>
            <button class="btn ${equipped ? '' : 'primary'}" data-wardrobe-action="model" data-skin="${esc(item.id)}" ${busy ? 'disabled' : ''}>
              ${equipped ? wardrobeCopy('Take off', 'Zdejmij') : wardrobeCopy('Equip', 'Załóż')}
            </button></article>`;
        }).join('')}</div>`;
        startStylePreviews(panel, style.cosmetics);
      } else {
        panel.innerHTML = `<p class="wardrobe-hint">${wardrobeCopy('Choose an emote for each slot. Hold P in game to open the wheel.', 'Wybierz emotkę do każdego slotu. W grze przytrzymaj P, aby otworzyć koło.')}</p>
          <div class="wardrobe-wheel">${style.slots.map((id, i) => {
            const here = style.emotes.find(emote => emote.id === id);
            const ang = Math.PI * i / 4;
            return `<label class="wardrobe-wheel-slot" style="--dx:${Math.sin(ang)};--dy:${-Math.cos(ang)}">
              <span>${i + 1} · ${esc(here ? wardrobeCopy(here.en, here.pl) : wardrobeCopy('Empty', 'Puste'))}</span>
              <select data-emote-slot="${i}" ${busy ? 'disabled' : ''} aria-label="${wardrobeCopy('Emote slot', 'Slot emotki')} ${i + 1}">
                <option value="">${wardrobeCopy('Empty', 'Puste')}</option>
                ${style.emotes.map(emote => `<option value="${esc(emote.id)}" ${emote.id === id ? 'selected' : ''}>${esc(wardrobeCopy(emote.en, emote.pl))}</option>`).join('')}
              </select></label>`;
          }).join('')}<span class="wardrobe-wheel-title">Quartz <kbd>P</kbd></span></div>`;
      }
      return;
    }
    if (wardrobeTab === 'capes') {
      if (account.type !== 'microsoft') {
        panel.textContent = wardrobeCopy('Sign in with Microsoft to view your Minecraft capes.', 'Zaloguj się przez Microsoft, aby zobaczyć swoje peleryny Minecraft.');
        return;
      }
      if (!capes) { panel.textContent = wardrobeCopy('Loading capes…', 'Wczytywanie peleryn…'); return; }
      panel.innerHTML = capes.length ? `<div class="wardrobe-grid">${capes.map(cape =>
        `<article class="wardrobe-item${cape.active ? ' equipped' : ''}">
        ${cape.url ? `<img src="${esc(cape.url)}" alt="">` : icon('sparkle', 28)}
        <strong>${esc(cape.name)}</strong>${cape.active ? `<small>${wardrobeCopy('Active on account', 'Aktywna na koncie')}</small>` : ''}</article>`
      ).join('')}</div>` : `<p class="wardrobe-empty">${wardrobeCopy('Your Minecraft account has no capes.', 'Twoje konto Minecraft nie ma peleryn.')}</p>`;
      return;
    }
    if (!skins) { panel.textContent = wardrobeCopy('Loading skins…', 'Wczytywanie skinów…'); return; }
    const disabled = busy ? 'disabled' : '';
    panel.innerHTML = `<div class="wardrobe-tools">
      <button class="btn primary" data-wardrobe-action="add" ${disabled}>${wardrobeCopy('Add skin', 'Dodaj skina')}</button>
      <label>${wardrobeCopy('Model for new skin', 'Model nowego skina')}
        <select id="wardrobe-model"><option value="wide">Classic</option><option value="slim">Slim</option></select>
      </label>
      ${skins.equipped.skin !== 'account' ? `<button class="btn" data-wardrobe-action="reset" ${disabled}>${wardrobeCopy('Use account skin', 'Użyj skina konta')}</button>` : ''}
    </div>
    ${skins.skins.length ? `<div class="wardrobe-grid">${skins.skins.map(skin => {
      const on = skin.id === skins.equipped.skin;
      return `<article class="wardrobe-item${on ? ' equipped' : ''}">
        ${skin.preview ? `<img src="${esc(skin.preview)}" alt="">` : icon('user', 28)}
        <strong>${esc(skin.name)}</strong><small>${skin.slim ? 'Slim' : 'Classic'}</small>
        <div class="wardrobe-item-actions">
          <button class="btn" data-wardrobe-action="equip" data-skin="${esc(skin.id)}" ${busy || on ? 'disabled' : ''}>${on ? wardrobeCopy('Equipped', 'Założony') : wardrobeCopy('Equip', 'Załóż')}</button>
          <button class="btn danger" data-wardrobe-action="remove" data-skin="${esc(skin.id)}" ${disabled}>${wardrobeCopy('Remove', 'Usuń')}</button>
        </div>
      </article>`;
    }).join('')}</div>` : `<p class="wardrobe-empty">${wardrobeCopy('Add a 64 × 64 PNG to start your skin collection.', 'Dodaj PNG 64 × 64, aby zacząć kolekcję skinów.')}</p>`}`;
  }

  async function refresh() {
    const id = ++request;
    const tab = wardrobeTab;
    paint();
    try {
      if (tab === 'models' || tab === 'emotes') {
        const response = await api('cosmetics:style');
        if (id === request) style = response;
      } else if (tab === 'skins') {
        const response = await api('cosmetics:get');
        if (id === request) skins = response;
      } else if (tab === 'capes' && account.type === 'microsoft' && !capes) {
        const response = await api('cosmetics:capes');
        if (id === request) capes = response;
      }
      if (id === request) paint();
    } catch (error) { if (id === request && active()) panel.textContent = error.message; }
  }

  tabs.addEventListener('click', event => {
    const next = event.target.closest('[data-wardrobe-tab]')?.dataset.wardrobeTab;
    if (next && next !== wardrobeTab) { wardrobeTab = next; refresh(); }
  });
  panel.addEventListener('change', async event => {
    const select = event.target.closest('[data-emote-slot]');
    if (!select || busy || !active()) return;
    busy = true;
    try { style = await api('cosmetics:set-emote', Number(select.dataset.emoteSlot), select.value || null); }
    catch (error) { if (active()) toast(error.message, true); }
    finally { busy = false; if (active()) paint(); }
  });
  panel.addEventListener('click', async event => {
    const button = event.target.closest('[data-wardrobe-action]');
    if (!button || busy || !active()) return;
    const { wardrobeAction, skin } = button.dataset;
    if (wardrobeAction === 'remove') {
      const item = skins?.skins.find(entry => entry.id === skin);
      if (!item || !await confirmRemoval('Usunąć skina?', item.name, 'Skin zostanie usunięty z garderoby.') || !active()) return;
    }
    const slim = $('#wardrobe-model', panel)?.value === 'slim';
    busy = true;
    paint();
    try {
      if (wardrobeAction === 'model') style = await api('cosmetics:equip-model', skin);
      if (wardrobeAction === 'add') await api('cosmetics:add-skin', slim);
      if (wardrobeAction === 'equip') await api('cosmetics:equip-skin', skin);
      if (wardrobeAction === 'reset') await api('cosmetics:equip-skin', 'account');
      if (wardrobeAction === 'remove') await api('cosmetics:remove-skin', skin);
    } catch (error) { if (active()) toast(error.message, true); }
    finally { busy = false; if (active()) refresh(); }
  });

  refresh();
}

const styleOffsets = {
  head:[0,0,0], body:[0,0,0], rightArm:[-5,2,0], leftArm:[5,2,0],
  rightLeg:[-2,12,0], leftLeg:[2,12,0], wingL:[-1.31,1.68,2.62], wingR:[1.31,1.68,2.62],
};
const styleFaces = [[0,1,3,2],[4,6,7,5],[0,4,5,1],[2,3,7,6],[0,2,6,4],[1,5,7,3]];
const styleTexture = new Image();
styleTexture.src = 'web-suit.png';
const styleUvOrder = [[0,1,3,2],[5,4,6,7],[4,5,1,0],[2,3,7,6],[4,0,2,6],[1,5,7,3]];
function suitFaces(box) {
  const [u, v] = box.uv, { w, h, d } = box;
  return [[u+d,v+d,w,h],[u+d+w+d,v+d,w,h],[u+d,v,w,d],
    [u+d+w,v,w,d],[u,v+d,d,h],[u+d+w,v+d,d,h]];
}
const styleLight = [.90,.81,1,.67,.72,.88];

function drawStylePreview(canvas, item, t) {
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  const faces = [];
  const angle = .52 + .23 * Math.sin(t * 1.55), horizontal = Math.sin(angle), facing = Math.cos(angle);
  const boxes = item.id === 'web_suit'
    ? [...item.boxes, {bone:'head', x:-4, y:-8, z:-4, w:8, h:8, d:8, color:'#d5b5df'}]
    : item.boxes;
  for (const b of boxes) {
    const points = [], origin = styleOffsets[b.bone];
    const wing = b.bone === 'wingL' ? -1 : b.bone === 'wingR' ? 1 : 0;
    const halo = item.id === 'halo';
    const fold = halo ? -t * .94 : wing * (.43 + .24 * Math.cos(t * 6));
    const tilt = -wing * (.12 + .095 * Math.sin(t * 6 - .38));
    const rise = halo ? .24 * Math.sin(t * 2.9) : 0;
    const textures = b.uv ? suitFaces(b) : null;
    for (let z = 0; z < 2; z++) for (let y = 0; y < 2; y++) for (let x = 0; x < 2; x++) {
      const wx = (b.x + x * b.w) * (wing ? .78 : 1);
      const wy = (b.y + y * b.h) * (wing ? .87 : 1);
      const lz = (b.z + z * b.d) * (wing ? .86 : 1);
      const lx = wx * Math.cos(tilt) - wy * Math.sin(tilt);
      const ly = wx * Math.sin(tilt) + wy * Math.cos(tilt);
      const vx = origin[0] + lx * Math.cos(fold) - lz * Math.sin(fold);
      const vz = origin[2] + lx * Math.sin(fold) + lz * Math.cos(fold);
      const depth = vx * horizontal + vz * facing;
      points.push([vx * facing - vz * horizontal, origin[1] + ly + rise + .28 * depth, depth]);
    }
    const rgb = [1, 3, 5].map(i => parseInt(b.color.slice(i, i + 2), 16));
    for (let i = 0; i < styleFaces.length; i++) {
      const pointsOnFace = (textures ? styleUvOrder[i] : styleFaces[i]).map(j => points[j]);
      const shade = styleLight[i];
      faces.push({points:pointsOnFace, depth:pointsOnFace.reduce((n, p) => n + p[2], 0) / 4,
        uv:textures?.[i], shade, color:'rgb(' + rgb.map(c => Math.round(c * shade)).join(',') + ')'});
    }
  }
  const points = faces.flatMap(face => face.points);
  const left = Math.min(...points.map(x => x[0])), right = Math.max(...points.map(x => x[0]));
  const top = Math.min(...points.map(x => x[1])), bottom = Math.max(...points.map(x => x[1]));
  const halo = item.id === 'halo', wings = item.slot === 'back';
  const scale = halo ? 5.2 : wings ? 3.7 : Math.min((canvas.width - 20) / (right - left), (canvas.height - 20) / (bottom - top));
  const ox = canvas.width / 2 - (halo || wings ? 0 : (left + right) * scale / 2);
  const oy = canvas.height / 2 + (halo ? 14.1 * scale : wings ? -4.2 * scale : -(top + bottom) * scale / 2);
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  faces.sort((a, b) => b.depth - a.depth);
  for (const face of faces) {
    const points = face.points.map(([x, y]) => [ox + x * scale, oy + y * scale]);
    ctx.beginPath();
    for (const point of points) ctx.lineTo(...point);
    ctx.closePath();
    if (face.uv && styleTexture.complete && styleTexture.naturalWidth) {
      const [a, b, , d] = points;
      const [u, v, w, h] = face.uv;
      ctx.save();
      ctx.clip();
      ctx.transform((b[0]-a[0])/w, (b[1]-a[1])/w, (d[0]-a[0])/h, (d[1]-a[1])/h, a[0], a[1]);
      ctx.imageSmoothingEnabled = false;
      ctx.drawImage(styleTexture, u, v, w, h, 0, 0, w, h);
      ctx.fillStyle = `rgba(10, 8, 25, ${(1 - face.shade) * .66})`;
      ctx.fillRect(0, 0, w, h);
      ctx.restore();
    } else {
      ctx.fillStyle = face.color;
      ctx.fill();
    }
  }
}

function startStylePreviews(panel, catalog) {
  const canvases = [...panel.querySelectorAll('[data-style-preview]')];
  let last = -100;
  function frame(now) {
    if (!canvases[0]?.isConnected) return;
    if (now - last >= 33) {
      for (const target of canvases) drawStylePreview(target, catalog[Number(target.dataset.stylePreview)], now / 1000);
      last = now;
    }
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
}
