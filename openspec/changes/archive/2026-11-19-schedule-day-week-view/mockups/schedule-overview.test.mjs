import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

test('переключение недели в день выбирает сегодня или понедельник и сохраняет фильтры', () => {
  const html = readFileSync(new URL('./schedule-overview.html', import.meta.url), 'utf8');
  const script = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
  assert.ok(script);
  const handlers = {};
  const app = { innerHTML: '' };
  const context = vm.createContext({
    document: {
      getElementById: () => app,
      addEventListener: (name, handler) => { handlers[name] = handler; },
    },
    matchMedia: () => ({ matches: false }),
    setTimeout: () => 0,
    clearTimeout: () => {},
  });
  vm.runInContext(script, context);
  const click = (act, value) => handlers.click({
    target: { closest: selector => selector === 'a' ? null : { dataset: { act, v: String(value) } } },
    preventDefault() {},
  });
  const selectedDate = () => vm.runInContext('addDays(BASE_MON, 7 * state.w + state.day)', context);

  click('fromgroup');
  click('step', 1);
  click('view', 'day');
  assert.equal(selectedDate(), '2026-11-23');
  assert.equal(vm.runInContext('state.view', context), 'day');
  assert.equal(vm.runInContext('state.f.group', context), 'Младшая группа');

  click('day', 5);
  click('view', 'day');
  assert.equal(selectedDate(), '2026-11-28');
  click('view', 'week');
  assert.equal(selectedDate(), '2026-11-28');
  click('step', -2);
  click('view', 'day');
  assert.equal(selectedDate(), '2026-11-09');

  click('today');
  click('day', 5);
  click('view', 'week');
  click('view', 'day');
  assert.equal(selectedDate(), '2026-11-17');
});
