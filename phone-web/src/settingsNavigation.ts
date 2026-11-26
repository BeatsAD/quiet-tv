export type View = {
  kind: string;
  title: string;
  keys?: string[];
  id?: string;
  message?: string;
  mediaType?: 'image'|'video';
  quickRange?: boolean;
};

const pages = new Set([
  'app', 'desktop-fields', 'app-icons', 'app-icon-batch', 'about', 'child-time', 'daily', 'media',
  'memo-edit', 'about-release', 'about-license', 'quiz-edit', 'quiz-questions', 'quiz-settings', 'screensaver',
  'update', 'upload-options', 'wallpaper', 'weather-component',
]);

export function isPage(view: View) {
  return pages.has(view.kind)||view.kind==='tool';
}

export function viewKey(view?: View) {
  return view ? [view.kind,view.id||view.title,...(view.keys||[])].join(':') : '';
}

function savedPages(trail: View[]) {
  return trail.filter(view=>isPage(view)&&view.kind!=='upload-options').map(view=>{
    const {kind,id,title,mediaType,keys}=view;
    // Titles and messages can contain personal filenames or draft text.
    return {kind,id,mediaType,keys:kind==='desktop-fields'||kind==='tool'&&['files','file-browser','file-detail','file-preview','backup-detail','backup-download'].includes(id||'')?keys:undefined,title:
      kind==='tool'&&['file-detail','input-test'].includes(id||'')?'文件详情':kind==='app'?'应用详情':title};
  }).filter(view=>!(view.kind==='tool'&&['input-test','backup-draft'].includes(view.id||'')));
}

export type SettingsHistory = {id: number; parent?: number; tab: number; pages: View[]};

export function readSettingsHistory(state: unknown = history.state): SettingsHistory | undefined {
  const entry=(state as {quietSettings?: SettingsHistory}|null)?.quietSettings;
  if(!entry||!Number.isInteger(entry.id)||!Number.isInteger(entry.tab)||entry.tab<0||entry.tab>6||!Array.isArray(entry.pages))return;
  return {...entry,pages:entry.pages.filter(view=>view&&typeof view.kind==='string'&&typeof view.title==='string'&&isPage(view))};
}

export function writeSettingsHistory(id:number,parent:number|undefined,tab:number,trail:View[],replace=false){
  const entry:SettingsHistory={id,parent,tab,pages:savedPages(trail)};
  const state={...history.state,quietSettings:entry};
  if(replace)history.replaceState(state,'');
  else history.pushState(state,'');
  return entry;
}

export function lastPage(trail:View[]) {
  for(let i=trail.length-1;i>=0;i--)if(isPage(trail[i]))return trail[i];
}
