export function AnalogClock({style,hour,minute,second,seconds,label}:{style:string;hour:number;minute:number;second:number;seconds:boolean;label:string}){
  const light=style==='analog-light',ink=light?'#202020':'#d9d9d9',s=seconds?second:0;
  return <svg className="tv-analog-clock" viewBox="-50 -50 100 100" role="img" aria-label={label}>
    <circle r="50" fill={light?'#fafafa':'#000000d9'}/>
    <g strokeLinecap="round">{Array.from({length:60},(_,i)=><line key={i} x1="0" y1="-46" x2="0" y2={i%5===0?-42.8:-44} stroke={light?(i%5===0?'#666':'#b0b0b0'):(i%5===0?'#b8b8b8':'#777')} strokeWidth={i%5===0?.95:.7} transform={`rotate(${i*6})`}/>)}</g>
    <g fill={ink} fontFamily="sans-serif" fontWeight="500" fontSize="10.5" textAnchor="middle" dominantBaseline="central">{Array.from({length:12},(_,i)=><text key={i} x={Math.sin(i*Math.PI/6)*36} y={-Math.cos(i*Math.PI/6)*36}>{i===0?12:i}</text>)}</g>
    <g stroke={ink} strokeLinecap="round"><line x1="0" y1="2" x2="0" y2="-26" strokeWidth="4.4" transform={`rotate(${hour%12*30+minute*.5})`}/><line x1="0" y1="2" x2="0" y2="-37.5" strokeWidth="3.2" transform={`rotate(${minute*6+s*.1})`}/></g>
    <circle r="1.8" fill={ink}/>
    {seconds&&<><line x1="0" y1="7" x2="0" y2="-44" stroke={light?'#ff9500':'#ddd'} strokeWidth=".55" strokeLinecap="round" transform={`rotate(${second*6})`}/><circle r="1.8" fill={light?'#ff9500':'#ddd'}/><circle r=".8" fill={light?'#fafafa':'#333'}/></>}
  </svg>;
}
