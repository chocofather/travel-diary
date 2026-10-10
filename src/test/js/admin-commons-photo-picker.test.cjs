// Commons 사진 후보 카드(공용 모듈)와 기존 여행지 이미지 관리 화면의 Commons 추가 흐름.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {FakeEvent, FakeElement, flush} = require('./fake-dom.cjs');
const {togglePhoto, setMain, serializeSelection} =
  require('../../main/resources/static/js/admin-commons-photo-picker.js');

const jsRoot = path.join(__dirname, '../../main/resources/static/js');

test('auto mode (new destination) always keeps exactly one main photo', () => {
  let photos = togglePhoto([], 'A.jpg', true, 'auto');
  photos = togglePhoto(photos, 'B.jpg', true, 'auto');
  assert.deepEqual(photos, [{fileName: 'A.jpg', main: true}, {fileName: 'B.jpg', main: false}]);
  photos = setMain(photos, 'B.jpg', 'auto');
  assert.deepEqual(photos.map(photo => photo.main), [false, true]);
  photos = togglePhoto(photos, 'B.jpg', false, 'auto');
  assert.deepEqual(photos, [{fileName: 'A.jpg', main: true}]);
});

test('explicit mode (existing destination) changes the main photo only when the admin chooses it', () => {
  let photos = togglePhoto([], 'A.jpg', true, 'explicit');
  photos = togglePhoto(photos, 'B.jpg', true, 'explicit');
  assert.deepEqual(photos.map(photo => photo.main), [false, false]);
  photos = setMain(photos, 'B.jpg', 'explicit');
  assert.deepEqual(photos.map(photo => photo.main), [false, true]);
  // 같은 사진을 다시 누르면 대표 지정을 풀고 기존 대표 사진을 유지한다.
  photos = setMain(photos, 'B.jpg', 'explicit');
  assert.deepEqual(photos.map(photo => photo.main), [false, false]);
  assert.equal(serializeSelection('Q243', []), '');
  assert.equal(serializeSelection('Q243', photos),
    '{"qid":"Q243","photos":[{"fileName":"A.jpg","main":false},{"fileName":"B.jpg","main":false}]}');
});

const candidates = {
  qid: 'Q243', status: 'AVAILABLE', category: 'Eiffel Tower',
  photos: ['Old.jpg', 'New A.jpg', 'New B.jpg', 'Public.jpg'].map(fileName => ({
    fileName, source: 'CATEGORY', savable: fileName !== 'Public.jpg',
    saveBlockReason: fileName === 'Public.jpg' ? '미국 기준 퍼블릭 도메인으로만 확인돼 국외 적용 범위가 불명확합니다.' : null,
    reviewReason: fileName === 'Public.jpg' ? '미국 기준 퍼블릭 도메인으로만 확인돼 국외 적용 범위가 불명확합니다.' : null,
    thumbnailUrl: `https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a8/${encodeURIComponent(fileName)}/240px-x.jpg`,
    filePageUrl: `https://commons.wikimedia.org/wiki/File:${encodeURIComponent(fileName)}`,
    width: 3000, height: 2000,
    author: 'Artist', licenseName: 'CC BY-SA 4.0', reuseStatus: fileName === 'Public.jpg' ? 'RIGHTS_UNCLEAR' : 'ELIGIBLE'
  }))
};

