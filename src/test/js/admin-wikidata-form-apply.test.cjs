const test = require('node:test');
const assert = require('node:assert/strict');
const {planAutofill, planTravelInfo, stillEmptyChanges, manualConflicts, automaticFieldsToClear, mergeSearchDetails} = require('../../main/resources/static/js/admin-wikidata-form-apply.js');

test('search details keep the quick list order, fill empty values and drop non-place candidates', () => {
  const merged = mergeSearchDetails([
    {qid: 'Q243', name: '에펠탑', shortDescription: '파리의 건축물', country: null},
    {qid: 'Q3821251', name: '에펠탑', shortDescription: '그림'},
    {qid: 'Q90', name: '파리', shortDescription: '프랑스의 수도'}
  ], [
    {qid: 'Q90', name: 'Paris', shortDescription: null, country: '프랑스', region: null},
    {qid: 'Q243', name: '에펠탑', shortDescription: '', country: '프랑스', region: '파리', imageUrl: 'https://commons.wikimedia.org/x'}
  ]);

  assert.deepEqual(merged.map(item => item.qid), ['Q243', 'Q90']);
  assert.equal(merged[0].shortDescription, '파리의 건축물');
  assert.equal(merged[0].region, '파리');
  assert.equal(merged[1].name, 'Paris');
  assert.equal(merged[1].shortDescription, '프랑스의 수도');
  assert.deepEqual(mergeSearchDetails([{qid: 'Q1'}], []), []);
});

const path = [
  {id: 16, regionName: '유럽'},
  {id: 17, regionName: '프랑스'},
  {id: 106, regionName: '파리'}
];

test('proposes only source language values and mapped coordinates', () => {
  const preview = {
    names: {ko: '에펠탑', en: 'Eiffel Tower', 'zh-CN': '埃菲尔铁塔'},
    shortDescriptions: {ko: '파리의 탑', en: 'tower in Paris'},
    latitude: 48.858296,
    longitude: 2.294479,
    regionMatch: {matched: true, countryId: 17, regionId: 106, path}
  };
  const current = {
    'translations[0].name': '',
    'translations[0].shortDescription': '',
    'translations[1].name': '',
    'translations[1].shortDescription': '',
    'translations[2].name': '',
    'translations[3].name': '',
    'translations[4].name': '',
    'translations[4].shortDescription': '',
    latitude: '', longitude: '', type: '', season: ''
  };

  const result = planAutofill(preview, current, false);

  assert.deepEqual(result.changes.map(change => change.name), [
    'translations[0].name', 'translations[0].shortDescription',
    'translations[1].name', 'translations[1].shortDescription',
    'translations[3].name', 'latitude', 'longitude'
  ]);
  assert.equal(result.changes[4].value, '埃菲尔铁塔');
  assert.deepEqual(result.regionPath, path);
  assert.equal(result.regionStatus, 'matched');
  assert.equal(result.changes.some(change => change.name === 'type' || change.name === 'season'), false);
});

test('preserves manager values and an in-progress region selection', () => {
  const preview = {
    names: {ko: '에펠탑', en: 'Eiffel Tower'},
    shortDescriptions: {ko: '파리의 탑'},
    latitude: 48.85,
    regionMatch: {matched: true, countryId: 17, regionId: 106, path}
  };
  const current = {
    'translations[0].name': '직접 작성한 이름',
    'translations[0].shortDescription': '',
    'translations[1].name': 'Custom English name',
    latitude: '48.90'
  };

  const result = planAutofill(preview, current, true);

  assert.deepEqual(result.changes.map(change => change.name), ['translations[0].shortDescription']);
  assert.deepEqual(result.preserved.map(field => field.name), [
    'translations[0].name', 'translations[1].name', 'latitude'
  ]);
  assert.equal(result.regionPath, null);
  assert.equal(result.regionStatus, 'existing');
});

