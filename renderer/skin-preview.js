const skinPreviewFaces = [[0,1,3,2],[5,4,6,7],[4,5,1,0],[2,3,7,6],[4,0,2,6],[1,5,7,3]];
const skinPreviewLight = [.04,.13,0,.30,.23,.14];

function drawSkinPreview(canvas, skin, cape, slim, angle) {
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.imageSmoothingEnabled = false;
  const faces = [], sin = Math.sin(angle), cos = Math.cos(angle);
  const scale = canvas.height / 37, centerX = canvas.width / 2, centerY = canvas.height * .29;
  function box(x, y, z, w, h, depth, u, v, texture, color, thin = 0, reversed = false) {
    const p = [];
    for (let dz = 0; dz < 2; dz++) for (let dy = 0; dy < 2; dy++) for (let dx = 0; dx < 2; dx++) {
      const px = x + dx * w + (dx ? thin : -thin);
      const py = y + dy * h + (dy ? thin : -thin);
      const pz = z + dz * depth + (dz ? thin : -thin);
      const distance = px * sin + pz * cos;
      p.push([centerX + scale * (px * cos - pz * sin), centerY + scale * (py + .24 * distance), distance]);
    }
    const regions = [[u+depth,v+depth,w,h],[u+depth+w+depth,v+depth,w,h],
      [u+depth,v,w,depth],[u+depth+w,v,w,depth],[u,v+depth,depth,h],[u+depth+w,v+depth,depth,h]];
    for (const [i, face] of skinPreviewFaces.entries()) {
      const corners = face.map(idx => p[idx]);
      const [a,b,,d] = corners;
      if ((b[0]-a[0])*(d[1]-a[1]) - (b[1]-a[1])*(d[0]-a[0]) <= 0.01) continue;
      faces.push({ corners, depth: corners.reduce((total, point) => total + point[2], 0) / 4,
        color, region: regions[reversed && i >= 4 ? 9-i : i], texture, shade: skinPreviewLight[i], reversed });
    }
  }
  const shift = slim ? 3 : 4, modern = skin?.naturalHeight >= 64;
  box(-4,-8,-4,8,8,8,0,0,skin,'#b58dcc');
  box(-4,0,-2,8,12,4,16,16,skin,'#655083');
  box(-4-shift,0,-2,shift,12,4,40,16,skin,'#b58dcc');
  box(4,0,-2,shift,12,4,modern?32:40,modern?48:16,skin,'#b58dcc',0,!modern);
  box(-4,12,-2,4,12,4,0,16,skin,'#494069');
  box(0,12,-2,4,12,4,modern?16:0,modern?48:16,skin,'#494069',0,!modern);
  if (skin) box(-4,-8,-4,8,8,8,32,0,skin,null,.26);
  if (modern) {
    box(-4,0,-2,8,12,4,16,32,skin,null,.22);
    box(-4-shift,0,-2,shift,12,4,40,32,skin,null,.22);
    box(4,0,-2,shift,12,4,48,48,skin,null,.22);
    box(-4,12,-2,4,12,4,0,32,skin,null,.22);
    box(0,12,-2,4,12,4,0,48,skin,null,.22);
  }
  if (cape) box(-5,0,2.5,10,16,1,0,0,cape,null);
  for (const { corners, color, region, texture, shade, reversed } of faces.sort((a,b) => b.depth-a.depth)) {
    const [a,b,,d] = corners;
    const [u,v,w,h] = region;
    ctx.save();
    ctx.transform((b[0]-a[0])/w,(b[1]-a[1])/w,(d[0]-a[0])/h,(d[1]-a[1])/h,a[0],a[1]);
    if (texture) {
      if (reversed) { ctx.translate(w, 0); ctx.scale(-1, 1); }
      const unit = texture === cape ? Math.max(1, texture.naturalWidth / 64) : 1;
      ctx.drawImage(texture, u*unit, v*unit, w*unit, h*unit, 0, 0, w, h);
    } else if (color) {
      ctx.fillStyle = color;
      ctx.fillRect(0, 0, w, h);
    }
    if (color) {
      ctx.fillStyle = `rgba(35,15,51,${shade})`;
      ctx.fillRect(0, 0, w, h);
    }
    ctx.restore();
  }
}

function bindSkinPreview(canvas, skinSource, capeSource, slim, interactive) {
  let skin = null, cape = null, angle = capeSource ? Math.PI + .40 : .47, pointer = null;
  const draw = () => { if (canvas.isConnected) drawSkinPreview(canvas, skin, cape, slim, angle); };
  const load = (url, assign) => {
    if (!url) return;
    const img = new Image();
    img.onload = () => { assign(img); draw(); };
    img.src = url;
  };
  load(skinSource, img => { skin = img; });
  load(capeSource, img => { cape = img; });
  if (interactive) {
    canvas.tabIndex = 0;
    canvas.addEventListener('pointerdown', event => {
      canvas.setPointerCapture(event.pointerId);
      pointer = event.clientX;
    });
    canvas.addEventListener('pointermove', event => {
      if (pointer === null) return;
      angle += (event.clientX - pointer) * .027;
      pointer = event.clientX;
      draw();
    });
    canvas.addEventListener('lostpointercapture', () => { pointer = null; });
    canvas.addEventListener('keydown', event => {
      if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
      event.preventDefault();
      angle += event.key === 'ArrowLeft' ? -.18 : .18;
      draw();
    });
  }
  draw();
}
