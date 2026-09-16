(() => {
  const widthField = document.querySelector('[data-testid="board-width"]');
  const heightField = document.querySelector('[data-testid="board-height"]');
  const obstacleField = document.querySelector('[data-testid="board-obstacles"]');
  const preview = document.querySelector('[data-testid="board-preview"]');
  const hint = document.querySelector('[data-testid="preview-hint"]');
  const overflow = document.querySelector('[data-testid="preview-too-large"]');

  if (!widthField || !heightField || !obstacleField || !preview) {
    return;
  }

  const MAX_CELLS = 400;
  const PAIR = /(-?\d+)\s*,\s*(-?\d+)/g;

  const obstacles = () =>
    [...obstacleField.value.matchAll(PAIR)].map(([, x, y]) => ({ x: Number(x), y: Number(y) }));

  const store = (pairs) => {
    obstacleField.value = pairs.map(({ x, y }) => `${x},${y}`).join(' ');
  };

  const dimension = (field) => {
    const value = Number.parseInt(field.value, 10);
    return Number.isFinite(value) && value > 0 ? value : 0;
  };

  const state = (name) => {
    preview.dataset.previewState = name;
    if (hint) {
      hint.hidden = name !== 'ready';
    }
    if (overflow) {
      overflow.hidden = name !== 'too-large';
    }
  };

  const toggle = (x, y) => {
    const pairs = obstacles();
    const index = pairs.findIndex((pair) => pair.x === x && pair.y === y);

    if (index === -1) {
      pairs.push({ x, y });
    } else {
      pairs.splice(index, 1);
    }

    store(pairs);
    render();
    preview.querySelector(`[data-preview-cell="${x},${y}"]`)?.focus();
  };

  const render = () => {
    const width = dimension(widthField);
    const height = dimension(heightField);

    preview.replaceChildren();

    if (width === 0 || height === 0) {
      state('empty');
      return;
    }

    if (width * height > MAX_CELLS) {
      state('too-large');
      return;
    }

    state('ready');
    preview.style.gridTemplateColumns = `repeat(${width}, minmax(0, 1fr))`;

    const pairs = obstacles();

    for (let y = height - 1; y >= 0; y -= 1) {
      for (let x = 0; x < width; x += 1) {
        const rock = pairs.some((pair) => pair.x === x && pair.y === y);
        const cell = document.createElement('button');

        cell.type = 'button';
        cell.className = 'preview__cell';
        cell.dataset.previewCell = `${x},${y}`;
        cell.setAttribute('aria-pressed', String(rock));
        cell.setAttribute('aria-label', `Rocher en ${x}, ${y}`);

        if (rock) {
          cell.dataset.obstacle = 'true';
        }

        cell.addEventListener('click', () => toggle(x, y));
        preview.append(cell);
      }
    }
  };

  const resize = () => {
    const width = dimension(widthField);
    const height = dimension(heightField);

    if (width > 0 && height > 0) {
      const pairs = obstacles();
      const kept = pairs.filter((pair) =>
        pair.x >= 0 && pair.x < width && pair.y >= 0 && pair.y < height);

      if (kept.length !== pairs.length) {
        store(kept);
      }
    }

    render();
  };

  widthField.addEventListener('input', render);
  heightField.addEventListener('input', render);
  widthField.addEventListener('change', resize);
  heightField.addEventListener('change', resize);
  obstacleField.addEventListener('input', render);

  render();
})();
