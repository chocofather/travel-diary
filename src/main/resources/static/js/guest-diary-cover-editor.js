/*
 * 비회원 체험 표지 편집기.
 *
 * 꾸미기 조작(스티커 붙이기 / 라벨기 글씨 / 옮기기·크기·회전·겹침)은 회원 표지 편집기와
 * 같은 파일이 그대로 맡는다. 이 파일이 하는 일은 세 가지뿐이다.
 *
 *   1. 저장 통로(DiarySaveTransport)에 브라우저 저장 구현을 끼운다.
 *      그래서 "회원이냐 비회원이냐" 가 편집 이벤트마다 갈리지 않는다.
 *   2. draft.coverDesign 을 읽어 표지 재질/색과 올려 둔 요소를 되살린다.
 *   3. 재질/색을 고르면 바로 draft 에 남긴다. (저장 버튼이 없다)
 *
 * 체험 표지는 언제나 한 장이라 회원의 "내 디자인" 목록·이름·삭제 개념이 없다.
 * 서버로 나가는 저장 요청도 없다. 이 파일에는 fetch 도 form 전송도 없다.
 * 사진 원본은 IndexedDB 에만 두고 draft 에는 photoRef 만 남는다. (GuestDiaryPhoto 가 맡는다)
 */
(function (global) {
    'use strict';

    const store = global.TravelDiaryGuestDraftStore;
    const page = document.querySelector('[data-guest-cover]');
    if (!store || !page) {
        return;
    }

    /*
     * 서버(DiaryCoverDesignElementServiceImpl)가 쓰는 것과 같은 기본 자리·크기.
     * 페이지 다꾸와 값이 다르다. 표지 쪽 값을 그대로 따라야 붙는 모습이 회원 화면과 같아진다.
     */
    const STICKER_SIZE = 0.22;
    const TAPE_WIDTH = 0.52;
    const TAPE_HEIGHT = 0.08;
    const LABEL_WIDTH = 0.44;
    const LABEL_HEIGHT = 0.09;
    const CENTER = 0.38;
    const OFFSET_STEP = 0.04;
    const OFFSET_CYCLE = 5;
    const DEFAULT_COVER_STYLE = 'DEFAULT';

    const board = page.querySelector('[data-guest-cover-board]');
    const missingNotice = page.querySelector('[data-guest-missing-draft]');
    const canvasBox = page.querySelector('[data-guest-cover-canvas]');
    const surface = page.querySelector('[data-guest-cover-surface]');
    const prompt = document.querySelector('[data-guest-prompt]');

    let cover = store.getCoverDesign();
    if (store.getDraft() === null) {
        show(board, false);
        show(missingNotice, true);
        return;
    }

    installGuestTransport();
    // 재질/색은 지금 바로 칠한다. 회원 편집기의 색 고르개 스크립트와 같은 자리를 쓴다.
    renderCoverSettings();
    wireSettings();
    wireActions();
    // 올려 둔 요소는 마크업 생성기가 등록된 뒤에 되살린다. (2단계 페이지 편집기와 같은 순서)
    document.addEventListener('DOMContentLoaded', restoreElements);
    // 사진 고르개도 같은 시점에 붙인다. (마크업 생성기가 등록된 뒤여야 한다)
    document.addEventListener('DOMContentLoaded', () => {
        global.GuestDiaryPhoto?.initialize(photoHost(), showPhotoStatus);
    });

    /* ===== 저장 통로 ===== */

    /*
     * 표지 편집 모듈이 부르는 저장을 전부 여기에서 받는다. 네트워크로 나가지 않는다.
     * 주소는 /guest/cover/... 라는 이름표이고, 대상은 체험 다이어리의 표지 한 장이다.
     */
    function installGuestTransport() {
        global.DiarySaveTransport.install(function (url, fields) {
            const elementMatch =
                /^\/guest\/cover\/elements\/([^/]+)\/([^/]+)$/.exec(url);
            if (elementMatch) {
                return elementCommand(elementMatch[1], elementMatch[2], fields);
            }
            switch (url) {
                case '/guest/cover/elements/sticker':
                    return createSticker(fields.sticker);
                case '/guest/cover/elements/label':
                    return createLabel(fields.text, fields.textFont, fields.textColor);
                default:
                    throw new Error('저장하지 못했습니다');
            }
        });
    }

    /** 옮기기 / 크기 / 회전 / 겹침 / 떼기. 주소 뒤쪽 이름이 무엇을 할지 정한다. */
    function elementCommand(elementId, command, fields) {
        if (command === 'delete') {
            // 지우기 전에 어떤 사진이었는지 봐 둔다. (뗀 뒤에는 draft 에서 찾을 수 없다)
            const removedPhotoRef = photoRefOf(elementId);
            const removed = store.removeCoverElement(elementId);
            if (!removed.ok) throw new Error('떼지 못했습니다.');
            refreshCover();
            releasePhoto(removedPhotoRef);
            return null;
        }
        if (command === 'layer') {
            return moveLayer(elementId, fields.direction);
        }

        const patch = {};
        if (command === 'position') {
            patch.positionX = Number(fields.positionX);
            patch.positionY = Number(fields.positionY);
        } else if (command === 'size') {
            patch.width = Number(fields.width);
            patch.height = Number(fields.height);
        } else if (command === 'rotation') {
            patch.rotation = Number(fields.rotation);
        } else {
            throw new Error('저장하지 못했습니다.');
        }

        const result = store.updateCoverElement(elementId, patch);
        if (!result.ok) throw new Error('저장하지 못했습니다.');
        refreshCover();
        return null;
    }

    /** 맨 앞/맨 뒤로 보내기. 서버가 하던 것처럼 정리된 순서를 돌려준다. */
    function moveLayer(elementId, direction) {
        const ordered = cover.elements.slice()
            .sort((left, right) => left.zIndex - right.zIndex)
            .map((item) => item.elementId)
            .filter((id) => id !== elementId);
        if (direction === 'front') {
            ordered.push(elementId);
        } else {
            ordered.unshift(elementId);
        }

        const layers = [];
        ordered.forEach((id, index) => {
            const zIndex = index + 1;
            store.updateCoverElement(id, {zIndex: zIndex});
            layers.push({id: id, zIndex: zIndex});
        });
        refreshCover();
        return {elements: layers};
    }

    /* ===== 사진 ===== */

    function photoRefOf(elementId) {
        const found = cover.elements.find((item) => item.elementId === elementId);
        return found ? found.photoRef : null;
    }

    /** 뗀 사진 정리. 같은 사진을 페이지에서도 쓰고 있으면 원본을 남긴다. */
    function releasePhoto(photoRef) {
        if (!photoRef || !global.GuestDiaryPhoto) return;
        global.GuestDiaryPhoto.release(photoRef, isPhotoStillUsed(photoRef));
    }

    /** 지금 draft 어딘가(표지 + 모든 장)에서 이 사진을 아직 쓰고 있는지. */
    function isPhotoStillUsed(photoRef) {
        const saved = store.getDraft();
        if (!saved) return false;
        const design = saved.coverDesign ? saved.coverDesign.elements : [];
        return usesPhoto(design, photoRef)
            || saved.pages.some((item) => usesPhoto(item.elements, photoRef));
    }

    function usesPhoto(elements, photoRef) {
        return (elements || []).some((item) => item.photoRef === photoRef);
    }

    /** 사진 모듈에 이 화면의 자리를 알려 준다. 페이지 쪽과 같은 보관소를 쓴다. */
    function photoHost() {
        return {
            scope: 'COVER',
            canvasAspect: global.GuestDiaryPhoto.COVER_CANVAS_ASPECT,
            alt: '표지 사진',
            draftId: () => store.getDraft()?.draftId,
            pageId: () => null,
            canvas: () => surface,
            placementOffset: placementOffset,
            elementUrls: elementUrls,
            attach: attach
        };
    }

    /* ===== 붙이기 ===== */

    /** 서버가 하던 것처럼 이미 붙어 있는 수만큼 조금씩 어긋나게 놓는다. */
    function placementOffset() {
        return OFFSET_STEP * (cover.elements.length % OFFSET_CYCLE);
    }

    function elementUrls(elementId) {
        const base = '/guest/cover/elements/' + elementId;
        return {
            position: base + '/position',
            size: base + '/size',
            rotation: base + '/rotation',
            layer: base + '/layer',
            delete: base + '/delete'
        };
    }

    function attach(element) {
        const result = store.addCoverElement(element);
        if (!result.ok) throw new Error('붙이지 못했습니다.');
        refreshCover();
        return result.element;
    }

    function createSticker(stickerId) {
        const option = document.querySelector(
            '.diary-sticker-option[data-sticker-id="' + cssEscape(stickerId) + '"]');
        if (!option) throw new Error('알 수 없는 스티커입니다.');

        const tape = option.dataset.stickerKind === 'masking-tape'
            || Boolean(option.dataset.tapeCenter);
        const offset = placementOffset();
        const saved = attach({
            elementType: 'STICKER',
            imageUrl: option.dataset.stickerImage,
            positionX: CENTER + offset,
            positionY: CENTER + offset,
            width: tape ? TAPE_WIDTH : STICKER_SIZE,
            height: tape ? TAPE_HEIGHT : STICKER_SIZE,
            rotation: 0
        });

        const payload = {
            id: saved.elementId,
            imageUrl: saved.imageUrl,
            maskingTape: tape,
            positionX: saved.positionX,
            positionY: saved.positionY,
            width: saved.width,
            height: saved.height,
            rotation: saved.rotation,
            zIndex: saved.zIndex,
            urls: elementUrls(saved.elementId)
        };
        if (option.dataset.tapeCenter) {
            payload.repeat = {
                left: option.dataset.tapeLeft,
                center: option.dataset.tapeCenter,
                right: option.dataset.tapeRight
            };
        }
        return payload;
    }

    function createLabel(text, textFont, textColor) {
        const offset = placementOffset();
        const saved = attach({
            elementType: 'TEXT',
            textContent: String(text === undefined ? '' : text),
            textFont: textFont || null,
            textColor: textColor || null,
            positionX: CENTER + offset,
            positionY: CENTER + offset,
            width: LABEL_WIDTH,
            height: LABEL_HEIGHT,
            rotation: 0
        });
        return labelPayload(saved);
    }

    function labelPayload(element) {
        return {
            id: element.elementId,
            elementType: 'TEXT',
            textContent: element.textContent || '',
            textFont: element.textFont || '',
            fontClass: element.textFont ? 'diary-font-' + element.textFont : '',
            textColor: element.textColor || '',
            positionX: element.positionX,
            positionY: element.positionY,
            width: element.width,
            height: element.height,
            rotation: element.rotation,
            zIndex: element.zIndex,
            urls: elementUrls(element.elementId)
        };
    }

    /* ===== 표지 설정 ===== */

    function refreshCover() {
        cover = store.getCoverDesign();
    }

    /** 저장해 둔 재질/색을 화면에 칠한다. 색 값은 #RRGGBB 만 쓴다. */
    function renderCoverSettings() {
        const style = (cover && cover.baseCoverStyle) || DEFAULT_COVER_STYLE;
        const chosen = document.querySelector(
            'input[name="baseCoverStyle"][value="' + cssEscape(style) + '"]')
            || document.querySelector('input[name="baseCoverStyle"]');
        if (chosen) {
            chosen.checked = true;
            paintMaterial(chosen.dataset.coverStyleClass);
        }

        const color = cover && cover.backgroundColor;
        const value = page.querySelector('[data-cover-color-value]');
        const picker = page.querySelector('[data-cover-color-input]');
        const field = page.querySelector('[data-cover-color-field]');
        if (value) value.value = color || '';
        if (picker && color) picker.value = color;
        if (field) field.classList.toggle('has-color', Boolean(color));
        paintColor(color);
    }

    /** 재질 class 는 고르는 자리의 값을 그대로 쓴다. 화면에 표를 다시 만들지 않는다. */
    function paintMaterial(styleClass) {
        Array.from(canvasBox.classList).forEach((name) => {
            if (name.startsWith('diary-cover-') && name !== 'diary-cover-canvas') {
                canvasBox.classList.remove(name);
            }
        });
        if (styleClass) canvasBox.classList.add(styleClass);
    }

    function paintColor(color) {
        if (color) {
            canvasBox.style.setProperty('--diary-cover-color', color);
        } else {
            canvasBox.style.removeProperty('--diary-cover-color');
        }
    }

    /* 재질/색은 고른 순간(change) 한 번만 저장한다. 끄는 동안 계속 쓰지 않는다. */
    function wireSettings() {
        document.querySelectorAll('input[name="baseCoverStyle"]').forEach((option) => {
            option.addEventListener('change', () => {
                if (!option.checked) return;
                paintMaterial(option.dataset.coverStyleClass);
                store.updateCoverDesign({baseCoverStyle: option.value});
                refreshCover();
            });
        });

        const value = page.querySelector('[data-cover-color-value]');
        const picker = page.querySelector('[data-cover-color-input]');
        const field = page.querySelector('[data-cover-color-field]');
        const clear = page.querySelector('[data-cover-color-clear]');

        // 색 고르개는 끄는 동안 input 이 계속 나므로 손을 뗀 뒤(change)에만 저장한다.
        picker?.addEventListener('input', () => paintColor(picker.value));
        picker?.addEventListener('change', () => {
            if (value) value.value = picker.value;
            field?.classList.add('has-color');
            paintColor(picker.value);
            store.updateCoverDesign({backgroundColor: picker.value});
            refreshCover();
        });

        clear?.addEventListener('click', () => {
            if (value) value.value = '';
            field?.classList.remove('has-color');
            paintColor('');
            store.updateCoverDesign({backgroundColor: null});
            refreshCover();
        });
    }

    /* ===== 되살리기 ===== */

    /**
     * 저장해 둔 표지 요소를 회원 화면과 같은 마크업으로 되살린다.
     *
     * <p>마크업 생성기(diaryElementRenderers)와 조작 엔진(diaryCanvas), 테이프 그리기(diaryTape)는
     * 모두 각 모듈의 DOMContentLoaded 안에서 등록된다. 그래서 이 되살리기는 그 뒤에 돌아야 한다.
     * 조작 엔진의 첫 훑기도 이미 끝난 뒤라 되살린 요소는 register 로 하나씩 넘겨 줘야 한다.
     */
    function restoreElements() {
        const renderers = global.diaryElementRenderers || {};
        refreshCover();
        surface.replaceChildren();
        if (!cover) return;

        cover.elements.slice()
            .sort((left, right) => left.zIndex - right.zIndex)
            .forEach((element) => {
                if (element.elementType === 'PHOTO') {
                    // 원본은 IndexedDB 에 있어 읽는 데 시간이 걸린다.
                    // 겹침 순서는 z-index 가 지키므로 늦게 붙어도 자리가 흐트러지지 않는다.
                    restorePhoto(element);
                    return;
                }
                const render = renderers[element.elementType];
                if (!render) return;
                const payload = restoredPayload(element);
                if (!payload) return;
                const item = render(payload);
                surface.append(item);
                global.diaryTape?.render(item);
                global.diaryCanvas?.register(item);
            });
    }

    /** 저장해 둔 표지 사진 한 장. 원본을 찾지 못하면 그 요소만 건너뛴다. */
    function restorePhoto(element) {
        if (!global.GuestDiaryPhoto) return;
        global.GuestDiaryPhoto
            .restore(element, elementUrls(element.elementId), '표지 사진')
            .then((item) => {
                if (!item) return;
                surface.append(item);
                // 되살린 사진도 옮기기/크기/회전이 붙어야 한다.
                global.diaryCanvas?.register(item);
            });
    }

    /** 저장해 둔 값을 붙일 때와 같은 모양으로 되돌린다. (renderer 가 그 모양을 기대한다) */
    function restoredPayload(element) {
        if (element.elementType === 'STICKER') {
            // 그림 경로는 저장해 둔 static 경로 그대로다. 테이프인지와 되풀이 조각만 다시 찾는다.
            const tape = global.diaryTape?.lookup(element.imageUrl)
                || {maskingTape: false, repeat: null};
            const payload = {
                id: element.elementId,
                imageUrl: element.imageUrl,
                maskingTape: tape.maskingTape,
                positionX: element.positionX,
                positionY: element.positionY,
                width: element.width,
                height: element.height,
                rotation: element.rotation,
                zIndex: element.zIndex,
                urls: elementUrls(element.elementId)
            };
            if (tape.repeat) {
                payload.repeat = tape.repeat;
            }
            return payload;
        }

        if (element.elementType === 'TEXT') {
            return labelPayload(element);
        }

        // 사진은 원본을 읽어야 해서 restorePhoto 가 따로 맡는다.
        return null;
    }

    /** 사진 안내는 꾸미기 도구가 이미 쓰고 있는 자리에 그대로 적는다. */
    function showPhotoStatus(text, isError) {
        const status = document.getElementById('diary-sticker-status');
        if (!status) return;
        status.textContent = text;
        status.classList.toggle('is-error', Boolean(isError));
    }

    /* ===== 안내 판 ===== */

    function wireActions() {
        prompt?.querySelector('[data-guest-action="close-prompt"]')
            ?.addEventListener('click', () => show(prompt, false));
        prompt?.addEventListener('click', (event) => {
            if (event.target === prompt) show(prompt, false);
        });
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') show(prompt, false);
        });
    }

    /* ===== 도구 ===== */

    function show(element, visible) {
        if (element) element.hidden = !visible;
    }

    /** 선택자에 값을 넣기 전에 따옴표 등을 막는다. */
    function cssEscape(value) {
        const text = String(value === undefined || value === null ? '' : value);
        return global.CSS && typeof global.CSS.escape === 'function'
            ? global.CSS.escape(text)
            : text.replace(/["\\]/g, '\\$&');
    }
})(window);
