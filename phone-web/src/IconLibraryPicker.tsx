import type { App, PhoneClient } from './api';
import { EmptyState, Icon, ResourceImage } from './widgets';

export type IconTarget=App&{coverKey:string;customCover:boolean};
type IconVariant={shape:string;form:string};
export type IconItem={id:string;name:string;style:string;variants?:IconVariant[]};
export type IconCatalog={revision:number;problem?:string;styles:string[];styleNames?:Record<string,string>;shapes:string[];items:IconItem[];targets:IconTarget[]};
export type IconFilters={style:string;shape:string;form:string};
export type IconSelection=IconFilters&{package:string;coverKey:string;id:string;catalogVersion:number};
export const iconTitles:Record<string,string>={apple:'苹果风格',google:'谷歌风',flat:'扁平化',skeuo:'拟物化',soft:'轻拟物','3d':'3D 风格',pixel:'像素风格',rectangle:'圆角矩形',circle:'圆形',square:'圆角正方形',text:'文字＋图标',icon:'纯图标',transparent:'透明背景'};
export const filterTitles={style:'图标风格',shape:'图标形状',form:'呈现形式'};
const iconShapes=['rectangle','circle','square'];
const iconForms=['icon','text','transparent'];
export function iconVariants(item:IconItem):IconVariant[]{
  return item.variants||iconShapes.flatMap(shape=>iconForms.filter(form=>shape==='rectangle'||form!=='text').map(form=>({shape,form})));
}
export function galleryFilterChoices(catalog:IconCatalog,key:keyof IconFilters){
  if(key==='style')return catalog.styles.map(value=>({value,title:catalog.styleNames?.[value]||iconTitles[value]||value}));
  const available=new Set(catalog.items.flatMap(item=>iconVariants(item).map(variant=>variant[key])));
  const values=key==='shape'?iconShapes:iconForms;
  return values.filter(value=>available.has(value)).map(value=>({value,title:iconTitles[value]}));
}
export function galleryIcons(catalog:IconCatalog,filters:IconFilters,query:string,desktopShape:string){
  const keyword=query.normalize('NFKC').trim().toLowerCase();
  return catalog.items.flatMap(item=>{
    if((filters.style&&item.style!==filters.style)||!item.name.normalize('NFKC').toLowerCase().includes(keyword))return [];
    const variants=iconVariants(item).filter(variant=>(!filters.shape||variant.shape===filters.shape)&&(!filters.form||variant.form===filters.form));
    const compatible=variants.filter(({shape})=>shape===desktopShape);
    const variant=compatible.find(({form})=>form==='icon')||compatible[0]||variants[0];
    return variant?[{item,filters:{style:item.style,...variant}}]:[];
  });
}
export function desktopIconShape(client:PhoneClient){
  const shape=client.frame?.domains.appearance?.iconShape;
  return shape==='circle'||shape==='square'?shape:'rectangle';
}
export function iconResource(client:PhoneClient,filters:IconFilters,id:string,revision:number,preview=false){return client.resource(`api/resources/icon-library/${filters.style}/${id}/${filters.shape}/${filters.form}/${preview?'preview':'thumb'}?v=${revision}`);}
export function filteredIcons(catalog:IconCatalog,filters:IconFilters){return catalog.items.filter(item=>item.style===filters.style&&(!item.variants||item.variants.some(variant=>variant.shape===filters.shape&&variant.form===filters.form)));}
export function iconFilterChoices(catalog:IconCatalog,filters:IconFilters,key:'style'|'form',forms?:string[]){
  const values=key==='style'?catalog.styles:forms??(filters.shape==='rectangle'?['text','icon','transparent']:['icon','transparent']);
  return values.map(value=>({value,title:key==='style'?catalog.styleNames?.[value]||iconTitles[value]||value:iconTitles[value]||value}));
}
export function IconMaterialTile({item,src,selected,disabled,onSelect,styleName}:{item:IconItem;src:string;selected:boolean;disabled:boolean;onSelect:()=>void;styleName?:string}){
  return <button type="button" className="icon-material" aria-pressed={selected}
    aria-label={[item.name,styleName,selected?'已选中':''].filter(Boolean).join('  ')} disabled={disabled} onClick={onSelect}>
    <span className="icon-material-art"><ResourceImage src={src} alt=""/>
      {selected&&<span className="icon-material-check"><Icon name="CheckIcon"/></span>}
    </span>
    <span>{item.name}{styleName&&<small>{styleName}</small>}</span>
  </button>;
}
export function IconMaterialGrid({client,catalog,filters,selected,ids,onSelect,disabled}:{client:PhoneClient;catalog:IconCatalog;filters:IconFilters;selected:string;ids?:string[];onSelect:(item:IconItem)=>void;disabled:boolean}){
  const items=filteredIcons(catalog,filters).filter(item=>!ids||ids.includes(item.id));
  return items.length?<div className={`icon-material-grid shape-${filters.shape}`} data-form={filters.form}>
    {items.map(item=><IconMaterialTile key={item.id} item={item} src={iconResource(client,filters,item.id,catalog.revision)}
      selected={selected===item.id} disabled={disabled} onSelect={()=>onSelect(item)}/>)}
  </div>:<EmptyState icon="ImageIcon" title={catalog.problem||'此组合暂无图标'} alert={!!catalog.problem}/>;
}
