// admin-wikidata-preview.js 를 작은 가짜 DOM 위에서 실제로 실행해 검색·후보 선택 흐름을 확인한다.
// (jsdom 없이 이 스크립트가 쓰는 DOM 기능만 흉내 낸다.)
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const jsRoot = path.join(__dirname, '../../main/resources/static/js');

const {FakeEvent, FakeElement, flush} = require('./fake-dom.cjs');

function buildPage() {
  const body = new FakeElement('body');
  const root = new FakeElement('section', {'data-wikidata-preview': ''});
  const keyword = new FakeElement('input', {'data-wikidata-keyword': ''});
  const searchButton = new FakeElement('button', {'data-wikidata-search': ''});
  root.append(keyword, searchButton,
    new FakeElement('p', {'data-wikidata-status': ''}),
    new FakeElement('div', {'data-wikidata-results': ''}),
    new FakeElement('div', {'data-wikidata-switch-review': ''}),
    new FakeElement('p', {'data-wikidata-stage': 'basic'}),
    new FakeElement('p', {'data-wikidata-stage': 'wikipedia'}),
    new FakeElement('p', {'data-wikidata-stage': 'commons'}),
    new FakeElement('div', {'data-wikidata-detail': ''}));
  const form = new FakeElement('form', {'data-translation-collapsible': ''});
  form.append(new FakeElement('input', {name: 'wikidataQid', 'data-wikidata-qid': ''}));
  ['ko', 'en', 'ja', 'zh-CN', 'zh-TW'].forEach((code, index) => {
    form.append(new FakeElement('input', {name: `wikipediaRevisionIds[${index}]`, 'data-wikipedia-revision': code}));
  });
  form.append(new FakeElement('input', {name: 'commonsSelectedPhotosJson', 'data-commons-selection': ''}));
  for (let index = 0; index < 5; index++) {
    for (const field of ['name', 'shortDescription', 'description']) {
      form.append(new FakeElement('textarea', {name: `translations[${index}].${field}`}));
    }
  }
  for (const name of ['latitude', 'longitude', 'type', 'season']) form.append(new FakeElement('input', {name}));
  for (const name of ['attractionInfo.homepageUrl', 'attractionInfo.contactNumber', 'restaurantInfo.contactNumber']) {
    form.append(new FakeElement('input', {name}));
  }
  const region = new FakeElement('div', {'data-region-field': ''});
  region.append(new FakeElement('input', {id: 'regionIdHidden'}));
  const submitButton = Object.assign(new FakeElement('button', {type: 'submit', 'data-registration-submit': ''}),
    {textContent: '등록'});
  const submitStatus = Object.assign(new FakeElement('p', {'data-registration-status': ''}), {hidden: true});
  form.append(region,
    new FakeElement('section', {'data-wikidata-wikipedia-area': ''}),
    new FakeElement('section', {'data-wikidata-commons-area': ''}),
    submitStatus, submitButton);
  body.append(root, form);
  return {body, root, form, keyword, searchButton};
}

function deferred() {
  let resolve;
  const promise = new Promise(done => { resolve = done; });
  return {promise, resolve};
}

/** 경로별 응답을 테스트가 원하는 순서로 풀어 주는 가짜 서버. */
function createServer() {
  const calls = [];
  const waiting = new Map();
  function respond(kind, status, body) {
    const queue = waiting.get(kind) || [];
    assert.ok(queue.length, `no pending ${kind} request`);
    queue.shift().resolve({status, body});
  }
  async function fetch(url) {
    const parsed = new URL(url, 'http://localhost');
    const kind = parsed.pathname.split('/').pop();
    calls.push(`${kind}?${parsed.searchParams}`);
    const gate = deferred();
    if (!waiting.has(kind)) waiting.set(kind, []);
    waiting.get(kind).push(gate);
    const {status, body} = await gate.promise;
    return {ok: status < 400, status, redirected: false, url: `http://localhost${parsed.pathname}`,
      json: async () => body};
  }
  return {calls, fetch, respond, pending: kind => (waiting.get(kind) || []).length};
}

