import { useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Icon } from './icons';

export function TopShelfSupport({ apps }: { apps: string[] }) {
  const id = useId();
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const [host, setHost] = useState<HTMLElement | null>(null);

  useLayoutEffect(() => {
    if (!host || !trigger.current || !panel.current) return;
    const button = trigger.current;
    const popup = panel.current;
    const gap = 12;
    const position = () => {
      const area = host.getBoundingClientRect();
      const anchor = button.getBoundingClientRect();
      const center = anchor.left - area.left + anchor.width / 2;
      const left = Math.max(gap, Math.min(center - popup.offsetWidth / 2, area.width - popup.offsetWidth - gap));
      const below = anchor.bottom - area.top + 8;
      const above = anchor.top - area.top - popup.offsetHeight - 8;
      const opensAbove = below + popup.offsetHeight > area.height - gap && above >= gap;
      popup.dataset.side = opensAbove ? 'above' : 'below';
      popup.style.left = `${left}px`;
      popup.style.top = `${opensAbove ? above : below}px`;
      popup.style.setProperty('--support-pointer', `${center - left}px`);
    };
    const dismiss = () => setHost(null);
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !button.contains(event.target) && !popup.contains(event.target)) dismiss();
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      dismiss();
      button.focus({ preventScroll: true });
    };
    position();
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    window.addEventListener('resize', position);
    window.addEventListener('scroll', dismiss, true);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', escape);
      window.removeEventListener('resize', position);
      window.removeEventListener('scroll', dismiss, true);
    };
  }, [host]);

  return <>
    <button ref={trigger} type="button" className="top-shelf-info"
      aria-label="查看顶部内容预览支持的应用" aria-expanded={!!host}
      aria-controls={host ? id : undefined} aria-describedby={host ? id : undefined}
      onClick={event => setHost(host ? null : event.currentTarget.closest<HTMLElement>('.quiet-app'))}>
      <Icon name="InfoCircledIcon"/>
    </button>
    {host && createPortal(<div ref={panel} id={id} className="top-shelf-popover" role="note">
      <strong>当前支持读取</strong>
      <p>{apps.map((name, index) => <span key={name}>{name}{index < apps.length - 1 ? '、' : ''}</span>)}</p>
    </div>, host)}
  </>;
}
