/*
 * 비회원 다이어리 체험 편집기.
 *
 * 편집 조작(본문 쓰기 / 한 줄 메모 / 스티커·라벨·메모지 붙이기 / 옮기기·크기·회전·겹침)은
 * 회원 편집기와 같은 파일이 그대로 맡는다. 이 파일이 하는 일은 두 가지뿐이다.
 *
 *   1. 저장 통로(DiarySaveTransport)에 브라우저 저장 구현을 끼운다.
 *      그래서 "회원이냐 비회원이냐" 가 편집 이벤트마다 갈리지 않는다.
 *   2. draft 를 읽어 지금 보고 있는 한 장을 화면에 채우고, 장 넘김/추가/삭제를 맡는다.
 *
 * 서버로 나가는 저장 요청은 없다. 이 파일에는 fetch 도 form 전송도 없다.
 * 사진 원본은 IndexedDB 에만 두고 draft 에는 photoRef 만 남는다. (GuestDiaryPhoto 가 맡는다)
 *
 * 저장 주소(/guest/...)는 네트워크 주소가 아니라 저장소를 가리키는 이름표다.
 */
(function (global) {
    'use strict';

    const store = global.TravelDiaryGuestDraftStore;
    const page = document.querySelector('[data-guest-editor]');
    if (!store || !page) {
        return;
    }

    /* 서버(DiaryController)가 쓰는 것과 같은 기본 자리·크기. 붙는 모습이 회원 화면과 같아진다. */
    const STICKER_SIZE = 0.18;
    const TAPE_WIDTH = 0.46;
    const TAPE_HEIGHT = 0.09;
    const LABEL_WIDTH = 0.30;
    const LABEL_HEIGHT = 0.08;
    const MEMO_WIDTH = 0.26;
    const MEMO_HEIGHT = 0.28;
    const CENTER = 0.41;
    const OFFSET_STEP = 0.04;
    const OFFSET_CYCLE = 5;
    const MEMO_STYLE_PREFIX = 'MEMO';

    const board = page.querySelector('[data-guest-board]');
    const missingNotice = page.querySelector('[data-guest-missing-draft]');
    const canvas = page.querySelector('[data-guest-canvas]');
    const sheet = page.querySelector('[data-guest-sheet]');
    const prompt = document.querySelector('[data-guest-prompt]');
    /** [새 페이지] 팝오버. 날짜를 고른 뒤에 만든다. */
    const pageAdd = page.querySelector('[data-guest-page-add]');

    let draft = store.getDraft();
    let current = null;

    /* ===== 시작 ===== */

    /*
     * 이 화면은 "이미 만들어 둔 체험 다이어리를 여는" 자리다.
     * 다이어리 만들기는 책장(/diaries/demo)에서만 시작하므로 여기에서 새로 만들지 않는다.
     */
    if (!draft) {
        backToShelf();
        return;
    }
    if (draft.pages.length === 0) {
        /*
         * 만들기 화면이 첫 장을 함께 만들지만, 마지막 장을 지운 경우를 대비해 한 장을 연다.
         * 첫 장의 날짜는 사용자가 이미 입력한 여행 시작일이다. (만들기 화면과 같은 규칙)
         */
        store.addPage({pageDate: draft.startDate});
        draft = store.getDraft();
    }
    current = resolveCurrentPage();
    if (!current) {
        backToShelf();
        return;
    }

    installGuestTransport();
    /*
     * 종이/머리말/본문은 지금 바로 채운다. 본문 Quill 과 조작 엔진이 자기 DOMContentLoaded 에서
     * 이 값들(data-content-url / data-header-url)을 찾기 때문에 그보다 먼저 있어야 한다.
     */
    renderCurrentPage();
    wireActions();
    // 꾸미기 요소만 마크업 생성기가 등록된 뒤에 되살린다.
    document.addEventListener('DOMContentLoaded', restoreElements);
    // 사진 고르개도 같은 시점에 붙인다. (마크업 생성기가 등록된 뒤여야 한다)
    document.addEventListener('DOMContentLoaded', () => {
        global.GuestDiaryPhoto?.initialize(photoHost(), showPhotoStatus);
    });

    /** 열 다이어리가 없다. 안내를 잠깐 보여 주고 책장으로 돌려보낸다. */
    function backToShelf() {
        show(board, false);
        show(missingNotice, true);
        global.location.replace(page.dataset.shelfUrl);
    }

    /* ===== 저장 통로 ===== */

    /*
     * 편집 모듈들이 부르는 저장을 전부 여기에서 받는다. 네트워크로 나가지 않는다.
     * 주소는 /guest/elements/... 같은 이름표이고, 대상 장은 지금 열려 있는 장이다.
     */
    function installGuestTransport() {
        global.DiarySaveTransport.install(function (url, fields) {
            const elementMatch = /^\/guest\/elements\/([^/]+)\/([^/]+)$/.exec(url);
            if (elementMatch) {
                return elementCommand(elementMatch[1], elementMatch[2], fields);
            }
            switch (url) {
                case '/guest/elements/sticker':
                    return createSticker(fields.sticker);
                case '/guest/elements/note':
                    return createNote(fields.style, fields.color);
                case '/guest/elements/label':
                    return createLabel(fields.text, fields.textFont, fields.textColor);
                case '/guest/page/content':
                    return savePageField({content: fields.content});
                case '/guest/page/header':
                    return savePageField({
                        pageHeader: fields.pageHeader,
                        pageHeaderFont: fields.pageHeaderFont,
                        pageHeaderBold: fields.pageHeaderBold === 'true'
                    });
                default:
                    throw new Error('저장하지 못했습니다');
            }
        });
    }

    function savePageField(patch) {
        const result = store.updatePage(current.pageId, patch);
        if (!result.ok) throw new Error('저장하지 못했습니다');
        current = result.page;
        return null;
    }

    /** 옮기기 / 크기 / 회전 / 겹침 / 지우기. 주소 뒤쪽 이름이 무엇을 할지 정한다. */
    function elementCommand(elementId, command, fields) {
        if (command === 'delete') {
            // 지우기 전에 어떤 사진이었는지 봐 둔다. (뗀 뒤에는 draft 에서 찾을 수 없다)
            const removedPhotoRef = photoRefOf(elementId);
            const removed = store.removeElement(current.pageId, elementId);
            if (!removed.ok) throw new Error('삭제하지 못했습니다.');
            refreshCurrent();
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
        } else if (command === 'text') {
            patch.textContent = String(fields.text === undefined ? '' : fields.text);
        } else {
            throw new Error('저장하지 못했습니다.');
        }

        const result = store.updateElement(current.pageId, elementId, patch);
        if (!result.ok) throw new Error('저장하지 못했습니다.');
        refreshCurrent();
        // 라벨/메모지 글 저장만 저장된 값을 돌려받아 화면을 맞춘다.
        return command === 'text' ? {textContent: result.element.textContent} : null;
    }

    /**
     * 맨 앞/맨 뒤로 보내기. 서버가 하던 것처럼 정리된 순서를 돌려준다.
     * 화면은 돌려받은 값으로만 z-index 를 다시 칠한다.
     */
    function moveLayer(elementId, direction) {
        const ordered = current.elements.slice()
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
            store.updateElement(current.pageId, id, {zIndex: zIndex});
            layers.push({id: id, zIndex: zIndex});
        });
        refreshCurrent();
        return {elements: layers};
    }

    /* ===== 사진 ===== */

    function photoRefOf(elementId) {
        const found = current.elements.find((item) => item.elementId === elementId);
        return found ? found.photoRef : null;
    }

    /**
     * 뗀 사진 정리. 같은 사진을 다른 장이나 표지에서도 쓰고 있으면 원본을 남긴다.
     * (지금은 한 요소당 한 장이지만, 그 전제가 깨져도 남의 사진을 지우지 않게 확인한다)
     */
    function releasePhoto(photoRef) {
        if (!photoRef || !global.GuestDiaryPhoto) return;
        global.GuestDiaryPhoto.release(photoRef, isPhotoStillUsed(photoRef));
    }

    /** 지금 draft 어딘가(모든 장 + 표지)에서 이 사진을 아직 쓰고 있는지. */
    function isPhotoStillUsed(photoRef) {
        const saved = store.getDraft();
        if (!saved) return false;
        const cover = saved.coverDesign ? saved.coverDesign.elements : [];
        return saved.pages.some((item) => usesPhoto(item.elements, photoRef))
            || usesPhoto(cover, photoRef);
    }

    function usesPhoto(elements, photoRef) {
        return (elements || []).some((item) => item.photoRef === photoRef);
    }

    /** 사진 모듈에 이 화면의 자리를 알려 준다. 붙이기·되살리기가 같은 값을 쓴다. */
    function photoHost() {
        return {
            scope: 'PAGE',
            canvasAspect: global.GuestDiaryPhoto.PAGE_CANVAS_ASPECT,
            alt: '여행일기 사진',
            draftId: () => store.getDraft()?.draftId,
            pageId: () => current.pageId,
            canvas: () => canvas,
            placementOffset: placementOffset,
            elementUrls: elementUrls,
            attach: attach
        };
    }

    /* ===== 붙이기 ===== */

    /** 서버가 하던 것처럼 이미 붙어 있는 수만큼 조금씩 어긋나게 놓는다. */
    function placementOffset() {
        return OFFSET_STEP * (current.elements.length % OFFSET_CYCLE);
    }

    function elementUrls(elementId, deleteCommand) {
        const base = '/guest/elements/' + elementId;
        return {
            position: base + '/position',
            size: base + '/size',
            rotation: base + '/rotation',
            layer: base + '/layer',
            text: base + '/text',
            delete: base + '/delete'
        };
    }

    function attach(element) {
        const result = store.addElement(current.pageId, element);
        if (!result.ok) throw new Error('붙이지 못했습니다.');
        refreshCurrent();
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

    function createNote(styleType, colorType) {
        const option = document.querySelector(
            '.diary-note-option[data-note-style="' + cssEscape(styleType) + '"]');
        if (!option) throw new Error('알 수 없는 디자인입니다.');

        const memo = String(styleType || '').indexOf(MEMO_STYLE_PREFIX) === 0
            || option.closest('[data-decor-panel="memo"]') !== null;
        const offset = placementOffset();
        const saved = attach({
            elementType: 'NOTE',
            textContent: '',
            styleType: styleType,
            colorType: colorType || null,
            positionX: CENTER + offset,
            positionY: CENTER + offset,
            width: memo ? MEMO_WIDTH : LABEL_WIDTH,
            height: memo ? MEMO_HEIGHT : LABEL_HEIGHT,
            rotation: 0
        });

        return {
            id: saved.elementId,
            elementType: 'NOTE',
            styleType: saved.styleType,
            styleClass: option.dataset.noteStyleClass || '',
            colorType: saved.colorType || '',
            colorClass: noteColorClass(colorType),
            label: option.dataset.noteLabel || '',
            textContent: saved.textContent || '',
            positionX: saved.positionX,
            positionY: saved.positionY,
            width: saved.width,
            height: saved.height,
            rotation: saved.rotation,
            zIndex: saved.zIndex,
            urls: elementUrls(saved.elementId)
        };
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

        return {
            id: saved.elementId,
            elementType: 'TEXT',
            textContent: saved.textContent,
            textFont: saved.textFont || '',
            fontClass: saved.textFont ? 'diary-font-' + saved.textFont : '',
            textColor: saved.textColor || '',
            positionX: saved.positionX,
            positionY: saved.positionY,
            width: saved.width,
            height: saved.height,
            rotation: saved.rotation,
            zIndex: saved.zIndex,
            urls: elementUrls(saved.elementId)
        };
    }

    /** 색 코드에 맞는 class 는 색 고르개 버튼이 들고 있다. 화면에 색 값을 적지 않는다. */
    function noteColorClass(colorType) {
        if (!colorType) return '';
        const swatch = document.querySelector(
            '.diary-note-swatch[data-note-color="' + cssEscape(colorType) + '"]');
        return swatch ? (swatch.dataset.noteColorClass || '') : '';
    }

    /* ===== 화면 그리기 ===== */

    function resolveCurrentPage() {
        const saved = draft.pages.find((item) => item.pageId === draft.currentPageId);
        return saved || draft.pages[0] || null;
    }

    function refreshCurrent() {
        draft = store.getDraft();
        if (!draft) return;
        const found = draft.pages.find((item) => item.pageId === current.pageId);
        if (found) current = found;
    }

    /*
     * 지금 보고 있는 한 장을 채운다. 이 함수는 다른 편집 모듈이 붙기 전에 한 번만 돈다.
     * (defer 스크립트라 DOM 은 이미 다 읽혔고 DOMContentLoaded 는 아직이다)
     */
    function renderCurrentPage() {
        store.setCurrentPageId(current.pageId);

        sheet.className = 'diary-sheet diary-sheet-single'
            + ' diary-sheet-bg-' + String(current.backgroundType || 'PLAIN').toLowerCase();
        if (current.paperColor) {
            sheet.style.setProperty('--diary-paper-color', current.paperColor);
        } else {
            sheet.style.removeProperty('--diary-paper-color');
        }

        // 사용자 입력은 textContent/value 로만 넣는다. innerHTML 로 넣지 않는다.
        page.querySelector('[data-guest-sheet-date]').textContent = current.pageDate || '';
        page.querySelector('[data-guest-page-number]').textContent =
            String(current.pageOrder);

        const header = page.querySelector('[data-guest-header]');
        header.value = current.pageHeader || '';
        header.dataset.headerFont = current.pageHeaderFont || 'DEFAULT';
        header.dataset.headerBold = current.pageHeaderBold ? 'true' : 'false';
        header.dataset.headerUrl = '/guest/page/header';
        if (current.pageHeaderFont && current.pageHeaderFont !== 'DEFAULT') {
            header.classList.add('diary-font-' + current.pageHeaderFont);
        }
        if (current.pageHeaderBold) header.classList.add('is-bold');

        /*
         * 본문. Quill 이 붙기 전의 초기 HTML 이라 여기에만 innerHTML 을 쓴다.
         * 값은 이 브라우저에서 Quill 이 만들어 저장한 것이고, 서버로 보낼 때는
         * 로그인 이후 단계에서 서버가 다시 검사한다.
         */
        const content = page.querySelector('[data-guest-content]');
        content.dataset.pageId = current.pageId;
        content.dataset.contentUrl = '/guest/page/content';
        content.innerHTML = current.content || '';

        renderPageControls();
        renderSettingsForm();
        renderPageDateNotice();
    }

    /**
     * 아직 채워지지 않은 것에 대한 안내.
     *
     * <p>순서가 있다. 여행 기간이 없으면 장의 날짜를 정할 수 없으므로 먼저 그것부터 알린다.
     * 기간이 있는데 이 장의 날짜만 없으면 그 장을 채우도록 안내한다.
     * 둘을 같이 띄우면 무엇부터 해야 하는지 알 수 없다.
     */
    function renderPageDateNotice() {
        const ready = basicsReady();
        const undated = store.pagesMissingDate(store.getDraft())
            .some((item) => item.pageId === current.pageId);
        show(page.querySelector('[data-guest-basics-missing]'), !ready);
        show(page.querySelector('[data-guest-page-date-missing]'), ready && undated);
    }

    /** 제목·여행 기간이 갖춰진 체험 여행일기인지. 만들기 화면과 같은 규칙을 그대로 본다. */
    function basicsReady() {
        return store.isComplete(store.getDraft());
    }

    /**
     * 저장해 둔 꾸미기 요소를 회원 화면과 같은 마크업으로 되살린다.
     *
     * <p>마크업 생성기(diaryElementRenderers)와 조작 엔진(diaryCanvas), 테이프 그리기(diaryTape)는
     * 모두 각 모듈의 DOMContentLoaded 안에서 등록된다. 그래서 이 되살리기는 그 뒤에 돌아야 한다.
     * 이 파일이 마지막 defer 스크립트라 여기 등록한 listener 가 가장 나중에 불린다.
     *
     * <p>조작 엔진의 첫 훑기는 이미 끝난 뒤라 캔버스가 비어 있었다. 그래서 되살린 요소는
     * 스티커를 새로 붙일 때와 같이 register 로 하나씩 넘겨 줘야 옮기기/크기/회전이 붙는다.
     */
    function restoreElements() {
        const renderers = global.diaryElementRenderers || {};
        canvas.replaceChildren();
        current.elements.slice()
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
                canvas.append(item);
                global.diaryTape?.render(item);
                global.diaryCanvas?.register(item);
            });
    }

    /** 저장해 둔 사진 한 장. 원본을 찾지 못하면 그 요소만 건너뛴다. */
    function restorePhoto(element) {
        if (!global.GuestDiaryPhoto) return;
        global.GuestDiaryPhoto
            .restore(element, elementUrls(element.elementId), '여행일기 사진')
            .then((item) => {
                if (!item) return;
                canvas.append(item);
                // 되살린 사진도 옮기기/크기/회전이 붙어야 한다.
                global.diaryCanvas?.register(item);
            });
    }

    /** 저장해 둔 값을 붙일 때와 같은 모양으로 되돌린다. (renderer 가 그 모양을 기대한다) */
    function restoredPayload(element) {
        const common = {
            id: element.elementId,
            positionX: element.positionX,
            positionY: element.positionY,
            width: element.width,
            height: element.height,
            rotation: element.rotation,
            zIndex: element.zIndex,
            urls: elementUrls(element.elementId)
        };

        if (element.elementType === 'STICKER') {
            // 테이프인지와 되풀이 조각은 저장된 그림 경로로 다시 찾는다. (서버와 같은 규칙)
            const tape = global.diaryTape?.lookup(element.imageUrl)
                || {maskingTape: false, repeat: null};
            const payload = Object.assign({}, common, {
                imageUrl: element.imageUrl,
                maskingTape: tape.maskingTape
            });
            if (tape.repeat) {
                payload.repeat = tape.repeat;
            }
            return payload;
        }

        if (element.elementType === 'NOTE') {
            const option = document.querySelector(
                '.diary-note-option[data-note-style="'
                + cssEscape(element.styleType) + '"]');
            return Object.assign({}, common, {
                elementType: 'NOTE',
                styleType: element.styleType,
                styleClass: option ? (option.dataset.noteStyleClass || '') : '',
                colorType: element.colorType || '',
                colorClass: noteColorClass(element.colorType),
                label: option ? (option.dataset.noteLabel || '') : '',
                textContent: element.textContent || ''
            });
        }

        if (element.elementType === 'TEXT') {
            return Object.assign({}, common, {
                elementType: 'TEXT',
                textContent: element.textContent || '',
                textFont: element.textFont || '',
                fontClass: element.textFont ? 'diary-font-' + element.textFont : '',
                textColor: element.textColor || ''
            });
        }

        // 사진은 원본을 읽어야 해서 restorePhoto 가 따로 맡는다.
        return null;
    }

    function renderPageControls() {
        const title = page.querySelector('[data-guest-diary-title]');
        // 사용자가 쓴 값이라 글자로만 넣는다.
        // 없는 제목을 지어내지 않는다. 비어 있으면 비어 있다고 보여 준다.
        if (title) title.textContent = draft.title || '제목 없음';

        const total = draft.pages.length;
        const index = draft.pages.findIndex((item) => item.pageId === current.pageId);
        page.querySelector('[data-guest-page-count]').textContent =
            total + ' / ' + store.MAX_PAGES + '장';
        page.querySelector('[data-guest-page-position]').textContent =
            (index + 1) + ' / ' + total;
        action('previous-page').disabled = index <= 0;
        action('next-page').disabled = index < 0 || index >= total - 1;
        action('delete-page').disabled = total <= 1;
    }

    function renderSettingsForm() {
        const date = page.querySelector('[data-guest-page-date]');
        // 고를 수 있는 범위는 이 여행일기의 여행 기간이다. (회원 화면과 같은 min/max)
        limitToTravelPeriod(date);
        /*
         * 여행 기간이 없으면 날짜 칸을 잠근다. 아무 날짜나 넣어 보고 거절당하는 대신
         * 채울 자리를 바로 알려 준다. (배경·종이색은 그대로 고칠 수 있다)
         */
        const ready = basicsReady();
        date.disabled = !ready;
        show(page.querySelector('[data-guest-page-date-locked]'), !ready);
        date.value = current.pageDate || '';
        page.querySelector('[data-guest-page-background]').value =
            current.backgroundType || 'PLAIN';
        const paper = page.querySelector('[data-guest-page-paper]');
        paper.value = current.paperColor || '';
    }

    /* ===== 페이지 날짜 ===== */

    /**
     * 날짜 칸이 고를 수 있는 범위. 이 여행일기의 여행 기간이다.
     *
     * <p>화면의 min/max 는 고르기 편하라고 두는 것이고, 실제 판정은 저장소가 다시 한다.
     * (손으로 값을 넣어도 여행 기간 밖의 날짜는 draft 에 들어가지 않는다)
     */
    function limitToTravelPeriod(input) {
        if (!input) return;
        if (draft.startDate) input.min = draft.startDate;
        if (draft.endDate) input.max = draft.endDate;
    }

    /**
     * 새 장에 먼저 넣어 둘 날짜.
     *
     * <p>보통은 지금 보고 있는 장의 다음 날이 자연스럽다. 다음 날이 여행 기간을 넘으면
     * 보고 있는 장과 같은 날로, 보고 있는 장에 날짜가 없으면 여행 시작일로 둔다.
     * 어디까지나 미리 골라 둔 값이라 사용자가 바꿀 수 있고, [페이지 추가] 를 눌러야 만들어진다.
     */
    function suggestedPageDate() {
        const nextDay = current.pageDate ? dayAfter(current.pageDate) : null;
        if (nextDay && draft.endDate && nextDay <= draft.endDate) {
            return nextDay;
        }
        return current.pageDate || draft.startDate || '';
    }

    function dayAfter(date) {
        const parsed = new Date(date + 'T00:00:00Z');
        if (Number.isNaN(parsed.getTime())) return null;
        parsed.setUTCDate(parsed.getUTCDate() + 1);
        return parsed.toISOString().slice(0, 10);
    }

    /** 새 페이지 팝오버를 열기 직전. 범위를 채우고 고르기 좋은 날짜를 넣어 둔다. */
    function prepareNewPageDate() {
        const input = page.querySelector('[data-guest-new-page-date]');
        if (!input) return;
        limitToTravelPeriod(input);
        input.value = suggestedPageDate();
        say(page.querySelector('[data-guest-add-page-error]'), '');
    }

    /* ===== 장 넘김 / 추가 / 삭제 ===== */

    /*
     * 장을 바꿀 때는 화면을 다시 연다. 본문 Quill 을 뜯어 고치지 않고
     * 회원 편집기가 장마다 화면을 새로 여는 것과 같은 흐름을 쓴다.
     * 저장은 그때그때 끝나 있어 다시 열어도 잃는 값이 없다.
     */
    function openPage(pageId) {
        store.setCurrentPageId(pageId);
        global.location.reload();
    }

    function wireActions() {
        action('previous-page').addEventListener('click', () => move(-1));
        action('next-page').addEventListener('click', () => move(1));

        /*
         * [새 페이지]. 회원 화면처럼 날짜를 고른 뒤에 만든다.
         * 다만 3장을 다 썼으면 날짜부터 묻지 않는다. 고르게 해 놓고 뒤늦게 막는 것보다
         * 누른 그 자리에서 바로 알리는 편이 낫다.
         */
        action('add-page').addEventListener('click', (event) => {
            /*
             * 여행 기간이 없으면 장의 날짜를 정할 수 없다. 날짜를 묻는 팝오버를 열어
             * 고르지도 못하게 하는 대신, 상단 안내(정보 입력하기)로 보낸다.
             */
            if (!basicsReady()) {
                event.preventDefault();
                page.querySelector('[data-guest-basics-missing]')
                    ?.scrollIntoView({block: 'nearest'});
                return;
            }
            if (draft.pages.length >= store.MAX_PAGES) {
                event.preventDefault();
                openPrompt();
                return;
            }
            // 팝오버가 열리는 참이면 고르기 좋은 날짜를 먼저 넣어 둔다.
            if (!pageAdd || !pageAdd.open) {
                prepareNewPageDate();
            }
        });

        action('confirm-add-page').addEventListener('click', () => {
            const chosen = page.querySelector('[data-guest-new-page-date]').value;
            const result = store.addPage({pageDate: chosen});
            if (!result.ok) {
                // 조용히 실패하지 않는다. 3장을 다 썼다는 사실을 그대로 알린다.
                if (result.reason === store.REASON.PAGE_LIMIT_REACHED) {
                    if (pageAdd) pageAdd.open = false;
                    openPrompt();
                    return;
                }
                say(page.querySelector('[data-guest-add-page-error]'), result.message);
                return;
            }
            openPage(result.page.pageId);
        });

        // 날짜가 없는 예전 장을 채우러 간다. 페이지 설정을 열어 날짜 칸으로 데려간다.
        action('set-page-date').addEventListener('click', () => {
            const settings = page.querySelector('.diary-page-settings');
            if (settings) settings.open = true;
            page.querySelector('[data-guest-page-date]').focus();
        });

        action('delete-page').addEventListener('click', () => {
            if (draft.pages.length <= 1) return;
            if (!global.confirm('이 페이지와 페이지의 글·꾸미기가 모두 삭제됩니다. 삭제하시겠습니까?')) {
                return;
            }
            const index = draft.pages.findIndex((item) => item.pageId === current.pageId);
            const removed = store.removePage(current.pageId);
            if (!removed.ok) return;
            const pages = removed.draft.pages;
            openPage(pages[Math.min(index, pages.length - 1)].pageId);
        });

        action('save-page-settings').addEventListener('click', () => {
            const patch = {
                backgroundType: page.querySelector('[data-guest-page-background]').value,
                paperColor: page.querySelector('[data-guest-page-paper]').value || null
            };
            /*
             * 여행 기간이 없으면 날짜는 아예 담지 않는다. 이 상태에서 정할 수 있는 날짜가 없고,
             * 어디에서 채우는지는 잠긴 칸 옆의 안내가 알려 준다.
             * (배경·종이색까지 함께 막지는 않는다)
             */
            if (basicsReady()) {
                patch.pageDate = page.querySelector('[data-guest-page-date]').value || null;
            }
            const result = store.updatePage(current.pageId, patch);
            if (!result.ok) {
                // 날짜가 비었거나 여행 기간 밖이면 이유를 그대로 보여 준다.
                say(page.querySelector('[data-guest-page-error]'), result.message);
                return;
            }
            say(page.querySelector('[data-guest-page-error]'), '');
            current = result.page;
            renderCurrentPage();
            renderPageDateNotice();
            page.querySelector('.diary-page-settings')?.removeAttribute('open');
        });

        /*
         * 저장하려고 로그인하러 간다. 체험 내용은 이 브라우저에 그대로 두고
         * "어느 다이어리였는지" 만 쪽지로 남긴 뒤 가져오기 자리로 간다.
         * 그 자리는 로그인해야 열리므로 서버가 평소처럼 로그인 화면으로 보내 주고,
         * 인증이 끝나면 원래 가려던 이 자리로 다시 데려온다.
         */
        action('login').addEventListener('click', (event) => {
            event.preventDefault();
            global.GuestDiaryImportIntent.startLogin(store.getDraft()?.draftId);
        });

        prompt?.querySelector('[data-guest-action="close-prompt"]')
            ?.addEventListener('click', closePrompt);
        prompt?.addEventListener('click', (event) => {
            if (event.target === prompt) closePrompt();
        });
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') closePrompt();
        });
    }

    function move(step) {
        const index = draft.pages.findIndex((item) => item.pageId === current.pageId);
        const next = draft.pages[index + step];
        if (next) openPage(next.pageId);
    }

    /** 사진 안내는 꾸미기 도구가 이미 쓰고 있는 자리에 그대로 적는다. */
    function showPhotoStatus(text, isError) {
        const status = document.getElementById('diary-sticker-status');
        if (!status) return;
        status.textContent = text;
        status.classList.toggle('is-error', Boolean(isError));
    }

    /* ===== 로그인 유도 ===== */

    function openPrompt(title, text) {
        if (!prompt) return;
        if (title) prompt.querySelector('[data-guest-prompt-title]').textContent = title;
        if (text) prompt.querySelector('[data-guest-prompt-text]').textContent = text;
        prompt.hidden = false;
    }

    function closePrompt() {
        if (prompt) prompt.hidden = true;
    }

    /* ===== 도구 ===== */

    function action(name) {
        return page.querySelector('[data-guest-action="' + name + '"]')
            || prompt?.querySelector('[data-guest-action="' + name + '"]')
            || document.createElement('button');
    }

    function show(element, visible) {
        if (element) element.hidden = !visible;
    }

    /** 안내 한 줄. 빈 말이면 자리를 접는다. */
    function say(element, message) {
        if (!element) return;
        element.textContent = message || '';
        element.hidden = !message;
    }

    /** 선택자에 값을 넣기 전에 따옴표 등을 막는다. */
    function cssEscape(value) {
        const text = String(value === undefined || value === null ? '' : value);
        return global.CSS && typeof global.CSS.escape === 'function'
            ? global.CSS.escape(text)
            : text.replace(/["\\]/g, '\\$&');
    }
})(window);
