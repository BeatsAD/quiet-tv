(() => {
  const scope = document.querySelector('[role="dialog"]') || document.querySelector('.q-tv');
  if (!scope || !window.quietTVRemote) return null;
  const nodes = [...scope.querySelectorAll('button,input,select,a[href],summary,[tabindex="0"]')].filter(n => {
    if (n.disabled || !n.getClientRects().length) return false;
    const folded = n.closest('details:not([open])');
    return !folded || n === folded.querySelector('summary');
  });
  const origin = Math.max(0, nodes.indexOf(document.activeElement));
  const keys = ['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'];
  const edges = nodes.map(node => keys.map(key => {
    window.quietTVRemote.finishEditing();
    node.focus({preventScroll:true});window.quietTVRemote.move(key);
    return nodes.indexOf(document.activeElement);
  }));
  const paths = Array(nodes.length).fill(null);paths[origin] = [];
  const queue = [origin];
  for (let i=0; i<queue.length; i++) {
    const at=queue[i];
    edges[at].forEach((to,k) => {if(to>=0 && paths[to]===null){paths[to]=[...paths[at],keys[k]];queue.push(to)}});
  }
  nodes[origin]?.focus({preventScroll:true});
  const items=nodes.map(n=>(n.getAttribute('aria-label')||n.textContent||n.placeholder||n.tagName).replace(/\s+/g,' ').trim());
  return {items,paths,missing:items.filter((_,i)=>paths[i]===null),escapes:edges.some(row=>row.some(i=>i<0)),focused:origin};
})()