function loadImagePage(response = candidates, {qid = 'Q243', query = ''} = {}) {
  const body = new FakeElement('body');
  const root = new FakeElement('section', qid ? {'data-commons-add': '', 'data-qid': qid} : {'data-commons-add': ''});
  const registered = Object.assign(new FakeElement('span', {'data-registered-commons-file': ''}), {textContent: 'Old.jpg'});
  // QID 후보 불러오기 버튼은 QID가 있는 여행지에만 있다. 검색 폼은 늘 있다.
  const load = new FakeElement('button', {'data-commons-add-load': ''});
  const searchForm = new FakeElement('form', {'data-commons-search-form': ''});
  searchForm.append(new FakeElement('input', {'data-commons-search-query': '', value: query}), new FakeElement('button'));
  const message = Object.assign(new FakeElement('p', {'data-commons-add-message': ''}), {hidden: true});
  const area = new FakeElement('div', {'data-commons-add-candidates': ''});
  const form = new FakeElement('form', {'data-commons-add-form': ''});
  const field = new FakeElement('input', {name: 'commonsSelectedPhotosJson', 'data-commons-add-selection': ''});
  const status = Object.assign(new FakeElement('p', {'data-commons-add-status': ''}), {hidden: true});
  const submit = Object.assign(new FakeElement('button', {'data-commons-add-submit': ''}), {disabled: true});
  form.append(field, status, submit);
  if (qid) root.append(registered, load); else root.append(registered);
  root.append(searchForm, message, area, form);
  body.append(root);
  const requests = [];
  let ready = null;
  const document = {
    addEventListener: (type, listener) => { if (type === 'DOMContentLoaded') ready = listener; },
    querySelector: selector => body.querySelector(selector),
    createElement: tag => new FakeElement(tag)
  };
  const fetch = async url => {
    requests.push(url);
    const body = typeof response === 'function' ? response(url) : response;
    return {ok: true, redirected: false, url: 'http://localhost/admin/api', json: async () => body};
  };
  const sandbox = {document, fetch, URL, console, setTimeout, encodeURIComponent};
  sandbox.window = sandbox;
  vm.createContext(sandbox);
  for (const file of ['admin-commons-photo-picker.js', 'admin-destination-commons-add.js']) {
    vm.runInContext(fs.readFileSync(path.join(jsRoot, file), 'utf8'), sandbox, {filename: file});
  }
  ready();
  const card = fileName => area.querySelectorAll('article').find(node => node.text().includes(fileName));
  const checkbox = fileName => card(fileName).querySelectorAll('input')[0];
  const mainButton = fileName => card(fileName).querySelectorAll('button')[0];
  return {root, load, searchForm, message, area, form, field, status, submit, requests, card, checkbox, mainButton};
}

function check(box, checked = true) {
  box.checked = checked;
  box.dispatchEvent(new FakeEvent('change'));
}

test('existing destination: loads candidates for its own QID, marks registered photos and hides blocked ones', async () => {
  const page = loadImagePage();
  page.load.click();
  await flush();

  assert.deepEqual(page.requests, ['/admin/api/wikidata/destinations/commons-photos?qid=Q243']);
  // 저장할 수 없는 라이선스(Public.jpg)는 이미지 관리 화면 결과에서 뺀다.
  assert.equal(page.area.querySelectorAll('article').length, 3);
  assert.equal(page.card('Public.jpg'), undefined);
  assert.equal(page.checkbox('Old.jpg').disabled, true, '이미 등록된 사진은 다시 고를 수 없다');
  assert.match(page.card('Old.jpg').text(), /이미 이 여행지에 등록된 사진/);
  assert.doesNotMatch(page.area.text(), /관리자 확인 필요/);
  assert.match(page.card('New A.jpg').text(), /Artist · CC BY-SA 4.0 저장 가능/);
  assert.match(page.card('New A.jpg').text(), /Commons에서 보기/);
  assert.equal(page.checkbox('New A.jpg').disabled, false);
  assert.equal(page.submit.disabled, true);
});

test('image management search: sends the query, serializes a SEARCH selection without QID and hides small photos', async () => {
  const photo = (fileName, size = 3000) => ({...candidates.photos[1], fileName, width: size, height: size});
  const results = {qid: null, status: 'AVAILABLE', selectionLimit: 5, nextCursor: null,
    photos: [photo('Petronas.jpg'), photo('Tiny.jpg', 120)]};
  const page = loadImagePage(results, {qid: '', query: 'Petronas Twin Towers Kuala Lumpur'});
  const submitted = new FakeEvent('submit');
  page.searchForm.dispatchEvent(submitted);
  await flush();

  assert.equal(submitted.defaultPrevented, true, '검색은 화면 안에서만 한다');
  assert.deepEqual(page.requests,
    [`/admin/api/wikidata/destinations/commons-search?query=${encodeURIComponent('Petronas Twin Towers Kuala Lumpur')}`]);
  assert.equal(page.area.querySelectorAll('article').length, 1);
  check(page.checkbox('Petronas.jpg'));
  assert.deepEqual(JSON.parse(page.field.value), {source: 'SEARCH', photos: [{fileName: 'Petronas.jpg', main: false}]});
});