function loadPage() {
  const page = buildPage();
  const server = createServer();
  let ready = null;
  const document = {
    addEventListener: (type, listener) => { if (type === 'DOMContentLoaded') ready = listener; },
    querySelector: selector => page.body.querySelector(selector),
    createElement: tag => new FakeElement(tag)
  };
  const sandbox = {document, fetch: server.fetch, URL, URLSearchParams, AbortController, Event: FakeEvent,
    console, setTimeout};
  sandbox.window = sandbox;
  vm.createContext(sandbox);
  for (const file of ['admin-wikidata-form-apply.js', 'admin-wikipedia-description-apply.js',
    'admin-commons-photo-picker.js', 'admin-wikidata-preview.js']) {
    vm.runInContext(fs.readFileSync(path.join(jsRoot, file), 'utf8'), sandbox, {filename: file});
  }
  ready();
  return {...page, server, field: name => page.form.elements.namedItem(name)};
}

const previewBody = {
  qid: 'Q243', names: {ko: '에펠탑', en: 'Eiffel Tower'}, shortDescriptions: {ko: '파리의 탑'},
  latitude: 48.8583, longitude: 2.2944, country: '프랑스', countryQid: 'Q142', regionPath: ['파리'],
  regionMatch: {matched: false, message: '직접 확인해 주세요.'}
};
const wikipediaBody = {
  qid: 'Q243', languages: ['ko', 'en'].map((language, index) => ({
    language, status: 'AVAILABLE', title: `title-${language}`, description: `${language} 설명 원문`,
    sourceUrl: `https://${language}.wikipedia.org/wiki/Eiffel`, sourceLanguage: language, variant: null,
    licenseName: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0/', revisionId: 100 + index
  }))
};
const commonsBody = {qid: 'Q243', status: 'NO_PHOTOS', photos: []};

async function search(page, quick, details) {
  page.keyword.value = '에펠탑';
  page.searchButton.click();
  await flush();
  page.server.respond('search', 200, quick);
  await flush();
  if (details) {
    page.server.respond('search-details', 200, details);
    await flush();
  }
}

function candidateButton(page, qid) {
  return page.root.querySelectorAll('button').find(button =>
    String(button.getAttribute('aria-label')).includes(`(${qid})`));
}

test('search shows minimal results before details and then drops non-place candidates', async () => {
  const page = loadPage();
  await search(page, [
    {qid: 'Q243', name: '에펠탑', shortDescription: '파리의 건축물'},
    {qid: 'Q3821251', name: '에펠탑', shortDescription: '조르주 쇠라의 그림'}
  ]);
  const results = page.root.querySelector('[data-wikidata-results]');
  assert.equal(results.children.length, 2);
  assert.match(results.text(), /국가·지역 확인 중/);
  assert.deepEqual(page.server.calls.map(call => call.split('?')[0]), ['search', 'search-details']);
  assert.equal(page.server.calls[1], 'search-details?qids=Q243&qids=Q3821251');

  page.server.respond('search-details', 200, [{qid: 'Q243', name: '에펠탑', country: '프랑스', region: '파리'}]);
  await flush();
  assert.equal(results.children.length, 1);
  assert.match(results.text(), /프랑스 · 파리/);
});

test('Wikipedia is applied as soon as it arrives without waiting for basic info', async () => {
  const page = loadPage();
  await search(page, [{qid: 'Q243', name: '에펠탑'}], [{qid: 'Q243', country: '프랑스'}]);
  page.field('translations[1].description').value = '관리자가 쓴 영어 설명';
  candidateButton(page, 'Q243').click();
  await flush();
  assert.deepEqual(['preview', 'wikipedia', 'commons-photos'].map(page.server.pending), [1, 1, 1]);

  page.server.respond('wikipedia', 200, wikipediaBody);
  await flush();
  assert.equal(page.field('translations[0].description').value, 'ko 설명 원문');
  assert.equal(page.field('wikipediaRevisionIds[0]').value, '100');
  // 관리자가 이미 쓴 칸은 덮어쓰지 않고 출처도 연결하지 않는다.
  assert.equal(page.field('translations[1].description').value, '관리자가 쓴 영어 설명');
  assert.equal(page.field('wikipediaRevisionIds[1]').value, '');
  assert.equal(page.field('wikidataQid').value, '', 'QID는 기본정보 확인 전까지 비워 둔다');

  page.server.respond('preview', 200, previewBody);
  await flush();
  assert.equal(page.field('wikidataQid').value, 'Q243');
  assert.equal(page.field('translations[0].name').value, '에펠탑');
  assert.equal(page.field('latitude').value, '48.8583');
  page.server.respond('commons-photos', 200, commonsBody);
  await flush();
});

