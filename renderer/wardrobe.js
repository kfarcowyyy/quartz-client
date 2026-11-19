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
  let skins, capes, style, previewId, currentTexture, busy = false, request = 0;

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
              <select id="wardrobe-emote-${i}" data-emote-slot="${i}" ${busy ? 'disabled' : ''} aria-label="${wardrobeCopy('Emote slot', 'Slot emotki')} ${i + 1}">
                <option value="">${wardrobeCopy('Empty', 'Puste')}</option>
                ${style.emotes.map(emote => `<option value="${esc(emote.id)}" ${emote.id === id ? 'selected' : ''}>${esc(wardrobeCopy(emote.en, emote.pl))}</option>`).join('')}
              </select></label>`;
          }).join('')}<span class="wardrobe-wheel-title">Quartz <kbd>P</kbd></span></div>`;
        for (const choice of panel.querySelectorAll('[data-emote-slot]')) addQuartzPicker(choice);
      }
      return;
    }
    if (wardrobeTab === 'capes') {
      if (account.type !== 'microsoft') {
        panel.textContent = wardrobeCopy('Sign in with Microsoft to view your Minecraft capes.', 'Zaloguj się przez Microsoft, aby zobaczyć swoje peleryny Minecraft.');
        return;
      }
      if (!capes) { panel.textContent = wardrobeCopy('Loading capes…', 'Wczytywanie peleryn…'); return; }
      paintCollection(capes.map((cape, index) => ({ ...cape, previewOnly: !cape.id, id: cape.id || 'cache-' + index })), true);
      return;
    }
    if (!skins) { panel.textContent = wardrobeCopy('Loading skins…', 'Wczytuję skiny…'); return; }
    paintCollection(skins.skins, false);
  }

  function paintCollection(items, isCape) {
    const selected = items.find(item => item.id === previewId);
    const worn = (item) => isCape ? item.active : item.id === skins.equipped.skin;
    const disabled = busy ? 'disabled' : '';
    const heading = isCape ? '' : `<div class="wardrobe-tools">
      <button class="btn primary" data-wardrobe-action="add" ${disabled}>${wardrobeCopy('Add skins', 'Dodaj skiny')}</button>
      <label>${wardrobeCopy('Model for new skins', 'Model dodawanych skinów')}
        <select id="wardrobe-model"><option value="wide">Classic</option><option value="slim">Slim</option></select>
      </label>
      ${skins.equipped.skin !== 'account' ? `<button class="btn" data-wardrobe-action="reset" ${disabled}>${wardrobeCopy('Use account skin', 'Użyj skina konta')}</button>` : ''}
    </div>`;
    const shelf = items.length ? `<div class="wardrobe-grid wardrobe-model-grid">${items.map(item =>
      `<button type="button" class="wardrobe-item wardrobe-model-card ${worn(item) ? 'equipped' : ''}"
          data-wardrobe-preview="${esc(item.id)}" aria-pressed="${item === selected}">
        <canvas width="128" height="118" data-model="${esc(item.id)}" aria-hidden="true"></canvas>
        <strong>${esc(item.name)}</strong>
        <small>${worn(item) ? wardrobeCopy('Equipped', 'Założony') : (isCape ? '' : item.slim ? 'Slim' : 'Classic')}</small></button>`).join('')}</div>`
      : `<p class="wardrobe-empty">${isCape
          ? wardrobeCopy('Your Minecraft account has no capes.', 'Twoje konto Minecraft nie ma peleryn.')
          : wardrobeCopy('Add Minecraft skin PNGs to start your collection.', 'Dodaj skiny Minecraft w PNG, aby zacząć kolekcję.')}</p>`;
    const detail = selected ? `<aside class="wardrobe-preview-detail" aria-label="${wardrobeCopy('3D preview', 'Podgląd 3D')}">
      <h2>${esc(selected.name)}</h2>
      <canvas width="220" height="190" data-model="${esc(selected.id)}" class="interactive-model"
          aria-label="${wardrobeCopy('Drag or use the arrow keys to rotate', 'Przeciągnij lub użyj strzałek, aby obrócić')}"></canvas>
      <small>${wardrobeCopy('Drag to rotate', 'Przeciągnij, aby obrócić')}</small>
      <div class="wardrobe-item-actions">
        <button class="btn primary" data-wardrobe-action="${isCape ? 'cape' : 'equip'}" data-skin="${esc(selected.id)}"
          ${busy || worn(selected) || selected.previewOnly ? 'disabled' : ''}>${worn(selected) ? wardrobeCopy('Equipped', 'Założony') : wardrobeCopy('Equip', 'Załóż')}</button>
        ${isCape ? '' : `<button class="btn danger" data-wardrobe-action="remove" data-skin="${esc(selected.id)}" ${disabled}>${wardrobeCopy('Remove', 'Usuń')}</button>`}
      </div></aside>` : '';
    panel.innerHTML = heading + `<div class="wardrobe-collection ${selected ? 'with-preview' : ''}">${shelf}${detail}</div>`;
    const picker = $('#wardrobe-model', panel);
    if (picker) addQuartzPicker(picker);
    for (const canvas of panel.querySelectorAll('[data-model]')) {
      const item = items.find(entry => entry.id === canvas.dataset.model);
      bindSkinPreview(canvas, isCape ? currentTexture : item.preview, isCape ? item.url : null,
        Boolean(item.slim), canvas.classList.contains('interactive-model'));
    }
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
        const [response, texture] = await Promise.all([api('cosmetics:capes'), api('cosmetics:active-head').catch(() => null)]);
        if (id === request) { capes = response; currentTexture = texture; }
      }
      if (id === request) paint();
    } catch (error) { if (id === request && active()) panel.textContent = error.message; }
  }

  tabs.addEventListener('click', event => {
    const next = event.target.closest('[data-wardrobe-tab]')?.dataset.wardrobeTab;
    if (next && next !== wardrobeTab) { wardrobeTab = next; previewId = undefined; refresh(); }
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
    const preview = event.target.closest('[data-wardrobe-preview]');
    if (preview && !busy && active()) {
      previewId = preview.dataset.wardrobePreview;
      paint();
      $('#wardrobe-content .interactive-model')?.focus();
      return;
    }
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
      if (wardrobeAction === 'add') {
        const result = await api('cosmetics:add-skin', slim);
        if (result?.ids.length) previewId = result.ids.at(-1);
        if (result?.failed) toast(wardrobeCopy(`Some skins could not be added (${result.failed}).`,
          `Nie udało się dodać części skinów (${result.failed}).`), true);
      }
      if (wardrobeAction === 'cape') capes = await api('cosmetics:equip-cape', skin);
      if (wardrobeAction === 'equip') await api('cosmetics:equip-skin', skin);
      if (wardrobeAction === 'reset') await api('cosmetics:equip-skin', 'account');
      if (wardrobeAction === 'remove') await api('cosmetics:remove-skin', skin);
      if (['equip', 'reset', 'remove'].includes(wardrobeAction)) { capes = null; void updateAccountHead(); }
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
