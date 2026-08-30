/**
 * 라벨기. 종이 배경 없이 글씨만 붙인다. (NOTE 라벨/떡메모지와 다른 갈래다)
 *
 * 붙일 자리는 두 곳이다 — 페이지 편집의 종이, 그리고 표지 디자인 편집의 표지.
 * 붙이는 절차와 만드는 마크업은 같고, 보낼 주소만 고르는 칸이 알려 준다.
 * (스티커 붙이기가 두 화면을 함께 맡는 방식을 그대로 따른다)
 *
 * 페이지에서는 꾸미기 팝오버의 한 갈래로 들어가므로 여닫이를 맡지 않는다.
 * (열고 닫기는 스티커 쪽이, 갈래 전환은 라벨/메모지 쪽이 이미 처리한다)
 * 표지에서는 자기 팝오버를 가지므로 그 여닫이만 여기서 맡는다.
 *
 * 글꼴 목록은 서버가 manifest(json/diary_label_fonts.json)대로 그려 주므로 여기서 들지 않는다.
 * 보내는 것은 문구와 고른 글꼴 code 뿐이고, 자리·크기·겹침 순서는 서버가 정한다.
 * 서버가 만들어 준 요소를 지금 보고 있는 캔버스에 바로 그려 화면을 새로 고치지 않는다.
 * (이동/크기/회전/겹침 순서/떼기는 사진·스티커와 같은 엔진을 그대로 쓴다)
 */