test('clearing a Wikipedia description unlinks its revision so registration is not blocked', async () => {
  const page = loadPage();
  await search(page, [{qid: 'Q243', name: '에펠탑'}], [{qid: 'Q243'}]);
  candidateButton(page, 'Q243').click();
  await flush();
  page.server.respond('wikipedia', 200, wikipediaBody);
  page.server.respond('preview', 200, previewBody);
  page.server.respond('commons-photos', 200, commonsBody);
  await flush();
  const description = page.field('translations[1].description');
  assert.equal(page.field('wikipediaRevisionIds[1]').value, '101');

  description.value = '';
  page.form.dispatchEvent(Object.assign(new FakeEvent('input'), {target: description}));
  assert.equal(page.field('wikipediaRevisionIds[1]').value, '', '빈 설명은 출처 없이 등록한다');
  assert.equal(page.field('wikipediaRevisionIds[0]').value, '100', '다른 언어 연결은 그대로 둔다');

  // 다시 입력하면 같은 판본의 관리자 수정본으로 연결한다.
  description.value = '관리자가 다시 쓴 설명';
  page.form.dispatchEvent(Object.assign(new FakeEvent('input'), {target: description}));
  assert.equal(page.field('wikipediaRevisionIds[1]').value, '101');
});

test('basic info failure withdraws untouched Wikipedia autofill and its revision links', async () => {
  const page = loadPage();
  await search(page, [{qid: 'Q243', name: '에펠탑'}], [{qid: 'Q243'}]);
  candidateButton(page, 'Q243').click();
  await flush();
  page.server.respond('wikipedia', 200, wikipediaBody);
  await flush();
  page.field('translations[1].description').value = '관리자가 고친 설명';
  page.form.dispatchEvent(Object.assign(new FakeEvent('input'), {target: page.field('translations[1].description')}));

  page.server.respond('preview', 502, {message: 'Wikidata 정보를 불러오지 못했습니다.'});
  await flush();
  assert.equal(page.field('translations[0].description').value, '');
  assert.equal(page.field('translations[1].description').value, '관리자가 고친 설명');
  assert.equal(page.field('wikipediaRevisionIds[0]').value, '');
  assert.equal(page.field('wikipediaRevisionIds[1]').value, '');
  assert.equal(page.field('wikidataQid').value, '');
  page.server.respond('commons-photos', 200, commonsBody);
  await flush();
});

test('switching QID clears the previous candidate at once and ignores its late responses', async () => {
  const page = loadPage();
  await search(page, [{qid: 'Q243', name: '에펠탑'}, {qid: 'Q90', name: '파리'}],
    [{qid: 'Q243'}, {qid: 'Q90'}]);
  candidateButton(page, 'Q243').click();
  await flush();
  page.server.respond('preview', 200, previewBody);
  page.server.respond('wikipedia', 200, wikipediaBody);
  await flush();
  assert.equal(page.field('translations[0].name').value, '에펠탑');

  candidateButton(page, 'Q90').click();
  await flush();
  // 새 후보 응답이 오기 전에 이전 후보의 자동입력·판본·QID가 모두 정리된다.
  assert.equal(page.field('translations[0].name').value, '');
  assert.equal(page.field('translations[0].description').value, '');
  assert.equal(page.field('wikipediaRevisionIds[0]').value, '');
  assert.equal(page.field('wikidataQid').value, '');

  // 늦게 도착한 이전 후보(Q243)의 Commons 응답은 새 후보 화면에 섞이지 않는다.
  page.server.respond('commons-photos', 200, {...commonsBody, status: 'AVAILABLE', photos: [{fileName: 'Old.jpg'}]});
  await flush();
  assert.doesNotMatch(page.form.querySelector('[data-wikidata-commons-area]').text(), /Old\.jpg/);
});

