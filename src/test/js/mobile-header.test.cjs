const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const {FakeElement, FakeEvent} = require('./fake-dom.cjs');

function header(width = 390) {
    const document = new FakeElement('document');
    const box = new FakeElement('div');
    const toggle = new FakeElement('button', {'data-open-label': '검색 열기', 'data-close-label': '검색 닫기'});
    const form = new FakeElement('form');
    const input = new FakeElement('input', {name: 'q'});
    const clear = new FakeElement('button');
    const menuToggle = new FakeElement('button');
    const menu = new FakeElement('nav');
    menu.hidden = true;
    form.append(input, clear);
    box.append(toggle, form, menuToggle);
    document.append(box, menu);
    for (const el of [document, box, toggle, form, input, menuToggle, menu]) {
        el.contains = target => el === target || el.descendants().includes(target);
        el.focus = () => { document.activeElement = el; };
    }
    toggle.closest = () => box;
    form.querySelector = selector => selector === '.search-clear' ? clear : input;
    const elements = {'search-toggle': toggle, 'search-form': form, 'site-menu-toggle': menuToggle, 'site-menu': menu};
    document.getElementById = id => elements[id] ?? null;
    const window = new FakeElement('window');
    window.innerWidth = width;
    window.location = {pathname: '/'};
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/main.js', 'utf8'), {document, window});
    document.dispatchEvent(new FakeEvent('DOMContentLoaded'));
    return {document, window, toggle, form, input, menuToggle, menu};
}

test('mobile search and menu are exclusive in both directions, and search toggles closed', () => {
    const h = header();
    h.toggle.click();
    assert.equal(h.form.classList.contains('open'), true);
    h.menuToggle.click();
    assert.equal(h.menu.hidden, false);
    assert.equal(h.form.classList.contains('open'), false);
    h.toggle.click();
    assert.equal(h.menu.hidden, true);
    assert.equal(h.form.classList.contains('open'), true);
    h.toggle.click();
    assert.equal(h.form.classList.contains('open'), false);
});

test('mobile Escape restores search focus and crossing the breakpoint clears open states', () => {
    const h = header();
    h.toggle.click();
    const escape = new FakeEvent('keydown');
    escape.key = 'Escape';
    h.document.dispatchEvent(escape);
    assert.equal(h.form.classList.contains('open'), false);
    assert.equal(h.document.activeElement, h.toggle);
    h.toggle.click();
    h.window.innerWidth = 1440;
    h.window.dispatchEvent(new FakeEvent('resize'));
    assert.equal(h.form.classList.contains('open'), false);
    assert.equal(h.menu.hidden, true);
    // Desktop resizes must not dismiss the existing inline search.
    h.toggle.click();
    h.window.innerWidth = 1500;
    h.window.dispatchEvent(new FakeEvent('resize'));
    assert.equal(h.form.classList.contains('open'), true);
});

test('keyword submit remains native; blank submit closes without navigating', () => {
    const h = header();
    h.toggle.click();
    h.input.value = '제주';
    assert.equal(h.form.dispatchEvent(new FakeEvent('submit')), true);
    h.input.value = '  ';
    assert.equal(h.form.dispatchEvent(new FakeEvent('submit')), false);
    assert.equal(h.input.value, '');
    assert.equal(h.form.classList.contains('open'), false);
});