document.addEventListener('DOMContentLoaded', () => {
    const panel = document.querySelector('[data-label-maker]');
    if (!panel) return;

    const input = panel.querySelector('[data-label-input]');
    const color = panel.querySelector('[data-label-color]');
    const colorValue = panel.querySelector('[data-label-color-value]');
    const attachButton = panel.querySelector('[data-label-attach]');
    const status = document.getElementById('diary-sticker-status');
    const createUrl = panel.dataset.createUrl;
    const canvas = document.querySelector('.diary-book-single .diary-canvas')
        || document.querySelector('.diary-cover-canvas.is-editable .diary-cover-surface');
    if (!input || !attachButton || !createUrl || !canvas) return;

    // 표지처럼 자기 팝오버를 가진 자리에서만 여닫이를 맡는다.
    setUpOwnPopover();

    /** 고른 글꼴. 처음에는 목록의 첫 글꼴이 눌려 있다. */
    let chosenFont = panel.querySelector('.diary-label-font.is-active')?.dataset.labelFont || '';
    let busy = false;
    /*
      한글은 글자를 만드는 동안(조합 중) Enter 가 한 번 더 들어온다.
      그대로 두면 조합이 끝나기 전에 붙거나 두 번 붙는다. 조합 중에는 Enter 를 흘려보낸다.
    */
    let composing = false;

    panel.querySelectorAll('.diary-label-font').forEach((option) => {
        option.addEventListener('click', () => chooseFont(option));
    });

    // 고른 색은 고르는 자리에도 그대로 보여 준다. (붙이면 이 색으로 붙는다)
    if (color) {
        color.addEventListener('input', paintColor);
        paintColor();
    }

    function paintColor() {
        if (colorValue) colorValue.textContent = color.value.toUpperCase();
        panel.querySelectorAll('[data-label-sample]').forEach((sample) => {
            sample.style.color = color.value;
        });
        /*
          고르는 자리의 바탕은 흰색이라 밝은 글자색을 고르면 보기 글이 사라진다.
          그래서 밝은 색일 때만 미리보기 칸의 바탕을 어둡게 바꾼다.
          바꾸는 것은 고르는 자리뿐이고, 실제로 붙는 글씨는 늘 바탕 없이 글자만이다.
          (저장되는 색 값도 그대로다)
        */
        const fonts = panel.querySelector('.diary-label-fonts');
        if (fonts) fonts.classList.toggle('is-light-ink', isLight(color.value));
    }

    /**
     * 밝은 색인지. sRGB 상대 휘도로 판단해 흰색 하나만 따로 다루지 않는다.
     * (같은 밝기라도 초록이 파랑보다 훨씬 밝게 보이므로 채널마다 가중치가 다르다)
     */
    function isLight(hex) {
        const value = /^#([0-9a-f]{6})$/i.exec(hex || '');
        if (!value) return false;

        const channels = [0, 2, 4].map((at) => {
            const ratio = parseInt(value[1].substr(at, 2), 16) / 255;
            return ratio <= 0.03928 ? ratio / 12.92 : Math.pow((ratio + 0.055) / 1.055, 2.4);
        });
        const luminance = 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
        // 흰 바탕에서 대비가 3:1 아래로 떨어지는 지점쯤이다.
        return luminance > 0.42;
    }

    input.addEventListener('compositionstart', () => {
        composing = true;
    });
    input.addEventListener('compositionend', () => {
        composing = false;
        paintSamples();
    });
    input.addEventListener('input', paintSamples);
    input.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' || composing || event.isComposing) return;
        // 팝오버가 폼 안에 있지 않지만, Enter 로 다른 것이 눌리지 않게 막아 둔다.
        event.preventDefault();
        attach();
    });

    attachButton.addEventListener('click', attach);

    /**
     * 표지 편집처럼 라벨기가 자기 팝오버를 가진 자리의 여닫이.
     * 스티커 picker 와 같은 규칙이다 — 버튼으로 열고, 바깥을 누르거나 Esc 로 닫는다.
     * 자리 보정(도구 줄 위로 열기)은 CSS 가 맡으므로 여기서 좌표를 계산하지 않는다.
     */
    function setUpOwnPopover() {
        const trigger = document.getElementById('diary-label-button');
        const own = document.getElementById('diary-label-popover');
        if (!trigger || !own || !own.contains(panel)) return;

        trigger.addEventListener('click', () => toggle(own.hidden));
        document.addEventListener('click', (event) => {
            if (own.hidden) return;
            if (!own.contains(event.target) && event.target !== trigger
                && !trigger.contains(event.target)) {
                toggle(false);
            }
        });
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape' && !own.hidden) {
                toggle(false);
                trigger.focus();
            }
        });

        function toggle(open) {
            own.hidden = !open;
            trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
            if (open) input.focus();
        }
    }

    /** 고른 글꼴을 새겨 두고 눌림 상태만 바꾼다. 미리보기 글꼴은 서버가 준 class 그대로다. */
    function chooseFont(option) {
        chosenFont = option.dataset.labelFont || '';
        panel.querySelectorAll('.diary-label-font').forEach((other) => {
            const active = other === option;
            other.classList.toggle('is-active', active);
            other.setAttribute('aria-pressed', active ? 'true' : 'false');
        });
        showStatus('');
    }

    /** 입력한 문구가 있으면 그 문구로, 없으면 보기 글로 미리 보여 준다. */
    function paintSamples() {
        const text = input.value.trim();
        panel.querySelectorAll('[data-label-sample]').forEach((sample) => {
            sample.textContent = text || '여행의 순간';
        });
    }

    function showStatus(text, isError = false) {
        if (!status) return;
        status.textContent = text;
        status.classList.toggle('is-error', isError);
    }

    async function attach() {
        if (busy) return;
        const text = input.value.trim();
        if (!text) {
            // 서버도 막지만, 여기서 먼저 알려 주는 편이 덜 놀랍다.
            showStatus('붙일 문구를 입력해 주세요', true);
            input.focus();
            return;
        }

        busy = true;
        showStatus('붙이는 중…');
        try {
            // 본문에 아직 저장되지 않은 입력이 있으면 먼저 저장한다. (스티커와 같은 흐름)
            if (window.diaryEditor && window.diaryEditor.hasPendingChanges()) {
                const saved = await window.diaryEditor.flush();
                if (!saved) throw new Error('본문을 저장하지 못해 글씨를 붙이지 못했습니다.');
            }

            const created = await createLabel(text, chosenFont, color ? color.value : '');
            const item = renderLabel(created);
            canvas.append(item);
            // 새로 붙은 글씨도 기존 드래그/크기/회전/겹침/떼기 조작을 그대로 쓴다.
            window.diaryCanvas?.register(item);
            window.diaryCanvas?.select(item);

            // 다음 문구를 바로 적을 수 있게 입력칸만 비운다. 고른 글꼴은 그대로 둔다.
            input.value = '';
            paintSamples();
            showStatus('붙였습니다');
        } catch (error) {
            showStatus(error.message || '글씨를 붙이지 못했습니다', true);
        } finally {
            busy = false;
        }
    }

    /** CSRF 토큰은 layout 의 meta 값을 그대로 쓴다. (스티커 붙이기와 같은 방식) */
    async function createLabel(text, textFont, textColor) {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (!csrfToken || !csrfHeader) {
            throw new Error('보안 토큰을 확인할 수 없어 글씨를 붙이지 못했습니다');
        }

        // 보내는 것은 문구와 고른 글꼴·글자색뿐이다. 나머지는 모두 서버가 정한다.
        const body = new URLSearchParams({text});
        if (textFont) body.set('textFont', textFont);
        if (textColor) body.set('textColor', textColor);

        const response = await fetch(createUrl, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
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
            let message = '글씨를 붙이지 못했습니다';
            if ((response.headers.get('Content-Type') || '').includes('application/json')) {
                const payload = await response.json();
                message = payload.message || message;
            }
            throw new Error(message);
        }
        return response.json();
    }

    /** 서버 렌더링 결과와 같은 마크업을 만든다. (detail.html 의 TEXT figure 와 동일) */
    function renderLabel(label) {
        const item = document.createElement('figure');
        item.className = 'diary-canvas-item diary-label';
        item.dataset.elementId = String(label.id);
        item.dataset.elementType = label.elementType;
        item.dataset.positionX = String(label.positionX);
        item.dataset.positionY = String(label.positionY);
        item.dataset.width = String(label.width);
        item.dataset.height = String(label.height);
        item.dataset.rotation = String(label.rotation);
        item.dataset.zIndex = String(label.zIndex);
        item.dataset.positionUrl = label.urls.position;
        item.dataset.sizeUrl = label.urls.size;
        item.dataset.rotationUrl = label.urls.rotation;
        item.dataset.layerUrl = label.urls.layer;
        item.style.left = `${label.positionX * 100}%`;
        item.style.top = `${label.positionY * 100}%`;
        item.style.width = `${label.width * 100}%`;
        item.style.height = `${label.height * 100}%`;
        item.style.transform = `rotate(${label.rotation}deg)`;
        item.style.setProperty('--diary-item-rotation', `${label.rotation}deg`);
        // 글자 크기를 상자에 맞추는 데 쓴다. (서버 렌더링과 같은 값을 실어 준다)
        item.style.setProperty('--diary-label-chars', String(label.textContent.length));
        item.style.zIndex = String(label.zIndex);

        const text = document.createElement('span');
        /*
          글꼴 class 는 서버가 준 것을 그대로 쓴다.
          여기서 code 를 다시 class 로 바꾸는 표를 두지 않는다.
          글꼴이 없는 요소는 빈 문자열이 와서 기본 글꼴로 그려진다.
        */
        text.className = `diary-label-text ${label.fontClass}`.trim();
        // 색을 고른 글씨만 색을 입힌다. 비어 있으면 기본 먹색으로 그려진다.
        if (label.textColor) text.style.color = label.textColor;
        text.textContent = label.textContent;

        item.append(text, rotateHandle(), resizeHandle(), layerActions(label.urls.delete));
        return item;
    }

    /**
     * 떼기 버튼. 실제 삭제 요청과 화면에서 빼는 일은 diary-canvas-drag.js 가 맡는다.
     * (서버 렌더링 글씨와 같은 data-delete-url / data-delete-confirm 만 실어 준다)
     */
    function deleteButton(deleteUrl) {
        const action = document.createElement('button');
        action.type = 'button';
        action.className = 'diary-layer-action is-danger';
        action.dataset.deleteUrl = deleteUrl;
        action.dataset.deleteConfirm = '이 글씨를 떼시겠습니까?';
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

    /** 스티커와 같은 액션 줄: 뒤로 / 앞으로 / 떼기 (요소 바깥 아래에 놓인다) */
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