// 실제 Q243 응답 형태: Wikidata names 에 zh-TW 가 없고, zhwiki 의 zh-tw 변형 제목은 艾菲爾鐵塔 이다.
const q243Preview = {...previewBody, names: {ko: '에펠탑', en: 'Eiffel Tower', ja: 'エッフェル塔', 'zh-CN': '埃菲尔铁塔'}};
const q243Wikipedia = {qid: 'Q243', languages: [
  ['zh-CN', 'zh-cn', '埃菲尔铁塔', '埃菲尔铁塔（法语：Tour Eiffel）'],
  ['zh-TW', 'zh-tw', '艾菲爾鐵塔', '艾菲爾鐵塔（法語：Tour Eiffel）']
].map(([language, variant, displayTitle, description], index) => ({
  language, status: 'AVAILABLE', title: '艾菲爾鐵塔', displayTitle, description,
  sourceUrl: `https://zh.wikipedia.org/wiki/%E8%89%BE?variant=${variant}`, sourceLanguage: 'zh', variant,
  licenseName: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0/', revisionId: 200 + index
}))};

for (const order of [['wikipedia', 'preview'], ['preview', 'wikipedia']]) {
  test(`Q243 traditional Chinese title is filled from the Wikipedia zh-tw title (${order.join(' then ')})`, async () => {
    const page = loadPage();
    await search(page, [{qid: 'Q243', name: '에펠탑'}], [{qid: 'Q243'}]);
    candidateButton(page, 'Q243').click();
    await flush();
    for (const kind of order) {
      page.server.respond(kind, 200, kind === 'preview' ? q243Preview : q243Wikipedia);
      await flush();
    }
    assert.equal(page.field('translations[3].name').value, '埃菲尔铁塔');
    assert.equal(page.field('translations[4].name').value, '艾菲爾鐵塔');
    assert.equal(page.field('translations[4].description').value, '艾菲爾鐵塔（法語：Tour Eiffel）');
    assert.equal(page.field('wikipediaRevisionIds[4]').value, '201');
    page.server.respond('commons-photos', 200, commonsBody);
    await flush();
  });
}

// 실제 Q9188 응답 형태: 간체 설명은 zh-cn, 번체 설명은 표기를 확인한 zh 원문이다.
const q9188Preview = {...previewBody, qid: 'Q9188', names: {ko: '엠파이어 스테이트 빌딩'},
  shortDescriptions: {ko: '뉴욕 맨해튼 미드타운에 위치한 마천루', 'zh-CN': '美国纽约摩天大楼', 'zh-TW': '位於美國紐約曼哈頓中城的摩天大樓'}};
const q9188Wikipedia = {...q243Wikipedia, qid: 'Q9188'};

for (const order of [['wikipedia', 'preview'], ['preview', 'wikipedia']]) {
  test(`Chinese short descriptions keep a manager-written value and are cleared on QID switch (${order.join(' then ')})`, async () => {
    const page = loadPage();
    await search(page, [{qid: 'Q9188', name: '엠파이어'}, {qid: 'Q90', name: '파리'}], [{qid: 'Q9188'}, {qid: 'Q90'}]);
    page.field('translations[4].shortDescription').value = '管理者撰寫的說明';
    candidateButton(page, 'Q9188').click();
    await flush();
    for (const kind of order) {
      page.server.respond(kind, 200, kind === 'preview' ? q9188Preview : q9188Wikipedia);
      await flush();
    }
    assert.equal(page.field('translations[3].shortDescription').value, '美国纽约摩天大楼');
    assert.equal(page.field('translations[4].shortDescription').value, '管理者撰寫的說明');
    // Wikipedia 상세 설명은 간단 설명 칸에 들어가지 않는다.
    assert.equal(page.field('translations[4].description').value, '艾菲爾鐵塔（法語：Tour Eiffel）');

    candidateButton(page, 'Q90').click();
    await flush();
    // 관리자 입력이 있으면 확인 단계를 거친다.
    page.root.querySelector('[data-wikidata-switch-review]').querySelectorAll('button')
      .find(button => button.textContent === '수동 입력 유지하고 후보 변경').click();
    await flush();
    assert.equal(page.field('translations[3].shortDescription').value, '', '이전 후보의 자동입력 간단 설명은 지운다');
    assert.equal(page.field('translations[4].shortDescription').value, '管理者撰寫的說明', '관리자 입력은 남긴다');
  });
}

