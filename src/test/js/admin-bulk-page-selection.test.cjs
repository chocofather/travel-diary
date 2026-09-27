// 국내·해외 일괄 등록 공통 '현재 페이지 선택' 상태.
const test = require('node:test');
const assert = require('node:assert/strict');
const {pageSelection, applyPageSelection} =
  require('../../main/resources/static/js/admin-bulk-page-selection.js');

const page = [{id: 'A'}, {id: 'B', registered: true}, {id: 'C'}, {id: 'D', locked: true}];
const selectable = item => !item.registered && !item.locked;

test('only selectable items on the current page count; selections from other pages do not', () => {
  // X 는 다른 페이지에서 고른 항목이다.
  const chosen = new Set(['X', 'A']);
  const some = pageSelection(page, selectable, item => chosen.has(item.id));
  assert.deepEqual(some.targets.map(item => item.id), ['A', 'C']);
  assert.deepEqual([some.selectable, some.selected, some.state], [2, 1, 'some']);

  chosen.add('C');
  assert.equal(pageSelection(page, selectable, item => chosen.has(item.id)).state, 'all');
  assert.equal(pageSelection(page, selectable, () => false).state, 'none');
  // 고를 수 있는 후보가 없는 페이지(등록완료만 보이는 필터 등)
  assert.equal(pageSelection([page[1], page[3]], selectable, () => true).state, 'none');
});

test('the header checkbox shows checked, indeterminate and disabled states', () => {
  const controls = () => ({toggle: {}, selectButton: {}, clearButton: {}, count: {}});

  const some = controls();
  applyPageSelection({selectable: 2, selected: 1, state: 'some'}, some);
  assert.deepEqual([some.toggle.checked, some.toggle.indeterminate, some.toggle.disabled], [false, true, false]);
  assert.deepEqual([some.selectButton.disabled, some.clearButton.disabled], [false, false]);
  assert.equal(some.count.textContent, '현재 페이지 선택 가능 2건 중 1건 선택');

  const all = controls();
  applyPageSelection({selectable: 2, selected: 2, state: 'all'}, all);
  assert.deepEqual([all.toggle.checked, all.toggle.indeterminate, all.selectButton.disabled], [true, false, true]);

  const empty = controls();
  applyPageSelection({selectable: 0, selected: 0, state: 'none'}, empty);
  assert.deepEqual([empty.toggle.disabled, empty.selectButton.disabled, empty.clearButton.disabled], [true, true, true]);
});
