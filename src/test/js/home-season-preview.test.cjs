// 메인 계절 hero 미리보기: localhost 에서만 ?season= 을 받고, 그 밖에는 월별 판별(null)을 쓴다.
const test = require('node:test');
const assert = require('node:assert/strict');
const {previewSeasonKey} =
  require('../../main/resources/static/js/home-season-preview.js');

test('localhost accepts the four preview seasons', () => {
  assert.equal(previewSeasonKey('localhost', '?season=spring'), 'SPRING');
  assert.equal(previewSeasonKey('localhost', '?season=summer'), 'SUMMER');
  assert.equal(previewSeasonKey('localhost', '?season=autumn'), 'FALL');
  assert.equal(previewSeasonKey('localhost', '?season=winter'), 'WINTER');
  assert.equal(previewSeasonKey('127.0.0.1', '?season=winter'), 'WINTER');
  assert.equal(previewSeasonKey('[::1]', '?season=winter'), 'WINTER');
});

test('a production host ignores the season parameter', () => {
  assert.equal(previewSeasonKey('tripbora.com', '?season=winter'), null);
  assert.equal(previewSeasonKey('www.tripbora.com', '?season=spring'), null);
  // localhost 로 시작하기만 하는 다른 호스트도 받지 않는다
  assert.equal(previewSeasonKey('localhost.example.com', '?season=summer'), null);
});

test('missing or unknown values fall back to the monthly season', () => {
  assert.equal(previewSeasonKey('localhost', ''), null);
  assert.equal(previewSeasonKey('localhost', '?season='), null);
  assert.equal(previewSeasonKey('localhost', '?season=fall'), null);
  assert.equal(previewSeasonKey('localhost', '?season=SPRING'), null);
  assert.equal(previewSeasonKey('localhost', '?season=constructor'), null);
  assert.equal(previewSeasonKey('localhost', '?other=spring'), null);
});
