const test = require('node:test');
const assert = require('node:assert/strict');
const {planWikipediaDescriptions, planWikipediaTitleFallback, stagePreviewDescription, discardPreviewDescription,
  summarizeWikipediaApplication} = require('../../main/resources/static/js/admin-wikipedia-description-apply.js');

// 실제 Q243: Wikidata에는 번체 label이 없고, zhwiki는 zh-tw 변형 제목 艾菲爾鐵塔 를 준다.
const q243Wikipedia = {qid: 'Q243', languages: [
  {language: 'zh-CN', status: 'AVAILABLE', sourceLanguage: 'zh', variant: 'zh-cn',
    title: '艾菲爾鐵塔', displayTitle: '埃菲尔铁塔'},
  {language: 'zh-TW', status: 'AVAILABLE', sourceLanguage: 'zh', variant: 'zh-tw',
    title: '艾菲爾鐵塔', displayTitle: '艾菲爾鐵塔'}
]};
const q243Names = {ko: '에펠탑', en: 'Eiffel Tower', ja: 'エッフェル塔', 'zh-CN': '埃菲尔铁塔'};

test('Q243 traditional Chinese title comes from the Wikipedia zh-tw variant title when Wikidata has none', () => {
  const changes = planWikipediaTitleFallback(q243Wikipedia, q243Names,
    {'translations[3].name': '埃菲尔铁塔', 'translations[4].name': ''});
  assert.deepEqual(changes.map(change => [change.name, change.value]), [['translations[4].name', '艾菲爾鐵塔']]);
});

test('traditional title fallback never overrides Wikidata, manual input, or copies another variant', () => {
  const empty = {'translations[4].name': ''};
  assert.deepEqual(planWikipediaTitleFallback(q243Wikipedia, {...q243Names, 'zh-TW': '艾菲爾鐵塔(Wikidata)'}, empty), []);
  assert.deepEqual(planWikipediaTitleFallback(q243Wikipedia, q243Names, {'translations[4].name': '관리자 제목'}), []);
  // 변환 제목이 없으면 간체 제목이나 원래 문서 제목으로 대신 채우지 않는다.
  const noConverted = {qid: 'Q243', languages: [{...q243Wikipedia.languages[1], displayTitle: null}]};
  assert.deepEqual(planWikipediaTitleFallback(noConverted, q243Names, empty), []);
  const wrongVariant = {qid: 'Q243', languages: [{...q243Wikipedia.languages[1], variant: 'zh-cn'}]};
  assert.deepEqual(planWikipediaTitleFallback(wrongVariant, q243Names, empty), []);
  const disambiguation = {qid: 'Q1', languages: [{...q243Wikipedia.languages[1], displayTitle: '巴黎 (德克薩斯州)'}]};
  assert.deepEqual(planWikipediaTitleFallback(disambiguation, {}, empty), []);
  const unavailable = {qid: 'Q243', languages: [{...q243Wikipedia.languages[1], status: 'QID_MISMATCH'}]};
  assert.deepEqual(planWikipediaTitleFallback(unavailable, q243Names, empty), []);
});
const provenance = {revisionId: 123, licenseName: 'CC BY-SA 4.0',
  licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0/'};

test('plans only matching Wikipedia language and Chinese variant for empty description fields', () => {
  const preview = {languages: [
    {language: 'ko', status: 'AVAILABLE', sourceLanguage: 'ko', description: '에펠탑 소개', ...provenance},
    {language: 'en', status: 'AVAILABLE', sourceLanguage: 'en', description: 'Eiffel Tower introduction', ...provenance, revisionId: 124},
    {language: 'ja', status: 'NO_SITELINK', description: null},
    {language: 'zh-CN', status: 'AVAILABLE', sourceLanguage: 'zh', variant: 'zh-cn', description: '简体介绍', ...provenance},
    {language: 'zh-TW', status: 'AVAILABLE', sourceLanguage: 'zh', variant: 'zh-cn', description: '錯誤變體', ...provenance}
  ]};
  const current = {
    'translations[0].description': '',
    'translations[1].description': '',
    'translations[2].description': '',
    'translations[3].description': '',
    'translations[4].description': ''
  };

  const plan = planWikipediaDescriptions(preview, current);

  assert.deepEqual(plan.changes.map(change => change.name), [
    'translations[0].description', 'translations[1].description', 'translations[3].description'
  ]);
  assert.equal(plan.changes[0].revisionId, 123);
  assert.equal(plan.changes[2].value, '简体介绍');
});

test('preserves manager-written descriptions and never substitutes another language', () => {
  const preview = {languages: [
    {language: 'ko', status: 'AVAILABLE', sourceLanguage: 'en', description: 'English fallback', ...provenance},
    {language: 'en', status: 'AVAILABLE', sourceLanguage: 'en', description: 'Wikipedia English', ...provenance},
    {language: 'ja', status: 'AVAILABLE', sourceLanguage: 'ja', description: '日本語', ...provenance},
    {language: 'zh-TW', status: 'AVAILABLE', sourceLanguage: 'zh', variant: 'zh-tw', description: '繁體', ...provenance}
  ]};
  const current = {
    'translations[0].description': '',
    'translations[1].description': 'Manager English',
    'translations[2].description': '管理者が書いた内容',
    'translations[4].description': ''
  };

  const plan = planWikipediaDescriptions(preview, current);

  assert.deepEqual(plan.changes.map(change => change.name), ['translations[4].description']);
  assert.deepEqual(plan.preserved.map(change => change.name), [
    'translations[1].description', 'translations[2].description'
  ]);
});

test('applied Wikipedia text remains editable and submitted until discarded', () => {
  const attributes = new Map([['name', 'translations[1].description']]);
  const field = {
    value: '',
    getAttribute: name => attributes.get(name) ?? null,
    setAttribute: (name, value) => attributes.set(name, value),
    removeAttribute: name => attributes.delete(name)
  };
  const change = {name: 'translations[1].description', value: 'Wikipedia text'};

  assert.equal(stagePreviewDescription(field, change), true);
  assert.equal(field.value, 'Wikipedia text');
  assert.equal(field.getAttribute('name'), 'translations[1].description');
  field.value = 'Wikipedia text edited by manager';
  assert.equal(discardPreviewDescription(field), true);
  assert.equal(field.value, '');
  assert.equal(field.getAttribute('name'), 'translations[1].description');
});

test('incomplete revision or license cannot be staged as Wikipedia sourced text', () => {
  const current = {'translations[0].description': ''};
  const preview = {languages: [{language: 'ko', sourceLanguage: 'ko', status: 'AVAILABLE',
    description: '소개', revisionId: null, licenseName: 'CC BY-SA', licenseUrl: provenance.licenseUrl}]};
  assert.equal(planWikipediaDescriptions(preview, current).changes.length, 0);
});

test('staging refuses to overwrite a description entered after review', () => {
  const field = {value: 'Manager text', getAttribute: () => 'translations[0].description'};
  assert.equal(stagePreviewDescription(field,
    {name: 'translations[0].description', value: 'Wikipedia text'}), false);
  assert.equal(field.value, 'Manager text');
});

test('source summary counts only descriptions actually applied to the form', () => {
  assert.equal(summarizeWikipediaApplication(['zh-TW', 'ko']),
    'Wikipedia 상세 설명 자동입력됨 · 2개 언어: 한국어 (ko), 중국어(번체) (zh-TW)');
  assert.equal(summarizeWikipediaApplication([]),
    'Wikipedia 상세 설명 자동입력 없음 · 출처 상세 보기에서 언어별 사유를 확인하세요.');
});
