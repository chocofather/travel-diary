// 이미지 순서 편집: 원하는 위치로 옮기면 나머지는 차례대로 밀린다.
const test = require('node:test');
const assert = require('node:assert/strict');
const {moveItem, changedCount, insertionPoint, finalIndex} =
  require('../../main/resources/static/js/admin-destination-image-order.js');

/** 한 줄에 cols 칸인 격자의 칸 위치(칸 100×80, 간격 10). */
function grid(count, cols) {
  return Array.from({length: count}, (_, i) => {
    const left = (i % cols) * 110, top = Math.floor(i / cols) * 90;
    return {left, right: left + 100, top, bottom: top + 80};
  });
}

test('the drop target follows the pointer inside a 38-photo grid, including row ends and the gaps between rows', () => {
  const rects = grid(38, 6);
  // 3번 칸(0부터 2)의 왼쪽 절반 → 3번 앞
  assert.equal(insertionPoint(rects, 225, 40).index, 2);
  // 3번 칸의 오른쪽 절반 → 4번 앞
  assert.equal(insertionPoint(rects, 290, 40).index, 3);
  // 첫 줄 끝을 넘으면 그 줄 마지막 칸 뒤(표시선도 그 칸 오른쪽)
  const rowEnd = insertionPoint(rects, 900, 40);
  assert.equal(rowEnd.index, 6);
  assert.equal(rowEnd.x, 5 * 110 + 100 + 5);
  assert.equal(rowEnd.top, 0);
  // 줄 사이 간격(84)은 가까운 줄로 본다
  assert.equal(insertionPoint(rects, 5, 84).index, 0);
  // 격자 아래·마지막 칸 뒤 → 맨 끝
  assert.equal(insertionPoint(rects, 900, 5000).index, 38);
});

test('the final position accounts for the photo leaving its own slot', () => {
  // 22번(21)을 3번 앞(2)에 → 3번 자리
  assert.equal(finalIndex(21, 2), 2);
  // 3번(2)을 23번 앞(22)에 → 22번 자리
  assert.equal(finalIndex(2, 22), 21);
  // 자기 앞이나 바로 뒤에 놓으면 제자리
  assert.equal(finalIndex(5, 5), 5);
  assert.equal(finalIndex(5, 6), 5);
});

const photos = count => Array.from({length: count}, (_, index) => index + 1);

test('moving photo 28 to position 2 shifts the photos in between back by one', () => {
  const before = photos(32);
  const after = moveItem(before, 27, 1);

  assert.deepEqual(after.slice(0, 4), [1, 28, 2, 3]);
  assert.deepEqual(after.slice(27, 30), [27, 29, 30]);
  assert.equal(after.length, 32);
  assert.deepEqual([...after].sort((a, b) => a - b), before, '사진이 빠지거나 겹치지 않는다');
  assert.equal(changedCount(before, after), 27, '2번부터 28번까지 자리가 바뀐다');
});

test('moving forward, backward, past the ends and back again', () => {
  assert.deepEqual(moveItem([1, 2, 3, 4], 0, 3), [2, 3, 4, 1]);
  assert.deepEqual(moveItem([1, 2, 3, 4], 3, -5), [4, 1, 2, 3]);
  assert.deepEqual(moveItem([1, 2, 3, 4], 1, 99), [1, 3, 4, 2]);
  const moved = moveItem([1, 2, 3, 4], 2, 0);
  assert.equal(changedCount([1, 2, 3, 4], moveItem(moved, 0, 2)), 0, '되돌리면 바뀐 사진이 없다');
});
