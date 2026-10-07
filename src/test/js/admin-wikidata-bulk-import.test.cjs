// 해외 일괄 등록 검토 행의 상태 판정과 등록 요청 본문.
const test = require('node:test');
const assert = require('node:assert/strict');
const {rowState, summarize, registerPayload, runRegistrationQueue, duplicateStatus, AUTO_RETRY_LIMIT} =
  require('../../main/resources/static/js/admin-wikidata-bulk-import.js');

/** 서버 흉내: 제한 중에는 저장하지 않고 RATE_LIMITED, 이미 저장된 QID는 DUPLICATE. 시각은 테스트가 움직인다. */
function fakeServer({limitAfter = Infinity, limitSeconds = 30, alwaysLimited = false, permanent = new Set()} = {}) {
  const state = {time: 0, blockedUntil: 0, saved: new Map(), sends: [], limitedOnce: false};
  state.send = async row => {
    state.sends.push(row.qid);
    await Promise.resolve();
    if (permanent.has(row.qid)) return {qid: row.qid, status: 'FAILED', message: '라이선스를 확인할 수 없습니다.'};
    if (state.saved.has(row.qid)) return {qid: row.qid, status: 'DUPLICATE', destinationId: state.saved.get(row.qid)};
    if (alwaysLimited || state.time < state.blockedUntil || (!state.limitedOnce && state.saved.size >= limitAfter)) {
      if (!alwaysLimited && state.time >= state.blockedUntil) {
        state.limitedOnce = true;
        state.blockedUntil = state.time + limitSeconds * 1000;
      }
      const left = Math.max(1, Math.ceil((state.blockedUntil - state.time) / 1000));
      return {qid: row.qid, status: 'RATE_LIMITED', retryAfterSeconds: alwaysLimited ? limitSeconds : left,
        service: 'Wikidata', message: 'Wikidata 요청이 많습니다. 잠시 후 다시 시도해 주세요.'};
    }
    state.saved.set(row.qid, 1000 + state.saved.size);
    return {qid: row.qid, status: 'SUCCESS', destinationId: state.saved.get(row.qid)};
  };
  return state;
}

function queueOptions(server, extra = {}) {
  const seen = new Set();
  return {
    send: server.send,
    now: () => server.time,
    sleep: async ms => { server.time += ms; await Promise.resolve(); },
    onChange: () => extra.rows?.forEach(row => row.result && seen.add(row.result.status)),
    seen,
    ...extra
  };
}

function registrationRows(count) {
  return Array.from({length: count}, (_, index) => row({qid: `Q${5000 + index + 1}`}));
}

test('19 destinations: 11 succeed, the limit pauses every worker, then only the rest are retried without duplicates', async () => {
  const server = fakeServer({limitAfter: 11, limitSeconds: 30});
  const rows = registrationRows(19);
  const options = queueOptions(server, {rows});

  await runRegistrationQueue(rows, options);

  assert.deepEqual(rows.map(item => item.result.status), Array(19).fill('SUCCESS'));
  assert.equal(server.saved.size, 19, '여행지마다 한 번만 저장된다');
  assert.ok(options.seen.has('WAITING'), '요청 제한 동안 대기 상태를 보여준다');
  assert.ok(options.seen.has('RETRYING'), '대기 뒤 자동 재시도 상태를 보여준다');
  // 성공한 11곳은 다시 보내지 않는다. 제한에 걸린 동안 보낸 요청은 작업 수(2)만큼으로 묶인다.
  const firstEleven = rows.slice(0, 11).map(item => item.qid);
  assert.ok(firstEleven.every(qid => server.sends.filter(sent => sent === qid).length === 1));
  assert.ok(server.sends.length <= 19 + 2, `보낸 요청 ${server.sends.length}건`);
  assert.ok(server.time >= 30000, '서버가 알려준 대기 시간만큼 기다린다');
  assert.equal(summarize(rows).success, 19);
});

test('a limit that never clears stops each destination after the retry limit with a reason', async () => {
  const server = fakeServer({alwaysLimited: true, limitSeconds: 2});
  const rows = registrationRows(3);

  await runRegistrationQueue(rows, queueOptions(server));

  for (const item of rows) {
    assert.equal(item.result.status, 'FAILED');
    assert.match(item.result.message, new RegExp(`자동 재시도 ${AUTO_RETRY_LIMIT}회`));
    assert.match(item.result.message, /실패한 여행지 재시도/);
    assert.equal(server.sends.filter(sent => sent === item.qid).length, AUTO_RETRY_LIMIT + 1);
  }
});

test('permanent errors are sent once and never retried automatically', async () => {
  const server = fakeServer({permanent: new Set(['Q5002'])});
  const rows = registrationRows(3);

  await runRegistrationQueue(rows, queueOptions(server));

  assert.deepEqual(rows.map(item => item.result.status), ['SUCCESS', 'FAILED', 'SUCCESS']);
  assert.equal(server.sends.filter(sent => sent === 'Q5002').length, 1);
});

