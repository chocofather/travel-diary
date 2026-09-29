// [프로토타입] 메인 스크롤 수렴 카드: 진행률·부채꼴 시작 위치·곡선 궤적 계산만 확인한다.
const test = require('node:test');
const assert = require('node:assert/strict');
const {convergeProgress, easeProgress, followProgress, fanStartOffsets, tabletStartOffsets, curveOffset} =
  require('../../main/resources/static/js/home-converge-prototype.js');

// 1440px 화면, 1120px 격자에 170px 카드 여섯 장이 20px 간격으로 놓인 최종 배치
const VIEWPORT = 1440;
const CARD = 170;
const desktopCards = [0, 1, 2, 3, 4, 5].map(i => ({left: 160 + i * (CARD + 20), width: CARD}));

// 1000px 높이 화면, 카드 한 줄 높이 300px: 윗변 930 에서 시작, 윗변 370(줄 가운데 520)에서 끝
const ROW = 300;

test('progress starts as the grid enters and completes when the row is near the middle', () => {
  assert.equal(convergeProgress(1200, ROW, 1000), 0);
  assert.equal(convergeProgress(930, ROW, 1000), 0);
  assert.equal(convergeProgress(370, ROW, 1000), 1);
  assert.equal(convergeProgress(650, ROW, 1000), 0.5);
  // 지나간 뒤(화면 위로 올라감)에도 정돈된 상태를 유지한다
  assert.equal(convergeProgress(-800, ROW, 1000), 1);
});

test('progress follows the scroll position continuously in both directions', () => {
  const tops = [930, 850, 750, 650, 550, 450, 370];
  const values = tops.map(top => convergeProgress(top, ROW, 1000));
  for (let i = 1; i < values.length; i++) {
    assert.ok(values[i] > values[i - 1], `progress should grow at top=${tops[i]}`);
  }
  assert.equal(convergeProgress(750, ROW, 1000), values[2]);
});

test('an unknown viewport height falls back to the final layout', () => {
  assert.equal(convergeProgress(500, ROW, 0), 1);
  assert.equal(convergeProgress(500, ROW, undefined), 1);
});

test('easing leaves and arrives gently', () => {
  assert.equal(easeProgress(0), 0);
  assert.equal(easeProgress(0.5), 0.5);
  assert.equal(easeProgress(1), 1);
  assert.ok(easeProgress(0.1) < 0.1);
  assert.ok(easeProgress(0.9) > 0.9);
});

test('the drawn progress eases toward the scroll target and settles on it', () => {
  // 한 프레임에는 남은 거리의 일부만 따라간다
  const oneFrame = followProgress(0, 1, 1000 / 60);
  assert.ok(oneFrame > 0.05 && oneFrame < 0.2);
  // 프레임 간격이 두 배면 두 프레임 따라간 것과 같다
  const twoFrames = followProgress(followProgress(0, 1, 1000 / 60), 1, 1000 / 60);
  assert.ok(Math.abs(followProgress(0, 1, 2000 / 60) - twoFrames) < 1e-9);
  // 스크롤을 멈추면 1초 안에 목표에 정확히 닿는다
  let value = 0;
  for (let frame = 0; frame < 60 && value !== 1; frame++) value = followProgress(value, 1, 1000 / 60);
  assert.equal(value, 1);
  // 되돌아갈 때도 같은 방식으로 따라간다
  assert.ok(followProgress(1, 0, 1000 / 60) < 1);
});

test('desktop cards start as two mirrored fans at the viewport edges', () => {
  const starts = fanStartOffsets(desktopCards, VIEWPORT);
  const startLefts = starts.map((start, i) => desktopCards[i].left + start.x);

  // 왼쪽 세 장은 왼쪽으로, 오른쪽 세 장은 오른쪽으로 크게 벌어진다
  starts.slice(0, 3).forEach(start => assert.ok(start.x < -150));
  starts.slice(3).forEach(start => assert.ok(start.x > 150));
  // 바깥 카드는 화면 끝에 걸쳐 있다
  assert.ok(startLefts[0] < 0);
  assert.ok(startLefts[5] + CARD > VIEWPORT);
  // 바깥 → 가운데로 갈수록 높아지는 부채꼴이고, 좌우가 대칭이다
  assert.ok(starts[0].y > starts[1].y && starts[1].y > starts[2].y);
  for (let i = 0; i < 3; i++) {
    assert.ok(Math.abs(starts[i].x + starts[5 - i].x) < 0.001);
    assert.equal(starts[i].y, starts[5 - i].y);
  }
  // 두 부채꼴 사이는 가운데가 비어 있다
  assert.ok(startLefts[3] - (startLefts[2] + CARD) > CARD * 2);
});

test('tablet side columns shift a little and the middle column stays put', () => {
  const cards = [0, 1, 2].map(i => ({left: 20 + i * 250, width: 234}));
  const starts = tabletStartOffsets(cards, 20 + (3 * 234 + 2 * 16) / 2);
  assert.ok(starts[0].x < 0 && starts[0].x > -60);
  assert.ok(starts[2].x > 0 && starts[2].x < 60);
  assert.equal(Math.abs(starts[1].x), 0);
  assert.equal(starts[1].y, 0);
});

test('cards travel along a curve that ends exactly in place', () => {
  const [outer] = fanStartOffsets(desktopCards, VIEWPORT);

  assert.deepEqual(curveOffset(outer, 0), {x: outer.x, y: outer.y});
  const end = curveOffset(outer, 1);
  assert.equal(Math.abs(end.x), 0);
  assert.equal(Math.abs(end.y), 0);

  // 곡선: 중간에서 가로는 절반 넘게 왔지만 세로는 아직 절반 넘게 남아 있다 (직선이면 둘 다 절반)
  const middle = curveOffset(outer, 0.5);
  assert.ok(Math.abs(middle.x) < Math.abs(outer.x) / 2);
  assert.ok(middle.y > outer.y / 2);
});
