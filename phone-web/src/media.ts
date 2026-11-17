export const wallpaperAccept='image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp,video/*,.mp4,.mov,.mkv,.webm,.m4v,.3gp,.3g2,.ts,.m2ts,.mts,.mpg,.mpeg,.vob,.flv';
/** Match the TV cover policy and preserve original upload filenames. */
export function mediaLabel(filename:string){return filename.replace(/\.[^.]+$/,'').replace(/[\x00-\x1f\x7f-\x9f/\\]/g,' ').trim().slice(0,48).replace(/[\uD800-\uDBFF]$/,'')||'我的壁纸';}
export const coverWidth=720,coverHeight=406;
export function cropGeometry(width:number,height:number,zoom:number,x:number,y:number){
  const scale=Math.max(coverWidth/width,coverHeight/height)*zoom;
  return {width:width*scale,height:height*scale,left:-(width*scale-coverWidth)*x/100,top:-(height*scale-coverHeight)*y/100};
}
export function coverZoomLimit(width:number,height:number){return Math.min(4,width/360,height/203);}

export function staticCover(buffer:ArrayBuffer,type:string){
  const view=new DataView(buffer),text=(p:number)=>String.fromCharCode(...new Uint8Array(buffer,p,4));
  if(type==='image/jpeg')return view.byteLength>=3&&view.getUint16(0)===0xffd8;
  const png=type==='image/png',start=png?8:12;
  if(view.byteLength<start||png&&(view.getUint32(0)!==0x89504e47||view.getUint32(4)!==0x0d0a1a0a)||!png&&(text(0)!=='RIFF'||text(8)!=='WEBP'))return false;
  for(let p=start;p+8<=view.byteLength;){const size=view.getUint32(png?p:p+4,!png),kind=text(png?p+4:p);if(['acTL','ANIM','ANMF'].includes(kind))return false;const next=p+8+size+(png?4:size%2);if(next>view.byteLength)return false;p=next;}
  return true;
}

export function mediaRenameTarget(hash:string){
  const params=new URLSearchParams(hash.replace(/^#/,'')),id=params.get('rename'),source=params.get('source')||'wallpaper';
  return id&&/^user-[a-f0-9-]{36}$/.test(id)&&['wallpaper','screensaver'].includes(source)?{id,source}:null;
}

export function wallpaperProblem(file:File,limits:Record<string,number>={}){
  const video=file.type.startsWith('video/')||/\.(mp4|mov|mkv|webm|m4v|3gp|3g2|ts|m2ts|mts|mpg|mpeg|vob|flv)$/i.test(file.name);
  return !file.size?'文件不能为空':!video&&!/\.(jpe?g|png|webp)$/i.test(file.name)&&!['image/jpeg','image/png','image/webp'].includes(file.type)?'不支持的图片格式':!video&&limits.wallpaper>0&&file.size>limits.wallpaper?'文件超过大小上限或电视可用空间':'';
}
