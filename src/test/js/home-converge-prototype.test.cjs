// 메인 랜드마크 아치 카드: 진행률·부채꼴 시작 위치·곡선 궤적과 수동 순환 레일(복제본·이동·드래그 판정)을 확인한다.
const test = require('node:test');
const assert = require('node:assert/strict');
const {
  convergeProgress, easeProgress, followProgress, fanStartOffsets, tabletStartOffsets, curveOffset,
  nearestSnap, isDrag, loopShift, loopStep, logicalIndex, initRail
} = require('../../main/resources/static/js/home-converge-prototype.js');

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

test('loop steps wrap around the original range in both directions', () => {
  // 7장: 원본 구간은 전체 목록의 7 ~ 13 칸
  assert.deepEqual(loopStep(12, 1, 7), {shift: 0, target: 13});
  assert.deepEqual(loopStep(13, 1, 7), {shift: -7, target: 7});
  assert.deepEqual(loopStep(7, -1, 7), {shift: 7, target: 13});
  assert.equal(loopShift(3, 7), 7);
  assert.equal(loopShift(16, 7), -7);
  assert.equal(loopShift(10, 7), 0);
  assert.equal(logicalIndex(7, 7), 0);
  assert.equal(logicalIndex(14, 7), 0);
  assert.equal(logicalIndex(6, 7), 6);
  assert.equal(nearestSnap([0, 190, 380], 300), 2);
});

test('only a pointer that moved past the threshold counts as a drag', () => {
  assert.equal(isDrag(0), false);
  assert.equal(isDrag(5), false);
  assert.equal(isDrag(-5), false);
  assert.equal(isDrag(6), true);
  assert.equal(isDrag(-40), true);
});

/*
  순환 레일: 브라우저 대신 쓰는 최소한의 가짜 DOM.
  레일 안쪽 여백 6px, 카드 170px + 간격 20px, 한 화면에 여섯 장. 원본 랜드마크 7장.
  smooth 스크롤은 바로 움직이지 않고 finishScroll() 때 목표에 닿는다(연달아 누르는 동안을 흉내 낸다).
*/
const INSET = 6;
const PITCH = 190;
const CLIENT_WIDTH = 6 * PITCH - 20 + 2 * INSET;
const COUNT = 7;

class FakeElement {
  constructor(tag, classes = [], attrs = {}) {
    this.tag = tag;
    this.classes = new Set(classes);
    this.attrs = {...attrs};
    this.children = [];
    this.parent = null;
    this.listeners = [];
    this.hidden = false;
    this.disabled = false;
  }

  get classList() {
    const classes = this.classes;
    return {
      add: (...names) => names.forEach(name => classes.add(name)),
      remove: (...names) => names.forEach(name => classes.delete(name)),
      contains: name => classes.has(name),
      toggle: (name, force) => {
        const on = force === undefined ? !classes.has(name) : Boolean(force);
        if (on) classes.add(name); else classes.delete(name);
        return on;
      }
    };
  }

  setAttribute(name, value) { this.attrs[name] = String(value); }
  getAttribute(name) { return name in this.attrs ? this.attrs[name] : null; }
  removeAttribute(name) { delete this.attrs[name]; }
  hasAttribute(name) { return name in this.attrs; }

  appendChild(child) {
    child.parent = this;
    this.children.push(child);
    return child;
  }

  insertBefore(child, reference) {
    child.parent = this;
    const index = this.children.indexOf(reference);
    this.children.splice(index < 0 ? this.children.length : index, 0, child);
    return child;
  }

  cloneNode(deep) {
    const copy = new FakeElement(this.tag, [...this.classes], this.attrs);
    if (deep) this.children.forEach(child => copy.appendChild(child.cloneNode(true)));
    return copy;
  }

  matches(selector) {
    return selector.split(',').map(part => part.trim()).some(part => {
      if (part.startsWith('.')) return this.classes.has(part.slice(1));
      if (part.startsWith('[')) return part.slice(1, -1) in this.attrs;
      return this.tag === part;
    });
  }

  descendants() {
    return this.children.flatMap(child => [child, ...child.descendants()]);
  }

