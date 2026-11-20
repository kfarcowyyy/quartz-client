let accountHeadSequence = 0;

function drawAccountHead(canvas, texture) {
  const drawing = canvas.getContext('2d');
  drawing.clearRect(0, 0, 38, 38);
  drawing.imageSmoothingEnabled = false;
  // Each face is drawn from its eight-pixel region in the Minecraft skin atlas.
  const faces = [
    { origin: [14, 2], x: [19, 6], y: [-10, 8], uv: [8, 0], color: '#b58bdb', shade: 0 },
    { origin: [4, 10], x: [19, 6], y: [0, 20], uv: [8, 8], color: '#9568c0', shade: .12 },
    { origin: [23, 16], x: [10, -8], y: [0, 20], uv: [16, 8], color: '#754f9c', shade: .24 },
  ];
  for (const face of faces) {
    const [u, v] = face.uv;
    const [x, y] = face.origin;
    drawing.setTransform(face.x[0] / 8, face.x[1] / 8, face.y[0] / 8, face.y[1] / 8, x, y);
    if (texture) {
      drawing.drawImage(texture, u, v, 8, 8, 0, 0, 8, 8);
      drawing.drawImage(texture, u + 32, v, 8, 8, 0, 0, 8, 8);
      drawing.fillStyle = `rgba(38, 21, 53, ${face.shade})`;
    } else {
      drawing.fillStyle = face.color;
    }
    drawing.fillRect(0, 0, 8, 8);
    if (!texture && u === 8 && v === 8) {
      drawing.fillStyle = '#e7d7f4';
      drawing.fillRect(1, 3, 2, 1);
      drawing.fillRect(5, 3, 2, 1);
      drawing.fillStyle = '#634683';
      drawing.fillRect(3, 5, 2, 1);
    }
  }
  drawing.resetTransform();
}

async function updateAccountHead() {
  const canvas = document.getElementById('account-head');
  const sequence = ++accountHeadSequence;
  if (!canvas) return;
  drawAccountHead(canvas);
  try {
    const texture = await api('cosmetics:active-head');
    if (!texture || !canvas.isConnected || sequence !== accountHeadSequence) return;
    const image = new Image();
    image.src = texture;
    await image.decode();
    if (canvas.isConnected && sequence === accountHeadSequence) drawAccountHead(canvas, image);
  } catch {
    /* Keep the placeholder if the skin cannot be loaded. */
  }
}
