/**
 * 다이어리 페이지 넘김 연출.
 * 기존 ?spread=N 이동 구조는 그대로 두고, 링크로 이동하기 전에 종이 한 면이 넘어가는 효과만 보여준다.
 * 실제 페이지 요소(TEXT/PHOTO)는 건드리지 않고 임시로 만든 빈 종이 면에만 transform 을 준다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const spread = document.querySelector('.diary-book-spread');
    const links = Array.from(document.querySelectorAll('.diary-spread-button[data-flip]'));
    if (!spread || links.length === 0) return;

    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const compactLayout = window.matchMedia('(max-width: 860px)');
    const FLIP_DURATION = 560;
    const SLIDE_DURATION = 440;

    let flipping = false;

    links.forEach((link) => {
        link.addEventListener('click', (event) => {
            // 새 탭으로 여는 조작은 그대로 둔다.
            if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;

            event.preventDefault();
            if (flipping) return; // 넘어가는 동안의 연속 클릭은 무시한다.

            flipping = true;
            // 본문에 저장하지 않은 입력이 있으면 먼저 저장하고, 실패하면 이동하지 않는다.
            saveOpenPages().then((saved) => {
                if (!saved) {
                    flipping = false;
                    return;
                }
                // 모션을 줄이는 설정이면 연출 없이 바로 이동한다.
                if (reducedMotion.matches) {
                    window.location.href = link.href;
                    return;
                }
                startFlip(link.dataset.flip === 'next', () => {
                    window.location.href = link.href;
                });
            });
        });
    });

    /** 본문 편집기가 있으면 저장을 먼저 끝낸다. (편집기가 없으면 그대로 통과) */
    function saveOpenPages() {
        const editor = window.diaryEditor;
        if (!editor || typeof editor.flush !== 'function') {
            return Promise.resolve(true);
        }
        return editor.flush().catch(() => false);
    }

    function startFlip(isNext, done) {
        // 넘어가는 동안에는 편집(드래그/resize/rotate)과 액션이 시작되지 않게 막는다.
        spread.classList.add('is-flipping');

        let paper = null;
        const stateClass = compactLayout.matches
            ? (isNext ? 'is-slide-next' : 'is-slide-previous')
            : (isNext ? 'is-flipping-next' : 'is-flipping-previous');
        spread.classList.add(stateClass);

        if (!compactLayout.matches) {
            paper = document.createElement('div');
            paper.className = 'diary-flip-paper ' + (isNext ? 'is-flip-next' : 'is-flip-previous');
            paper.setAttribute('aria-hidden', 'true');
            // 넘어가는 면의 종이 배경을 그대로 따라간다.
            const source = spread.querySelector(
                isNext ? '.diary-sheet-right' : '.diary-sheet-left');
            const background = Array.from(source ? source.classList : [])
                .find(name => name.startsWith('diary-sheet-bg-'));
            if (background) paper.classList.add(background);
            spread.append(paper);
        }

        window.setTimeout(() => {
            if (paper) paper.remove();
            spread.classList.remove('is-flipping', stateClass);
            done();
        }, paper ? FLIP_DURATION : SLIDE_DURATION);
    }
});
