import { useEffect, useRef, useState } from 'react';
import { cropGeometry, staticCover } from './media';
import { useOperationFeedback } from './OperationFeedback';

export function CoverCrop({file,shape,currentShape,busy,appName,onUpload}:{
  file:File;shape:string;currentShape:string;busy:boolean;appName?:string;onUpload:(file:File)=>Promise<boolean>;
}){
  const feedback=useOperationFeedback();
  const canvas=useRef<HTMLCanvasElement>(null),generation=useRef(0),submitting=useRef(false);
  const drag=useRef<{id:number;x:number;y:number;panX:number;panY:number}|null>(null);
  const [image,setImage]=useState<HTMLImageElement|null>(null),[zoom,setZoom]=useState(1),[pan,setPan]=useState({x:50,y:50});
  const [error,setError]=useState(''),[encoding,setEncoding]=useState(false);
  const width=shape==='rectangle'?720:512,height=shape==='rectangle'?405:512;
  const shapeChanged=shape!==currentShape,disabled=busy||encoding||shapeChanged;

  useEffect(()=>{
    const attempt=++generation.current;
    setEncoding(false);setImage(null);setError('');setZoom(1);setPan({x:50,y:50});drag.current=null;
    if(!file.size||file.size>2*1024*1024||!/^image[/](jpeg|png|webp)$/.test(file.type)){
      setError('请选择不超过 2 MB 的 JPG、PNG 或静态 WebP 图片');
      return;
    }
    let active=true;
    const url=URL.createObjectURL(file),source=new Image();
    source.onload=()=>{
      if(!active)return;
      const {naturalWidth:w,naturalHeight:h}=source;
      if(!w||!h||w>16384||h>16384||w*h>40000000){setError('图片尺寸无效或超过 4000 万像素');return;}
      setImage(source);
    };
    source.onerror=()=>{if(active)setError('图片无法读取，请重新选择');};
    void file.arrayBuffer().then(buffer=>{
      if(!active)return;
      if(!staticCover(buffer,file.type)){setError('请选择 JPG、PNG 或静态 WebP 图片');return;}
      source.src=url;
    }).catch(()=>{if(active)setError('图片无法读取，请重新选择');});
    return()=>{active=false;if(generation.current===attempt)generation.current++;URL.revokeObjectURL(url);};
  },[file]);

  useEffect(()=>{
    const context=canvas.current?.getContext('2d');
    if(!context||!image)return;
    const rect=cropGeometry(image.naturalWidth,image.naturalHeight,zoom,pan.x,pan.y,{width,height});
    context.clearRect(0,0,width,height);
    context.save();
    if(shape==='circle'){context.beginPath();context.arc(width/2,height/2,width/2,0,Math.PI*2);context.clip();}
    context.drawImage(image,rect.left,rect.top,rect.width,rect.height);
    context.restore();
  },[image,zoom,pan,shape,width,height]);

  async function submit(){
    const preview=canvas.current;
    if(!preview||!image||disabled||submitting.current)return;
    const attempt=generation.current;
    submitting.current=true;setEncoding(true);setError('');
    try{
      const blob=await new Promise<Blob>((resolve,reject)=>preview.toBlob(value=>{
        if(value)resolve(value);else reject(new Error('裁剪图片无法生成，请重新选择'));
      },'image/png'));
      if(generation.current!==attempt)return;
      if(blob.size>2*1024*1024)throw new Error('裁剪后的图片超过 2 MB，请选择其他图片');
      await onUpload(new File([blob],'icon.png',{type:'image/png'}));
    }catch(problem){
      const message=problem instanceof Error?problem.message:'裁剪未完成';
      setError(message);
      feedback({kind:'error',title:'图标裁剪失败',detail:message});
    }finally{
      submitting.current=false;
      if(generation.current===attempt)setEncoding(false);
    }
  }

  const clamp=(value:number)=>Math.max(0,Math.min(100,value));
  return <div className="cover-editor" data-shape={shape}>
    {appName&&<p className="section-note">{appName}</p>}
    <canvas ref={canvas} width={width} height={height} aria-label="图标裁剪预览"
      onPointerDown={event=>{
        if(disabled||!image||!event.isPrimary)return;
        event.currentTarget.setPointerCapture(event.pointerId);
        drag.current={id:event.pointerId,x:event.clientX,y:event.clientY,panX:pan.x,panY:pan.y};
      }}
      onPointerMove={event=>{
        const start=drag.current;
        if(!start||start.id!==event.pointerId||!image||disabled)return;
        const rect=cropGeometry(image.naturalWidth,image.naturalHeight,zoom,start.panX,start.panY,{width,height});
        const scale=width/event.currentTarget.getBoundingClientRect().width,dx=rect.width-width,dy=rect.height-height;
        setPan({x:Math.abs(dx)>1?clamp(start.panX-(event.clientX-start.x)*scale/dx):50,
          y:Math.abs(dy)>1?clamp(start.panY-(event.clientY-start.y)*scale/dy):50});
      }}
      onPointerUp={()=>{drag.current=null;}} onPointerCancel={()=>{drag.current=null;}} onLostPointerCapture={()=>{drag.current=null;}}/>
    {!image&&!error&&<p role="status">正在读取图片…</p>}
    <fieldset disabled={disabled||!image} className="crop-controls">
      <label>缩放 <output>{Math.round(zoom*100)}%</output><input type="range" min={1} max={4} step={.01} value={zoom} onChange={event=>setZoom(Number(event.target.value))}/></label>
      <label>水平位置<input type="range" min={0} max={100} value={pan.x} onChange={event=>setPan({...pan,x:Number(event.target.value)})}/></label>
      <label>垂直位置<input type="range" min={0} max={100} value={pan.y} onChange={event=>setPan({...pan,y:Number(event.target.value)})}/></label>
    </fieldset>
    {error&&<p className="form-error" role="alert">{error}</p>}
    {shapeChanged&&<p className="form-error" role="alert">桌面图标形状已变化，请返回后重新选择图片。</p>}
    <button className="primary-button" disabled={disabled||!image} onClick={()=>void submit()}>确认替换</button>
  </div>;
}