test('missing Korean and uncertain region remain manual inputs', () => {
  const preview = {
    names: {en: 'Statue of Liberty'},
    shortDescriptions: {'zh-CN': '说明'},
    regionMatch: {matched: false, path: []}
  };
  const current = {
    'translations[0].name': '',
    'translations[1].name': '',
    'translations[3].shortDescription': ''
  };

  const result = planAutofill(preview, current, false);

  assert.deepEqual(result.changes.map(change => change.name), [
    'translations[1].name', 'translations[3].shortDescription'
  ]);
  assert.equal(result.regionPath, null);
  assert.equal(result.regionStatus, 'manual');
});

test('changes made after review cannot be overwritten at confirmation', () => {
  const reviewed = [
    {name: 'translations[0].name', label: '한국어 여행지명', value: '에펠탑'},
    {name: 'latitude', label: '위도', value: '48.858296'}
  ];

  const remaining = stillEmptyChanges(reviewed, {
    'translations[0].name': '관리자가 새로 입력한 이름',
    latitude: ''
  });

  assert.deepEqual(remaining.map(change => change.name), ['latitude']);
});

test('a path with IDs different from the matched country and city is not selected', () => {
  const preview = {
    names: {}, shortDescriptions: {},
    regionMatch: {matched: true, countryId: 17, regionId: 106,
      path: [path[0], path[1], {id: 999, regionName: '다른 도시'}]}
  };

  const result = planAutofill(preview, {}, false);

  assert.equal(result.regionPath, null);
  assert.equal(result.regionStatus, 'manual');
});

test('candidate change clears only unchanged automatic values before applying new source', () => {
  const current = {
    'translations[0].name': '에펠탑',
    'translations[1].name': 'Custom English',
    'translations[0].shortDescription': '파리의 탑'
  };
  const automatic = {
    'translations[0].name': '에펠탑',
    'translations[1].name': 'Eiffel Tower',
    'translations[0].shortDescription': '파리의 탑'
  };
  const clears = automaticFieldsToClear(current, automatic);
  assert.deepEqual(clears, ['translations[0].name', 'translations[0].shortDescription']);
  for (const name of clears) current[name] = '';
  const result = planAutofill({names: {ko: '자유의 여신상'}, shortDescriptions: {}}, current, false);
  assert.deepEqual(result.changes.map(item => item.name), ['translations[0].name']);
  assert.equal(current['translations[1].name'], 'Custom English');
});

test('candidate switch reports manual conflicts without treating unchanged automatic fields as manual', () => {
  const conflicts = manualConflicts({
    'translations[0].name': '직접 고친 이름',
    'translations[1].name': 'Eiffel Tower',
    'translations[0].description': '직접 쓴 설명'
  }, {'translations[0].name': '에펠탑', 'translations[1].name': 'Eiffel Tower'});
  assert.deepEqual(conflicts.map(item => item.name), [
    'translations[0].name', 'translations[0].description'
  ]);
});

test('travel info fills every section homepage and phone within each column limit without touching manual input', () => {
  const current = {
    'attractionInfo.homepageUrl': '', 'attractionInfo.contactNumber': '',
    'accommodationInfo.homepageUrl': '', 'accommodationInfo.contactNumber': '',
    'restaurantInfo.homepageUrl': 'https://manual.example', 'restaurantInfo.contactNumber': '',
    'activityInfo.homepageUrl': '', 'shopInfo.contactNumber': ''
  };
  const longPhone = '+44 (0)20 7323 8000 ext. 1234 5678 9012';
  const changes = planTravelInfo({homepageUrl: 'https://www.louvre.fr/en/', contactNumber: longPhone}, current);

  assert.deepEqual(changes.map(change => change.name), [
    'attractionInfo.homepageUrl', 'attractionInfo.contactNumber',
    'accommodationInfo.homepageUrl',
    'activityInfo.homepageUrl', 'shopInfo.contactNumber'
  ]);
  assert.ok(changes.every(change => change.value.length <= 255));
  assert.deepEqual(planTravelInfo(null, current), []);
  assert.deepEqual(planTravelInfo({homepageUrl: 'https://a.example', contactNumber: '+1 555'}, {})
    .map(change => change.name), []);
});
