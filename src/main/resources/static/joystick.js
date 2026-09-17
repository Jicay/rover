(() => {
  const sequence = document.querySelector('[data-testid="command-sequence"]');

  if (!sequence) {
    return;
  }

  const normalise = (value) => value.toUpperCase().replace(/[^FBLR]/g, '');

  const write = (value) => {
    sequence.value = value;
    sequence.focus();
  };

  document.querySelectorAll('[data-command]').forEach((key) => {
    key.addEventListener('click', () => {
      write(normalise(sequence.value) + key.dataset.command);
    });
  });

  const erase = document.querySelector('[data-testid="joystick-erase"]');

  if (erase) {
    erase.addEventListener('click', () => {
      write(normalise(sequence.value).slice(0, -1));
    });
  }

  sequence.addEventListener('input', () => {
    const cleaned = normalise(sequence.value);

    if (cleaned !== sequence.value) {
      const caret = Math.max(0, sequence.selectionStart - (sequence.value.length - cleaned.length));
      sequence.value = cleaned;
      sequence.setSelectionRange(caret, caret);
    }
  });
})();
