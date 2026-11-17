import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Icon } from './icons';
import type { OperationNotice } from './operationResults';

const FeedbackContext=createContext<(notice:OperationNotice)=>void>(()=>{});
export const useOperationFeedback=()=>useContext(FeedbackContext);

export function OperationFeedbackProvider({children}:{children:ReactNode}){
  const [notice,setNotice]=useState<(OperationNotice&{id:number})|null>(null),[host,setHost]=useState<Element|null>(null),sequence=useRef(0);
  const show=useCallback((next:OperationNotice)=>setNotice({...next,id:++sequence.current}),[]);
  useEffect(()=>{if(!notice)return;const timer=setTimeout(()=>setNotice(null),3000);return()=>clearTimeout(timer);},[notice?.id]);
  useLayoutEffect(()=>{
    // Portals must stay inside the active native dialog's top layer, including after returning to its parent panel.
    const update=()=>{const dialogs=[...document.querySelectorAll('dialog[open]:not(.is-closing)')],dialog=dialogs.at(-1);setHost(dialog?.querySelector('.sheet-panel')||dialog||document.querySelector('.quiet-app'));};
    const observer=new MutationObserver(update);observer.observe(document.body,{subtree:true,childList:true,attributes:true,attributeFilter:['open','class']});update();return()=>observer.disconnect();
  },[]);
  const symbol=notice?.kind==='success'?'CheckCircledIcon':notice?.kind==='error'?'ExclamationTriangleIcon':'InfoCircledIcon';
  return <FeedbackContext.Provider value={show}>{children}{host&&notice&&createPortal(<div key={notice.id} className={`operation-toast ${notice.kind}`} role={notice.kind==='error'?'alert':'status'} aria-live={notice.kind==='error'?'assertive':'polite'} aria-atomic="true"><Icon name={symbol} className="operation-toast-icon"/><div><strong>{notice.title}</strong>{notice.detail&&notice.detail!==notice.title&&<span>{notice.detail}</span>}</div></div>,host)}</FeedbackContext.Provider>;
}
