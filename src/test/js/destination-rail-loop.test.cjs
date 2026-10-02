const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');

function fixture(key = 'regionId', complete = false) {
    class Classes extends Set {
        contains(name) { return this.has(name); }
        add(name) { super.add(name); }
        remove(name) { this.delete(name); }
        toggle(name, on) { on ? this.add(name) : this.delete(name); }
    }
    class Item {
        constructor(id) {
            this.dataset = {[key]: String(id)};
            this.attributes = new Map([['id', `region-${id}`]]);
            this.classList = new Classes();
            this.width = 70;
        }
        setAttribute(k, v) { this.attributes.set(k, v); }
        removeAttribute(k) { this.attributes.delete(k); }
        hasAttribute(k) { return this.attributes.has(k); }
        matches(selector) { return selector.includes('[tabindex]') && this.tabIndex !== undefined; }
        querySelectorAll() { return []; }
        cloneNode() {
            const copy = new Item(this.dataset[key]);
            copy.attributes = new Map(this.attributes);
            copy.classList = new Classes(this.classList);
            copy.tabIndex = this.tabIndex;
            return copy;
        }
        getBoundingClientRect() {
            const left = rail.children.indexOf(this) * 80 - rail.scrollLeft;
            return {left, right: left + this.width, width: this.width};
        }
        remove() { rail.children.splice(rail.children.indexOf(this), 1); }
        closest() { return rail; }
        focus() { context.document.activeElement = this; }
    }
    const listeners = {};
    const rail = {
        children: [new Item(1), new Item(2), new Item(3)],
        classList: new Classes(), clientWidth: 160, scrollLeft: 0, isConnected: true,
        querySelectorAll(selector) { return this.children.filter(e => selector === '[data-rail-clone]' ? e.hasAttribute('data-rail-clone') : true); },
        getBoundingClientRect() { return {left: 0}; },
        prepend(fragment) { this.children.unshift(...fragment.children); },
        append(fragment) { this.children.push(...fragment.children); },
        addEventListener(name, handler) { listeners[name] = handler; },
        matches() { return complete; },
        style: {width: ''},
        parentElement: {clientWidth: 250, style: {setProperty(k, v) { this[k] = v; }, removeProperty(k) { delete this[k]; }}}
    };
    if (complete) Object.defineProperty(rail, 'clientWidth', {get() { return parseFloat(this.style.width) || 160; }});
    rail.children[2].classList.add('selected');
    const observers = [];
    const window = {matchMedia(query) { return {matches: query.includes('reduced-motion') || complete}; }};
    const context = {window, document: {
        addEventListener() {},
        createDocumentFragment() { return {children: [], append(item) { this.children.push(item); }}; }
    }, getComputedStyle() { return {columnGap: '10px', paddingLeft: '20px', paddingRight: '20px'}; }, cancelAnimationFrame() {}, setTimeout() {}, clearTimeout() {},
    ResizeObserver: class { constructor(callback) { this.callback = callback; observers.push(this); } observe() {} disconnect() { this.disconnected = true; } }};
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/destination-rail-loop.js', 'utf8'), context);
    const loop = window.DestinationRailLoop.create(rail, rail, '.item', key);
    return {rail, loop, api: window.DestinationRailLoop, listeners, observers, document: context.document};
}

test('both region keys keep bounded clones, canonical selection and one accessible set', () => {
    for (const key of ['regionId', 'cityId']) {
        const {rail, loop, api, document} = fixture(key);
        assert.equal(rail.children.length, 9);
        const clones = rail.children.filter(e => e.hasAttribute('data-rail-clone'));
        assert.ok(clones.every(e => e.attributes.get('aria-hidden') === 'true' && e.tabIndex === -1 && !e.hasAttribute('id')));
        const clicked = clones.find(e => e.dataset[key] === '2');
        document.activeElement = clicked;
        const original = api.original(clicked);
        assert.equal(document.activeElement, original);
        assert.ok(!original.hasAttribute('data-rail-clone'));
        assert.equal(api.select(clicked), original);
        assert.equal(rail.children.filter(e => e.classList.contains('selected')).length, 3);
        for (let i = 0; i < 20; i++) loop.rebuild();
        assert.equal(rail.children.length, 9);
    }
});

test('upper desktop rail fits whole slots and settles movement on item boundaries', () => {
    const {rail, loop, listeners, observers} = fixture('regionId', true);
    assert.equal(rail.clientWidth, 150); // 2 * 70 + 10; available width is 210.
    assert.equal(rail.parentElement.style['--region-rail-inset'], '30px');
    assert.equal(rail.scrollLeft % 80, 0);
    for (let i = 0; i < 40; i++) loop.move(i < 20 ? 1 : -1);
    assert.equal(rail.scrollLeft % 80, 0);
    rail.scrollLeft += 27;
    listeners.scrollend();
    assert.equal(rail.scrollLeft % 80, 0);
    rail.parentElement.clientWidth = 340;
    observers[0].callback();
    assert.equal(rail.clientWidth, 230);
    assert.equal(rail.parentElement.style['--region-rail-inset'], '35px');
    assert.equal(rail.scrollLeft % 80, 0);
    assert.equal(rail.children.length, 9);
});

test('forward/reverse wrap, resize and detached rail cleanup preserve finite state', () => {
    const {rail, loop, api, listeners, observers} = fixture();
    for (let i = 0; i < 50; i++) loop.move(1, 150);
    assert.ok(rail.scrollLeft >= 240 && rail.scrollLeft < 480);
    for (let i = 0; i < 50; i++) loop.move(-1, 150);
    assert.ok(rail.scrollLeft >= 240 && rail.scrollLeft < 480);
    rail.scrollLeft = 0; listeners.scroll();
    assert.equal(rail.scrollLeft, 240);
    rail.clientWidth = 500; observers[0].callback();
    assert.equal(rail.children.length, 21);
    rail.clientWidth = 160; observers[0].callback();
    assert.equal(rail.children.length, 9);
    rail.isConnected = false;
    api.create(null, null, '.item', 'regionId');
    assert.equal(observers[0].disconnected, true);
});
