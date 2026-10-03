/**
 * 관리자 여행정보 구조화 콘텐츠(STRUCTURED) 블록 에디터.
 *
 * <p>정해진 블록 6종을 위에서 아래로 쌓는다. 자유 배치·드래그는 없고 위/아래 버튼으로 순서를 바꾼다.
 * 화면 상태는 하나의 model({version: 1, blocks: [...]})이 갖고, 모든 추가·삭제·복제·이동·입력·이미지 업로드가
 * 이 model 을 바꾼다. 저장 직전에 model 을 JSON 으로 써서 hidden 칸(structuredContent)에 넣는다.
 * 서버가 같은 JSON 을 다시 엄격하게 읽고 검사하므로, 여기의 검사는 미리 알려 주는 용도다.
 *
 * <p>처음 model 은 같은 hidden 칸의 값이다. 수정 화면이면 저장된 글, 저장이 거부되어 다시 그려진 화면이면
 * 방금 보낸 값이라 작성하던 블록·글·이미지·순서가 그대로 돌아온다.
 *
 * <p>사용자 글은 모두 textContent / value 로만 넣는다. (innerHTML 로 그리지 않는다)
 */
(function () {
    'use strict';

    const UPLOAD_URL = '/admin/api/travel-info/content-images';
    const IMAGE_URL_PREFIX = '/uploads/travel-info/content/';
    const IMAGE_ACCEPT = '.jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp';

    /** 서버 StructuredContentValidator 와 같은 한도. (StructuredEditorLimitsContractTest 가 맞는지 본다) */
    const LIMITS = Object.freeze({
        blocks: 60,
        sliderItems: 20,
        title: 120,
        richText: 5000,
        imageText: 3000,
        caption: 300,
        alt: 200,
        itemTitle: 100,
        callout: 500
    });

    const TYPE_LABELS = Object.freeze({
        SECTION_TITLE: '섹션 제목',
        RICH_TEXT: '일반 글',
        FULL_IMAGE: '큰 이미지',
        IMAGE_TEXT: '이미지 + 글',
        IMAGE_SLIDER: '이미지 슬라이더',
        IMAGE_GRID: '이미지 배치',
        CALLOUT: '강조 문구'
    });

    /** 이미지 배치 블록의 칸 수. (서버 StructuredContentValidator.GRID_COLUMNS 와 같다) */
    const GRID_COLUMNS = Object.freeze([2, 3]);

    const ADD_OPTIONS = Object.freeze([
        {label: '섹션 제목', type: 'SECTION_TITLE'},
        {label: '일반 글', type: 'RICH_TEXT'},
        {label: '큰 이미지', type: 'FULL_IMAGE'},
        {label: '이미지 왼쪽 + 글 오른쪽', type: 'IMAGE_TEXT', imagePosition: 'LEFT'},
        {label: '글 왼쪽 + 이미지 오른쪽', type: 'IMAGE_TEXT', imagePosition: 'RIGHT'},
        {label: '이미지 슬라이더', type: 'IMAGE_SLIDER'},
        {label: '이미지 2장 배치', type: 'IMAGE_GRID', columns: 2},
        {label: '이미지 3장 배치', type: 'IMAGE_GRID', columns: 3},
        {label: '강조 문구', type: 'CALLOUT'}
    ]);

    // ---- 작은 도우미 ----------------------------------------------------------------------

    function el(tag, attributes = {}, children = []) {
        const element = document.createElement(tag);
        Object.entries(attributes).forEach(([name, value]) => {
            if (value === null || value === undefined || value === false) return;
            if (name === 'text') element.textContent = value;
            else if (name === 'className') element.className = value;
            else if (name.startsWith('on')) element.addEventListener(name.slice(2), value);
            else element.setAttribute(name, value === true ? '' : value);
        });
        (Array.isArray(children) ? children : [children])
            .filter(child => child !== null && child !== undefined && child !== false)
            .forEach(child => element.append(child));
        return element;
    }

    function text(value) {
        return typeof value === 'string' ? value : '';
    }

    function blank(value) {
        return text(value).trim() === '';
    }

    /** 짧고 겹치지 않는 식별자. 서버 규칙([A-Za-z0-9_-], 40자 이하)에 맞는 'b-' / 'i-' + 영숫자 10자. */
    function randomPart() {
        const bytes = new Uint8Array(10);
        window.crypto.getRandomValues(bytes);
        return Array.from(bytes, byte => (byte % 36).toString(36)).join('');
    }

    function newId(prefix, used) {
        let id;
        do {
            id = `${prefix}-${randomPart()}`;
        } while (used.has(id));
        used.add(id);
        return id;
    }

    function csrfHeaders() {
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;
        return token && header ? {[header]: token} : {};
    }

    /** 서버가 돌려준 값만 이미지로 받는다. 크기는 서버가 잰 값이다. */
    function readImage(value) {
        if (!value || typeof value.url !== 'string' || !value.url.startsWith(IMAGE_URL_PREFIX)) return null;
        const width = Number(value.width);
        const height = Number(value.height);
        if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1) return null;
        return {url: value.url, width, height};
    }

    async function uploadImage(file) {
        const body = new FormData();
        body.append('image', file);
        let response;
        try {
            response = await fetch(UPLOAD_URL, {
                method: 'POST', credentials: 'same-origin', headers: csrfHeaders(), body
            });
        } catch {
            throw new Error('이미지를 올리지 못했습니다. 네트워크 상태를 확인한 뒤 다시 시도해 주세요.');
        }
        let data = {};
        try {
            data = await response.json();
        } catch {
            data = {};
        }
        const image = response.ok ? readImage(data) : null;
        if (!image) {
            throw new Error(typeof data.message === 'string' && data.message
                ? data.message
                : '이미지를 올리지 못했습니다. 잠시 후 다시 시도해 주세요.');
        }
        return image;
    }

    /** 블록 카드·확인 창에 쓰는 이름. 이미지 배치는 장수까지 보여 준다. */
    function blockLabel(block) {
        return block.type === 'IMAGE_GRID' ? `이미지 ${block.columns}장 배치` : TYPE_LABELS[block.type];
    }

    // ---- model --------------------------------------------------------------------------

    function emptyImageItem(itemIds) {
        return {id: newId('i', itemIds), image: null, alt: '', title: '', caption: ''};
    }

    function readImageItem(item) {
        return {id: item.id, image: readImage(item.image), alt: text(item.alt),
            title: text(item.title), caption: text(item.caption)};
    }

    /** 슬라이더·이미지 배치의 사진 한 장. 서버 정규 JSON 과 같은 순서(id, image, alt, title, caption). */
    function writeImageItem(item) {
        const written = {id: item.id};
        if (item.image) written.image = {url: item.image.url, width: item.image.width, height: item.image.height};
        if (!blank(item.alt)) written.alt = item.alt;
        if (!blank(item.title)) written.title = item.title;
        if (!blank(item.caption)) written.caption = item.caption;
        return written;
    }

    function emptyBlock(option, usedIds) {
        const id = newId('b', usedIds);
        switch (option.type) {
            case 'SECTION_TITLE': return {id, type: 'SECTION_TITLE', title: ''};
            case 'RICH_TEXT': return {id, type: 'RICH_TEXT', layout: 'DEFAULT', text: ''};
            case 'FULL_IMAGE': return {id, type: 'FULL_IMAGE', image: null, alt: '', caption: ''};
            case 'IMAGE_TEXT':
                return {id, type: 'IMAGE_TEXT', imagePosition: option.imagePosition === 'RIGHT' ? 'RIGHT' : 'LEFT',
                    image: null, alt: '', title: '', text: ''};
            case 'IMAGE_SLIDER': return {id, type: 'IMAGE_SLIDER', title: '', items: []};
            case 'IMAGE_GRID': {
                // 장수가 정해진 블록이라 빈 칸을 미리 만들어 둔다.
                const columns = option.columns === 3 ? 3 : 2;
                const itemIds = new Set();
                return {id, type: 'IMAGE_GRID', columns,
                    items: Array.from({length: columns}, () => emptyImageItem(itemIds))};
            }
            default: return {id, type: 'CALLOUT', text: ''};
        }
    }

    /**
     * 저장된 JSON 한 블록을 화면 model 로. 모르는 블록이면 null.
     * 예전 섹션 제목의 짧은 소개(lead)는 읽지 않는다. (다시 저장하면 빠진다. 서버도 같은 방식으로 버린다)
     * 본문 폭(layout)이 없던 예전 일반 글은 기본(DEFAULT)이다.
     */
    function readBlock(raw) {
        if (!raw || typeof raw !== 'object' || typeof raw.id !== 'string') return null;
        const id = raw.id;
        switch (raw.type) {
            case 'SECTION_TITLE': return {id, type: raw.type, title: text(raw.title)};
            case 'RICH_TEXT':
                return {id, type: raw.type, layout: raw.layout === 'FOCUSED' ? 'FOCUSED' : 'DEFAULT', text: text(raw.text)};
            case 'FULL_IMAGE':
                return {id, type: raw.type, image: readImage(raw.image), alt: text(raw.alt), caption: text(raw.caption)};
            case 'IMAGE_TEXT':
                return {id, type: raw.type, imagePosition: raw.imagePosition === 'RIGHT' ? 'RIGHT' : 'LEFT',
                    image: readImage(raw.image), alt: text(raw.alt), title: text(raw.title), text: text(raw.text)};
            case 'IMAGE_SLIDER':
                return {id, type: raw.type, title: text(raw.title),
                    items: (Array.isArray(raw.items) ? raw.items : [])
                        .filter(item => item && typeof item.id === 'string')
                        .map(readImageItem)};
            case 'IMAGE_GRID': {
                // 칸 수와 장수가 맞지 않는 값은 화면에서 고칠 수 없으므로 읽지 못한 블록으로 둔다.
                const items = Array.isArray(raw.items) ? raw.items.filter(item => item && typeof item.id === 'string') : [];
                if (!GRID_COLUMNS.includes(raw.columns) || items.length !== raw.columns) return null;
                return {id, type: raw.type, columns: raw.columns, items: items.map(readImageItem)};
            }
            case 'CALLOUT': return {id, type: raw.type, text: text(raw.text)};
            default: return null;
        }
    }

    /** 값이 있는 칸만, 서버 정규 JSON 과 같은 순서(type, id, …)로 쓴다. */
    function writeBlock(block) {
        const out = {type: block.type, id: block.id};
        const put = (key, value) => {
            if (!blank(value)) out[key] = value;
        };
        const putImage = image => {
            if (image) out.image = {url: image.url, width: image.width, height: image.height};
        };
        switch (block.type) {
            case 'SECTION_TITLE': put('title', block.title); break;
            case 'RICH_TEXT': out.layout = block.layout; put('text', block.text); break;
            case 'FULL_IMAGE': putImage(block.image); put('alt', block.alt); put('caption', block.caption); break;
            case 'IMAGE_TEXT':
                out.imagePosition = block.imagePosition;
                putImage(block.image); put('alt', block.alt); put('title', block.title); put('text', block.text);
                break;
            case 'IMAGE_SLIDER':
                put('title', block.title);
                out.items = block.items.map(writeImageItem);
                break;
            case 'IMAGE_GRID':
                out.columns = block.columns;
                out.items = block.items.map(writeImageItem);
                break;
            default: put('text', block.text);
        }
        return out;
    }

    function blockHasContent(block) {
        switch (block.type) {
            case 'SECTION_TITLE': return !blank(block.title);
            case 'IMAGE_SLIDER': return !blank(block.title) || block.items.length > 0;
            case 'IMAGE_GRID':
                return block.items.some(item => item.image || !blank(item.title) || !blank(item.caption) || !blank(item.alt));
            case 'FULL_IMAGE': return !!block.image || !blank(block.alt) || !blank(block.caption);
            case 'IMAGE_TEXT':
                return !!block.image || !blank(block.alt) || !blank(block.title) || !blank(block.text);
            default: return !blank(block.text);
        }
    }

    function blockSummary(block) {
        if (block.type === 'IMAGE_SLIDER') {
            const title = text(block.title).trim();
            return `${title ? title + ' · ' : ''}이미지 ${block.items.length}장`;
        }
        if (block.type === 'IMAGE_GRID') {
            return `이미지 ${block.items.filter(item => item.image).length} / ${block.columns}장`;
        }
        const value = text(block.title || block.text || block.alt).trim().split('\n')[0];
        return value.length > 60 ? value.slice(0, 60) + '…' : value;
    }

    // ---- 검사 (서버 규칙과 같은 필수값. 최종 기준은 서버다) -----------------------------------

    function validate(blocks, pendingUploads) {
        const global = [];
        const fields = new Map(); // focusKey → message
        const byBlock = new Map(); // blockId → [message]
        const add = (block, key, message) => {
            fields.set(key, message);
            if (!byBlock.has(block.id)) byBlock.set(block.id, []);
            byBlock.get(block.id).push(message);
        };
        if (pendingUploads > 0) global.push('이미지 업로드가 끝난 뒤 다시 저장해 주세요.');
        if (blocks.length === 0) global.push('블록을 하나 이상 추가해 주세요.');
        if (blocks.length > LIMITS.blocks) global.push(`블록은 ${LIMITS.blocks}개까지 추가할 수 있습니다.`);

        blocks.forEach(block => {
            const key = name => `${block.id}:${name}`;
            switch (block.type) {
                case 'SECTION_TITLE':
                    if (blank(block.title)) add(block, key('title'), '제목을 입력해 주세요.');
                    break;
                case 'RICH_TEXT':
                    if (blank(block.text)) add(block, key('text'), '본문을 입력해 주세요.');
                    break;
                // 이미지 설명(alt)은 선택이다. 비우면 공개 화면이 캡션·제목 등으로 대신 채운다.
                case 'FULL_IMAGE':
                    if (!block.image) add(block, key('image'), '이미지를 올려 주세요.');
                    break;
                case 'IMAGE_TEXT':
                    if (!block.image) add(block, key('image'), '이미지를 올려 주세요.');
                    if (blank(block.text)) add(block, key('text'), '본문을 입력해 주세요.');
                    break;
                case 'IMAGE_SLIDER':
                    if (block.items.length === 0) add(block, key('add'), '이미지를 한 장 이상 추가해 주세요.');
                    if (block.items.length > LIMITS.sliderItems) {
                        add(block, key('add'), `이미지는 ${LIMITS.sliderItems}장까지 추가할 수 있습니다.`);
                    }
                    block.items.forEach((item, index) => {
                        const itemKey = name => `${block.id}:${item.id}:${name}`;
                        if (!item.image) add(block, itemKey('image'), `${index + 1}번째 이미지: 이미지를 올려 주세요.`);
                    });
                    break;
                case 'IMAGE_GRID':
                    // 장수는 블록을 만들 때 정해지고 화면에서 바뀌지 않는다. 칸마다 이미지만 필수다.
                    block.items.forEach((item, index) => {
                        if (!item.image) {
                            add(block, `${block.id}:${item.id}:image`, `${index + 1}번째 칸: 이미지를 올려 주세요.`);
                        }
                    });
                    break;
                default:
                    if (blank(block.text)) add(block, key('text'), '강조 문구를 입력해 주세요.');
            }
        });
        return {global, fields, byBlock, valid: global.length === 0 && fields.size === 0};
    }

    // ---- 에디터 -----------------------------------------------------------------------------

    function createEditor(root, input, form) {
        const state = {
            blocks: [],
            collapsed: new Set(),
            uploads: new Set(), // 업로드 중인 이미지 자리 key
            imageErrors: new Map(), // 이미지 자리 key → 서버 메시지
            errors: null, // 마지막 저장 시도 검사 결과
            menuOpen: false,
            loadError: false,
            dirty: false,
            active: !root.hidden
        };

        // 처음 model: 수정 화면의 저장 값, 또는 저장이 거부되어 다시 그려진 화면의 방금 보낸 값.
        const initial = input.value.trim();
        if (initial) {
            try {
                const parsed = JSON.parse(initial);
                if (!parsed || parsed.version !== 1 || !Array.isArray(parsed.blocks)) throw new Error('version');
                state.blocks = parsed.blocks.map(readBlock).filter(Boolean);
                if (state.blocks.length !== parsed.blocks.length) state.loadError = true;
            } catch {
                state.loadError = true;
            }
        }

        function allBlockIds() {
            return new Set(state.blocks.map(block => block.id));
        }

        function markDirty() {
            state.dirty = true;
        }

        function serialize() {
            return JSON.stringify({version: 1, blocks: state.blocks.map(writeBlock)});
        }

        // ---- 그리기 ----

        /** 다시 그린 뒤에도 보던 칸에 머문다. (업로드가 끝나 다시 그려도 입력 중인 칸이 풀리지 않게) */
        function render(focusKey) {
            const active = document.activeElement;
            const activeKey = active && root.contains(active) ? active.dataset.focusKey : null;
            const selection = active && typeof active.selectionStart === 'number'
                ? [active.selectionStart, active.selectionEnd] : null;

            // replaceChildren 은 null 을 "null" 글자로 넣으므로 빈 자리는 미리 뺀다.
            root.replaceChildren(...[
                el('div', {className: 'admin-structured-editor-header'}, [
                    el('h3', {text: '구조화 콘텐츠'}),
                    el('p', {className: 'admin-help-text',
                        text: '블록을 위에서 아래로 쌓아 본문을 만듭니다. 순서가 공개 화면 순서이고, 모양은 화면이 정합니다.'})
                ]),
                renderSummary(),
                state.loadError ? el('p', {className: 'admin-structured-load-error', role: 'alert',
                    text: '저장된 블록 중 읽지 못한 것이 있습니다. 화면에 보이는 블록만 다시 저장됩니다.'}) : null,
                state.blocks.length === 0
                    ? el('p', {className: 'admin-structured-empty',
                        text: '아직 블록이 없습니다. 아래 [+ 블록 추가]로 첫 블록을 넣어 주세요.'})
                    : el('ol', {className: 'admin-structured-blocks'},
                        state.blocks.map((block, index) => renderBlock(block, index))),
                renderAddBar()
            ].filter(Boolean));

            const target = focusKey || activeKey;
            if (target) {
                const element = root.querySelector(`[data-focus-key="${CSS.escape(target)}"]`);
                if (element && !element.disabled) {
                    element.focus({preventScroll: !focusKey});
                    if (!focusKey && selection && typeof element.setSelectionRange === 'function') {
                        try {
                            element.setSelectionRange(selection[0], selection[1]);
                        } catch {
                            // 선택을 되돌릴 수 없는 입력 종류는 그냥 둔다.
                        }
                    }
                }
            }
        }

        function renderSummary() {
            const errors = state.errors;
            if (!errors || errors.valid) return null;
            const count = errors.global.length + errors.fields.size;
            return el('div', {className: 'admin-alert admin-structured-error-summary', role: 'alert',
                tabindex: '-1', 'data-focus-key': 'error-summary'}, [
                el('p', {text: `구조화 콘텐츠에서 고칠 곳이 ${count}개 있습니다.`}),
                errors.global.length ? el('ul', {}, errors.global.map(message => el('li', {text: message}))) : null
            ]);
        }

        function renderAddBar() {
            const full = state.blocks.length >= LIMITS.blocks;
            const menuId = `${root.id || 'structured'}-add-menu`;
            return el('div', {className: 'admin-structured-add'}, [
                el('button', {
                    type: 'button', className: 'admin-btn is-small admin-structured-add-toggle',
                    'aria-expanded': String(state.menuOpen), 'aria-controls': menuId,
                    disabled: full, 'data-focus-key': 'add-toggle', text: '+ 블록 추가',
                    onclick: () => {
                        state.menuOpen = !state.menuOpen;
                        render(state.menuOpen ? 'add-option-0' : 'add-toggle');
                    }
                }),
                full ? el('span', {className: 'admin-help-text', text: `블록은 ${LIMITS.blocks}개까지 추가할 수 있습니다.`}) : null,
                el('div', {
                    id: menuId, className: 'admin-structured-add-menu', role: 'group',
                    'aria-label': '추가할 블록 종류', hidden: !state.menuOpen,
                    onkeydown: event => {
                        if (event.key !== 'Escape') return;
                        state.menuOpen = false;
                        render('add-toggle');
                    }
                }, ADD_OPTIONS.map((option, index) => el('button', {
                    type: 'button', className: 'admin-structured-add-option', text: option.label,
                    'data-focus-key': `add-option-${index}`,
                    onclick: () => addBlock(option)
                })))
            ]);
        }

        function renderBlock(block, index) {
            const label = blockLabel(block);
            const position = `${index + 1}번째 블록(${label})`;
            const collapsed = state.collapsed.has(block.id);
            const bodyId = `sb-${block.id}-body`;
            const errorId = `sb-${block.id}-errors`;
            const messages = state.errors?.byBlock.get(block.id) || [];
            const action = (name, symbol, ariaLabel, handler, options = {}) => el('button', {
                type: 'button', className: `admin-btn is-small admin-structured-action${options.danger ? ' is-danger' : ''}`,
                'aria-label': ariaLabel, title: ariaLabel, 'data-focus-key': `${block.id}:${name}`,
                disabled: options.disabled, 'aria-expanded': options.expanded, 'aria-controls': options.controls,
                text: symbol, onclick: handler
            });

            return el('li', {className: `admin-structured-block${messages.length ? ' is-invalid' : ''}`,
                'data-block-id': block.id, 'aria-label': position}, [
                el('div', {className: 'admin-structured-block-header'}, [
                    el('span', {className: 'admin-structured-block-type', text: `${index + 1}. ${label}`}),
                    el('span', {className: 'admin-structured-block-summary', 'data-block-summary': '',
                        text: blockSummary(block)}),
                    el('div', {className: 'admin-structured-block-actions', role: 'group', 'aria-label': `${position} 관리`}, [
                        action('up', '↑', `${position} 위로 이동`, () => moveBlock(block, -1), {disabled: index === 0}),
                        action('down', '↓', `${position} 아래로 이동`, () => moveBlock(block, 1),
                            {disabled: index === state.blocks.length - 1}),
                        action('copy', '복제', `${position} 복제`, () => duplicateBlock(block),
                            {disabled: state.blocks.length >= LIMITS.blocks}),
                        action('toggle', collapsed ? '펼치기' : '접기', `${position} ${collapsed ? '펼치기' : '접기'}`,
                            () => toggleBlock(block), {expanded: String(!collapsed), controls: bodyId}),
                        action('delete', '삭제', `${position} 삭제`, () => deleteBlock(block), {danger: true})
                    ])
                ]),
                messages.length ? el('ul', {id: errorId, className: 'admin-structured-block-errors'},
                    messages.map(message => el('li', {text: message}))) : null,
                el('div', {id: bodyId, className: 'admin-structured-block-body', hidden: collapsed},
                    renderBlockFields(block, errorId))
            ]);
        }

        function invalidAttributes(key, errorId) {
            const invalid = state.errors?.fields.has(key);
            return invalid ? {'aria-invalid': 'true', 'aria-describedby': errorId, className: 'is-invalid'} : {};
        }

        /** 글 칸 하나. 값이 바뀌면 model 만 바꾸고 다시 그리지 않는다. (입력 중 커서를 지킨다) */
        function field(block, options, errorId) {
            const key = options.key;
            const id = `sb-${key.replace(/:/g, '-')}`;
            const invalid = invalidAttributes(key, errorId);
            const control = el(options.multiline ? 'textarea' : 'input', {
                id, type: options.multiline ? null : 'text', maxlength: options.max,
                rows: options.multiline ? options.rows || 4 : null,
                'aria-required': options.required ? 'true' : null,
                'aria-invalid': invalid['aria-invalid'], 'aria-describedby': invalid['aria-describedby'],
                className: invalid.className, 'data-focus-key': key,
                oninput: event => {
                    options.set(event.target.value);
                    markDirty();
                    if (options.summary) {
                        const summary = root.querySelector(`[data-block-id="${CSS.escape(block.id)}"] [data-block-summary]`);
                        if (summary) summary.textContent = blockSummary(block);
                    }
                }
            });
            control.value = options.value;
            return el('div', {className: 'admin-form-field'}, [
                el('label', {for: id}, [options.label, options.required
                    ? el('span', {className: 'admin-structured-required', 'aria-hidden': 'true', text: ' *'})
                    : el('span', {className: 'admin-structured-optional', text: ' (선택)'})]),
                control,
                options.help ? el('span', {className: 'admin-help-text', text: options.help}) : null
            ]);
        }

        /**
         * 이미지 자리. 고르면 바로 올리고, 성공하면 서버가 준 url / width / height 로 바꾼다.
         * 바꾸기·지우기는 model 만 고치고, 예전 파일 정리는 저장 때 서버가 한다.
         */
        function imageControl(options, errorId) {
            const {key, image, label} = options;
            const uploading = state.uploads.has(key);
            const error = state.imageErrors.get(key);
            const inputId = `sb-${key.replace(/:/g, '-')}-file`;
            const statusId = `${inputId}-status`;
            const invalid = invalidAttributes(key, errorId);
            const fileInput = el('input', {
                id: inputId, type: 'file', accept: IMAGE_ACCEPT, className: 'admin-visually-hidden',
                disabled: uploading, 'data-focus-key': `${key}:file`,
                'aria-describedby': [statusId, invalid['aria-describedby']].filter(Boolean).join(' '),
                'aria-invalid': invalid['aria-invalid'],
                onchange: event => {
                    const file = event.target.files?.[0];
                    if (file) uploadInto(key, file, options.assign);
                }
            });
            return el('div', {className: `admin-structured-image${invalid.className ? ' is-invalid' : ''}`}, [
                el('div', {className: 'admin-structured-image-preview'}, image
                    ? el('img', {src: image.url, alt: '', loading: 'lazy'})
                    : el('span', {text: '이미지 없음'})),
                el('div', {className: 'admin-structured-image-side'}, [
                    el('div', {className: 'admin-structured-image-actions'}, [
                        el('label', {for: inputId, className: `admin-btn is-small${uploading ? ' is-disabled' : ''}`},
                            [image ? `${label} 교체` : `${label} 올리기`, fileInput]),
                        image && options.remove ? el('button', {
                            type: 'button', className: 'admin-btn is-small', disabled: uploading,
                            'data-focus-key': `${key}:remove`, text: '이미지 빼기',
                            onclick: () => {
                                options.remove();
                                markDirty();
                                render(`${key}:file`);
                            }
                        }) : null
                    ]),
                    el('p', {id: statusId, className: `admin-structured-image-status${error ? ' is-error' : ''}`,
                        'aria-live': 'polite',
                        text: uploading ? '업로드 중…'
                            : error || (image ? `${image.width} × ${image.height}px` : 'JPG·PNG·WebP, 10MB 이하. 긴 변 2000px를 넘으면 줄여 저장합니다.')})
                ])
            ]);
        }

        function renderBlockFields(block, errorId) {
            const key = name => `${block.id}:${name}`;
            switch (block.type) {
                case 'SECTION_TITLE':
                    return [field(block, {key: key('title'), label: '제목', max: LIMITS.title, required: true, summary: true,
                        value: block.title, set: value => { block.title = value; }}, errorId)];
                case 'RICH_TEXT':
                    return [
                        renderChoices(block, {name: 'layout', legend: '본문 폭', property: 'layout',
                            choices: [['DEFAULT', '기본'], ['FOCUSED', '집중형']],
                            help: '집중형은 짧은 도입부·에세이를 좁은 폭 가운데에 둡니다. 휴대폰에서는 같습니다.'}),
                        field(block, {key: key('text'), label: '본문', max: LIMITS.richText, multiline: true, rows: 7,
                            required: true, summary: true, help: '문단을 나누려면 빈 줄을 사용하세요. HTML 은 글자 그대로 보입니다.',
                            value: block.text, set: value => { block.text = value; }}, errorId)
                    ];
                case 'FULL_IMAGE':
                    return [
                        imageControl({key: key('image'), image: block.image, label: '이미지',
                            assign: image => { block.image = image; }, remove: () => { block.image = null; }}, errorId),
                        field(block, {key: key('alt'), label: '이미지 설명(alt)', max: LIMITS.alt, summary: true,
                            help: '사진을 볼 수 없는 사람에게 읽어 주는 설명입니다. 비워 두면 캡션을 대신 씁니다.',
                            value: block.alt, set: value => { block.alt = value; }}, errorId),
                        field(block, {key: key('caption'), label: '캡션', max: LIMITS.caption,
                            value: block.caption, set: value => { block.caption = value; }}, errorId)
                    ];
                case 'IMAGE_TEXT':
                    return [
                        renderChoices(block, {name: 'position', legend: '배치', property: 'imagePosition',
                            choices: [['LEFT', '이미지 왼쪽'], ['RIGHT', '이미지 오른쪽']],
                            help: '휴대폰에서는 언제나 이미지가 위에 옵니다.'}),
                        el('div', {className: 'admin-structured-split'}, [
                            el('div', {}, [
                                imageControl({key: key('image'), image: block.image, label: '이미지',
                                    assign: image => { block.image = image; }, remove: () => { block.image = null; }}, errorId),
                                field(block, {key: key('alt'), label: '이미지 설명(alt)', max: LIMITS.alt,
                                    help: '비워 두면 제목을 대신 씁니다.',
                                    value: block.alt, set: value => { block.alt = value; }}, errorId)
                            ]),
                            el('div', {}, [
                                field(block, {key: key('title'), label: '제목', max: LIMITS.title, summary: true,
                                    value: block.title, set: value => { block.title = value; }}, errorId),
                                field(block, {key: key('text'), label: '본문', max: LIMITS.imageText, multiline: true,
                                    rows: 6, required: true, summary: true, help: '문단을 나누려면 빈 줄을 사용하세요.',
                                    value: block.text, set: value => { block.text = value; }}, errorId)
                            ])
                        ])
                    ];
                case 'IMAGE_SLIDER':
                    return renderSlider(block, errorId);
                case 'IMAGE_GRID':
                    // 장수가 정해져 있어 추가·삭제·이동 없이 칸마다 이미지를 올리거나 바꾼다.
                    return [
                        el('ol', {className: `admin-structured-items admin-structured-grid is-${block.columns}`,
                            'aria-label': `${blockLabel(block)} 이미지`},
                        block.items.map((item, index) => renderImageItem(block, item, index, errorId, {fixed: true}))),
                        el('span', {className: 'admin-help-text',
                            text: '공개 화면에서는 사진을 같은 비율로 맞춰 자르고, 휴대폰에서는 한 줄에 한 장씩 보여 줍니다.'})
                    ];
                default:
                    return [field(block, {key: key('text'), label: '강조 문구', max: LIMITS.callout, multiline: true,
                        rows: 2, required: true, summary: true,
                        value: block.text, set: value => { block.text = value; }}, errorId)];
            }
        }

        /** 정해진 프리셋 중 하나를 고르는 라디오 묶음. (이미지 위치, 본문 폭) 숫자를 직접 넣는 칸은 두지 않는다. */
        function renderChoices(block, options) {
            const name = `sb-${block.id}-${options.name}`;
            const choice = ([value, label]) => el('label', {className: 'admin-content-format-option'}, [
                (() => {
                    const radio = el('input', {type: 'radio', name, value,
                        'data-focus-key': `${block.id}:${options.name}-${value}`,
                        onchange: () => {
                            block[options.property] = value;
                            markDirty();
                        }});
                    radio.checked = block[options.property] === value;
                    return radio;
                })(),
                el('span', {text: label})
            ]);
            return el('fieldset', {className: 'admin-structured-choices'}, [
                el('legend', {text: options.legend}),
                ...options.choices.map(choice),
                el('span', {className: 'admin-help-text', text: options.help})
            ]);
        }

        function renderSlider(block, errorId) {
            const addKey = `${block.id}:add`;
            const room = LIMITS.sliderItems - block.items.length;
            const addInputId = `sb-${block.id}-add-files`;
            const addInvalid = invalidAttributes(addKey, errorId);
            const addError = state.imageErrors.get(addKey);
            return [
                field(block, {key: `${block.id}:title`, label: '슬라이더 제목', max: LIMITS.title, summary: true,
                    value: block.title, set: value => { block.title = value; }}, errorId),
                block.items.length
                    ? el('ol', {className: 'admin-structured-items', 'aria-label': '슬라이더 이미지'},
                        block.items.map((item, index) => renderImageItem(block, item, index, errorId)))
                    : el('p', {className: 'admin-structured-empty', text: '아직 이미지가 없습니다.'}),
                el('div', {className: 'admin-structured-image-actions'}, [
                    el('label', {for: addInputId, className: `admin-btn is-small${room <= 0 ? ' is-disabled' : ''}`}, [
                        '+ 이미지 추가',
                        el('input', {id: addInputId, type: 'file', accept: IMAGE_ACCEPT, multiple: true,
                            className: 'admin-visually-hidden', disabled: room <= 0, 'data-focus-key': addKey,
                            'aria-invalid': addInvalid['aria-invalid'], 'aria-describedby': `${addInputId}-help ${addInvalid['aria-describedby'] || ''}`.trim(),
                            onchange: event => addSliderImages(block, Array.from(event.target.files || []))})
                    ]),
                    el('span', {id: `${addInputId}-help`, className: `admin-help-text${addError ? ' is-error' : ''}`,
                        'aria-live': 'polite',
                        text: addError || `여러 장을 한 번에 고를 수 있습니다. ${block.items.length} / ${LIMITS.sliderItems}장`})
                ])
            ];
        }

        /**
         * 슬라이더·이미지 배치의 사진 한 장. fixed(이미지 배치)면 장수가 정해져 있어 이동·삭제 버튼을 두지 않는다.
         */
        function renderImageItem(block, item, index, errorId, options = {}) {
            const key = name => `${block.id}:${item.id}:${name}`;
            const position = options.fixed ? `${index + 1}번째 칸` : `${index + 1}번째 이미지`;
            const itemAction = (name, symbol, ariaLabel, handler, options = {}) => el('button', {
                type: 'button', className: `admin-btn is-small admin-structured-action${options.danger ? ' is-danger' : ''}`,
                'aria-label': ariaLabel, title: ariaLabel, 'data-focus-key': key(name), disabled: options.disabled,
                text: symbol, onclick: handler
            });
            return el('li', {className: 'admin-structured-item', 'aria-label': position}, [
                el('div', {className: 'admin-structured-item-header'}, [
                    el('span', {className: 'admin-structured-block-type', text: position}),
                    options.fixed ? null : el('div', {className: 'admin-structured-block-actions', role: 'group', 'aria-label': `${position} 관리`}, [
                        itemAction('up', '↑', `${position} 위로 이동`, () => moveItem(block, item, -1), {disabled: index === 0}),
                        itemAction('down', '↓', `${position} 아래로 이동`, () => moveItem(block, item, 1),
                            {disabled: index === block.items.length - 1}),
                        itemAction('delete', '삭제', `${position} 삭제`, () => deleteItem(block, item), {danger: true})
                    ])
                ]),
                el('div', {className: 'admin-structured-item-body'}, [
                    imageControl({key: key('image'), image: item.image, label: '이미지',
                        assign: image => { item.image = image; }}, errorId),
                    el('div', {className: 'admin-structured-item-fields'}, [
                        field(block, {key: key('title'), label: '제목', max: LIMITS.itemTitle,
                            value: item.title, set: value => { item.title = value; }}, errorId),
                        field(block, {key: key('caption'), label: '설명', max: LIMITS.caption,
                            value: item.caption, set: value => { item.caption = value; }}, errorId),
                        field(block, {key: key('alt'), label: '이미지 설명(alt)', max: LIMITS.alt,
                            help: '비워 두면 제목·설명을 대신 씁니다.',
                            value: item.alt, set: value => { item.alt = value; }}, errorId)
                    ])
                ])
            ]);
        }

        // ---- 동작 ----

        function addBlock(option) {
            if (state.blocks.length >= LIMITS.blocks) return;
            const block = emptyBlock(option, allBlockIds());
            state.blocks.push(block);
            state.menuOpen = false;
            markDirty();
            const firstField = {
                SECTION_TITLE: 'title', RICH_TEXT: 'text', CALLOUT: 'text', IMAGE_SLIDER: 'title',
                IMAGE_GRID: block.items?.[0] ? `${block.items[0].id}:image:file` : null
            }[block.type];
            render(firstField ? `${block.id}:${firstField}` : `${block.id}:image:file`);
        }

        function moveBlock(block, offset) {
            const from = state.blocks.indexOf(block);
            const to = from + offset;
            if (from < 0 || to < 0 || to >= state.blocks.length) return;
            state.blocks.splice(from, 1);
            state.blocks.splice(to, 0, block);
            markDirty();
            // 끝에 닿아 같은 버튼이 막히면 반대쪽 버튼에 머문다.
            const edge = (offset < 0 && to === 0) || (offset > 0 && to === state.blocks.length - 1);
            render(`${block.id}:${edge ? (offset < 0 ? 'down' : 'up') : (offset < 0 ? 'up' : 'down')}`);
        }

        /** 복제는 새 블록 id, 슬라이더·이미지 배치라면 이미지마다 새 id. 이미지 파일은 같은 것을 함께 쓴다. */
        function duplicateBlock(block) {
            if (state.blocks.length >= LIMITS.blocks) return;
            const copy = JSON.parse(JSON.stringify(block));
            copy.id = newId('b', allBlockIds());
            if (copy.type === 'IMAGE_SLIDER' || copy.type === 'IMAGE_GRID') {
                const itemIds = new Set();
                copy.items.forEach(item => { item.id = newId('i', itemIds); });
            }
            state.blocks.splice(state.blocks.indexOf(block) + 1, 0, copy);
            markDirty();
            render(`${copy.id}:copy`);
        }

        function toggleBlock(block) {
            if (state.collapsed.has(block.id)) state.collapsed.delete(block.id);
            else state.collapsed.add(block.id);
            render(`${block.id}:toggle`);
        }

        function deleteBlock(block) {
            if (blockHasContent(block)
                && !window.confirm(`${blockLabel(block)} 블록을 삭제할까요? 입력한 내용이 사라집니다.`)) {
                return;
            }
            const index = state.blocks.indexOf(block);
            state.blocks.splice(index, 1);
            state.collapsed.delete(block.id);
            markDirty();
            const next = state.blocks[Math.min(index, state.blocks.length - 1)];
            render(next ? `${next.id}:delete` : 'add-toggle');
        }

        function moveItem(block, item, offset) {
            const from = block.items.indexOf(item);
            const to = from + offset;
            if (from < 0 || to < 0 || to >= block.items.length) return;
            block.items.splice(from, 1);
            block.items.splice(to, 0, item);
            markDirty();
            const edge = (offset < 0 && to === 0) || (offset > 0 && to === block.items.length - 1);
            render(`${block.id}:${item.id}:${edge ? (offset < 0 ? 'down' : 'up') : (offset < 0 ? 'up' : 'down')}`);
        }

        function deleteItem(block, item) {
            const hasText = !blank(item.title) || !blank(item.caption) || !blank(item.alt);
            if ((item.image || hasText) && !window.confirm('이 이미지를 슬라이더에서 뺄까요? 입력한 설명도 사라집니다.')) {
                return;
            }
            const index = block.items.indexOf(item);
            block.items.splice(index, 1);
            markDirty();
            const next = block.items[Math.min(index, block.items.length - 1)];
            render(next ? `${block.id}:${next.id}:delete` : `${block.id}:add`);
        }

        async function uploadInto(key, file, assign) {
            state.uploads.add(key);
            state.imageErrors.delete(key);
            render();
            try {
                assign(await uploadImage(file));
                markDirty();
            } catch (error) {
                state.imageErrors.set(key, error.message);
            } finally {
                state.uploads.delete(key);
                render();
            }
        }

        /** 고른 여러 장을 차례로 올린다. 남은 자리보다 많으면 앞에서부터 자리만큼만 넣는다. */
        async function addSliderImages(block, files) {
            const addKey = `${block.id}:add`;
            const room = LIMITS.sliderItems - block.items.length;
            state.imageErrors.delete(addKey);
            if (files.length === 0 || room <= 0) return;
            const accepted = files.slice(0, room);
            if (files.length > room) {
                state.imageErrors.set(addKey,
                    `이미지는 ${LIMITS.sliderItems}장까지 넣을 수 있어 ${files.length - room}장은 추가하지 않았습니다.`);
            }
            const itemIds = new Set(block.items.map(item => item.id));
            const items = accepted.map(() => ({id: newId('i', itemIds), image: null, alt: '', title: '', caption: ''}));
            block.items.push(...items);
            markDirty();
            items.forEach(item => state.uploads.add(`${block.id}:${item.id}:image`));
            render();
            for (let index = 0; index < items.length; index++) {
                const key = `${block.id}:${items[index].id}:image`;
                try {
                    items[index].image = await uploadImage(accepted[index]);
                } catch (error) {
                    state.imageErrors.set(key, error.message);
                } finally {
                    state.uploads.delete(key);
                    render();
                }
            }
        }

        // ---- 저장 ----

        form.addEventListener('submit', event => {
            // 읽지 못한 저장 값은 손대기 전까지 그대로 다시 보낸다. (서버가 같은 기준으로 판단한다)
            const keepRaw = state.loadError && !state.dirty;
            if (!state.active) {
                // 일반 에디터로 저장해도, 저장이 거부되어 돌아오면 이 초안을 되살릴 수 있게 값은 남긴다.
                if (!keepRaw) input.value = serialize();
                return;
            }
            const result = validate(state.blocks, state.uploads.size);
            if (!result.valid) {
                event.preventDefault();
                state.errors = result;
                // 고칠 곳이 접힌 블록 안에 있으면 펼친다.
                result.byBlock.forEach((messages, blockId) => state.collapsed.delete(blockId));
                render('error-summary');
                return;
            }
            state.errors = null;
            if (!keepRaw) input.value = serialize();
        });

        render();

        return {
            hasContent: () => state.blocks.length > 0,
            setActive(active) {
                state.active = active;
                root.hidden = !active;
            },
            isActive: () => state.active,
            serialize,
            blocks: () => state.blocks
        };
    }

    document.addEventListener('DOMContentLoaded', () => {
        const root = document.querySelector('[data-structured-editor]');
        const input = document.getElementById('travel-info-structured-content');
        const form = root?.closest('form');
        if (!root || !input || !form) return;
        root.structuredEditor = createEditor(root, input, form);
    });

    // 테스트에서 상태 없는 도우미를 직접 확인할 수 있게 둔다.
    window.TravelInfoStructuredEditor = Object.freeze({LIMITS, readBlock, writeBlock, validate, newId});
})();
