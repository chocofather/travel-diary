/**
 * 되풀이해서 그리는 스티커(마스킹테이프) 렌더러.
 *
 * 조각 경로(data-tape-left/center/right)가 있는 요소만 대상으로,
 * 그림 한 장을 늘이는 대신 [왼쪽 끝][가운데 무늬 되풀이][오른쪽 끝] 으로 이어 붙인다.
 * 길이를 늘려도 양끝과 무늬의 비율은 그대로고 가운데가 되풀이되는 수만 늘어난다.
 *
 * 읽기/편집 화면과 방금 붙인 스티커가 모두 이 함수 하나를 쓴다.
 * (요소의 위치/크기/회전/겹침 순서는 기존 방식 그대로다 — 여기서는 요소 안쪽만 그린다)
 *
 * "이 그림이 마스킹테이프인가" 와 "조각 경로가 무엇인가" 도 여기에서 답한다.
 * 서버는 저장된 image_url 하나로 그 둘을 다시 알아내는데(DiaryStickerKind / 스티커 목록),
 * 브라우저 쪽도 같은 답을 내야 체험 여행일기와 가져온 뒤의 모습이 같아진다.
 */
(function (global) {
    'use strict';

    /**
     * 마스킹테이프 asset 폴더. 서버의 DiaryStickerKind 와 같은 규칙이다.
     * (두 곳이 어긋나면 같은 테이프가 화면마다 다르게 그려진다)
     */
    const MASKING_TAPE_PREFIX = '/images/diary/stickers/masking-tape/';

    /** 저장된 그림 경로만 보고 마스킹테이프인지 가린다. 목록(picker)이 없는 화면에서도 답이 같다. */
    function isMaskingTape(imageUrl) {
        return typeof imageUrl === 'string' && imageUrl.startsWith(MASKING_TAPE_PREFIX);
    }

    /**
     * 그림 경로 → {마스킹테이프인지, 조각 경로}.
     *
     * <p>조각 경로는 화면에 이미 실려 있는 표에서 찾는다. 꾸미기 목록(.diary-sticker-option)이
     * 있는 편집 화면과, 목록 없이 미리보기만 있는 화면(책장·가져오기)이 같은 이름표를 쓴다.
     */
    function lookup(imageUrl) {
        const tape = isMaskingTape(imageUrl);
        if (!tape || !imageUrl) {
            return {maskingTape: false, repeat: null};
        }
        const source = document.querySelector(
            '[data-sticker-image="' + cssEscape(imageUrl) + '"][data-tape-center]');
        if (!source) {
            return {maskingTape: true, repeat: null};
        }
        return {
            maskingTape: true,
            repeat: {
                left: source.dataset.tapeLeft,
                center: source.dataset.tapeCenter,
                right: source.dataset.tapeRight
            }
        };
    }

    function render(item) {
        if (!item || item.classList.contains('is-tape-repeat')) return;

        const {tapeLeft, tapeCenter, tapeRight} = item.dataset;
        if (!tapeLeft || !tapeCenter || !tapeRight) return;

        const tape = document.createElement('div');
        tape.className = 'diary-tape';
        tape.setAttribute('aria-hidden', 'true');
        tape.append(piece('diary-tape-cap', tapeLeft),
                    piece('diary-tape-fill', tapeCenter),
                    piece('diary-tape-cap', tapeRight));

        // 완성형 그림은 대체 텍스트를 위해 남겨 두고 화면에서만 감춘다.
        item.prepend(tape);
        item.classList.add('is-tape-repeat');
    }

    function piece(className, url) {
        const part = document.createElement('span');
        part.className = className;
        part.style.backgroundImage = `url("${url}")`;
        return part;
    }

    /** 선택자에 값을 넣기 전에 따옴표 등을 막는다. */
    function cssEscape(value) {
        const text = String(value === undefined || value === null ? '' : value);
        return global.CSS && typeof global.CSS.escape === 'function'
            ? global.CSS.escape(text)
            : text.replace(/["\\]/g, '\\$&');
    }

    /*
      화면이 다 읽히기 전에도 물어볼 수 있도록 먼저 내놓는다.
      (defer 스크립트들이 DOMContentLoaded 전에 미리보기를 그리면서 lookup 을 쓴다)
    */
    global.diaryTape = {render, lookup, isMaskingTape, MASKING_TAPE_PREFIX};

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('.diary-sticker[data-tape-center]').forEach(render);
    });
})(window);