// 실제 Q243 응답 형태: 번체 원문이 없어 간체(zh-hans) 원문을 Wikipedia 변환기로 번체로 바꿨다.
const q243ConvertedPreview = {...q243Preview,
  shortDescriptions: {ko: '프랑스 파리 마르스 광장의 건축물', 'zh-CN': '位于法国巴黎战神广场的铁制镂空塔',
    'zh-TW': '位於法國巴黎戰神廣場的鐵製鏤空塔'},
  shortDescriptionConversions: {'zh-TW': 'zh-hans'}};
const conversionNotes = page => page.form.descendants()
  .filter(node => node.className === 'admin-wikidata-conversion-note');

for (const order of [['wikipedia', 'preview'], ['preview', 'wikipedia']]) {
  test(`Q243 traditional short description is marked as an automatic Chinese script conversion (${order.join(' then ')})`, async () => {
    const page = loadPage();
    await search(page, [{qid: 'Q243', name: '에펠탑'}, {qid: 'Q90', name: '파리'}], [{qid: 'Q243'}, {qid: 'Q90'}]);
    candidateButton(page, 'Q243').click();
    await flush();
    for (const kind of order) {
      page.server.respond(kind, 200, kind === 'preview' ? q243ConvertedPreview : q243Wikipedia);
      await flush();
    }
    const simplified = page.field('translations[3].shortDescription');
    const traditional = page.field('translations[4].shortDescription');
    assert.equal(simplified.value, '位于法国巴黎战神广场的铁制镂空塔');
    assert.equal(simplified.dataset.autofill, 'wikidata', '원문은 Wikidata 자동입력으로 표시한다');
    assert.equal(traditional.value, '位於法國巴黎戰神廣場的鐵製鏤空塔');
    assert.equal(traditional.dataset.autofill, 'chinese-conversion');
    const notes = conversionNotes(page);
    assert.equal(notes.length, 1);
    assert.equal(traditional.parent.children[traditional.parent.children.indexOf(traditional) + 1], notes[0]);
    assert.match(notes[0].textContent, /^중국어 표기 자동 변환 · Wikidata 간체\(zh-hans\) 설명을 Wikipedia 변환기로 번체로/);
    assert.match(page.root.querySelector('[data-wikidata-stage="basic"]').textContent,
      /중국어 표기 자동 변환: 중국어\(번체\) 간단 설명/);
    // 간단 설명 보완은 상세 설명·Wikipedia 출처와 섞이지 않는다.
    assert.equal(page.field('translations[4].description').value, '艾菲爾鐵塔（法語：Tour Eiffel）');
    assert.equal(page.field('wikipediaRevisionIds[4]').value, '201');

    candidateButton(page, 'Q90').click();
    await flush();
    assert.equal(traditional.value, '', '다른 후보로 바꾸면 변환 문장을 지운다');
    assert.equal(conversionNotes(page).length, 0);
  });
}

test('editing a converted Chinese short description turns it into a manager-written value', async () => {
  const page = loadPage();
  await search(page, [{qid: 'Q243', name: '에펠탑'}], [{qid: 'Q243'}]);
  page.field('translations[3].shortDescription').value = '管理员写的说明';
  candidateButton(page, 'Q243').click();
  await flush();
  page.server.respond('preview', 200, q243ConvertedPreview);
  await flush();
  // 관리자가 먼저 쓴 간체 설명은 덮어쓰지 않는다.
  assert.equal(page.field('translations[3].shortDescription').value, '管理员写的说明');

  const traditional = page.field('translations[4].shortDescription');
  traditional.value = '巴黎鐵塔';
  page.form.dispatchEvent(Object.assign(new FakeEvent('input'), {target: traditional}));
  assert.equal(traditional.dataset.autofill, undefined);
  assert.equal(conversionNotes(page).length, 0, '관리자가 고친 칸에는 변환 안내를 남기지 않는다');
});

