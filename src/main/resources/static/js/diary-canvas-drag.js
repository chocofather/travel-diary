/**
 * 다이어리 캔버스 요소의 드래그 이동과 크기 조절.
 * 화면에서는 즉시 반영하고, 손을 뗄 때 상대값(0~1 계열)을 한 번만 저장한다.
 * Pointer Events 만 사용하고 별도 드래그/리사이즈 라이브러리는 쓰지 않는다.
 */
document.addEventListener('DOMContentLoaded', () => {
    // 읽기 모드에서는 어떤 조작도 시작되지 않도록 아예 붙이지 않는다.
    if (!document.querySelector('.diary-detail-page.is-edit-mode')) return;

    const items = Array.from(document.querySelectorAll('.diary-canvas-item[data-element-id]'));
    if (items.length === 0) return;

    /** 너무 작아져 잡을 수 없는 요소가 생기지 않게 하는 최소 크기 (상대값) */
    const MIN_SIZE = 0.08;
    const MAX_SIZE = 1;

    /** 액션(수정/삭제 등)을 눌렀을 때는 드래그를 시작하지 않는다. */
    const isActionTarget = target =>
        !!target.closest('button, a, summary, details, form, textarea, input, select');

    function select(item) {
        items.forEach(other => other.classList.toggle('is-selected', other === item));
    }

    function clearSelection() {
        items.forEach(other => other.classList.remove('is-selected'));
    }

    // 페이지의 빈 곳을 누르면 선택을 푼다.
    document.addEventListener('pointerdown', (event) => {
        if (!event.target.closest('.diary-canvas-item')) clearSelection();
    });

    items.forEach((item) => {
        const canvas = item.closest('.diary-canvas');
        if (!canvas) return;

        let dragging = false;
        let startPointerX = 0;
        let startPointerY = 0;
        let startX = 0; // 드래그 시작 시점의 상대 위치
        let startY = 0;
        let currentX = 0;
        let currentY = 0;

        const ratio = value => Number.parseFloat(value) || 0;

        /** 요소 중심이 캔버스 밖으로 완전히 나가지 않는 선에서만 움직인다. (가장자리 걸침은 허용) */
        function clamp(value, sizeRatio) {
            const min = -0.5 * sizeRatio;
            const max = 1 - 0.5 * sizeRatio;
            return Math.min(Math.max(value, min), max);
        }

        function apply(x, y) {
            item.style.left = `${(x * 100).toFixed(5)}%`;
            item.style.top = `${(y * 100).toFixed(5)}%`;
        }

        item.addEventListener('pointerdown', (event) => {
            if (event.button !== 0 && event.pointerType === 'mouse') return;
            // 요소를 누르면 선택되고, 조절점(크기/회전)은 아래 각 로직이 담당한다.
            select(item);
            if (isActionTarget(event.target)) return;

            dragging = true;
            startPointerX = event.clientX;
            startPointerY = event.clientY;
            startX = ratio(item.dataset.positionX);
            startY = ratio(item.dataset.positionY);
            currentX = startX;
            currentY = startY;

            item.classList.add('is-dragging');
            // 포인터가 요소 밖으로 나가도 이동이 이어지게 한다.
            item.setPointerCapture(event.pointerId);
            event.preventDefault();
        });

        item.addEventListener('pointermove', (event) => {
            if (!dragging) return;

            const canvasWidth = canvas.clientWidth;
            const canvasHeight = canvas.clientHeight;
            if (canvasWidth === 0 || canvasHeight === 0) return;

            // 픽셀 이동량을 캔버스 크기로 나눠 상대값으로 바꾼다.
            const deltaX = (event.clientX - startPointerX) / canvasWidth;
            const deltaY = (event.clientY - startPointerY) / canvasHeight;
            currentX = clamp(startX + deltaX, item.offsetWidth / canvasWidth);
            currentY = clamp(startY + deltaY, item.offsetHeight / canvasHeight);
            apply(currentX, currentY);
        });

        async function finishDrag(event) {
            if (!dragging) return;
            dragging = false;
            item.classList.remove('is-dragging');
            if (item.hasPointerCapture(event.pointerId)) {
                item.releasePointerCapture(event.pointerId);
            }

            const movedX = Number(currentX.toFixed(5));
            const movedY = Number(currentY.toFixed(5));
            if (movedX === startX && movedY === startY) return;

            try {
                await savePosition(item, movedX, movedY);
                item.dataset.positionX = String(movedX);
                item.dataset.positionY = String(movedY);
            } catch (error) {
                // 저장에 실패하면 드래그 전 위치로 되돌린다.
                apply(startX, startY);
                currentX = startX;
                currentY = startY;
                window.alert(error.message);
            }
        }

        item.addEventListener('pointerup', finishDrag);
        item.addEventListener('pointercancel', finishDrag);

        // ===== 크기 조절 =====
        const handle = item.querySelector('.diary-resize-handle');
        if (!handle) return;

        const keepsRatio = item.dataset.elementType === 'PHOTO';
        let resizing = false;
        let startWidth = 0;
        let startHeight = 0;
        let currentWidth = 0;
        let currentHeight = 0;

        function applySize(width, height) {
            item.style.width = `${(width * 100).toFixed(5)}%`;
            item.style.height = `${(height * 100).toFixed(5)}%`;
        }

        handle.addEventListener('pointerdown', (event) => {
            if (event.button !== 0 && event.pointerType === 'mouse') return;

            resizing = true;
            startPointerX = event.clientX;
            startPointerY = event.clientY;
            startWidth = ratio(item.dataset.width);
            startHeight = ratio(item.dataset.height);
            currentWidth = startWidth;
            currentHeight = startHeight;

            item.classList.add('is-resizing');
            handle.setPointerCapture(event.pointerId);
            // 조절점을 잡았을 때는 요소 이동이 시작되지 않게 한다.
            event.stopPropagation();
            event.preventDefault();
        });

        handle.addEventListener('pointermove', (event) => {
            if (!resizing) return;

            const canvasWidth = canvas.clientWidth;
            const canvasHeight = canvas.clientHeight;
            if (canvasWidth === 0 || canvasHeight === 0) return;

            // 픽셀 변화량을 캔버스 크기로 나눠 상대값으로 바꾼다.
            const deltaWidth = (event.clientX - startPointerX) / canvasWidth;
            const deltaHeight = (event.clientY - startPointerY) / canvasHeight;

            let width = Math.min(Math.max(startWidth + deltaWidth, MIN_SIZE), MAX_SIZE);
            let height;
            if (keepsRatio && startHeight > 0) {
                // 사진은 지금 비율을 유지한 채 대각선으로만 커지고 작아진다.
                const aspect = startWidth / startHeight;
                height = Math.min(Math.max(width / aspect, MIN_SIZE), MAX_SIZE);
                width = Math.min(Math.max(height * aspect, MIN_SIZE), MAX_SIZE);
            } else {
                height = Math.min(Math.max(startHeight + deltaHeight, MIN_SIZE), MAX_SIZE);
            }

            currentWidth = width;
            currentHeight = height;
            applySize(currentWidth, currentHeight);
        });

        async function finishResize(event) {
            if (!resizing) return;
            resizing = false;
            item.classList.remove('is-resizing');
            if (handle.hasPointerCapture(event.pointerId)) {
                handle.releasePointerCapture(event.pointerId);
            }

            const width = Number(currentWidth.toFixed(5));
            const height = Number(currentHeight.toFixed(5));
            if (width === startWidth && height === startHeight) return;

            try {
                await saveSize(item, width, height);
                item.dataset.width = String(width);
                item.dataset.height = String(height);
            } catch (error) {
                // 저장에 실패하면 조절 전 크기로 되돌린다.
                applySize(startWidth, startHeight);
                currentWidth = startWidth;
                currentHeight = startHeight;
                window.alert(error.message);
            }
        }

        handle.addEventListener('pointerup', finishResize);
        handle.addEventListener('pointercancel', finishResize);

        // ===== 회전 =====
        const rotateHandle = item.querySelector('.diary-rotate-handle');
        if (!rotateHandle) return;

        let rotating = false;
        let centerX = 0;
        let centerY = 0;
        let startAngle = 0;
        let startRotation = 0;
        let currentRotation = 0;

        /** 요소 중심과 포인터가 이루는 각도 (deg) */
        function pointerAngle(event) {
            return Math.atan2(event.clientY - centerY, event.clientX - centerX) * 180 / Math.PI;
        }

        /** -180 ~ 180 으로 맞춰 값이 계속 누적되지 않게 한다. (DB CHECK 범위 안) */
        function normalize(degrees) {
            return ((degrees + 180) % 360 + 360) % 360 - 180;
        }

        function applyRotation(degrees) {
            // transform 에는 회전만 들어간다. (이동은 left/top, 크기는 width/height)
            item.style.transform = `rotate(${degrees.toFixed(2)}deg)`;
        }

        rotateHandle.addEventListener('pointerdown', (event) => {
            if (event.button !== 0 && event.pointerType === 'mouse') return;

            // 회전 중심은 요소의 중심점이다.
            const rect = item.getBoundingClientRect();
            centerX = rect.left + rect.width / 2;
            centerY = rect.top + rect.height / 2;

            rotating = true;
            startAngle = pointerAngle(event);
            startRotation = ratio(item.dataset.rotation);
            currentRotation = startRotation;

            item.classList.add('is-rotating');
            rotateHandle.setPointerCapture(event.pointerId);
            // 회전점을 잡았을 때는 이동/크기 조절이 시작되지 않게 한다.
            event.stopPropagation();
            event.preventDefault();
        });

        rotateHandle.addEventListener('pointermove', (event) => {
            if (!rotating) return;
            // 시작 각도 대비 변화량만큼 기존 회전값에 더한다.
            currentRotation = normalize(startRotation + (pointerAngle(event) - startAngle));
            applyRotation(currentRotation);
        });

        async function finishRotate(event) {
            if (!rotating) return;
            rotating = false;
            item.classList.remove('is-rotating');
            if (rotateHandle.hasPointerCapture(event.pointerId)) {
                rotateHandle.releasePointerCapture(event.pointerId);
            }

            const rotation = Number(currentRotation.toFixed(2));
            if (rotation === startRotation) return;

            try {
                await saveRotation(item, rotation);
                item.dataset.rotation = String(rotation);
            } catch (error) {
                // 저장에 실패하면 회전 전 각도로 되돌린다.
                applyRotation(startRotation);
                currentRotation = startRotation;
                window.alert(error.message);
            }
        }

        rotateHandle.addEventListener('pointerup', finishRotate);
        rotateHandle.addEventListener('pointercancel', finishRotate);

        // ===== 겹침 순서 (앞으로 / 뒤로) =====
        item.querySelectorAll('.diary-layer-action').forEach((button) => {
            button.addEventListener('click', async () => {
                if (button.disabled) return;
                button.disabled = true;
                try {
                    // 새 순서는 서버가 계산해서 돌려준다.
                    const result = await saveLayer(item, button.dataset.layerDirection);
                    applyLayers(canvas, result.elements);
                } catch (error) {
                    // 실패하면 화면은 그대로 두고 안내만 한다.
                    window.alert(error.message);
                } finally {
                    button.disabled = false;
                }
            });
        });
    });

    /** 서버가 정리한 순서를 현재 캔버스 요소에 그대로 반영한다. */
    function applyLayers(canvas, layers) {
        if (!Array.isArray(layers)) return;
        layers.forEach((layer) => {
            const target = canvas.querySelector(
                `.diary-canvas-item[data-element-id="${layer.id}"]`);
            if (!target) return;
            target.style.zIndex = String(layer.zIndex);
            target.dataset.zIndex = String(layer.zIndex);
        });
    }

    function savePosition(item, positionX, positionY) {
        return save(item.dataset.positionUrl, {
            positionX: String(positionX),
            positionY: String(positionY)
        }, '위치를 저장하지 못했습니다.');
    }

    function saveSize(item, width, height) {
        return save(item.dataset.sizeUrl, {
            width: String(width),
            height: String(height)
        }, '크기를 저장하지 못했습니다.');
    }

    function saveRotation(item, rotation) {
        return save(item.dataset.rotationUrl, {
            rotation: String(rotation)
        }, '회전 각도를 저장하지 못했습니다.');
    }

    function saveLayer(item, direction) {
        return save(item.dataset.layerUrl, {
            direction: String(direction)
        }, '겹침 순서를 저장하지 못했습니다.');
    }

    /** 저장은 기존 화면을 그대로 둔 채 값만 보낸다. (CSRF 토큰은 layout 의 meta 사용) */
    async function save(url, fields, defaultMessage) {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (!csrfToken || !csrfHeader) {
            throw new Error(`보안 토큰을 확인할 수 없어 ${defaultMessage}`);
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
            throw new Error('로그인이 필요합니다.');
        }
        if (!response.ok) {
            let message = defaultMessage;
            const contentType = response.headers.get('Content-Type') || '';
            if (contentType.includes('application/json')) {
                const payload = await response.json();
                message = payload.message || message;
            }
            throw new Error(message);
        }

        // 204(위치/크기/회전)는 본문이 없고, 겹침 순서만 정리된 목록을 돌려준다.
        if (response.status === 204) return null;
        const contentType = response.headers.get('Content-Type') || '';
        return contentType.includes('application/json') ? response.json() : null;
    }
});
