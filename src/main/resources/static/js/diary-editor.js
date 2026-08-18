/**
 * 다이어리 종이 본문 편집기.
 * 각 페이지의 종이 자체를 Quill 편집 영역으로 쓰고, 서식 명령은 펼침 위 공통 툴바가
 * 지금 쓰고 있는 쪽(active page)에만 적용한다. 본문은 diary_pages.content 에 자동저장한다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const editorElements = Array.from(document.querySelectorAll('.diary-editor[data-content-url]'));
    if (editorElements.length === 0 || typeof Quill === 'undefined') return;

    const toolbar = document.querySelector('.diary-toolbar');
    const statusText = document.getElementById('diary-save-status');
    const SAVE_DELAY = 800;
    /**
     * 다이어리 전용 글꼴. 값은 Quill 의 font 포맷 값이자 ql-font-{값} 클래스가 된다.
     * (스타일은 diary-fonts.css, 서버 허용 목록은 DiaryContentSanitizer 와 같은 값을 쓴다)
     */
    const FONTS = [
        {value: '', label: '기본'},
        {value: 'fromsol', label: '그리운 프롬솔'},
        {value: 'nanum-square', label: '나눔스퀘어'},
        {value: 'bookk-myeongjo', label: '부크크 명조'},
        {value: 'hiker', label: '하이커체'},
        {value: 'cafe24-surround', label: '카페24 써라운드'},
        {value: 'lee-seoyun', label: '이서윤체'},
        {value: 'ggubulim', label: '꾸불림체'},
        {value: 'ohchungi', label: '그리운 국한박 오춘기 김작가'},
        {value: 'chosun-gungsuh', label: '조선궁서체'},
        {value: 'gunham', label: '군함이말문트였체'},
        {value: 'dunggeunmo', label: '둥근모꼴+ Fixedsys'},
        {value: 'mitmi', label: '밑미 폰트'},
        {value: 'green-umbrella', label: '윤초록우산어린이 만세'},
        {value: 'incheon-jaram', label: '인천교육자람체'},
        {value: 'park-dahyun', label: '온글잎 박다현체'}
    ];
    const FONT_VALUES = FONTS.map(font => font.value).filter(Boolean);
    /** 일기 본문에 필요한 서식만 허용한다. (붙여넣기도 이 범위로 걸러진다) */
    const FORMATS = ['bold', 'italic', 'underline', 'font', 'size', 'color', 'align'];
    /** 이모지 목록은 diary-emoji-data.js 가 제공한다. */
    const EMOJI_CATEGORIES = window.DIARY_EMOJI_CATEGORIES || [];

    const Font = Quill.import('formats/font');
    Font.whitelist = FONT_VALUES;
    Quill.register(Font, true);

    const pages = editorElements.map(createPage);
    let activePage = null;
    // 툴바 동기화(setupToolbar)가 글꼴 드롭다운 초기화보다 먼저 읽으므로 여기서 선언해 둔다.
    let fontTrigger = null;

    /** 툴바 팝오버(글꼴/이모지)는 한 번에 하나만 열어 둔다. */
    const popovers = [];

    setActivePage(pages[0]);
    setupToolbar();
    setupFontPicker();
    setupEmoji();
    setupSaveBeforeLeaving();
    setupEditDone();

    function createPage(element) {
        const quill = new Quill(element, {
            placeholder: '이 날의 기록을 남겨보세요.',
            formats: FORMATS,
            modules: {toolbar: false, history: {userOnly: true}}
        });

        quill.root.setAttribute('aria-label', '페이지 본문');

        const page = {
            element,
            quill,
            sheet: element.closest('.diary-sheet'),
            url: element.dataset.contentUrl,
            savedHtml: quill.getSemanticHTML(),
            lastRange: null,
            timer: 0,
            saving: null
        };

        quill.on('text-change', (delta, oldDelta, source) => {
            if (source !== 'user') return;
            scheduleSave(page);
            // 입력으로 커서가 옮겨질 때는 selection-change 가 따로 오지 않는다.
            // Quill 이 선택 영역을 갱신한 다음에 읽도록 마이크로태스크로 한 번 미룬다.
            Promise.resolve().then(() => {
                if (activePage === page) syncToolbar();
            });
        });
        quill.on('selection-change', (range) => {
            if (!range) return;
            page.lastRange = range;
            setActivePage(page);
            syncToolbar();
        });
        quill.root.addEventListener('focus', () => setActivePage(page));

        return page;
    }

    function setActivePage(page) {
        if (!page || activePage === page) return;
        activePage = page;
        pages.forEach(other => other.sheet?.classList.toggle('is-active-page', other === page));
    }

    /* ===== 자동저장 ===== */

    function isDirty(page) {
        return page.quill.getSemanticHTML() !== page.savedHtml;
    }

    function scheduleSave(page) {
        window.clearTimeout(page.timer);
        showStatus('작성 중…');
        page.timer = window.setTimeout(() => savePage(page), SAVE_DELAY);
    }

    /** 저장 결과를 boolean 으로 돌려준다. (실패해도 예외를 던지지 않는다) */
    function savePage(page) {
        window.clearTimeout(page.timer);
        if (page.saving) return page.saving;
        if (!isDirty(page)) {
            return Promise.resolve(true);
        }

        const html = page.quill.getSemanticHTML();
        showStatus('저장 중…');
        page.saving = sendContent(page.url, html)
            .then(() => {
                page.savedHtml = html;
                showStatus('저장됨');
                return true;
            })
            .catch(error => {
                showStatus(error.message || '저장하지 못했습니다', true);
                return false;
            })
            .finally(() => {
                page.saving = null;
            });
        return page.saving;
    }

    /** CSRF 토큰은 layout 의 meta 값을 그대로 쓴다. */
    async function sendContent(url, content) {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (!csrfToken || !csrfHeader) {
            throw new Error('보안 토큰을 확인할 수 없어 저장하지 못했습니다');
        }

        const response = await fetch(url, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                [csrfHeader]: csrfToken
            },
            body: new URLSearchParams({content})
        });

        if (response.status === 401) {
            const redirect = window.location.pathname + window.location.search;
            window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`;
            throw new Error('로그인이 필요합니다');
        }
        if (!response.ok) {
            let message = '저장하지 못했습니다';
            if ((response.headers.get('Content-Type') || '').includes('application/json')) {
                const payload = await response.json();
                message = payload.message || message;
            }
            throw new Error(message);
        }
    }

    function showStatus(text, isError = false) {
        if (!statusText) return;
        statusText.textContent = text;
        statusText.classList.toggle('is-error', isError);
    }

    /** 저장이 남아 있으면 먼저 끝내고, 실패하면 false 를 돌려준다. */
    async function flush() {
        for (const page of pages) {
            const saved = await savePage(page);
            if (!saved) return false;
        }
        return true;
    }

    // 페이지 넘김/사진 등록처럼 화면을 떠나는 동작에서 마지막 입력을 지키기 위해 노출한다.
    window.diaryEditor = {
        flush,
        hasPendingChanges: () => pages.some(isDirty)
    };

    /** 이 화면의 폼 전송(사진 붙이기/페이지 설정 등) 직전에도 본문을 먼저 저장한다. */
    function setupSaveBeforeLeaving() {
        document.addEventListener('submit', (event) => {
            const form = event.target;
            if (!(form instanceof HTMLFormElement) || form.dataset.contentFlushed === 'true') return;
            if (!pages.some(isDirty)) return;

            event.preventDefault();
            flush().then(saved => {
                if (!saved) return;
                form.dataset.contentFlushed = 'true';
                form.submit();
            });
        });
    }

    /* ===== 공통 툴바 ===== */

    function setupToolbar() {
        if (!toolbar) return;

        toolbar.querySelectorAll('.diary-toolbar-button[data-editor-command]').forEach(button => {
            // 버튼을 눌러도 종이의 선택 영역이 풀리지 않게 한다.
            button.addEventListener('mousedown', event => event.preventDefault());
            button.addEventListener('click', () => {
                const command = button.dataset.editorCommand;
                const current = formatsForEditing();
                applyFormat(command, !current[command]);
            });
        });

        toolbar.querySelectorAll('.diary-toolbar-select[data-editor-command]').forEach(select => {
            select.addEventListener('change', () => {
                applyFormat(select.dataset.editorCommand, select.value || false);
            });
        });

        const colorInput = toolbar.querySelector('.diary-toolbar-color[data-editor-command]');
        colorInput?.addEventListener('input', () => {
            applyFormat(colorInput.dataset.editorCommand, colorInput.value);
        });

        syncToolbar();
    }

    /**
     * 툴바 표시용 서식. 기억해 둔 값이 아니라 지금 커서가 있는 위치의 실제 Quill 서식만 읽는다.
     * 커서가 없으면 null 을 돌려주고 표시를 건드리지 않는다.
     */
    function selectionFormats() {
        if (!activePage) return null;
        const range = activePage.quill.getSelection();
        return range ? activePage.quill.getFormat(range) : null;
    }

    /** 서식을 적용할 때 쓰는 기준. 툴바를 누르며 선택이 풀린 경우 lastRange 로 복원한다. */
    function formatsForEditing() {
        if (!activePage) return {};
        const range = activePage.quill.getSelection() || activePage.lastRange;
        return range ? activePage.quill.getFormat(range) : {};
    }

    function applyFormat(name, value) {
        if (!activePage) return;
        const quill = activePage.quill;
        const range = quill.getSelection() || activePage.lastRange;
        if (range) {
            quill.setSelection(range.index, range.length, 'silent');
        } else {
            quill.focus();
        }

        quill.format(name, value, 'user');
        syncToolbar();
        scheduleSave(activePage);
    }

    function syncToolbar() {
        if (!toolbar) return;
        // 커서가 없는 순간에는 마지막 표시를 그대로 둔다. (기억한 값으로 덮어쓰지 않는다)
        const formats = selectionFormats();
        if (!formats) return;

        toolbar.querySelectorAll('.diary-toolbar-button[data-editor-command]').forEach(button => {
            const active = Boolean(formats[button.dataset.editorCommand]);
            button.classList.toggle('is-active', active);
            button.setAttribute('aria-pressed', active ? 'true' : 'false');
        });
        toolbar.querySelectorAll('.diary-toolbar-select[data-editor-command]').forEach(select => {
            const value = formats[select.dataset.editorCommand];
            select.value = typeof value === 'string' ? value : '';
        });
        syncFontTrigger(formats);
    }

    /* ===== 글꼴 드롭다운 (이름을 실제 글꼴로 보여준다) ===== */

    function setupFontPicker() {
        const trigger = document.getElementById('diary-font-trigger');
        const list = document.getElementById('diary-font-list');
        if (!trigger || !list) return;

        fontTrigger = trigger;
        FONTS.forEach(font => {
            const option = document.createElement('button');
            option.type = 'button';
            option.className = `diary-font-option diary-font-${font.value || 'default'}`;
            option.textContent = font.label;
            option.dataset.fontValue = font.value;
            option.setAttribute('role', 'option');
            option.setAttribute('aria-selected', 'false');
            option.addEventListener('mousedown', event => event.preventDefault());
            option.addEventListener('click', () => {
                // 선택 영역이 있으면 그 영역 전체, 커서만 있으면 다음 입력부터 적용된다.
                applyFormat('font', font.value || false);
                toggle(false);
                trigger.focus();
            });
            list.append(option);
        });

        const toggle = registerPopover(trigger, list, () => trigger.focus());
        trigger.addEventListener('mousedown', event => event.preventDefault());
        trigger.addEventListener('click', () => toggle(list.hidden));

        // 방향키로 목록을 훑을 수 있게 한다. (Enter/Space 는 버튼 기본 동작)
        list.addEventListener('keydown', (event) => {
            if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
            event.preventDefault();
            const options = Array.from(list.querySelectorAll('.diary-font-option'));
            const current = options.indexOf(document.activeElement);
            const step = event.key === 'ArrowDown' ? 1 : -1;
            const next = current < 0 ? 0 : (current + step + options.length) % options.length;
            options[next]?.focus();
        });
        trigger.addEventListener('keydown', (event) => {
            if (event.key !== 'ArrowDown' || list.hidden) return;
            event.preventDefault();
            list.querySelector('.diary-font-option')?.focus();
        });

        // 처음에는 커서가 없으므로 서식 없음(기본) 상태로 시작한다.
        syncFontTrigger(selectionFormats() || {});
    }

    /** 커서 위치의 글꼴 이름을 버튼에 보여준다. 여러 글꼴이 섞여 있으면 중립 표시. */
    function syncFontTrigger(formats) {
        if (!fontTrigger) return;
        const value = formats.font;
        const label = value === undefined
            ? '기본'
            : (typeof value === 'string'
                ? (FONTS.find(font => font.value === value)?.label ?? '글꼴')
                : '여러 글꼴');

        fontTrigger.textContent = label;
        fontTrigger.className = 'diary-font-trigger'
            + (fontTrigger.classList.contains('is-active') ? ' is-active' : '')
            + (typeof value === 'string' && value ? ` diary-font-${value}` : '');

        // 여러 글꼴이 섞인 선택 영역에서는 어떤 항목도 선택 표시하지 않는다.
        const selectedValue = value === undefined ? '' : value;
        document.querySelectorAll('#diary-font-list .diary-font-option').forEach(option => {
            const selected = typeof selectedValue === 'string'
                && option.dataset.fontValue === selectedValue;
            option.setAttribute('aria-selected', selected ? 'true' : 'false');
            option.classList.toggle('is-selected', selected);
        });
    }

    /**
     * 툴바 팝오버 공통 처리.
     * 하나를 열면 나머지는 닫고, 바깥 클릭과 Esc 로 닫는다.
     */
    function registerPopover(trigger, panel, onClose) {
        function toggle(open) {
            // 자기 자신을 다시 닫지 않도록 다른 팝오버만 닫는다.
            if (open) popovers.forEach(other => {
                if (other.panel !== panel) other.close();
            });
            panel.hidden = !open;
            panel.classList.toggle('is-open', open);
            trigger.classList.toggle('is-active', open);
            trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
        }

        popovers.push({panel, close: () => toggle(false)});

        document.addEventListener('click', (event) => {
            if (!panel.hidden && !panel.contains(event.target) && event.target !== trigger
                && !trigger.contains(event.target)) {
                toggle(false);
            }
        });
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape' && !panel.hidden) {
                toggle(false);
                onClose?.();
            }
        });

        toggle(false);
        return toggle;
    }

    /** 편집 완료: 저장이 끝난 뒤에만 읽기 모드로 넘어간다. */
    function setupEditDone() {
        document.querySelectorAll('[data-editor-done]').forEach(link => {
            link.addEventListener('click', (event) => {
                if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
                event.preventDefault();
                flush().then(saved => {
                    // 실패하면 편집 화면을 그대로 두고 상태 문구만 남긴다.
                    if (saved) window.location.href = link.href;
                });
            });
        });
    }

    /* ===== 이모지 ===== */

    function setupEmoji() {
        const button = document.getElementById('diary-emoji-button');
        const popover = document.getElementById('diary-emoji-popover');
        const tabs = document.getElementById('diary-emoji-tabs');
        const grid = document.getElementById('diary-emoji-grid');
        if (!button || !popover || !tabs || !grid || EMOJI_CATEGORIES.length === 0) return;

        // 기본 상태는 반드시 닫힘 (열고 닫기와 바깥 클릭/Esc 는 공통 처리를 쓴다)
        const togglePopover = registerPopover(button, popover, () => button.focus());

        EMOJI_CATEGORIES.forEach((category, index) => {
            const tab = document.createElement('button');
            tab.type = 'button';
            tab.className = 'diary-emoji-tab';
            tab.textContent = category.icon;
            tab.title = category.name;
            tab.setAttribute('role', 'tab');
            tab.setAttribute('aria-label', `${category.name} 이모지`);
            tab.setAttribute('aria-selected', index === 0 ? 'true' : 'false');
            tab.addEventListener('mousedown', event => event.preventDefault());
            tab.addEventListener('click', () => showCategory(category));
            tabs.append(tab);
        });
        showCategory(EMOJI_CATEGORIES[0]);

        button.addEventListener('mousedown', event => event.preventDefault());
        button.addEventListener('click', () => togglePopover(popover.hidden));

        function showCategory(category) {
            Array.from(tabs.children).forEach(tab => {
                const selected = tab.title === category.name;
                tab.classList.toggle('is-active', selected);
                tab.setAttribute('aria-selected', selected ? 'true' : 'false');
            });

            grid.replaceChildren();
            grid.scrollTop = 0;
            category.emojis.forEach(emoji => {
                const item = document.createElement('button');
                item.type = 'button';
                item.className = 'diary-emoji-item';
                item.textContent = emoji;
                item.title = `이모지 ${emoji}`;
                item.setAttribute('aria-label', `이모지 ${emoji} 넣기`);
                item.addEventListener('mousedown', event => event.preventDefault());
                item.addEventListener('click', () => {
                    insertEmoji(emoji);
                    togglePopover(false);
                });
                grid.append(item);
            });
        }
    }

    function insertEmoji(emoji) {
        if (!activePage) return;
        const quill = activePage.quill;
        const range = quill.getSelection() || activePage.lastRange
            || {index: Math.max(0, quill.getLength() - 1), length: 0};

        quill.deleteText(range.index, range.length, 'user');
        quill.insertText(range.index, emoji, 'user');
        quill.setSelection(range.index + emoji.length, 0, 'silent');
        activePage.lastRange = {index: range.index + emoji.length, length: 0};
        scheduleSave(activePage);
    }
});