test('Q243 traditional title keeps a manager-written title and is cleared when switching candidates', async () => {
  const page = loadPage();
  await search(page, [{qid: 'Q243', name: '에펠탑'}, {qid: 'Q90', name: '파리'}], [{qid: 'Q243'}, {qid: 'Q90'}]);
  page.field('translations[4].name').value = '巴黎鐵塔';
  candidateButton(page, 'Q243').click();
  await flush();
  page.server.respond('preview', 200, q243Preview);
  page.server.respond('wikipedia', 200, q243Wikipedia);
  await flush();
  assert.equal(page.field('translations[4].name').value, '巴黎鐵塔');

  page.field('translations[4].name').value = '';
  page.form.dispatchEvent(Object.assign(new FakeEvent('input'), {target: page.field('translations[4].name')}));
  candidateButton(page, 'Q243').click();
  await flush();
  assert.equal(page.field('translations[4].name').value, '艾菲爾鐵塔', '같은 후보를 다시 고르면 빈 칸을 채운다');

  candidateButton(page, 'Q90').click();
  await flush();
  assert.equal(page.field('translations[4].name').value, '', '다른 후보로 바꾸면 이전 후보의 번체 제목이 남지 않는다');
});

test('registration page keeps its Commons selection rules through the shared picker (first photo is main)', async () => {
  const page = loadPage();
  await search(page, [{qid: 'Q243', name: '에펠탑'}], [{qid: 'Q243'}]);
  candidateButton(page, 'Q243').click();
  await flush();
  page.server.respond('preview', 200, previewBody);
  page.server.respond('wikipedia', 200, wikipediaBody);
  page.server.respond('commons-photos', 200, {qid: 'Q243', status: 'AVAILABLE', photos: ['A.jpg', 'B.jpg'].map(fileName => ({
    fileName, savable: true, reuseStatus: 'ELIGIBLE', source: 'CATEGORY',
    thumbnailUrl: 'https://thumb.wikimedia.org/wikipedia/commons/thumb/x.jpg'}))});
  await flush();
  const area = page.form.querySelector('[data-wikidata-commons-area]');
  for (const fileName of ['A.jpg', 'B.jpg']) {
    const box = area.querySelectorAll('article').find(card => card.text().includes(fileName)).querySelectorAll('input')[0];
    box.checked = true;
    box.dispatchEvent(new FakeEvent('change'));
  }

  assert.deepEqual(JSON.parse(page.field('commonsSelectedPhotosJson').value), {qid: 'Q243', photos: [
    {fileName: 'A.jpg', main: true}, {fileName: 'B.jpg', main: false}]});
});

test('travel info fills the official website and phone, marks them as automatic and clears them on candidate switch', async () => {
  const page = loadPage();
  await search(page, [{qid: 'Q19675', name: '루브르 박물관'}, {qid: 'Q90', name: '파리'}], [{qid: 'Q19675'}, {qid: 'Q90'}]);
  page.field('restaurantInfo.contactNumber').value = '관리자가 쓴 번호';
  candidateButton(page, 'Q19675').click();
  await flush();
  page.server.respond('preview', 200, {...previewBody, qid: 'Q19675', travelInfo: {
    homepageUrl: 'https://www.louvre.fr/en/', contactNumber: '+33-1-40-20-53-17',
    openingHoursStated: true, admissionFeeStated: true}});
  await flush();

  const homepage = page.field('attractionInfo.homepageUrl');
  assert.equal(homepage.value, 'https://www.louvre.fr/en/');
  assert.equal(homepage.dataset.autofill, 'wikidata');
  assert.equal(page.field('attractionInfo.contactNumber').value, '+33-1-40-20-53-17');
  assert.equal(page.field('restaurantInfo.contactNumber').value, '관리자가 쓴 번호', '관리자 입력은 덮어쓰지 않는다');
  const detailText = page.root.querySelector('[data-wikidata-detail]').text();
  assert.match(detailText, /운영시간·휴관일·입장료는 자주 바뀌어 자동입력하지 않습니다/);
  assert.match(detailText, /Wikidata에 운영시간·입장료 정보가 있지만/);
  assert.match(page.root.querySelector('[data-wikidata-stage="basic"]').textContent, /상세정보 홈페이지·전화번호 자동입력/);

  // 관리자가 고친 칸은 자동입력 표시가 사라지고, 후보를 바꿔도 남는다.
  const phone = page.field('attractionInfo.contactNumber');
  phone.value = '+33 1 00 00 00 00';
  page.form.dispatchEvent(Object.assign(new FakeEvent('input'), {target: phone}));
  assert.equal(phone.dataset.autofill, undefined);

  candidateButton(page, 'Q90').click();
  await flush();
  assert.equal(homepage.value, '', '이전 후보의 자동입력 홈페이지는 지운다');
  assert.equal(homepage.dataset.autofill, undefined);
  assert.equal(phone.value, '+33 1 00 00 00 00');
  page.server.respond('wikipedia', 200, {...wikipediaBody, qid: 'Q19675'});
  page.server.respond('commons-photos', 200, commonsBody);
  await flush();
});

