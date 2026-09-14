/*
 * 체험 다이어리의 표지를 보기 전용으로 그린다.
 *
 * 회원 화면은 서버가 그려 준다(diary/cover-preview :: canvas). 체험 다이어리는 서버가
 * 값을 모르므로 브라우저가 같은 마크업을 만들어야 한다. 그 한 벌을 여기에 두고
 * 책장 카드(/diaries/demo)와 페이지 편집 화면의 작은 표지가 함께 쓴다.
 *
 * 회원 목록과 같은 두 갈래를 그대로 따른다.
 *   - 꾸민 표지(coverDesign)가 있으면  → .diary-cover-canvas 한 장
 *   - 없으면(기본 표지를 고른 경우)    → 재질만 보이는 무지 표지 + 책등
 *
 * 보기 전용이라 조작 손잡이도 저장 주소도 요소 번호도 넣지 않는다.
 * (조작 엔진은 .is-editable 이 있을 때만 붙으므로 여기에는 잡을 것이 아예 없다)
 *
 * 사진은 원본이 IndexedDB 에 있어 주소를 읽는 데 시간이 걸린다.
 * 상자를 먼저 놓고 GuestDiaryPhoto 가 주소를 채운다.
 */
(function (global) {
    'use strict';

    /** 표지 재질 코드의 생김새. localStorage 값은 믿을 수 없어 아는 모양만 class 로 바꾼다. */
    const STYLE_CODE = /^[A-Z][A-Z_]*$/;

    /** DiaryCoverStyle.toCssClass 와 같은 규칙. (LEATHER_BLACK → diary-cover-leather-black) */
    function coverStyleClass(code) {
        const value = String(code === undefined || code === null ? '' : code);
        if (!STYLE_CODE.test(value)) {
            return 'diary-cover-default';
        }
        return 'diary-cover-' + value.toLowerCase().replace(/_/g, '-');
    }

    /** 이미 붙어 있던 재질 class 를 걷어내고 새 것만 남긴다. */
    function paintMaterial(element, styleClass) {
        Array.from(element.classList).forEach((name) => {
            if (name.startsWith('diary-cover-')
                && name !== 'diary-cover-canvas'
                && name !== 'diary-cover-surface') {
                element.classList.remove(name);
            }
        });
        element.classList.add(styleClass);
    }

    /**
     * 꾸민 표지의 한 조각. 사용자가 쓴 글은 textContent 로만 넣는다.
     * 그림 경로는 저장해 둔 static 경로 그대로다. (data: 는 저장 단계에서 이미 걸러진다)
     */
    function previewItem(element) {
        const item = document.createElement('figure');
        item.style.left = (element.positionX * 100) + '%';
        item.style.top = (element.positionY * 100) + '%';
        item.style.width = (element.width * 100) + '%';
        item.style.height = (element.height * 100) + '%';
        item.style.transform = 'rotate(' + element.rotation + 'deg)';
        item.style.zIndex = String(element.zIndex);

        if (element.elementType === 'STICKER' && element.imageUrl) {
            item.className = 'diary-canvas-item diary-canvas-sticker diary-sticker';
            /*
             * 마스킹테이프인지는 저장된 그림 경로 하나로 가린다. (서버가 쓰는 규칙과 같다)
             * 조각 경로도 같은 자리에서 찾으므로, 꾸미기 목록이 없는 이 화면에서도
             * 가져온 뒤의 회원 표지와 같은 모습이 된다.
             */
            const tape = global.diaryTape?.lookup(element.imageUrl)
                || {maskingTape: false, repeat: null};
            if (tape.maskingTape) {
                item.dataset.stickerKind = 'masking-tape';
            }
            if (tape.repeat) {
                item.dataset.tapeLeft = tape.repeat.left;
                item.dataset.tapeCenter = tape.repeat.center;
                item.dataset.tapeRight = tape.repeat.right;
            }
            const image = document.createElement('img');
            image.src = element.imageUrl;
            image.alt = '';
            image.setAttribute('aria-hidden', 'true');
            item.append(image);
            return item;
        }

        if (element.elementType === 'TEXT') {
            item.className = 'diary-canvas-item diary-label';
            const text = element.textContent || '';
            item.style.setProperty('--diary-label-chars', String(text.length));
            const span = document.createElement('span');
            span.className = 'diary-label-text';
            if (element.textFont) span.classList.add('diary-font-' + element.textFont);
            if (element.textColor) span.style.color = element.textColor;
            span.textContent = text;
            item.append(span);
            return item;
        }

        if (element.elementType === 'PHOTO' && element.photoRef) {
            item.className = 'diary-canvas-item diary-canvas-photo diary-photo';
            item.classList.add(element.photoStyle === 'FULL'
                ? 'is-photo-full' : 'is-photo-polaroid');
            const image = document.createElement('img');
            image.alt = '';
            image.setAttribute('aria-hidden', 'true');
            item.append(image);
            /*
              원본은 IndexedDB 에 있어 읽는 데 시간이 걸린다. 상자를 먼저 놓고 주소만 채운다.
              편집 화면과 같은 주소 모음을 쓰므로 같은 사진에 주소가 두 번 생기지 않는다.
            */
            global.GuestDiaryPhoto?.fillImage(image, element.photoRef);
            return item;
        }

        return null;
    }

    /** 꾸민 표지 한 장. 회원 보관함 카드(cover-preview :: canvas)와 같은 구조다. */
    function customCanvas(coverDesign) {
        const canvas = document.createElement('div');
        canvas.className = 'diary-cover-canvas';
        paintMaterial(canvas, coverStyleClass(coverDesign.baseCoverStyle));
        if (coverDesign.backgroundColor) {
            canvas.style.setProperty('--diary-cover-color', coverDesign.backgroundColor);
        }

        const surface = document.createElement('div');
        surface.className = 'diary-canvas diary-cover-surface';
        coverDesign.elements.slice()
            .sort((left, right) => left.zIndex - right.zIndex)
            .forEach((element) => {
                const item = previewItem(element);
                if (!item) return;
                surface.append(item);
                global.diaryTape?.render(item);
            });

        const spine = document.createElement('span');
        spine.className = 'diary-cover-canvas-spine';
        spine.setAttribute('aria-hidden', 'true');

        canvas.append(surface, spine);
        return canvas;
    }

    /** 기본 표지를 고른 경우. 회원 목록의 무지 표지와 같은 조각이다. */
    function presetCover() {
        const fragment = document.createDocumentFragment();
        const placeholder = document.createElement('div');
        placeholder.className = 'diary-book-placeholder';
        placeholder.setAttribute('aria-hidden', 'true');
        const spine = document.createElement('span');
        spine.className = 'diary-book-spine';
        spine.setAttribute('aria-hidden', 'true');
        fragment.append(placeholder, spine);
        return fragment;
    }

    global.GuestDiaryCoverPreview = {
        coverStyleClass: coverStyleClass,

        /**
         * 표지 한 장을 그린다.
         *
         * @param book 재질 class 를 받을 바깥 상자 (.diary-book 에 해당)
         * @param coverHost 표지가 들어갈 자리 (.diary-book-cover 에 해당)
         * @param draft 체험 다이어리. coverDesign 이 있으면 꾸민 표지로 그린다
         */
        render(book, coverHost, draft) {
            if (!coverHost || !draft) return;
            coverHost.replaceChildren();

            const cover = draft.coverDesign;
            // 재질은 꾸민 표지가 있으면 그쪽 값이, 없으면 고른 기본 표지가 기준이다.
            const style = coverStyleClass(
                cover ? cover.baseCoverStyle : draft.coverStyle);
            if (book) paintMaterial(book, style);

            if (cover) {
                coverHost.append(customCanvas(cover));
            } else {
                coverHost.append(presetCover());
            }
        }
    };
})(window);