  querySelectorAll(selector) { return this.descendants().filter(element => element.matches(selector)); }
  querySelector(selector) { return this.querySelectorAll(selector)[0] ?? null; }

  closest(selector) {
    for (let element = this; element; element = element.parent) {
      if (element.matches(selector)) return element;
    }
    return null;
  }

  focus() { FakeElement.focused = this; }

  addEventListener(type, listener) { this.listeners.push({type, listener}); }
  removeEventListener(type, listener) {
    const index = this.listeners.findIndex(entry => entry.type === type && entry.listener === listener);
    if (index >= 0) this.listeners.splice(index, 1);
  }

  fire(type, props = {}) {
    const event = {
      type, target: this, defaultPrevented: false, propagationStopped: false, ...props,
      preventDefault() { this.defaultPrevented = true; },
      stopPropagation() { this.propagationStopped = true; }
    };
    this.listeners.filter(entry => entry.type === type).forEach(entry => entry.listener(event));
    return event;
  }

  // 레일 안 카드: 화면에 그려지는 카드들 가운데 몇 번째인지로 자리를 셈한다 (복제본은 순환 중에만 그려진다)
  get offsetLeft() {
    const grid = this.parent;
    if (!grid || !grid.isGrid) return 0;
    const index = grid.displayed().indexOf(this);
    return index < 0 ? 0 : INSET + index * PITCH;
  }

  get offsetWidth() { return 170; }
}

class FakeGrid extends FakeElement {
  constructor() {
    super('ul', ['home-converge-grid']);
    this.isGrid = true;
    this.clientWidth = CLIENT_WIDTH;
    this.position = 0;
    this.animationTarget = null;
    this.lastBehavior = null;
  }

  displayed() {
    return this.children.filter(child => this.classes.has('is-looping') || !child.classes.has('is-clone'));
  }

  get scrollWidth() { return this.displayed().length * PITCH - 20 + 2 * INSET; }
  get scrollLeft() { return this.position; }
  set scrollLeft(value) {
    this.position = Math.min(Math.max(0, value), Math.max(0, this.scrollWidth - this.clientWidth));
  }

  scrollTo({left, behavior}) {
    this.lastBehavior = behavior;
    if (behavior === 'smooth') {
      this.animationTarget = left;
    } else {
      this.animationTarget = null;
      this.scrollLeft = left;
    }
  }

  finishScroll() {
    if (this.animationTarget !== null) this.scrollLeft = this.animationTarget;
    this.animationTarget = null;
  }
}

function fakeRail({ready = true, reducedMotion = false, count = COUNT} = {}) {
  const timers = new Map();
  let timerId = 0;
  const win = Object.assign(new FakeElement('window'), {
    matchMedia: query => ({matches: reducedMotion && query.includes('reduced-motion')}),
    getComputedStyle: () => ({paddingLeft: INSET + 'px'}),
    requestAnimationFrame: () => 1,
    setTimeout: callback => { timers.set(++timerId, callback); return timerId; },
    clearTimeout: id => timers.delete(id)
  });
  const flushTimers = () => {
    const pending = [...timers.values()];
    timers.clear();
    pending.forEach(callback => callback());
  };

  const rail = new FakeElement('div', ['home-converge-rail']);
  const prev = rail.appendChild(new FakeElement('button', ['home-converge-nav'], {'data-home-converge-prev': ''}));
  const next = rail.appendChild(new FakeElement('button', ['home-converge-nav'], {'data-home-converge-next': ''}));
  const grid = rail.appendChild(new FakeGrid());
  const originals = Array.from({length: count}, (_, i) => {
    const card = grid.appendChild(new FakeElement('li', ['home-converge-card',
      ...(i === 0 ? ['is-first'] : []), ...(i === count - 1 ? ['is-last'] : [])]));
    card.appendChild(new FakeElement('a', ['home-converge-link'], {href: `/destinations/${i + 1}`, id: `landmark-${i + 1}`}));
    return card;
  });

  let used = 0;
  const control = initRail(win, rail, grid, originals, () => ready, () => { used += 1; });
  // 수렴이 끝나면 init 이 부르는 것과 같다
  control.refresh();

  // 첫 칸(원본 1번)이 레일 왼쪽 끝에 놓였을 때의 자리
  const start = COUNT * PITCH;
  return {win, rail, grid, prev, next, originals, control, flushTimers, used: () => used, start};
}

