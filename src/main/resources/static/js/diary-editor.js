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
    /** 프로젝트에서 이미 허용 중인 글꼴 중 일기에 쓰는 것만 */
    const FONTS = ['serif', 'monospace', 'pretendard', 'noto-sans-kr', 'noto-serif-kr', 'nanum-human'];
    /** 일기 본문에 필요한 서식만 허용한다. (붙여넣기도 이 범위로 걸러진다) */
    const FORMATS = ['bold', 'italic', 'underline', 'font', 'size', 'color', 'align'];
    const EMOJIS = [
        '😊', '😂', '🥰', '❤️',
        '✈️', '🚗', '🚆', '🚌',
        '🌸', '🌊', '🌅', '🌙',
        '☕', '🍜', '🍰', '🍻',
        '📍', '📷', '⭐', '🎉'
    ];

    const Font = Quill.import('formats/font');
    Font.whitelist = FONTS;
    Quill.register(Font, true);

    const pages = editorElements.map(createPage);
    let activePage = null;

    setActivePage(pages[0]);
    setupToolbar();
    setupEmoji();
    setupSaveBeforeLeaving();

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
                const current = currentFormats();
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

    function currentFormats() {
        if (!activePage) return {};
        const range = activePage.quill.getSelection() || activePage.lastRange;
        return range ? activePage.quill.getFormat(range) : activePage.quill.getFormat();
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
        const formats = currentFormats();

        toolbar.querySelectorAll('.diary-toolbar-button[data-editor-command]').forEach(button => {
            const active = Boolean(formats[button.dataset.editorCommand]);
            button.classList.toggle('is-active', active);
            button.setAttribute('aria-pressed', active ? 'true' : 'false');
        });
        toolbar.querySelectorAll('.diary-toolbar-select[data-editor-command]').forEach(select => {
            const value = formats[select.dataset.editorCommand];
            select.value = typeof value === 'string' ? value : '';
        });
    }

    /* ===== 이모지 ===== */

    function setupEmoji() {
        const button = document.getElementById('diary-emoji-button');
        const popover = document.getElementById('diary-emoji-popover');
        if (!button || !popover) return;

        EMOJIS.forEach(emoji => {
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
            popover.append(item);
        });

        button.addEventListener('mousedown', event => event.preventDefault());
        button.addEventListener('click', () => togglePopover(popover.hidden));
        document.addEventListener('click', (event) => {
            if (!popover.hidden && !event.target.closest('.diary-emoji')) togglePopover(false);
        });
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape' && !popover.hidden) {
                togglePopover(false);
                button.focus();
            }
        });

        function togglePopover(open) {
            popover.hidden = !open;
            button.setAttribute('aria-expanded', open ? 'true' : 'false');
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
