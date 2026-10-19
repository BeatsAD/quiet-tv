// The browser and TV use the same directional navigation; folded fields and disabled controls are excluded.
export function controlsIn(scope) {
  if (!scope) return [];
  return [...scope.querySelectorAll('button,input,select,a[href],summary,[tabindex="0"]')].filter(node => {
    if (node.disabled || node.getAttribute('aria-disabled') === 'true' || !node.getClientRects().length) return false;
    const folded = node.closest('details:not([open])');
    return !folded || node === folded.querySelector('summary');
  });
}

export function nextControl(controls, active, key) {
  if (!controls.includes(active)) return controls[0];
  const r = active.getBoundingClientRect(), horizontal = key === 'ArrowLeft' || key === 'ArrowRight';
  const positive = key === 'ArrowRight' || key === 'ArrowDown';
  const x = r.x + r.width / 2, y = r.y + r.height / 2;
  return controls.filter(node => node !== active).map(node => {
    const b = node.getBoundingClientRect(), dx = b.x + b.width / 2 - x, dy = b.y + b.height / 2 - y;
    const along = horizontal ? dx : dy, across = horizontal ? dy : dx;
    const overlap = horizontal ? b.top < r.bottom && b.bottom > r.top : b.left < r.right && b.right > r.left;
    return { node, along, score: Math.abs(along) + Math.abs(across) * 3 + (overlap ? 0 : 200) };
  }).filter(item => positive ? item.along > 4 : item.along < -4).sort((a, b) => a.score - b.score)[0]?.node;
}

export function focusControl(node) {
  if (!node || node.disabled) return;
  node.focus({ preventScroll: true });
  node.scrollIntoView({ block: 'nearest', inline: 'nearest' });
}
