// 여행지 카테고리: 선택한 것 중 하나가 대표. 규칙은 저장 서비스(DestinationService.resolveMainCategoryId)와 같다.
const test = require('node:test');
const assert = require('node:assert/strict');
const {nextMainCategoryId} =
  require('../../main/resources/static/js/admin-destination-category-select.js');

test('the first selected category becomes the main one', () => {
  assert.equal(nextMainCategoryId(['12'], ''), '12');
});

test('selecting more categories or choosing another main keeps the chosen main', () => {
  assert.equal(nextMainCategoryId(['12', '3', '40'], '12'), '12');
  // '대표로' 버튼으로 40 을 고른 뒤
  assert.equal(nextMainCategoryId(['12', '3', '40'], '40'), '40');
});

test('removing the main category makes the smallest remaining id the main, numerically', () => {
  assert.equal(nextMainCategoryId(['12', '40', '9'], '3'), '9');
});

test('clearing every category leaves no main category', () => {
  assert.equal(nextMainCategoryId([], '12'), '');
});

test('a stored main category is kept on the edit form, and missing one falls back to the smallest id', () => {
  assert.equal(nextMainCategoryId(['3', '5', '8'], '5'), '5');
  assert.equal(nextMainCategoryId(['8', '3', '5'], ''), '3');
});
