const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function setup() {
    const media = {matches: true, addEventListener() {}};
    const timers = [];
    const window = {matchMedia: () => media, setTimeout: callback => timers.push(callback)};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname,
        '../../main/resources/static/js/community-comment-mobile.js'), 'utf8'), {window});
    const listeners = {};
    const image = {
        addEventListener: (name, callback) => { listeners[name] = callback; },
        getBoundingClientRect: () => ({left: 16, width: 328}),
        closest: () => null
    };
    const modal = {closest: () => null};
    const moves = [];
    let closes = 0;
    const navigation = window.CommunityCommentMobile.bindImageNavigation({
        modal, image, show: offset => moves.push(offset), close: () => closes++
    });
    return {media, timers, image, modal, listeners, moves, navigation, closes: () => closes};
}

test('mobile taps navigate without dismissing, overlay stays open and desktop delegates unchanged', () => {
    const state = setup();
    state.navigation.handleClick({target: state.image, clientX: 30});
    state.navigation.handleClick({target: state.image, clientX: 330});
    state.navigation.handleClick({target: state.modal});
    assert.deepEqual(state.moves, [-1, 1]);
    assert.equal(state.closes(), 0);
    state.navigation.handleClick({target: {closest: () => ({className: 'close-btn'})}});
    assert.equal(state.closes(), 1);
    state.media.matches = false;
    assert.equal(state.navigation.handleClick({target: state.image}), false);
    assert.deepEqual(state.moves, [-1, 1]);
});

test('horizontal swipe advances once, suppresses its click and ignores vertical gestures', () => {
    const state = setup();
    state.listeners.pointerdown({pointerId: 1, clientX: 260, clientY: 100});
    state.listeners.pointerup({pointerId: 1, clientX: 60, clientY: 110});
    state.navigation.handleClick({target: state.image, clientX: 60});
    assert.deepEqual(state.moves, [1]);
    state.timers.forEach(callback => callback());
    state.listeners.pointerdown({pointerId: 1, clientX: 100, clientY: 100});
    state.listeners.pointerup({pointerId: 1, clientX: 150, clientY: 220});
    assert.deepEqual(state.moves, [1]);
    state.listeners.pointerdown({pointerId: 2, clientX: 60, clientY: 100});
    state.listeners.pointerup({pointerId: 2, clientX: 260, clientY: 110});
    assert.deepEqual(state.moves, [1, -1]);
});
