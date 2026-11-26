import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react';
import { Icon } from './icons';

export function AppFilterMenu({options,value,onChange,title='应用筛选',disabled=false}:{
  options:string[];value:number;onChange:(value:number)=>void;title?:string;disabled?:boolean;
}){
  const [open,setOpen]=useState(false),id=useId();
  const root=useRef<HTMLDivElement>(null),trigger=useRef<HTMLButtonElement>(null),menu=useRef<HTMLDivElement>(null);
  const label=options[value];
  const longest=Math.max(...options.map(option=>Array.from(option).length));

  useEffect(()=>{
    if(disabled){if(open)setOpen(false);return;}
    if(!open)return;
    const outside=(event:PointerEvent)=>{
      if(!root.current?.contains(event.target as Node))setOpen(false);
    };
    document.addEventListener('pointerdown',outside);
    return ()=>document.removeEventListener('pointerdown',outside);
  },[open,disabled]);

  useLayoutEffect(()=>{
    if(open)menu.current?.querySelector<HTMLButtonElement>('[aria-checked=true]')?.focus();
  },[open]);

  function close(){
    setOpen(false);
    trigger.current?.focus({preventScroll:true});
  }

  function navigate(event:KeyboardEvent<HTMLDivElement>){
    if(event.key==='Escape'||event.key==='Tab'){
      if(event.key==='Escape')event.preventDefault();
      close();
      return;
    }
    const items=Array.from(menu.current?.querySelectorAll('button')||[]);
    const index=items.indexOf(document.activeElement as HTMLButtonElement);
    const last=items.length-1;
    switch(event.key){
      case 'ArrowDown':event.preventDefault();items[(index+1)%items.length]?.focus();break;
      case 'ArrowUp':event.preventDefault();items[index<=0?last:index-1]?.focus();break;
      case 'Home':event.preventDefault();items[0]?.focus();break;
      case 'End':event.preventDefault();items[last]?.focus();break;
    }
  }

  return <div ref={root} className="app-toolbar-field app-toolbar-filter" data-open={open}
    style={{'--app-filter-characters':longest} as CSSProperties}
    onBlur={event=>{if(event.relatedTarget&&!event.currentTarget.contains(event.relatedTarget))setOpen(false);}}>
    <button ref={trigger} type="button" className="app-filter-trigger" disabled={disabled} aria-label={`${title}：${label}`}
      aria-haspopup="menu" aria-expanded={open&&!disabled} aria-controls={open&&!disabled?id:undefined}
      onClick={()=>setOpen(!open)} onKeyDown={event=>{
        if(event.key==='ArrowDown'||event.key==='ArrowUp'){event.preventDefault();setOpen(true);}
      }}>
      <span>{label}</span><Icon name="ChevronDownIcon"/>
    </button>
    {open&&!disabled&&<div ref={menu} id={id} className="app-filter-menu" role="menu" aria-label={title} onKeyDown={navigate}>
      {options.map((option,index)=><button key={index} type="button" role="menuitemradio" tabIndex={-1}
        aria-checked={value===index} onClick={()=>{onChange(index);close();}}>
        <span>{option}</span>{value===index&&<Icon name="CheckIcon"/>}
      </button>)}
    </div>}
  </div>;
}
