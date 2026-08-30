/**
 * 다이어리 편집 화면의 라벨 / 떡메모지 붙이기.
 *
 * 꾸미기 팝오버는 스티커 것을 그대로 쓰고, 여기서는 위쪽 갈래(스티커/라벨/메모지) 전환과
 * 라벨·메모지 고르기만 맡는다. 스티커 쪽 코드(diary-sticker-picker.js)는 건드리지 않는다.
 * 열고 닫기·바깥 클릭·Esc 는 스티커 쪽이 이미 팝오버 단위로 처리한다.
 *
 * 고른 디자인 code 만 서버로 보내고, 모양 class·자리·크기는 서버가 정해 돌려준다.
 * 화면이 code 를 class 로 바꾸는 표를 따로 두지 않는다(서버의 styleClass 를 그대로 쓴다).
 * 서버가 만들어 준 요소를 지금 보고 있는 캔버스에 바로 그려 화면을 새로 고치지 않는다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const button = document.getElementById('diary-sticker-button');
    const popover = document.getElementById('diary-sticker-popover');
    if (!button || !popover) return;

    const status = document.getElementById('diary-sticker-status');
    const createUrl = button.dataset.noteCreateUrl;
    const canvas = document.querySelector('.diary-book-single .diary-canvas');
    if (!createUrl || !canvas) return;

    let busy = false;

    // 위쪽 갈래. 아래 묶음 하나만 남기고 나머지는 감춘다.
    popover.querySelectorAll('.diary-decor-tab').forEach((tab) => {
        tab.addEventListener('click', () => showPanel(tab.dataset.decorTab));
    });
    popover.querySelectorAll('.diary-note-option').forEach((option) => {
        option.addEventListener('click', () => attach(option));
    });

    /*
      갈래(라벨 / 메모지)마다 고른 색을 따로 기억한다.
      라벨은 아이보리로, 메모지는 하늘로 쓰던 사람이 갈래를 오갈 때마다
      다시 고르지 않아도 되게 한다.
      처음에는 눌려 있는 색(맨 앞의 기본색)을 그대로 새겨 둔다 — "색 없음" 자리는 없다.
    */
    const chosenColors = new Map();

    popover.querySelectorAll('.diary-note-swatch').forEach((swatch) => {
        swatch.addEventListener('click', () => chooseColor(swatch));
    });
    popover.querySelectorAll('.diary-decor-panel').forEach((panel) => {
        const active = panel.querySelector('.diary-note-swatch.is-active');
        if (!active) return;
        chosenColors.set(panel.dataset.decorPanel, active.dataset.noteColor || '');
        // 미리보기도 처음부터 그 색으로 보여 준다.
        paintPreviews(panel, colorClassOf(active));
    });

    /** 고른 색을 그 갈래에 새겨 두고, 그 갈래의 미리보기에 곧바로 입힌다. */
    function chooseColor(swatch) {
        const panel = swatch.closest('.diary-decor-panel');
        if (!panel) return;

        chosenColors.set(panel.dataset.decorPanel, swatch.dataset.noteColor || '');
        panel.querySelectorAll('.diary-note-swatch').forEach((other) => {
            const active = other === swatch;
            other.classList.toggle('is-active', active);
            other.setAttribute('aria-pressed', active ? 'true' : 'false');
        });
        paintPreviews(panel, colorClassOf(swatch));
        showStatus('');
    }

    /*
      스와치가 이미 달고 있는 색 class 를 그대로 가져다 쓴다.
      code 를 class 로 바꾸는 표를 화면에 따로 두지 않는다(서버가 정한 이름 하나뿐이다).
    */
    function colorClassOf(element) {
        return Array.from(element.classList)
            .find((name) => name.startsWith('diary-note-color-')) || '';
    }

    /** 미리보기의 색만 갈아 끼운다. 모양 class 는 건드리지 않는다. */
    function paintPreviews(panel, colorClass) {
        panel.querySelectorAll('.diary-note-preview').forEach((preview) => {
            const previous = colorClassOf(preview);
            if (previous) preview.classList.remove(previous);
            if (colorClass) preview.classList.add(colorClass);
        });
    }

    /**
     * 고른 갈래의 묶음만 남긴다.
     * 스티커 묶음 안쪽(분류 탭·최근·마스킹테이프)은 건드리지 않으므로,
     * 갈래를 오갔다 돌아와도 보고 있던 스티커 분류가 그대로 남는다.
     */
    function showPanel(name) {
        popover.querySelectorAll('.diary-decor-tab').forEach((tab) => {
            const active = tab.dataset.decorTab === name;
            tab.classList.toggle('is-active', active);
            tab.setAttribute('aria-selected', active ? 'true' : 'false');
        });
        popover.querySelectorAll('.diary-decor-panel').forEach((panel) => {
            panel.hidden = panel.dataset.decorPanel !== name;
        });
        showStatus('');
    }

    function showStatus(text, isError = false) {
        if (!status) return;
        status.textContent = text;
        status.classList.toggle('is-error', isError);
    }

    async function attach(option) {
        const styleType = option?.dataset.noteStyle;
        if (!styleType || busy) return;
        // 그 갈래에서 고른 색으로 붙인다. 고르지 않았으면 서버가 모양의 기본색을 쓴다.
        const colorType = chosenColors.get(
            option.closest('.diary-decor-panel')?.dataset.decorPanel) || '';
        busy = true;
        showStatus('붙이는 중…');

        try {
            // 본문에 아직 저장되지 않은 입력이 있으면 먼저 저장한다. (스티커와 같은 흐름)
            if (window.diaryEditor && window.diaryEditor.hasPendingChanges()) {
                const saved = await window.diaryEditor.flush();
                if (!saved) throw new Error('본문을 저장하지 못해 라벨을 붙이지 못했습니다.');
            }

            const created = await createNote(styleType, colorType);
            const item = renderNote(created);
            canvas.append(item);
            // 새로 붙은 라벨도 기존 드래그/크기/회전/겹침/떼기 조작을 그대로 쓴다.
            window.diaryCanvas?.register(item);
            window.diaryCanvas?.select(item);
            // 붙이자마자 바로 쓸 수 있게 연다. (빈 라벨을 두 번 눌러야 하지 않게)
            window.diaryNoteText?.begin(item);
            showStatus('붙였습니다');
        } catch (error) {
            showStatus(error.message || '라벨을 붙이지 못했습니다', true);
        } finally {
            busy = false;
        }
    }

    /** CSRF 토큰은 layout 의 meta 값을 그대로 쓴다. (스티커 붙이기와 같은 방식) */
    async function createNote(styleType, colorType) {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (!csrfToken || !csrfHeader) {
            throw new Error('보안 토큰을 확인할 수 없어 라벨을 붙이지 못했습니다');
        }

        // 보내는 것은 고른 모양과 색뿐이다. 나머지는 모두 서버가 정한다.
        const body = new URLSearchParams({style: styleType});
        // 색을 고르지 않았으면 아예 보내지 않는다. 서버가 그 모양의 기본색을 쓴다.
        if (colorType) body.append('color', colorType);

        const response = await fetch(createUrl, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                [csrfHeader]: csrfToken
            },
            body
        });

        if (response.status === 401) {
            const redirect = window.location.pathname + window.location.search;
            window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`;
            throw new Error('로그인이 필요합니다');
        }
        if (!response.ok) {
            let message = '라벨을 붙이지 못했습니다';
            if ((response.headers.get('Content-Type') || '').includes('application/json')) {
                const payload = await response.json();
                message = payload.message || message;
            }
            throw new Error(message);
        }
        return response.json();
    }

    /** 서버 렌더링 결과와 같은 마크업을 만든다. (detail.html 의 NOTE figure 와 동일) */
    function renderNote(note) {
        const item = document.createElement('figure');
        /*
          모양과 색 class 는 서버가 준 것을 그대로 쓴다.
          여기서 code 를 다시 class 로 바꾸는 표를 두지 않는다.
          색이 없는 요소는 빈 문자열이 와서 그 모양의 기본색으로 그려진다.
        */
        item.className = `diary-canvas-item diary-note ${note.styleClass} ${note.colorClass}`
            .trim();
        item.dataset.elementId = String(note.id);
        item.dataset.elementType = note.elementType;
        item.dataset.noteStyle = note.styleType;
        if (note.colorType) item.dataset.noteColor = note.colorType;
        item.dataset.positionX = String(note.positionX);
        item.dataset.positionY = String(note.positionY);
        item.dataset.width = String(note.width);
        item.dataset.height = String(note.height);
        item.dataset.rotation = String(note.rotation);
        item.dataset.zIndex = String(note.zIndex);
        item.dataset.positionUrl = note.urls.position;
        item.dataset.sizeUrl = note.urls.size;
        item.dataset.rotationUrl = note.urls.rotation;
        item.dataset.layerUrl = note.urls.layer;
        // 글쓰기는 편집 화면에서만 있다. 이 주소가 있는 요소만 고쳐 쓸 수 있다.
        item.dataset.textUrl = note.urls.text;
        item.style.left = `${note.positionX * 100}%`;
        item.style.top = `${note.positionY * 100}%`;
        item.style.width = `${note.width * 100}%`;
        item.style.height = `${note.height * 100}%`;
        item.style.transform = `rotate(${note.rotation}deg)`;
        item.style.setProperty('--diary-item-rotation', `${note.rotation}deg`);
        item.style.zIndex = String(note.zIndex);

        const surface = document.createElement('div');
        surface.className = 'diary-note-surface';
        const text = document.createElement('div');
        text.className = 'diary-note-text';
        // 붙인 직후에는 빈 글이다. 보기 글을 대신 채워 넣지 않는다.
        text.textContent = note.textContent;
        surface.append(text);

        item.append(surface, rotateHandle(), resizeHandle(),
            layerActions(note.urls.delete));
        return item;
    }

    /**
     * 떼기 버튼. 실제 삭제 요청과 화면에서 빼는 일은 diary-canvas-drag.js 가 맡는다.
     * (서버 렌더링 NOTE 와 같은 data-delete-url / data-delete-confirm 만 실어 준다)
     */
    function deleteButton(deleteUrl) {
        const action = document.createElement('button');
        action.type = 'button';
        action.className = 'diary-layer-action is-danger';
        action.dataset.deleteUrl = deleteUrl;
        action.dataset.deleteConfirm = '이 라벨을 떼시겠습니까?';
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

    /** 라벨/메모지의 액션 줄: 글 편집 / 뒤로 / 앞으로 / 떼기 (서버 렌더링과 같은 구조) */
    function layerActions(deleteUrl) {
        const actions = document.createElement('div');
        actions.className = 'diary-layer-actions';

        // 두 번 누르기를 모르는 사람도, 손가락으로 쓰는 사람도 여기로 들어온다.
        const edit = document.createElement('button');
        edit.type = 'button';
        edit.className = 'diary-layer-action';
        edit.dataset.noteEdit = '';
        edit.textContent = '글 편집';
        actions.append(edit);

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