// 레일 왼쪽 끝에 보이는 카드의 원본 번호 (복제본이면 같은 링크의 원본 번호)
function leftmostLandmark(grid) {
  const index = Math.round(grid.scrollLeft / PITCH);
  return grid.displayed()[index].querySelector('.home-converge-link').getAttribute('href');
}

test('the rail loops with one clone set on each side that assistive tech never sees', () => {
  const {grid, prev, next, originals, start} = fakeRail();

  assert.ok(grid.classes.has('is-looping'));
  assert.equal(grid.children.length, COUNT * 3);
  assert.deepEqual(grid.children.slice(COUNT, COUNT * 2), originals);
  // [복제 1..7][원본 1..7][복제 1..7] — 같은 순서
  const hrefs = grid.children.map(card => card.querySelector('.home-converge-link').getAttribute('href'));
  assert.deepEqual(hrefs.slice(0, COUNT), hrefs.slice(COUNT, COUNT * 2));
  assert.deepEqual(hrefs.slice(COUNT * 2), hrefs.slice(COUNT, COUNT * 2));

  const clones = grid.children.filter(card => card.classes.has('is-clone'));
  assert.equal(clones.length, COUNT * 2);
  clones.forEach(clone => {
    assert.equal(clone.getAttribute('aria-hidden'), 'true');
    assert.ok(!clone.classes.has('is-first') && !clone.classes.has('is-last'));
    const link = clone.querySelector('.home-converge-link');
    assert.equal(link.getAttribute('tabindex'), '-1');
    // 같은 id 가 두 번 생기지 않는다
    assert.equal(link.hasAttribute('id'), false);
  });
  originals.forEach(card => {
    assert.equal(card.hasAttribute('aria-hidden'), false);
    assert.equal(card.querySelector('.home-converge-link').hasAttribute('tabindex'), false);
  });

  // 원본 1번이 왼쪽 끝이고, 끝이 없으니 두 버튼 모두 쓸 수 있다
  assert.equal(grid.scrollLeft, start);
  assert.equal(prev.hidden, false);
  assert.equal(next.hidden, false);
  assert.equal(prev.disabled, false);
  assert.equal(next.disabled, false);
});

test('next moves one card at a time and wraps from the last landmark to the first', () => {
  const {grid, next, control, flushTimers, start} = fakeRail();
  const press = () => {
    next.fire('click');
    grid.finishScroll();
    flushTimers();
  };

  press();
  assert.equal(control.logicalIndex(), 1);
  assert.equal(leftmostLandmark(grid), '/destinations/2');

  for (let i = 0; i < 5; i++) press();
  // [7][1][2][3][4][5] 가 아니라 7번이 왼쪽 끝: [7][1]... 앞은 6번까지 넘긴 상태
  assert.equal(control.logicalIndex(), 6);
  assert.equal(leftmostLandmark(grid), '/destinations/7');

  press();
  assert.equal(control.logicalIndex(), 0);
  assert.equal(leftmostLandmark(grid), '/destinations/1');
  // 멈춘 뒤에는 늘 원본 구간에 있다
  assert.equal(grid.scrollLeft, start);
});

test('prev wraps from the first landmark to the last', () => {
  const {grid, prev, control, flushTimers, start} = fakeRail();

  prev.fire('click');
  grid.finishScroll();
  flushTimers();

  assert.equal(control.logicalIndex(), COUNT - 1);
  assert.equal(leftmostLandmark(grid), '/destinations/7');
  assert.equal(grid.scrollLeft, start + (COUNT - 1) * PITCH);
});

test('rapid clicks keep counting from the pending target', () => {
  const {grid, prev, next, control, flushTimers} = fakeRail();

  // 앞 이동이 끝나기 전에 연달아 누른다
  for (let i = 0; i < 20; i++) next.fire('click');
  assert.equal(control.logicalIndex(), 20 % COUNT);
  grid.finishScroll();
  flushTimers();
  assert.equal(control.logicalIndex(), 20 % COUNT);
  assert.equal(leftmostLandmark(grid), `/destinations/${20 % COUNT + 1}`);

  for (let i = 0; i < 9; i++) prev.fire('click');
  grid.finishScroll();
  flushTimers();
  const expected = ((20 - 9) % COUNT + COUNT) % COUNT;
  assert.equal(control.logicalIndex(), expected);
  assert.equal(leftmostLandmark(grid), `/destinations/${expected + 1}`);
});