test('leaving the page stops sending; destinations already saved stay saved', async () => {
  const server = fakeServer({limitAfter: 2, limitSeconds: 60});
  const rows = registrationRows(6);
  let leaving = false;
  const options = queueOptions(server, {
    isCancelled: () => leaving,
    // 첫 대기가 시작되면(요청 제한) 사용자가 페이지를 떠난다.
    sleep: async ms => { leaving = true; server.time += ms; await Promise.resolve(); }
  });

  await runRegistrationQueue(rows, options);

  const sentAfterLeaving = server.sends.length;
  assert.equal(server.saved.size, 2);
  assert.deepEqual(rows.slice(0, 2).map(item => item.result.status), ['SUCCESS', 'SUCCESS']);
  assert.ok(rows.slice(2).every(item => item.result.status === 'FAILED' || item.result.status === 'SUCCESS'));
  assert.ok(rows.slice(2).some(item => /페이지를 벗어나/.test(item.result.message || '')));
  assert.ok(sentAfterLeaving <= 4, `떠난 뒤 더 보내지 않는다(보낸 요청 ${sentAfterLeaving}건)`);
});

function row(overrides = {}) {
  return {qid: 'Q243', reviewState: 'ready', photosState: 'ready', koreanName: '', type: 'ATTRACTION',
    season: 'SPRING', regionId: null, result: null, photo: null,
    review: {names: {ko: '에펠탑'}, autoRegionId: 300}, ...overrides};
}

test('missing values are never defaulted: type, season, region and Korean name need review', () => {
  const needs = rowState(row({type: '', season: '', review: {names: {en: 'Eiffel Tower'}, autoRegionId: null}}));
  assert.deepEqual(needs, {state: 'needs', problems: ['한국어 이름', '지역', '유형', '시즌']});
  // 관리자가 보완하면 등록할 수 있다.
  assert.deepEqual(rowState(row({koreanName: '에펠탑', regionId: 301,
    review: {names: {en: 'Eiffel Tower'}, autoRegionId: null}})), {state: 'ready'});
  assert.equal(rowState(row({reviewState: 'loading'})).state, 'loading');
  assert.equal(rowState(row({photosState: 'loading'})).state, 'loading');
  assert.equal(rowState(row({result: {status: 'DUPLICATE'}})).state, 'done');
  // 실패한 행도 값이 갖춰져 있으면 재시도할 수 있다.
  assert.equal(rowState(row({result: {status: 'FAILED', message: 'x'}})).state, 'ready');
});

test('summary separates never-tried rows from failures and finished rows', () => {
  const count = summarize([row(), row({qid: 'Q2', type: ''}), row({qid: 'Q3', result: {status: 'SUCCESS'}}),
    row({qid: 'Q4', result: {status: 'FAILED'}}), row({qid: 'Q5', result: {status: 'DUPLICATE'}})]);
  assert.equal(count.total, 5);
  assert.equal(count.fresh, 1);
  assert.equal(count.ready, 2);
  assert.equal(count.needs, 1);
  assert.deepEqual([count.success, count.duplicate, count.failed], [1, 1, 1]);
});

test('registration payload sends only file identifiers and keeps the automatic region unless changed', () => {
  assert.deepEqual(registerPayload(row({photo: {fileName: 'Tour Eiffel.jpg'}, koreanName: '  '})), {
    qid: 'Q243', type: 'ATTRACTION', season: 'SPRING', regionId: null, koreanName: null,
    photoFileName: 'Tour Eiffel.jpg'});
  assert.equal(registerPayload(row({regionId: 301, koreanName: ' 에펠탑 '})).regionId, 301);
  assert.equal(registerPayload(row({koreanName: ' 에펠탑 '})).koreanName, '에펠탑');
});

test('possible duplicates are sent only after the admin acknowledged them and are counted apart', () => {
  assert.equal(registerPayload(row()).allowPossibleDuplicate, undefined);
  assert.equal(registerPayload(row({allowPossibleDuplicate: true})).allowPossibleDuplicate, true);
  const count = summarize([row({result: {status: 'POSSIBLE_DUPLICATE', destinationId: 31}}), row({qid: 'Q2'})]);
  assert.equal(count.review, 1);
  // 저장 직전에 새로 찾은 중복 가능성은 다시 확인하기 전까지 '등록 가능'으로 세지 않는다.
  assert.equal(count.fresh, 1);
  assert.equal(duplicateStatus({duplicate: {status: 'POSSIBLE_DUPLICATE'}}), 'POSSIBLE_DUPLICATE');
  assert.equal(duplicateStatus({registered: true}), 'REGISTERED');
  assert.equal(duplicateStatus({registered: false}), 'NOT_REGISTERED');
});
