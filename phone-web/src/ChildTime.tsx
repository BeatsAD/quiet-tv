import { ChoiceField, Group } from './widgets';
import type { Value } from './api';

const modes = ['不限制', '单次限时', '循环限时'].map((title, value) => ({title, value}));
const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'].map((title, index) => ({title, value: 1 << index}));
const schedules = [{title: '每天', value: 127}, {title: '工作日', value: 31}, {title: '周末', value: 96}];

export function childTimeRule(rule?: Record<string, Value>) {
  return {mode: Number(rule?.mode ?? 0), minutes: Number(rule?.minutes ?? 60), repeatDays: Number(rule?.repeatDays ?? 127)};
}

type Rule = ReturnType<typeof childTimeRule>;
export type ChildTimeDraft = {base: Rule; mode: number; hours: string; minutes: string; repeatDays: number};

export function childTimeDraft(rule: Rule): ChildTimeDraft {
  return {base: rule, mode: rule.mode, hours: String(Math.floor(rule.minutes / 60)), minutes: String(rule.minutes % 60), repeatDays: rule.repeatDays};
}

export function ChildTime({draft, change, current, blocked, authorized, save, cancel}: {
  draft: ChildTimeDraft; change: (draft: ChildTimeDraft) => void; current: Rule; blocked: boolean;
  authorized: boolean; save: (rule: Rule) => void; cancel: () => void;
}) {
  const {base, mode, hours, minutes, repeatDays} = draft;
  const total = Number(hours) * 60 + Number(minutes);
  const validTime = /^\d+$/.test(hours) && /^\d+$/.test(minutes) && Number(minutes) < 60 && total >= 1 && total <= 1440;
  const conflict = current.mode !== base.mode || current.minutes !== base.minutes || current.repeatDays !== base.repeatDays;
  return <form onSubmit={event => {
    event.preventDefault();
    if (blocked || conflict || (mode > 0 && !validTime)) return;
    save({mode, minutes: validTime ? total : base.minutes, repeatDays});
  }}>
    <Group><ChoiceField title="使用方式" options={modes} value={mode} disabled={blocked}
      onChange={value => change({...draft, mode: Number(value)})}/></Group>
    {mode > 0 && <Group>
      <div className="inline-number-form">
        <label>小时<input type="number" inputMode="numeric" min={0} max={24} step={1} required value={hours}
          disabled={blocked} onChange={event => change({...draft, hours: event.target.value})}/></label>
        <label>分钟<input type="number" inputMode="numeric" min={0} max={59} step={1} required value={minutes}
          disabled={blocked} onChange={event => change({...draft, minutes: event.target.value})}/></label>
      </div>
      {!validTime && <p className="section-note">使用时长需在 1 分钟至 24 小时之间。</p>}
    </Group>}
    {mode === 2 && <Group>
      <ChoiceField title="常用重复日期" options={schedules} value={repeatDays} disabled={blocked}
        onChange={value => change({...draft, repeatDays: Number(value)})}/>
      <ChoiceField title="重复日期" options={weekdays} value={weekdays.filter(day => repeatDays & day.value).map(day => day.value)}
        multiple disabled={blocked} hint="至少选择一天" onChange={value => {
          const days = repeatDays ^ Number(value);
          if (days) change({...draft, repeatDays: days});
        }}/>
    </Group>}
    {conflict && <p className="form-error" role="alert">电视上的规则已变化。请取消后重新打开，读取当前规则。</p>}
    <div className="confirmation-dialog-actions">
      <button type="button" disabled={blocked} onClick={cancel}>取消</button>
      <button className="confirm-button" disabled={blocked || conflict || (mode > 0 && !validTime)}>
        {authorized ? '保存' : '验证家长密码'}
      </button>
    </div>
  </form>;
}
