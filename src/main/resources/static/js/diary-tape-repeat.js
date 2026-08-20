/**
 * 되풀이해서 그리는 스티커(마스킹테이프) 렌더러.
 *
 * 조각 경로(data-tape-left/center/right)가 있는 요소만 대상으로,
 * 그림 한 장을 늘이는 대신 [왼쪽 끝][가운데 무늬 되풀이][오른쪽 끝] 으로 이어 붙인다.
 * 길이를 늘려도 양끝과 무늬의 비율은 그대로고 가운데가 되풀이되는 수만 늘어난다.
 *
 * 읽기/편집 화면과 방금 붙인 스티커가 모두 이 함수 하나를 쓴다.
 * (요소의 위치/크기/회전/겹침 순서는 기존 방식 그대로다 — 여기서는 요소 안쪽만 그린다)
 */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.diary-sticker[data-tape-center]').forEach(render);

    window.diaryTape = {render};

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
});
