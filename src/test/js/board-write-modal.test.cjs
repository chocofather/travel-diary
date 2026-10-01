const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

test('mobile and desktop write actions open the same chooser and Escape closes it', () => {
    const documentListeners = {};
    let focusCount = 0;
    const modal = {
        hidden: true,
        querySelector: () => ({focus: () => focusCount++}),
        addEventListener() {}
    };
    const buttons = Array.from({length: 2}, () => ({
        listeners: {},
        addEventListener(event, listener) { this.listeners[event] = listener; }
    }));
    const document = {
        getElementById: () => modal,
        querySelectorAll: () => buttons,
        addEventListener: (event, listener) => { documentListeners[event] = listener; }
    };
    vm.runInNewContext(fs.readFileSync(path.join(__dirname,
        '../../main/resources/static/js/board-write-modal.js'), 'utf8'), {document});
    documentListeners.DOMContentLoaded();
    for (const button of buttons) {
        button.listeners.click();
        assert.equal(modal.hidden, false);
        documentListeners.keydown({key: 'Escape'});
        assert.equal(modal.hidden, true);
    }
    assert.equal(focusCount, 2);
});
