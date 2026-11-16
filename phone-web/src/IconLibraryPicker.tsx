import type { App, PhoneClient } from './api';
import { ChoiceList, Icon, ResourceImage } from './widgets';

export type IconTarget=App&{coverKey:string;customCover:boolean};
export type IconItem={id:string;name:string;style:string;variants?:{shape:string;form:string}[]};
export type IconCatalog={revision:number;problem?:string;styles:string[];shapes:string[];items:IconItem[];targets:IconTarget[]};
export type IconFilters={style:string;shape:string;form:string};
export type IconSelection=IconFilters&{package:string;coverKey:string;id:string;catalogVersion:number};
export type FilterPage=''|'style'|'shape'|'form';
export const iconTitles:Record<string,string>={apple:'苹果风',google:'谷歌风',flat:'扁平化',skeuo:'拟物化',soft:'轻拟物','3d':'3D 风格',pixel:'像素风格',rectangle:'长方形',circle:'圆形',square:'方形',text:'文字＋图标',icon:'纯图标',transparent:'透明背景'};
export const filterTitles={style:'图标风格',shape:'图标形状',form:'呈现形式'};
export function iconResource(client:PhoneClient,filters:IconFilters,id:string,revision:number,preview=false){return client.resource(`api/resources/icon-library/${filters.style}/${id}/${filters.shape}/${filters.form}/${preview?'preview':'thumb'}?v=${revision}`);}
export function filteredIcons(catalog:IconCatalog,filters:IconFilters){return catalog.items.filter(item=>item.style===filters.style&&(!item.variants||item.variants.some(variant=>variant.shape===filters.shape&&variant.form===filters.form)));}
export function IconFiltersBar({filters,disabled,shapeLocked=false,onOpen}:{filters:IconFilters;disabled:boolean;shapeLocked?:boolean;onOpen:(page:FilterPage)=>void}){
  return <div className="icon-filters" aria-label="图标筛选条件">{(['style','shape','form'] as const).map(key=><button key={key} disabled={disabled||shapeLocked&&key==='shape'} aria-label={`${filterTitles[key]}：${iconTitles[filters[key]]||filters[key]}${shapeLocked&&key==='shape'?'  跟随桌面设置':''}`} onClick={()=>onOpen(key)}><span>{iconTitles[filters[key]]||filters[key]}</span>{!(shapeLocked&&key==='shape')&&<Icon name="ChevronDownIcon"/>}</button>)}</div>;
}
export function IconFilterOptions({catalog,filters,page,onChange,disabled}:{catalog:IconCatalog;filters:IconFilters;page:Exclude<FilterPage,''>;onChange:(filters:IconFilters)=>void;disabled:boolean}){
  const values=page==='style'?catalog.styles:page==='shape'?catalog.shapes:filters.shape==='rectangle'?['text','icon','transparent']:['icon','transparent'];
  return <ChoiceList title={filterTitles[page]} options={values.map(value=>({value,title:iconTitles[value]||value}))} value={filters[page]} disabled={disabled} onChange={value=>{const next={...filters,[page]:String(value)};if(next.shape!=='rectangle'&&next.form==='text')next.form='icon';onChange(next);}}/>;
}
export function IconMaterialGrid({client,catalog,filters,selected,ids,onSelect,disabled}:{client:PhoneClient;catalog:IconCatalog;filters:IconFilters;selected:string;ids?:string[];onSelect:(item:IconItem)=>void;disabled:boolean}){
  const items=filteredIcons(catalog,filters).filter(item=>!ids||ids.includes(item.id));
  return items.length?<div className={`icon-material-grid shape-${filters.shape}`}>{items.map(item=><button key={item.id} className="icon-material" aria-pressed={selected===item.id} disabled={disabled} onClick={()=>onSelect(item)}><span className="icon-material-art"><ResourceImage src={iconResource(client,filters,item.id,catalog.revision)} alt=""/>{selected===item.id&&<span className="icon-material-check"><Icon name="CheckIcon"/></span>}</span><span>{item.name}</span></button>)}</div>:<p className="empty-state">{catalog.problem||'此组合暂无图标'}</p>;
}