test('existing destination: several photos are sent together and the main photo changes only when chosen', async () => {
  const page = loadImagePage();
  page.load.click();
  await flush();
  check(page.checkbox('New A.jpg'));
  check(page.checkbox('New B.jpg'));

  assert.equal(page.submit.disabled, false);
  assert.deepEqual(JSON.parse(page.field.value), {qid: 'Q243', photos: [
    {fileName: 'New A.jpg', main: false}, {fileName: 'New B.jpg', main: false}]});
  assert.match(page.area.text(), /대표: 기존 대표 사진 유지/);

  page.mainButton('New B.jpg').click();
  assert.deepEqual(JSON.parse(page.field.value).photos.map(photo => photo.main), [false, true]);
  page.mainButton('New B.jpg').click();
  assert.deepEqual(JSON.parse(page.field.value).photos.map(photo => photo.main), [false, false]);
  // URL·저작자·라이선스는 보내지 않는다.
  assert.doesNotMatch(page.field.value, /thumb|Artist|CC BY/);

  const submitted = new FakeEvent('submit');
  page.form.dispatchEvent(submitted);
  await flush();
  assert.equal(submitted.defaultPrevented, false);
  assert.equal(page.submit.disabled, true);
  assert.equal(page.submit.textContent, '추가 중…');
  assert.match(page.status.textContent, /추가하는 중입니다/);
  const again = new FakeEvent('submit');
  page.form.dispatchEvent(again);
  assert.equal(again.defaultPrevented, true, '진행 중에는 다시 제출하지 않는다');
});

test('show more appends the next page once, without duplicates, keeping selection, main photo and the limit', async () => {
  const photo = fileName => ({...candidates.photos[1], fileName});
  const firstPage = {qid: 'Q243', status: 'AVAILABLE', selectionLimit: 2, nextCursor: '16:file|ab|1',
    photos: [photo('Old.jpg'), photo('New A.jpg')]};
  const secondPage = {qid: 'Q243', status: 'AVAILABLE', selectionLimit: 2, nextCursor: null,
    photos: [photo('New A.jpg'), photo('New C.jpg'), photo('New D.jpg')]};
  const page = loadImagePage(url => (url.includes('cursor=') ? secondPage : firstPage));
  page.load.click();
  await flush();
  check(page.checkbox('New A.jpg'));
  page.mainButton('New A.jpg').click();
  const more = () => page.area.querySelectorAll('button').find(button => button.textContent === '사진 더 보기');

  more().click();
  await flush();
  assert.deepEqual(page.requests.slice(1),
    [`/admin/api/wikidata/destinations/commons-photos?qid=Q243&cursor=${encodeURIComponent('16:file|ab|1')}`]);
  assert.deepEqual(page.area.querySelectorAll('article').map(card => card.querySelectorAll('strong')[0].textContent),
    ['Old.jpg', 'New A.jpg', 'New C.jpg', 'New D.jpg'], '이미 받은 사진은 다시 붙이지 않는다');
  assert.equal(page.checkbox('New A.jpg').checked, true);
  assert.equal(page.checkbox('Old.jpg').disabled, true, '이미 등록된 사진 표시는 그대로다');
  assert.deepEqual(JSON.parse(page.field.value).photos, [{fileName: 'New A.jpg', main: true}]);
  assert.equal(more().hidden, true, '더 볼 사진이 없으면 버튼을 숨긴다');

  check(page.checkbox('New C.jpg'));
  assert.equal(page.checkbox('New D.jpg').disabled, true, '선택 한도에 닿으면 나머지는 고를 수 없다');
  assert.match(page.area.text(), /추가할 사진 2\/2장 · 대표: New A.jpg · 최대 2장/);
  assert.equal(page.mainButton('New D.jpg').hidden, true, '고르지 않은 사진은 대표로 지정할 수 없다');
  more().click();
  await flush();
  assert.equal(page.requests.length, 2, '다음 위치가 없으면 다시 요청하지 않는다');
});

test('existing destination: empty selection or a response for another QID is not submitted', async () => {
  const page = loadImagePage({...candidates, qid: 'Q90'});
  const empty = new FakeEvent('submit');
  page.form.dispatchEvent(empty);
  assert.equal(empty.defaultPrevented, true);

  page.load.click();
  await flush();
  assert.equal(page.area.querySelectorAll('article').length, 0);
  assert.match(page.message.textContent, /다른 응답/);
  assert.equal(page.field.value, '');
});
