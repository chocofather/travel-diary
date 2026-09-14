/*
 * 비회원 체험 사진 붙이기/되살리기.
 *
 * 페이지 다꾸와 표지 꾸미기가 이 한 벌을 함께 쓴다. 다른 것은 어느 캔버스에 붙느냐뿐이라
 * 각 편집기가 host 로 자기 자리를 알려 주고, 여기에서 파일 고르기부터 화면 그리기까지 맡는다.
 *
 * 원본(Blob)은 IndexedDB 에만 둔다. 서버로 올리지 않고, base64 로 바꾸지도 않는다.
 * draft 에는 photoRef 와 자리/크기만 남는다. blob: 주소는 화면 수명 동안만 쓰는 값이라
 * 절대 draft 에 저장하지 않는다. (새로고침하면 그 주소는 더 이상 열리지 않는다)
 *
 * 붙이는 차례는 "원본 먼저, 메타 나중" 이다.
 *   파일 검증 → IndexedDB 저장 → draft 에 요소 추가 → 화면 그리기
 * 뒤쪽이 실패하면 방금 넣은 Blob 을 도로 지운다. 그래야
 * "화면에는 보이는데 새로고침하면 사라지는" 요소나 주인 없는 Blob 이 남지 않는다.
 */
(function (global) {
    'use strict';

    const store = global.TravelDiaryGuestPhotoStore;

    /*
     * 받아들일 사진. 회원 화면과 같은 한도(Spring max-file-size 10MB)를 쓴다.
     *
     * 종류는 서버가 실제로 펼쳐 볼 수 있는 것만 둔다. 나중에 내 여행일기로 가져올 때
     * 서버가 같은 형식만 받으므로, 여기만 넓히면 "붙일 수는 있는데 가져올 수 없는 사진" 이 생긴다.
     * (WebP 는 표준 ImageIO 에 reader 가 없다. SVG 는 스크립트를 품을 수 있어 받지 않는다)
     */
    const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/gif'];
    const MAX_SIZE = 10 * 1024 * 1024;

    /* 서버(DiaryPhotoFrame)가 쓰는 것과 같은 값. 붙는 모습이 회원 화면과 같아진다. */
    const PHOTO_WIDTH = 0.34;
    const PAGE_CANVAS_ASPECT = 41 / 38;
    const COVER_CANVAS_ASPECT = 3 / 4;
    const INNER_WIDTH = 1 - 2 * 0.035;
    const FRAME_HEIGHT = 0.035 + 0.08;
    const RATIO_MIN = 0.2;
    const RATIO_MAX = 5.0;
    const HEIGHT_MAX = 0.9;
    const SIZE_MIN = 0.01;
    const FULL_PHOTO_STYLE = 'FULL';

    /*
     * photoRef → 지금 화면에서 쓰는 blob: 주소.
     * 같은 사진을 다시 그릴 때 주소를 새로 만들지 않게 하고, 떠날 때 한 번에 돌려주기 위해 모아 둔다.
     */
    const objectUrls = new Map();

    // 화면을 떠날 때 만들어 둔 주소를 모두 돌려준다. (메모리 누수 방지)
    global.addEventListener('pagehide', releaseAll);

    function releaseAll() {
        objectUrls.forEach((url) => global.URL.revokeObjectURL(url));
        objectUrls.clear();
    }

    /** 이 사진의 blob: 주소 하나만 돌려준다. (요소를 뗐을 때) */
    function releaseUrl(photoRef) {
        const url = objectUrls.get(photoRef);
        if (!url) return;
        global.URL.revokeObjectURL(url);
        objectUrls.delete(photoRef);
    }

    /** 보관해 둔 Blob 의 화면용 주소. 이미 만들어 둔 것이 있으면 그대로 쓴다. */
    async function objectUrlFor(photoRef) {
        if (objectUrls.has(photoRef)) {
            return objectUrls.get(photoRef);
        }
        if (!store) return null;
        const found = await store.getPhoto(photoRef);
        if (!found.ok || !found.photo.blob) {
            return null;
        }
        const url = global.URL.createObjectURL(found.photo.blob);
        objectUrls.set(photoRef, url);
        return url;
    }

    /* ===== 파일 검증 ===== */

    /**
     * 고른 파일이 실제로 쓸 수 있는 사진인지.
     * 확장자 문자열은 믿지 않는다. MIME 과 실제로 그려지는지까지 본다.
     * (클라이언트 검증은 보안 경계가 아니다. 로그인 후 옮길 때 서버가 다시 확인한다)
     */
    async function inspect(file) {
        if (!file || ALLOWED_TYPES.indexOf(file.type) === -1) {
            return {ok: false, message: 'JPG, PNG, GIF 사진만 붙일 수 있어요.'};
        }
        if (file.size > MAX_SIZE) {
            return {ok: false, message: '사진은 10MB까지 붙일 수 있어요.'};
        }

        const size = await decodedSize(file);
        if (!size) {
            return {ok: false, message: '이 사진을 열 수 없어요. 다른 파일을 골라 주세요.'};
        }
        return {ok: true, width: size.width, height: size.height};
    }

    /** 실제로 그려지는지 확인하면서 원본 크기도 함께 얻는다. */
    function decodedSize(file) {
        if (typeof global.createImageBitmap === 'function') {
            return global.createImageBitmap(file).then((bitmap) => {
                const size = {width: bitmap.width, height: bitmap.height};
                bitmap.close?.();
                return size;
            }).catch(() => null);
        }
        // createImageBitmap 이 없는 브라우저. 같은 확인을 <img> 로 한다.
        return new Promise((resolve) => {
            const url = global.URL.createObjectURL(file);
            const image = new Image();
            image.onload = () => {
                resolve({width: image.naturalWidth, height: image.naturalHeight});
                global.URL.revokeObjectURL(url);
            };
            image.onerror = () => {
                resolve(null);
                global.URL.revokeObjectURL(url);
            };
            image.src = url;
        });
    }

    /* ===== 처음 놓는 크기 ===== */

    function clampRatio(photoRatio) {
        return photoRatio > 0
            ? Math.min(Math.max(photoRatio, RATIO_MIN), RATIO_MAX) : 1.0;
    }

    /**
     * 폴라로이드의 처음 크기. 서버 DiaryPhotoFrame.polaroidSize 와 같은 셈이다.
     * 사진 자리가 원본 비율 그대로가 되도록 높이를 구하고, 캔버스를 넘으면 함께 줄인다.
     */
    function polaroidSize(photoRatio, canvasAspect) {
        const ratio = clampRatio(photoRatio);
        let width = PHOTO_WIDTH;
        let height = width * canvasAspect * (INNER_WIDTH / ratio + FRAME_HEIGHT);
        if (height > HEIGHT_MAX) {
            width = width * HEIGHT_MAX / height;
            height = HEIGHT_MAX;
        }
        return {width: round(width), height: round(height)};
    }

    /**
     * 일반 사진(FULL)의 처음 크기. 서버 DiaryPhotoFrame.fullSize 와 같은 셈이다.
     *
     * 프레임이 없어 요소 상자가 곧 사진이므로 상자의 화면 비율이 원본과 같아야 잘리지 않는다.
     * 좌표가 0~1 상대값이라 캔버스의 가로/세로가 그대로 곱해진다. 그래서 상자를 정사각으로 두면
     * 세로로 긴 표지에서는 세로 상자가 되어 가로 사진의 좌우가 잘린다.
     *
     * 화면 비율 = (너비 × 캔버스폭) / (높이 × 캔버스높이) 이므로
     * 높이 = 너비 × 캔버스비율 / 원본비율 이다. 긴 쪽을 기본 크기로 잡는다.
     */
    function fullSize(photoRatio, canvasAspect) {
        const ratio = clampRatio(photoRatio);
        let width = PHOTO_WIDTH;
        let height = width * canvasAspect / ratio;
        if (height > PHOTO_WIDTH) {
            // 화면에서 세로로 긴 사진이다. 높이를 기준으로 잡는다.
            height = PHOTO_WIDTH;
            width = height * ratio / canvasAspect;
        }
        return {
            width: round(Math.max(width, SIZE_MIN)),
            height: round(Math.max(height, SIZE_MIN))
        };
    }

    function round(value) {
        return Math.round(value * 100000) / 100000;
    }

    function photoStyleClass(photoStyle) {
        return photoStyle === FULL_PHOTO_STYLE ? 'is-photo-full' : 'is-photo-polaroid';
    }

    /* ===== 화면에 그릴 값 ===== */

    /**
     * 회원 사진 renderer 가 기대하는 모양으로 맞춘다.
     * imageUrl 만 서버 경로가 아니라 이 화면에서 만든 blob: 주소다.
     */
    function payloadOf(element, imageUrl, urls, alt) {
        return {
            id: element.elementId,
            elementType: 'PHOTO',
            imageUrl: imageUrl,
            alt: alt,
            photoStyleClass: photoStyleClass(element.photoStyle),
            positionX: element.positionX,
            positionY: element.positionY,
            width: element.width,
            height: element.height,
            rotation: element.rotation,
            zIndex: element.zIndex,
            urls: urls
        };
    }

    function renderer() {
        return (global.diaryElementRenderers || {}).PHOTO || null;
    }

    /* ===== 붙이기 ===== */

    /**
     * 고른 파일 한 장을 붙인다.
     *
     * @param host 편집기가 알려 준 자리. scope/canvasAspect/canvas/draftId/pageId 와
     *             attach(element) · detach(elementId) · elementUrls(elementId) 를 갖는다.
     */
    async function attachFile(host, file, photoStyle) {
        if (!store) {
            return {ok: false, message: '이 브라우저에서는 사진 임시 저장을 사용할 수 없습니다.'};
        }

        const checked = await inspect(file);
        if (!checked.ok) {
            return {ok: false, message: checked.message};
        }

        // 1) 원본을 먼저 보관한다. 여기가 실패하면 draft 에는 아무것도 남지 않는다.
        const saved = await store.savePhoto({
            draftId: host.draftId(),
            scope: host.scope,
            pageId: host.pageId ? host.pageId() : null,
            file: file,
            width: checked.width,
            height: checked.height
        });
        if (!saved.ok) {
            return {ok: false, message: storageMessage(saved.reason)};
        }

        // 2) draft 에 메타데이터만 남긴다. Blob 도 blob: 주소도 담지 않는다.
        const ratio = checked.height > 0 ? checked.width / checked.height : 1;
        const size = photoStyle === FULL_PHOTO_STYLE
            ? fullSize(ratio, host.canvasAspect)
            : polaroidSize(ratio, host.canvasAspect);
        const offset = host.placementOffset();

        let element;
        try {
            element = host.attach({
                elementType: 'PHOTO',
                photoRef: saved.photoRef,
                // 서버 경로가 아니다. 화면 주소는 그릴 때마다 photoRef 로 새로 얻는다.
                imageUrl: null,
                photoStyle: photoStyle,
                positionX: offset,
                positionY: offset,
                width: size.width,
                height: size.height,
                rotation: 0
            });
        } catch (error) {
            // draft 에 남기지 못했다. 방금 넣은 Blob 을 도로 지워 주인 없는 사진을 만들지 않는다.
            await store.deletePhoto(saved.photoRef);
            return {ok: false, message: '사진을 붙이지 못했습니다.'};
        }

        // 3) 화면에 그린다.
        const url = await objectUrlFor(saved.photoRef);
        const render = renderer();
        if (!url || !render) {
            return {ok: true, element: element};
        }
        const item = render(payloadOf(
            element, url, host.elementUrls(element.elementId), host.alt));
        host.canvas().append(item);
        global.diaryCanvas?.register(item);
        global.diaryCanvas?.select(item);
        return {ok: true, element: element, item: item};
    }

    function storageMessage(reason) {
        if (reason === store.REASON.QUOTA_EXCEEDED) {
            return '브라우저 저장 공간이 부족해 사진을 추가할 수 없습니다.';
        }
        if (reason === store.REASON.UNAVAILABLE) {
            return '이 브라우저에서는 사진 임시 저장을 사용할 수 없습니다.';
        }
        return '사진을 붙이지 못했습니다.';
    }

    /* ===== 되살리기 ===== */

    /**
     * 저장해 둔 사진 한 장을 되살린다.
     * Blob 을 찾지 못하면(브라우저 저장소를 비운 경우 등) 그 요소만 건너뛴다.
     */
    async function restore(element, urls, alt) {
        const url = await objectUrlFor(element.photoRef);
        const render = renderer();
        if (!url || !render) return null;
        return render(payloadOf(element, url, urls, alt));
    }

    /**
     * 화면에 이미 있는 <img> 에 사진을 채운다. (책장 카드의 작은 표지처럼 보기 전용인 자리)
     * 되살리기와 같은 주소 모음을 쓰므로 같은 사진에 주소가 두 번 생기지 않는다.
     */
    function fillImage(image, photoRef) {
        objectUrlFor(photoRef).then((url) => {
            if (url) image.src = url;
        });
    }

    /* ===== 떼기 ===== */

    /**
     * 요소에서 뗀 사진을 정리한다.
     * 아직 다른 곳(다른 장이나 표지)에서 쓰고 있으면 Blob 을 남긴다.
     *
     * @param stillUsed 지금 draft 가 여전히 쓰고 있는지
     */
    async function release(photoRef, stillUsed) {
        if (!photoRef) return;
        if (stillUsed) return;
        releaseUrl(photoRef);
        if (store) await store.deletePhoto(photoRef);
    }

    global.GuestDiaryPhoto = {
        ALLOWED_TYPES: ALLOWED_TYPES,
        MAX_SIZE: MAX_SIZE,
        PAGE_CANVAS_ASPECT: PAGE_CANVAS_ASPECT,
        COVER_CANVAS_ASPECT: COVER_CANVAS_ASPECT,
        isAvailable: () => Boolean(store),
        attachFile: attachFile,
        restore: restore,
        fillImage: fillImage,
        release: release,
        releaseUrl: releaseUrl,
        releaseAll: releaseAll,

        /**
         * 파일 고르개를 붙인다. 회원 화면과 같은 자리·같은 모습의 input 을 그대로 쓴다.
         * 고른 뒤 값을 비워 같은 파일을 다시 골라도 동작하게 한다.
         */
        initialize(host, onStatus) {
            const inputs = Array.from(
                document.querySelectorAll('[data-guest-photo-input]'));
            if (!inputs.length) return;

            let busy = false;
            inputs.forEach((input) => input.addEventListener('change', async () => {
                const files = Array.from(input.files || []);
                input.value = '';
                if (!files.length || busy) return;

                busy = true;
                onStatus('사진을 붙이는 중…');
                try {
                    let placed = 0;
                    for (const file of files) {
                        const result = await attachFile(
                            host, file, input.dataset.photoStyle || '');
                        if (!result.ok) {
                            onStatus(result.message, true);
                            break;
                        }
                        placed += 1;
                    }
                    if (placed > 0) {
                        onStatus(placed > 1 ? `사진 ${placed}장을 붙였습니다` : '붙였습니다');
                    }
                } finally {
                    busy = false;
                }
            }));
        }
    };
})(window);
