import { RowSortHandle, useRowSort } from './RowSort';
import { createPortal } from 'react-dom';
import { Icon, quickGlyph } from './widgets';
import { QuickIcon, type QuickIcons, type QuickItem } from './QuickPreview';
export function QuickSort({order,revision,catalog,onCommit,onActions,hiddenIds=[],icons,status,soundOn}:{order:string[];hiddenIds?:string[];status?:(id:string)=>string;soundOn?:(id:string)=>boolean|undefined;revision:string;catalog:QuickItem[];icons?:QuickIcons;onCommit:(ids:string[],revision:string)=>void;onActions:(id:string)=>void}){
  function title(id:string){return catalog.find(item=>item.id===id)?.title||id;}
  const sort=useRowSort(order,revision,onCommit,title);
  const {floating,announcement,host}=sort;
  return <><div className="quick-sort-list" {...sort.listProps} onContextMenu={event=>event.preventDefault()}>{sort.order.map(id=>{
    const state=status?.(id);
    return <div key={id} data-quick-id={id} data-sort-id={id} className="quick-sort-row">
      <div className={`setting-row quick-item ${floating?.id===id?'is-placeholder':''}`}>
        <QuickIcon glyph={catalog.find(item=>item.id===id)?.glyph||id} icons={icons} fallback={quickGlyph(id)} off={soundOn?.(id)===false} className="row-icon"/>
        <span className="row-title quick-name">{title(id)}{state&&<small>  {state}</small>}{hiddenIds.includes(id)&&<small>  未显示</small>}</span>
        <RowSortHandle className="quick-sort-handle" title={title(id)} onStart={event=>sort.start(event,id)} onActions={()=>onActions(id)}/>
        <button type="button" className="icon-button muted quick-menu" aria-label={`${title(id)}更多操作`} onClick={()=>onActions(id)}><Icon name="DotsHorizontalIcon"/></button>
      </div>
    </div>;
  })}</div>{floating&&host&&createPortal(<div className="setting-row quick-item quick-drag-ghost" aria-hidden style={{left:floating.left,top:floating.top,width:floating.width}}><QuickIcon glyph={catalog.find(item=>item.id===floating.id)?.glyph||floating.id} icons={icons} fallback={quickGlyph(floating.id)} off={soundOn?.(floating.id)===false} className="row-icon"/><span className="row-title">{title(floating.id)}</span><Icon name="HamburgerMenuIcon"/></div>,host)}<span className="quiet-sr-only" aria-live="polite">{announcement}</span></>;
}
