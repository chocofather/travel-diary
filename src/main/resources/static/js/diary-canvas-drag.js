/**
 * 다이어리 캔버스 요소의 드래그 이동과 크기 조절.
 * 화면에서는 즉시 반영하고, 손을 뗄 때 상대값(0~1 계열)을 한 번만 저장한다.
 * Pointer Events 만 사용하고 별도 드래그/리사이즈 라이브러리는 쓰지 않는다.
 */
document.addEventListener('DOMContentLoaded', () => {
    // 읽기 모드에서는 어떤 조작도 시작되지 않도록 아예 붙이지 않는다.
    if (!document.querySelector('.diary-detail-page.is-edit-mode')) return;

    // 나중에 붙는 요소(스티커)도 같은 조작을 쓰도록 목록에 더해 나간다.
    const items = Array.from(document.querySelectorAll('.diary-canvas-item[data-element-id]'));

    /** 너무 작아져 잡을 수 없는 요소가 생기지 않게 하는 최소 크기 (상대값) */
    const MIN_SIZE = 0.08;
    const MAX_SIZE = 1;
    /** 마스킹테이프는 띠라서 일반 스티커보다 짧게도, 길게도 붙일 수 있다. */
    const MIN_TAPE_LENGTH = 0.1;
    /** 끝 조각 한 개의 가로세로 비율. (CSS 의 .diary-tape-cap aspect-ratio 와 같은 값) */
    const TAPE_CAP_ASPECT = 18 / 40;

    /** 액션(수정/삭제 등)을 눌렀을 때는 드래그를 시작하지 않는다. */
    const isActionTarget = target =>
        !!target.closest('button, a, summary, details, form, textarea, input, select');

    /** 요소와 액션 줄 사이 간격 */
    const ACTIONS_GAP = 10;
    /** 액션 줄이 종이 가장자리에 딱 붙지 않게 남기는 여백 */
    const ACTIONS_EDGE = 4;

    /**
     * 액션 줄을 기울어진 요소 "아래" 에 놓는다.
     *
     * 요소의 아래 모서리에 붙여 두면 각도에 따라 그 모서리가 위로 올라와 본문을 가린다.
     * 그래서 요소가 화면에서 실제로 차지하는 네모의 높이를 각도에서 구해,
     * 그 네모 아래에 늘 같은 간격으로 둔다. (자리는 CSS 가 두 값으로만 잡는다)
     *
     * 좌우로는 요소 한가운데에 맞추되, 종이 밖으로 나가려 하면 그만큼만 밀어 넣는다.
     */
    function layoutActions(item) {
        const actions = item.querySelector('.diary-layer-actions');
        const canvas = item.closest('.diary-canvas');
        // 고르지 않은 요소의 줄은 화면에 없어 폭을 잴 수 없다. 보일 때만 맞춘다.
        if (!actions || !canvas || actions.offsetWidth === 0) return;

        const width = item.offsetWidth;
        const height = item.offsetHeight;
        const degrees = Number.parseFloat(
            getComputedStyle(item).getPropertyValue('--diary-item-rotation')) || 0;
        const radians = degrees * Math.PI / 180;
        const sin = Math.abs(Math.sin(radians));
        const cos = Math.abs(Math.cos(radians));

        // 기울어진 네모의 높이 절반. 기준점이 요소 한가운데라 여기서부터 내려가면 된다.
        const drop = (width * sin + height * cos) / 2 + ACTIONS_GAP;

        const centerX = item.offsetLeft + width / 2;
        const half = actions.offsetWidth / 2;
        let shift = 0;
        if (centerX - half < ACTIONS_EDGE) {
            shift = ACTIONS_EDGE - (centerX - half);
        } else if (centerX + half > canvas.clientWidth - ACTIONS_EDGE) {
            shift = canvas.clientWidth - ACTIONS_EDGE - (centerX + half);
        }

        item.style.setProperty('--diary-actions-drop', `${drop.toFixed(1)}px`);
        item.style.setProperty('--diary-actions-shift', `${shift.toFixed(1)}px`);
    }

    function select(item) {
        items.forEach(other => other.classList.toggle('is-selected', other === item));
        // 줄이 보이게 된 다음에야 폭을 잴 수 있다.
        layoutActions(item);
    }

    function clearSelection() {
        items.forEach(other => other.classList.remove('is-selected'));
    }

    /**
     * 지운 요소를 화면과 목록에서 함께 뺀다.
     * 조작점/액션 줄은 요소 안에 있으므로 요소를 지우면 같이 사라지고,
     * 목록에서도 빠져 이후 저장 요청 대상이 되지 않는다.
     */
    function removeItem(item) {
        const index = items.indexOf(item);
        if (index >= 0) items.splice(index, 1);
        item.classList.remove('is-selected');
        item.remove();
    }

    // 페이지의 빈 곳을 누르면 선택을 푼다.
    document.addEventListener('pointerdown', (event) => {
        if (!event.target.closest('.diary-canvas-item')) clearSelection();
    });

    items.forEach(setupItem);

    /*
      종이 크기가 달라지면 요소의 실제 픽셀 크기도 달라진다.
      액션 줄을 내리는 거리와 가장자리에서 미는 정도는 픽셀이라 다시 구한다.
      (지금 보이는 줄은 고른 요소의 것 하나뿐이다)
    */
    window.addEventListener('resize', () => {
        items.forEach((item) => {
            if (item.classList.contains('is-selected')) layoutActions(item);
        });
    });

    // 스티커처럼 화면을 새로 고치지 않고 붙는 요소도 같은 조작을 쓸 수 있게 열어 둔다.
    window.diaryCanvas = {
        register(item) {
            if (!item || items.includes(item)) return;
            items.push(item);
            setupItem(item);
        },
        select
    };

    function setupItem(item) {
        const canvas = item.closest('.diary-canvas');
        if (!canvas) return;
        // 같은 요소에 조작을 두 번 붙이면 한 번의 움직임이 두 번 반영돼 크기가 튄다.
        if (item.dataset.canvasReady) return;
        item.dataset.canvasReady = 'true';

        // 키보드로 들어와 줄이 보이게 된 경우에도 자리를 맞춰 둔다.
        item.addEventListener('focusin', () => layoutActions(item));

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
            // 줄은 요소를 따라 움직이지만, 가장자리에 닿으면 미는 정도가 달라진다.
            layoutActions(item);
        }

        item.addEventListener('pointerdown', (event) => {
            if (event.button !== 0 && event.pointerType === 'mouse') return;
            /*
              라벨/메모지에 글을 쓰는 중이면 옮기지 않는다.
              글자를 고르려고 끌었을 뿐인데 종이가 따라 움직이면 쓸 수가 없다.
              (조절점은 글 쓰는 동안 화면에서 내려가므로 여기서만 막으면 된다)
            */
            if (item.classList.contains('is-editing')) return;
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
        /**
         * 마스킹테이프는 띠라서 길이만 잡는다.
         * 두께는 종이 높이의 몇 %밖에 안 되는 값이라, 조절점이 세로로 조금만 흔들려도
         * 두께가 몇 배로 뛰거나 최소치에 붙어 버린다. 그래서 길이만 바꾸고 두께는 그대로 둔다.
         * (일반 스티커/사진 동작은 그대로다)
         */
        const isMaskingTape = item.dataset.stickerKind === 'masking-tape';
        /**
         * 조절할 수 있는 가장 짧은 길이.
         * 끝 조각을 이어 붙여 그리는 테이프는 그 두 조각이 겹치지 않을 만큼만 필요하고,
         * 조각 폭은 두께에서 나오므로 잡은 순간의 두께로 그때그때 구한다.
         * (고정 숫자를 크게 잡아 두면 짧은 테이프를 잡는 순간 길이가 튄다)
         */
        function minWidthOf(heightRatio, canvasWidth, canvasHeight) {
            if (!isMaskingTape) return MIN_SIZE;
            if (!item.dataset.tapeCenter) return MIN_TAPE_LENGTH;

            const capsRatio = 2 * heightRatio * canvasHeight * TAPE_CAP_ASPECT / canvasWidth;
            return Math.max(MIN_TAPE_LENGTH, capsRatio);
        }

        let resizing = false;
        // 조절을 시작한 순간의 값만 쓴다. (진행 중에는 다시 읽지 않아 값이 쌓이지 않는다)
        let resizePointerId = null;
        let resizeStartX = 0;
        let resizeStartY = 0;
        let resizeCanvasWidth = 0;
        let resizeCanvasHeight = 0;
        let resizeMinWidth = MIN_SIZE;
        let startWidth = 0;
        let startHeight = 0;
        let currentWidth = 0;
        let currentHeight = 0;

        function applySize(width, height) {
            item.style.width = `${(width * 100).toFixed(5)}%`;
            item.style.height = `${(height * 100).toFixed(5)}%`;
            // 요소가 커지면 그만큼 줄도 아래로 내려간다.
            layoutActions(item);
        }

        function limit(value, min) {
            return Math.min(Math.max(value, min), MAX_SIZE);
        }

        handle.addEventListener('pointerdown', (event) => {
            if (event.button !== 0 && event.pointerType === 'mouse') return;

            const canvasWidth = canvas.clientWidth;
            const canvasHeight = canvas.clientHeight;
            if (canvasWidth === 0 || canvasHeight === 0) return;

            resizing = true;
            // 조절 중에 다른 손가락이 종이를 눌러도 이 값들이 바뀌지 않게 여기서만 잡아 둔다.
            resizePointerId = event.pointerId;
            resizeStartX = event.clientX;
            resizeStartY = event.clientY;
            resizeCanvasWidth = canvasWidth;
            resizeCanvasHeight = canvasHeight;
            startWidth = ratio(item.dataset.width);
            startHeight = ratio(item.dataset.height);
            resizeMinWidth = minWidthOf(startHeight, canvasWidth, canvasHeight);
            currentWidth = startWidth;
            currentHeight = startHeight;

            item.classList.add('is-resizing');
            handle.setPointerCapture(event.pointerId);
            // 조절점을 잡았을 때는 요소 이동이 시작되지 않게 한다.
            event.stopPropagation();
            event.preventDefault();
        });

        handle.addEventListener('pointermove', (event) => {
            if (!resizing || event.pointerId !== resizePointerId) return;
            // 이미 버튼을 뗀 뒤에 들어온 움직임이면(창 밖에서 놓기 등) 여기서 조절을 끝낸다.
            // 그대로 두면 다음에 조절점 위를 지나기만 해도 옛 시작점 기준으로 크기가 튄다.
            if (event.pointerType === 'mouse' && (event.buttons & 1) === 0) {
                finishResize(event);
                return;
            }

            // 픽셀 변화량을 캔버스 크기로 나눠 상대값으로 바꾼다. (시작값 기준으로만 더한다)
            const deltaWidth = (event.clientX - resizeStartX) / resizeCanvasWidth;
            const deltaHeight = (event.clientY - resizeStartY) / resizeCanvasHeight;

            let width = limit(startWidth + deltaWidth, resizeMinWidth);
            let height;
            if (keepsRatio && startHeight > 0) {
                // 사진은 지금 비율을 유지한 채 대각선으로만 커지고 작아진다.
                const aspect = startWidth / startHeight;
                height = limit(width / aspect, MIN_SIZE);
                width = limit(height * aspect, MIN_SIZE);
            } else if (isMaskingTape) {
                // 테이프는 잡은 순간의 두께를 그대로 두고 길이만 따라온다.
                height = startHeight;
            } else {
                height = limit(startHeight + deltaHeight, MIN_SIZE);
            }

            currentWidth = width;
            currentHeight = height;
            applySize(currentWidth, currentHeight);
        });

        async function finishResize(event) {
            if (!resizing || event.pointerId !== resizePointerId) return;
            resizing = false;
            resizePointerId = null;
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
        // 어떤 이유로든 포인터를 놓치면(조절점이 가려짐/창 밖에서 놓기 등) 조절을 끝낸다.
        handle.addEventListener('lostpointercapture', finishResize);

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
            // 액션 줄이 같이 기울어 읽기 어려워지지 않도록 반대로 돌릴 각도를 알려 준다.
            item.style.setProperty('--diary-item-rotation', `${degrees.toFixed(2)}deg`);
            // 각도가 바뀌면 요소가 차지하는 네모도 달라진다. 줄을 그 아래로 다시 내린다.
            layoutActions(item);
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

        // ===== 삭제 / 떼기 =====
        // 화면을 새로 고치지 않고 이 요소만 지운다. (본문/한 줄 메모의 편집 상태는 그대로 둔다)
        const deleteButton = item.querySelector('.diary-layer-action[data-delete-url]');
        deleteButton?.addEventListener('click', async () => {
            if (deleteButton.disabled) return;
            const confirmText = deleteButton.dataset.deleteConfirm;
            if (confirmText && !window.confirm(confirmText)) return;

            deleteButton.disabled = true;
            try {
                await save(deleteButton.dataset.deleteUrl, {}, '삭제하지 못했습니다.');
                removeItem(item);
            } catch (error) {
                // 실패하면 요소를 그대로 두고 지금 화면에 머문다.
                deleteButton.disabled = false;
                window.alert(error.message);
            }
        });

        // ===== 겹침 순서 (앞으로 / 뒤로) =====
        // 같은 액션 줄에 삭제 버튼이 있으므로 방향값이 있는 버튼만 고른다.
        item.querySelectorAll('.diary-layer-action[data-layer-direction]').forEach((button) => {
            button.addEventListener('click', async () => {
                if (button.disabled) return;
                button.disabled = true;
                try {
                    // 새 순서는 서버가 계산해서 돌려준다.
                    const result = await saveLayer(item, button.dataset.layerDirection);
                    applyLayers(canvas, result?.elements);
                } catch (error) {
                    // 실패하면 화면은 그대로 두고 안내만 한다.
                    window.alert(error.message);
                } finally {
                    button.disabled = false;
                }
            });
        });
    }

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

    /** 이미 지운 요소는 저장 대상이 아니다. (삭제 직후 남은 조작이 요청을 보내지 않게 한다) */
    function isRemoved(item) {
        return !item.isConnected;
    }

    function savePosition(item, positionX, positionY) {
        if (isRemoved(item)) return Promise.resolve(null);
        return save(item.dataset.positionUrl, {
            positionX: String(positionX),
            positionY: String(positionY)
        }, '위치를 저장하지 못했습니다.');
    }

    function saveSize(item, width, height) {
        if (isRemoved(item)) return Promise.resolve(null);
        return save(item.dataset.sizeUrl, {
            width: String(width),
            height: String(height)
        }, '크기를 저장하지 못했습니다.');
    }

    function saveRotation(item, rotation) {
        if (isRemoved(item)) return Promise.resolve(null);
        return save(item.dataset.rotationUrl, {
            rotation: String(rotation)
        }, '회전 각도를 저장하지 못했습니다.');
    }

    function saveLayer(item, direction) {
        if (isRemoved(item)) return Promise.resolve(null);
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
