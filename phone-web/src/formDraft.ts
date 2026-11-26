import { useState } from 'react';

export type FormDrafts = Map<string, unknown>;

export function useFormDraft<T>(drafts: FormDrafts, key: string, initial: () => T) {
  const [value, setValue] = useState(() => (drafts.get(key) as T | undefined) ?? initial());
  function update(next: T) {
    drafts.set(key, next);
    setValue(next);
  }
  return {value, update, clear: () => drafts.delete(key)};
}
