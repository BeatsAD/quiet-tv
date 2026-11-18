import type { Value } from './api';

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六'];
const WHITE = '#f7f7f7', GREY = '#898b8f', RED = '#ff453d', ORANGE = '#ff9d16';

export type SaverClockTime = { year: number; month: number; day: number; hour: number; minute: number; second: number };

export function saverClockLayout(values: Record<string, Value>) {
  const digital = values.saverContent === 'clock';
  const analog = digital && values.saverAnalogClock === true;
  const largeDate = digital && !analog && values.saverDigitalLayout === 'large-date';
  const calendar = digital && values.saverCalendar === true;
  return {
    digital, analog, largeDate, calendar,
    square: values.saverAnalogStyle === 'square',
    composed: analog || largeDate || calendar,
    lunar: (calendar || largeDate) && values.saverLunarDate === true,
    seconds: digital && !largeDate && values.saverSeconds === true,
  };
}

function Dial({ time, square, showSeconds }: { time: SaverClockTime; square: boolean; showSeconds: boolean }) {
  const second = showSeconds ? time.second : 0;
  const minute = time.minute + second / 60;
  const hand = (angle: number, reach: number, base: number, width: number) => (
    <g transform={`rotate(${angle})`} fill={WHITE}>
      <line x1="0" x2="0" y1="1" y2={-base} stroke={WHITE} strokeWidth="4.6" strokeLinecap="round"/>
      <polygon points={`${-width * .38},${-base} ${-width / 2},${-reach} ${width / 2},${-reach} ${width * .38},${-base}`}/>
      <circle cy={-reach} r={width / 2}/>
      <circle cy={-base} r={width * .38}/>
    </g>
  );
  return <>
    {Array.from({ length: 60 }, (_, mark) => {
      const angle = mark * Math.PI / 30, x = Math.sin(angle), y = -Math.cos(angle);
      const edge = square ? 181.5 / Math.max(Math.abs(x), Math.abs(y)) : 181.5;
      const hourMark = mark % 5 === 0, cardinal = mark % 15 === 0;
      const length = square ? cardinal ? 37 : hourMark ? 60.5 : 18 : hourMark ? 13 : 11.5;
      const labelRadius = square ? 113 : 140;
      return <g key={mark}>
        <line x1={x * (edge - length)} y1={y * (edge - length)} x2={x * edge} y2={y * edge}
          stroke={hourMark ? WHITE : '#5e6063'} strokeWidth={hourMark ? 3.7 : 3.5} strokeLinecap="round"/>
        {hourMark && (!square || cardinal) &&
          <text x={x * labelRadius} y={y * labelRadius} fontSize={square ? 53 : 43} fontWeight="500">{mark / 5 || 12}</text>}
      </g>;
    })}
    {hand(time.hour % 12 * 30 + minute / 2, square ? 104 : 98, 26, square ? 12.4 : 11.8)}
    {hand(minute * 6, square ? 181 : 169, square ? 35 : 33, square ? 12.4 : 10.4)}
    <circle r="11" fill={WHITE}/>
    {showSeconds ? <>
      <line y1="29" y2="-183" stroke={ORANGE} strokeWidth="2.3" strokeLinecap="round" transform={`rotate(${second * 6})`}/>
      <circle r="7.2" fill={ORANGE}/><circle r="3.7" fill="#000"/>
    </> : <circle r="4.2" fill="#3c3e41"/>}
  </>;
}

function civilMonth(time: SaverClockTime) {
  const date = new Date(0);
  date.setUTCFullYear(time.year, time.month - 1, 1);
  const firstColumn = date.getUTCDay();
  date.setUTCMonth(time.month, 0);
  return { firstColumn, days: date.getUTCDate() };
}

