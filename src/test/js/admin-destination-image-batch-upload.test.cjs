// 이미지 관리 화면의 나눠 올리기: 한 장씩 차례로 보내고, 실패한 사진만 같은 키로 다시 보낸다.
const test = require('node:test');
const assert = require('node:assert/strict');
const {planUpload, summarize, describeResponse, runUploadQueue, markRetry} =
  require('../../main/resources/static/js/admin-destination-image-batch-upload.js');

const MB = 1024 * 1024;
// 화면이 받는 사진 한 장 한도: min(max-file-size 20MB, max-request-size 25MB - 64KB) = 20MB
const MAX = 20 * MB;
let keySeq = 0;
const newKey = () => `key-${++keySeq}`;
const photos = (count, size = 1.2 * MB) => Array.from({length: count}, (_, i) => ({name: `p${i + 1}.jpg`, size}));

/** 서버 흉내: 업로드 키로 저장 여부를 기억하고, 정해 둔 사진은 한 번 실패시킨다. */
function fakeServer({failOnce = new Set(), networkErrorOnce = new Set()} = {}) {
  const saved = new Map();
  const sends = [];
  const send = async entry => {
    sends.push(entry.name);
    await Promise.resolve();
    if (networkErrorOnce.delete(entry.name)) {
      // 서버는 저장을 마쳤는데 응답만 끊긴 경우
      saved.set(entry.uploadKey, saved.size + 1);
      throw new TypeError('Failed to fetch');
    }
    if (failOnce.delete(entry.name)) return {ok: false, message: '사진을 저장하지 못했습니다.'};
    const already = saved.has(entry.uploadKey);
    if (!already) saved.set(entry.uploadKey, saved.size + 1);
    return {ok: true, imageId: saved.get(entry.uploadKey), alreadySaved: already};
  };
  return {saved, sends, send};
}

for (const count of [1, 8, 20]) {
  test(`${count} photos are sent one at a time in selection order and all succeed`, async () => {
    const entries = planUpload(photos(count), MAX, newKey);
    const server = fakeServer();
    const seen = [];

    await runUploadQueue(entries, server.send, entry => seen.push(`${entry.name}:${entry.status}`));

    assert.deepEqual(server.sends, entries.map(entry => entry.name));
    assert.equal(server.saved.size, count);
    assert.equal(summarize(entries).success, count);
    // 한 번에 한 장만 올린다: 올리는 중 → 끝남이 번갈아 온다.
    assert.deepEqual(seen.slice(0, 2), ['p1.jpg:uploading', 'p1.jpg:success']);
  });
}

test('a 10.89MB photo is uploaded; only the photo over 20MB is left out with its reason', async () => {
  const files = photos(20);
  files[2] = {name: 'camera.jpg', size: Math.round(10.89 * MB)};
  files[4] = {name: 'huge.jpg', size: Math.round(21.3 * MB)};
  files[5] = {name: 'exact.jpg', size: 20 * MB};
  const entries = planUpload(files, MAX, newKey);
  const server = fakeServer();

  const before = summarize(entries);
  assert.equal(before.total, 20);
  assert.equal(Math.round(before.bytes / MB), 73);
  assert.equal(entries[2].status, 'pending', '10.89MB 는 올린다');
  assert.equal(entries[5].status, 'pending', '정확히 20MB 까지는 올린다');
  assert.equal(entries[4].status, 'failed');
  assert.equal(entries[4].message, '사진 한 장은 20MB까지 올릴 수 있습니다. (이 사진 21.30MB)');

  await runUploadQueue(entries, server.send);

  assert.ok(!server.sends.includes('huge.jpg'));
  assert.ok(server.sends.includes('camera.jpg') && server.sends.includes('exact.jpg'));
  assert.equal(summarize(entries).success, 19);
  // 용량 초과는 다시 보내도 같은 결과라 재시도 대상이 아니다.
  assert.equal(markRetry(entries), 0);
});

test('a failure in the middle keeps the saved photos, and retry sends only the failed ones without duplicates', async () => {
  const entries = planUpload(photos(20), MAX, newKey);
  const server = fakeServer({failOnce: new Set(['p9.jpg']), networkErrorOnce: new Set(['p14.jpg'])});

  await runUploadQueue(entries, server.send);

  const afterFirst = summarize(entries);
  assert.deepEqual([afterFirst.success, afterFirst.failed], [18, 2]);
  assert.deepEqual(entries.filter(e => e.status === 'failed').map(e => e.name), ['p9.jpg', 'p14.jpg']);
  assert.match(entries[13].message, /네트워크 오류/);
  // p14 는 서버에 이미 저장됐다(응답만 끊김).
  assert.equal(server.saved.size, 19);

  server.sends.length = 0;
  assert.equal(markRetry(entries), 2);
  await runUploadQueue(entries, server.send);

  assert.deepEqual(server.sends, ['p9.jpg', 'p14.jpg'], '실패한 사진만 다시 보낸다');
  assert.equal(summarize(entries).success, 20);
  assert.equal(server.saved.size, 20, '같은 키로 다시 보낸 p14 는 두 번 저장되지 않는다');
});

test('server responses become readable reasons', () => {
  assert.deepEqual(describeResponse(200, {imageId: 7, alreadySaved: false}, false), {ok: true, imageId: 7, alreadySaved: false});
  assert.equal(describeResponse(413, null, false, MAX).message,
    '사진이 서버 용량 한도를 넘었습니다. 사진 한 장은 20MB까지 올릴 수 있습니다.');
  assert.equal(describeResponse(400, {message: 'JPG, JPEG, PNG 이미지 파일만 업로드할 수 있습니다.'}, false).message,
    'JPG, JPEG, PNG 이미지 파일만 업로드할 수 있습니다.');
  // 세션이 끊기면 로그인 화면으로 넘어가 200 HTML 이 온다.
  assert.match(describeResponse(200, null, true).message, /로그인/);
  assert.match(describeResponse(403, null, false).message, /보안 토큰/);
  assert.match(describeResponse(500, null, false).message, /HTTP 500/);
});
