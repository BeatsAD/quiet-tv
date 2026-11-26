import type { ReactNode } from 'react';
import { createPortal } from 'react-dom';
import type { App } from './api';
import { RowSortHandle, useRowSort } from './RowSort';
import { Icon, fileSize } from './widgets';

export function ManagedAppList({apps,order,revision,canSort,image,onOpen,onActions,onCommit}:{
  apps:App[];order:string[];revision:string;canSort:boolean;
  image:(app:App)=>ReactNode;onOpen:(app:App)=>void;onActions:(app:App)=>void;onCommit:(order:string[],revision:string)=>void;
}){
  const byId=new Map(apps.map(app=>[app.id,app])),sortable=new Set(order);
  const sort=useRowSort(order,revision,onCommit,id=>byId.get(id)?.name||id);

  function contents(app:App){
    const status=app.installed===false?'可恢复':app.childHidden?'儿童模式隐藏':app.hidden?'已隐藏':app.disabled?'已停用':'';
    return <>{image(app)}<span className="app-row-copy">
      <strong>{app.name}</strong>
      <small>版本 {app.version||'未知'}</small>
      <small>安装包 {fileSize(app.apkBytes)}</small>
      {status&&<small>{status}</small>}
    </span></>;
  }

  function row(app:App){
    const id=app.id,canMove=sortable.has(id);
    return <div key={id} data-app-id={id} data-sort-id={canMove?id:undefined}
      className={`app-row-shell ${sort.floating?.id===id?'is-placeholder':''}`}>
      <button type="button" className="app-row-open" onClick={()=>onOpen(app)}>{contents(app)}</button>
      <RowSortHandle title={app.name} disabled={!canSort||!canMove||order.length<2}
        onStart={event=>sort.start(event,id)} onActions={()=>onActions(app)}/>
      <button type="button" className="icon-button muted app-row-menu" aria-label={`${app.name}更多操作`} onClick={()=>onActions(app)}>
        <Icon name="DotsHorizontalIcon"/>
      </button>
    </div>;
  }

  const floating=sort.floating,ghost=floating&&byId.get(floating.id);
  return <>
    <div className="app-sort-list" {...sort.listProps}>
      {sort.order.map(id=>{const app=byId.get(id);return app&&row(app);})}
      {apps.filter(app=>!sortable.has(app.id)).map(row)}
    </div>
    {floating&&ghost&&sort.host&&createPortal(<div className="app-row-shell row-drag-ghost" aria-hidden
      style={{left:floating.left,top:floating.top,width:floating.width,height:floating.height}}>
      <div className="app-row-open">{contents(ghost)}</div>
      <Icon name="HamburgerMenuIcon" className="muted"/>
    </div>,sort.host)}
    <span className="quiet-sr-only" aria-live="polite">{sort.announcement}</span>
  </>;
}
