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
    /** 한 줄 메모 글꼴 기본값. (page_header_font DB 기본값과 같은 값) */
    const DEFAULT_HEADER_FONT = 'DEFAULT';
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
    /**
     * 형광펜 색상. 문구용 파스텔 형광펜 정도의 밝기만 쓴다.
     * (서버 허용 목록은 DiaryContentSanitizer.DIARY_HIGHLIGHT_COLORS 와 같은 값이다)
     */
    const HIGHLIGHTS = [
        {value: '#fff5a5', name: '노랑'},
        {value: '#ffd6e4', name: '핑크'},
        {value: '#c9f2e3', name: '민트'},
        {value: '#cfe6fb', name: '하늘'},
        {value: '#e2d9f7', name: '연보라'},
        {value: '#ffdec2', name: '살구'}
    ];
    /** 일기 본문에 필요한 서식만 허용한다. (붙여넣기도 이 범위로 걸러진다) */
    const FORMATS = ['bold', 'italic', 'underline', 'font', 'size', 'color', 'background', 'align'];
    /** 이모지 목록은 diary-emoji-data.js 가 제공한다. */
    const EMOJI_CATEGORIES = window.DIARY_EMOJI_CATEGORIES || [];
    /** 최근 사용 이모지는 이 브라우저에만 남긴다. (서버 저장 없음) */
    const RECENT_EMOJI_KEY = 'travelDiaryRecentEmojis';
    const RECENT_EMOJI_LIMIT = 30;
    const RECENT_EMOJI_CATEGORY = {id: 'recent', name: '최근', icon: '🕘'};

    const Font = Quill.import('formats/font');
    Font.whitelist = FONT_VALUES;
    Quill.register(Font, true);

    const pages = editorElements.map(createPage);
    let activePage = null;
    // 툴바 동기화(setupToolbar)가 글꼴/형광펜 초기화보다 먼저 읽으므로 여기서 선언해 둔다.
    let fontTrigger = null;
    let highlightTrigger = null;
    let highlightSwatch = null;
    /**
     * 켜져 있는 형광펜. 색을 고르면 여기 담기고, 그 뒤로는 본문에서 드래그한 구간을
     * 손을 뗄 때마다 칠한다. (null 이면 형광펜을 내려놓은 상태)
     */
    let highlightMode = null;
    /** 지금 드래그가 시작된 쪽. mouseup 때 어느 종이를 칠할지 정하는 데만 쓴다. */
    let markingPage = null;

    /** 툴바 팝오버(글꼴/형광펜/이모지)는 한 번에 하나만 열어 둔다. */
    const popovers = [];

    setActivePage(pages[0]);
    setupToolbar();
    setupFontPicker();
    setupHeaderStyle();
    setupHighlightPicker();
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
            saving: null,
            // 날짜 옆 한 줄 메모. 본문과 따로 저장하되 같은 상태 표시/flush 흐름을 쓴다.
            headerInput: null,
            headerUrl: null,
            savedHeader: '',
            headerFont: DEFAULT_HEADER_FONT,
            headerBold: false,
            savedHeaderFont: DEFAULT_HEADER_FONT,
            savedHeaderBold: false,
            headerTimer: 0,
            headerSaving: null
        };

        setupPageHeader(page);

        quill.on('text-change', (delta, oldDelta, source) => {
            if (source !== 'user') return;
            // 종이 밖으로 넘치는 입력은 되돌린다. (스크롤 대신 페이지를 한 장 더 쓴다)
            if (rejectOverflow(page, oldDelta)) return;
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

        // 형광펜으로 칠할 구간은 이 종이에서 시작한 드래그로만 잡는다.
        quill.root.addEventListener('mousedown', () => {
            markingPage = page;
        });
        quill.root.addEventListener('touchstart', () => {
            markingPage = page;
        }, {passive: true});

        return page;
    }

    function setActivePage(page) {
        if (!page || activePage === page) return;
        activePage = page;
        pages.forEach(other => other.sheet?.classList.toggle('is-active-page', other === page));
    }

    /**
     * 날짜 옆 한 줄 메모.
     * 종이 안의 내용이라 본문과 같은 debounce/상태 표시/flush 를 그대로 쓴다.
     */
    function setupPageHeader(page) {
        const input = page.sheet?.querySelector('.diary-sheet-header-input[data-header-url]');
        if (!input) return;

        page.headerInput = input;
        page.headerUrl = input.dataset.headerUrl;
        page.savedHeader = input.value;
        page.headerFont = input.dataset.headerFont || DEFAULT_HEADER_FONT;
        page.headerBold = input.dataset.headerBold === 'true';
        page.savedHeaderFont = page.headerFont;
        page.savedHeaderBold = page.headerBold;

        input.addEventListener('input', () => scheduleHeaderSave(page));
        // 한 줄만 쓰는 자리라 Enter 로 줄바꿈하지 않고 입력을 마친다.
        input.addEventListener('keydown', (event) => {
            if (event.key !== 'Enter') return;
            event.preventDefault();
            input.blur();
        });
        // 자리를 뜰 때는 debounce 를 기다리지 않고 바로 저장한다.
        input.addEventListener('blur', () => saveHeader(page));
    }

    /* ===== 자동저장 ===== */

    function isDirty(page) {
        return isContentDirty(page) || isHeaderDirty(page);
    }

    function isContentDirty(page) {
        return page.quill.getSemanticHTML() !== page.savedHtml;
    }

    /** 내용/글꼴/굵기 중 하나라도 바뀌면 저장 대상이다. */
    function isHeaderDirty(page) {
        if (page.headerInput == null) return false;
        return page.headerInput.value !== page.savedHeader
            || page.headerFont !== page.savedHeaderFont
            || page.headerBold !== page.savedHeaderBold;
    }

    /** 한 줄 메모가 있는 쪽. 편집 모드는 한 장만 펼치므로 사실상 그 장이다. */
    function headerPage() {
        return pages.find(page => page.headerInput != null) || null;
    }

    /**
     * 한 장에 담기는 줄 수는 종이가 정한다. 그 밖으로 넘치는 입력은 받지 않는다.
     *
     * 종이 안에서 스크롤하지 않기로 했으므로, 넘치는 글은 보이지 않을 뿐 사라지지 않는다.
     * 그래서 넘치게 만든 입력만 직전 상태로 되돌리고 커서를 그 자리에 남긴다.
     * 지우는 쪽은 막지 않는다. (이미 넘쳐 있는 글을 정리할 수 있어야 한다)
     */
    function rejectOverflow(page, previousContents) {
        const root = page.quill.root;
        if (root.scrollHeight <= root.clientHeight + 1) return false;
        // 글자 수가 줄어드는 변경(지우기)은 그대로 둔다
        if (page.quill.getLength() <= previousContents.length()) return false;

        const range = page.quill.getSelection();
        page.quill.setContents(previousContents, 'silent');
        if (range) {
            page.quill.setSelection(Math.min(range.index, page.quill.getLength() - 1), 0, 'silent');
        }
        showStatus('이 페이지가 가득 찼습니다. 새 페이지를 추가해 주세요.', true);
        return true;
    }

    function scheduleSave(page) {
        window.clearTimeout(page.timer);
        showStatus('작성 중…');
        page.timer = window.setTimeout(() => savePage(page), SAVE_DELAY);
    }

    function scheduleHeaderSave(page) {
        window.clearTimeout(page.headerTimer);
        showStatus('작성 중…');
        page.headerTimer = window.setTimeout(() => saveHeader(page), SAVE_DELAY);
    }

    /** 저장 결과를 boolean 으로 돌려준다. (실패해도 예외를 던지지 않는다) */
    function savePage(page) {
        window.clearTimeout(page.timer);
        if (page.saving) return page.saving;
        if (!isContentDirty(page)) {
            return Promise.resolve(true);
        }

        const html = page.quill.getSemanticHTML();
        showStatus('저장 중…');
        page.saving = sendField(page.url, {content: html})
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

    /**
     * 한 줄 메모 저장. 본문과 같은 방식으로 결과만 boolean 으로 돌려준다.
     * 실패해도 입력한 값은 화면에 그대로 남긴다. (savedHeader 를 갱신하지 않아 다음 flush 에서 다시 시도한다)
     */
    function saveHeader(page) {
        window.clearTimeout(page.headerTimer);
        if (page.headerSaving) return page.headerSaving;
        if (!isHeaderDirty(page)) {
            return Promise.resolve(true);
        }

        const pageHeader = page.headerInput.value;
        const pageHeaderFont = page.headerFont;
        const pageHeaderBold = page.headerBold;
        showStatus('저장 중…');
        page.headerSaving = sendField(page.headerUrl, {
            pageHeader,
            pageHeaderFont,
            pageHeaderBold: String(pageHeaderBold)
        })
            .then(() => {
                page.savedHeader = pageHeader;
                page.savedHeaderFont = pageHeaderFont;
                page.savedHeaderBold = pageHeaderBold;
                showStatus('저장됨');
                return true;
            })
            .catch(error => {
                showStatus(error.message || '저장하지 못했습니다', true);
                return false;
            })
            .finally(() => {
                page.headerSaving = null;
            });
        return page.headerSaving;
    }

    /** CSRF 토큰은 layout 의 meta 값을 그대로 쓴다. */
    async function sendField(url, fields) {
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
            body: new URLSearchParams(fields)
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

    /** 저장이 남아 있으면 먼저 끝내고, 실패하면 false 를 돌려준다. (본문 + 한 줄 메모) */
    async function flush() {
        for (const page of pages) {
            const contentSaved = await savePage(page);
            const headerSaved = await saveHeader(page);
            if (!contentSaved || !headerSaved) return false;
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

    /**
     * 서식 적용.
     * 툴바를 누르며 선택이 풀렸을 수 있으므로 (1) 종이로 focus 복귀 → (2) 눌리기 직전 range 복원
     * → (3) 마지막에 서식 적용 순서로 처리한다. 서식을 마지막에 적용해야 커서 서식(다음 입력에
     * 적용될 값)이 focus 복귀 때문에 초기화되지 않는다.
     */
    function applyFormat(name, value) {
        if (!activePage) return;
        // 다른 편집 도구를 고르면 들고 있던 형광펜은 자연스럽게 내려놓는다.
        setHighlightMode(null);
        const quill = activePage.quill;
        const range = quill.getSelection() || activePage.lastRange;

        quill.focus();
        if (range) {
            quill.setSelection(range.index, range.length, 'silent');
            activePage.lastRange = range;
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
        syncHighlightTrigger(formats);
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
                // applyFormat 이 종이로 focus 를 돌려주므로 바로 이어서 입력할 수 있다.
                applyFormat('font', font.value || false);
                toggle(false);
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

    /* ===== 한 줄 메모 서식 (글꼴 / 굵게) =====
       본문 Quill 서식과는 별개다. 여기서 고른 값은 page_header_font / page_header_bold 로만 간다. */

    function setupHeaderStyle() {
        const page = headerPage();
        const trigger = document.getElementById('diary-header-font-trigger');
        const list = document.getElementById('diary-header-font-list');
        const boldButton = document.getElementById('diary-header-bold');
        if (!page || !trigger || !list || !boldButton) return;

        // 목록은 본문 글꼴과 같은 FONTS 를 그대로 쓰고, 미리보기도 같은 클래스를 쓴다.
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
                setHeaderFont(page, font.value || DEFAULT_HEADER_FONT);
                toggle(false);
            });
            list.append(option);
        });

        const toggle = registerPopover(trigger, list, () => trigger.focus());
        trigger.addEventListener('mousedown', event => event.preventDefault());
        trigger.addEventListener('click', () => toggle(list.hidden));

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

        boldButton.addEventListener('mousedown', event => event.preventDefault());
        boldButton.addEventListener('click', () => setHeaderBold(page, !page.headerBold));

        syncHeaderStyle(page);
    }

    function setHeaderFont(page, font) {
        if (page.headerFont === font) return;
        page.headerFont = font;
        syncHeaderStyle(page);
        scheduleHeaderSave(page);
    }

    function setHeaderBold(page, bold) {
        if (page.headerBold === bold) return;
        page.headerBold = bold;
        syncHeaderStyle(page);
        scheduleHeaderSave(page);
    }

    /** 고른 글꼴/굵기를 메모 입력칸과 툴바 표시에 함께 반영한다. */
    function syncHeaderStyle(page) {
        const font = page.headerFont;
        const isDefault = !font || font === DEFAULT_HEADER_FONT;
        const label = isDefault
            ? '기본'
            : (FONTS.find(item => item.value === font)?.label ?? '기본');

        page.headerInput.className = 'diary-sheet-header-input'
            + (isDefault ? '' : ` diary-font-${font}`)
            + (page.headerBold ? ' is-bold' : '');

        const trigger = document.getElementById('diary-header-font-trigger');
        if (trigger) {
            trigger.textContent = label;
            trigger.className = 'diary-font-trigger is-compact'
                + (trigger.classList.contains('is-active') ? ' is-active' : '')
                + (isDefault ? '' : ` diary-font-${font}`);
        }
        document.querySelectorAll('#diary-header-font-list .diary-font-option')
            .forEach(option => {
                const selected = (option.dataset.fontValue || DEFAULT_HEADER_FONT)
                    === (isDefault ? DEFAULT_HEADER_FONT : font);
                option.setAttribute('aria-selected', selected ? 'true' : 'false');
                option.classList.toggle('is-selected', selected);
            });

        const boldButton = document.getElementById('diary-header-bold');
        if (boldButton) {
            boldButton.classList.toggle('is-active', page.headerBold);
            boldButton.setAttribute('aria-pressed', page.headerBold ? 'true' : 'false');
        }
    }

    /* ===== 형광펜 (색을 먼저 고르고 문장을 드래그해서 칠한다) ===== */

    /**
     * 형광펜은 Quill 의 background 서식을 그대로 쓴다. (저장/읽기 모드 표시는 그대로)
     * 다만 쓰는 순서가 실제 형광펜과 같다.
     * 색을 고르면 형광펜을 든 상태(highlightMode)가 되고, 그 뒤로는 본문에서 문장을
     * 드래그해 손을 뗄 때마다 그 구간이 칠해진다. 모드는 계속 켜져 있어 여러 문장을 이어 칠할 수 있다.
     */
    function setupHighlightPicker() {
        const trigger = document.getElementById('diary-highlight-trigger');
        const palette = document.getElementById('diary-highlight-palette');
        if (!trigger || !palette) return;

        highlightTrigger = trigger;
        highlightSwatch = document.getElementById('diary-highlight-current');

        HIGHLIGHTS.forEach(highlight => palette.append(
            createOption(highlight.value, highlight.name, `${highlight.name} 형광펜`)));
        // 이미 칠해 둔 곳의 형광펜을 지우는 항목
        palette.append(createOption('', '형광펜 없음', '형광펜 지우기'));

        const toggle = registerPopover(trigger, palette, () => trigger.focus());
        trigger.addEventListener('mousedown', event => event.preventDefault());
        trigger.addEventListener('click', () => {
            // 형광펜을 들고 있는 중에 다시 누르면 내려놓는다.
            if (highlightMode) {
                setHighlightMode(null);
                toggle(false);
                return;
            }
            toggle(palette.hidden);
        });

        // Esc 로도 형광펜을 내려놓는다. (팝오버 닫기는 공통 처리가 따로 맡는다)
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape' && highlightMode) setHighlightMode(null);
        });

        // 본문에서 드래그가 끝나는 순간 그 구간을 칠한다.
        document.addEventListener('mouseup', finishMarking);
        document.addEventListener('touchend', finishMarking);

        function finishMarking() {
            const page = markingPage;
            markingPage = null;
            if (!highlightMode || !page) return;
            // 브라우저가 선택 영역을 확정한 뒤에 읽는다.
            window.setTimeout(() => paintSelection(page), 0);
        }

        // 방향키로 색을 훑을 수 있게 한다. (글꼴 목록과 같은 조작)
        palette.addEventListener('keydown', (event) => {
            if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
            event.preventDefault();
            const options = Array.from(palette.querySelectorAll('.diary-highlight-option'));
            const current = options.indexOf(document.activeElement);
            const step = event.key === 'ArrowDown' ? 1 : -1;
            const next = current < 0 ? 0 : (current + step + options.length) % options.length;
            options[next]?.focus();
        });
        trigger.addEventListener('keydown', (event) => {
            if (event.key !== 'ArrowDown' || palette.hidden) return;
            event.preventDefault();
            palette.querySelector('.diary-highlight-option')?.focus();
        });

        // 처음에는 커서가 없으므로 형광펜 없음 상태로 시작한다.
        syncHighlightTrigger(selectionFormats() || {});

        function createOption(value, label, description) {
            const option = document.createElement('button');
            option.type = 'button';
            option.className = 'diary-highlight-option' + (value ? '' : ' is-none');
            option.dataset.highlightValue = value;
            option.title = description;
            option.setAttribute('role', 'option');
            option.setAttribute('aria-selected', 'false');
            option.setAttribute('aria-label', description);

            const swatch = document.createElement('span');
            swatch.className = 'diary-highlight-swatch';
            swatch.setAttribute('aria-hidden', 'true');
            if (value) swatch.style.backgroundColor = value;

            const name = document.createElement('span');
            name.className = 'diary-highlight-name';
            name.textContent = label;

            option.append(swatch, name);
            option.addEventListener('mousedown', event => event.preventDefault());
            option.addEventListener('click', () => {
                // 색을 고르면 형광펜을 든 상태가 된다. 칠하기는 다음 드래그부터.
                startHighlighting({value, name: label});
                toggle(false);
            });
            return option;
        }
    }

    /** 형광펜을 들거나(highlight) 내려놓는다(null). */
    function setHighlightMode(highlight) {
        highlightMode = highlight;
        if (!highlight) markingPage = null;
        syncHighlightTrigger(selectionFormats() || {});
    }

    /**
     * 고른 색으로 형광펜을 든다.
     * 이미 잡아 둔 선택 영역이 있으면 그 구간은 바로 칠하고, 형광펜은 계속 들고 있는다.
     */
    function startHighlighting(highlight) {
        setHighlightMode(highlight);

        const page = activePage;
        if (!page) return;
        const range = page.quill.getSelection() || page.lastRange;
        if (!range || range.length === 0) return;

        // 팔레트를 누르며 선택이 풀렸을 수 있으므로 종이로 focus 를 돌려준 뒤 칠한다.
        page.quill.focus();
        paintRange(page, range);
    }

    /** 드래그가 끝난 구간을 칠한다. 선택 길이가 0이면 아무것도 하지 않는다. */
    function paintSelection(page) {
        if (!highlightMode) return;
        const range = page.quill.getSelection();
        if (!range || range.length === 0) return;
        setActivePage(page);
        paintRange(page, range);
    }

    /**
     * 실제 칠하기.
     * formatText 로 그 구간만 바꾸므로 커서 서식(다음에 입력할 글자)은 건드리지 않고,
     * 칠한 뒤에도 선택 영역과 focus 가 그대로 남는다.
     */
    function paintRange(page, range) {
        page.quill.formatText(range.index, range.length,
            'background', highlightMode.value || false, 'user');
        page.quill.setSelection(range.index, range.length, 'silent');
        page.lastRange = range;
        syncToolbar();
        // 칠하기도 본문 변경이므로 기존 자동저장 흐름을 그대로 탄다.
        scheduleSave(page);
    }

    /**
     * 형광펜 버튼 표시.
     * 형광펜을 들고 있으면 그 색을, 아니면 커서 위치의 형광펜 색을 보여준다.
     */
    function syncHighlightTrigger(formats) {
        if (!highlightTrigger) return;
        const current = highlightMode
            ? (highlightMode.value ? matchHighlight(highlightMode.value) : null)
            : matchHighlight(formats.background);
        const marking = Boolean(highlightMode);

        highlightTrigger.classList.toggle('is-marking', marking);
        highlightTrigger.classList.toggle('has-highlight', Boolean(current));
        highlightTrigger.title = marking
            ? `형광펜 ${highlightMode.name} · 칠할 문장을 드래그하세요 (Esc 로 종료)`
            : (current ? `형광펜 ${current.name}` : '형광펜');
        highlightTrigger.setAttribute('aria-pressed', marking ? 'true' : 'false');
        if (highlightSwatch) {
            highlightSwatch.style.backgroundColor = current ? current.value : 'transparent';
        }

        // 들고 있는 형광펜(지우개 포함)을 팔레트에서도 고른 상태로 보여준다.
        const selectedValue = highlightMode
            ? highlightMode.value
            : (current ? current.value : '');
        document.querySelectorAll('#diary-highlight-palette .diary-highlight-option')
            .forEach(option => {
                const selected = option.dataset.highlightValue === selectedValue;
                option.setAttribute('aria-selected', selected ? 'true' : 'false');
                option.classList.toggle('is-selected', selected);
            });
    }

    /** Quill 이 rgb() 로 돌려줘도 같은 색으로 알아보게 맞춘다. */
    function matchHighlight(value) {
        const key = colorKey(value);
        return key ? HIGHLIGHTS.find(highlight => colorKey(highlight.value) === key) ?? null : null;
    }

    function colorKey(value) {
        if (typeof value !== 'string') return null;

        const hex = value.trim().match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);
        if (hex) {
            const digits = hex[1].length === 3
                ? hex[1].split('').map(digit => digit + digit).join('')
                : hex[1];
            return digits.toLowerCase();
        }

        const rgb = value.trim().match(/^rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)$/i);
        return rgb
            ? [rgb[1], rgb[2], rgb[3]]
                .map(part => Number(part).toString(16).padStart(2, '0')).join('')
            : null;
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

        // 맨 앞에 '최근' 탭을 두고, 처음 보여주는 카테고리는 기존과 같게 유지한다.
        const categories = [RECENT_EMOJI_CATEGORY, ...EMOJI_CATEGORIES];
        let shownCategory = EMOJI_CATEGORIES[0];

        categories.forEach(category => {
            const tab = document.createElement('button');
            tab.type = 'button';
            tab.className = 'diary-emoji-tab';
            tab.textContent = category.icon;
            tab.title = category.name;
            tab.setAttribute('role', 'tab');
            tab.setAttribute('aria-label', `${category.name} 이모지`);
            tab.setAttribute('aria-selected', 'false');
            tab.addEventListener('mousedown', event => event.preventDefault());
            tab.addEventListener('click', () => showCategory(category));
            tabs.append(tab);
        });
        showCategory(shownCategory);

        button.addEventListener('mousedown', event => event.preventDefault());
        button.addEventListener('click', () => togglePopover(popover.hidden));

        function showCategory(category) {
            shownCategory = category;
            Array.from(tabs.children).forEach(tab => {
                const selected = tab.title === category.name;
                tab.classList.toggle('is-active', selected);
                tab.setAttribute('aria-selected', selected ? 'true' : 'false');
            });

            const emojis = category === RECENT_EMOJI_CATEGORY
                ? readRecentEmojis() : category.emojis;

            grid.replaceChildren();
            grid.scrollTop = 0;
            if (emojis.length === 0) {
                const empty = document.createElement('p');
                empty.className = 'diary-emoji-empty';
                empty.textContent = '아직 사용한 이모지가 없어요.';
                grid.append(empty);
                return;
            }

            emojis.forEach(emoji => {
                const item = document.createElement('button');
                item.type = 'button';
                item.className = 'diary-emoji-item';
                item.textContent = emoji;
                item.title = `이모지 ${emoji}`;
                item.setAttribute('aria-label', `이모지 ${emoji} 넣기`);
                item.addEventListener('mousedown', event => event.preventDefault());
                item.addEventListener('click', () => {
                    insertEmoji(emoji);
                    // picker 로 직접 고른 것만 최근 목록에 남긴다.
                    rememberRecentEmoji(emoji);
                    if (shownCategory === RECENT_EMOJI_CATEGORY) showCategory(RECENT_EMOJI_CATEGORY);
                    togglePopover(false);
                });
                grid.append(item);
            });
        }
    }

    /** 최근 사용 이모지 읽기. localStorage 를 못 쓰면 빈 목록으로 조용히 넘어간다. */
    function readRecentEmojis() {
        try {
            const stored = JSON.parse(window.localStorage.getItem(RECENT_EMOJI_KEY) || '[]');
            return Array.isArray(stored)
                ? stored.filter(emoji => typeof emoji === 'string').slice(0, RECENT_EMOJI_LIMIT)
                : [];
        } catch (error) {
            return [];
        }
    }

    /** 고른 이모지를 맨 앞으로 올린다. 같은 이모지는 기존 자리에서 빼고 최대 30개만 남긴다. */
    function rememberRecentEmoji(emoji) {
        const next = [emoji, ...readRecentEmojis().filter(item => item !== emoji)]
            .slice(0, RECENT_EMOJI_LIMIT);
        try {
            window.localStorage.setItem(RECENT_EMOJI_KEY, JSON.stringify(next));
        } catch (error) {
            // 저장이 막혀 있어도 이모지 넣기 자체는 그대로 동작한다.
        }
        return next;
    }

    function insertEmoji(emoji) {
        if (!activePage) return;
        // 이모지를 넣는 것도 다른 편집 도구를 고른 것이므로 형광펜을 내려놓는다.
        setHighlightMode(null);
        const quill = activePage.quill;
        const range = quill.getSelection() || activePage.lastRange
            || {index: Math.max(0, quill.getLength() - 1), length: 0};

        // 넣은 뒤 바로 이어서 쓸 수 있게 종이로 focus 를 돌려준다.
        quill.focus();
        quill.deleteText(range.index, range.length, 'user');
        quill.insertText(range.index, emoji, 'user');
        quill.setSelection(range.index + emoji.length, 0, 'silent');
        activePage.lastRange = {index: range.index + emoji.length, length: 0};
        scheduleSave(activePage);
    }
});
