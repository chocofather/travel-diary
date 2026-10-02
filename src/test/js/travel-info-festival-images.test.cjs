const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const {FakeElement} = require('./fake-dom.cjs');

class Image extends FakeElement {
    constructor(width, height, festival = true, complete = true) {
        super('img');
        Object.assign(this, {naturalWidth: width, naturalHeight: height, festival, complete});
    }
    matches() { return this.festival; }
}

function list(images) {
    const listeners = {};
    const document = {
        querySelector: () => null,
        querySelectorAll: selector => selector.includes('.is-festival') ? images.filter(i => i.festival) : [],
        addEventListener: (name, handler, capture) => { listeners[name] = {handler, capture}; }
    };
    const window = {location: {href: 'https://example.test/travel-info?contentType=FESTIVAL'}, addEventListener: () => {}};
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/travel-info-list.js', 'utf8'),
        {document, window, URL, HTMLImageElement: Image});
    return listeners;
}

test('cached festival images distinguish posters from photos without changing GENERAL images', () => {
    const portrait = new Image(443, 627), threshold = new Image(500, 600);
    const landscape = new Image(940, 587), general = new Image(940, 587, false);
    list([portrait, threshold, landscape, general]);
    assert.equal(portrait.classList.contains('is-landscape'), false);
    assert.equal(threshold.classList.contains('is-landscape'), false);
    assert.equal(landscape.classList.contains('is-landscape'), true);
    assert.equal(general.classList.contains('is-landscape'), false);
});

test('captured load handles lazy and newly inserted images and clears an outdated photo class', () => {
    const listeners = list([]);
    assert.equal(listeners.load.capture, true);
    const image = new Image(940, 626);
    listeners.load.handler({target: image});
    assert.equal(image.classList.contains('is-landscape'), true);
    image.naturalWidth = 443;
    image.naturalHeight = 627;
    listeners.load.handler({target: image});
    assert.equal(image.classList.contains('is-landscape'), false);
    const unloaded = new Image(0, 0);
    listeners.load.handler({target: unloaded});
    assert.equal(unloaded.classList.contains('is-landscape'), false);
});