function MonthGrid({ time, lunar, monthStarts }: { time: SaverClockTime; lunar: string; monthStarts: number[] }) {
  const { firstColumn, days } = civilMonth(time);
  const step = firstColumn + days > 35 ? 43.5 : 53.5;
  const divider = time.month < 10 ? 582 : 596;
  return <>
    <text x="528" y="111" fill={RED} fontSize="25.5" fontWeight="640" textAnchor="start">{time.month}月</text>
    {lunar && <>
      <line x1={divider} x2={divider} y1="99.5" y2="122.5" stroke="#35383b" strokeWidth=".65"/>
      <text x={divider + 10} y="111" fill={GREY} fontSize="23" textAnchor="start">{lunar}</text>
    </>}
    {WEEKDAYS.map((day, i) => <text key={day} x={543 + i * 50} y="161" fill={i === 0 || i === 6 ? GREY : WHITE} fontSize="22.5">{day}</text>)}
    {Array.from({ length: days }, (_, i) => {
      const day = i + 1, index = i + firstColumn, column = index % 7;
      const x = 543 + column * 50, y = 213.5 + Math.floor(index / 7) * step;
      const today = day === time.day;
      return <g key={day}>
        {today ? <circle cx={x} cy={y} r="22" fill={RED}/> : monthStarts.includes(day) &&
          <line x1={x - 13.5} x2={x + 13.5} y1={y + 17} y2={y + 17} stroke={RED} strokeWidth="2"/>}
        <text x={x} y={y} fill={!today && (column === 0 || column === 6) ? GREY : WHITE} fontSize="25" fontWeight="500">{day}</text>
      </g>;
    })}
  </>;
}

function LargeDate({ time, lunar }: { time: SaverClockTime; lunar: string }) {
  const { firstColumn } = civilMonth(time);
  const day = String(time.day);
  const monthWidth = time.month < 10 ? 69 : 94;
  const titleLeft = -(89 + 13.5 + monthWidth) / 2;
  const lunarStep = Math.min(41, 164 / lunar.length);
  const digitCenter = lunar ? -24.5 : 0;
  const numberWidth = day.length * 109;
  return <>
    <text x={titleLeft} y="145" textAnchor="start" fill={RED} fontSize="43.5" fontWeight="650">
      周{WEEKDAYS[(firstColumn + time.day - 1) % 7]}
    </text>
    <text x={titleLeft + 102.5} y="145" textAnchor="start" fill={GREY} fontSize="43.5" fontWeight="650">{time.month}月</text>
    <text x={digitCenter} y="309" fontSize="211" fontWeight="650" textLength={numberWidth} lengthAdjust="spacingAndGlyphs">{day}</text>
    {[...lunar].map((character, i) => <text key={i} x={digitCenter + numberWidth / 2 + 31}
      y={309 + (i - (lunar.length - 1) / 2) * lunarStep} fontSize={Math.min(34.5, lunarStep * .88)} fontWeight="620">{character}</text>)}
  </>;
}

export function SaverClock({ time, timeLabel, layout, lunar, monthStarts }: {
  time: SaverClockTime; timeLabel: string; layout: ReturnType<typeof saverClockLayout>; lunar: string; monthStarts: number[];
}) {
  const center = layout.calendar ? 277 : 480;
  const caption = layout.largeDate ? `${time.month}月${time.day}日` : timeLabel;
  return <svg className="saver-clock-scene" viewBox="0 0 960 540" role="img" aria-label={lunar ? `${caption} ${lunar}` : caption}
    fill={WHITE} textAnchor="middle" dominantBaseline="central">
    {layout.analog ? <g transform={`translate(${center} 270)`}><Dial time={time} square={layout.square} showSeconds={layout.seconds}/></g> :
      layout.largeDate ? <g transform={`translate(${center} 0)`}><LargeDate time={time} lunar={lunar}/></g> :
        <text x={center} y="270" fontSize="83" fontWeight="300">{timeLabel}</text>}
    {layout.calendar && <MonthGrid time={time} lunar={lunar} monthStarts={monthStarts}/>}
  </svg>;
}
