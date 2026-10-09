const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');

// 지역 rail 이 쓰는 DOM 기능만 흉내 낸다. 항목 폭 70px, 간격 10px.
function fixture({count = 10, clientWidth = 300, selected = null} = {}) {
    const frames = [];
    const observers = [];
    const listeners = {};
    const button = () => ({hidden: true, handlers: [], addEventListener(type, handler) { this.handlers.push(handler); },
        click() { this.handlers.forEach(handler => handler()); }});
    const prev = button();
    const next = button();
    const rail = {
        items: [], clientWidth, scrollLeft: 0, isConnected: true,
        classList: {names: new Set(), toggle(name, on) { on ? this.names.add(name) : this.names.delete(name); }},
        get scrollWidth() { return Math.max(this.clientWidth, this.items.length * 80 - 10); },
        getBoundingClientRect() { return {left: 0, width: this.clientWidth}; },
        querySelectorAll() { return this.items; },
        addEventListener(type, handler) { listeners[type] = handler; },
        scrollTo({left}) { this.scrollLeft = left; listeners.scroll?.(); },
        closest() { return rail; }
    };
    for (let i = 0; i < count; i++) {
        const attributes = new Map();
        const classes = new Set(i === selected ? ['selected'] : []);
        rail.items.push({
            index: i, attributes,
            classList: {contains: name => classes.has(name), toggle(name, on) { on ? classes.add(name) : classes.delete(name); }},
            setAttribute(name, value) { attributes.set(name, String(value)); },
            hasAttribute(name) { return attributes.has(name); },
            matches() { return false; },
            closest() { return rail; },
            cloneNode() { throw new Error('지역 항목을 복제하면 안 된다'); },
            getBoundingClientRect() { return {left: i * 80 - rail.scrollLeft, width: 70}; }
        });
    }
    const window = {matchMedia: () => ({matches: false}), addEventListener() {}};
    const context = {
        window,
        document: {addEventListener() {}},
        requestAnimationFrame(callback) { frames.push(callback); return frames.length; },
        cancelAnimationFrame() {},
        ResizeObserver: class {
            constructor(callback) { this.callback = callback; this.targets = []; observers.push(this); }
            observe(target) { this.targets.push(target); }
            disconnect() { this.disconnected = true; }
        }
    };
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/destination-region-rail.js', 'utf8'), context);
    const api = window.DestinationRegionRail;
    const flush = () => { while (frames.length) frames.shift()(); };
    const rail1 = api.create(rail, rail, '.item', [prev], [next]);
    flush();
    return {rail, prev, next, api, instance: rail1, flush, observers, listeners};
}

test('a list that fits keeps one set of items and shows no arrows', () => {
    const {rail, prev, next} = fixture({count: 3, clientWidth: 300});
    assert.equal(rail.items.length, 3);
    assert.ok(rail.items.every(item => !item.hasAttribute('data-rail-clone')));
    assert.equal(prev.hidden, true);
    assert.equal(next.hidden, true);
});

test('real overflow shows the arrows and the last item can be reached completely', () => {
    const {rail, prev, next, flush} = fixture({count: 10, clientWidth: 300});
    assert.equal(rail.items.length, 10);
    assert.equal(prev.hidden, true);
    assert.equal(next.hidden, false);

    // 오른쪽에서 잘린 첫 항목(4번째, left 240)이 왼쪽 끝으로 온다
    next.click(); flush();
    assert.equal(rail.scrollLeft, 240);
    assert.equal(prev.hidden, false);

    for (let i = 0; i < 10; i++) { next.click(); flush(); }
    const max = rail.scrollWidth - rail.clientWidth;
    assert.equal(rail.scrollLeft, max);
    const last = rail.items.at(-1).getBoundingClientRect();
    assert.ok(last.left + last.width <= rail.clientWidth);
    assert.equal(next.hidden, true);

    for (let i = 0; i < 10; i++) { prev.click(); flush(); }
    assert.equal(rail.scrollLeft, 0);
    assert.equal(prev.hidden, true);
    assert.equal(next.hidden, false);
});

test('overflow is recalculated after resize and a re-rendered list gets its own state', () => {
    const {rail, next, flush, observers, api} = fixture({count: 5, clientWidth: 300});
    assert.equal(next.hidden, false);
    assert.ok(observers[0].targets.includes(rail));

    rail.clientWidth = 600;
    observers[0].callback(); flush();
    assert.equal(next.hidden, true);

    rail.clientWidth = 200;
    observers[0].callback(); flush();
    assert.equal(next.hidden, false);

    // 목록이 교체되면 옛 rail 은 정리되고 새 rail 이 처음부터 다시 계산한다
    rail.isConnected = false;
    const fresh = fixture({count: 2, clientWidth: 300});
    api.create(null, null, '.item', [], []);
    assert.equal(observers[0].disconnected, true);
    assert.equal(fresh.next.hidden, true);
});

test('selection reveals an off-screen item and leaves a visible one in place', () => {
    const {rail, instance, flush} = fixture({count: 10, clientWidth: 300, selected: 8});
    // 처음 그릴 때 선택된 지역(left 640)이 보이도록 맞춘다
    assert.equal(rail.scrollLeft, 640 + 70 - 300);

    const before = rail.scrollLeft;
    instance.select(rail.items[7]); flush();
    assert.equal(rail.scrollLeft, before);
    assert.equal(rail.items.filter(item => item.classList.contains('selected')).length, 1);
    assert.equal(rail.items[7].attributes.get('aria-pressed'), 'true');

    instance.select(rail.items[0]); flush();
    assert.equal(rail.scrollLeft, 0);
});