function submit(page) {
  const event = new FakeEvent('submit');
  page.form.dispatchEvent(event);
  return event;
}

test('registration locks the submit button and shows progress only after the submit really starts', async () => {
  const page = loadPage();
  const button = page.form.querySelector('[data-registration-submit]');
  const status = page.form.querySelector('[data-registration-status]');
  page.field('wikidataQid').value = 'Q243';

  const first = submit(page);
  await flush();
  assert.equal(first.defaultPrevented, false);
  assert.equal(button.disabled, true);
  assert.equal(button.textContent, '등록 중…');
  assert.equal(status.hidden, false);
  assert.match(status.textContent, /등록 중입니다/);
  // 서버 응답 전에는 완료나 성공을 표시하지 않는다.
  assert.doesNotMatch(status.textContent, /완료되었습니다|성공/);

  const second = submit(page);
  assert.equal(second.defaultPrevented, true, '진행 중에는 다시 제출하지 않는다');
});

test('a submit blocked by another check does not lock the registration button', async () => {
  const page = loadPage();
  await search(page, [{qid: 'Q243', name: '에펠탑'}], [{qid: 'Q243'}]);
  candidateButton(page, 'Q243').click();
  await flush();
  // 기본정보·Wikipedia 조회 중에는 기존 검사가 제출을 막는다.
  const blocked = submit(page);
  await flush();
  assert.equal(blocked.defaultPrevented, true);
  assert.equal(page.form.querySelector('[data-registration-submit]').disabled, false);
  // 막힌 이유는 버튼 옆에 보여주고, 진행 중 표시는 하지 않는다.
  const status = page.form.querySelector('[data-registration-status]');
  assert.equal(status.hidden, false);
  assert.match(status.textContent, /조회가 끝난 뒤 등록해 주세요/);
  assert.doesNotMatch(status.textContent, /등록 중입니다/);
  page.server.respond('preview', 200, previewBody);
  page.server.respond('wikipedia', 200, wikipediaBody);
  page.server.respond('commons-photos', 200, commonsBody);
  await flush();
});

test('reselecting the same QID shares the in-flight request instead of calling the API again', async () => {
  const page = loadPage();
  await search(page, [{qid: 'Q243', name: '에펠탑'}], [{qid: 'Q243'}]);
  candidateButton(page, 'Q243').click();
  await flush();
  candidateButton(page, 'Q243').click();
  await flush();
  assert.equal(page.server.calls.filter(call => call.startsWith('preview')).length, 1);
  assert.equal(page.server.calls.filter(call => call.startsWith('wikipedia')).length, 1);
  page.server.respond('preview', 200, previewBody);
  page.server.respond('wikipedia', 200, wikipediaBody);
  page.server.respond('commons-photos', 200, commonsBody);
  await flush();
  assert.equal(page.field('wikidataQid').value, 'Q243');
  assert.equal(page.field('translations[0].description').value, 'ko 설명 원문');
});