test('reduced motion still navigates manually without smooth scrolling', () => {
  const {grid, prev, next, control, flushTimers, start} = fakeRail({reducedMotion: true});

  next.fire('click');
  assert.equal(grid.lastBehavior, 'auto');
  assert.equal(grid.scrollLeft, start + PITCH);
  flushTimers();
  assert.equal(control.logicalIndex(), 1);

  prev.fire('click');
  prev.fire('click');
  flushTimers();
  assert.equal(control.logicalIndex(), COUNT - 1);
  assert.equal(leftmostLandmark(grid), '/destinations/7');
});

test('a rail that fits on one screen does not loop or show buttons', () => {
  const {grid, prev, next} = fakeRail({count: 6});

  assert.ok(!grid.classes.has('is-looping'));
  assert.equal(grid.children.length, 6);
  assert.equal(prev.hidden, true);
  assert.equal(next.hidden, true);
});

test('a mouse drag scrolls the rail and swallows the click that ends it', () => {
  const {win, grid, flushTimers, used, start} = fakeRail();

  grid.fire('pointerdown', {pointerType: 'mouse', button: 0, clientX: 600});
  win.fire('pointermove', {clientX: 560});
  assert.ok(grid.classes.has('is-dragging'));
  assert.equal(grid.scrollLeft, start + 40);
  win.fire('pointerup', {});

  // 놓으면 가장 가까운 카드 자리로 맞춘다
  grid.finishScroll();
  assert.equal(grid.scrollLeft, start);
  const click = grid.fire('click', {});
  assert.equal(click.defaultPrevented, true);
  assert.equal(click.propagationStopped, true);
  assert.ok(used() > 0);

  // 멈추면 스냅을 되돌리고, 다음 클릭은 다시 링크로 간다
  flushTimers();
  assert.ok(!grid.classes.has('is-dragging') && !grid.classes.has('is-free'));
  assert.equal(grid.fire('click', {}).defaultPrevented, false);
});

test('dragging past the last landmark keeps going from the first one', () => {
  const {win, grid, control, flushTimers, start} = fakeRail();

  // 일곱 장 반 가까이 왼쪽으로 끈다: 복제 구간에 닿기 전에 같은 모습의 자리로 옮겨 붙는다
  grid.fire('pointerdown', {pointerType: 'mouse', button: 0, clientX: 1500});
  win.fire('pointermove', {clientX: 1500 - (COUNT * PITCH + 60)});
  assert.ok(grid.scrollLeft >= start && grid.scrollLeft < start + COUNT * PITCH);
  win.fire('pointerup', {});
  grid.finishScroll();
  flushTimers();

  assert.equal(control.logicalIndex(), 0);
  assert.equal(leftmostLandmark(grid), '/destinations/1');
  assert.equal(grid.scrollLeft, start);
});

test('a click with almost no movement still follows the card link', () => {
  const {win, grid, start} = fakeRail();

  grid.fire('pointerdown', {pointerType: 'mouse', button: 0, clientX: 600});
  win.fire('pointermove', {clientX: 597});
  win.fire('pointerup', {});

  assert.ok(!grid.classes.has('is-dragging'));
  assert.equal(grid.scrollLeft, start);
  assert.equal(grid.fire('click', {}).defaultPrevented, false);
});

test('the rail ignores every control until the cards have converged', () => {
  const {win, grid, prev, next} = fakeRail({ready: false});

  assert.ok(!grid.classes.has('is-looping'));
  assert.equal(prev.hidden, true);
  assert.equal(next.hidden, true);

  next.fire('click');
  grid.fire('pointerdown', {pointerType: 'mouse', button: 0, clientX: 600});
  win.fire('pointermove', {clientX: 400});
  win.fire('pointerup', {});

  assert.equal(grid.scrollLeft, 0);
  assert.equal(grid.fire('click', {}).defaultPrevented, false);
});
