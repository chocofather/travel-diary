/**
 * 다이어리 편집 화면의 스티커 붙이기.
 * picker 에서 고른 스티커 코드만 서버로 보내고, 실제 이미지 경로는 서버가 허용 목록에서 정한다.
 * 서버가 만들어 준 요소를 지금 보고 있는 캔버스에 바로 그려 화면을 새로 고치지 않는다.
 * (이동/크기/회전/겹침 순서 저장은 사진과 같은 endpoint 를 그대로 쓴다)
 */
document.addEventListener('DOMContentLoaded', () => {
    const button = document.getElementById('diary-sticker-button');
    const popover = document.getElementById('diary-sticker-popover');
    if (!button || !popover) return;

    const status = document.getElementById('diary-sticker-status');
    const createUrl = button.dataset.createUrl;
    const canvas = document.querySelector('.diary-book-single .diary-canvas');
    if (!createUrl || !canvas) return;

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

    popover.querySelectorAll('.diary-sticker-option').forEach((option) => {
        option.addEventListener('click', () => attach(option.dataset.stickerCode));
    });

    toggle(false);

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

    async function attach(stickerCode) {
        if (!stickerCode || busy) return;
        busy = true;
        showStatus('붙이는 중…');

        try {
            // 본문에 아직 저장되지 않은 입력이 있으면 먼저 저장한다. (기존 flush 흐름 재사용)
            if (window.diaryEditor && window.diaryEditor.hasPendingChanges()) {
                const saved = await window.diaryEditor.flush();
                if (!saved) throw new Error('본문을 저장하지 못해 스티커를 붙이지 못했습니다.');
            }

            const created = await createSticker(stickerCode);
            const item = renderSticker(created);
            canvas.append(item);
            // 새로 붙은 스티커도 기존 드래그/크기/회전/겹침 조작을 그대로 쓴다.
            window.diaryCanvas?.register(item);
            window.diaryCanvas?.select(item);
            showStatus('붙였습니다');
        } catch (error) {
            showStatus(error.message || '스티커를 붙이지 못했습니다', true);
        } finally {
            busy = false;
        }
    }

    /** CSRF 토큰은 layout 의 meta 값을 그대로 쓴다. (기존 저장 요청과 같은 방식) */
    async function createSticker(stickerCode) {
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
            body: new URLSearchParams({sticker: stickerCode})
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

    /** 떼기는 사진 삭제와 같은 폼 전송이다. (공용 asset 이라 서버는 행만 지운다) */
    function deleteForm(action) {
        const form = document.createElement('form');
        form.className = 'diary-sticker-delete';
        form.action = action;
        form.method = 'post';
        form.setAttribute('onsubmit', "return confirm('이 스티커를 떼시겠습니까?');");
        form.append(
            hiddenInput('spread', button.dataset.spread || '0'),
            hiddenInput('page', button.dataset.page || ''));

        // 서버 렌더링 폼은 Thymeleaf 가 넣어 주는 값을 여기서는 meta 에서 가져온다.
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfField = document.querySelector('meta[name="_csrf_parameter"]')?.content
            || '_csrf';
        if (csrfToken) form.append(hiddenInput(csrfField, csrfToken));

        const submit = document.createElement('button');
        submit.type = 'submit';
        submit.className = 'diary-layer-action is-danger';
        submit.textContent = '떼기';
        form.append(submit);
        return form;
    }

    function hiddenInput(name, value) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        return input;
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
        actions.append(deleteForm(deleteUrl));
        return actions;
    }
});
