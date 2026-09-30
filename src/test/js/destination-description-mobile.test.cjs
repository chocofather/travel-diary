const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const {FakeElement, FakeEvent} = require('./fake-dom.cjs');

function introduction(height, width = 393) {
    const description = new FakeElement('div');
    description.textContent = '원본 전체 본문과 문단';
    description.scrollHeight = height;
    description.clientHeight = 147;
    description.getBoundingClientRect = () => ({top: 100});
    const toggle = new FakeElement('button', {'aria-expanded': 'false', 'data-expand-label': '더보기', 'data-collapse-label': '접기'});
    const document = {getElementById: id => id === 'destination-description' ? description : toggle};
    const window = new FakeElement('window');
    window.innerWidth = width;
    window.matchMedia = () => ({get matches() {return window.innerWidth <= 600;}});
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/destination-description-mobile.js', 'utf8'), {document, window, getComputedStyle: () => ({lineHeight: '24.5px'})});
    return {description, toggle, window};
}

test('long introduction toggles without replacing or shortening its original text', () => {
    const d = introduction(600);
    assert.equal(d.toggle.hidden, false);
    assert.equal(d.description.classList.contains('is-collapsible'), true);
    assert.equal(d.description.classList.contains('is-expanded'), false);
    d.toggle.click();
    assert.equal(d.toggle.getAttribute('aria-expanded'), 'true');
    assert.equal(d.toggle.textContent, '접기');
    assert.equal(d.description.classList.contains('is-expanded'), true);
    d.toggle.click();
    assert.equal(d.toggle.textContent, '더보기');
    assert.equal(d.description.classList.contains('is-expanded'), false);
    assert.equal(d.description.textContent, '원본 전체 본문과 문단');
});

test('six-line introduction has no toggle; tablet/desktop stay full and resize resets safely', () => {
    assert.equal(introduction(147).toggle.hidden, true);
    for (const width of [768, 1440]) {
        const d = introduction(600, width);
        assert.equal(d.toggle.hidden, true);
        assert.equal(d.description.classList.contains('is-collapsible'), false);
    }
    const d = introduction(600);
    d.toggle.click();
    d.window.innerWidth = 768;
    d.window.dispatchEvent(new FakeEvent('resize'));
    assert.equal(d.toggle.hidden, true);
    assert.equal(d.toggle.getAttribute('aria-expanded'), 'false');
    d.window.innerWidth = 360;
    d.window.dispatchEvent(new FakeEvent('resize'));
    assert.equal(d.toggle.hidden, false);
    assert.equal(d.description.classList.contains('is-expanded'), false);
});
