/**
 * 다이어리 편집 화면의 스티커 붙이기.
 * picker 에서 고른 스티커 id 만 서버로 보내고, 실제 이미지 경로는 서버가 허용 목록에서 정한다.
 * 분류 탭/스티커 목록은 서버가 manifest(json/diary_stickers.json)대로 그려 주므로 여기서 목록을 들지 않는다.
 * '최근' 탭만 이 브라우저(localStorage)에 남는 화면용 분류로, 서버 목록에 있는 스티커만 담는다.
 * 서버가 만들어 준 요소를 지금 보고 있는 캔버스에 바로 그려 화면을 새로 고치지 않는다.
 * (이동/크기/회전/겹침 순서 저장은 사진과 같은 endpoint 를 그대로 쓴다)
 */
document.addEventListener('DOMContentLoaded', () => {
    const button = document.getElementById('diary-sticker-button');
    const popover = document.getElementById('diary-sticker-popover');
    if (!button || !popover) return;

    const status = document.getElementById('diary-sticker-status');
    const createUrl = button.dataset.createUrl;
    // 붙일 자리는 두 곳이다 — 페이지 편집의 종이, 그리고 표지 디자인 편집의 표지.
    // 붙이는 절차와 만드는 마크업은 같고, 보낼 주소만 버튼이 알려 준다.
    const canvas = document.querySelector('.diary-book-single .diary-canvas')
        || document.querySelector('.diary-cover-canvas.is-editable .diary-cover-surface');
    if (!createUrl || !canvas) return;

    /** 최근 쓴 스티커는 이 브라우저에만 남긴다. (이모지 최근 목록과 같은 방식, 서버 저장 없음) */
    const RECENT_KEY = 'travelDiaryRecentStickers';
    const RECENT_LIMIT = 30;
    const RECENT_CATEGORY = 'recent';

    const recentGrid = document.getElementById('diary-sticker-grid-recent');
    const recentEmpty = document.getElementById('diary-sticker-recent-empty');
    // 지금 목록(manifest)에 있는 스티커만 id 로 찾아 쓴다.
    const stickers = new Map();
    popover.querySelectorAll('.diary-sticker-grid:not(#diary-sticker-grid-recent) '
        + '.diary-sticker-option').forEach((option) => {
        stickers.set(option.dataset.stickerId, option);
    });

    let busy = false;

    button.addEventListener('click', () => toggle(popover.hidden));

    document.addEventListener('click', (event) => {
        if (popover.hidden) return;
        if (!popover.contains(event.target) && event.target !== button
            && !button.contains(event.target)) {
            toggle(false);
        }
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && !popover.hidden) {
            toggle(false);
            button.focus();
        }
    });

    // 분류 탭과 스티커 버튼 모두 서버가 그려 준 것을 그대로 쓴다.
    // (스티커가 늘어나도 이 로직은 바뀌지 않는다)
    popover.querySelectorAll('.diary-sticker-tab').forEach((tab) => {
        tab.addEventListener('click', () => showCategory(tab.dataset.stickerCategory));
    });
    popover.querySelectorAll('.diary-sticker-option').forEach((option) => {
        option.addEventListener('click', () => attach(option.dataset.stickerId));
    });

    // 마스킹테이프 안의 작은 갈래(전체/일반/투명). 그 묶음 안에서만 걸러 보여 준다.
    popover.querySelectorAll('.diary-sticker-subtab').forEach((subtab) => {
        subtab.addEventListener('click', () => showTapeType(subtab));
    });

    toggle(false);

    // 최근에 쓴 것이 있으면 그 탭부터, 없으면 지금까지처럼 첫 분류부터 보여 준다.
    const recent = renderRecent();
    if (recent.length > 0) showCategory(RECENT_CATEGORY);

    /** 고른 분류의 그리드만 남기고 나머지는 감춘다. */
    function showCategory(categoryId) {
        popover.querySelectorAll('.diary-sticker-tab').forEach((tab) => {
            const active = tab.dataset.stickerCategory === categoryId;
            tab.classList.toggle('is-active', active);
            tab.setAttribute('aria-selected', active ? 'true' : 'false');
        });
        popover.querySelectorAll('.diary-sticker-grid').forEach((grid) => {
            grid.hidden = grid.dataset.stickerCategory !== categoryId;
            if (!grid.hidden) grid.scrollTop = 0;
        });
        showStatus('');
    }

    /** 고른 갈래의 테이프만 남긴다. (같은 묶음 안에서만 걸러 최근 탭 등에는 영향이 없다) */
    function showTapeType(subtab) {
        const grid = subtab.closest('.diary-sticker-grid');
        const chosen = subtab.dataset.tapeType;
        if (!grid) return;

        grid.querySelectorAll('.diary-sticker-subtab').forEach((other) => {
            const active = other === subtab;
            other.classList.toggle('is-active', active);
            other.setAttribute('aria-pressed', active ? 'true' : 'false');
        });
        grid.querySelectorAll('.diary-sticker-option').forEach((option) => {
            option.hidden = chosen !== 'ALL' && option.dataset.tapeType !== chosen;
        });
        grid.scrollTop = 0;
        showStatus('');
    }

    /**
     * 열고 닫기.
     * 다른 툴바 팝오버(글꼴/형광펜/이모지)도 document 클릭으로 닫히므로,
     * 스티커 버튼을 누르는 것만으로 서로 동시에 열리지 않는다.
     */
    function toggle(open) {
        popover.hidden = !open;
        popover.classList.toggle('is-open', open);
        button.classList.toggle('is-active', open);
        button.setAttribute('aria-expanded', open ? 'true' : 'false');
        if (!open) showStatus('');
    }

    function showStatus(text, isError = false) {
        if (!status) return;
        status.textContent = text;
        status.classList.toggle('is-error', isError);
    }

    /**
     * 최근 목록 읽기. localStorage 를 못 쓰면 빈 목록으로 조용히 넘어간다.
     * 지금 목록에 없는 스티커(빠졌거나 id 가 바뀐 것)는 여기서 걸러 다시 저장해 둔다.
     */
    function readRecent() {
        let stored = [];
        try {
            const raw = JSON.parse(window.localStorage.getItem(RECENT_KEY) || '[]');
            if (Array.isArray(raw)) stored = raw.filter((id) => typeof id === 'string');
        } catch (error) {
            return [];
        }

        const kept = [];
        stored.forEach((id) => {
            if (stickers.has(id) && !kept.includes(id)) kept.push(id);
        });
        const next = kept.slice(0, RECENT_LIMIT);
        if (next.length !== stored.length) save(next);
        return next;
    }

    /** 방금 쓴 스티커를 맨 앞으로 올린다. 같은 스티커는 기존 자리에서 빼고 30개만 남긴다. */
    function rememberRecent(stickerId) {
        const next = [stickerId, ...readRecent().filter((id) => id !== stickerId)]
            .slice(0, RECENT_LIMIT);
        save(next);
        return next;
    }

    function save(ids) {
        try {
            window.localStorage.setItem(RECENT_KEY, JSON.stringify(ids));
        } catch (error) {
            // 저장이 막혀 있어도 스티커 붙이기 자체는 그대로 동작한다.
        }
    }

    /**
     * 최근 탭 다시 그리기.
     * 이미지/이름은 서버가 그려 둔 버튼을 그대로 복사해 쓰므로 지난 경로를 믿지 않는다.
     */
    function renderRecent() {
        const ids = readRecent();
        if (!recentGrid) return ids;

        recentGrid.replaceChildren();
        if (ids.length === 0) {
            if (recentEmpty) recentGrid.append(recentEmpty);
            return ids;
        }

        ids.forEach((id) => {
            const option = stickers.get(id).cloneNode(true);
            option.addEventListener('click', () => attach(id));
            recentGrid.append(option);
        });
        return ids;
    }

    async function attach(stickerId) {
        if (!stickerId || busy) return;
        busy = true;
        showStatus('붙이는 중…');

        try {
            // 본문에 아직 저장되지 않은 입력이 있으면 먼저 저장한다. (기존 flush 흐름 재사용)
            if (window.diaryEditor && window.diaryEditor.hasPendingChanges()) {
                const saved = await window.diaryEditor.flush();
                if (!saved) throw new Error('본문을 저장하지 못해 스티커를 붙이지 못했습니다.');
            }

            const created = await createSticker(stickerId);
            const item = renderSticker(created);
            canvas.append(item);
            // 되풀이형 테이프는 붙는 즉시 새로고침 뒤와 같은 모습으로 그린다. (같은 렌더러)
            window.diaryTape?.render(item);
            // 새로 붙은 스티커도 기존 드래그/크기/회전/겹침 조작을 그대로 쓴다.
            window.diaryCanvas?.register(item);
            window.diaryCanvas?.select(item);
            // 실제로 붙인 것만 최근 목록에 남기고, 최근 탭도 바로 다시 그린다.
            rememberRecent(stickerId);
            renderRecent();
            showStatus('붙였습니다');
        } catch (error) {
            showStatus(error.message || '스티커를 붙이지 못했습니다', true);
        } finally {
            busy = false;
        }
    }

    /** CSRF 토큰은 layout 의 meta 값을 그대로 쓴다. (기존 저장 요청과 같은 방식) */
    async function createSticker(stickerId) {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (!csrfToken || !csrfHeader) {
            throw new Error('보안 토큰을 확인할 수 없어 스티커를 붙이지 못했습니다');
        }

        const response = await fetch(createUrl, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                [csrfHeader]: csrfToken
            },
            body: new URLSearchParams({sticker: stickerId})
        });

        if (response.status === 401) {
            const redirect = window.location.pathname + window.location.search;
            window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`;
            throw new Error('로그인이 필요합니다');
        }
        if (!response.ok) {
            let message = '스티커를 붙이지 못했습니다';
            if ((response.headers.get('Content-Type') || '').includes('application/json')) {
                const payload = await response.json();
                message = payload.message || message;
            }
            throw new Error(message);
        }
        return response.json();
    }

    /** 서버 렌더링 결과와 같은 마크업을 만든다. (detail.html 의 스티커 figure 와 동일) */
    function renderSticker(sticker) {
        const item = document.createElement('figure');
        item.className = 'diary-canvas-item diary-canvas-sticker diary-sticker';
        item.dataset.elementId = String(sticker.id);
        item.dataset.elementType = 'STICKER';
        // 방금 붙인 마스킹테이프도 서버 렌더링과 같은 표시를 달아 준다. (길이만 늘리는 조작)
        if (sticker.maskingTape) item.dataset.stickerKind = 'masking-tape';
        // 되풀이해서 그리는 스티커면 조각 경로도 서버 렌더링과 똑같이 실어 둔다.
        if (sticker.repeat && sticker.repeat.center) {
            item.dataset.tapeLeft = sticker.repeat.left;
            item.dataset.tapeCenter = sticker.repeat.center;
            item.dataset.tapeRight = sticker.repeat.right;
        }
        item.dataset.positionX = String(sticker.positionX);
        item.dataset.positionY = String(sticker.positionY);
        item.dataset.width = String(sticker.width);
        item.dataset.height = String(sticker.height);
        item.dataset.rotation = String(sticker.rotation);
        item.dataset.zIndex = String(sticker.zIndex);
        item.dataset.positionUrl = sticker.urls.position;
        item.dataset.sizeUrl = sticker.urls.size;
        item.dataset.rotationUrl = sticker.urls.rotation;
        item.dataset.layerUrl = sticker.urls.layer;
        item.style.left = `${sticker.positionX * 100}%`;
        item.style.top = `${sticker.positionY * 100}%`;
        item.style.width = `${sticker.width * 100}%`;
        item.style.height = `${sticker.height * 100}%`;
        item.style.transform = `rotate(${sticker.rotation}deg)`;
        item.style.setProperty('--diary-item-rotation', `${sticker.rotation}deg`);
        item.style.zIndex = String(sticker.zIndex);

        const image = document.createElement('img');
        image.src = sticker.imageUrl;
        image.alt = '여행일기 스티커';
        item.append(image, rotateHandle(), resizeHandle(),
            layerActions(sticker.urls.delete));
        return item;
    }

    /**
     * 떼기 버튼. 실제 삭제 요청과 화면에서 빼는 일은 diary-canvas-drag.js 가 맡는다.
     * (서버 렌더링 스티커와 같은 data-delete-url / data-delete-confirm 만 실어 준다)
     */
    function deleteButton(deleteUrl) {
        const action = document.createElement('button');
        action.type = 'button';
        action.className = 'diary-layer-action is-danger';
        action.dataset.deleteUrl = deleteUrl;
        action.dataset.deleteConfirm = '이 스티커를 떼시겠습니까?';
        action.textContent = '떼기';
        return action;
    }

    function rotateHandle() {
        return handleButton('diary-rotate-handle', '회전');
    }

    function resizeHandle() {
        return handleButton('diary-resize-handle', '크기 조절');
    }

    function handleButton(className, label) {
        const handle = document.createElement('button');
        handle.type = 'button';
        handle.className = className;
        handle.setAttribute('aria-label', label);
        return handle;
    }

    /** 사진과 같은 액션 줄: 뒤로 / 앞으로 / 떼기 (스티커 바깥 아래에 놓인다) */
    function layerActions(deleteUrl) {
        const actions = document.createElement('div');
        actions.className = 'diary-layer-actions';
        [['BACKWARD', '뒤로'], ['FORWARD', '앞으로']].forEach(([direction, label]) => {
            const action = document.createElement('button');
            action.type = 'button';
            action.className = 'diary-layer-action';
            action.dataset.layerDirection = direction;
            action.textContent = label;
            actions.append(action);
        });
        actions.append(deleteButton(deleteUrl));
        return actions;
    }
});
