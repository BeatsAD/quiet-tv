import { useEffect, useRef, useState } from 'react';
import { coverWidth, coverHeight, cropGeometry, coverZoomLimit, staticCover } from './media';
import { ConfirmDialog } from './widgets';
import { useOperationFeedback } from './OperationFeedback';

/** Same fixed 720 × 406 frame and admission limits as the TV cover uploader. */
export function CoverCrop({file,busy,appName,problem,onUpload}:{file:File;busy:boolean;appName?:string;problem?:string;onUpload:(file:File)=>Promise<boolean|void>}){
  const feedback=useOperationFeedback(),submitting=useRef(false);
  const canvas=useRef<HTMLCanvasElement>(null),generation=useRef(0),drag=useRef<{id:number;x:number;y:number;panX:number;panY:number}|null>(null);
  const [image,setImage]=useState<HTMLImageElement|null>(null),[zoom,setZoom]=useState(1),[pan,setPan]=useState({x:50,y:50}),[error,setError]=useState(''),[encoding,setEncoding]=useState(false);
  const [confirm,setConfirm]=useState(false),[uploadFailed,setUploadFailed]=useState(false);
  const disabled=busy||encoding;
  useEffect(()=>{
    const attempt=++generation.current;setEncoding(false);setImage(null);setError('');setConfirm(false);setUploadFailed(false);setZoom(1);setPan({x:50,y:50});drag.current=null;
    if(file.size>2*1024*1024){setError('请选择不超过 2 MB 的 JPG、PNG 或 WebP 图片');return;}
    if(!/^(image\/(jpeg|png|webp))$/.test(file.type)){setError('封面支持 JPG、PNG 和 WebP 静态图片');return;}
    let active=true;const url=URL.createObjectURL(file),source=new Image();
    source.onload=()=>{if(!active)return;const {naturalWidth:w,naturalHeight:h}=source;if(w<360||h<203||w>16384||h>16384||w*h>40000000){setError('图片至少为 360 × 203，且不能超过 4000 万像素');return;}setImage(source);};
    source.onerror=()=>{if(active)setError('图片无法读取，请重新选择');};void file.arrayBuffer().then(buffer=>{if(!active)return;if(!staticCover(buffer,file.type)){setError('请选择 JPG、PNG 或 WebP 静态图片');return;}source.src=url;}).catch(()=>{if(active)setError('图片无法读取，请重新选择');});
    return()=>{active=false;if(generation.current===attempt)generation.current++;URL.revokeObjectURL(url);};
  },[file]);
  useEffect(()=>{const context=canvas.current?.getContext('2d');if(!context||!image)return;const rect=cropGeometry(image.naturalWidth,image.naturalHeight,zoom,pan.x,pan.y);context.fillStyle='#505966';context.fillRect(0,0,coverWidth,coverHeight);context.drawImage(image,rect.left,rect.top,rect.width,rect.height);},[image,zoom,pan]);
  async function submit(){if(!canvas.current||!image||disabled||submitting.current)return;const attempt=generation.current;submitting.current=true;setEncoding(true);setError('');setUploadFailed(false);try{const blob=await new Promise<Blob>((resolve,reject)=>canvas.current!.toBlob(value=>value?resolve(value):reject(new Error('裁剪图片无法生成，请重新选择')),'image/png'));if(generation.current!==attempt)return;if(blob.size>2*1024*1024)throw new Error('裁剪后的图片超过 2 MB，请选择其他图片');if(await onUpload(new File([blob],'cover.png',{type:'image/png'}))===false)setUploadFailed(true);else setConfirm(false);}catch(error){const message=error instanceof Error?error.message:'裁剪未完成';setError(message);feedback({kind:'error',title:'封面生成失败',detail:message});}finally{submitting.current=false;if(generation.current===attempt)setEncoding(false);}}
  return <div className="cover-editor">
    <p className="section-note">拖动图片调整位置，滑动下方控件缩放。</p>
    <canvas ref={canvas} width={coverWidth} height={coverHeight} aria-label="封面裁剪预览" onPointerDown={event=>{if(disabled||!image||!event.isPrimary)return;event.currentTarget.setPointerCapture(event.pointerId);drag.current={id:event.pointerId,x:event.clientX,y:event.clientY,panX:pan.x,panY:pan.y};}} onPointerMove={event=>{const start=drag.current;if(!start||start.id!==event.pointerId||!image||disabled)return;const rect=cropGeometry(image.naturalWidth,image.naturalHeight,zoom,start.panX,start.panY),scale=coverWidth/event.currentTarget.getBoundingClientRect().width,dx=rect.width-coverWidth,dy=rect.height-coverHeight;setPan({x:dx>0?Math.max(0,Math.min(100,start.panX-(event.clientX-start.x)*scale/dx*100)):50,y:dy>0?Math.max(0,Math.min(100,start.panY-(event.clientY-start.y)*scale/dy*100)):50});}} onPointerUp={()=>{drag.current=null;}} onPointerCancel={()=>{drag.current=null;}} onLostPointerCapture={()=>{drag.current=null;}}/>
    {!image&&!error&&<p role="status">正在读取图片…</p>}
    <fieldset disabled={disabled||!image} className="crop-controls">
      <label>缩放 <output>{Math.round(zoom*100)}%</output><input aria-label="封面缩放" type="range" min={1} max={image?coverZoomLimit(image.naturalWidth,image.naturalHeight):1} step={.01} value={zoom} onChange={event=>setZoom(Number(event.target.value))}/></label>
      <label>水平位置<input aria-label="封面水平位置" type="range" min={0} max={100} value={pan.x} onChange={event=>setPan({...pan,x:Number(event.target.value)})}/></label>
      <label>垂直位置<input aria-label="封面垂直位置" type="range" min={0} max={100} value={pan.y} onChange={event=>setPan({...pan,y:Number(event.target.value)})}/></label>
    </fieldset>
    {error&&<p className="form-error" role="alert">{error}</p>}
    <button className="primary-button" disabled={disabled||!image} onClick={()=>{setError('');setUploadFailed(false);setConfirm(true);}}>上传并应用</button>
    {confirm&&<ConfirmDialog title="更换封面？" confirmLabel="确认更换" busyLabel="正在更换…" destructive={false} busy={disabled} onCancel={()=>setConfirm(false)} onConfirm={()=>void submit()}>
      <p className="confirmation-media-name">{appName||'当前应用'}</p><p>将使用当前裁剪结果替换此应用的封面。</p>
      {(error||uploadFailed)&&<p className="form-error" role="alert">{error||problem||'封面更新未完成，请重试。'}</p>}
    </ConfirmDialog>}
  </div>;
}
