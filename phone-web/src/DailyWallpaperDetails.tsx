import type { PhoneClient } from './api';
import { DailyWallpaperRefresh } from './DailyWallpaperRefresh';
import { fileSize } from './widgets';

export function DailyWallpaperDetails({client,disabled}:{client:PhoneClient;disabled:boolean}){
  const info=(client.values().dailyWallpaper||client.metadata?.dailyWallpaper) as Record<string,unknown>|undefined;
  const movie=info?.movie as {title?:string;year?:number;overview?:string;rating?:{value?:number;vote_count?:number}}|undefined;
  return <article className="daily-picture-details">
    <header className="daily-picture-heading">
      <div className="daily-picture-source">{movie&&<img src="tmdb.svg" alt="TMDB" width="108" height="15"/>}<span>{String(info?.source||'必应')}</span></div>
      {!!info?.title&&<h3>{String(info.title)}</h3>}
      {movie&&<div className="daily-picture-rating">{!!movie.year&&<span>{movie.year}</span>}{movie.rating?.value!==undefined&&<span>TMDB <strong>{movie.rating.value.toFixed(1)}</strong></span>}{movie.rating?.vote_count!==undefined&&<span>{movie.rating.vote_count} 人评分</span>}</div>}
    </header>
    {!!movie?.overview&&<p className="daily-picture-overview">{movie.overview}</p>}
    <dl className="daily-picture-metadata">
      {!!movie&&!!info?.contentDate&&<div><dt>推荐日期</dt><dd>{String(info.contentDate)}</dd></div>}
      {!!info?.width&&!!info?.height&&<div><dt>尺寸</dt><dd>{String(info.width)} × {String(info.height)}</dd></div>}
      {!!info?.bytes&&<div><dt>大小</dt><dd>{fileSize(Number(info.bytes))}</dd></div>}
    </dl>
    {movie?<p className="daily-picture-attribution">This product uses the TMDB API but is not endorsed or certified by TMDB.</p>:!!info?.copyright&&<p className="daily-picture-attribution">{String(info.copyright)}</p>}
    <p className="section-note">{info?.cached?'每天更新，获取失败时保留当前背景。':'暂用本地壁纸，获取成功后自动切换。'}</p>
    <DailyWallpaperRefresh client={client} disabled={disabled}/>
  </article>;
}
