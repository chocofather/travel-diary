// 관리자 화면 스크립트를 Node에서 실제로 실행하기 위한 작은 가짜 DOM.
// jsdom 없이 테스트 대상 스크립트가 쓰는 DOM 기능만 흉내 낸다.

function kebab(key) {
  return key.replace(/[A-Z]/g, letter => `-${letter.toLowerCase()}`);
}

class FakeEvent {
  constructor(type, options = {}) {
    this.type = type;
    this.bubbles = Boolean(options.bubbles);
    this.target = null;
    this.defaultPrevented = false;
  }

  preventDefault() {
    this.defaultPrevented = true;
  }
}

class FakeElement {
  constructor(tag, attributes = {}) {
    this.tagName = tag.toUpperCase();
    this.attributes = new Map(Object.entries(attributes).map(([key, value]) => [key, String(value)]));
    this.children = [];
    this.parent = null;
    this.listeners = {};
    this.textContent = '';
    this.value = attributes.value ?? '';
    this.hidden = false;
    this.disabled = false;
    this.checked = false;
    this.maxLength = -1;
    this.className = '';
    const classes = new Set();
    this.classList = {
      toggle: (name, force) => {
        const on = force === undefined ? !classes.has(name) : Boolean(force);
        if (on) classes.add(name); else classes.delete(name);
        return on;
      },
      add: name => classes.add(name),
      remove: name => classes.delete(name),
      contains: name => classes.has(name) || this.className.split(/\s+/).includes(name)
    };
    this.dataset = new Proxy({}, {
      get: (_, key) => this.attributes.get(`data-${kebab(String(key))}`),
      set: (_, key, value) => {
        this.attributes.set(`data-${kebab(String(key))}`, String(value));
        return true;
      },
      deleteProperty: (_, key) => this.attributes.delete(`data-${kebab(String(key))}`),
      has: (_, key) => this.attributes.has(`data-${kebab(String(key))}`)
    });
  }

  get name() {
    return this.getAttribute('name') ?? '';
  }

  getAttribute(name) {
    return this.attributes.has(name) ? this.attributes.get(name) : null;
  }

  setAttribute(name, value) {
    this.attributes.set(name, String(value));
  }

  removeAttribute(name) {
    this.attributes.delete(name);
  }

  append(...nodes) {
    for (const node of nodes) {
      const child = typeof node === 'string' ? Object.assign(new FakeElement('#text'), {textContent: node}) : node;
      child.parent = this;
      this.children.push(child);
    }
  }

  after(...nodes) {
    if (!this.parent) return;
    const siblings = this.parent.children;
    const index = siblings.indexOf(this);
    for (const [offset, node] of nodes.entries()) {
      const child = typeof node === 'string' ? Object.assign(new FakeElement('#text'), {textContent: node}) : node;
      child.parent = this.parent;
      siblings.splice(index + 1 + offset, 0, child);
    }
  }

  replaceChildren(...nodes) {
    this.children = [];
    this.append(...nodes);
  }

  remove() {
    if (this.parent) this.parent.children = this.parent.children.filter(child => child !== this);
  }

  addEventListener(type, listener) {
    (this.listeners[type] ||= []).push(listener);
  }

  dispatchEvent(event) {
    if (!event.target) event.target = this;
    for (const listener of this.listeners[event.type] || []) listener(event);
    if (event.bubbles && this.parent) this.parent.dispatchEvent(event);
    return !event.defaultPrevented;
  }

  click() {
    this.dispatchEvent(new FakeEvent('click', {bubbles: true}));
  }

  closest() {
    return null;
  }

  descendants() {
    return this.children.flatMap(child => [child, ...child.descendants()]);
  }

  matches(selector) {
    const match = /^([a-z]+)?(?:#([\w-]+))?(?:\[([\w-]+)(?:="?([^"\]]*)"?)?\])?$/i.exec(selector.trim());
    if (!match) throw new Error(`unsupported selector ${selector}`);
    const [, tag, id, attribute, value] = match;
    if (tag && this.tagName !== tag.toUpperCase()) return false;
    if (id && this.getAttribute('id') !== id) return false;
    if (attribute && !this.attributes.has(attribute)) return false;
    return value === undefined || this.getAttribute(attribute) === value;
  }

  querySelectorAll(selector) {
    return this.descendants().filter(node => node.matches(selector));
  }

  querySelector(selector) {
    return this.querySelectorAll(selector)[0] ?? null;
  }

  get elements() {
    const controls = this.descendants().filter(node => node.getAttribute('name'));
    controls.namedItem = name => controls.find(node => node.getAttribute('name') === name) ?? null;
    return controls;
  }

  text() {
    return [this.textContent, ...this.children.map(child => child.text())].join(' ');
  }
}

// 대기 중인 Promise와 setTimeout(0) 작업(제출 잠금 등)이 모두 끝날 때까지 기다린다.
async function flush() {
  for (let i = 0; i < 10; i++) await new Promise(resolve => setImmediate(resolve));
  await new Promise(resolve => setTimeout(resolve, 5));
  for (let i = 0; i < 5; i++) await new Promise(resolve => setImmediate(resolve));
}

module.exports = {FakeEvent, FakeElement, flush};
