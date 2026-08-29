/**
 * 표지 디자인에 사진 붙이기.
 *
 * 파일을 고르면 바로 올려서 표지에 그린다. 화면을 새로 고치지 않으므로 꾸미던 자리를 잃지 않고,
 * 붙자마자 옮기고 돌릴 수 있다. 한 번에 여러 장을 고를 수 있고 사진 한 장이 요소 하나다.
 *
 * 조작(이동/크기/회전/겹침/삭제)은 스티커와 마찬가지로 diary-canvas-drag.js 가 그대로 맡는다.
 * 여기서는 서버가 그려 준 것과 같은 마크업을 만들어 캔버스에 넣고 엔진에 넘기기만 한다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const inputs = Array.from(document.querySelectorAll('.diary-cover-photo-input'));
    if (!inputs.length) return;

    const canvas = document.querySelector('.diary-cover-canvas.is-editable .diary-cover-surface');
    const status = document.getElementById('diary-sticker-status');
    if (!canvas) return;

    let busy = false;

    /*
      등록 자리마다 붙는 모습이 다르다. (일반 사진 / 폴라로이드)
      고르는 자리가 이미 모습을 정하므로 올릴 때 그 값을 함께 보낸다.
    */
    inputs.forEach((input) => input.addEventListener('change', async () => {
        const files = Array.from(input.files || []);
        // 값을 먼저 비워 둔다. 같은 파일을 다시 골라도 change 가 오게 하려는 것이다.
        input.value = '';
        if (!files.length || busy) return;

        if (files.some((file) => !file.type.startsWith('image/'))) {
            showStatus('사진 파일만 붙일 수 있습니다', true);
            return;
        }

        busy = true;
        showStatus('올리는 중…');
        try {
            const photos = await upload(input.dataset.createUrl, input.dataset.photoStyle, files);
            photos.forEach((photo) => {
                const item = render(photo);
                canvas.append(item);
                window.diaryCanvas?.register(item);
                window.diaryCanvas?.select(item);
            });
            showStatus(photos.length > 1 ? `사진 ${photos.length}장을 붙였습니다` : '붙였습니다');
        } catch (error) {
            showStatus(error.message || '사진을 붙이지 못했습니다', true);
        } finally {
            busy = false;
        }
    }));

    /** CSRF 토큰은 layout 의 meta 값을 그대로 쓴다. (기존 저장 요청과 같은 방식) */
    async function upload(createUrl, photoStyle, files) {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (!createUrl || !csrfToken || !csrfHeader) {
            throw new Error('보안 토큰을 확인할 수 없어 사진을 붙이지 못했습니다');
        }

        const form = new FormData();
        files.forEach((file) => form.append('images', file));
        // 이번에 올리는 사진들이 어떤 모습으로 붙을지. (고른 자리가 정한다)
        form.append('photoStyle', photoStyle);

        const response = await fetch(createUrl, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {'Accept': 'application/json', [csrfHeader]: csrfToken},
            body: form
        });

        if (response.status === 401) {
            const redirect = window.location.pathname + window.location.search;
            window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`;
            throw new Error('로그인이 필요합니다');
        }
        if (!response.ok) {
            let message = '사진을 붙이지 못했습니다';
            if ((response.headers.get('Content-Type') || '').includes('application/json')) {
                message = (await response.json()).message || message;
            }
            throw new Error(message);
        }
        return (await response.json()).photos || [];
    }

    /** 서버 렌더링 결과와 같은 마크업을 만든다. (cover-design-edit.html 의 사진 figure 와 동일) */
    function render(photo) {
        const item = document.createElement('figure');
        item.className = 'diary-canvas-item diary-canvas-photo diary-photo';
        // 어떤 모습으로 붙을지는 서버가 이미 정해 돌려준다. (등록한 자리가 정한 값)
        item.classList.add(photo.photoStyleClass);
        item.dataset.elementId = String(photo.id);
        // 크기를 조절할 때 원본 비율을 지키는 것은 엔진이 이 값을 보고 정한다.
        item.dataset.elementType = 'PHOTO';
        item.dataset.positionX = String(photo.positionX);
        item.dataset.positionY = String(photo.positionY);
        item.dataset.width = String(photo.width);
        item.dataset.height = String(photo.height);
        item.dataset.rotation = String(photo.rotation);
        item.dataset.zIndex = String(photo.zIndex);
        item.dataset.positionUrl = photo.urls.position;
        item.dataset.sizeUrl = photo.urls.size;
        item.dataset.rotationUrl = photo.urls.rotation;
        item.dataset.layerUrl = photo.urls.layer;
        item.style.left = `${photo.positionX * 100}%`;
        item.style.top = `${photo.positionY * 100}%`;
        item.style.width = `${photo.width * 100}%`;
        item.style.height = `${photo.height * 100}%`;
        item.style.transform = `rotate(${photo.rotation}deg)`;
        item.style.setProperty('--diary-item-rotation', `${photo.rotation}deg`);
        item.style.zIndex = String(photo.zIndex);

        const image = document.createElement('img');
        image.src = photo.imageUrl;
        image.alt = '표지 사진';
        item.append(image, handle('diary-rotate-handle', '회전'),
                handle('diary-resize-handle', '크기 조절'), layerActions(photo.urls.delete));
        return item;
    }

    function handle(className, label) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = className;
        button.setAttribute('aria-label', label);
        return button;
    }

    /**
     * 액션 줄. 실제 저장/삭제와 화면에서 빼는 일은 diary-canvas-drag.js 가 맡는다.
     * (서버 렌더링 사진과 같은 data-* 만 실어 준다)
     */
    /** 사진의 모습은 등록할 때 정해지므로 이 줄에는 겹침 순서와 삭제만 둔다. */
    function layerActions(deleteUrl) {
        const actions = document.createElement('div');
        actions.className = 'diary-layer-actions';
        actions.append(
            layerButton('BACKWARD', '뒤로'),
            layerButton('FORWARD', '앞으로'),
            deleteButton(deleteUrl));
        return actions;
    }

    function layerButton(direction, text) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'diary-layer-action';
        button.dataset.layerDirection = direction;
        button.textContent = text;
        return button;
    }

    function deleteButton(deleteUrl) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'diary-layer-action is-danger';
        button.dataset.deleteUrl = deleteUrl;
        button.dataset.deleteConfirm = '이 사진을 삭제하시겠습니까?';
        button.textContent = '삭제';
        return button;
    }

    function showStatus(text, isError = false) {
        if (!status) return;
        status.textContent = text;
        status.classList.toggle('is-error', isError);
    }
});
