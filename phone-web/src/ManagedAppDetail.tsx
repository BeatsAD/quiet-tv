import type { ReactNode } from 'react';
import type { App } from './api';
import { applicationActionTitle, type ApplicationAction } from './AppActionConfirmation';
import { fileSize, Group, Row } from './widgets';

const commonActions:Record<string,string>={
  open:'PlayCircledIcon', rename:'Pencil1Icon', cover:'ImageIcon', boot:'PowerIcon',
};
const desktopActions:Record<string,string>={
  hide:'EyeNoneIcon', show:'EyeOpenIcon', dockAdd:'HomePlusIcon', dockRemove:'HomeIcon',
};
const installationActions:Record<string,string>={
  DISABLE:'MinusCircledIcon', disable:'MinusCircledIcon', ENABLE:'CheckCircledIcon',
  UNINSTALL:'TrashIcon', systemUninstall:'TrashIcon', RESTORE:'DownloadIcon',
};

const groupedActionIds=new Set([...Object.keys(commonActions),...Object.keys(desktopActions),...Object.keys(installationActions)]);

export function ManagedAppDetail({app,image,busy,onAction}:{
  app:App; image:ReactNode; busy:boolean; onAction:(action:ApplicationAction)=>void;
}){
  const actions=app.actions||[];
  const section=(icons:Record<string,string>,others:ApplicationAction[]=[])=>{
    const ordered=Object.keys(icons).flatMap(id=>actions.filter(action=>action.id===id));
    if(!ordered.length&&!others.length)return null;
    return <Group>{[...ordered,...others].map(action=>
      <Row key={action.id} title={applicationActionTitle(action)} icon={icons[action.id]||'GearIcon'}
        danger={action.danger&&action.id!=='ENABLE'&&action.id!=='RESTORE'} hint={action.reason||undefined}
        disabled={busy||!!action.reason} navigate onClick={()=>onAction(action)}/>
    )}</Group>;
  };
  const otherActions=actions.filter(({id})=>id!=='restoreCover'&&!groupedActionIds.has(id));
  return <div className="managed-app-detail">
    <Group>
      <div className="app-detail-summary">
        <div className="app-detail-identity">
          {image}
          <div>
            <h2>{app.name}</h2>
            <p>{app.system?'预装应用':'用户应用'}</p>
          </div>
        </div>
        <dl className="app-detail-metadata">
          {app.renamed&&app.originalName&&<div><dt>原名称</dt><dd>{app.originalName}</dd></div>}
          <div><dt>版本</dt><dd>{app.version||'未知'}</dd></div>
          <div><dt>安装包</dt><dd>{fileSize(app.apkBytes)}</dd></div>
          <div><dt>包名</dt><dd>{app.id}</dd></div>
          {(app.protected||app.protection)&&<div><dt>保护状态</dt><dd>受保护</dd></div>}
        </dl>
        {app.protection&&!app.canRename&&<p className="section-note">{app.protection}</p>}
      </div>
    </Group>
    {section(commonActions,otherActions)}
    {section(desktopActions)}
    {section(installationActions)}
  </div>;
}
