const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const {FakeElement, FakeEvent} = require('./fake-dom.cjs');

function gallery(width = 393, count = 3) {
    const document = new FakeElement('document');
    const modal = new FakeElement('dialog', {id: 'destination-image-modal'});
    const image = new FakeElement('img');
    const close = new FakeElement('button');
    const nav = new FakeElement('button');
    const sources = Array.from({length: count}, (_, i) => Object.assign(new FakeElement('img'), {src: `photo-${i}`, alt: `Photo ${i}`}));
    document.body = {style: {overflow: ''}};
    document.activeElement = sources[0];
    document.getElementById = id => id === 'destination-image-modal' ? modal : null;
    document.querySelectorAll = () => sources;
    modal.querySelector = selector => selector === '.image-modal-img' ? image : close;
    modal.contains = target => [image, close, nav].includes(target);
    image.getBoundingClientRect = () => ({left: 10, width: 300});
    for (const el of [image, close, nav, ...sources]) {
        el.isConnected = true;
        el.focus = () => { document.activeElement = el; };
    }
    image.closest = selector => selector === '.image-modal-img' ? image : null;
    close.closest = selector => selector === '.image-modal-close' ? close : null;
    nav.closest = selector => selector === '.image-modal-nav' ? nav : null;
    sources.forEach(source => { source.closest = selector => selector.includes('.carousel') ? source : null; });
    modal.showModal = () => { modal.open = true; document.activeElement = close; };
    modal.close = () => {
        modal.open = false;
        document.activeElement = close;
        const event = new FakeEvent('close'); event.target = modal; document.dispatchEvent(event);
    };
    const window = {matchMedia: () => ({matches: width <= 600})};
    const CustomEvent = class extends FakeEvent { constructor(type, options) { super(type); this.detail = options.detail; } };
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/destination-image-modal.js', 'utf8'), {document, window, CustomEvent});
    function send(type, target = image, options = {}) {
        const event = Object.assign(new FakeEvent(type), {target, pointerId: 1, isPrimary: true, button: 0, clientX: 160, clientY: 100, stopPropagation() {this.stopped = true;}}, options);
        document.dispatchEvent(event);
        return event;
    }
    send('click', sources[0]);
    return {document, modal, image, close, nav, sources, send};
}

test('mobile image taps navigate circularly and the center never closes the dialog', () => {
    const g = gallery();
    assert.equal(g.send('click', g.image, {clientX: 160}).stopped, true);
    assert.equal(g.modal.open, true);
    assert.equal(g.image.src, 'photo-0');
    g.send('click', g.image, {clientX: 295});
    assert.equal(g.image.src, 'photo-1');
    g.send('click', g.image, {clientX: 25});
    g.send('click', g.image, {clientX: 25});
    assert.equal(g.image.src, 'photo-2');
});

test('horizontal swipe moves once; its following click, vertical gesture and jitter do not navigate', () => {
    const g = gallery();
    g.send('pointerdown', g.image, {clientX: 250});
    g.send('pointerup', g.image, {clientX: 80, clientY: 105});
    assert.equal(g.image.src, 'photo-1');
    g.send('click', g.image, {clientX: 80});
    assert.equal(g.image.src, 'photo-1');
    g.send('pointerdown', g.image, {clientX: 80});
    g.send('pointerup', g.image, {clientX: 250});
    assert.equal(g.image.src, 'photo-0');
    g.send('pointerdown');
    g.send('pointerup', g.image, {clientX: 165, clientY: 105});
    assert.equal(g.image.src, 'photo-0');
    g.send('pointerdown');
    g.send('pointerup', g.image, {clientX: 180, clientY: 220});
    g.send('click');
    assert.equal(g.image.src, 'photo-0');
    assert.equal(g.modal.open, true);
});

test('single image ignores navigation; close and Escape restore focus before aria-hidden', () => {
    const g = gallery(360, 1);
    const setAttribute = g.modal.setAttribute.bind(g.modal);
    g.modal.setAttribute = (name, value) => {
        if (name === 'aria-hidden') assert.notEqual(g.document.activeElement, g.close);
        setAttribute(name, value);
    };
    g.send('click', g.image, {clientX: 295});
    g.send('pointerdown', g.image, {clientX: 250});
    g.send('pointerup', g.image, {clientX: 80});
    assert.equal(g.modal.open, true);
    assert.equal(g.image.src, 'photo-0');
    g.send('click', g.close);
    assert.equal(g.document.activeElement, g.sources[0]);
    assert.equal(g.modal.open, false);
    g.send('click', g.sources[0]);
    g.send('keydown', g.image, {key: 'Escape'});
    assert.equal(g.modal.open, false);
    assert.equal(g.document.activeElement, g.sources[0]);
});

test('real backdrop closes; tablet and desktop retain image click close, arrows and keyboard', () => {
    const mobile = gallery();
    mobile.send('click', mobile.modal);
    assert.equal(mobile.modal.open, false);
    for (const width of [768, 1440]) {
        const g = gallery(width);
        g.send('click', g.nav);
        assert.equal(g.image.src, 'photo-1');
        g.send('keydown', g.image, {key: 'ArrowRight'});
        assert.equal(g.image.src, 'photo-2');
        g.send('click', g.image);
        assert.equal(g.modal.open, false);
    }
});

test('after navigation focus returns to the currently visible carousel image', () => {
    const g = gallery();
    g.send('click', g.image, {clientX: 295});
    g.send('click', g.close);
    assert.equal(g.document.activeElement, g.sources[1]);
    assert.equal(g.sources[1].getAttribute('tabindex'), '-1');
});
